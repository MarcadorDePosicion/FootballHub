from pathlib import Path
import re
from urllib.parse import urlsplit, urlunsplit
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
    football_data_base_url: str = "https://api.football-data.org/v4"
    football_data_competitions: str = "PL"
    football_data_cache_ttl_seconds: int = Field(default=120, gt=0)
    football_data_max_requests_per_minute: int = Field(default=10, gt=0)
    football_data_days_back: int = Field(default=1, ge=0, le=365)
    football_data_days_forward: int = Field(default=1, ge=0, le=365)

    @field_validator("football_data_base_url")
    @classmethod
    def normalize_base_url(cls, value: str) -> str:
        parts = urlsplit(value.strip())
        if parts.scheme != "https" or not parts.hostname or parts.username or parts.password or parts.query or parts.fragment:
            raise ValueError("Football base URL must be HTTPS without credentials, query or fragment")
        return urlunsplit((parts.scheme, parts.netloc, re.sub(r"/+", "/", parts.path).rstrip("/"), "", ""))

    @field_validator("football_data_competitions")
    @classmethod
    def normalize_competitions(cls, value: str) -> str:
        codes = [code.strip().upper() for code in value.split(",")]
        if not all(re.fullmatch(r"[A-Z0-9]+", code) for code in codes):
            raise ValueError("Competition codes must be nonempty alphanumeric values")
        return ",".join(dict.fromkeys(codes))

    @model_validator(mode="after")
    def require_live_key(self) -> "Settings":
        if self.football_data_mode == "live" and not self.football_data_api_key.get_secret_value().strip():
            raise ValueError("Live football requires FOOTBALL_DATA_API_KEY")
        return self

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
