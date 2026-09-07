import asyncio

import pytest

from claim_summary_worker.runtime import WorkerRuntime


class RecordingComponent:
    def __init__(
        self,
        name: str,
        calls: list[str],
        *,
        ready: bool = True,
        start_error: Exception | None = None,
        close_error: Exception | None = None,
    ) -> None:
        self.name = name
        self.calls = calls
        self.is_ready = ready
        self.start_error = start_error
        self.close_error = close_error

    async def start(self) -> None:
        self.calls.append(f"start:{self.name}")
        if self.start_error is not None:
            raise self.start_error

    async def close(self) -> None:
        self.calls.append(f"close:{self.name}")
        if self.close_error is not None:
            raise self.close_error


def test_runtime_orders_startup_and_shutdown_for_delivery_safety() -> None:
    calls: list[str] = []
    runtime = WorkerRuntime(
        RecordingComponent("publisher", calls),
        RecordingComponent("consumer", calls),
    )

    asyncio.run(runtime.start())
    assert runtime.is_ready is True
    asyncio.run(runtime.close())

    assert calls == [
        "start:publisher",
        "start:consumer",
        "close:consumer",
        "close:publisher",
    ]


def test_runtime_closes_publisher_when_consumer_startup_fails() -> None:
    calls: list[str] = []
    runtime = WorkerRuntime(
        RecordingComponent("publisher", calls),
        RecordingComponent("consumer", calls, start_error=RuntimeError("consumer failed")),
    )

    with pytest.raises(RuntimeError, match="consumer failed"):
        asyncio.run(runtime.start())

    assert calls == ["start:publisher", "start:consumer", "close:publisher"]


def test_runtime_readiness_requires_both_components() -> None:
    runtime = WorkerRuntime(
        RecordingComponent("publisher", [], ready=True),
        RecordingComponent("consumer", [], ready=False),
    )

    assert runtime.is_ready is False


def test_runtime_closes_publisher_even_when_consumer_shutdown_fails() -> None:
    calls: list[str] = []
    runtime = WorkerRuntime(
        RecordingComponent("publisher", calls),
        RecordingComponent("consumer", calls, close_error=RuntimeError("close failed")),
    )

    with pytest.raises(RuntimeError, match="close failed"):
        asyncio.run(runtime.close())

    assert calls == ["close:consumer", "close:publisher"]
