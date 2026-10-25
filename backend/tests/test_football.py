from datetime import date, timedelta
from typing import get_args

import pytest
from fastapi.testclient import TestClient

from app.main import create_app
from app.football_schemas import (
    CardKind, Coach, EventCoverage, EventKind, ExternalId, FootballData, GoalKind,
    MatchEvent, MatchEvents, MatchLineup, MatchMinute, MatchStatusKind, Player,
    PlayerAppearance, PlayerPosition, PreferredFoot,
)
from app.football_service import create_demo


COLLECTIONS = {"leagues", "teams", "players", "standings", "matches", "lineups", "matchEvents", "appearances"}
KEYS = {
    "leagues": {"id", "name", "country"},
    "teams": {"id", "name", "shortName", "city", "stadium", "founded", "leagueId"},
    "players": {"id", "teamId", "name", "number", "position", "nationality", "demo",
                "dateOfBirth", "heightCm", "preferredFoot", "externalId"},
    "standings": {"leagueId", "teamId", "played", "won", "drawn", "lost", "goalsFor",
                  "goalsAgainst", "position", "officialPoints"},
    "matches": {"id", "leagueId", "homeId", "awayId", "date", "time", "status", "homeScore",
                "awayScore", "round", "venue"},
    "lineups": {"matchId", "teamId", "formation", "starterRows", "substitutes", "demo", "coach"},
    "matchEvents": {"matchId", "coverage", "events", "demo"},
    "appearances": {"playerId", "matchId", "teamId", "started", "minutesPlayed"},
}
EVENT_KEYS = {"id", "kind", "time", "teamId", "playerId", "assistPlayerId", "incomingPlayerId",
              "goalKind", "cardKind", "homeScore", "awayScore"}


def test_public_football_contract_and_determinism(client, settings):
    assert settings.football_data_api_key.get_secret_value() == ""
    response = client.get("/api/football")  # No JWT or provider credentials.
    assert response.status_code == 200
    payload = response.json()
    assert set(payload) == COLLECTIONS
    assert client.get("/api/football").json() == payload
    assert FootballData.model_validate(payload).model_dump(by_alias=True) == payload
    for name, expected in KEYS.items():
        assert isinstance(payload[name], list)
        assert payload[name]
        for row in payload[name]:
            assert set(row) == expected
    for group in payload["matchEvents"]:
        for event in group["events"]:
            assert set(event) == EVENT_KEYS
            assert set(event["time"]) == {"minute", "added"}
            assert type(event["time"]["minute"]) is int
    for lineup in payload["lineups"]:
        assert all(isinstance(row, list) for row in lineup["starterRows"])
        if lineup["coach"] is not None:
            assert set(lineup["coach"]) == {"name", "externalId"}


def test_full_android_demo_ids_dates_and_nulls(client, application):
    anchor = date(2026, 10, 25)
    application.state.football_demo_date = anchor
    payload = client.get("/api/football").json()
    assert [x["id"] for x in payload["leagues"]] == [1, 2, 3]
    assert [x["id"] for x in payload["teams"]] == list(range(1, 13))
    assert [x["id"] for x in payload["players"]] == [team*100+i for team in range(1, 13) for i in range(14)]
    assert [x["id"] for x in payload["matches"]] == list(range(1, 7))
    assert [x["date"] for x in payload["matches"]] == [
        (anchor + timedelta(days=offset)).isoformat() for offset in (0, 0, 0, -1, -1, 1)]
    assert [x["time"] for x in payload["matches"]] == ["18:30", "21:00", "17:30", "20:00", "19:00", "20:30"]
    assert len(payload["lineups"]) == 10
    assert len(payload["appearances"]) == 68
    assert {x["matchId"] for x in payload["matchEvents"]} == {3, 4, 5}
    assert all(x["demo"] for key in ("players", "lineups", "matchEvents") for x in payload[key])
    assert all(x["position"] is None and x["officialPoints"] is None for x in payload["standings"])
    assert all(x["minutesPlayed"] is None for x in payload["appearances"])
    assert all(x["externalId"] is None for x in payload["players"])
    player = next(x for x in payload["players"] if x["id"] == 913)
    assert player["name"] == "Niko Barto"
    assert player["number"] is None and player["dateOfBirth"] is None
    assert not any(x["matchId"] == 6 for x in payload["lineups"])
    for match in payload["matches"]:
        date.fromisoformat(match["date"])
        if match["status"] == "SCHEDULED":
            assert match["homeScore"] is None and match["awayScore"] is None


