import pytest
from pydantic import ValidationError

from claim_summary_worker.config import WorkerSettings


def test_settings_use_safe_local_defaults() -> None:
    settings = WorkerSettings()

    assert settings.environment == "local"
    assert settings.max_event_bytes == 65_536


def test_event_size_limit_cannot_be_disabled() -> None:
    with pytest.raises(ValidationError):
        WorkerSettings(max_event_bytes=0)
