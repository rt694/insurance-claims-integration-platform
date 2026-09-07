import logging
from datetime import UTC, datetime
from typing import Any

import aio_pika
from aio_pika import DeliveryMode, ExchangeType, Message
from aio_pika.abc import (
    AbstractChannel,
    AbstractExchange,
    AbstractIncomingMessage,
    AbstractQueue,
    AbstractRobustConnection,
)

from claim_summary_worker.config import WorkerSettings
from claim_summary_worker.contracts import EventContractError, decode_claim_submitted
from claim_summary_worker.processing import ClaimEventHandler

CLAIMS_EVENTS_EXCHANGE = "claims.events"
CLAIMS_RETRY_EXCHANGE = "claims.retry"
CLAIMS_DEAD_LETTER_EXCHANGE = "claims.dead-letter"
CLAIM_SUMMARY_REQUESTS_QUEUE = "claim.summary.requests.v1"
CLAIM_SUMMARY_REQUESTS_RETRY_QUEUE = "claim.summary.requests.retry.v1"
CLAIM_SUMMARY_REQUESTS_DEAD_LETTER_QUEUE = "claim.summary.requests.dlq.v1"
CLAIM_SUBMITTED_ROUTING_KEY = "claim.submitted.v1"

RETRY_COUNT_HEADER = "retryCount"
FAILURE_TYPE_HEADER = "failureType"
DEAD_LETTERED_AT_HEADER = "deadLetteredAt"

INVALID_CONTRACT_FAILURE = "INVALID_CONTRACT"
PROCESSING_FAILURE = "PROCESSING_FAILURE"

logger = logging.getLogger(__name__)


class MessageRoutingError(RuntimeError):
    """Raised when RabbitMQ does not safely accept a routed failure message."""


