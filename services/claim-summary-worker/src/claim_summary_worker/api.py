from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from typing import Protocol, TypedDict

from fastapi import FastAPI, Request

from claim_summary_worker.config import WorkerSettings, get_settings
from claim_summary_worker.safety import SYSTEM_PROMPT


class HealthResponse(TypedDict):
    status: str
    service: str


class ConsumerLifecycle(Protocol):
    @property
    def is_ready(self) -> bool: ...

    async def start(self) -> None: ...

    async def close(self) -> None: ...


def create_app(
    settings: WorkerSettings | None = None,
    consumer: ConsumerLifecycle | None = None,
) -> FastAPI:
    resolved_settings = settings or get_settings()

    @asynccontextmanager
    async def lifespan(app: FastAPI) -> AsyncIterator[None]:
        app.state.policy_ready = bool(SYSTEM_PROMPT.strip())
        consumer_started = False
        if resolved_settings.rabbitmq_enabled and consumer is not None:
            await consumer.start()
            consumer_started = True
        try:
            yield
        finally:
            app.state.policy_ready = False
            if consumer is not None and consumer_started:
                await consumer.close()

    app = FastAPI(
        title="Claim Summary Worker",
        version="0.1.0",
        description="Human reviewer assistance for synthetic insurance claims",
        lifespan=lifespan,
    )
    app.state.settings = resolved_settings
    app.state.policy_ready = False
    app.state.consumer = consumer

    @app.get("/health/live", tags=["health"])
    async def liveness(request: Request) -> HealthResponse:
        worker_settings: WorkerSettings = request.app.state.settings
        return {"status": "UP", "service": worker_settings.service_name}

    @app.get("/health/ready", tags=["health"])
    async def readiness(request: Request) -> HealthResponse:
        worker_settings: WorkerSettings = request.app.state.settings
        configured_consumer: ConsumerLifecycle | None = request.app.state.consumer
        broker_ready = not worker_settings.rabbitmq_enabled or (
            configured_consumer is not None and configured_consumer.is_ready
        )
        return {
            "status": "UP" if request.app.state.policy_ready and broker_ready else "DOWN",
            "service": worker_settings.service_name,
        }

    return app
