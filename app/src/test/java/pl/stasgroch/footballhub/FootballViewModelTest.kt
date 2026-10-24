package pl.stasgroch.footballhub

import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import pl.stasgroch.footballhub.data.model.*
import pl.stasgroch.footballhub.data.repository.*
import pl.stasgroch.footballhub.ui.FootballViewModel
import kotlin.coroutines.CoroutineContext

class FootballViewModelTest {
    private val dispatcher = QueueDispatcher()
    private val store = ViewModelStore()
    private val users = FakeUsers()
    private val source = FakeSource()
    private fun create(): FootballViewModel = FootballViewModel(FootballRepository(source), users, dispatcher).also {
        store.put("vm", it)
    }

    @After fun clear() {
        store.clear()
        dispatcher.drain()
    }

    @Test fun refreshLoadsFailsAndRecoversWithoutLosingPreviousData() {
        val gate = CompletableDeferred<Unit>()
        source.gate = gate
        source.data = FootballData(leagues = listOf(League(1, "Liga", "Polska")))
        val vm = create()
        assertTrue(vm.state.value.loading)
        dispatcher.drain()
        vm.refresh()
        assertEquals(1, source.calls)
        gate.complete(Unit)
        dispatcher.drain()
        assertEquals(source.data, vm.state.value.data)
        assertFalse(vm.state.value.loading)
        assertTrue(vm.userDataReady.value)
        source.fail = true
        vm.refresh()
        dispatcher.drain()
        assertNotNull(vm.state.value.error)
        assertFalse(vm.state.value.loading)
        assertEquals(source.data, vm.state.value.data)
        source.fail = false
        vm.refresh()
        dispatcher.drain()
        assertNull(vm.state.value.error)
    }

    @Test fun differentFavoritesProgressWhileEarlierRecordIsSuspended() {
        val vm = create()
        dispatcher.drain()
        val gate = CompletableDeferred<Unit>()
        users.beforeFavorite = { key, _ -> if (key == "team:1") gate.await() }
        vm.favorite(1, true)
        dispatcher.drain()
        vm.favorite(2, true)
        vm.favoritePlayer(1, true)
        vm.favoritePlayer(2, true)
        dispatcher.drain()
        assertEquals(setOf(2), vm.favorites.value)
        assertEquals(setOf(1, 2), vm.favoritePlayers.value)
        assertFalse(vm.saving.value)
        gate.complete(Unit)
        dispatcher.drain()
        assertEquals(setOf(1, 2), vm.favorites.value)
        assertEquals(4, users.favoriteCalls.size)
    }

    @Test fun repeatedTogglesAreOrderedForBothTeamsAndPlayers() {
        val vm = create()
        dispatcher.drain()
        val gate = CompletableDeferred<Unit>()
        users.beforeFavorite = { _, _ -> gate.await() }
        vm.favorite(7, true)
        vm.favorite(7, false)
        vm.favorite(7, true)
        vm.favoritePlayer(7, true)
        vm.favoritePlayer(7, false)
        vm.favoritePlayer(7, true)
        dispatcher.drain()
        assertEquals(2, users.favoriteCalls.size)
        gate.complete(Unit)
        dispatcher.drain()
        for (key in listOf("team:7", "player:7")) {
            assertEquals(listOf(true, false, true), users.favoriteCalls.filter { it.first == key }.map { it.second })
        }
        assertEquals(setOf(7), vm.favorites.value)
        assertEquals(setOf(7), vm.favoritePlayers.value)
    }

