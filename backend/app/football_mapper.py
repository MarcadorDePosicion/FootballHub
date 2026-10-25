"""football-data.org v4 to the unchanged Android wire contract.

See FOOTBALL_LIVE.md for deliberate loss of information in the Android model.
Missing optional collections are not interpreted as complete event coverage.
"""
from datetime import datetime
import hashlib
import json
from zoneinfo import ZoneInfo

from pydantic import ValidationError

from .football_client import invalid_payload
from .football_schemas import (
    Coach, ExternalId, FootballData, League, Match, MatchEvent, MatchEvents,
    MatchLineup, MatchMinute, Player, PlayerAppearance, Standing, Team,
)


def obj(value):
    if not isinstance(value, dict):
        raise ValueError("Expected object")
    return value


def rows(value):
    if not isinstance(value, list) or not all(isinstance(item, dict) for item in value):
        raise ValueError("Expected object list")
    return value


def optional_rows(value):
    return None if value is None else rows(value)


def integer(value, *, nullable=False, minimum=0):
    if value is None and nullable:
        return None
    if type(value) is not int or not minimum <= value <= 2147483647:
        raise ValueError("Invalid integer")
    return value


def identity(value):
    return integer(obj(value)["id"], minimum=1)


def string(value):
    if not isinstance(value, str) or not value.strip():
        raise ValueError("Invalid string")
    return value


def optional_string(value):
    return None if value is None else string(value)


def position(value):
    return {
        "Goalkeeper": "GOALKEEPER", "Defence": "DEFENDER", "Defender": "DEFENDER",
        "Centre-Back": "DEFENDER", "Left-Back": "DEFENDER", "Right-Back": "DEFENDER",
        "Midfield": "MIDFIELDER", "Defensive Midfield": "MIDFIELDER",
        "Central Midfield": "MIDFIELDER", "Attacking Midfield": "MIDFIELDER",
        "Left Midfield": "MIDFIELDER", "Right Midfield": "MIDFIELDER",
        "Offence": "FORWARD", "Forward": "FORWARD", "Centre-Forward": "FORWARD",
        "Second Striker": "FORWARD", "Left Winger": "FORWARD", "Right Winger": "FORWARD",
    }.get(optional_string(value))


