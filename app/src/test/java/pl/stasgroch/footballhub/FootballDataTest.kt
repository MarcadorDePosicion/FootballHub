package pl.stasgroch.footballhub

import org.junit.Assert.*
import org.junit.Test
import okhttp3.OkHttpClient
import okhttp3.Request
import com.google.gson.Gson
import pl.stasgroch.footballhub.data.model.*
import pl.stasgroch.footballhub.data.remote.*

class FootballDataTest {
    @Test fun lineupsHaveElevenDistinctPlayersFromTheCorrectTeam() {
        val data = Gson().fromJson(Gson().toJson(DemoData.create()), FootballData::class.java)
        assertEquals(data.players.size, data.players.map { it.id }.distinct().size)
        data.lineups.forEach { lineup ->
            assertTrue(lineup.hasValidFormation())
            val starters = lineup.starterRows!!.flatten()
            assertTrue(starters.intersect(lineup.substitutes.orEmpty().toSet()).isEmpty())
            (starters + lineup.substitutes.orEmpty()).forEach { id ->
                assertEquals(lineup.teamId, data.players.single { it.id == id }.teamId)
            }
            assertTrue(lineup.demo)
        }
        assertTrue(data.lineups.none { it.matchId == 6 })
        assertTrue(data.players.any { it.number == null && it.nationality == null })
    }
    @Test fun incompleteOrInvalidFormationsDoNotProducePitch() {
        assertFalse(MatchLineup(1, 1).hasValidFormation())
        val valid = DemoData.create().lineups.first()
        assertFalse(valid.copy(formation = "4-4-3").hasValidFormation())
        assertFalse(valid.copy(formation = "unknown").hasValidFormation())
        assertFalse(valid.copy(starterRows = listOf(List(11) { 1 })).hasValidFormation())
        val empty = Gson().fromJson("{}", FootballData::class.java)
        assertTrue(empty.players.isEmpty())
        assertTrue(empty.lineups.isEmpty())
    }
    @Test fun mockReturnsValidJsonWithoutNetwork() {
        val client = OkHttpClient.Builder().addInterceptor(MockFootballInterceptor()).build()
        client.newCall(Request.Builder().url("https://football-hub.example/api/football").build()).execute().use {
            assertEquals(200, it.code)
            assertEquals("local-mock", it.header("X-FootballHub-Source"))
            val data = Gson().fromJson(it.body!!.string(), FootballData::class.java)
            assertEquals(12, data.teams.size)
            assertEquals(3, data.leagues.size)
            assertTrue(data.matches.all { m -> data.teams.any { t -> t.id == m.homeId } && data.teams.any { t -> t.id == m.awayId } })
            assertTrue(data.standings.all { s -> s.played == s.won + s.drawn + s.lost })
        }
        client.newCall(Request.Builder().url("https://football-hub.example/unknown").build()).execute().use { assertEquals(404, it.code) }
    }
    @Test fun validatesNotesAndPredictions() {
        assertNotNull(EntryValidation.error("NOTE", "  ", "", ""))
        assertNull(EntryValidation.error("NOTE", "Dobry pressing.", "", ""))
        assertNotNull(EntryValidation.error("PREDICTION", "", "-1", "2"))
        assertNotNull(EntryValidation.error("PREDICTION", "", "2", "100"))
        assertNotNull(EntryValidation.error("PREDICTION", "", "abc", "2"))
        assertNull(EntryValidation.error("PREDICTION", "", "0", "2"))
        assertNotNull(EntryValidation.error("NOTE", "a".repeat(2001), "", ""))
    }
}

