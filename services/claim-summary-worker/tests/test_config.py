import pytest
from pydantic import ValidationError

from claim_summary_worker.config import WorkerSettings


def test_settings_use_safe_local_defaults() -> None:
    settings = WorkerSettings()

    assert settings.environment == "local"
    assert settings.max_event_bytes == 65_536
    assert settings.rabbitmq_enabled is False
    assert settings.rabbitmq_password is None


def test_event_size_limit_cannot_be_disabled() -> None:
    with pytest.raises(ValidationError):
        WorkerSettings(max_event_bytes=0)


def test_enabled_consumer_requires_a_password() -> None:
    with pytest.raises(ValidationError, match="RabbitMQ password is required"):
        WorkerSettings(rabbitmq_enabled=True)