def test_all_demo_references_and_goal_scores(client):
    data = client.get("/api/football").json()
    leagues = {x["id"]: x for x in data["leagues"]}
    teams = {x["id"]: x for x in data["teams"]}
    players = {x["id"]: x for x in data["players"]}
    matches = {x["id"]: x for x in data["matches"]}
    for key, mapped in (("leagues", leagues), ("teams", teams), ("players", players), ("matches", matches)):
        assert len(mapped) == len(data[key])
    for team in teams.values():
        assert team["leagueId"] in leagues
    for player in players.values():
        assert player["teamId"] in teams
    for match in matches.values():
        assert match["homeId"] != match["awayId"]
        assert teams[match["homeId"]]["leagueId"] == teams[match["awayId"]]["leagueId"] == match["leagueId"]
        assert match["venue"] == teams[match["homeId"]]["stadium"]
    for standing in data["standings"]:
        assert teams[standing["teamId"]]["leagueId"] == standing["leagueId"]
        assert standing["played"] == standing["won"] + standing["drawn"] + standing["lost"]
    lineups = {(x["matchId"], x["teamId"]): x for x in data["lineups"]}
    for (match_id, team_id), lineup in lineups.items():
        assert team_id in (matches[match_id]["homeId"], matches[match_id]["awayId"])
        assert [len(row) for row in lineup["starterRows"]] == [1, 4, 3, 3]
        roster = [id for row in lineup["starterRows"] for id in row] + lineup["substitutes"]
        assert len(roster) == len(set(roster)) == 14
        assert all(players[id]["teamId"] == team_id for id in roster)
    event_ids = []
    for group in data["matchEvents"]:
        match = matches[group["matchId"]]
        score = [0, 0]
        for event in group["events"]:
            event_ids.append(event["id"])
            assert event["teamId"] in (match["homeId"], match["awayId"])
            for key in ("playerId", "assistPlayerId", "incomingPlayerId"):
                if event[key] is not None:
                    assert event[key] in players
            if event["goalKind"] == "OWN_GOAL":
                assert players[event["playerId"]]["teamId"] != event["teamId"]
            else:
                assert players[event["playerId"]]["teamId"] == event["teamId"]
            for key in ("assistPlayerId", "incomingPlayerId"):
                if event[key] is not None:
                    assert players[event[key]]["teamId"] == event["teamId"]
            if event["kind"] == "GOAL":
                score[0 if event["teamId"] == match["homeId"] else 1] += 1
                assert score == [event["homeScore"], event["awayScore"]]
        assert score == [match["homeScore"], match["awayScore"]]
    assert len(event_ids) == len(set(event_ids)) == 14
    assert set(event_ids) == set(range(301, 307)) | set(range(401, 407)) | {501, 502}
    for appearance in data["appearances"]:
        assert players[appearance["playerId"]]["teamId"] == appearance["teamId"]
        lineup = lineups[appearance["matchId"], appearance["teamId"]]
        starters = [id for row in lineup["starterRows"] for id in row]
        assert appearance["started"] == (appearance["playerId"] in starters)
        assert appearance["playerId"] in starters + lineup["substitutes"]
        assert matches[appearance["matchId"]]["status"] == "FINISHED"


def test_own_goal_second_yellow_and_substitution_semantics(client):
    data = client.get("/api/football").json()
    events = {event["id"]: event for group in data["matchEvents"] for event in group["events"]}
    assert (events[405]["teamId"], events[405]["playerId"], events[405]["goalKind"]) == (3, 402, "OWN_GOAL")
    assert events[306]["cardKind"] == "SECOND_YELLOW"
    assert events[303]["time"] == {"minute": 45, "added": 2}
    assert (events[304]["playerId"], events[304]["incomingPlayerId"]) == (908, 913)
    assert events[305]["assistPlayerId"] == 907
    appearances = {(x["matchId"], x["playerId"]): x for x in data["appearances"]}
    assert appearances[3, 913]["started"] is False
    assert appearances[3, 908]["started"] is True
    assert (3, 912) not in appearances  # Unused substitute is not a played appearance.


