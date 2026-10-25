import asyncio
from copy import deepcopy
from datetime import date
import secrets
import logging

import httpx
import pytest
from fastapi.testclient import TestClient

from app.config import Settings
from app.main import create_app
from app.football_client import FootballClient, FootballError
from app.football_mapper import map_football
from app.football_service import LiveFootballService


class Clock:
    now = 0.0

    def __call__(self):
        return self.now


@pytest.fixture(autouse=True)
def no_network(monkeypatch):
    async def forbidden(*args, **kwargs):
        pytest.fail("Live tests must use MockTransport, never real HTTP transport")
    # Windows asyncio itself requires a loopback socketpair; block HTTP transport instead.
    monkeypatch.setattr(httpx.AsyncHTTPTransport, "handle_async_request", forbidden)


@pytest.fixture
def live_settings():
    return Settings(_env_file=None, env="test", database_url="sqlite:///:memory:",
                    football_data_mode="live", football_data_api_key=secrets.token_hex(24),
                    football_data_max_requests_per_minute=100)


@pytest.fixture
def upstream():
    competition = {"id": 2021, "name": "Premier League", "code": "PL", "area": {"name": "England"}}
    def person(id, name=None):
        return {"id": id, "name": name or f"Player {id}", "position": "Midfield",
                "dateOfBirth": "1995-04-18", "nationality": "England"}
    teams = [{"id": id, "name": name, "shortName": name, "venue": name + " Stadium",
              "founded": 1886, "squad": [person(id*100+i) for i in range(1, 14)]}
             for id, name in ((61, "Arsenal"), (64, "Liverpool"))]
    def side(team):
        return {"id": team["id"], "name": team["name"], "formation": "4-3-3",
                "coach": {"id": team["id"]+1000, "name": "Coach"},
                "lineup": [dict(p, shirtNumber=i+1) for i, p in enumerate(team["squad"][:11])],
                "bench": team["squad"][11:]}
    def goal(minute, team, scorer, hs, aws, type="REGULAR", assist=None):
        return {"minute": minute, "injuryTime": None, "type": type, "team": {"id": team},
                "scorer": person(scorer), "assist": None if assist is None else person(assist),
                "score": {"home": hs, "away": aws}}
    match = {"id": 400001, "competition": competition, "utcDate": "2026-06-01T22:30:00Z",
             "matchday": 9, "status": "FINISHED", "homeTeam": side(teams[0]), "awayTeam": side(teams[1]),
             "score": {"duration": "REGULAR", "fullTime": {"home": 3, "away": 1}},
             "goals": [goal(12, 61, 6101, 1, 0, assist=6102), goal(20, 64, 6401, 1, 1, "PENALTY"),
                       goal(50, 61, 6112, 2, 1), goal(80, 61, 6402, 3, 1, "OWN")],
             "bookings": [{"minute": 31, "team": {"id": 64}, "player": person(6402), "card": "YELLOW"},
                          {"minute": 82, "team": {"id": 64}, "player": person(6402), "card": "YELLOW_RED"}],
             "substitutions": [{"minute": 46, "team": {"id": 61}, "playerOut": person(6101), "playerIn": person(6112)}]}
    table = [{"position": rank, "team": {"id": team}, "playedGames": 8, "won": 4, "draw": 2,
              "lost": 2, "goalsFor": 10, "goalsAgainst": 6, "points": points}
             for rank, team, points in ((1, 64, 13), (2, 61, 20))]
    return {"teams": {"competition": competition, "teams": teams}, "matches": {"matches": [match]},
            "standings": {"competition": competition, "standings": [
                {"type": "HOME", "table": []}, {"type": "TOTAL", "table": table},
                {"type": "AWAY", "table": []}]}}


class Provider:
    def __init__(self, payload):
        self.payload = payload
        self.requests = []
        self.failure = None
        self.no_table = False

    def __call__(self, request):
        self.requests.append(request)
        resource = request.url.path.rsplit("/", 1)[-1]
        if self.failure:
            if isinstance(self.failure, int):
                return httpx.Response(self.failure, text=request.headers["X-Auth-Token"])
            if self.failure == "timeout":
                raise httpx.ReadTimeout(request.headers["X-Auth-Token"], request=request)
            if self.failure == "transport":
                raise httpx.ConnectError(request.headers["X-Auth-Token"], request=request)
            if self.failure == "json":
                return httpx.Response(200, text="not json " + request.headers["X-Auth-Token"])
        if resource == "standings" and self.no_table:
            return httpx.Response(404)
        return httpx.Response(200, json=deepcopy(self.payload[resource]))


