import json
from datetime import date, datetime
from decimal import Decimal
from enum import StrEnum
from typing import Annotated, Any
from uuid import UUID

from pydantic import AwareDatetime, BaseModel, ConfigDict, Field, ValidationError, model_validator

CLAIM_SUBMITTED_EVENT_TYPE = "claim.submitted"
CLAIM_SUBMITTED_EVENT_VERSION = 1
CLAIM_AGGREGATE_TYPE = "claim"
CLAIM_SUMMARY_COMPLETED_EVENT_TYPE = "claim.summary.completed"
CLAIM_SUMMARY_COMPLETED_EVENT_VERSION = 1


class EventContractError(ValueError):
    """Raised when a broker body cannot satisfy the supported event contract."""


class StrictContract(BaseModel):
    model_config = ConfigDict(
        extra="forbid",
        frozen=True,
        populate_by_name=True,
        str_strip_whitespace=True,
    )


class ClaimType(StrEnum):
    AUTO = "AUTO"
    PROPERTY = "PROPERTY"
    LIFE = "LIFE"
    DISABILITY = "DISABILITY"


class ClaimStatus(StrEnum):
    SUBMITTED = "SUBMITTED"


class HumanReviewQueue(StrEnum):
    STANDARD_REVIEW = "STANDARD_REVIEW"
    COMPLEX_REVIEW = "COMPLEX_REVIEW"
    SPECIALIST_REVIEW = "SPECIALIST_REVIEW"


class ClaimSubmittedData(StrictContract):
    claim_id: UUID = Field(alias="claimId")
    claim_type: ClaimType = Field(alias="claimType")
    incident_date: date = Field(alias="incidentDate")
    description: Annotated[str, Field(min_length=1, max_length=4_000)]
    estimated_loss: Annotated[Decimal, Field(ge=0, max_digits=15, decimal_places=2)] = Field(
        alias="estimatedLoss"
    )
    status: ClaimStatus
    submitted_at: datetime = Field(alias="submittedAt")


class ClaimSubmittedEnvelope(StrictContract):
    event_id: UUID = Field(alias="eventId")
    event_type: str = Field(alias="eventType")
    event_version: int = Field(alias="eventVersion", gt=0)
    aggregate_type: str = Field(alias="aggregateType")
    aggregate_id: UUID = Field(alias="aggregateId")
    correlation_id: Annotated[str, Field(min_length=1, max_length=128)] = Field(
        alias="correlationId"
    )
    occurred_at: datetime = Field(alias="occurredAt")
    data: ClaimSubmittedData

    @model_validator(mode="after")
    def validate_supported_contract(self) -> "ClaimSubmittedEnvelope":
        if (
            self.event_type != CLAIM_SUBMITTED_EVENT_TYPE
            or self.event_version != CLAIM_SUBMITTED_EVENT_VERSION
            or self.aggregate_type != CLAIM_AGGREGATE_TYPE
        ):
            raise ValueError("unsupported claim event contract")
        if self.aggregate_id != self.data.claim_id:
            raise ValueError("aggregate ID does not match payload claim ID")
        return self


class SummaryModelInput(StrictContract):
    claim_id: UUID
    claim_type: ClaimType
    incident_date: date
    description: Annotated[str, Field(min_length=1, max_length=4_000)]
    estimated_loss: Annotated[Decimal, Field(ge=0, max_digits=15, decimal_places=2)]


class ClaimSummaryOutput(StrictContract):
    claim_id: UUID = Field(alias="claimId")
    summary: Annotated[str, Field(min_length=1, max_length=2_000)]
    missing_information: list[Annotated[str, Field(min_length=1, max_length=500)]] = Field(
        alias="missingInformation", max_length=20
    )
    recommended_human_review_queue: HumanReviewQueue = Field(alias="recommendedHumanReviewQueue")
    safety_flags: list[Annotated[str, Field(min_length=1, max_length=100)]] = Field(
        alias="safetyFlags", max_length=20
    )


class ClaimSummaryCompletedData(ClaimSummaryOutput):
    generated_at: AwareDatetime = Field(alias="generatedAt")


class ClaimSummaryCompletedEnvelope(StrictContract):
    event_id: UUID = Field(alias="eventId")
    event_type: str = Field(
        default=CLAIM_SUMMARY_COMPLETED_EVENT_TYPE,
        alias="eventType",
    )
    event_version: int = Field(
        default=CLAIM_SUMMARY_COMPLETED_EVENT_VERSION,
        alias="eventVersion",
    )
    aggregate_type: str = Field(default=CLAIM_AGGREGATE_TYPE, alias="aggregateType")
    aggregate_id: UUID = Field(alias="aggregateId")
    correlation_id: Annotated[str, Field(min_length=1, max_length=128)] = Field(
        alias="correlationId"
    )
    occurred_at: AwareDatetime = Field(alias="occurredAt")
    data: ClaimSummaryCompletedData

    @model_validator(mode="after")
    def validate_completed_contract(self) -> "ClaimSummaryCompletedEnvelope":
        if (
            self.event_type != CLAIM_SUMMARY_COMPLETED_EVENT_TYPE
            or self.event_version != CLAIM_SUMMARY_COMPLETED_EVENT_VERSION
            or self.aggregate_type != CLAIM_AGGREGATE_TYPE
        ):
            raise ValueError("unsupported claim summary event contract")
        if self.aggregate_id != self.data.claim_id:
            raise ValueError("aggregate ID does not match summary claim ID")
        return self


def decode_claim_submitted(body: bytes, max_event_bytes: int = 65_536) -> ClaimSubmittedEnvelope:
    if len(body) > max_event_bytes:
        raise EventContractError("claim event exceeds the size limit")
    try:
        return ClaimSubmittedEnvelope.model_validate_json(body)
    except (ValidationError, ValueError, json.JSONDecodeError) as exception:
        raise EventContractError("claim event failed schema validation") from exception


def to_model_input(event: ClaimSubmittedEnvelope) -> SummaryModelInput:
    """Allowlist only the fields required to assist a human reviewer."""

    return SummaryModelInput(
        claim_id=event.data.claim_id,
        claim_type=event.data.claim_type,
        incident_date=event.data.incident_date,
        description=event.data.description,
        estimated_loss=event.data.estimated_loss,
    )


def strict_summary_json_schema() -> dict[str, Any]:
    """Return the schema future model providers must require and validate."""

    return ClaimSummaryOutput.model_json_schema(by_alias=True)
