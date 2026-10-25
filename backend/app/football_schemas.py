"""Wire fields from Android FootballModels.kt and MatchEvents.kt (not computed getters)."""
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel

PlayerPosition = Literal["GOALKEEPER", "DEFENDER", "MIDFIELDER", "FORWARD"]
PreferredFoot = Literal["LEFT", "RIGHT", "BOTH"]
EventCoverage = Literal["UNAVAILABLE", "PARTIAL", "COMPLETE"]
EventKind = Literal["GOAL", "CARD", "SUBSTITUTION"]
GoalKind = Literal["REGULAR", "PENALTY", "OWN_GOAL"]
CardKind = Literal["YELLOW", "SECOND_YELLOW", "RED"]
# Match.status is a String in Kotlin. Unknown future values must remain representable.
MatchStatusKind = Literal[
    "SCHEDULED", "TIMED", "IN_PLAY", "PAUSED", "EXTRA_TIME", "PENALTY_SHOOTOUT",
    "FINISHED", "POSTPONED", "SUSPENDED", "CANCELLED", "AWARDED", "UNKNOWN",
]


class FootballModel(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True, extra="forbid")


class League(FootballModel):
    id: int
    name: str
    country: str


class Team(FootballModel):
    id: int
    name: str
    short_name: str
    city: str
    stadium: str
    founded: int
    league_id: int


class ExternalId(FootballModel):
    provider: str
    id: str


class Coach(FootballModel):
    name: str
    external_id: ExternalId | None = None


class Player(FootballModel):
    id: int
    team_id: int | None
    name: str
    number: int | None = None
    position: PlayerPosition | None = None
    nationality: str | None = None
    demo: bool = False
    date_of_birth: str | None = None
    height_cm: int | None = None
    preferred_foot: PreferredFoot | None = None
    external_id: ExternalId | None = None


class Match(FootballModel):
    id: int
    league_id: int
    home_id: int
    away_id: int
    date: str
    time: str
    status: str
    home_score: int | None
    away_score: int | None
    round: int
    venue: str | None = None


class Standing(FootballModel):
    league_id: int
    team_id: int
    played: int
    won: int
    drawn: int
    lost: int
    goals_for: int
    goals_against: int
    position: int | None = None
    official_points: int | None = None


class MatchLineup(FootballModel):
    match_id: int
    team_id: int
    formation: str | None = None
    starter_rows: list[list[int]] | None = None
    substitutes: list[int] | None = None
    demo: bool = False
    coach: Coach | None = None


class MatchMinute(FootballModel):
    minute: int
    added: int | None = None


class MatchEvent(FootballModel):
    id: int
    kind: EventKind | None
    time: MatchMinute
    team_id: int
    player_id: int | None = None
    assist_player_id: int | None = None
    incoming_player_id: int | None = None
    goal_kind: GoalKind | None = None
    card_kind: CardKind | None = None
    home_score: int | None = None
    away_score: int | None = None


class MatchEvents(FootballModel):
    match_id: int
    coverage: EventCoverage = "UNAVAILABLE"
    events: list[MatchEvent] = Field(default_factory=list)
    demo: bool = False


class PlayerAppearance(FootballModel):
    player_id: int
    match_id: int
    team_id: int
    started: bool | None = None
    minutes_played: int | None = None


class FootballData(FootballModel):
    leagues: list[League] = Field(default_factory=list)
    teams: list[Team] = Field(default_factory=list)
    players: list[Player] = Field(default_factory=list)
    standings: list[Standing] = Field(default_factory=list)
    matches: list[Match] = Field(default_factory=list)
    lineups: list[MatchLineup] = Field(default_factory=list)
    match_events: list[MatchEvents] = Field(default_factory=list)
    appearances: list[PlayerAppearance] = Field(default_factory=list)
