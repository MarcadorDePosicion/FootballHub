import pytest
from sqlalchemy import delete, select

from app.models import JournalEntryModel, FavoriteTeamModel, FavoritePlayerModel, User


def note(**changes):
    return dict(id="entry", matchId=123, type="NOTE", text="comment",
                predictedHome=None, predictedAway=None, updatedAt=1700000000000) | changes


@pytest.mark.parametrize("method,path", [
    ("get", "/api/entries"), ("put", "/api/entries/entry"),
    ("delete", "/api/entries/entry"),
])
@pytest.mark.parametrize("headers", [{}, {"Authorization": "Bearer invalid"}])
def test_entries_require_token(client, method, path, headers):
    response = client.request(method, path, json=note(), headers=headers)
    assert response.status_code == 401
    assert response.headers["www-authenticate"] == "Bearer"


def test_crud_sorting_and_idempotence(client, accounts, application):
    user_id, headers = accounts[0]
    for entry_id, timestamp in [("z", 100), ("b", 200), ("a", 200)]:
        body = note(id=entry_id, updatedAt=timestamp)
        for _ in range(2):
            response = client.put(f"/api/entries/{entry_id}", json=body, headers=headers)
            assert response.status_code == 200
            assert response.json() == body
    entries = client.get("/api/entries", headers=headers).json()
    assert [entry["id"] for entry in entries] == ["a", "b", "z"]
    body = note(id="a", type="PREDICTION", text="  prediction comment  ",
                predictedHome=0, predictedAway=99, updatedAt=300)
    response = client.put("/api/entries/a", json=body, headers=headers)
    assert response.status_code == 200
    assert response.json() == body | {"text": "prediction comment"}
    assert client.get("/api/entries", headers=headers).json()[0] == response.json()
    with application.state.database.SessionLocal() as db:
        entry = db.get(JournalEntryModel, (user_id, "a"))
        assert (entry.text, entry.predicted_home, entry.predicted_away) == ("prediction comment", 0, 99)
        assert len(list(db.scalars(select(JournalEntryModel)))) == 3
    for _ in range(2):
        response = client.delete("/api/entries/a", headers=headers)
        assert response.status_code == 204
        assert response.content == b""
    assert [e["id"] for e in client.get("/api/entries", headers=headers).json()] == ["b", "z"]


def test_normalization_and_note_scores(client, accounts, application):
    user_id, headers = accounts[0]
    response = client.put("/api/entries/%20entry%20", headers=headers,
                          json=note(id=" entry ", text="  hello  ", predictedHome=2, predictedAway=1))
    assert response.status_code == 200
    assert response.json() == note(text="hello")
    with application.state.database.SessionLocal() as db:
        entry = db.get(JournalEntryModel, (user_id, "entry"))
        assert (entry.text, entry.predicted_home, entry.predicted_away) == ("hello", None, None)


@pytest.mark.parametrize("changes", [
    {"id": " "}, {"id": "different"}, {"matchId": 0}, {"matchId": -1},
    {"updatedAt": 0}, {"updatedAt": -1}, {"updatedAt": None},
    {"type": "OTHER"}, {"text": " "}, {"text": "x" * 2001},
    {"matchId": True}, {"updatedAt": 2**63}, {"matchId": 2**31},
    {"type": "PREDICTION", "predictedHome": -1, "predictedAway": 0},
    {"type": "PREDICTION", "predictedHome": 100, "predictedAway": 0},
    {"type": "PREDICTION", "predictedHome": 0, "predictedAway": 100},
    {"type": "PREDICTION", "predictedHome": 0},
    {"type": "PREDICTION", "predictedHome": True, "predictedAway": 1},
    {"type": "PREDICTION", "predictedHome": 1, "predictedAway": 2, "text": "x" * 2001},
])
def test_invalid_entry(client, accounts, changes):
    headers = accounts[0][1]
    assert client.put("/api/entries/entry", json=note(**changes), headers=headers).status_code == 422
    assert client.get("/api/entries", headers=headers).json() == []


@pytest.mark.parametrize("field", ["id", "matchId", "type", "text", "updatedAt"])
def test_missing_required_field(client, accounts, field):
    body = note()
    del body[field]
    assert client.put("/api/entries/entry", json=body, headers=accounts[0][1]).status_code == 422


