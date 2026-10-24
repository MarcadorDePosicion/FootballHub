package pl.stasgroch.footballhub

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import pl.stasgroch.footballhub.data.model.*
import pl.stasgroch.footballhub.data.repository.*
import pl.stasgroch.footballhub.ui.FootballViewModel
import pl.stasgroch.footballhub.ui.navigation.FootballApp
import pl.stasgroch.footballhub.ui.theme.FootballHubTheme

class FootballErrorUiTest {
    @get:Rule val rule = createComposeRule()
    private val store = ViewModelStore()
    private var loads = 0
    private var failLoad = false
    private val users = object : UserRepository {
        override val entries = MutableStateFlow<List<JournalEntry>>(emptyList())
        override val favorites = MutableStateFlow<Set<Int>>(emptySet())
        override val favoritePlayers = MutableStateFlow<Set<Int>>(emptySet())
        override suspend fun save(entry: JournalEntry) { error("write") }
        override suspend fun delete(id: String) {}
        override suspend fun favorite(teamId: Int, enabled: Boolean) {}
        override suspend fun favoritePlayer(playerId: Int, enabled: Boolean) {}
    }
    private lateinit var vm: FootballViewModel
    private fun launch() {
        rule.runOnIdle {
            vm = FootballViewModel(FootballRepository(FootballDataSource {
                loads++
                if (failLoad) error("load")
                FootballData()
            }), users, Dispatchers.Main.immediate)
            store.put("vm", vm)
        }
        rule.setContent { FootballHubTheme { FootballApp(vm) } }
    }
    @After fun clear() { rule.runOnIdle { store.clear() } }

    @Test fun refreshErrorOffersWorkingRetry() {
        failLoad = true
        launch()
        rule.onNodeWithText("Spróbuj ponownie").assertIsDisplayed()
        rule.runOnIdle { failLoad = false }
        rule.onNodeWithText("Spróbuj ponownie").performClick()
        rule.onNodeWithText("Spróbuj ponownie").assertDoesNotExist()
        rule.runOnIdle { assertEquals(2, loads) }
    }

    @Test fun validationAndWriteErrorsDoNotOfferRefresh() {
        launch()
        rule.runOnIdle { vm.saveEntry(null, 1, "NOTE", "", "", "") {} }
        rule.onNodeWithText("Wpisz treść notatki.").assertIsDisplayed()
        rule.onNodeWithText("Spróbuj ponownie").assertDoesNotExist()
        rule.runOnIdle { vm.saveEntry(null, 1, "NOTE", "tekst", "", "") {} }
        rule.onNodeWithText("Nie udało się zapisać zmiany. Spróbuj ponownie.").assertIsDisplayed()
        rule.onNodeWithText("Spróbuj ponownie").assertDoesNotExist()
        rule.runOnIdle { assertEquals(1, loads) }
    }
}
