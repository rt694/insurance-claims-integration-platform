import asyncio
import json
from typing import Any, cast

import pytest
from aio_pika.abc import AbstractExchange, AbstractIncomingMessage

from claim_summary_worker.config import WorkerSettings
from claim_summary_worker.contracts import ClaimSubmittedEnvelope
from claim_summary_worker.messaging import (
    DEAD_LETTERED_AT_HEADER,
    FAILURE_TYPE_HEADER,
    INVALID_CONTRACT_FAILURE,
    PROCESSING_FAILURE,
    RETRY_COUNT_HEADER,
    RabbitMqConsumer,
)


class RecordingHandler:
    def __init__(self, error: Exception | None = None) -> None:
        self.error = error
        self.events: list[ClaimSubmittedEnvelope] = []

    async def handle(self, event: ClaimSubmittedEnvelope) -> None:
        self.events.append(event)
        if self.error is not None:
            raise self.error


class FakeIncomingMessage:
    def __init__(self, body: bytes, headers: dict[str, Any] | None = None) -> None:
        self.body = body
        self.headers = headers or {}
        self.content_type = "application/json"
        self.content_encoding = "utf-8"
        self.correlation_id = "messaging-test-correlation"
        self.message_id = "8b050b8f-1e6f-4aca-b7d0-198d93cde18f"
        self.timestamp = None
        self.type = "claim.submitted"
        self.acknowledged = False
        self.rejections: list[bool] = []

    async def ack(self) -> None:
        self.acknowledged = True

    async def reject(self, requeue: bool = False) -> None:
        self.rejections.append(requeue)


class FakeExchange:
    def __init__(self, error: Exception | None = None) -> None:
        self.error = error
        self.messages: list[Any] = []

    async def publish(self, message: Any, routing_key: str, mandatory: bool = True) -> bool:
        if self.error is not None:
            raise self.error
        self.messages.append((message, routing_key, mandatory))
        return True


def event_body(claim_event: dict[str, Any]) -> bytes:
    return json.dumps(claim_event).encode()


def configured_consumer(handler: RecordingHandler) -> RabbitMqConsumer:
    return RabbitMqConsumer(
        WorkerSettings(rabbitmq_max_processing_attempts=3),
        handler,
    )


def test_successful_processing_acknowledges_only_after_handler(
    claim_event: dict[str, Any],
) -> None:
    handler = RecordingHandler()
    consumer = configured_consumer(handler)
    message = FakeIncomingMessage(event_body(claim_event))

    asyncio.run(consumer._process_message(cast(AbstractIncomingMessage, message)))

    assert len(handler.events) == 1
    assert message.acknowledged is True
    assert message.rejections == []


def test_invalid_contract_is_confirmed_to_dead_letter_before_acknowledgment() -> None:
    handler = RecordingHandler()
    consumer = configured_consumer(handler)
    dead_letter_exchange = FakeExchange()
    consumer._dead_letter_exchange = cast(AbstractExchange, dead_letter_exchange)
    message = FakeIncomingMessage(b'{"eventType":"unsupported"}')

    asyncio.run(consumer._process_message(cast(AbstractIncomingMessage, message)))

    routed_message, routing_key, mandatory = dead_letter_exchange.messages[0]
    assert routed_message.headers[FAILURE_TYPE_HEADER] == INVALID_CONTRACT_FAILURE
    assert DEAD_LETTERED_AT_HEADER in routed_message.headers
    assert routing_key == "claim.submitted.v1"
    assert mandatory is True
    assert handler.events == []
    assert message.acknowledged is True


def test_transient_failure_is_sent_to_delay_queue_with_incremented_attempt(
    claim_event: dict[str, Any],
) -> None:
    consumer = configured_consumer(RecordingHandler(RuntimeError("temporary")))
    retry_exchange = FakeExchange()
    consumer._retry_exchange = cast(AbstractExchange, retry_exchange)
    message = FakeIncomingMessage(event_body(claim_event))

    asyncio.run(consumer._process_message(cast(AbstractIncomingMessage, message)))

    routed_message, _, _ = retry_exchange.messages[0]
    assert routed_message.headers[RETRY_COUNT_HEADER] == 1
    assert routed_message.headers[FAILURE_TYPE_HEADER] == PROCESSING_FAILURE
    assert message.acknowledged is True


def test_exhausted_failure_is_sent_to_dead_letter_queue(
    claim_event: dict[str, Any],
) -> None:
    consumer = configured_consumer(RecordingHandler(RuntimeError("still failing")))
    dead_letter_exchange = FakeExchange()
    consumer._dead_letter_exchange = cast(AbstractExchange, dead_letter_exchange)
    message = FakeIncomingMessage(event_body(claim_event), {RETRY_COUNT_HEADER: 2})

    asyncio.run(consumer._process_message(cast(AbstractIncomingMessage, message)))

    routed_message, _, _ = dead_letter_exchange.messages[0]
    assert routed_message.headers[RETRY_COUNT_HEADER] == 3
    assert routed_message.headers[FAILURE_TYPE_HEADER] == PROCESSING_FAILURE
    assert DEAD_LETTERED_AT_HEADER in routed_message.headers
    assert message.acknowledged is True


def test_original_is_requeued_when_failure_routing_is_not_confirmed(
    claim_event: dict[str, Any],
) -> None:
    consumer = configured_consumer(RecordingHandler(RuntimeError("temporary")))
    consumer._retry_exchange = cast(AbstractExchange, FakeExchange(RuntimeError("broker down")))
    message = FakeIncomingMessage(event_body(claim_event))

    with pytest.raises(RuntimeError, match="broker down"):
        asyncio.run(consumer._process_message(cast(AbstractIncomingMessage, message)))

    assert message.acknowledged is False
    assert message.rejections == [True]


def test_invalid_retry_header_restarts_bounded_attempt_count(
    claim_event: dict[str, Any],
) -> None:
    consumer = configured_consumer(RecordingHandler(RuntimeError("temporary")))
    retry_exchange = FakeExchange()
    consumer._retry_exchange = cast(AbstractExchange, retry_exchange)
    message = FakeIncomingMessage(event_body(claim_event), {RETRY_COUNT_HEADER: "invalid"})

    asyncio.run(consumer._process_message(cast(AbstractIncomingMessage, message)))

    routed_message, _, _ = retry_exchange.messages[0]
    assert routed_message.headers[RETRY_COUNT_HEADER] == 1
