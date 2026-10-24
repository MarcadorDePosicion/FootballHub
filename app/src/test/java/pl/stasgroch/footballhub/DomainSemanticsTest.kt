package pl.stasgroch.footballhub

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test
import pl.stasgroch.footballhub.data.model.*
import pl.stasgroch.footballhub.data.remote.DemoData
import pl.stasgroch.footballhub.ui.screens.statusLabel

class DomainSemanticsTest {
    private val gson = Gson()
    // Application-domain JSON, NOT football-data.org's nested team/draw/points response.
    private val standingJson = """{"leagueId":1,"teamId":1,"played":8,"won":6,"drawn":1,"lost":1,"goalsFor":19,"goalsAgainst":6}"""

    @Test fun domainJsonWithoutOfficialFieldsKeepsNulls() {
        val row = gson.fromJson(standingJson, Standing::class.java)
        assertNull(row.position)
        assertNull(row.officialPoints)
        assertEquals(19, row.displayPoints)
    }

    @Test fun domainJsonUsesOfficialPointsIncludingPenaltiesAndZero() {
        val row = gson.fromJson(standingJson.dropLast(1) + ",\"position\":4,\"officialPoints\":-2}", Standing::class.java)
        assertEquals(4, row.position)
        assertEquals(-2, row.displayPoints)
        assertEquals(19, row.points)
        assertEquals(0, row.copy(officialPoints = 0).displayPoints)
    }

    @Test fun officialRanksOverridePointsAndNeedNotStartAtOne() {
        val row = gson.fromJson(standingJson, Standing::class.java)
        val rows = listOf(row.copy(position = 8, officialPoints = 100), row.copy(teamId = 2, position = 7, officialPoints = -2))
        assertTrue(rows.hasOfficialPositions())
        assertEquals(listOf(2, 1), rows.orderedStandings().map { it.teamId })
    }

    @Test fun missingPartialDuplicateAndInvalidRanksFallBackStably() {
        val row = gson.fromJson(standingJson, Standing::class.java)
        listOf(null to null, 1 to null, 1 to 1, 0 to 2, -1 to 2).forEach { (a, b) ->
            val rows = listOf(row.copy(position = a, officialPoints = -2), row.copy(teamId = 2, position = b))
            assertFalse(rows.hasOfficialPositions())
            assertEquals(listOf(2, 1), rows.orderedStandings().map { it.teamId })
        }
        val tied = listOf(row.copy(teamId = 2), row)
        assertEquals(tied, tied.orderedStandings())
        assertEquals(emptyList<Standing>(), emptyList<Standing>().orderedStandings())
    }

    @Test fun everyStatusHasExplicitLabelClassificationAndFiltering() {
        val labels = linkedMapOf("SCHEDULED" to "Planowany", "TIMED" to "Planowany", "IN_PLAY" to "Trwa",
            "PAUSED" to "Przerwa", "EXTRA_TIME" to "Dogrywka", "PENALTY_SHOOTOUT" to "Rzuty karne",
            "FINISHED" to "Koniec", "POSTPONED" to "Przełożony", "SUSPENDED" to "Wstrzymany",
            "CANCELLED" to "Odwołany", "AWARDED" to "Walkower", "unexpected" to "Status nieznany",
            "LIVE" to "Trwa", "IN_PROGRESS" to "Trwa")
        val fixture = DemoData.create().matches.first()
        labels.forEach { (raw, label) ->
            val match = fixture.copy(status = raw)
            val scheduled = raw in listOf("SCHEDULED", "TIMED")
            val live = raw in listOf("IN_PLAY", "PAUSED", "EXTRA_TIME", "PENALTY_SHOOTOUT", "LIVE", "IN_PROGRESS")
            val finished = raw in listOf("FINISHED", "AWARDED")
            assertEquals(raw, label, statusLabel(raw))
            assertEquals(raw, scheduled, match.matchStatus.isScheduled)
            assertEquals(raw, live, match.matchStatus.isLive)
            assertEquals(raw, finished, match.matchStatus.isFinished)
            assertEquals(raw, raw == "FINISHED", match.matchStatus.isPlayed)
            assertEquals(raw, scheduled || live, match.matchStatus.canPredict)
            assertEquals(scheduled, match.matchesStatusFilter("SCHEDULED"))
            assertEquals(finished, match.matchesStatusFilter("FINISHED"))
            assertEquals(live, match.matchesStatusFilter("LIVE"))
            assertTrue(match.matchesStatusFilter("ALL"))
            assertFalse(match.matchesStatusFilter("unexpected"))
        }
        assertEquals(MatchStatusKind.UNKNOWN, MatchStatusKind.from(null))
    }

    @Test fun venueComesFromMatchAndDemoSuppliesItExplicitly() {
        val data = DemoData.create()
        data.matches.forEach { assertEquals(data.team(it.homeId)?.stadium, it.venue) }
        val match = data.matches.first()
        assertEquals("Neutralny stadion", match.copy(venue = "Neutralny stadion").venueLabel)
        listOf(null, "", "  ").forEach { assertEquals("Stadion nieznany", match.copy(venue = it).venueLabel) }
        val json = gson.toJsonTree(match).asJsonObject.apply { remove("venue") }
        assertNull(gson.fromJson(json, Match::class.java).venue)
    }

    @Test fun bothSecondYellowNamesDismissPlayerAndSurviveRoundTrip() {
        listOf("SECOND_YELLOW", "YELLOW_RED", "RED").forEach { raw ->
            val event = gson.fromJson("""{"id":1,"kind":"CARD","time":{"minute":60},"teamId":1,"playerId":100,"cardKind":"$raw"}""", MatchEvent::class.java)
            assertEquals(if (raw == "RED") CardKind.RED else CardKind.SECOND_YELLOW, event.cardKind)
            val appearance = PlayerAppearance(100, 1, 1, started = true)
            assertEquals(60, appearance.playedMinutes(MatchEvents(1, EventCoverage.COMPLETE, listOf(event)), true))
            assertEquals(event, gson.fromJson(gson.toJson(event), MatchEvent::class.java))
        }
    }

    @Test fun unknownEventAndCardRemainUnknownInsteadOfYellowOrSubstitution() {
        val unknown = gson.fromJson("""{"id":1,"kind":"NEW_EVENT","time":{"minute":60},"teamId":1,"playerId":100,"cardKind":"NEW_CARD"}""", MatchEvent::class.java)
        assertNull(unknown.kind)
        assertNull(unknown.cardKind)
        assertTrue(unknown.playerSummary(100).startsWith("Nieznane zdarzenie"))
        val card = unknown.copy(kind = EventKind.CARD)
        assertNull(card.cardKind)
        assertEquals(90, PlayerAppearance(100, 1, 1, started = true).playedMinutes(MatchEvents(1, EventCoverage.COMPLETE, listOf(card)), true))
    }
}
