from fastapi import FastAPI

from claim_summary_worker.api import create_app
from claim_summary_worker.config import SummaryProviderType, WorkerSettings, get_settings
from claim_summary_worker.messaging import RabbitMqConsumer, RabbitMqResultPublisher
from claim_summary_worker.metrics import WorkerMetrics
from claim_summary_worker.processing import ClaimSummaryProcessor, SummaryProvider
from claim_summary_worker.providers import MockSummaryProvider, OpenAISummaryProvider
from claim_summary_worker.runtime import WorkerRuntime


def build_summary_provider(settings: WorkerSettings) -> SummaryProvider:
    if settings.summary_provider is SummaryProviderType.MOCK:
        return MockSummaryProvider()

    if settings.openai_api_key is None:  # pragma: no cover - guarded by settings validation
        raise ValueError("OpenAI API key is required when the OpenAI provider is selected")
    return OpenAISummaryProvider(
        api_key=settings.openai_api_key,
        model=settings.openai_model,
        timeout_seconds=settings.openai_timeout_seconds,
        max_retries=settings.openai_max_retries,
        max_output_tokens=settings.openai_max_output_tokens,
    )


def build_app() -> FastAPI:
    settings = get_settings()
    metrics = WorkerMetrics()
    if not settings.rabbitmq_enabled:
        return create_app(settings, metrics=metrics)

    publisher = RabbitMqResultPublisher(settings)
    processor = ClaimSummaryProcessor(build_summary_provider(settings), publisher, metrics=metrics)
    consumer = RabbitMqConsumer(settings, processor)
    return create_app(settings, WorkerRuntime(publisher, consumer), metrics)


app = build_app()
