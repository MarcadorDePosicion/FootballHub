from datetime import datetime, timezone

from sqlalchemy import BigInteger, DateTime, ForeignKey, Integer, String
from sqlalchemy.orm import Mapped, mapped_column, validates

from .database import Base


class User(Base):
    __tablename__ = "users"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    email: Mapped[str] = mapped_column(String(320), unique=True, index=True)
    password_hash: Mapped[str] = mapped_column(String(128))
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=lambda: datetime.now(timezone.utc)
    )

    @validates("email")
    def normalize_email(self, key: str, value: str) -> str:
        return value.strip().lower()


class JournalEntryModel(Base):
    __tablename__ = "entries"

    user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), primary_key=True)
    entry_id: Mapped[str] = mapped_column(String, primary_key=True)
    match_id: Mapped[int] = mapped_column(Integer)
    type: Mapped[str] = mapped_column(String)
    text: Mapped[str] = mapped_column(String)
    predicted_home: Mapped[int | None] = mapped_column(Integer, nullable=True)
    predicted_away: Mapped[int | None] = mapped_column(Integer, nullable=True)
    updated_at: Mapped[int] = mapped_column(BigInteger)


class FavoriteTeamModel(Base):
    __tablename__ = "favorite_teams"

    user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), primary_key=True)
    team_id: Mapped[int] = mapped_column(Integer, primary_key=True)


class FavoritePlayerModel(Base):
    __tablename__ = "favorite_players"

    user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), primary_key=True)
    player_id: Mapped[int] = mapped_column(Integer, primary_key=True)