@pytest.mark.parametrize("kind,text", [
    ("NOTE", "x" * 2000), ("PREDICTION", "x" * 2000),
    ("PREDICTION", ""), ("NOTE", "😀" * 1000),
], ids=["note-limit", "prediction-limit", "empty-prediction-comment", "utf16-limit"])
def test_text_boundaries(client, accounts, kind, text):
    body = note(type=kind, text=text, predictedHome=99, predictedAway=0)
    response = client.put("/api/entries/entry", json=body, headers=accounts[0][1])
    assert response.status_code == 200
    assert response.json()["text"] == text


def test_note_ignores_unused_scores(client, accounts):
    response = client.put("/api/entries/entry", headers=accounts[0][1],
                          json=note(predictedHome=-10, predictedAway="not a score"))
    assert response.status_code == 200
    assert response.json() == note()


@pytest.mark.parametrize("kind", ["NOTE", "PREDICTION"])
@pytest.mark.parametrize("content", ["x" * 2000, "😀" * 1000], ids=["ascii", "utf16"])
def test_original_text_over_limit_is_rejected_before_trim(client, accounts, kind, content):
    headers = accounts[0][1]
    body = note(type=kind, text=" " + content + " ", predictedHome=1, predictedAway=2)
    response = client.put("/api/entries/entry", json=body, headers=headers)
    assert response.status_code == 422
    assert client.get("/api/entries", headers=headers).json() == []


@pytest.mark.parametrize("kind", ["NOTE", "PREDICTION"])
@pytest.mark.parametrize("content", ["x" * 1998, "😀" * 999], ids=["ascii", "utf16"])
def test_original_text_at_limit_is_trimmed_and_preserved(client, accounts, application, kind, content):
    user_id, headers = accounts[0]
    body = note(type=kind, text=" " + content + " ", predictedHome=1, predictedAway=2)
    response = client.put("/api/entries/entry", json=body, headers=headers)
    assert response.status_code == 200
    expected = body | {"text": content}
    if kind == "NOTE":
        expected |= {"predictedHome": None, "predictedAway": None}
    assert response.json() == expected
    assert client.get("/api/entries", headers=headers).json() == [expected]
    with application.state.database.SessionLocal() as db:
        assert db.get(JournalEntryModel, (user_id, "entry")).text == content


def test_utf16_limit_and_empty_path(client, accounts):
    headers = accounts[0][1]
    assert client.put("/api/entries/entry", json=note(text="😀" * 1001), headers=headers).status_code == 422
    assert client.put("/api/entries/%20", json=note(), headers=headers).status_code == 422
    assert client.delete("/api/entries/%20", headers=headers).status_code == 422


def test_entry_ownership_cannot_be_overridden(client, accounts, application):
    a_id, a = accounts[0]
    b_id, b = accounts[1]
    assert client.put("/api/entries/entry", json=note(), headers=a).status_code == 200
    assert client.get("/api/entries", headers=b).json() == []
    assert client.delete("/api/entries/entry", headers=b).status_code == 204
    for owner_key in ("userId", "user_id"):
        body = note(text="B only") | {owner_key: a_id}
        assert client.put("/api/entries/entry", json=body, headers=b).status_code == 200
        assert client.get("/api/entries", headers=a).json() == [note()]
        assert client.get("/api/entries", headers=b).json() == [note(text="B only")]
    with application.state.database.SessionLocal() as db:
        assert db.get(JournalEntryModel, (a_id, "entry")).text == "comment"
        assert db.get(JournalEntryModel, (b_id, "entry")).text == "B only"
    client.delete("/api/entries/entry", headers=b)
    assert client.get("/api/entries", headers=a).json() == [note()]


def test_user_delete_cascades_only_owned_data(client, accounts, application):
    for _, headers in accounts:
        client.put("/api/entries/entry", json=note(), headers=headers)
        client.put("/api/favorites/teams/1", headers=headers)
        client.put("/api/favorites/players/1", headers=headers)
    with application.state.database.SessionLocal() as db:
        db.execute(delete(User).where(User.id == accounts[0][0]))
        db.commit()
        for model in (JournalEntryModel, FavoriteTeamModel, FavoritePlayerModel):
            assert [row.user_id for row in db.scalars(select(model))] == [accounts[1][0]]