    @Test fun validationAndJournalGuardDoNotBlockFavorites() {
        val vm = create()
        dispatcher.drain()
        var callbacks = 0
        vm.saveEntry(null, 1, "NOTE", " ", "", "") { callbacks++ }
        assertNotNull(vm.state.value.error)
        vm.saveEntry(null, 1, "PREDICTION", "", "100", "1") { callbacks++ }
        assertFalse(vm.saving.value)
        assertEquals(0, users.saveCalls)
        val gate = CompletableDeferred<Unit>()
        users.journalGate = gate
        vm.saveEntry(null, 1, "NOTE", "  tekst  ", "", "") { callbacks++ }
        assertTrue(vm.saving.value)
        vm.saveEntry(null, 2, "NOTE", "duplikat", "", "") { callbacks++ }
        vm.favorite(1, true)
        vm.favoritePlayer(1, true)
        dispatcher.drain()
        assertEquals(1, users.saveCalls)
        assertEquals(setOf(1), vm.favorites.value)
        assertEquals(setOf(1), vm.favoritePlayers.value)
        assertEquals(0, callbacks)
        gate.complete(Unit)
        dispatcher.drain()
        assertFalse(vm.saving.value)
        assertEquals("tekst", vm.entries.value.single().text)
        assertEquals(1, callbacks)
        assertNull(vm.state.value.error)
    }

    @Test fun deleteUsesJournalBusyStateAndAllowsFavorites() {
        val vm = create()
        dispatcher.drain()
        users.entries.value = listOf(JournalEntry("entry", 1, "NOTE", "tekst"))
        val gate = CompletableDeferred<Unit>()
        users.journalGate = gate
        vm.deleteEntry("entry")
        vm.saveEntry(null, 1, "NOTE", "ignored", "", "") { fail("Parallel save") }
        vm.favorite(2, true)
        dispatcher.drain()
        assertTrue(vm.saving.value)
        assertEquals(0, users.saveCalls)
        assertEquals(setOf(2), vm.favorites.value)
        gate.complete(Unit)
        dispatcher.drain()
        assertFalse(vm.saving.value)
        assertTrue(vm.entries.value.isEmpty())
    }

    @Test fun unrelatedSuccessDoesNotClearFavoriteErrorAndQueueContinuesAfterFailure() {
        val vm = create()
        dispatcher.drain()
        users.beforeFavorite = { key, enabled -> if (key == "team:1" && enabled) error("write") }
        vm.favorite(1, true)
        dispatcher.drain()
        val failure = vm.state.value.error
        assertNotNull(failure)
        vm.favorite(2, true)
        vm.refresh()
        vm.saveEntry(null, 1, "NOTE", "ok", "", "") {}
        dispatcher.drain()
        assertEquals(failure, vm.state.value.error)
        vm.favorite(1, true)
        vm.favorite(1, false)
        dispatcher.drain()
        assertFalse(1 in vm.favorites.value)
        assertNull(vm.state.value.error)
    }

    @Test fun backgroundRefreshAndFavoriteSuccessPreserveJournalValidationError() {
        val gate = CompletableDeferred<Unit>()
        source.gate = gate
        val vm = create()
        dispatcher.drain()
        vm.saveEntry(null, 1, "NOTE", "", "", "") {}
        val validation = vm.state.value.error
        assertNotNull(validation)
        vm.favoritePlayer(1, true)
        gate.complete(Unit)
        dispatcher.drain()
        assertEquals(validation, vm.state.value.error)
    }

    @Test fun failedJournalSaveResetsBusyAndCanBeRetried() {
        val vm = create()
        dispatcher.drain()
        users.failJournal = true
        vm.saveEntry(null, 1, "NOTE", "ok", "", "") { fail("Failed save callback") }
        dispatcher.drain()
        assertFalse(vm.saving.value)
        assertNotNull(vm.state.value.error)
        users.failJournal = false
        vm.saveEntry(null, 1, "PREDICTION", "", "2", "1") {}
        dispatcher.drain()
        assertNull(vm.state.value.error)
        assertEquals(2, vm.entries.value.single().homeScore)
    }

    @Test fun clearingViewModelCancelsPendingWritesAndQueuedToggles() {
        val vm = create()
        dispatcher.drain()
        val gate = CompletableDeferred<Unit>()
        users.beforeFavorite = { _, _ -> gate.await() }
        users.journalGate = gate
        vm.favorite(1, true)
        vm.favorite(1, false)
        vm.saveEntry(null, 1, "NOTE", "ok", "", "") { fail("Cleared callback") }
        dispatcher.drain()
        store.clear()
        dispatcher.drain()
        gate.complete(Unit)
        dispatcher.drain()
        assertTrue(users.favorites.value.isEmpty())
        assertTrue(users.entries.value.isEmpty())
        assertEquals(1, users.favoriteCalls.size)
    }

