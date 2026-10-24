import pytest
from fastapi.testclient import TestClient

from app.config import Settings
from app.main import create_app


@pytest.fixture(autouse=True)
def controlled_environment(monkeypatch):
    for name in Settings.model_fields:
        monkeypatch.delenv(name, raising=False)
        monkeypatch.delenv(name.upper(), raising=False)


@pytest.fixture
def settings():
    return Settings(
        _env_file=None,
        env="test",
        database_url="sqlite:///:memory:",
        jwt_secret_key="test-only-placeholder-at-least-32-bytes",
        jwt_algorithm="HS256",
        jwt_access_token_expire_minutes=60,
        football_data_api_key="",
        football_data_mode="demo",
    )


@pytest.fixture
def application(settings):
    instance = create_app(settings)
    yield instance
    instance.dependency_overrides.clear()


@pytest.fixture
def client(application):
    with TestClient(application) as instance:
        yield instance


@pytest.fixture
def accounts(client, application, settings):
    from app.auth import create_access_token
    from app.models import User

    result = []
    with application.state.database.SessionLocal() as db:
        for email in ("a@example.com", "b@example.com"):
            user = User(email=email, password_hash="unused-in-token-tests")
            db.add(user)
            db.commit()
            result.append((user.id, {"Authorization": f"Bearer {create_access_token(user.id, settings)}"}))
    return result
