import os
from pathlib import Path
import subprocess
import sys

import pytest
from pydantic import ValidationError

from app.config import DEVELOPMENT_SECRET, Settings
from app.database import Database


@pytest.mark.parametrize("prefix", ["postgres://", "postgresql://", "postgresql+psycopg://"])
def test_postgresql_uses_psycopg(monkeypatch, prefix):
    monkeypatch.setenv("DATABASE_URL", prefix + "user:example@localhost/test_db")
    settings = Settings(_env_file=None)
    assert settings.database_url == "postgresql+psycopg://user:example@localhost/test_db"
    database = Database(settings.database_url)
    try:
        assert database.engine.dialect.driver == "psycopg"
    finally:
        database.engine.dispose()


@pytest.mark.parametrize("secret", [DEVELOPMENT_SECRET, "", "   "])
def test_production_rejects_placeholder(monkeypatch, secret):
    monkeypatch.setenv("ENV", "production")
    monkeypatch.setenv("JWT_SECRET_KEY", secret)
    with pytest.raises(ValidationError, match="Production requires"):
        Settings(_env_file=None)


def test_production_rejects_default_secret(monkeypatch):
    monkeypatch.setenv("ENV", "production")
    with pytest.raises(ValidationError, match="Production requires"):
        Settings(_env_file=None)


def test_explicit_production_configuration():
    settings = Settings(_env_file=None, env="production", jwt_secret_key="example-test-value-not-a-real-secret")
    assert settings.env == "production"


def test_default_development_database_is_sqlite():
    assert Settings(_env_file=None).database_url.startswith("sqlite:///")


def test_import_and_test_app_ignore_invalid_dotenv(tmp_path):
    bad_env = tmp_path / ".env"
    bad_env.write_text("ENV=production\nDATABASE_URL=invalid\nJWT_SECRET_KEY=development-only-change-me\n", encoding="utf-8")
    environment = os.environ.copy()
    environment["PYTHONPATH"] = str(Path(__file__).resolve().parents[1])
    # Force the Settings default to the invalid file before importing app.main.
    script = """
from app.config import Settings
Settings.model_config['env_file'] = '.env'
from app.main import app, create_app
from fastapi.testclient import TestClient
settings = Settings(_env_file=None, env='test', database_url='sqlite:///:memory:',
                    jwt_secret_key='test', jwt_algorithm='HS256',
                    football_data_api_key='', football_data_mode='demo')
with TestClient(create_app(settings)) as client:
    assert client.get('/api/health').status_code == 200
"""
    result = subprocess.run([sys.executable, "-c", script], cwd=tmp_path, env=environment,
                            capture_output=True, text=True, timeout=30)
    assert result.returncode == 0, result.stderr
    assert not list(tmp_path.glob("*.db"))
