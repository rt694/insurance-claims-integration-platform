from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from typing import Protocol, TypedDict

from fastapi import FastAPI, Request, Response
from prometheus_client import CONTENT_TYPE_LATEST, generate_latest

from claim_summary_worker.config import WorkerSettings, get_settings
from claim_summary_worker.metrics import WorkerMetrics
from claim_summary_worker.safety import SYSTEM_PROMPT


class HealthResponse(TypedDict):
    status: str
    service: str


class WorkerRuntimeLifecycle(Protocol):
    @property
    def is_ready(self) -> bool: ...

    async def start(self) -> None: ...

    async def close(self) -> None: ...


def create_app(
    settings: WorkerSettings | None = None,
    runtime: WorkerRuntimeLifecycle | None = None,
    metrics: WorkerMetrics | None = None,
) -> FastAPI:
    resolved_settings = settings or get_settings()
    resolved_metrics = metrics or WorkerMetrics()

    @asynccontextmanager
    async def lifespan(app: FastAPI) -> AsyncIterator[None]:
        app.state.policy_ready = bool(SYSTEM_PROMPT.strip())
        runtime_started = False
        if resolved_settings.rabbitmq_enabled and runtime is not None:
            await runtime.start()
            runtime_started = True
        try:
            yield
        finally:
            app.state.policy_ready = False
            if runtime is not None and runtime_started:
                await runtime.close()

    app = FastAPI(
        title="Claim Summary Worker",
        version="0.1.0",
        description="Human reviewer assistance for synthetic insurance claims",
        lifespan=lifespan,
    )
    app.state.settings = resolved_settings
    app.state.policy_ready = False
    app.state.runtime = runtime
    app.state.metrics = resolved_metrics

    @app.get("/health/live", tags=["health"])
    async def liveness(request: Request) -> HealthResponse:
        worker_settings: WorkerSettings = request.app.state.settings
        return {"status": "UP", "service": worker_settings.service_name}

    @app.get("/health/ready", tags=["health"])
    async def readiness(request: Request, response: Response) -> HealthResponse:
        worker_settings: WorkerSettings = request.app.state.settings
        configured_runtime: WorkerRuntimeLifecycle | None = request.app.state.runtime
        broker_ready = not worker_settings.rabbitmq_enabled or (
            configured_runtime is not None and configured_runtime.is_ready
        )
        ready = request.app.state.policy_ready and broker_ready
        if not ready:
            response.status_code = 503
        return {
            "status": "UP" if ready else "DOWN",
            "service": worker_settings.service_name,
        }

    @app.get("/metrics", include_in_schema=False)
    async def metrics_endpoint(request: Request) -> Response:
        worker_metrics: WorkerMetrics = request.app.state.metrics
        return Response(
            content=generate_latest(worker_metrics.registry),
            headers={"Content-Type": CONTENT_TYPE_LATEST},
        )

    return app