def app_with_provider(settings, provider, clock):
    return create_app(settings, football_transport=httpx.MockTransport(provider),
                      football_clock=clock, football_today=lambda: date(2026, 6, 2))


def test_live_contract_mapping_and_cache(live_settings, upstream):
    provider, clock = Provider(upstream), Clock()
    with TestClient(app_with_provider(live_settings, provider, clock)) as client:
        response = client.get("/api/football")
        assert response.status_code == 200
        assert response.headers["X-Football-Data-Cache"] == "miss"
        data = response.json()
        assert set(data) == {"teams", "leagues", "players", "matches", "standings", "lineups", "matchEvents", "appearances"}
        match = data["matches"][0]
        assert (match["date"], match["time"], match["venue"]) == ("2026-06-02", "00:30", None)
        assert [row["teamId"] for row in data["standings"]] == [64, 61]
        assert [row["position"] for row in data["standings"]] == [1, 2]
        assert [row["officialPoints"] for row in data["standings"]] == [13, 20]
        assert all(p["heightCm"] is None and p["preferredFoot"] is None and not p["demo"] for p in data["players"])
        events = data["matchEvents"][0]
        assert events["coverage"] == "COMPLETE"
        own = next(e for e in events["events"] if e["goalKind"] == "OWN_GOAL")
        assert (own["teamId"], own["playerId"]) == (61, 6402)
        assert any(e["cardKind"] == "SECOND_YELLOW" for e in events["events"])
        assert events["events"][0]["assistPlayerId"] == 6102
        sub = next(e for e in events["events"] if e["kind"] == "SUBSTITUTION")
        assert (sub["playerId"], sub["incomingPlayerId"]) == (6101, 6112)
        lineup = data["lineups"][0]
        assert len(lineup["starterRows"][0]) == 11  # Flat list, no fabricated pitch coordinates.
        assert lineup["substitutes"] == [6112, 6113]
        appearance = {p["playerId"]: p for p in data["appearances"]}
        assert appearance[6112]["started"] is False and appearance[6112]["minutesPlayed"] is None
        assert appearance[6113]["minutesPlayed"] == 0
        assert len(provider.requests) == 3
        assert [r.url.path for r in provider.requests] == [f"/v4/competitions/PL/{x}" for x in ("teams", "matches", "standings")]
        request = provider.requests[1]
        assert dict(request.url.params) == {"dateFrom": "2026-06-01", "dateTo": "2026-06-03"}
        assert all(request.headers[f"X-Unfold-{name}"] == "true" for name in ("Lineups", "Bookings", "Subs", "Goals"))
        assert all(r.headers["X-Auth-Token"] == live_settings.football_data_api_key.get_secret_value() for r in provider.requests)
        clock.now = 119
        cached = client.get("/api/football")
        assert cached.json() == data and cached.headers["X-Football-Data-Cache"] == "hit"
        assert len(provider.requests) == 3
        clock.now = 120
        assert client.get("/api/football").headers["X-Football-Data-Cache"] == "miss"
        assert len(provider.requests) == 6
    assert_references(data)


def assert_references(data):
    leagues = {x["id"] for x in data["leagues"]}
    teams = {x["id"] for x in data["teams"]}
    players = {x["id"] for x in data["players"]}
    matches = {x["id"]: x for x in data["matches"]}
    assert all(x["leagueId"] in leagues for x in data["teams"])
    assert all(x["teamId"] in teams for x in data["players"])
    for match in matches.values():
        assert match["leagueId"] in leagues and {match["homeId"], match["awayId"]} <= teams
    for row in data["standings"]:
        assert row["leagueId"] in leagues and row["teamId"] in teams
    for row in data["lineups"]:
        assert row["teamId"] in teams and row["matchId"] in matches
        assert {id for group in row["starterRows"] or [] for id in group} <= players
        assert set(row["substitutes"] or []) <= players
    for group in data["matchEvents"]:
        assert group["matchId"] in matches
        for e in group["events"]:
            assert e["teamId"] in teams
            assert {e[k] for k in ("playerId", "assistPlayerId", "incomingPlayerId") if e[k] is not None} <= players
    for row in data["appearances"]:
        assert row["playerId"] in players and row["teamId"] in teams and row["matchId"] in matches


