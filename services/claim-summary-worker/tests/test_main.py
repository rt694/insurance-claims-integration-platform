from secrets import token_urlsafe

import pytest
from pydantic import SecretStr

import claim_summary_worker.main as main_module
from claim_summary_worker.config import WorkerSettings
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
