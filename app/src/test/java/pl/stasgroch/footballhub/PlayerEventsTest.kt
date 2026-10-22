package pl.stasgroch.footballhub

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import com.google.gson.Gson
import pl.stasgroch.footballhub.data.model.*
import pl.stasgroch.footballhub.data.remote.DemoData
import pl.stasgroch.footballhub.data.repository.PlayerIdentityMap

class PlayerEventsTest {
    private val data = Gson().fromJson(Gson().toJson(DemoData.create()), FootballData::class.java)
    @Test fun ageUsesBirthdayAndHandlesMissingInvalidOrFutureDates() {
        val player = Player(1, null, "Test", dateOfBirth = "2000-10-23")
        assertEquals(25, player.age(LocalDate.of(2026, 10, 22)))
        assertEquals(26, player.age(LocalDate.of(2026, 10, 23)))
        assertNull(player.copy(dateOfBirth = null).age())
        assertNull(player.copy(dateOfBirth = "bad").age())
        assertNull(player.copy(dateOfBirth = "2099-01-01").age(LocalDate.of(2026, 1, 1)))
    }
    @Test fun eventScoresReferencesAndSubstitutionsAgreeWithMatches() {
        data.matchEvents.forEach { record ->
            assertTrue(record.demo)
            val match = data.matches.single { it.id == record.matchId }
            var home = 0; var away = 0
            data.eventsFor(match.id).forEach { event ->
                assertTrue(event.teamId in listOf(match.homeId, match.awayId))
                val player = data.players.single { it.id == event.playerId }
                val expectedTeam = if (event.goalKind == GoalKind.OWN_GOAL) {
                    if (event.teamId == match.homeId) match.awayId else match.homeId
                } else event.teamId
                assertEquals(expectedTeam, player.teamId)
                event.assistPlayerId?.let { assertEquals(event.teamId, data.players.single { p -> p.id == it }.teamId) }
                if (event.kind == EventKind.GOAL) {
                    if (event.teamId == match.homeId) home++ else away++
                    assertEquals(home, event.homeScore); assertEquals(away, event.awayScore)
                }
                if (event.kind == EventKind.SUBSTITUTION) {
                    val lineup = data.lineups.single { it.matchId == match.id && it.teamId == event.teamId }
                    assertTrue(event.playerId in lineup.starterRows.orEmpty().flatten())
                    assertTrue(event.incomingPlayerId in lineup.substitutes.orEmpty())
                    assertEquals(event.teamId, data.players.single { it.id == event.incomingPlayerId }.teamId)
                }
            }
            assertEquals(match.homeScore, home); assertEquals(match.awayScore, away)
        }
    }
    @Test fun appearancesExcludeUnusedBenchAndScheduledMatchesAndShareEvents() {
        assertTrue(data.appearances.none { it.matchId in listOf(1, 2, 6) })
        assertTrue(data.appearances.none { it.playerId == 911 })
        assertEquals(false, data.appearances.single { it.playerId == 913 }.started)
        val sub = data.eventsFor(3).single { it.kind == EventKind.SUBSTITUTION }
        assertTrue(sub.involves(913)); assertTrue(sub.involves(908))
        assertEquals("Wejście 63′", sub.playerSummary(913))
        assertEquals("Zejście 63′", sub.playerSummary(908))
        assertEquals("45+2′", data.eventsFor(3).first { it.time.added != null }.time.label)
        assertTrue(MatchMinute(45, 3) < MatchMinute(46))
        assertTrue(data.matchEvents.none { it.matchId == 6 })
        assertEquals(EventCoverage.UNAVAILABLE, MatchEvents(6).coverage)
    }
    @Test fun externalIdsResolveWithoutNameBasedIdentityOrInventedFields() {
        val a = Player(1, null, "Adam Nowak", externalId = ExternalId("football-data.org", "123"))
        val b = Player(2, null, "Adam Nowak", externalId = ExternalId("football-data.org", "456"))
        val ids = PlayerIdentityMap(listOf(a, b))
        assertEquals(1, ids.resolve(a.externalId!!)); assertEquals(2, ids.resolve(b.externalId!!))
        assertNull(ids.resolve(ExternalId("football-data.org", "unknown")))
        assertNull(a.heightCm); assertNull(a.preferredFoot); assertNull(a.age())
        assertTrue(runCatching { PlayerIdentityMap(listOf(a, b.copy(externalId = a.externalId))) }.isFailure)
    }
}
