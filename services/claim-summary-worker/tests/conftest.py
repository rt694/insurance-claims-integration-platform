from collections.abc import Iterator
from datetime import UTC, datetime
from decimal import Decimal
from typing import Any
from uuid import UUID

import pytest
from fastapi.testclient import TestClient

from claim_summary_worker.api import create_app
from claim_summary_worker.config import WorkerSettings

CLAIM_ID = UUID("63eb8ca7-dc07-4a86-978a-e8a1b471650b")
EVENT_ID = UUID("8b050b8f-1e6f-4aca-b7d0-198d93cde18f")
OCCURRED_AT = datetime(2026, 3, 1, 12, 0, tzinfo=UTC)


@pytest.fixture
def claim_event() -> dict[str, Any]:
    return {
        "eventId": str(EVENT_ID),
        "eventType": "claim.submitted",
        "eventVersion": 1,
        "aggregateType": "claim",
        "aggregateId": str(CLAIM_ID),
        "correlationId": "worker-test-correlation",
        "occurredAt": OCCURRED_AT.isoformat().replace("+00:00", "Z"),
        "data": {
            "claimId": str(CLAIM_ID),
            "claimType": "AUTO",
            "incidentDate": "2026-01-10",
            "description": "Synthetic vehicle damage for worker testing",
            "estimatedLoss": str(Decimal("1250.00")),
            "status": "SUBMITTED",
            "submittedAt": OCCURRED_AT.isoformat().replace("+00:00", "Z"),
        },
    }


@pytest.fixture
def client() -> Iterator[TestClient]:
    app = create_app(WorkerSettings())
    with TestClient(app) as test_client:
        yield test_client
