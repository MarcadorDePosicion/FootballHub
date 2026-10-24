package pl.stasgroch.footballhub

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import pl.stasgroch.footballhub.data.model.*
import pl.stasgroch.footballhub.data.remote.DemoData
import pl.stasgroch.footballhub.ui.screens.*
import pl.stasgroch.footballhub.ui.theme.FootballHubTheme

class DomainSemanticsUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun matchVenueNeverFallsBackToHomeStadium() {
        val demo = DemoData.create()
        val match = demo.matches.first()
        val venue = mutableStateOf<String?>(null)
        rule.setContent { FootballHubTheme {
            MatchDetailsScreen(match.id, demo.copy(matches = listOf(match.copy(venue = venue.value))), {}, {})
        } }
        rule.onNodeWithText("Stadion: Stadion nieznany").assertExists()
        rule.onNodeWithText("Stadion: ${demo.team(match.homeId)!!.stadium}").assertDoesNotExist()
        rule.runOnIdle { venue.value = " " }
        rule.onNodeWithText("Stadion: Stadion nieznany").assertExists()
        rule.runOnIdle { venue.value = "Neutralny stadion" }
        rule.onNodeWithText("Stadion: Neutralny stadion").assertExists()
    }

    @Test fun officialRankAndPenaltyPointsAreShownInProviderOrder() {
        val data = DemoData.create()
        val rows = data.standings.filter { it.leagueId == 1 }.take(2)
        rule.setContent { FootballHubTheme {
            StandingsTable(listOf(rows[0].copy(position = 8, officialPoints = 20), rows[1].copy(position = 7, officialPoints = -2)), data, emptySet(), {})
        } }
        val first = rule.onNodeWithTag("standing-${rows[1].teamId}")
        val second = rule.onNodeWithTag("standing-${rows[0].teamId}")
        assertTrue(first.fetchSemanticsNode().boundsInRoot.top < second.fetchSemanticsNode().boundsInRoot.top)
        rule.onNode(hasText("7") and hasAnyAncestor(hasTestTag("standing-${rows[1].teamId}")), useUnmergedTree = true).assertExists()
        rule.onNodeWithText("-2", useUnmergedTree = true).assertExists()
    }

    @Test fun unknownCardAndUnknownEventUseNeutralSymbol() {
        val data = DemoData.create()
        val match = data.matches.first()
        val kind = mutableStateOf<EventKind?>(EventKind.CARD)
        rule.setContent { FootballHubTheme {
            MatchTimeline(match, data.copy(matchEvents = listOf(MatchEvents(match.id, EventCoverage.COMPLETE,
                listOf(MatchEvent(90001, kind.value, MatchMinute(60), match.homeId))))), {})
        } }
        rule.onNodeWithContentDescription("Nieznane zdarzenie").assertExists()
        rule.onNodeWithContentDescription("Żółta kartka").assertDoesNotExist()
        rule.runOnIdle { kind.value = null }
        rule.onNodeWithContentDescription("Nieznane zdarzenie").assertExists()
    }
}
