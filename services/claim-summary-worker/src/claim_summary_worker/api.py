from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from typing import TypedDict

from fastapi import FastAPI, Request

from claim_summary_worker.config import WorkerSettings, get_settings
from claim_summary_worker.safety import SYSTEM_PROMPT


class HealthResponse(TypedDict):
    status: str
    service: str


def create_app(settings: WorkerSettings | None = None) -> FastAPI:
    resolved_settings = settings or get_settings()

    @asynccontextmanager
    async def lifespan(app: FastAPI) -> AsyncIterator[None]:
        app.state.ready = bool(SYSTEM_PROMPT.strip())
        yield
        app.state.ready = False

    app = FastAPI(
        title="Claim Summary Worker",
        version="0.1.0",
        description="Human reviewer assistance for synthetic insurance claims",
        lifespan=lifespan,
    )
    app.state.settings = resolved_settings
    app.state.ready = False

    @app.get("/health/live", tags=["health"])
    async def liveness(request: Request) -> HealthResponse:
        worker_settings: WorkerSettings = request.app.state.settings
        return {"status": "UP", "service": worker_settings.service_name}

    @app.get("/health/ready", tags=["health"])
    async def readiness(request: Request) -> HealthResponse:
        worker_settings: WorkerSettings = request.app.state.settings
        return {
            "status": "UP" if request.app.state.ready else "DOWN",
            "service": worker_settings.service_name,
        }

    return app
