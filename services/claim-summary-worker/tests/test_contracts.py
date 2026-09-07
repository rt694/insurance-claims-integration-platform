import copy
import json
from typing import Any

import pytest

from claim_summary_worker.contracts import (
    CLAIM_SUBMITTED_EVENT_TYPE,
    ClaimSummaryOutput,
    EventContractError,
    decode_claim_submitted,
    strict_summary_json_schema,
    to_model_input,
)


def encoded(event: dict[str, Any]) -> bytes:
    return json.dumps(event).encode()


def test_decodes_the_supported_java_event_contract(claim_event: dict[str, Any]) -> None:
    event = decode_claim_submitted(encoded(claim_event))

    assert event.event_type == CLAIM_SUBMITTED_EVENT_TYPE
    assert event.aggregate_id == event.data.claim_id
    assert str(event.data.estimated_loss) == "1250.00"


@pytest.mark.parametrize(
    ("field", "value"),
    [
        ("eventType", "claim.updated"),
        ("eventVersion", 2),
        ("aggregateType", "policy"),
    ],
)
def test_rejects_unsupported_event_contracts(
    claim_event: dict[str, Any], field: str, value: object
) -> None:
    claim_event[field] = value

    with pytest.raises(EventContractError, match="schema validation"):
        decode_claim_submitted(encoded(claim_event))


def test_rejects_an_aggregate_id_that_does_not_match_the_payload(
    claim_event: dict[str, Any],
) -> None:
    claim_event["aggregateId"] = "045329fa-48ca-4c9e-af62-a3bdcfd6ba30"

    with pytest.raises(EventContractError, match="schema validation"):
        decode_claim_submitted(encoded(claim_event))


def test_rejects_unknown_or_unnecessary_fields(claim_event: dict[str, Any]) -> None:
    claim_event["data"]["claimantName"] = "Not required by the worker"

    with pytest.raises(EventContractError, match="schema validation"):
        decode_claim_submitted(encoded(claim_event))


def test_rejects_a_body_before_parsing_when_it_exceeds_the_byte_limit() -> None:
    with pytest.raises(EventContractError, match="size limit"):
        decode_claim_submitted(b"{" + b"x" * 100, max_event_bytes=32)


def test_rejects_a_description_over_the_contract_limit(claim_event: dict[str, Any]) -> None:
    oversized = copy.deepcopy(claim_event)
    oversized["data"]["description"] = "x" * 4_001

    with pytest.raises(EventContractError, match="schema validation"):
        decode_claim_submitted(encoded(oversized))


def test_model_input_is_an_explicit_minimized_allowlist(claim_event: dict[str, Any]) -> None:
    event = decode_claim_submitted(encoded(claim_event))

    model_input = to_model_input(event)

    assert set(model_input.model_dump(mode="json")) == {
        "claim_id",
        "claim_type",
        "incident_date",
        "description",
        "estimated_loss",
    }


def test_summary_contract_exports_aliases_expected_by_the_claims_service() -> None:
    schema = strict_summary_json_schema()

    assert schema["additionalProperties"] is False
    assert set(schema["required"]) == {
        "claimId",
        "summary",
        "missingInformation",
        "recommendedHumanReviewQueue",
        "safetyFlags",
    }
    assert ClaimSummaryOutput.model_config["extra"] == "forbid"
