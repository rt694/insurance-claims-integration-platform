from claim_summary_worker.contracts import (
    ClaimSummaryOutput,
    ClaimType,
    HumanReviewQueue,
    SummaryModelInput,
)

UNTRUSTED_INSTRUCTION_PATTERN = "UNTRUSTED_INSTRUCTION_PATTERN"
INSTRUCTION_MARKERS = (
    "ignore previous",
    "ignore the rules",
    "system prompt",
    "developer message",
)


class MockSummaryProvider:
    """Deterministic local provider that exercises the production output contract."""

    async def generate(self, model_input: SummaryModelInput) -> ClaimSummaryOutput:
        review_queue = (
            HumanReviewQueue.SPECIALIST_REVIEW
            if model_input.claim_type in {ClaimType.LIFE, ClaimType.DISABILITY}
            else HumanReviewQueue.STANDARD_REVIEW
        )
        normalized_description = model_input.description.casefold()
        safety_flags = (
            [UNTRUSTED_INSTRUCTION_PATTERN]
            if any(marker in normalized_description for marker in INSTRUCTION_MARKERS)
            else []
        )
        return ClaimSummaryOutput(
            claimId=model_input.claim_id,
            summary=(
                f"{model_input.claim_type.value} claim reports an incident dated "
                f"{model_input.incident_date.isoformat()} with an estimated loss value of "
                f"{model_input.estimated_loss:.2f}. The submitted description contains "
                f"{len(model_input.description)} characters and requires human review."
            ),
            missingInformation=["Supporting documentation was not included in the event."],
            recommendedHumanReviewQueue=review_queue,
            safetyFlags=safety_flags,
        )