class Mapper:
    def __init__(self):
        self.leagues, self.teams, self.players, self.matches = {}, {}, {}, {}
        self.standings, self.lineups, self.events, self.appearances = [], [], [], []
        self.event_ids = set()

    def player(self, raw, team_id):
        if raw is None:
            return None
        raw = obj(raw)
        if raw.get("id") is None:
            return None
        id = identity(raw)
        existing = self.players.get(id)
        name = optional_string(raw.get("name")) or (existing.name if existing else None)
        if name is None:
            raise ValueError("Missing player name")
        values = dict(name=name, position=position(raw.get("position")),
                      number=integer(raw.get("shirtNumber"), nullable=True),
                      nationality=optional_string(raw.get("nationality")),
                      date_of_birth=optional_string(raw.get("dateOfBirth")))
        if values["date_of_birth"]:
            datetime.strptime(values["date_of_birth"], "%Y-%m-%d")
        if existing:
            # A historical appearance must not overwrite current squad membership.
            for key, value in values.items():
                if getattr(existing, key) is None and value is not None:
                    setattr(existing, key, value)
        else:
            self.players[id] = Player(id=id, team_id=team_id, **values,
                                      external_id=ExternalId(provider="football-data.org", id=str(id)))
        return id

    def package(self, teams_payload, matches_payload, standings_payload):
        competition = obj(teams_payload["competition"])
        league_id = identity(competition)
        country = optional_string(obj(competition.get("area") or {}).get("name")) or ""
        self.leagues[league_id] = League(id=league_id, name=string(competition["name"]), country=country)
        for raw in rows(teams_payload["teams"]):
            team_id = identity(raw)
            if not country:
                country = optional_string(obj(raw.get("area") or {}).get("name")) or ""
                self.leagues[league_id].country = country
            if team_id not in self.teams:
                self.teams[team_id] = Team(
                    id=team_id, name=string(raw["name"]),
                    short_name=optional_string(raw.get("shortName")) or optional_string(raw.get("tla")) or string(raw["name"]),
                    city="", stadium=optional_string(raw.get("venue")) or "",
                    founded=integer(raw.get("founded"), nullable=True) or 0, league_id=league_id,
                )
            for player in optional_rows(raw.get("squad")) or []:
                self.player(player, team_id)
        for raw in rows(matches_payload["matches"]):
            if identity(obj(raw["competition"])) != league_id:
                raise ValueError("Unexpected match competition")
            self.match(raw, league_id)
        if standings_payload is not None:
            if identity(standings_payload["competition"]) != league_id:
                raise ValueError("Unexpected standings competition")
            # The contract cannot represent multiple groups; keep the first TOTAL table.
            total = next((group for group in rows(standings_payload["standings"])
                          if group.get("type") == "TOTAL"), None)
            if total:
                seen = set()
                for raw in rows(total["table"]):
                    team_id = identity(raw["team"])
                    if team_id not in self.teams or team_id in seen:
                        raise ValueError("Invalid standings reference")
                    seen.add(team_id)
                    self.standings.append(Standing(
                        league_id=league_id, team_id=team_id,
                        played=integer(raw["playedGames"]), won=integer(raw["won"]),
                        drawn=integer(raw["draw"]), lost=integer(raw["lost"]),
                        goals_for=integer(raw["goalsFor"]), goals_against=integer(raw["goalsAgainst"]),
                        position=integer(raw["position"], minimum=1),
                        official_points=integer(raw["points"], minimum=-2147483648),
                    ))

    def match(self, raw, league_id):
        match_id = identity(raw)
        if match_id in self.matches:
            raise ValueError("Duplicate match")
        home, away = obj(raw["homeTeam"]), obj(raw["awayTeam"])
        home_id, away_id = identity(home), identity(away)
        if home_id == away_id or any(id not in self.teams for id in (home_id, away_id)):
            raise ValueError("Missing match team")
        timestamp = datetime.fromisoformat(string(raw["utcDate"]).replace("Z", "+00:00"))
        if timestamp.tzinfo is None:
            raise ValueError("Missing timezone")
        local = timestamp.astimezone(ZoneInfo("Europe/Warsaw"))
        score = obj(raw["score"])
        full_time = obj(score["fullTime"])
        match = Match(id=match_id, league_id=league_id, home_id=home_id, away_id=away_id,
                      date=local.strftime("%Y-%m-%d"), time=local.strftime("%H:%M"),
                      status=string(raw["status"]), home_score=integer(full_time.get("home"), nullable=True),
                      away_score=integer(full_time.get("away"), nullable=True),
                      round=integer(raw.get("matchday"), nullable=True) or 0,
                      venue=optional_string(raw.get("venue")))
        self.matches[match_id] = match
        rosters = {}
        for side in (home, away):
            team_id = identity(side)
            starters = optional_rows(side.get("lineup"))
            bench = optional_rows(side.get("bench"))
            start_ids = None if starters is None else [self.player(player, team_id) for player in starters]
            bench_ids = None if bench is None else [self.player(player, team_id) for player in bench]
            roster_ids = (start_ids or []) + (bench_ids or [])
            if None in roster_ids or len(roster_ids) != len(set(roster_ids)):
                raise ValueError("Invalid lineup IDs")
            coach = side.get("coach")
            mapped_coach = None
            if coach is not None and obj(coach).get("name"):
                coach_id = integer(coach.get("id"), nullable=True, minimum=1)
                mapped_coach = Coach(name=string(coach["name"]), external_id=None if coach_id is None else
                                    ExternalId(provider="football-data.org", id=str(coach_id)))
            if any(value is not None for value in (starters, bench, side.get("formation"), mapped_coach)):
                self.lineups.append(MatchLineup(
                    match_id=match_id, team_id=team_id, formation=optional_string(side.get("formation")),
                    # API list order does not prove tactical coordinates. Android falls back to a list.
                    starter_rows=None if start_ids is None else ([start_ids] if start_ids else []),
                    substitutes=bench_ids, coach=mapped_coach,
                ))
            rosters[team_id] = (start_ids, bench_ids)
        arrays = {key: optional_rows(raw.get(key)) for key in ("goals", "bookings", "substitutions")}
        events, complete = [], all(value is not None for value in arrays.values())
        fingerprints = {}
        for category, items in arrays.items():
            for event in items or []:
                team_id = identity(event["team"])
                if team_id not in (home_id, away_id):
                    raise ValueError("Invalid event team")
                kind = {"goals": "GOAL", "bookings": "CARD", "substitutions": "SUBSTITUTION"}[category]
                goal_kind = {"REGULAR": "REGULAR", "PENALTY": "PENALTY", "OWN": "OWN_GOAL"}.get(event.get("type"))
                card_kind = {"YELLOW": "YELLOW", "YELLOW_RED": "SECOND_YELLOW", "RED": "RED"}.get(event.get("card"))
                player_team = (away_id if team_id == home_id else home_id) if goal_kind == "OWN_GOAL" else team_id
                player_id = self.player(event.get({"goals": "scorer", "bookings": "player", "substitutions": "playerOut"}[category]), player_team)
                assist_id = self.player(event.get("assist"), team_id) if category == "goals" else None
                incoming_id = self.player(event.get("playerIn"), team_id) if category == "substitutions" else None
                if player_id is None or (kind == "SUBSTITUTION" and incoming_id is None) or (kind == "GOAL" and goal_kind is None) or (kind == "CARD" and card_kind is None):
                    complete = False
                event_score = obj(event.get("score") or {})
                # v4 events have no guaranteed provider ID. Hash stable identity fields, not list position.
                fingerprint = json.dumps([match_id, category, event.get("minute"), event.get("injuryTime"),
                                          team_id, player_id, incoming_id, event.get("type"), event.get("card")])
                occurrence = fingerprints.get(fingerprint, 0)
                fingerprints[fingerprint] = occurrence + 1
                event_id = int.from_bytes(hashlib.sha256(f"{fingerprint}:{occurrence}".encode()).digest()[:4], "big") & 0x7fffffff
                if event_id in self.event_ids:
                    raise ValueError("Event ID collision")
                self.event_ids.add(event_id)
                events.append(MatchEvent(
                    id=event_id, kind=kind, time=MatchMinute(minute=integer(event["minute"]),
                        added=integer(event.get("injuryTime"), nullable=True)), team_id=team_id,
                    player_id=player_id, assist_player_id=assist_id, incoming_player_id=incoming_id,
                    goal_kind=goal_kind if kind == "GOAL" else None, card_kind=card_kind if kind == "CARD" else None,
                    home_score=integer(event_score.get("home"), nullable=True), away_score=integer(event_score.get("away"), nullable=True),
                ))
        # Complete only after a regulation-time finish, all arrays and consistent goal totals.
        # Extra-time/shootout must not trigger Android's fallback assumption of 90 minutes.
        totals = [sum(e.kind == "GOAL" and e.team_id == id for e in events) for id in (home_id, away_id)]
        complete = complete and match.status == "FINISHED" and score.get("duration") == "REGULAR" and totals == [match.home_score, match.away_score]
        coverage = "COMPLETE" if complete else "PARTIAL" if any(v is not None for v in arrays.values()) else "UNAVAILABLE"
        events.sort(key=lambda event: (event.time.minute, event.time.added or 0, event.id))
        self.events.append(MatchEvents(match_id=match_id, coverage=coverage, events=events))
        played = match.status in ("IN_PLAY", "PAUSED", "EXTRA_TIME", "PENALTY_SHOOTOUT", "FINISHED")
        if played:
            for team_id, (starters, bench) in rosters.items():
                incoming = {e.incoming_player_id for e in events if e.team_id == team_id and e.kind == "SUBSTITUTION" and e.incoming_player_id}
                involved = {id for e in events for id in (e.player_id, e.assist_player_id)
                            if id and ((e.team_id == team_id and e.goal_kind != "OWN_GOAL") or (e.team_id != team_id and e.goal_kind == "OWN_GOAL"))}
                for id in dict.fromkeys((starters or []) + (bench or []) + sorted(incoming | involved)):
                    started = True if starters is not None and id in starters else False if id in incoming or (bench is not None and id in bench) else None
                    unused = complete and starters is not None and id in (bench or []) and id not in incoming | involved
                    self.appearances.append(PlayerAppearance(player_id=id, match_id=match_id, team_id=team_id,
                                                             started=started, minutes_played=0 if unused else None))


def map_football(packages) -> FootballData:
    try:
        mapper = Mapper()
        for teams, matches, standings in packages:
            mapper.package(obj(teams), obj(matches), None if standings is None else obj(standings))
        return FootballData(leagues=list(mapper.leagues.values()), teams=list(mapper.teams.values()),
                            players=list(mapper.players.values()), matches=list(mapper.matches.values()),
                            standings=mapper.standings, lineups=mapper.lineups,
                            match_events=mapper.events, appearances=mapper.appearances)
    except (KeyError, TypeError, ValueError, OverflowError, ValidationError):
        raise invalid_payload() from None
