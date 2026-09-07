from functools import lru_cache

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class WorkerSettings(BaseSettings):
    """Environment-backed settings with safe local defaults."""

    model_config = SettingsConfigDict(
        env_prefix="CLAIM_SUMMARY_WORKER_",
        extra="ignore",
        frozen=True,
    )

    service_name: str = "claim-summary-worker"
    environment: str = "local"
    max_event_bytes: int = Field(default=65_536, ge=1_024, le=1_048_576)


@lru_cache
def get_settings() -> WorkerSettings:
    return WorkerSettings()
