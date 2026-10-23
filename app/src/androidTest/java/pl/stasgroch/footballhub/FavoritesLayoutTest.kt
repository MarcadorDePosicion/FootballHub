package pl.stasgroch.footballhub

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import pl.stasgroch.footballhub.data.remote.DemoData
import pl.stasgroch.footballhub.ui.screens.FavoritesScreen
import pl.stasgroch.footballhub.ui.theme.FootballHubTheme

class FavoritesLayoutTest {
    @get:Rule val rule = createComposeRule()

    @Test fun teamOnlyFavoritesShowPlayerEmptyState() {
        rule.setContent { FootballHubTheme {
            FavoritesScreen(DemoData.create(), setOf(1), {}, {}, {})
        } }
        rule.onNodeWithTag("favorites-tab-1").performClick()
        rule.onNodeWithText("Nie masz jeszcze ulubionych zawodników. Dodaj zawodnika gwiazdką na jego profilu.").assertIsDisplayed()
        rule.onNodeWithTag("favorites-tab-0").performClick()
        rule.onNodeWithText("Arsenal").assertIsDisplayed()
    }

    @Test fun playerOnlyFavoritesShowTeamEmptyState() {
        rule.setContent { FootballHubTheme {
            FavoritesScreen(DemoData.create(), emptySet(), {}, {}, {}, favoritePlayers = setOf(101))
        } }
        rule.onNodeWithText("Nie masz jeszcze ulubionych drużyn. Dodaj drużynę gwiazdką na jej profilu.").assertIsDisplayed()
        rule.onNodeWithTag("favorites-tab-1").performClick()
        rule.onNodeWithTag("favorite-player-101").assertIsDisplayed()
    }
}
