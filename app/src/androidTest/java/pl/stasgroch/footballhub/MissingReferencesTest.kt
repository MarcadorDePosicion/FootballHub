package pl.stasgroch.footballhub

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import pl.stasgroch.footballhub.data.local.*
import pl.stasgroch.footballhub.data.model.*
import pl.stasgroch.footballhub.data.remote.DemoData
import pl.stasgroch.footballhub.ui.screens.*
import pl.stasgroch.footballhub.ui.theme.FootballHubTheme
import java.util.UUID

class MissingReferencesTest {
    @get:Rule val rule = createComposeRule()
    private val demo = DemoData.create()
    private val match = demo.matches.first()

    @Test fun missingHomeAwayAndLeagueBlockMatchDetails() {
        val data = mutableStateOf(demo.copy(teams = demo.teams.filterNot { it.id == match.homeId }))
        rule.setContent { FootballHubTheme { MatchDetailsScreen(match.id, data.value, {}, {}) } }
        fun assertUnavailable() = rule.onNodeWithText("Niepełne dane meczu — drużyna lub liga niedostępna.").assertIsDisplayed()
        assertUnavailable()
        rule.runOnIdle { data.value = demo.copy(teams = demo.teams.filterNot { it.id == match.awayId }) }
        assertUnavailable()
        rule.runOnIdle { data.value = demo.copy(leagues = emptyList()) }
        assertUnavailable()
        rule.runOnIdle { data.value = demo.copy(matches = emptyList()) }
        rule.onNodeWithText("Nie znaleziono meczu.").assertIsDisplayed()
        rule.runOnIdle { data.value = demo }
        rule.onNodeWithText("Nie znaleziono meczu.").assertDoesNotExist()
        rule.onNodeWithText(demo.team(match.homeId)!!.name).assertExists()
    }

    @Test fun missingStarterAndSubstituteUseListInsteadOfMovingPitchPositions() {
        val lineup = demo.lineups.first()
        val fixture = demo.matches.first { it.id == lineup.matchId }
        val absent = setOf(lineup.starterRows!!.flatten().first(), lineup.substitutes!!.first())
        val data = demo.copy(players = demo.players.filterNot { it.id in absent })
        rule.setContent { FootballHubTheme {
            Column(Modifier.verticalScroll(rememberScrollState())) { MatchLineups(fixture, data) }
        } }
        if (lineup.teamId == fixture.awayId) rule.onNodeWithTag("lineup-away").performClick()
        rule.onNodeWithText("Brak pełnego ustawienia — prezentujemy dostępnych zawodników.").assertExists()
        rule.onNodeWithText("Dostępna jest tylko część zawodników rezerwowych.").performScrollTo().assertIsDisplayed()
        absent.forEach { id -> rule.onNodeWithTag("player-$id").assertDoesNotExist() }
    }

    @Test fun unknownEventPlayerIsNotALinkAndUnknownTeamIsNotAssignedToHome() {
        val events = listOf(
            MatchEvent(90001, EventKind.GOAL, MatchMinute(10), match.homeId, playerId = Int.MAX_VALUE),
            MatchEvent(90002, EventKind.CARD, MatchMinute(20), Int.MAX_VALUE, playerId = 101)
        )
        var opened = false
        rule.setContent { FootballHubTheme {
            MatchTimeline(match, demo.copy(matchEvents = listOf(MatchEvents(match.id, EventCoverage.PARTIAL, events))), { opened = true })
        } }
        rule.onNodeWithText("Zawodnik nieznany").assertHasNoClickAction()
        rule.onNodeWithTag("event-90002").assertDoesNotExist()
        rule.onNodeWithText("Pominięto zdarzenia z nieznanym powiązaniem drużyny.").assertIsDisplayed()
        rule.runOnIdle { assertFalse(opened) }
    }

    @Test fun incompleteMatchCardDoesNotOfferNavigation() {
        var opened = false
        rule.setContent { FootballHubTheme {
            MatchCard(match, demo.copy(leagues = emptyList()), { opened = true })
        } }
        rule.onNodeWithText("Niepełne dane meczu — drużyna lub liga niedostępna.").assertHasNoClickAction()
        rule.runOnIdle { assertFalse(opened) }
    }

