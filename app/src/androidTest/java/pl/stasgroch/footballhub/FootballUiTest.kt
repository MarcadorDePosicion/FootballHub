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
        if (text in listOf("Start", "Mecze", "Ligi", "Ulubione", "Moje")) {
            repeat(6) {
                if (rule.onAllNodesWithTag("floating-navigation").fetchSemanticsNodes().isEmpty()) {
                    androidx.test.espresso.Espresso.pressBack(); rule.waitForIdle()
                }
            }
        }
        if (rule.onAllNodesWithContentDescription(text).fetchSemanticsNodes().isNotEmpty()) rule.onNodeWithContentDescription(text).performClick()
        else rule.onNodeWithText(text).performClick()
    }
    private fun entryAction(text: String, action: String) {
        val id = runBlocking { FootballDatabase.get(rule.activity).userDao().entries().first().single { it.text == text }.id }
        rule.onNode(hasScrollToIndexAction() and androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange)).performScrollToNode(hasTestTag("entry-$id"))
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
        rule.onNode(hasScrollToIndexAction() and androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange)).performScrollToNode(target)
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

    @Test fun reselectMainTabScrollsUpAndPreservesFiltersAndDate() {
        waitFor("Mecze dnia")
        rule.onNodeWithTag("match-list").performTouchInput { swipeUp() }
        click("Start")
        rule.waitForIdle()
        rule.onNodeWithText("Mecze dnia").assertIsDisplayed()
        click("Mecze")
        click("Poprzedni dzień")
        click("Filtry")
        click("Zakończone")
        click("Zastosuj")
        rule.onNodeWithTag("match-list").performTouchInput { swipeUp() }
        click("Mecze")
        rule.waitForIdle()
        rule.onNodeWithText("Wczoraj").assertIsDisplayed()
        rule.onNodeWithText("Filtry (1)").assertIsDisplayed()
        click("Mecze")
        rule.onNodeWithText("Wczoraj").assertIsDisplayed()
        screenshot("navigation-reselected")
        scrollClick("Manchester City")
        rule.onNodeWithTag("floating-navigation").assertDoesNotExist()
        click("Wstecz")
        rule.onNodeWithTag("floating-navigation").assertIsDisplayed()
        rule.onNodeWithContentDescription("Mecze").assertIsSelected()
        rule.onNodeWithText("Wczoraj").assertIsDisplayed()
    }

    @Test fun dayPagesFollowFingerBeforeRelease() {
        waitFor("Mecze dnia")
        click("Mecze")
        val title = rule.onNodeWithText("Centrum meczowe").fetchSemanticsNode().boundsInRoot
        val pager = rule.onNodeWithTag("days-pager")
        pager.performTouchInput {
            down(androidx.compose.ui.geometry.Offset(width * .85f, height * .3f))
            moveBy(androidx.compose.ui.geometry.Offset(-width * .4f, 0f), 400)
        }
        rule.waitForIdle()
        rule.onNodeWithText("Liverpool").assertIsDisplayed()
        rule.onNodeWithText("Raków Częstochowa").assertIsDisplayed()
        org.junit.Assert.assertEquals(title, rule.onNodeWithText("Centrum meczowe").fetchSemanticsNode().boundsInRoot)
        screenshot("days-mid-drag")
        pager.performTouchInput { cancel() }
        rule.waitForIdle()
    }

    @Test fun horizontalGesturesChangeDaysAndDetailTabsWithoutChangingMainScreen() {
        waitFor("Mecze dnia")
        click("Mecze")
        val list = rule.onNodeWithTag("match-list")
        list.performTouchInput { swipeRight() }
        rule.onNodeWithText("Wczoraj").assertIsDisplayed()
        list.performTouchInput { swipeLeft() }
        rule.onNodeWithText("Dziś").assertIsDisplayed()
        click("Następny dzień")
        list.performTouchInput { swipeLeft() }
        rule.onNodeWithText("Jutro").assertDoesNotExist()
        list.performTouchInput { swipeRight() }
        rule.onNodeWithText("Jutro").assertIsDisplayed()
        list.performTouchInput { swipeRight() }
        // A tiny drag and a vertical gesture must not change the day.
        list.performTouchInput { swipe(androidx.compose.ui.geometry.Offset(4f, centerY), androidx.compose.ui.geometry.Offset(16f, centerY), 250) }
        rule.onNodeWithText("Dziś").assertIsDisplayed()
        list.performTouchInput { swipeUp() }
        click("Mecze")
        rule.onNodeWithText("Dziś").assertIsDisplayed()
        scrollClick("Liverpool")
        val detail = rule.onNodeWithTag("match-detail-list")
        detail.performScrollToNode(hasTestTag("match-pages"))
        val pages = rule.onNodeWithTag("match-pages")
        val titleBounds = rule.onNodeWithText("Szczegóły meczu").fetchSemanticsNode().boundsInRoot
        pages.performTouchInput {
            down(androidx.compose.ui.geometry.Offset(width * .85f, height * .2f))
            moveBy(androidx.compose.ui.geometry.Offset(-width * .4f, 0f), 400)
        }
        rule.waitForIdle()
        rule.onNodeWithText("Tabela ligi").assertIsDisplayed()
        org.junit.Assert.assertEquals(titleBounds, rule.onNodeWithText("Szczegóły meczu").fetchSemanticsNode().boundsInRoot)
        screenshot("match-mid-drag")
        pages.performTouchInput { cancel() }
        rule.waitForIdle()
        pages.performTouchInput { swipeLeft() }
        detail.performScrollToNode(hasTestTag("match-tab-1"))
        rule.onNodeWithTag("match-tab-1").assertIsSelected()
        rule.onNodeWithTag("match-tab-0").performClick()
        pages.performTouchInput { swipeLeft() }
        rule.onNodeWithTag("match-tab-1").assertIsSelected()
        pages.performTouchInput { swipeRight() }
        rule.onNodeWithTag("match-tab-0").assertIsSelected()
        click("Ligi")
        click("La Liga")
        val league = rule.onNodeWithTag("league-list")
        league.performTouchInput { swipeLeft() }
        rule.onNodeWithTag("league-tab-1").assertIsSelected()
        league.performTouchInput { swipeRight() }
        rule.onNodeWithTag("league-tab-0").assertIsSelected()
        click("FC Barcelona")
        val team = rule.onNodeWithTag("team-list")
        team.performTouchInput { swipeLeft() }
        rule.onNodeWithTag("team-tab-1").assertIsSelected()
        team.performTouchInput { swipeLeft() }
        rule.onNodeWithTag("team-tab-2").assertIsSelected()
        team.performTouchInput { swipeRight() }
        rule.onNodeWithTag("team-tab-1").assertIsSelected()
        rule.onNodeWithTag("team-tab-3").performClick()
        team.performScrollToNode(hasTestTag("player-513"))
        val playerTop = rule.onNodeWithTag("player-513").fetchSemanticsNode().boundsInRoot.top
        team.performTouchInput { swipeRight() }
        rule.onNodeWithTag("team-tab-2").assertIsSelected()
        team.performTouchInput { swipeLeft() }
        rule.onNodeWithTag("team-tab-3").assertIsSelected()
        org.junit.Assert.assertEquals(playerTop, rule.onNodeWithTag("player-513").fetchSemanticsNode().boundsInRoot.top, 2f)
        rule.onNodeWithTag("floating-navigation").assertDoesNotExist()
        screenshot("team-swipe-no-navigation")
    }

    @Test fun playerSearchRosterAndLineups() {
        waitFor("Mecze dnia")
        rule.onNodeWithTag("team-search").performClick().performTextInput("bruno")
        rule.onNodeWithTag("search-filter-1").performClick()
        rule.onNodeWithTag("search-player-101").assertDoesNotExist()
        rule.onNodeWithTag("search-filter-2").performClick()
        rule.onNodeWithTag("search-player-101").performClick()
        rule.onNodeWithTag("player-profile-101").assertIsDisplayed()
        scrollClick("Arsenal")
        rule.onNodeWithTag("team-tab-3").performClick()
        rule.onNodeWithTag("team-tab-3").assertIsSelected()
        rule.onNode(hasScrollToIndexAction() and androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange)).performScrollToNode(hasTestTag("player-101"))
        rule.onNodeWithText("Bruno Baran").assertIsDisplayed()
        screenshot("team-players")
        click("Mecze")
        scrollClick("Liverpool")
        rule.onNode(hasScrollToIndexAction() and androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange)).performScrollToNode(hasTestTag("match-tab-0"))
        rule.onNodeWithTag("match-tab-0").assertIsSelected()
        rule.onNode(hasScrollToIndexAction() and androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange)).performScrollToNode(hasTestTag("football-pitch"))
        rule.onNodeWithTag("football-pitch").assertExists()
        rule.onNode(hasScrollToIndexAction() and androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange)).performTouchInput { swipeUp(durationMillis = 500) }
        screenshot("match-lineup")
        rule.onNode(hasScrollToIndexAction() and androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange)).performScrollToNode(hasTestTag("lineup-away"))
        rule.onNodeWithTag("lineup-away").performClick()
        rule.onNodeWithText("4-3-3").assertExists()
        click("Mecze")
        click("Następny dzień")
        scrollClick("Raków Częstochowa")
        rule.onNode(hasScrollToIndexAction() and androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange)).performScrollToNode(hasText("Skład nie został jeszcze udostępniony."))
        rule.onNodeWithText("Skład nie został jeszcze udostępniony.").assertIsDisplayed()
        rule.onNodeWithTag("football-pitch").assertDoesNotExist()
        screenshot("lineup-unavailable")
    }

    @Test fun playerSearchRestoresQueryAndFilterAndShowsMissingFieldsAndHistory() {
        waitFor("Mecze dnia")
        val search = rule.onNodeWithTag("team-search")
        search.performClick().performTextInput("niko")
        rule.onNodeWithTag("search-filter-2").performClick()
        rule.onNodeWithTag("search-player-113").performScrollTo().performClick()
        rule.onNodeWithTag("player-profile-113").assertIsDisplayed()
        rule.onNodeWithTag("floating-navigation").assertDoesNotExist()
        rule.onNodeWithText("Data urodzenia i wiek nie są dostępne.").assertIsDisplayed()
        rule.onNodeWithText("Numer koszulki").assertDoesNotExist()
        screenshot("player-missing-fields")
        rule.onNodeWithTag("player-tab-1").performClick()
        rule.onNodeWithText("Brak dostępnej historii występów.").assertIsDisplayed()
        androidx.test.espresso.Espresso.pressBack()
        rule.waitForIdle()
        search.assertTextContains("niko")
        rule.onNodeWithTag("search-filter-2").assertIsSelected()
        rule.onNodeWithTag("search-player-113").assertExists()
        search.performImeAction()
        rule.onNodeWithTag("team-search-results").assertDoesNotExist()
        click("Ligi")
        click("Ekstraklasa")
        click("Lech Poznań")
        rule.onNodeWithTag("team-tab-3").performClick()
        val teamList = rule.onNodeWithTag("team-list")
        teamList.performScrollToNode(hasTestTag("player-913"))
        val before = rule.onNodeWithTag("player-913").fetchSemanticsNode().boundsInRoot.top
        rule.onNodeWithTag("player-913").performClick()
        rule.onNodeWithTag("player-profile-913").assertIsDisplayed()
        rule.onNodeWithTag("player-tab-1").performClick()
        rule.onNodeWithTag("appearance-3").assertIsDisplayed()
        rule.onNodeWithText("Wejście 63′").assertIsDisplayed()
        rule.onNodeWithText("Gol 78′").assertIsDisplayed()
        screenshot("player-appearance")
        androidx.test.espresso.Espresso.pressBack()
        rule.onNodeWithTag("team-tab-3").assertIsSelected()
        org.junit.Assert.assertEquals(before, rule.onNodeWithTag("player-913").fetchSemanticsNode().boundsInRoot.top, 2f)
    }

    @Test fun matchEventsPitchBenchAndHistoryOpenTheSamePlayers() {
        waitFor("Mecze dnia")
        click("Mecze")
        scrollClick("Lech Poznań")
        val list = rule.onNodeWithTag("match-detail-list")
        list.performScrollToNode(hasTestTag("event-player-913"))
        rule.onNodeWithTag("event-player-913").performClick()
        rule.onNodeWithTag("player-profile-913").assertIsDisplayed()
        rule.onNodeWithTag("player-tab-1").performClick()
        rule.onNodeWithTag("appearance-3").performClick()
        list.performScrollToNode(hasTestTag("match-timeline-toggle"))
        rule.onNodeWithTag("match-timeline-toggle").performClick()
        list.performScrollToNode(hasTestTag("event-304"))
        screenshot("match-timeline")
        rule.onNode(hasTestTag("event-player-913") and hasAnyAncestor(hasTestTag("event-304"))).performClick()
        rule.onNodeWithTag("player-profile-913").assertIsDisplayed()
        androidx.test.espresso.Espresso.pressBack()
        list.performScrollToNode(hasTestTag("match-pages"))
        rule.onNodeWithTag("pitch-player-908").performScrollTo()
        val pitchBefore = rule.onNodeWithTag("pitch-player-908").fetchSemanticsNode().boundsInRoot.top
        rule.onNodeWithTag("pitch-player-908").performClick()
        rule.onNodeWithTag("player-profile-908").assertIsDisplayed()
        androidx.test.espresso.Espresso.pressBack()
        org.junit.Assert.assertEquals(pitchBefore, rule.onNodeWithTag("pitch-player-908").fetchSemanticsNode().boundsInRoot.top, 2f)
        repeat(6) {
            if (!rule.onNodeWithTag("player-913").isDisplayed()) list.performTouchInput { swipeUp(durationMillis = 500) }
        }
        rule.onNodeWithTag("player-913").assertIsDisplayed()
        screenshot("lineup-bench-events")
        rule.onNodeWithTag("player-913").performTouchInput { click() }
        rule.onNodeWithTag("player-profile-913").assertIsDisplayed()
        androidx.test.espresso.Espresso.pressBack()
        rule.onNodeWithTag("player-913").assertIsDisplayed()
        screenshot("lineup-bench-events")
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
        rule.onNode(hasScrollToIndexAction() and androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange)).performScrollToNode(hasTestTag("match-tab-1"))
        rule.onNodeWithTag("match-tab-1").performClick()
        rule.onNode(hasScrollToIndexAction() and androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange)).performScrollToNode(hasTestTag("standing-1"))
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
        rule.onNode(hasScrollToIndexAction() and androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange)).performScrollToNode(hasTestTag("standing-10"))
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

    @Test fun navigationFloatsOnMainScreensAndHidesOnDetails() {
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
            click(it)
            assertFloating()
            rule.onNodeWithContentDescription(it).assertIsSelected()
        }
        screenshot("floating-leagues")
        click("La Liga")
        rule.onNodeWithTag("floating-navigation").assertDoesNotExist()
        click("Athletic Bilbao")
        rule.onNodeWithTag("floating-navigation").assertDoesNotExist()
        screenshot("floating-team")
        click("Mecze")
        click("Poprzedni dzień")
        scrollClick("Athletic Bilbao")
        rule.onNodeWithTag("floating-navigation").assertDoesNotExist()
        screenshot("floating-match")
        scrollClick("Dodaj notatkę")
        rule.onNodeWithTag("floating-navigation").assertDoesNotExist()
    }

    @Test fun leagueHeaderStaysCompactAndTabsOpenRealContent() {
        waitFor("Mecze dnia")
        click("Ligi")
        click("La Liga")
        rule.onNodeWithText("Pkt").assertIsDisplayed()
        val header = rule.onNodeWithTag("league-header")
        val expandedHeight = header.fetchSemanticsNode().boundsInRoot.height
        screenshot("league-expanded")
        rule.onNodeWithTag("league-list").performTouchInput { swipeUp(durationMillis = 600) }
        rule.waitForIdle()
        org.junit.Assert.assertEquals(expandedHeight, header.fetchSemanticsNode().boundsInRoot.height, 2f)
        rule.onNodeWithTag("league-tab-0").assertIsDisplayed()
        screenshot("league-compact")
        rule.onNodeWithTag("floating-navigation").assertDoesNotExist()
        rule.onNodeWithTag("league-list").performTouchInput { swipeDown(durationMillis = 600) }
        rule.waitForIdle()
        org.junit.Assert.assertEquals(expandedHeight, header.fetchSemanticsNode().boundsInRoot.height, 2f)
        rule.onNodeWithTag("floating-navigation").assertDoesNotExist()
        rule.onNodeWithTag("league-tab-1").performClick()
        rule.onNodeWithText("Athletic Bilbao").assertIsDisplayed()
        screenshot("league-matches")
        click("Athletic Bilbao")
        waitFor("Szczegóły meczu")
        rule.onNode(hasScrollToIndexAction() and androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange)).performTouchInput { swipeUp(durationMillis = 600) }
        rule.onNodeWithTag("floating-navigation").assertDoesNotExist()
        screenshot("match-navigation-compact")
        rule.onNode(hasScrollToIndexAction() and androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange)).performTouchInput { swipeDown(durationMillis = 600) }
        rule.onNodeWithTag("floating-navigation").assertDoesNotExist()
    }

    @Test fun floatingNavigationFollowsScrollWithoutResizingList() {
        waitFor("Mecze dnia")
        listOf("Start", "Mecze").forEach { tab ->
            click(tab)
            val list = rule.onNodeWithTag("match-list")
            val bar = rule.onNodeWithTag("floating-navigation")
            bar.assert(androidx.compose.ui.test.SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Rozwinięta"))
            val viewport = list.fetchSemanticsNode().boundsInRoot
            val expandedBounds = bar.fetchSemanticsNode().boundsInRoot
            screenshot("$tab-expanded")
            list.performTouchInput { swipeUp(durationMillis = 600) }
            rule.waitForIdle()
            bar.assert(androidx.compose.ui.test.SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Zwinięta"))
            val compactBounds = bar.fetchSemanticsNode().boundsInRoot
            org.junit.Assert.assertTrue(compactBounds.height < expandedBounds.height)
            org.junit.Assert.assertTrue(compactBounds.width < expandedBounds.width)
            listOf("Start", "Mecze", "Ligi", "Ulubione", "Moje").forEach { name ->
                val bounds = rule.onNodeWithContentDescription(name).fetchSemanticsNode().boundsInRoot
                org.junit.Assert.assertTrue(bounds.height / rule.activity.resources.displayMetrics.density >= 47.5f)
            }
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












