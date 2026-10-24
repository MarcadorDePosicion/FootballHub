from datetime import datetime, timedelta, timezone

import bcrypt
import jwt
import pytest
from sqlalchemy import select

from app.database import get_db
from app.models import User

PASSWORD = "correct-password"


def register(client, email="User@Example.com", password=PASSWORD):
    return client.post("/api/auth/register", json={"email": email, "password": password})


def assert_profile(profile):
    assert set(profile) == {"id", "email", "createdAt"}
    assert isinstance(profile["id"], int) and profile["id"] > 0
    assert profile["createdAt"].endswith(("Z", "+00:00"))
    assert datetime.fromisoformat(profile["createdAt"].replace("Z", "+00:00")).utcoffset() == timedelta(0)


def assert_unauthorized(response):
    assert response.status_code == 401
    assert response.headers["WWW-Authenticate"] == "Bearer"
    assert response.json() == {"detail": "Invalid authentication credentials"}


def test_register_profile_and_bcrypt_storage(client, application):
    response = register(client, "  User@Example.com ")
    assert response.status_code == 201
    profile = response.json()
    assert_profile(profile)
    assert profile["email"] == "user@example.com"
    with application.state.database.SessionLocal() as db:
        user = db.get(User, profile["id"])
        assert user.password_hash != PASSWORD
        assert bcrypt.checkpw(PASSWORD.encode(), user.password_hash.encode())
        assert not bcrypt.checkpw(b"incorrect-password", user.password_hash.encode())
        assert user.email == "user@example.com"


def test_duplicate_normalized_email_rolls_back_same_session(client, application):
    # Reuse the same session across requests to detect a missing rollback.
    with application.state.database.SessionLocal() as db:
        def same_session():
            yield db
        application.dependency_overrides[get_db] = same_session
        assert register(client).status_code == 201
        assert register(client, "  user@example.com ").status_code == 409
        assert db.is_active
        assert register(client, "other@example.com").status_code == 201
        assert len(db.scalars(select(User)).all()) == 2
        assert client.get("/api/health").status_code == 200


@pytest.mark.parametrize("route", ["register", "login"])
@pytest.mark.parametrize("email,password", [
    ("not-an-email", PASSWORD), ("user@example.com", "short"),
    ("user@example.com", "a" * 73), ("user@example.com", "ą" * 37),
])
def test_invalid_credentials_are_422(client, route, email, password):
    response = client.post(f"/api/auth/{route}", json={"email": email, "password": password})
    assert response.status_code == 422


@pytest.mark.parametrize("password", ["a" * 72, "ą" * 36])
def test_72_byte_password_boundary(client, password):
    assert register(client, password=password).status_code == 201
    assert client.post("/api/auth/login", json={"email": "user@example.com", "password": password}).status_code == 200


def test_login_and_me_contract(client, settings):
    profile = register(client).json()
    response = client.post("/api/auth/login", json={"email": " USER@example.com ", "password": PASSWORD})
    assert response.status_code == 200
    body = response.json()
    assert set(body) == {"accessToken", "tokenType", "user"}
    assert body["tokenType"] == "bearer"
    assert_profile(body["user"])
    assert body["user"] == profile
    claims = jwt.decode(body["accessToken"], settings.jwt_secret_key.get_secret_value(), algorithms=["HS256"])
    assert claims["sub"] == str(profile["id"])
    assert claims["exp"] - claims["iat"] == 60 * settings.jwt_access_token_expire_minutes
    me = client.get("/api/auth/me", headers={"Authorization": f"Bearer {body['accessToken']}"})
    assert me.status_code == 200
    assert me.json() == profile
    assert_profile(me.json())


def test_wrong_password_and_unknown_email_are_indistinguishable(client):
    register(client)
    for email, password in [("user@example.com", "wrong-password"), ("absent@example.com", PASSWORD)]:
        assert_unauthorized(client.post("/api/auth/login", json={"email": email, "password": password}))


@pytest.mark.parametrize("header", [None, "Basic abc", "Bearer", "Bearer ", "Bearer invalid", "Bearer a b"])
def test_missing_or_malformed_authorization(client, header):
    assert_unauthorized(client.get("/api/auth/me", headers={} if header is None else {"Authorization": header}))


@pytest.mark.parametrize("case", ["expired", "missing-sub", "missing-iat", "missing-exp", "wrong-key", "missing-user",
                                  "sub-text", "sub-zero", "sub-negative", "sub-number", "sub-huge", "sub-space", "sub-decimal",
                                  "algorithm-none", "algorithm-HS384", "future-iat", "iat-list", "exp-list", "sub-list"])
def test_invalid_signed_tokens(client, settings, case):
    now = datetime.now(timezone.utc)
    claims = {"sub": "999", "iat": now, "exp": now + timedelta(minutes=5)}
    key = settings.jwt_secret_key.get_secret_value()
    algorithm = "HS256"
    if case == "expired":
        claims.update(iat=now - timedelta(minutes=10), exp=now - timedelta(minutes=5))
    elif case.startswith("missing-") and case != "missing-user":
        del claims[case.removeprefix("missing-")]
    elif case.startswith("sub-"):
        claims["sub"] = {"sub-text": "abc", "sub-zero": "0", "sub-negative": "-1", "sub-number": 1,
                         "sub-huge": "99999999999999999999999999999999", "sub-space": " 1", "sub-decimal": "1.5", "sub-list": []}[case]
    elif case in ("iat-list", "exp-list"):
        claims[case.split("-")[0]] = []
    elif case == "wrong-key":
        key = "different-test-key-not-real-secret-at-least-32"
    elif case == "algorithm-none":
        algorithm, key = "none", ""
    elif case == "algorithm-HS384":
        algorithm = "HS384"
        key = "different-test-key-for-HS384-at-least-48-bytes-long"
    elif case == "future-iat":
        claims["iat"] = now + timedelta(minutes=1)
    token = jwt.encode(claims, key, algorithm=algorithm)
    assert_unauthorized(client.get("/api/auth/me", headers={"Authorization": f"Bearer {token}"}))


def test_new_application_database_is_empty(client, application):
    with application.state.database.SessionLocal() as db:
        assert db.scalars(select(User)).all() == []