class RabbitMqConsumer:
    """Reliable RabbitMQ adapter for claim-submitted deliveries."""

    def __init__(self, settings: WorkerSettings, handler: ClaimEventHandler) -> None:
        self._settings = settings
        self._handler = handler
        self._connection: AbstractRobustConnection | None = None
        self._channel: AbstractChannel | None = None
        self._queue: AbstractQueue | None = None
        self._retry_exchange: AbstractExchange | None = None
        self._dead_letter_exchange: AbstractExchange | None = None
        self._consumer_tag: str | None = None

    @property
    def is_ready(self) -> bool:
        return bool(
            self._consumer_tag
            and self._connection
            and not self._connection.is_closed
            and self._connection.connected.is_set()
            and self._channel
            and not self._channel.is_closed
        )

    async def start(self) -> None:
        password = self._settings.rabbitmq_password
        if password is None:
            raise ValueError("RabbitMQ password is required")

        self._connection = await aio_pika.connect_robust(
            host=self._settings.rabbitmq_host,
            port=self._settings.rabbitmq_port,
            login=self._settings.rabbitmq_username,
            password=password.get_secret_value(),
            virtualhost=self._settings.rabbitmq_virtual_host,
            timeout=self._settings.rabbitmq_connection_timeout_seconds,
            client_properties={"connection_name": self._settings.service_name},
        )
        try:
            await self._start_consuming(self._connection)
        except BaseException:
            await self.close()
            raise

    async def _start_consuming(self, connection: AbstractRobustConnection) -> None:
        channel = await connection.channel(
            publisher_confirms=True,
            on_return_raises=True,
        )
        self._channel = channel
        await channel.set_qos(prefetch_count=self._settings.rabbitmq_prefetch_count)

        await channel.declare_exchange(
            CLAIMS_EVENTS_EXCHANGE,
            ExchangeType.TOPIC,
            durable=True,
            passive=True,
        )
        self._retry_exchange = await channel.declare_exchange(
            CLAIMS_RETRY_EXCHANGE,
            ExchangeType.TOPIC,
            durable=True,
            passive=True,
        )
        self._dead_letter_exchange = await channel.declare_exchange(
            CLAIMS_DEAD_LETTER_EXCHANGE,
            ExchangeType.TOPIC,
            durable=True,
            passive=True,
        )
        self._queue = await channel.declare_queue(
            CLAIM_SUMMARY_REQUESTS_QUEUE,
            durable=True,
            passive=True,
        )
        self._consumer_tag = await self._queue.consume(self._process_message, no_ack=False)

    async def close(self) -> None:
        if self._queue is not None and self._consumer_tag is not None:
            await self._queue.cancel(self._consumer_tag)
        self._consumer_tag = None
        if self._connection is not None and not self._connection.is_closed:
            await self._connection.close()

    async def _process_message(self, message: AbstractIncomingMessage) -> None:
        retry_count = self._read_retry_count(message.headers)
        try:
            event = decode_claim_submitted(message.body, self._settings.max_event_bytes)
        except EventContractError:
            await self._route_then_ack(
                message,
                exchange=self._required_dead_letter_exchange(),
                headers={
                    **dict(message.headers),
                    FAILURE_TYPE_HEADER: INVALID_CONTRACT_FAILURE,
                    DEAD_LETTERED_AT_HEADER: datetime.now(UTC).isoformat(),
                },
            )
            logger.warning(
                "Dead-lettered invalid claim event",
                extra={"message_id": message.message_id, "failure_type": INVALID_CONTRACT_FAILURE},
            )
            return

        try:
            await self._handler.handle(event)
        except Exception:
            next_attempt = retry_count + 1
            exhausted = next_attempt >= self._settings.rabbitmq_max_processing_attempts
            headers = {
                **dict(message.headers),
                RETRY_COUNT_HEADER: next_attempt,
                FAILURE_TYPE_HEADER: PROCESSING_FAILURE,
            }
            if exhausted:
                headers[DEAD_LETTERED_AT_HEADER] = datetime.now(UTC).isoformat()
            await self._route_then_ack(
                message,
                exchange=(
                    self._required_dead_letter_exchange()
                    if exhausted
                    else self._required_retry_exchange()
                ),
                headers=headers,
            )
            logger.warning(
                "Routed failed claim event",
                extra={
                    "message_id": message.message_id,
                    "attempt": next_attempt,
                    "dead_lettered": exhausted,
                },
            )
            return

        await message.ack()

    async def _route_then_ack(
        self,
        message: AbstractIncomingMessage,
        *,
        exchange: AbstractExchange,
        headers: dict[str, Any],
    ) -> None:
        routed_message = Message(
            body=message.body,
            headers=headers,
            content_type=message.content_type,
            content_encoding=message.content_encoding,
            delivery_mode=DeliveryMode.PERSISTENT,
            correlation_id=message.correlation_id,
            message_id=message.message_id,
            timestamp=message.timestamp,
            type=message.type,
            app_id=self._settings.service_name,
        )
        try:
            confirmation = await exchange.publish(
                routed_message,
                routing_key=CLAIM_SUBMITTED_ROUTING_KEY,
                mandatory=True,
            )
            if confirmation is None:
                raise MessageRoutingError("RabbitMQ did not confirm the routed message")
        except Exception:
            await message.reject(requeue=True)
            raise
        await message.ack()

    @staticmethod
    def _read_retry_count(headers: Any) -> int:
        value: object = dict(headers).get(RETRY_COUNT_HEADER, 0)
        if isinstance(value, bool) or not isinstance(value, int) or value < 0:
            return 0
        return value

    def _required_retry_exchange(self) -> AbstractExchange:
        if self._retry_exchange is None:
            raise RuntimeError("RabbitMQ consumer has not started")
        return self._retry_exchange

    def _required_dead_letter_exchange(self) -> AbstractExchange:
        if self._dead_letter_exchange is None:
            raise RuntimeError("RabbitMQ consumer has not started")
        return self._dead_letter_exchange