@pytest.mark.parametrize("coverage", get_args(EventCoverage))
def test_nullable_and_empty_contract_over_http(client, monkeypatch, coverage):
    from app.routers import football as route
    data = FootballData(
        players=[Player(id=1, team_id=None, name="Unknown")],
        lineups=[MatchLineup(match_id=1, team_id=1)],
        match_events=[MatchEvents(match_id=1, coverage=coverage)],
        appearances=[PlayerAppearance(player_id=1, match_id=1, team_id=1)],
    )
    monkeypatch.setattr(route, "create_demo", lambda _: data)
    payload = client.get("/api/football").json()
    assert payload["matches"] == []
    assert payload["players"][0]["teamId"] is None
    assert payload["players"][0]["position"] is None
    assert payload["lineups"][0]["starterRows"] is None
    assert payload["lineups"][0]["substitutes"] is None
    assert payload["matchEvents"][0]["coverage"] == coverage
    assert payload["matchEvents"][0]["events"] == []
    assert payload["appearances"][0]["started"] is None
    assert payload["appearances"][0]["minutesPlayed"] is None


def test_enums_optional_metadata_and_unknown_status_serialization(client, monkeypatch):
    from app.routers import football as route
    data = create_demo(date(2026, 10, 25))
    statuses = [*get_args(MatchStatusKind), "LIVE", "IN_PROGRESS", "FUTURE_PROVIDER_STATUS"]
    data.matches = [data.matches[0].model_copy(update={"status": value, "venue": None}) for value in statuses]
    data.standings[0].position = 5
    data.standings[0].official_points = 17
    data.players = [data.players[0].model_copy(update={"position": position, "preferred_foot": foot,
                    "external_id": ExternalId(provider="demo", id="example")})
                    for position in get_args(PlayerPosition) for foot in get_args(PreferredFoot)]
    data.lineups[0].coach = Coach(name="Demo coach", external_id=ExternalId(provider="demo", id="coach"))
    data.match_events[0].events = [MatchEvent(id=i, kind=kind, time=MatchMinute(minute=1), team_id=1,
                                            goal_kind=goal, card_kind=card)
        for i, (kind, goal, card) in enumerate(zip(get_args(EventKind), get_args(GoalKind), get_args(CardKind)))]
    data.match_events[0].events.append(MatchEvent(id=99, kind=None, time=MatchMinute(minute=2), team_id=1))
    monkeypatch.setattr(route, "create_demo", lambda _: data)
    response = client.get("/api/football")
    assert response.status_code == 200
    payload = response.json()
    assert [x["status"] for x in payload["matches"]] == statuses
    assert all(x["venue"] is None for x in payload["matches"])
    assert payload["standings"][0]["officialPoints"] == 17
    assert payload["standings"][0]["position"] == 5
    assert payload["players"][0]["externalId"] == {"provider": "demo", "id": "example"}
    assert payload["lineups"][0]["coach"]["externalId"] == {"provider": "demo", "id": "coach"}
    assert {x["position"] for x in payload["players"]} == set(get_args(PlayerPosition))
    assert {x["preferredFoot"] for x in payload["players"]} == set(get_args(PreferredFoot))
    events = payload["matchEvents"][0]["events"]
    assert [x["kind"] for x in events] == [*get_args(EventKind), None]
    assert [x["goalKind"] for x in events[:3]] == list(get_args(GoalKind))
    assert [x["cardKind"] for x in events[:3]] == list(get_args(CardKind))


def test_live_returns_controlled_error_without_demo(settings, monkeypatch):
    from app.routers import football as route
    settings.football_data_mode = "live"
    def forbidden_demo(_):
        pytest.fail("Live must not silently return demo")
    monkeypatch.setattr(route, "create_demo", forbidden_demo)
    with TestClient(create_app(settings)) as client:
        response = client.get("/api/football")
    assert response.status_code == 503
    assert response.json() == {"detail": "Live football source is not configured yet"}


def test_demo_service_is_independent_and_has_no_network(monkeypatch):
    import socket
    def forbidden_connection(*args, **kwargs):
        pytest.fail("Demo must not connect to a network")
    monkeypatch.setattr(socket.socket, "connect", forbidden_connection)
    anchor = date(2026, 10, 25)
    first = create_demo(anchor)
    original = first.model_dump(by_alias=True)
    first.players.clear()
    assert create_demo(anchor).model_dump(by_alias=True) == original
