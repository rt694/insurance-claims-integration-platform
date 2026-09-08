import asyncio
import json
from datetime import UTC, datetime
from typing import Any
from uuid import UUID, uuid4

import pytest

from claim_summary_worker.contracts import (
    ClaimSummaryCompletedEnvelope,
    ClaimSummaryOutput,
    HumanReviewQueue,
    SummaryModelInput,
    decode_claim_submitted,
)
from claim_summary_worker.metrics import WorkerMetrics
from claim_summary_worker.processing import (
    ClaimSummaryProcessor,
    ProviderOutputError,
    completed_event_id,
)

RESULT_EVENT_ID = UUID("13c3ad53-269c-43ef-b34a-0d5c52ddf712")
GENERATED_AT = datetime(2026, 3, 1, 12, 5, tzinfo=UTC)


class FixedProvider:
    def __init__(self, claim_id: UUID) -> None:
        self.claim_id = claim_id
        self.inputs: list[SummaryModelInput] = []

    async def generate(self, model_input: SummaryModelInput) -> ClaimSummaryOutput:
        self.inputs.append(model_input)
        return ClaimSummaryOutput(
            claimId=self.claim_id,
            summary="Synthetic incident requires human review.",
            missingInformation=["Supporting documentation"],
            recommendedHumanReviewQueue=HumanReviewQueue.STANDARD_REVIEW,
            safetyFlags=[],
        )


class RecordingPublisher:
    def __init__(self) -> None:
        self.events: list[ClaimSummaryCompletedEnvelope] = []

    async def publish(self, event: ClaimSummaryCompletedEnvelope) -> None:
        self.events.append(event)


def test_processor_preserves_identity_and_correlation(claim_event: dict[str, Any]) -> None:
    submitted = decode_claim_submitted(json.dumps(claim_event).encode())
    provider = FixedProvider(submitted.data.claim_id)
    publisher = RecordingPublisher()
    metrics = WorkerMetrics()
    processor = ClaimSummaryProcessor(
        provider,
        publisher,
        clock=lambda: GENERATED_AT,
        event_id_factory=lambda _: RESULT_EVENT_ID,
        metrics=metrics,
    )

    asyncio.run(processor.handle(submitted))

    assert len(provider.inputs) == 1
    assert provider.inputs[0].claim_id == submitted.data.claim_id
    assert len(publisher.events) == 1
    completed = publisher.events[0]
    assert completed.event_id == RESULT_EVENT_ID
    assert completed.event_type == "claim.summary.completed"
    assert completed.event_version == 1
    assert completed.aggregate_id == submitted.aggregate_id
    assert completed.correlation_id == submitted.correlation_id
    assert completed.occurred_at == GENERATED_AT
    assert completed.data.generated_at == GENERATED_AT
    assert (
        metrics.registry.get_sample_value(
            "insurance_worker_claim_summaries_total", {"outcome": "success"}
        )
        == 1
    )


def test_processor_rejects_cross_claim_provider_output(claim_event: dict[str, Any]) -> None:
    submitted = decode_claim_submitted(json.dumps(claim_event).encode())
    publisher = RecordingPublisher()
    metrics = WorkerMetrics()
    processor = ClaimSummaryProcessor(FixedProvider(uuid4()), publisher, metrics=metrics)

    with pytest.raises(ProviderOutputError, match="does not match"):
        asyncio.run(processor.handle(submitted))

    assert publisher.events == []
    assert (
        metrics.registry.get_sample_value(
            "insurance_worker_claim_summaries_total", {"outcome": "error"}
        )
        == 1
    )


def test_completed_event_id_is_stable_for_source_redelivery(claim_event: dict[str, Any]) -> None:
    submitted = decode_claim_submitted(json.dumps(claim_event).encode())

    first = completed_event_id(submitted.event_id)
    second = completed_event_id(submitted.event_id)

    assert first == second
    assert first != submitted.event_id