@pytest.mark.parametrize("coverage", ["COMPLETE", "PARTIAL", "UNAVAILABLE"])
def test_valid_partial_packages_and_missing_table_are_cached(live_settings, upstream, coverage):
    if coverage != "COMPLETE":
        upstream["matches"]["matches"][0].pop("substitutions")
    if coverage == "UNAVAILABLE":
        for key in ("goals", "bookings"):
            upstream["matches"]["matches"][0].pop(key)
    provider = Provider(upstream)
    provider.no_table = True
    with TestClient(app_with_provider(live_settings, provider, Clock())) as client:
        response = client.get("/api/football")
        assert response.status_code == 200
        assert response.json()["standings"] == []
        assert response.json()["matchEvents"][0]["coverage"] == coverage
        assert client.get("/api/football").headers["X-Football-Data-Cache"] == "hit"
        assert len(provider.requests) == 3


@pytest.mark.parametrize("scores", [(None, None), (0, 0)])
@pytest.mark.parametrize("utc,expected", [("2026-01-01T23:30:00Z", ("2026-01-02", "00:30")),
                                         ("2026-06-01T22:30:00Z", ("2026-06-02", "00:30"))])
def test_null_and_zero_scores_timezone_and_scheduled(live_settings, upstream, scores, utc, expected):
    match = upstream["matches"]["matches"][0]
    match.update(utcDate=utc, status="TIMED")
    match["score"]["fullTime"] = dict(zip(("home", "away"), scores))
    for key in ("goals", "bookings", "substitutions"):
        match.pop(key)
    provider = Provider(upstream)
    with TestClient(app_with_provider(live_settings, provider, Clock())) as client:
        data = client.get("/api/football").json()
    result = data["matches"][0]
    assert (result["date"], result["time"]) == expected
    assert (result["homeScore"], result["awayScore"]) == scores
    assert result["status"] == "TIMED" and result["venue"] is None
    assert data["appearances"] == []


@pytest.mark.parametrize("failure", [429, 500, 503, "timeout", "transport"])
@pytest.mark.parametrize("warm", [False, True])
def test_transient_errors_stale_or_unavailable(live_settings, upstream, failure, warm, caplog):
    caplog.set_level(logging.DEBUG)
    provider, clock = Provider(upstream), Clock()
    with TestClient(app_with_provider(live_settings, provider, clock)) as client:
        previous = client.get("/api/football").json() if warm else None
        clock.now = 121
        provider.failure = failure
        before = len(provider.requests)
        response = client.get("/api/football")
        assert len(provider.requests) == before + 1  # No automatic retries, including 429.
        if warm:
            assert response.status_code == 200
            assert response.headers["X-Football-Data-Cache"] == "stale"
            assert response.json() == previous
        else:
            assert response.status_code == 503
        token = live_settings.football_data_api_key.get_secret_value()
        assert token not in response.text and token not in caplog.text


@pytest.mark.parametrize("failure,status", [(401, 503), (403, 503), (404, 502), ("json", 502), ("structure", 502)])
@pytest.mark.parametrize("warm", [False, True])
def test_permanent_errors_do_not_replace_cache(live_settings, upstream, failure, status, warm, caplog):
    caplog.set_level(logging.DEBUG)
    provider, clock = Provider(upstream), Clock()
    app = app_with_provider(live_settings, provider, clock)
    with TestClient(app) as client:
        previous = client.get("/api/football").json() if warm else None
        clock.now = 121
        if failure == "structure":
            provider.payload["matches"] = {"matches": [{"id": 400001}]}
        else:
            provider.failure = failure
        response = client.get("/api/football")
        assert response.status_code == status
        cached = app.state.football_service.cached
        assert (cached.model_dump(by_alias=True) if cached else None) == previous
        token = live_settings.football_data_api_key.get_secret_value()
        assert token not in response.text and token not in caplog.text
        if warm:
            provider.failure = 429
            stale = client.get("/api/football")
            assert stale.headers["X-Football-Data-Cache"] == "stale" and stale.json() == previous


def test_global_limit_across_competitions_no_excess_requests(live_settings, upstream):
    live_settings.football_data_competitions = "PL,PD"
    live_settings.football_data_max_requests_per_minute = 4
    provider, clock = Provider(upstream), Clock()
    with TestClient(app_with_provider(live_settings, provider, clock)) as client:
        assert client.get("/api/football").status_code == 503
        assert len(provider.requests) == 4
        assert "/PD/teams" in provider.requests[-1].url.path
        assert client.get("/api/football").status_code == 503
        assert len(provider.requests) == 4
        clock.now = 60
        assert client.get("/api/football").status_code == 503
        assert len(provider.requests) == 8