    @Test fun standingsKeepUnavailableRowWithoutInventingTeam() {
        val row = demo.standings.first()
        val data = mutableStateOf(demo.copy(teams = emptyList()))
        rule.setContent { FootballHubTheme { StandingsTable(listOf(row), data.value, emptySet(), {}) } }
        rule.onNodeWithText("Dane drużyny lub ligi są niedostępne.").assertIsDisplayed().assertHasNoClickAction()
        rule.runOnIdle { data.value = demo.copy(leagues = emptyList()) }
        rule.onNodeWithText("Dane drużyny lub ligi są niedostępne.").assertIsDisplayed()
        rule.runOnIdle { data.value = demo }
        rule.onNodeWithText(demo.team(row.teamId)!!.name).assertExists()
    }

    @Test fun missingPlayerProfileAndOptionalClubAreSafe() {
        val player = demo.players.first()
        val data = mutableStateOf(demo.copy(players = emptyList()))
        rule.setContent { FootballHubTheme { PlayerScreen(player.id, data.value, {}, {}) } }
        rule.onNodeWithText("Nie znaleziono zawodnika.").assertIsDisplayed()
        rule.runOnIdle { data.value = demo.copy(teams = emptyList()) }
        rule.onNodeWithTag("player-profile-${player.id}").assertExists()
        rule.onNodeWithTag("player-club").assertDoesNotExist()
    }

    @Test fun incompleteDataDoesNotRemovePersistedJournalOrFavorites() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "missing-references-${UUID.randomUUID()}.db"
        fun open() = Room.databaseBuilder(context, FootballDatabase::class.java, name).build()
        var db = open()
        try {
            val note = EntryEntity("kept-note", match.id, "NOTE", "Zachowana notatka", null, null, 1)
            val prediction = EntryEntity("kept-prediction", match.id, "PREDICTION", "Zachowany typ", 2, 1, 2)
            val player = demo.players.first()
            db.userDao().save(note)
            db.userDao().save(prediction)
            db.userDao().addFavorite(FavoriteEntity(match.homeId))
            db.userDao().addFavoritePlayer(FavoritePlayerEntity(player.id))
            val entries = db.userDao().entries().first().map { JournalEntry(it.id, it.matchId, it.kind, it.text, it.homeScore, it.awayScore, it.updatedAt) }
            val teams = db.userDao().favorites().first().map { it.teamId }.toSet()
            val players = db.userDao().favoritePlayers().first().map { it.playerId }.toSet()
            val data = mutableStateOf(FootballData())
            val favorites = mutableStateOf(false)
            var callbacks = 0
            rule.setContent { FootballHubTheme {
                if (favorites.value) FavoritesScreen(data.value, teams, { callbacks++ }, { callbacks++ }, {},
                    favoritePlayers = players, openPlayer = { callbacks++ }, removePlayer = { callbacks++ })
                else JournalScreen(data.value, entries, "ALL", {}, { callbacks++ }, { callbacks++ }, { callbacks++ }, {})
            } }
            rule.onNodeWithTag("journal-match-${match.id}").assertIsNotEnabled()
            rule.onNodeWithText("Zachowana notatka").assertExists()
            rule.onNodeWithText("Zachowany typ").assertExists()
            rule.runOnIdle { favorites.value = true }
            rule.onNodeWithText("Część ulubionych drużyn jest niedostępna w aktualnie załadowanych danych. Zapisane ulubione zostały zachowane.").assertExists()
            rule.onNodeWithTag("favorites-tab-1").performClick()
            rule.onNodeWithText("Część ulubionych zawodników jest niedostępna w aktualnie załadowanych danych. Zapisane ulubione zostały zachowane.").assertExists()
            rule.runOnIdle { data.value = demo }
            rule.onNodeWithTag("favorite-player-${player.id}").assertIsDisplayed()
            rule.runOnIdle { assertEquals(0, callbacks) }
            db.close()
            db = open()
            assertEquals(listOf(prediction, note), db.userDao().entries().first())
            assertEquals(teams, db.userDao().favorites().first().map { it.teamId }.toSet())
            assertEquals(players, db.userDao().favoritePlayers().first().map { it.playerId }.toSet())
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
