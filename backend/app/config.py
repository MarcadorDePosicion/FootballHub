from pathlib import Path
from typing import Literal

from pydantic import Field, SecretStr, field_validator, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

BACKEND_DIR = Path(__file__).resolve().parents[1]
DEVELOPMENT_SECRET = "development-only-change-me"


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=BACKEND_DIR / ".env", env_file_encoding="utf-8", extra="ignore"
    )

    env: Literal["development", "test", "production"] = "development"
    database_url: str = f"sqlite:///{(BACKEND_DIR / 'footballhub.db').as_posix()}"
    jwt_secret_key: SecretStr = SecretStr(DEVELOPMENT_SECRET)
    jwt_algorithm: Literal["HS256"] = "HS256"
    jwt_access_token_expire_minutes: int = Field(default=60, gt=0)
    football_data_api_key: SecretStr = SecretStr("")
    football_data_mode: Literal["demo", "live"] = "demo"

    @field_validator("database_url")
    @classmethod
    def normalize_database_url(cls, value: str) -> str:
        for prefix in ("postgresql://", "postgres://"):
            if value.startswith(prefix):
                return "postgresql+psycopg://" + value[len(prefix):]
        return value

    @model_validator(mode="after")
    def reject_production_placeholder(self) -> "Settings":
        secret = self.jwt_secret_key.get_secret_value().strip()
        if self.env == "production" and (not secret or secret == DEVELOPMENT_SECRET):
            raise ValueError("Production requires a non-placeholder JWT_SECRET_KEY")
        if self.env == "production" and len(secret.encode("utf-8")) < 32:
            raise ValueError("Production requires JWT_SECRET_KEY of at least 32 bytes")
        return self