def test_local_limit_returns_stale(live_settings, upstream):
    live_settings.football_data_max_requests_per_minute = 3
    live_settings.football_data_cache_ttl_seconds = 1
    provider, clock = Provider(upstream), Clock()
    with TestClient(app_with_provider(live_settings, provider, clock)) as client:
        original = client.get("/api/football").json()
        clock.now = 2
        response = client.get("/api/football")
        assert response.status_code == 200 and response.json() == original
        assert response.headers["X-Football-Data-Cache"] == "stale"
        assert len(provider.requests) == 3


def test_concurrent_requests_single_flight(live_settings, upstream):
    async def run():
        entered, release = asyncio.Event(), asyncio.Event()
        provider = Provider(upstream)
        async def transport(request):
            if not provider.requests:
                entered.set()
                await release.wait()
            return provider(request)
        app = create_app(live_settings, football_transport=httpx.MockTransport(transport), football_clock=Clock())
        async with app.router.lifespan_context(app):
            async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url="http://test") as client:
                first = asyncio.create_task(client.get("/api/football"))
                await entered.wait()
                others = [asyncio.create_task(client.get("/api/football")) for _ in range(5)]
                release.set()
                responses = await asyncio.gather(first, *others)
                assert all(r.status_code == 200 for r in responses)
                assert len(provider.requests) == 3
                assert [r.headers["X-Football-Data-Cache"] for r in responses].count("miss") == 1
                assert all(r.json() == responses[0].json() for r in responses)
    asyncio.run(run())


def test_application_caches_and_limiters_are_isolated(live_settings, upstream):
    provider = Provider(upstream)
    for _ in range(2):
        with TestClient(app_with_provider(live_settings, provider, Clock())) as client:
            assert client.get("/api/football").headers["X-Football-Data-Cache"] == "miss"
    assert len(provider.requests) == 6


def test_players_discovered_from_events_and_stable_event_ids(upstream):
    for team in upstream["teams"]["teams"]:
        team["squad"] = []
    match = upstream["matches"]["matches"][0]
    for side in ("homeTeam", "awayTeam"):
        match[side].pop("lineup")
        match[side].pop("bench")
    packages = [(upstream["teams"], upstream["matches"], upstream["standings"])]
    first = map_football(packages).model_dump(by_alias=True)
    assert_references(first)
    assert next(p for p in first["players"] if p["id"] == 6402)["teamId"] == 64
    match["goals"].reverse()
    second = map_football(packages).model_dump(by_alias=True)
    assert first["matchEvents"] == second["matchEvents"]


@pytest.mark.parametrize("duration,status", [("EXTRA_TIME", "FINISHED"), ("PENALTY_SHOOTOUT", "FINISHED"),
                                              ("REGULAR", "AWARDED"), ("REGULAR", "FUTURE_STATUS")])
def test_no_false_regulation_minutes(upstream, duration, status):
    match = upstream["matches"]["matches"][0]
    match["status"] = status
    match["score"]["duration"] = duration
    data = map_football([(upstream["teams"], upstream["matches"], None)])
    assert data.matches[0].status == status
    assert data.match_events[0].coverage == "PARTIAL"
    assert all(p.minutes_played is None for p in data.appearances)


def test_multiple_competitions_valid_bounded_package(live_settings, upstream):
    live_settings.football_data_competitions = "PL,PD"
    second = deepcopy(upstream)
    competition = dict(second["teams"]["competition"], id=2014, name="La Liga", code="PD")
    second["teams"]["competition"] = second["standings"]["competition"] = competition
    second["matches"]["matches"][0]["competition"] = competition
    second["matches"]["matches"][0]["id"] = 400002
    requests = []
    def transport(request):
        requests.append(request)
        source = second if "/PD/" in request.url.path else upstream
        return httpx.Response(200, json=source[request.url.path.rsplit("/", 1)[-1]])
    app = create_app(live_settings, football_transport=httpx.MockTransport(transport), football_clock=Clock())
    with TestClient(app) as client:
        response = client.get("/api/football")
        assert response.status_code == 200
        data = response.json()
        assert {row["id"] for row in data["leagues"]} == {2021, 2014}
        assert {row["leagueId"] for row in data["matches"]} == {2021, 2014}
        assert len(data["teams"]) == 2  # A club appearing in two competitions remains one identity.
        assert len(data["players"]) == 26
        assert len(requests) == 6
        assert all(team["leagueId"] == 2021 for team in data["teams"])
        assert_references(data)


