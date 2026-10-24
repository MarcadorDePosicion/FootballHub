from datetime import datetime, timezone
from typing import Literal

from pydantic import BaseModel, ConfigDict, EmailStr, Field, field_validator, model_validator
from pydantic.alias_generators import to_camel


class ApiModel(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True, from_attributes=True)


class Credentials(ApiModel):
    email: EmailStr
    password: str = Field(min_length=8, repr=False)

    @field_validator("email", mode="before")
    @classmethod
    def normalize_email(cls, value):
        return value.strip().lower() if isinstance(value, str) else value

    @field_validator("password")
    @classmethod
    def validate_password_bytes(cls, value: str) -> str:
        if len(value.encode("utf-8")) > 72:
            raise ValueError("Password must not exceed 72 UTF-8 bytes")
        return value


class UserProfile(ApiModel):
    id: int
    email: EmailStr
    created_at: datetime

    @field_validator("created_at")
    @classmethod
    def normalize_utc(cls, value: datetime) -> datetime:
        if value.tzinfo is None:
            return value.replace(tzinfo=timezone.utc)
        return value.astimezone(timezone.utc)


class LoginResponse(ApiModel):
    access_token: str
    token_type: Literal["bearer"] = "bearer"
    user: UserProfile


class Favorites(ApiModel):
    team_ids: list[int]
    player_ids: list[int]


class JournalEntry(ApiModel):
    id: str = Field(min_length=1)
    match_id: int = Field(strict=True, gt=0, le=2147483647)
    type: Literal["NOTE", "PREDICTION"]
    text: str
    predicted_home: int | None = Field(default=None, strict=True, ge=0, le=99)
    predicted_away: int | None = Field(default=None, strict=True, ge=0, le=99)
    updated_at: int = Field(strict=True, gt=0, le=9223372036854775807)

    @model_validator(mode="before")
    @classmethod
    def clear_unused_scores(cls, value):
        if isinstance(value, dict) and value.get("type") == "NOTE":
            value = dict(value)
            for field in ("predictedHome", "predictedAway", "predicted_home", "predicted_away"):
                value.pop(field, None)
        return value

    @field_validator("id", mode="before")
    @classmethod
    def trim_strings(cls, value):
        return value.strip() if isinstance(value, str) else value

    @model_validator(mode="after")
    def validate_entry(self):
        # Kotlin String.length counts UTF-16 code units, including surrogate pairs.
        if len(self.text.encode("utf-16-le")) // 2 > 2000:
            raise ValueError("Text must not exceed 2000 characters")
        if self.type == "NOTE":
            if not self.text.strip():
                raise ValueError("Note text must not be blank")
            self.predicted_home = None
            self.predicted_away = None
        elif self.predicted_home is None or self.predicted_away is None:
            raise ValueError("Prediction requires both scores from 0 to 99")
        # Android preserves optional prediction comments, including empty text.
        self.text = self.text.strip()
        return self
