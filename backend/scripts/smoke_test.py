"""Run through stdin in the isolated Compose backend; creates disposable users.

Only use against footballhub-2e-test, whose volume is removed after the test.
Uses production dependencies and real HTTP/PostgreSQL, not pytest or mocks.
"""
import json
import uuid
from urllib.error import HTTPError
from urllib.request import Request, urlopen

from sqlalchemy import inspect, select
from sqlalchemy.exc import IntegrityError

from app.config import Settings
from app.database import Database
from app.models import FavoriteTeamModel, JournalEntryModel, User
from app.routers.favorites import add_favorite


def request(method, path, expected=200, body=None, token=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    req = Request("http://127.0.0.1:8000" + path,
                  data=None if body is None else json.dumps(body).encode(),
                  headers=headers, method=method)
    try:
        response = urlopen(req, timeout=10)
    except HTTPError as error:
        response = error
    with response:
        assert response.status == expected, (method, path, response.status, expected)
        raw = response.read()
    return json.loads(raw) if raw else None


def main():
    assert request("GET", "/api/health") == {"status": "ok", "database": "ok"}
    accounts = []
    for _ in range(2):
        credentials = {"email": f"smoke-{uuid.uuid4().hex}@example.com",
                       "password": uuid.uuid4().hex}
        profile = request("POST", "/api/auth/register", 201, credentials)
        request("POST", "/api/auth/register", 409, credentials)
        token = request("POST", "/api/auth/login", body=credentials)["accessToken"]
        assert request("GET", "/api/auth/me", token=token) == profile
        accounts.append((profile["id"], token))
    user_id, token = accounts[0]
    other_token = accounts[1][1]
    request("GET", "/api/entries", 401)
    print("PASS HTTP: health, register, duplicate, login, JWT, authorization")

    entry = dict(id="smoke", matchId=123, type="PREDICTION", text="  comment  ",
                 predictedHome=2, predictedAway=1, updatedAt=1700000000000)
    expected = entry | {"text": "comment"}
    for _ in range(2):
        assert request("PUT", "/api/entries/smoke", body=entry, token=token) == expected
    assert request("GET", "/api/entries", token=token) == [expected]
    assert request("GET", "/api/entries", token=other_token) == []
    request("DELETE", "/api/entries/smoke", 204, token=other_token)
    assert request("GET", "/api/entries", token=token) == [expected]
    request("PUT", "/api/entries/wrong", 422, entry, token)
    print("PASS HTTP: entry upsert, 64-bit timestamp, comment, ownership, ID validation")

    for _ in range(2):
        assert request("PUT", "/api/favorites/teams/1", token=token)["teamIds"] == [1]
        assert request("PUT", "/api/favorites/players/2", token=token)["playerIds"] == [2]
    request("DELETE", "/api/favorites/teams/1", token=other_token)
    assert request("GET", "/api/favorites", token=other_token) == {"teamIds": [], "playerIds": []}
    assert request("GET", "/api/favorites", token=token) == {"teamIds": [1], "playerIds": [2]}
    print("PASS HTTP: favorites, duplicates and ownership")

    database = Database(Settings().database_url)
    try:
        assert database.engine.dialect.name == "postgresql"
        schema = inspect(database.engine)
        columns = {c["name"]: c["type"] for c in schema.get_columns("entries")}
        assert str(columns["updated_at"]) == "BIGINT"
        user_columns = {c["name"]: c["type"] for c in schema.get_columns("users")}
        assert user_columns["created_at"].timezone
        assert set(schema.get_pk_constraint("entries")["constrained_columns"]) == {"user_id", "entry_id"}
        with database.SessionLocal() as db:
            assert db.get(JournalEntryModel, (user_id, "smoke")).updated_at == 1700000000000
            assert db.get(User, user_id).created_at.utcoffset() is not None
            # Real PostgreSQL IntegrityError, then rollback in production helper;
            # the same session must remain usable for another write and query.
            result = add_favorite(db, FavoriteTeamModel, FavoriteTeamModel.team_id, user_id, 1)
            assert result.team_ids == [1]
            result = add_favorite(db, FavoriteTeamModel, FavoriteTeamModel.team_id, user_id, 3)
            assert result.team_ids == [1, 3]
            db.add(FavoriteTeamModel(user_id=-1, team_id=99))
            try:
                db.commit()
            except IntegrityError:
                db.rollback()
            else:
                raise AssertionError("PostgreSQL did not enforce foreign key")
            assert db.scalar(select(User.id).where(User.id == user_id)) == user_id
        print("PASS PostgreSQL: schema, timezone, persistence, composite key, rollback, foreign key")
    finally:
        database.engine.dispose()

    for team_id in (1, 3):
        request("DELETE", f"/api/favorites/teams/{team_id}", token=token)
    request("DELETE", "/api/favorites/players/2", token=token)
    assert request("GET", "/api/favorites", token=token) == {"teamIds": [], "playerIds": []}
    request("DELETE", "/api/entries/smoke", 204, token=token)
    assert request("GET", "/api/entries", token=token) == []
    football = request("GET", "/api/football")
    assert set(football) == {"leagues", "teams", "players", "standings", "matches", "lineups", "matchEvents", "appearances"}
    assert all(isinstance(value, list) for value in football.values())
    assert football == request("GET", "/api/football")
    print("PASS HTTP: deletion and public deterministic football contract")
    print("SUCCESS: 5 smoke-check groups (4 HTTP, 1 direct PostgreSQL)")


if __name__ == "__main__":
    main()
