import asyncio
import json
from secrets import token_urlsafe
from typing import Any

import aio_pika
import pytest
from aio_pika import DeliveryMode, ExchangeType, Message
from pydantic import SecretStr
from testcontainers.community.rabbitmq import RabbitMqContainer

from claim_summary_worker.config import WorkerSettings
from claim_summary_worker.contracts import ClaimSubmittedEnvelope
from claim_summary_worker.messaging import (
    CLAIM_SUBMITTED_ROUTING_KEY,
    CLAIM_SUMMARY_REQUESTS_DEAD_LETTER_QUEUE,
    CLAIM_SUMMARY_REQUESTS_QUEUE,
    CLAIM_SUMMARY_REQUESTS_RETRY_QUEUE,
    CLAIMS_DEAD_LETTER_EXCHANGE,
    CLAIMS_EVENTS_EXCHANGE,
    CLAIMS_RETRY_EXCHANGE,
    FAILURE_TYPE_HEADER,
    INVALID_CONTRACT_FAILURE,
    RabbitMqConsumer,
)


class SignalingHandler:
    def __init__(self) -> None:
        self.processed = asyncio.Event()
        self.event: ClaimSubmittedEnvelope | None = None

    async def handle(self, event: ClaimSubmittedEnvelope) -> None:
        self.event = event
        self.processed.set()


@pytest.mark.integration
def test_real_broker_consumes_valid_event_and_dead_letters_invalid_contract(
    claim_event: dict[str, Any],
) -> None:
    username = "claims_integration_test"
    password = token_urlsafe(24)
    with RabbitMqContainer(
        "rabbitmq:4.3.5-management-alpine",
        username=username,
        password=password,
    ) as rabbitmq:
        parameters = rabbitmq.get_connection_params()
        asyncio.run(
            exercise_consumer(
                host=parameters.host,
                port=parameters.port,
                username=username,
                password=password,
                claim_event=claim_event,
            )
        )


async def exercise_consumer(
    *,
    host: str,
    port: int,
    username: str,
    password: str,
    claim_event: dict[str, Any],
) -> None:
    topology_connection = await aio_pika.connect_robust(
        host=host,
        port=port,
        login=username,
        password=password,
    )
    topology_channel = await topology_connection.channel(publisher_confirms=True)
    events_exchange = await topology_channel.declare_exchange(
        CLAIMS_EVENTS_EXCHANGE, ExchangeType.TOPIC, durable=True
    )
    retry_exchange = await topology_channel.declare_exchange(
        CLAIMS_RETRY_EXCHANGE, ExchangeType.TOPIC, durable=True
    )
    dead_letter_exchange = await topology_channel.declare_exchange(
        CLAIMS_DEAD_LETTER_EXCHANGE, ExchangeType.TOPIC, durable=True
    )
    requests_queue = await topology_channel.declare_queue(
        CLAIM_SUMMARY_REQUESTS_QUEUE, durable=True
    )
    await requests_queue.bind(events_exchange, CLAIM_SUBMITTED_ROUTING_KEY)
    retry_queue = await topology_channel.declare_queue(
        CLAIM_SUMMARY_REQUESTS_RETRY_QUEUE,
        durable=True,
        arguments={
            "x-queue-type": "quorum",
            "x-dead-letter-strategy": "at-least-once",
            "x-overflow": "reject-publish",
            "x-message-ttl": 5_000,
            "x-dead-letter-exchange": CLAIMS_EVENTS_EXCHANGE,
            "x-dead-letter-routing-key": CLAIM_SUBMITTED_ROUTING_KEY,
        },
    )
    await retry_queue.bind(retry_exchange, CLAIM_SUBMITTED_ROUTING_KEY)
    dead_letter_queue = await topology_channel.declare_queue(
        CLAIM_SUMMARY_REQUESTS_DEAD_LETTER_QUEUE, durable=True
    )
    await dead_letter_queue.bind(dead_letter_exchange, CLAIM_SUBMITTED_ROUTING_KEY)

    handler = SignalingHandler()
    consumer = RabbitMqConsumer(
        WorkerSettings(
            rabbitmq_enabled=True,
            rabbitmq_host=host,
            rabbitmq_port=port,
            rabbitmq_username=username,
            rabbitmq_password=SecretStr(password),
        ),
        handler,
    )
    await consumer.start()
    try:
        assert consumer.is_ready is True
        await events_exchange.publish(
            Message(
                json.dumps(claim_event).encode(),
                delivery_mode=DeliveryMode.PERSISTENT,
                content_type="application/json",
            ),
            CLAIM_SUBMITTED_ROUTING_KEY,
        )
        await asyncio.wait_for(handler.processed.wait(), timeout=5)
        assert handler.event is not None

        await events_exchange.publish(
            Message(b'{"eventType":"unsupported"}', delivery_mode=DeliveryMode.PERSISTENT),
            CLAIM_SUBMITTED_ROUTING_KEY,
        )
        dead_letter = await wait_for_message(dead_letter_queue)
        assert dead_letter.headers[FAILURE_TYPE_HEADER] == INVALID_CONTRACT_FAILURE
        await dead_letter.ack()
    finally:
        await consumer.close()
        await topology_connection.close()


async def wait_for_message(
    queue: aio_pika.abc.AbstractQueue,
) -> aio_pika.abc.AbstractIncomingMessage:
    async with asyncio.timeout(5):
        while True:
            message = await queue.get(fail=False)
            if message is not None:
                return message
            await asyncio.sleep(0.05)
