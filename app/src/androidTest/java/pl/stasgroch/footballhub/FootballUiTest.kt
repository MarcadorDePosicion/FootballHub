package pl.stasgroch.footballhub

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import pl.stasgroch.footballhub.data.local.FootballDatabase
import androidx.test.platform.app.InstrumentationRegistry
import java.io.FileInputStream
import java.io.File
import androidx.compose.ui.graphics.asAndroidBitmap
import android.graphics.Bitmap

class FootballUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private var arsenalWasFavorite = false
    @Before fun removePreviousTestEntries() = runBlocking {
        val dao = FootballDatabase.get(rule.activity).userDao()
        arsenalWasFavorite = dao.favorites().first().any { it.teamId == 1 }
        dao.entries().first().filter { it.text.startsWith("Test UI:") }.forEach { dao.delete(it.id) }
    }
    @org.junit.After fun restoreFavorite() = runBlocking {
        val dao = FootballDatabase.get(rule.activity).userDao()
        if (arsenalWasFavorite) dao.addFavorite(pl.stasgroch.footballhub.data.local.FavoriteEntity(1)) else dao.removeFavorite(1)
    }

    private fun waitFor(text: String) {
        rule.waitUntil(15000) { (rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() || rule.onAllNodesWithContentDescription(text).fetchSemanticsNodes().isNotEmpty()) }
    }
    private fun click(text: String) {
        if (rule.onAllNodesWithContentDescription(text).fetchSemanticsNodes().isNotEmpty()) rule.onNodeWithContentDescription(text).performClick()
        else rule.onNodeWithText(text).performClick()
    }
    private fun entryAction(text: String, action: String) {
        val id = runBlocking { FootballDatabase.get(rule.activity).userDao().entries().first().single { it.text == text }.id }
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("entry-$id"))
        rule.onNode(hasContentDescription(action) and hasAnyAncestor(hasTestTag("entry-$id"))).performClick()
    }
    private fun screenshot(name: String) {
        rule.waitForIdle()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val output = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "$name.png")
        output.outputStream().use { stream ->
            val bitmap = if (name == "filter-panel") automation.takeScreenshot() else rule.onRoot().captureToImage().asAndroidBitmap()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        automation.executeShellCommand("mkdir -p /sdcard/Download/footballhub-ui").use { FileInputStream(it.fileDescriptor).readBytes() }
        automation.executeShellCommand("cp ${output.absolutePath} /sdcard/Download/footballhub-ui/$name.png").use { FileInputStream(it.fileDescriptor).readBytes() }
    }
    private fun scrollClick(text: String) {
        val target = hasText(text) and hasSetTextAction().not()
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(target)
        rule.onNode(target).performClick()
    }

    @Test fun teamSearchSuggestionsDismissAndProfile() {
        waitFor("Mecze dnia")
        val search = rule.onNodeWithTag("team-search")
        search.performClick().performTextInput("at")
        rule.onNodeWithTag("search-team-7").assertExists()
        rule.onNodeWithTag("search-team-8").assertExists()
        search.performTextReplacement("ath")
        rule.onNodeWithTag("search-team-7").assertDoesNotExist()
        rule.onNodeWithTag("search-team-8").assertExists()
        rule.onNode(isRoot() and hasAnyDescendant(hasTestTag("team-search"))).performTouchInput { click(androidx.compose.ui.geometry.Offset(8f, height * .6f)) }
        rule.onNodeWithTag("team-search-results").assertDoesNotExist()
        search.assertIsNotFocused().assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("")))
        search.performClick().performTextInput("barcelona")
        rule.onNodeWithTag("search-team-5").performClick()
        rule.onNodeWithTag("team-tab-0").assertIsDisplayed()
        rule.onAllNodesWithText("FC Barcelona").onFirst().assertExists()
    }

    @Test fun matchDatesAndChronologicalOrder() {
        waitFor("Mecze dnia")
        click("Mecze")
        rule.onNodeWithText("Dziś").assertIsDisplayed()
        rule.onNodeWithText("Manchester City").assertDoesNotExist()
        click("Poprzedni dzień")
        rule.onNodeWithText("Wczoraj").assertIsDisplayed()
        rule.onNodeWithText("Manchester City").assertExists()
        click("Poprzedni dzień")
        rule.onNodeWithText("Brak meczów dla wybranych filtrów.").assertIsDisplayed()
        repeat(3) { click("Następny dzień") }
        rule.onNodeWithText("Jutro").assertIsDisplayed()
        rule.onNodeWithText("Raków Częstochowa").assertExists()
        click("Poprzedni dzień")
        click("Filtry")
        rule.onNodeWithText("Trwające").assertDoesNotExist()
        click("Chronologicznie")
        screenshot("filter-panel")
        click("Zastosuj")
        val lech = rule.onNodeWithText("Lech Poznań").fetchSemanticsNode().boundsInRoot.top
        val arsenal = rule.onNodeWithText("Arsenal").fetchSemanticsNode().boundsInRoot.top
        org.junit.Assert.assertTrue(lech < arsenal)
        screenshot("matches-date")
        click("Ligi")
        click("Mecze")
        rule.onNodeWithText("Dziś").assertIsDisplayed()
    }

    @Test fun compactMatchActionsTeamLinksAndHighlightedTable() {
        waitFor("Mecze dnia")
        click("Mecze")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        scrollClick("Liverpool")
        waitFor("Szczegóły meczu")
        rule.onNodeWithTag("match-home-team").performClick()
        rule.onNodeWithTag("team-tab-0").assertExists()
        click("Wstecz")
        rule.onNodeWithTag("match-away-team").performClick()
        rule.onNodeWithTag("team-tab-0").assertExists()
        click("Wstecz")
        rule.onNodeWithText("Twoja perspektywa").assertDoesNotExist()
        val note = rule.onNodeWithText("Dodaj notatkę").fetchSemanticsNode().boundsInRoot
        val prediction = rule.onNodeWithText("Dodaj typ wyniku").fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertEquals(note.center.y, prediction.center.y, 2f)
        org.junit.Assert.assertTrue(note.right < prediction.left)
        screenshot("match-compact-actions")
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("standing-1"))
        rule.onNodeWithTag("standing-1").assertIsSelected()
        rule.onNodeWithTag("standing-2").assertIsSelected()
        rule.onNodeWithTag("standing-3").assertIsNotSelected()
        screenshot("match-table")
    }

    @Test fun teamTableAndCompactFilters() {
        waitFor("Mecze dnia")
        click("Mecze")
        click("Filtry")
        screenshot("filter-panel")
        rule.onNode(hasText("Ekstraklasa") and hasClickAction()).performClick()
        click("Zakończone")
        click("Zastosuj")
        rule.onNodeWithText("Filtry (2)").assertIsDisplayed()
        rule.onNodeWithText("Legia Warszawa").assertIsDisplayed()
        rule.onNodeWithText("Arsenal").assertDoesNotExist()
        click("Filtry (2)")
        click("Nadchodzące")
        click("Anuluj")
        rule.onNodeWithText("Legia Warszawa").assertIsDisplayed()
        click("Ligi")
        click("Mecze")
        rule.onNodeWithText("Filtry (2)").assertIsDisplayed()
        screenshot("compact-filters")
        click("Wyczyść")
        click("Ligi")
        click("Ekstraklasa")
        click("Legia Warszawa")
        rule.onNodeWithTag("team-tab-1").performClick()
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("standing-10"))
        rule.onNodeWithTag("standing-10").assertIsSelected()
        rule.onNodeWithTag("standing-9").assertIsNotSelected()
        screenshot("team-table")
        rule.onNodeWithText("FOOTBALL HUB").assertDoesNotExist()
        rule.onNodeWithTag("team-tab-2").performClick()
        rule.onNodeWithText("1916").assertIsDisplayed()
        rule.onNodeWithText("Stadion Wojska Polskiego").assertIsDisplayed()
        screenshot("team-information")
        rule.onNodeWithTag("team-tab-0").performClick()
        rule.onNodeWithText("Mecze drużyny").assertExists()
    }

    @Test fun navigationFloatsAcrossTabsAndDetailRoutes() {
        waitFor("Mecze dnia")
        fun assertFloating() {
            val bounds = rule.onNodeWithTag("floating-navigation").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
            org.junit.Assert.assertTrue(bounds.left > root.left && bounds.right < root.right)
            org.junit.Assert.assertTrue(bounds.bottom < root.bottom)
        }
        listOf("Start", "Mecze", "Ulubione", "Moje", "Ligi").forEach {
            click(it)
            assertFloating()
        }
        screenshot("floating-leagues")
        click("La Liga")
        assertFloating()
        click("Athletic Bilbao")
        assertFloating()
        screenshot("floating-team")
        click("Mecze")
        click("Poprzedni dzień")
        scrollClick("Athletic Bilbao")
        assertFloating()
        screenshot("floating-match")
        scrollClick("Dodaj notatkę")
        rule.onNodeWithTag("floating-navigation").assertDoesNotExist()
    }

    @Test fun leagueHeaderCollapsesAndTabsOpenRealContent() {
        waitFor("Mecze dnia")
        click("Ligi")
        click("La Liga")
        rule.onNodeWithText("Pkt").assertIsDisplayed()
        val header = rule.onNodeWithTag("league-header")
        val expandedHeight = header.fetchSemanticsNode().boundsInRoot.height
        screenshot("league-expanded")
        rule.onNodeWithTag("league-list").performTouchInput { swipeUp(durationMillis = 600) }
        rule.waitForIdle()
        org.junit.Assert.assertTrue(header.fetchSemanticsNode().boundsInRoot.height < expandedHeight - 20f)
        rule.onNodeWithTag("league-tab-0").assertIsDisplayed()
        screenshot("league-compact")
        rule.onNodeWithTag("floating-navigation").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Zwinięta"))
        rule.onNodeWithTag("league-list").performTouchInput { swipeDown(durationMillis = 600) }
        rule.waitForIdle()
        org.junit.Assert.assertEquals(expandedHeight, header.fetchSemanticsNode().boundsInRoot.height, 2f)
        rule.onNodeWithTag("floating-navigation").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Rozwinięta"))
        rule.onNodeWithTag("league-tab-1").performClick()
        rule.onNodeWithText("Athletic Bilbao").assertIsDisplayed()
        screenshot("league-matches")
        click("Athletic Bilbao")
        waitFor("Szczegóły meczu")
        rule.onNode(hasScrollToIndexAction()).performTouchInput { swipeUp(durationMillis = 600) }
        rule.onNodeWithTag("floating-navigation").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Zwinięta"))
        screenshot("match-navigation-compact")
        rule.onNode(hasScrollToIndexAction()).performTouchInput { swipeDown(durationMillis = 600) }
        rule.onNodeWithTag("floating-navigation").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Rozwinięta"))
    }

    @Test fun floatingNavigationFollowsScrollWithoutResizingList() {
        waitFor("Mecze dnia")
        listOf("Start", "Mecze").forEach { tab ->
            click(tab)
            val list = rule.onNodeWithTag("match-list")
            val bar = rule.onNodeWithTag("floating-navigation")
            bar.assert(androidx.compose.ui.test.SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Rozwinięta"))
            val viewport = list.fetchSemanticsNode().boundsInRoot
            screenshot("$tab-expanded")
            list.performTouchInput { swipeUp(durationMillis = 600) }
            rule.waitForIdle()
            bar.assert(androidx.compose.ui.test.SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Zwinięta"))
            org.junit.Assert.assertEquals(viewport, list.fetchSemanticsNode().boundsInRoot)
            screenshot("$tab-compact")
            val lastTeam = if (tab == "Start") "Athletic Bilbao" else "Real Madrid"
            list.performTouchInput { swipeUp(durationMillis = 600) }
            val lastRow = rule.onNodeWithText(lastTeam).fetchSemanticsNode().boundsInRoot
            org.junit.Assert.assertTrue("Ostatni mecz musi być nad paskiem", lastRow.bottom < bar.fetchSemanticsNode().boundsInRoot.top)
            list.performTouchInput { swipeDown(durationMillis = 600) }
            rule.waitForIdle()
            bar.assert(androidx.compose.ui.test.SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Rozwinięta"))
            list.performScrollToIndex(0)
            bar.assert(androidx.compose.ui.test.SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Rozwinięta"))
        }
        rule.onNodeWithTag("match-list").performTouchInput { swipeUp(durationMillis = 600) }
        click("Ligi")
        rule.onNodeWithText("Rozgrywki").assertIsDisplayed()
    }

    @Test fun navigationCrudAndFavorites() {
        waitFor("Mecze dnia")
        screenshot("home")
        click("Mecze")
        scrollClick("Liverpool")
        waitFor("Szczegóły meczu")
        screenshot("match")
        scrollClick("Dodaj notatkę")
        screenshot("note-form")
        click("Zapisz")
        rule.onNodeWithText("Wpisz treść notatki.").assertIsDisplayed()
        rule.onNodeWithText("Treść notatki").performTextInput("Test UI: pressing")
        rule.onNodeWithText("Zapisz").performScrollTo().performClick()
        waitFor("Szczegóły meczu")
        click("Moje")
        waitFor("Test UI: pressing")
        entryAction("Test UI: pressing", "Edytuj")
        rule.onNodeWithText("Treść notatki").performTextReplacement("Test UI: wysoki pressing")
        rule.onNodeWithText("Zapisz").performScrollTo().performClick()
        waitFor("Test UI: wysoki pressing")
        rule.onAllNodesWithText("ARS – LIV").onFirst().performClick()
        scrollClick("Dodaj typ wyniku")
        rule.onNodeWithText("ARS").performTextInput("2")
        rule.onNodeWithText("LIV").performTextInput("1")
        rule.onNodeWithText("Komentarz (opcjonalnie)").performTextInput("Test UI: typ")
        rule.onNodeWithText("Zapisz").performScrollTo().performClick()
        waitFor("Szczegóły meczu")
        click("Moje")
        click("Typy")
        waitFor("2 : 1")
        entryAction("Test UI: typ", "Edytuj")
        rule.onNodeWithText("ARS").performTextReplacement("3")
        rule.onNodeWithText("Zapisz").performScrollTo().performClick()
        waitFor("3 : 1")
        rule.activityRule.scenario.recreate()
        waitFor("3 : 1")
        click("Wszystkie")
        rule.onAllNodesWithTag("journal-match-1").assertCountEquals(1)
        screenshot("journal-grouped")
        entryAction("Test UI: typ", "Usuń")
        click("Usuń wpis")
        rule.onNodeWithText("Test UI: typ").assertDoesNotExist()
        click("Notatki")
        waitFor("Test UI: wysoki pressing")
        entryAction("Test UI: wysoki pressing", "Usuń")
        click("Usuń wpis")
        click("Ligi")
        click("Premier League")
        rule.onNodeWithText("Pkt").assertIsDisplayed()
        screenshot("standings")
        click("Arsenal")
        if (arsenalWasFavorite) click("Usuń z ulubionych")
        click("Dodaj do ulubionych")
        waitFor("Usuń z ulubionych")
        click("Ulubione")
        waitFor("Arsenal")
        screenshot("favorites-stars")
        rule.activityRule.scenario.recreate()
        waitFor("Arsenal")
        click("Arsenal")
        waitFor("Usuń z ulubionych")
        click("Usuń z ulubionych")
        click("Start")
        waitFor("Mecze dnia")
    }
}












