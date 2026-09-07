from typing import Protocol


class RuntimeComponent(Protocol):
    @property
    def is_ready(self) -> bool: ...

    async def start(self) -> None: ...

    async def close(self) -> None: ...


class WorkerRuntime:
    """Starts publication before consumption and reverses that order on shutdown."""

    def __init__(self, publisher: RuntimeComponent, consumer: RuntimeComponent) -> None:
        self._publisher = publisher
        self._consumer = consumer

    @property
    def is_ready(self) -> bool:
        return self._publisher.is_ready and self._consumer.is_ready

    async def start(self) -> None:
        await self._publisher.start()
        try:
            await self._consumer.start()
        except BaseException:
            await self._publisher.close()
            raise

    async def close(self) -> None:
        try:
            await self._consumer.close()
        finally:
            await self._publisher.close()
