from fastapi import FastAPI

from claim_summary_worker.api import create_app
from claim_summary_worker.config import get_settings
from claim_summary_worker.messaging import RabbitMqConsumer, RabbitMqResultPublisher
from claim_summary_worker.processing import ClaimSummaryProcessor
from claim_summary_worker.providers import MockSummaryProvider
from claim_summary_worker.runtime import WorkerRuntime


def build_app() -> FastAPI:
    settings = get_settings()
    if not settings.rabbitmq_enabled:
        return create_app(settings)

    publisher = RabbitMqResultPublisher(settings)
    processor = ClaimSummaryProcessor(MockSummaryProvider(), publisher)
    consumer = RabbitMqConsumer(settings, processor)
    return create_app(settings, WorkerRuntime(publisher, consumer))


app = build_app()
