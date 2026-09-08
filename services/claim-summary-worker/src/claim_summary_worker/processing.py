from collections.abc import Callable
from datetime import UTC, datetime
from time import monotonic
from typing import Protocol
from uuid import NAMESPACE_URL, UUID, uuid5

from claim_summary_worker.contracts import (
    ClaimSubmittedEnvelope,
    ClaimSummaryCompletedData,
    ClaimSummaryCompletedEnvelope,
    ClaimSummaryOutput,
    SummaryModelInput,
)
from claim_summary_worker.metrics import WorkerMetrics
from claim_summary_worker.safety import prepare_model_input


class ClaimEventHandler(Protocol):
    """Application port called by the RabbitMQ request consumer."""

    async def handle(self, event: ClaimSubmittedEnvelope) -> None: ...


class SummaryProvider(Protocol):
    async def generate(self, model_input: SummaryModelInput) -> ClaimSummaryOutput: ...


class SummaryPublisher(Protocol):
    async def publish(self, event: ClaimSummaryCompletedEnvelope) -> None: ...


class ProviderOutputError(ValueError):
    """Raised when provider output does not belong to the consumed claim."""


def completed_event_id(source_event_id: UUID) -> UUID:
    """Derive a stable result ID so source-event redelivery remains idempotent downstream."""

    return uuid5(NAMESPACE_URL, f"claim-summary-completed:{source_event_id}")


class ClaimSummaryProcessor:
    def __init__(
        self,
        provider: SummaryProvider,
        publisher: SummaryPublisher,
        clock: Callable[[], datetime] | None = None,
        event_id_factory: Callable[[UUID], UUID] = completed_event_id,
        metrics: WorkerMetrics | None = None,
    ) -> None:
        self._provider = provider
        self._publisher = publisher
        self._clock = clock or (lambda: datetime.now(UTC))
        self._event_id_factory = event_id_factory
        self._metrics = metrics or WorkerMetrics()

    async def handle(self, event: ClaimSubmittedEnvelope) -> None:
        started_at = monotonic()
        try:
            model_input = prepare_model_input(event)
            summary = await self._provider.generate(model_input)
            if summary.claim_id != event.data.claim_id:
                raise ProviderOutputError("provider output claim ID does not match input claim ID")

            generated_at = self._clock()
            completed = ClaimSummaryCompletedEnvelope(
                eventId=self._event_id_factory(event.event_id),
                aggregateId=event.aggregate_id,
                correlationId=event.correlation_id,
                occurredAt=generated_at,
                data=ClaimSummaryCompletedData(
                    **summary.model_dump(by_alias=True),
                    generatedAt=generated_at,
                ),
            )
            await self._publisher.publish(completed)
        except Exception:
            self._metrics.record_processing("error", monotonic() - started_at)
            raise
        self._metrics.record_processing("success", monotonic() - started_at)
