import asyncio
import json
from datetime import UTC, datetime
from typing import Any, cast
from uuid import UUID

import pytest
from aio_pika.abc import AbstractExchange

from claim_summary_worker.config import WorkerSettings
from claim_summary_worker.contracts import (
    ClaimSummaryCompletedData,
    ClaimSummaryCompletedEnvelope,
    HumanReviewQueue,
)
from claim_summary_worker.messaging import (
    CLAIM_SUMMARY_COMPLETED_ROUTING_KEY,
    MessageRoutingError,
    RabbitMqResultPublisher,
    ResultEventTooLargeError,
)

CLAIM_ID = UUID("63eb8ca7-dc07-4a86-978a-e8a1b471650b")
EVENT_ID = UUID("13c3ad53-269c-43ef-b34a-0d5c52ddf712")
GENERATED_AT = datetime(2026, 3, 1, 12, 5, tzinfo=UTC)


class FakeExchange:
    def __init__(self, confirmation: object | None = object()) -> None:
        self.confirmation = confirmation
        self.publications: list[tuple[Any, str, bool]] = []

    async def publish(
        self, message: Any, routing_key: str, mandatory: bool = True
    ) -> object | None:
        self.publications.append((message, routing_key, mandatory))
        return self.confirmation


def completed_event(
    summary: str = "Synthetic claim requires human review.",
) -> ClaimSummaryCompletedEnvelope:
    return ClaimSummaryCompletedEnvelope(
        eventId=EVENT_ID,
        aggregateId=CLAIM_ID,
        correlationId="publisher-test-correlation",
        occurredAt=GENERATED_AT,
        data=ClaimSummaryCompletedData(
            claimId=CLAIM_ID,
            summary=summary,
            missingInformation=["Supporting documentation"],
            recommendedHumanReviewQueue=HumanReviewQueue.STANDARD_REVIEW,
            safetyFlags=[],
            generatedAt=GENERATED_AT,
        ),
    )


def test_publisher_sends_persistent_versioned_event_with_correlation() -> None:
    exchange = FakeExchange()
    publisher = RabbitMqResultPublisher(WorkerSettings())
    publisher._exchange = cast(AbstractExchange, exchange)

    asyncio.run(publisher.publish(completed_event()))

    message, routing_key, mandatory = exchange.publications[0]
    body = json.loads(message.body)
    assert routing_key == CLAIM_SUMMARY_COMPLETED_ROUTING_KEY
    assert mandatory is True
    assert message.message_id == str(EVENT_ID)
    assert message.correlation_id == "publisher-test-correlation"
    assert message.delivery_mode.value == 2
    assert body["eventType"] == "claim.summary.completed"
    assert body["eventVersion"] == 1
    assert body["aggregateId"] == str(CLAIM_ID)
    assert body["data"]["generatedAt"] == "2026-03-01T12:05:00Z"


def test_publisher_requires_startup() -> None:
    publisher = RabbitMqResultPublisher(WorkerSettings())

    with pytest.raises(RuntimeError, match="has not started"):
        asyncio.run(publisher.publish(completed_event()))


def test_publisher_rejects_event_that_java_consumer_would_reject_for_size() -> None:
    publisher = RabbitMqResultPublisher(WorkerSettings(max_event_bytes=1_024))
    publisher._exchange = cast(AbstractExchange, FakeExchange())

    with pytest.raises(ResultEventTooLargeError, match="size limit"):
        asyncio.run(publisher.publish(completed_event("x" * 2_000)))


def test_publisher_requires_a_broker_confirmation() -> None:
    publisher = RabbitMqResultPublisher(WorkerSettings())
    publisher._exchange = cast(AbstractExchange, FakeExchange(confirmation=None))

    with pytest.raises(MessageRoutingError, match="did not confirm"):
        asyncio.run(publisher.publish(completed_event()))
