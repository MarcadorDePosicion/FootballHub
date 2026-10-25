"""Fictional Android demo, with the same stable IDs; never provider data.

The caller supplies the reference date. Dates remain relative like Android's
LocalDate.now(), without changing the package midway through a server run.
"""
from datetime import date, timedelta
from datetime import datetime
import asyncio
import time
from zoneinfo import ZoneInfo

from .football_client import FootballClient, FootballError
from .football_mapper import map_football

from .football_schemas import (
    Coach, FootballData, League, Match, MatchEvent, MatchEvents, MatchLineup,
    MatchMinute, Player, PlayerAppearance, Standing, Team,
)


class LiveFootballService:
    def __init__(self, settings, *, transport=None, clock=time.monotonic, today=None, client=None):
        self.client = FootballClient(settings, transport=transport, client=client, clock=clock)
        self.clock = clock
        self.today = today or (lambda: datetime.now(ZoneInfo("Europe/Warsaw")).date())
        self.ttl = settings.football_data_cache_ttl_seconds
        self.cached = None
        self.expires = 0.0
        self.lock = asyncio.Lock()

    async def get(self):
        async with self.lock:
            if self.cached is not None and self.clock() < self.expires:
                return self.cached.model_copy(deep=True), "hit"
            try:
                package = map_football(await self.client.fetch(self.today()))
            except FootballError as error:
                # Access/validation errors are explicit even with cache; keep last good data intact.
                if error.transient and self.cached is not None:
                    return self.cached.model_copy(deep=True), "stale"
                raise
            self.cached = package
            self.expires = self.clock() + self.ttl
            return package.model_copy(deep=True), "miss"

    async def close(self):
        await self.client.close()


def demo_events() -> list[MatchEvents]:
    def goal(id, minute, team, scorer, assist, home, away, kind="REGULAR", added=None):
        return MatchEvent(id=id, kind="GOAL", time=MatchMinute(minute=minute, added=added),
                          team_id=team, player_id=scorer, assist_player_id=assist,
                          goal_kind=kind, home_score=home, away_score=away)

    def card(id, minute, team, player, kind, added=None):
        return MatchEvent(id=id, kind="CARD", time=MatchMinute(minute=minute, added=added),
                          team_id=team, player_id=player, card_kind=kind)

    def substitution(id, minute, team, outgoing, incoming):
        return MatchEvent(id=id, kind="SUBSTITUTION", time=MatchMinute(minute=minute),
                          team_id=team, player_id=outgoing, incoming_player_id=incoming)

    return [
        MatchEvents(match_id=3, coverage="COMPLETE", demo=True, events=[
            goal(301, 18, 9, 908, 906, 1, 0), card(302, 31, 10, 1002, "YELLOW"),
            goal(303, 45, 10, 1009, None, 1, 1, "PENALTY", 2),
            substitution(304, 63, 9, 908, 913), goal(305, 78, 9, 913, 907, 2, 1),
            card(306, 82, 10, 1002, "SECOND_YELLOW"),
        ]),
        MatchEvents(match_id=4, coverage="COMPLETE", demo=True, events=[
            goal(401, 12, 3, 308, 306, 1, 0), goal(402, 35, 4, 409, None, 1, 1),
            goal(403, 54, 3, 308, 307, 2, 1), substitution(404, 67, 4, 409, 413),
            goal(405, 80, 3, 402, None, 3, 1, "OWN_GOAL"), card(406, 90, 4, 401, "RED", 3),
        ]),
        MatchEvents(match_id=5, coverage="COMPLETE", demo=True, events=[
            goal(501, 24, 7, 708, None, 1, 0), goal(502, 70, 8, 809, 806, 1, 1),
        ]),
    ]


