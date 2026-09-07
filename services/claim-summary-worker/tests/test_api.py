from secrets import token_urlsafe

from fastapi.testclient import TestClient
from pydantic import SecretStr

from claim_summary_worker.api import create_app
from claim_summary_worker.config import WorkerSettings


class FakeConsumer:
    def __init__(self, ready: bool) -> None:
        self.is_ready = ready
        self.started = False
        self.closed = False

    async def start(self) -> None:
        self.started = True

    async def close(self) -> None:
        self.closed = True


def test_liveness_reports_the_service_is_running(client: TestClient) -> None:
    response = client.get("/health/live")

    assert response.status_code == 200
    assert response.json() == {"status": "UP", "service": "claim-summary-worker"}


def test_readiness_reports_initialization_is_complete(client: TestClient) -> None:
    response = client.get("/health/ready")

    assert response.status_code == 200
    assert response.json() == {"status": "UP", "service": "claim-summary-worker"}


def test_openapi_describes_both_operational_endpoints(client: TestClient) -> None:
    paths = client.get("/openapi.json").json()["paths"]

    assert "/health/live" in paths
    assert "/health/ready" in paths


def test_readiness_tracks_an_enabled_broker_consumer() -> None:
    consumer = FakeConsumer(ready=True)
    settings = WorkerSettings(rabbitmq_enabled=True, rabbitmq_password=SecretStr(token_urlsafe()))

    with TestClient(create_app(settings, consumer)) as broker_client:
        assert consumer.started is True
        assert broker_client.get("/health/ready").json()["status"] == "UP"

    assert consumer.closed is True


def test_readiness_is_down_when_enabled_consumer_is_not_connected() -> None:
    consumer = FakeConsumer(ready=False)
    settings = WorkerSettings(rabbitmq_enabled=True, rabbitmq_password=SecretStr(token_urlsafe()))

    with TestClient(create_app(settings, consumer)) as broker_client:
        assert broker_client.get("/health/ready").json()["status"] == "DOWN"


def test_readiness_is_down_when_enabled_consumer_is_not_wired() -> None:
    settings = WorkerSettings(rabbitmq_enabled=True, rabbitmq_password=SecretStr(token_urlsafe()))

    with TestClient(create_app(settings)) as broker_client:
        assert broker_client.get("/health/ready").json()["status"] == "DOWN"


def test_disabled_consumer_is_not_started_or_closed() -> None:
    consumer = FakeConsumer(ready=False)

    with TestClient(create_app(WorkerSettings(), consumer)) as broker_client:
        assert broker_client.get("/health/ready").json()["status"] == "UP"
        assert consumer.started is False

    assert consumer.closed is False
