from functools import lru_cache

from pydantic import Field, SecretStr, model_validator
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
    rabbitmq_enabled: bool = False
    rabbitmq_host: str = "localhost"
    rabbitmq_port: int = Field(default=5_672, ge=1, le=65_535)
    rabbitmq_username: str = "claims_app"
    rabbitmq_password: SecretStr | None = None
    rabbitmq_virtual_host: str = "/"
    rabbitmq_connection_timeout_seconds: float = Field(default=5.0, gt=0, le=60)
    rabbitmq_prefetch_count: int = Field(default=1, ge=1, le=1_000)
    rabbitmq_max_processing_attempts: int = Field(default=3, ge=1, le=10)

    @model_validator(mode="after")
    def require_broker_password_when_enabled(self) -> "WorkerSettings":
        if self.rabbitmq_enabled and self.rabbitmq_password is None:
            raise ValueError("RabbitMQ password is required when consumption is enabled")
        return self


@lru_cache
def get_settings() -> WorkerSettings:
    return WorkerSettings()
