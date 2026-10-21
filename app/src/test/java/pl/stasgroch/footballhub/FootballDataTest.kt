package pl.stasgroch.footballhub

import org.junit.Assert.*
import org.junit.Test
import okhttp3.OkHttpClient
import okhttp3.Request
import com.google.gson.Gson
import pl.stasgroch.footballhub.data.model.*
import pl.stasgroch.footballhub.data.remote.*

class FootballDataTest {
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

