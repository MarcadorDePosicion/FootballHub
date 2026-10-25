"""Bounded, sequential provider access. Never expose upstream exception text."""
import asyncio
from collections import deque
from datetime import date, timedelta
import time

import httpx

from .config import Settings


class FootballError(Exception):
    def __init__(self, status: int, detail: str, *, transient: bool = False):
        super().__init__(detail)
        self.status = status
        self.detail = detail
        self.transient = transient


def invalid_payload() -> FootballError:
    return FootballError(502, "Invalid football provider response")


def confirmed_competition(code: str, teams: dict, matches: dict) -> bool:
    """A standings 404 is optional only after positive evidence for this code."""
    competition = teams.get("competition")
    if not isinstance(competition, dict) or competition.get("code") != code:
        return False
    league_id = competition.get("id")
    if type(league_id) is not int or league_id <= 0:
        return False
    if not isinstance(teams.get("teams"), list) or not isinstance(matches.get("matches"), list):
        return False
    return all(isinstance(match, dict) and isinstance(match.get("competition"), dict)
               and match["competition"].get("id") == league_id
               and match["competition"].get("code") == code for match in matches["matches"])


class RequestLimiter:
    """One sliding window shared by every competition in an application instance."""
    def __init__(self, limit: int, clock=time.monotonic):
        self.limit = limit
        self.clock = clock
        self.requests = deque()
        self.lock = asyncio.Lock()

    async def acquire(self):
        async with self.lock:
            now = self.clock()
            while self.requests and now - self.requests[0] >= 60:
                self.requests.popleft()
            if len(self.requests) >= self.limit:
                raise FootballError(503, "Football request limit reached", transient=True)
            self.requests.append(now)


class FootballClient:
    def __init__(self, settings: Settings, *, transport=None, client=None, clock=time.monotonic):
        self.settings = settings
        self.limiter = RequestLimiter(settings.football_data_max_requests_per_minute, clock)
        self._owns_client = client is None
        self.client = client if client is not None else httpx.AsyncClient(
            transport=transport, timeout=httpx.Timeout(10.0), follow_redirects=False,
        )

    async def close(self):
        if self._owns_client:
            await self.client.aclose()

    async def request(self, path: str, *, params=None, unfold=False, optional=False):
        await self.limiter.acquire()
        headers = {"X-Auth-Token": self.settings.football_data_api_key.get_secret_value()}
        if unfold:
            headers.update({f"X-Unfold-{name}": "true" for name in ("Lineups", "Bookings", "Subs", "Goals")})
        try:
            response = await self.client.get(self.settings.football_data_base_url + path,
                                             params=params, headers=headers, timeout=10.0,
                                             follow_redirects=False)
        except httpx.RequestError:
            raise FootballError(503, "Football provider is unavailable", transient=True) from None
        if response.status_code == 404 and optional:
            return None
        if response.status_code in (401, 403):
            raise FootballError(503, "Football provider access denied")
        if response.status_code == 429 or response.status_code >= 500:
            raise FootballError(503, "Football provider is unavailable", transient=True)
        if response.status_code != 200:
            raise FootballError(502, "Football provider request failed")
        try:
            payload = response.json()
        except (ValueError, UnicodeError):
            raise invalid_payload() from None
        if not isinstance(payload, dict):
            raise invalid_payload()
        return payload

    async def fetch(self, reference_date: date):
        params = {
            "dateFrom": (reference_date - timedelta(days=self.settings.football_data_days_back)).isoformat(),
            "dateTo": (reference_date + timedelta(days=self.settings.football_data_days_forward)).isoformat(),
        }
        packages = []
        for code in self.settings.football_data_competitions.split(","):
            prefix = f"/competitions/{code}"
            teams = await self.request(prefix + "/teams")
            matches = await self.request(prefix + "/matches", params=params, unfold=True)
            standings = await self.request(prefix + "/standings",
                                           optional=confirmed_competition(code, teams, matches))
            packages.append((teams, matches, standings))
        return packages
