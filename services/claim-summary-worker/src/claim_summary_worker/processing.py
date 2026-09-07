from typing import Protocol

from claim_summary_worker.contracts import ClaimSubmittedEnvelope


class ClaimEventHandler(Protocol):
    """Application port completed by the provider-and-publisher story."""

    async def handle(self, event: ClaimSubmittedEnvelope) -> None: ...
