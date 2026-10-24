import pytest
from sqlalchemy import select

from app.models import FavoriteTeamModel, FavoritePlayerModel
from app.database import get_db


@pytest.mark.parametrize("method,path", [
    ("get", "/api/favorites"), ("put", "/api/favorites/teams/1"),
    ("delete", "/api/favorites/teams/1"), ("put", "/api/favorites/players/1"),
    ("delete", "/api/favorites/players/1"),
])
@pytest.mark.parametrize("headers", [{}, {"Authorization": "Bearer invalid"}])
def test_favorites_require_token(client, method, path, headers):
    response = client.request(method, path, headers=headers)
    assert response.status_code == 401
    assert response.headers["www-authenticate"] == "Bearer"


def test_favorites_cycle_sorted_and_isolated(client, accounts, application):
    a_id, a = accounts[0]
    _, b = accounts[1]
    empty = {"teamIds": [], "playerIds": []}
    assert client.get("/api/favorites", headers=a).json() == empty


    expected = {"teamIds": [], "playerIds": []}
    for category, key in (("teams", "teamIds"), ("players", "playerIds")):
        for item_id in (9, 2, 5):
            expected[key] = sorted(expected[key] + [item_id])
            for _ in range(2):
                response = client.put(f"/api/favorites/{category}/{item_id}", headers=a)
                assert response.status_code == 200
                assert response.json() == expected
        assert client.get("/api/favorites", headers=b).json() == empty
        response = client.put(f"/api/favorites/{category}/9", headers=b, json={"userId": a_id})
        assert response.status_code == 200
        assert client.get("/api/favorites", headers=a).json() == expected
        client.delete(f"/api/favorites/{category}/9", headers=b)
        assert client.get("/api/favorites", headers=a).json() == expected
    with application.state.database.SessionLocal() as db:
        for model in (FavoriteTeamModel, FavoritePlayerModel):
            assert len(list(db.scalars(select(model)))) == 3
    for category, key in (("teams", "teamIds"), ("players", "playerIds")):
        for item_id in (9, 2, 5):
            expected[key].remove(item_id)
            for _ in range(2):
                response = client.delete(f"/api/favorites/{category}/{item_id}", headers=a)
                assert response.status_code == 200
                assert response.json() == expected
    assert client.get("/api/favorites", headers=a).json() == empty


@pytest.mark.parametrize("category", ["teams", "players"])
def test_duplicate_rollback_keeps_same_session_usable(client, accounts, application, category):
    with application.state.database.SessionLocal() as db:
        application.dependency_overrides[get_db] = lambda: db
        for item_id in (1, 1, 2):
            response = client.put(f"/api/favorites/{category}/{item_id}", headers=accounts[0][1])
            assert response.status_code == 200
        expected = {"teamIds": [], "playerIds": []}
        expected["teamIds" if category == "teams" else "playerIds"] = [1, 2]
        assert client.get("/api/favorites", headers=accounts[0][1]).json() == expected


@pytest.mark.parametrize("category", ["teams", "players"])
@pytest.mark.parametrize("item_id", [0, -1, "abc", 2147483648])
@pytest.mark.parametrize("method", ["put", "delete"])
def test_favorites_invalid_ids(client, accounts, category, item_id, method):
    response = client.request(method, f"/api/favorites/{category}/{item_id}", headers=accounts[0][1])
    assert response.status_code == 422
    assert client.get("/api/favorites", headers=accounts[0][1]).json() == {"teamIds": [], "playerIds": []}
