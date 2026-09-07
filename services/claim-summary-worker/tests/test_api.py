from fastapi.testclient import TestClient


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
