from prometheus_client import (
    CollectorRegistry,
    Counter,
    GCCollector,
    Histogram,
    PlatformCollector,
    ProcessCollector,
)


class WorkerMetrics:
    """Low-cardinality metrics for aggregate worker behavior."""

    def __init__(self, registry: CollectorRegistry | None = None) -> None:
        self.registry = registry or CollectorRegistry()
        GCCollector(registry=self.registry)
        PlatformCollector(registry=self.registry)
        ProcessCollector(registry=self.registry)
        self._processed = Counter(
            "insurance_worker_claim_summaries_total",
            "Claim summary processing attempts",
            ("outcome",),
            registry=self.registry,
        )
        self._duration = Histogram(
            "insurance_worker_claim_summary_duration_seconds",
            "Time spent generating and publishing a claim summary",
            registry=self.registry,
        )

    def record_processing(self, outcome: str, duration_seconds: float) -> None:
        self._processed.labels(outcome=outcome).inc()
        self._duration.observe(duration_seconds)
