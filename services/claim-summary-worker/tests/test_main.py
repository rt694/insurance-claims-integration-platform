from secrets import token_urlsafe

import pytest
from pydantic import SecretStr

import claim_summary_worker.main as main_module
from claim_summary_worker.config import SummaryProviderType, WorkerSettings
from claim_summary_worker.providers import MockSummaryProvider, OpenAISummaryProvider
from claim_summary_worker.runtime import WorkerRuntime


def test_build_app_leaves_runtime_unwired_when_broker_is_disabled(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setattr(main_module, "get_settings", WorkerSettings)

    app = main_module.build_app()

    assert app.state.runtime is None


def test_build_app_wires_mock_provider_pipeline_when_broker_is_enabled(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    settings = WorkerSettings(
        rabbitmq_enabled=True,
        rabbitmq_password=SecretStr(token_urlsafe()),
    )
    monkeypatch.setattr(main_module, "get_settings", lambda: settings)

    app = main_module.build_app()

    assert isinstance(app.state.runtime, WorkerRuntime)


def test_provider_factory_keeps_mock_as_default() -> None:
    provider = main_module.build_summary_provider(WorkerSettings())

    assert isinstance(provider, MockSummaryProvider)


def test_provider_factory_builds_openai_adapter_only_when_selected() -> None:
    settings = WorkerSettings(
        summary_provider=SummaryProviderType.OPENAI,
        openai_api_key=SecretStr(token_urlsafe()),
    )

    provider = main_module.build_summary_provider(settings)

    assert isinstance(provider, OpenAISummaryProvider)
