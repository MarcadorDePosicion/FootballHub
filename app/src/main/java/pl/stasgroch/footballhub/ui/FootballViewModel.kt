package pl.stasgroch.footballhub.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import pl.stasgroch.footballhub.data.model.FootballData
import pl.stasgroch.footballhub.data.model.JournalEntry
import pl.stasgroch.footballhub.data.model.EntryValidation
import pl.stasgroch.footballhub.data.repository.FootballRepository
import pl.stasgroch.footballhub.data.repository.UserRepository
import pl.stasgroch.footballhub.data.repository.RoomUserRepository
import pl.stasgroch.footballhub.data.local.FootballDatabase
import java.util.UUID

enum class FootballErrorKind { REFRESH, WRITE, VALIDATION, READ }
data class FootballError(val message: String, val kind: FootballErrorKind)
data class FootballUiState(val data: FootballData = FootballData(), val loading: Boolean = false, val error: FootballError? = null)

// Calls and state are confined to the UI thread (a serial dispatcher in JVM tests).
class FootballViewModel(
    private val repository: FootballRepository,
    private val users: UserRepository,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate
) : ViewModel(CoroutineScope(SupervisorJob() + dispatcher)) {
    private val _entries = MutableStateFlow<List<JournalEntry>>(emptyList())
    val entries: StateFlow<List<JournalEntry>> = _entries.asStateFlow()
    private val _favoritePlayers = MutableStateFlow<Set<Int>>(emptySet())
    val favoritePlayers: StateFlow<Set<Int>> = _favoritePlayers.asStateFlow()
    private val _favorites = MutableStateFlow<Set<Int>>(emptySet())
    val favorites: StateFlow<Set<Int>> = _favorites.asStateFlow()
    private val _userDataReady = MutableStateFlow(false)
    val userDataReady: StateFlow<Boolean> = _userDataReady.asStateFlow()
    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()
    private val _state = MutableStateFlow(FootballUiState())
    val state: StateFlow<FootballUiState> = _state.asStateFlow()

    private data class Operation(val kind: String, val id: Int? = null)
    private val journal = Operation("journal")
    private val refresh = Operation("refresh")
    private data class Failure(val request: Long, val error: FootballError)
    private var requestSequence = 0L
    private val errors = mutableMapOf<Operation, Failure>()
    private val favoriteJobs = mutableMapOf<Operation, Job>()

    // Priority follows request order, not completion order. Keep independent errors.
    private fun report(operation: Operation, request: Long, error: FootballError?) {
        if ((errors[operation]?.request ?: -1) > request) return
        if (error == null) errors.remove(operation) else errors[operation] = Failure(request, error)
        _state.value = _state.value.copy(error = errors.values.maxByOrNull { it.request }?.error)
    }
    init {
        refresh()
        val readRequest = ++requestSequence
        viewModelScope.launch {
            try {
                combine(users.entries, users.favorites, users.favoritePlayers) { e, f, p -> Triple(e, f, p) }.collect { (e, f, p) ->
                    _favoritePlayers.value = p
                    _entries.value = e
                    _favorites.value = f
                    _userDataReady.value = true
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { report(Operation("read"), readRequest, FootballError("Nie udało się odczytać lokalnej bazy. Uruchom aplikację ponownie.", FootballErrorKind.READ)) }
        }
    }

    fun saveEntry(id: String?, matchId: Int, kind: String, text: String, home: String, away: String, done: () -> Unit) {
        if (saving.value) return
        val error = EntryValidation.error(kind, text, home, away)
        if (error != null) { report(journal, ++requestSequence, FootballError(error, FootballErrorKind.VALIDATION)); return }
        mutateJournal(done) {
            users.save(JournalEntry(id ?: UUID.randomUUID().toString(), matchId, kind, text.trim(),
                if (kind == "PREDICTION") home.toInt() else null, if (kind == "PREDICTION") away.toInt() else null))
        }
    }

    fun deleteEntry(id: String) = mutateJournal { users.delete(id) }
    fun favoritePlayer(id: Int, enabled: Boolean) = enqueueFavorite(Operation("player", id)) { users.favoritePlayer(id, enabled) }
    fun favorite(id: Int, enabled: Boolean) = enqueueFavorite(Operation("team", id)) { users.favorite(id, enabled) }

    private fun mutateJournal(done: () -> Unit = {}, action: suspend () -> Unit) {
        if (saving.value) return
        val request = ++requestSequence
        _saving.value = true
        viewModelScope.launch {
            try { action(); report(journal, request, null); done() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { report(journal, request, FootballError("Nie udało się zapisać zmiany. Spróbuj ponownie.", FootballErrorKind.WRITE)) }
            finally { _saving.value = false }
        }
    }

    private fun enqueueFavorite(operation: Operation, action: suspend () -> Unit) {
        val request = ++requestSequence
        val previous = favoriteJobs[operation]
        val next = viewModelScope.launch(start = CoroutineStart.LAZY) {
            previous?.join()
            try { action(); report(operation, request, null) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { report(operation, request, FootballError("Nie udało się zapisać zmiany. Spróbuj ponownie.", FootballErrorKind.WRITE)) }
        }
        favoriteJobs[operation] = next
        next.invokeOnCompletion {
            if (favoriteJobs[operation] === next) favoriteJobs.remove(operation)
        }
        next.start()
    }

    fun refresh() {
        if (_state.value.loading) return
        val request = ++requestSequence
        _state.value = _state.value.copy(loading = true)
        viewModelScope.launch {
            try {
                val data = repository.load()
                _state.value = _state.value.copy(data = data, loading = false)
                report(refresh, request, null)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                _state.value = _state.value.copy(loading = false)
                report(refresh, request, FootballError("Nie udało się wczytać danych. Spróbuj ponownie.", FootballErrorKind.REFRESH))
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                require(modelClass == FootballViewModel::class.java) { "Unsupported ViewModel: ${modelClass.name}" }
                val app = checkNotNull(extras[APPLICATION_KEY])
                @Suppress("UNCHECKED_CAST")
                return FootballViewModel(FootballRepository.demo(), RoomUserRepository(FootballDatabase.get(app).userDao())) as T
            }
        }
    }
}

