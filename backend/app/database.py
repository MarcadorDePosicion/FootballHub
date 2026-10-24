from collections.abc import Iterator

from fastapi import Request
from sqlalchemy import create_engine
from sqlalchemy.engine import make_url
from sqlalchemy.orm import DeclarativeBase, Session, sessionmaker
from sqlalchemy.pool import StaticPool


class Base(DeclarativeBase):
    pass


class Database:
    """Each application owns its engine and SessionLocal, including test apps."""

    def __init__(self, database_url: str):
        url = make_url(database_url)
        options = {}
        if url.get_backend_name() == "sqlite":
            options["connect_args"] = {"check_same_thread": False}
            if url.database in (None, "", ":memory:"):
                options["poolclass"] = StaticPool
        self.engine = create_engine(url, **options)
        self.SessionLocal = sessionmaker(bind=self.engine, autoflush=False)


def get_db(request: Request) -> Iterator[Session]:
    with request.app.state.database.SessionLocal() as session:
        yield session
