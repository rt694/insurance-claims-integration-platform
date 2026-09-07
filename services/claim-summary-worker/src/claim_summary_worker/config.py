from enum import StrEnum
from functools import lru_cache

from pydantic import AliasChoices, Field, SecretStr, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class SummaryProviderType(StrEnum):
    MOCK = "mock"
    OPENAI = "openai"


class WorkerSettings(BaseSettings):
    """Environment-backed settings with safe local defaults."""

    model_config = SettingsConfigDict(
        env_prefix="CLAIM_SUMMARY_WORKER_",
        extra="ignore",
        frozen=True,
        populate_by_name=True,
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
    summary_provider: SummaryProviderType = SummaryProviderType.MOCK
    openai_api_key: SecretStr | None = Field(
        default=None,
        validation_alias=AliasChoices(
            "OPENAI_API_KEY",
            "CLAIM_SUMMARY_WORKER_OPENAI_API_KEY",
        ),
    )
    openai_model: str = Field(default="gpt-6-astra", min_length=1, max_length=100)
    openai_timeout_seconds: float = Field(default=30.0, gt=0, le=120)
    openai_max_retries: int = Field(default=2, ge=0, le=5)
    openai_max_output_tokens: int = Field(default=1_000, ge=256, le=4_096)

    @model_validator(mode="after")
    def require_broker_password_when_enabled(self) -> "WorkerSettings":
        if self.rabbitmq_enabled and self.rabbitmq_password is None:
            raise ValueError("RabbitMQ password is required when consumption is enabled")
        if self.summary_provider is SummaryProviderType.OPENAI and self.openai_api_key is None:
            raise ValueError("OpenAI API key is required when the OpenAI provider is selected")
        return self


@lru_cache
def get_settings() -> WorkerSettings:
    return WorkerSettings()
