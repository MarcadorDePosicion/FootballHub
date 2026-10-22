package pl.stasgroch.footballhub

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import pl.stasgroch.footballhub.data.model.*
import pl.stasgroch.footballhub.data.remote.DemoData
import pl.stasgroch.footballhub.ui.screens.*
import pl.stasgroch.footballhub.ui.theme.FootballHubTheme

class LineupLayoutTest {
    @get:Rule val rule = createComposeRule()

    @Test fun missingFormationAndOptionalPlayerFieldsStayReadable() {
        val original = DemoData.create()
        val lineup = original.lineups.first().copy(formation = null)
        val data = original.copy(lineups = listOf(lineup), players = original.players.map { it.copy(number = null, nationality = null, position = null) })
        rule.setContent { FootballHubTheme {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.5f)) {
                LazyColumn(Modifier.width(320.dp)) { item { MatchLineups(data.matches.first(), data) } }
            }
        } }
        rule.onNodeWithTag("football-pitch").assertDoesNotExist()
        rule.onNodeWithText("Brak pełnego ustawienia — prezentujemy dostępnych zawodników.").assertExists()
        rule.onNodeWithTag("player-100").assertExists()
    }

    @Test fun emptyRosterExplainsMissingData() {
        rule.setContent { FootballHubTheme { LazyColumn { playerRoster(emptyList()) } } }
        rule.onNodeWithText("Lista zawodników nie została jeszcze udostępniona.").assertIsDisplayed()
    }
}