    @Test fun olderRefreshFailurePreservesNewerValidationAndRemainsAfterCorrection() {
        val gate = CompletableDeferred<Unit>()
        source.gate = gate
        source.fail = true
        val vm = create()
        dispatcher.drain()
        vm.saveEntry(null, 1, "NOTE", "", "", "") {}
        val validation = vm.state.value.error
        assertEquals(pl.stasgroch.footballhub.ui.FootballErrorKind.VALIDATION, validation?.kind)
        assertEquals("Wpisz treść notatki.", validation?.message)
        gate.complete(Unit)
        dispatcher.drain()
        assertEquals(validation, vm.state.value.error)
        assertFalse(vm.state.value.loading)
        vm.saveEntry(null, 1, "NOTE", "poprawione", "", "") {}
        dispatcher.drain()
        assertEquals(pl.stasgroch.footballhub.ui.FootballErrorKind.REFRESH, vm.state.value.error?.kind)
        source.fail = false
        vm.refresh()
        dispatcher.drain()
        assertNull(vm.state.value.error)
    }

    @Test fun writeFailureIsTypedAndNotClearedByRefresh() {
        val vm = create()
        dispatcher.drain()
        users.failJournal = true
        vm.saveEntry(null, 1, "NOTE", "tekst", "", "") {}
        dispatcher.drain()
        assertEquals(pl.stasgroch.footballhub.ui.FootballErrorKind.WRITE, vm.state.value.error?.kind)
        val failure = vm.state.value.error
        vm.refresh()
        dispatcher.drain()
        assertEquals(failure, vm.state.value.error)
        assertEquals(1, users.saveCalls)
    }

    private class QueueDispatcher : CoroutineDispatcher() {
        private val tasks = ArrayDeque<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) { tasks.addLast(block) }
        fun drain() {
            var count = 0
            while (tasks.isNotEmpty()) {
                check(++count < 10000) { "Coroutine queue did not settle" }
                tasks.removeFirst().run()
            }
        }
    }

    private class FakeSource : FootballDataSource {
        var calls = 0
        var gate: CompletableDeferred<Unit>? = null
        var fail = false
        var data = FootballData()
        override suspend fun load(): FootballData {
            calls++
            gate?.await()
            if (fail) error("load")
            return data
        }
    }

    private class FakeUsers : UserRepository {
        override val entries = MutableStateFlow<List<JournalEntry>>(emptyList())
        override val favorites = MutableStateFlow<Set<Int>>(emptySet())
        override val favoritePlayers = MutableStateFlow<Set<Int>>(emptySet())
        val favoriteCalls = mutableListOf<Pair<String, Boolean>>()
        var beforeFavorite: suspend (String, Boolean) -> Unit = { _, _ -> }
        var journalGate: CompletableDeferred<Unit>? = null
        var failJournal = false
        var saveCalls = 0
        override suspend fun favorite(teamId: Int, enabled: Boolean) {
            favoriteCalls += "team:$teamId" to enabled
            beforeFavorite("team:$teamId", enabled)
            favorites.value = if (enabled) favorites.value + teamId else favorites.value - teamId
        }
        override suspend fun favoritePlayer(playerId: Int, enabled: Boolean) {
            favoriteCalls += "player:$playerId" to enabled
            beforeFavorite("player:$playerId", enabled)
            favoritePlayers.value = if (enabled) favoritePlayers.value + playerId else favoritePlayers.value - playerId
        }
        override suspend fun save(entry: JournalEntry) {
            saveCalls++
            journalGate?.await()
            if (failJournal) error("save")
            entries.value = entries.value.filterNot { it.id == entry.id } + entry
        }
        override suspend fun delete(id: String) {
            journalGate?.await()
            if (failJournal) error("delete")
            entries.value = entries.value.filterNot { it.id == id }
        }
    }
}