@pytest.mark.parametrize("resource,status", [("teams", 404), ("matches", 404), ("matches", 500), ("standings", 500)])
def test_interrupted_cycle_is_not_cached(live_settings, upstream, resource, status):
    requests = []
    def transport(request):
        requests.append(request)
        current = request.url.path.rsplit("/", 1)[-1]
        return httpx.Response(status if current == resource else 200, json=upstream[current])
    app = create_app(live_settings, football_transport=httpx.MockTransport(transport), football_clock=Clock())
    with TestClient(app) as client:
        response = client.get("/api/football")
        assert response.status_code == (502 if status == 404 else 503)
        assert app.state.football_service.cached is None
        assert len(requests) == ["teams", "matches", "standings"].index(resource) + 1


@pytest.mark.parametrize("malformation", ["teams-missing", "matches-missing", "standings-missing", "wrong-team", "bad-date"])
def test_invalid_required_structures_are_not_cached(live_settings, upstream, malformation):
    if malformation.endswith("-missing"):
        section = malformation.split("-")[0]
        upstream[section].pop(section)
    elif malformation == "wrong-team":
        upstream["matches"]["matches"][0]["homeTeam"]["id"] = 99999
    else:
        upstream["matches"]["matches"][0]["utcDate"] = "2026-06-01T12:00:00"
    provider = Provider(upstream)
    app = app_with_provider(live_settings, provider, Clock())
    with TestClient(app) as client:
        assert client.get("/api/football").status_code == 502
        assert app.state.football_service.cached is None


def test_empty_match_window_is_valid_and_cached(live_settings, upstream):
    upstream["matches"]["matches"] = []
    provider = Provider(upstream)
    with TestClient(app_with_provider(live_settings, provider, Clock())) as client:
        response = client.get("/api/football")
        assert response.status_code == 200
        assert response.json()["matches"] == []
        assert response.json()["appearances"] == []
        assert client.get("/api/football").headers["X-Football-Data-Cache"] == "hit"
        assert len(provider.requests) == 3


def test_demo_never_invokes_provider(settings):
    def forbidden(request):
        pytest.fail("Demo must not call provider even with an API key configured")
    settings.football_data_api_key = live_settings_key = Settings(
        _env_file=None, football_data_api_key=secrets.token_hex(16)).football_data_api_key
    assert live_settings_key.get_secret_value()
    with TestClient(create_app(settings, football_transport=httpx.MockTransport(forbidden))) as client:
        assert client.get("/api/football").status_code == 200


def test_four_competitions_stop_at_ten_without_waiting(live_settings, upstream):
    live_settings.football_data_competitions = "PL,PD,SA,BL1"
    live_settings.football_data_max_requests_per_minute = 10
    clock = Clock()
    requests = []
    def transport(request):
        requests.append((clock.now, request.url.path))
        return httpx.Response(200, json=upstream[request.url.path.rsplit("/", 1)[-1]])
    app = create_app(live_settings, football_transport=httpx.MockTransport(transport), football_clock=clock)
    with TestClient(app) as client:
        response = client.get("/api/football")
        assert response.status_code == 503
        assert response.json() == {"detail": "Football request limit reached"}
        assert len(requests) == 10
        assert requests[-1] == (0.0, "/v4/competitions/BL1/teams")
        assert app.state.football_service.cached is None
        assert clock.now == 0.0  # No waiting or clock advancement in the limiter.
        clock.now = 59.999
        assert client.get("/api/football").status_code == 503
        assert len(requests) == 10
        clock.now = 60.0
        assert client.get("/api/football").status_code == 503
        assert len(requests) == 20  # New window, same incomplete cycle; no cached partial package.
        assert all(timestamp == 60.0 for timestamp, _ in requests[10:])


@pytest.mark.parametrize("evidence", ["confirmed", "wrong-code", "missing-code", "wrong-match-competition"])
def test_standings_404_requires_confirmed_competition(live_settings, upstream, evidence):
    if evidence == "wrong-code":
        live_settings.football_data_competitions = "INVALID"
    elif evidence == "missing-code":
        upstream["teams"]["competition"].pop("code")
    elif evidence == "wrong-match-competition":
        upstream["matches"]["matches"][0]["competition"] = {"id": 2014, "code": "PD"}
    provider = Provider(upstream)
    provider.no_table = True
    app = app_with_provider(live_settings, provider, Clock())
    with TestClient(app) as client:
        response = client.get("/api/football")
        assert len(provider.requests) == 3
        if evidence == "confirmed":
            assert response.status_code == 200
            assert response.json()["standings"] == []
            assert app.state.football_service.cached is not None
        else:
            assert response.status_code == 502
            assert app.state.football_service.cached is None
