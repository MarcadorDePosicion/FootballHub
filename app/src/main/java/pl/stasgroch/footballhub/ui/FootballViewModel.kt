package pl.stasgroch.footballhub.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pl.stasgroch.footballhub.data.model.FootballData
import pl.stasgroch.footballhub.data.repository.FootballRepository
import pl.stasgroch.footballhub.data.repository.RoomUserRepository
import pl.stasgroch.footballhub.data.local.FootballDatabase
import kotlinx.coroutines.flow.combine
import pl.stasgroch.footballhub.data.model.JournalEntry
import pl.stasgroch.footballhub.data.model.EntryValidation
import java.util.UUID

data class FootballUiState(val data: FootballData = FootballData(), val loading: Boolean = false, val error: String? = null)
class FootballViewModel(application: Application) : AndroidViewModel(application) {
    private val users = RoomUserRepository(FootballDatabase.get(application).userDao())
    val entries = MutableStateFlow<List<JournalEntry>>(emptyList())
    val favoritePlayers = MutableStateFlow<Set<Int>>(emptySet())
    val favorites = MutableStateFlow<Set<Int>>(emptySet())
    val userDataReady = MutableStateFlow(false)
    val saving = MutableStateFlow(false)
    private val repository = FootballRepository.demo()
    private val _state = MutableStateFlow(FootballUiState())
    val state = _state.asStateFlow()
    init {
        refresh()
        viewModelScope.launch {
            try {
                combine(users.entries, users.favorites, users.favoritePlayers) { e, f, p -> Triple(e, f, p) }.collect { (e, f, p) ->
                    favoritePlayers.value = p
                    entries.value = e
                    favorites.value = f
                    userDataReady.value = true
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _state.value = _state.value.copy(error = "Nie udało się odczytać lokalnej bazy. Uruchom aplikację ponownie.") }
        }
    }
    fun saveEntry(id: String?, matchId: Int, kind: String, text: String, home: String, away: String, done: () -> Unit) {
        if (saving.value) return
        val error = EntryValidation.error(kind, text, home, away)
        if (error != null) { _state.value = _state.value.copy(error = error); return }
        mutate(done) { users.save(JournalEntry(id ?: UUID.randomUUID().toString(), matchId, kind, text.trim(), if (kind == "PREDICTION") home.toInt() else null, if (kind == "PREDICTION") away.toInt() else null)) }
    }
    fun deleteEntry(id: String) = mutate { users.delete(id) }
    fun favoritePlayer(id: Int, enabled: Boolean) = mutate { users.favoritePlayer(id, enabled) }
    fun favorite(id: Int, enabled: Boolean) = mutate { users.favorite(id, enabled) }
    private fun mutate(done: () -> Unit = {}, action: suspend () -> Unit) {
        if (saving.value) return
        saving.value = true
        viewModelScope.launch {
            try { action(); _state.value = _state.value.copy(error = null); done() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { _state.value = _state.value.copy(error = "Nie udało się zapisać zmiany. Spróbuj ponownie.") }
            finally { saving.value = false }
        }
    }
    fun refresh() {
        if (_state.value.loading) return
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try { _state.value = FootballUiState(repository.load()) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { _state.value = _state.value.copy(loading = false, error = "Nie udało się wczytać danych. Spróbuj ponownie.") }
        }
    }
}