def create_demo(reference_date: date) -> FootballData:
    team_rows = [
        (1, "Arsenal", "ARS", "Londyn", "Emirates Stadium", 1886, 1),
        (2, "Liverpool", "LIV", "Liverpool", "Anfield", 1892, 1),
        (3, "Manchester City", "MCI", "Manchester", "Etihad Stadium", 1880, 1),
        (4, "Chelsea", "CHE", "Londyn", "Stamford Bridge", 1905, 1),
        (5, "FC Barcelona", "BAR", "Barcelona", "Camp Nou", 1899, 2),
        (6, "Real Madrid", "RMA", "Madryt", "Santiago Bernabéu", 1902, 2),
        (7, "Atlético Madrid", "ATM", "Madryt", "Metropolitano", 1903, 2),
        (8, "Athletic Bilbao", "ATH", "Bilbao", "San Mamés", 1898, 2),
        (9, "Lech Poznań", "LPO", "Poznań", "Enea Stadion", 1922, 3),
        (10, "Legia Warszawa", "LEG", "Warszawa", "Stadion Wojska Polskiego", 1916, 3),
        (11, "Raków Częstochowa", "RAK", "Częstochowa", "Miejski Stadion Piłkarski", 1921, 3),
        (12, "Jagiellonia Białystok", "JAG", "Białystok", "Chorten Arena", 1920, 3),
    ]
    teams = [Team(**dict(zip(
        ("id", "name", "short_name", "city", "stadium", "founded", "league_id"), row
    ))) for row in team_rows]
    by_team = {team.id: team for team in teams}
    match_rows = [
        (1, 1, 1, 2, 0, "18:30", "SCHEDULED", None, None, 9),
        (2, 2, 5, 6, 0, "21:00", "SCHEDULED", None, None, 9),
        (3, 3, 9, 10, 0, "17:30", "FINISHED", 2, 1, 12),
        (4, 1, 3, 4, -1, "20:00", "FINISHED", 3, 1, 9),
        (5, 2, 7, 8, -1, "19:00", "FINISHED", 1, 1, 9),
        (6, 3, 11, 12, 1, "20:30", "SCHEDULED", None, None, 12),
    ]
    matches = [Match(id=id, league_id=league, home_id=home, away_id=away,
                     date=(reference_date + timedelta(days=offset)).isoformat(), time=time,
                     status=status, home_score=hs, away_score=aws, round=round,
                     venue=by_team[home].stadium)
               for id, league, home, away, offset, time, status, hs, aws, round in match_rows]
    standings = []
    for team in teams:
        rank = (team.id - 1) % 4
        standings.append(Standing(league_id=team.league_id, team_id=team.id, played=8,
                                  won=6-rank, drawn=1, lost=1+rank,
                                  goals_for=19-rank*3, goals_against=6+rank*2))
    names = ["Adam Nowak", "Bruno Baran", "Carlos Silva", "Daniel Costa", "Emil Wolski",
             "Filip Marek", "Gabriel Lima", "Hugo Martin", "Igor Lis", "Jan Bąk",
             "Kamil Król", "Leon Vidal", "Marek Alba", "Niko Barto"]
    players = []
    for team in teams:
        for index, name in enumerate(names):
            position = ("GOALKEEPER" if index in (0, 11) else
                        "DEFENDER" if index in (1, 2, 3, 4, 12) else
                        "MIDFIELDER" if index in (5, 6, 7) else "FORWARD")
            players.append(Player(
                id=team.id*100+index, team_id=team.id, name=name,
                number=None if index == 13 else index+1, position=position,
                nationality=None if index == 13 else "Polska", demo=True,
                date_of_birth=None if index == 13 else f"{1994+index%8}-04-18",
                height_cm=188 if index == 0 else None,
                preferred_foot="RIGHT" if index == 0 else None,
            ))
    lineups = []
    for match in matches:
        if match.id == 6:  # Android deliberately leaves tomorrow's lineup unavailable.
            continue
        for team_id in (match.home_id, match.away_id):
            base = team_id*100
            coach = None if team_id % 2 == 0 else Coach(name={
                9: "Paweł Szkoleniowy", 3: "Marek Treningowy"
            }.get(team_id, "Jan Przykładowy"))
            lineups.append(MatchLineup(
                match_id=match.id, team_id=team_id, formation="4-3-3",
                starter_rows=[[base], [base+i for i in range(1, 5)],
                              [base+i for i in range(5, 8)], [base+i for i in range(8, 11)]],
                substitutes=[base+i for i in range(11, 14)], demo=True, coach=coach,
            ))
    events = demo_events()
    by_match = {match.id: match for match in matches}
    by_events = {group.match_id: group.events for group in events}
    appearances = []
    for lineup in lineups:
        if by_match[lineup.match_id].status != "FINISHED":
            continue
        starters = [id for row in lineup.starter_rows for id in row]
        entered = [event.incoming_player_id for event in by_events.get(lineup.match_id, [])
                   if event.kind == "SUBSTITUTION" and event.team_id == lineup.team_id]
        for player_id in dict.fromkeys(starters + entered):
            # Null minutes intentionally preserve Android's calculation from COMPLETE events.
            appearances.append(PlayerAppearance(player_id=player_id, match_id=lineup.match_id,
                                                team_id=lineup.team_id, started=player_id in starters))
    return FootballData(
        leagues=[League(id=1, name="Premier League", country="Anglia"),
                 League(id=2, name="La Liga", country="Hiszpania"),
                 League(id=3, name="Ekstraklasa", country="Polska")],
        teams=teams, players=players, standings=standings, matches=matches,
        lineups=lineups, match_events=events, appearances=appearances,
    )
