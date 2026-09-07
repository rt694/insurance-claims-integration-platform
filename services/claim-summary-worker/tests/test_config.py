import pytest
from pydantic import ValidationError

from claim_summary_worker.config import SummaryProviderType, WorkerSettings


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


def test_mock_summary_provider_is_the_safe_default() -> None:
    settings = WorkerSettings()

    assert settings.summary_provider is SummaryProviderType.MOCK
    assert settings.openai_api_key is None


def test_openai_provider_requires_api_key() -> None:
    with pytest.raises(ValidationError, match="OpenAI API key is required"):
        WorkerSettings(summary_provider=SummaryProviderType.OPENAI)


def test_openai_api_key_can_be_loaded_from_standard_environment_name(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setenv("OPENAI_API_KEY", "environment-only-test-key")
    settings = WorkerSettings(summary_provider=SummaryProviderType.OPENAI)

    assert settings.openai_api_key is not None
    assert settings.openai_api_key.get_secret_value() == "environment-only-test-key"
    assert "environment-only-test-key" not in repr(settings)
