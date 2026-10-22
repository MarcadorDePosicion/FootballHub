package pl.stasgroch.footballhub.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import pl.stasgroch.footballhub.data.model.Team
import pl.stasgroch.footballhub.data.model.Player

class HeaderSearchState(query: String = "", filter: Int = 0, resume: Boolean = false) {
    val query = mutableStateOf(query)
    val filter = mutableIntStateOf(filter)
    var resume by mutableStateOf(resume)
    companion object {
        val Saver = listSaver<HeaderSearchState, Any>(
            save = { listOf(it.query.value, it.filter.intValue, it.resume) },
            restore = { HeaderSearchState(it[0] as String, it[1] as Int, it[2] as Boolean) })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun HeaderSearch(teams: List<Team>, players: List<Player> = emptyList(), search: HeaderSearchState = remember { HeaderSearchState() }, openPlayer: (Player) -> Unit = {}, resultScroll: ScrollState = rememberScrollState(), openTeam: (Int) -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    var query by search.query
    val requester = remember { FocusRequester() }
    var expanded by remember { mutableStateOf(false) }
    var filter by search.filter
    LaunchedEffect(Unit) { if (search.resume) { requester.requestFocus(); expanded = true } }
    fun dismiss() { search.resume = false; expanded = false; query = ""; filter = 0; focus.clearFocus(); keyboard?.hide() }
    val matches = teams.filter { team ->
        val words = team.name.split(" ", "-")
        query.isBlank() || words.any { it.startsWith(query.trim(), ignoreCase = true) } || team.name.startsWith(query.trim(), true)
    }
    ExposedDropdownMenuBox(expanded, onExpandedChange = { if (!it) dismiss() else expanded = true }, modifier = Modifier.padding(end = 8.dp)) {
        TextField(query, { query = it; expanded = true }, singleLine = true,
            modifier = Modifier.fillMaxWidth().focusRequester(requester).menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                .onFocusChanged {
                    expanded = it.isFocused
                    if (it.isFocused) search.resume = false
                    if (!it.isFocused && !search.resume) { query = ""; filter = 0; keyboard?.hide() }
                }.testTag("team-search"),
            placeholder = { Text("Szukaj w Football Hub", style = MaterialTheme.typography.bodySmall) },
            leadingIcon = { Icon(HubIcons.Search, null) },
            textStyle = MaterialTheme.typography.bodySmall,
            shape = MaterialTheme.shapes.extraLarge,
            colors = TextFieldDefaults.colors(focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surface, unfocusedContainerColor = MaterialTheme.colorScheme.surface),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { dismiss() }))
        ExposedDropdownMenu(expanded, onDismissRequest = { dismiss() }, scrollState = resultScroll, modifier = Modifier.heightIn(max = 300.dp).testTag("team-search-results")) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("Wszystkie", "Drużyna", "Zawodnik").forEachIndexed { index, label ->
                    FilterChip(filter == index, { filter = index }, { Text(label, style = MaterialTheme.typography.labelSmall) }, modifier = Modifier.testTag("search-filter-$index"))
                }
            }
            val foundPlayers = players.filter { player -> query.isBlank() || player.name.split(" ", "-").any { it.startsWith(query.trim(), true) } }
            if ((filter == 2 || matches.isEmpty()) && (filter == 1 || foundPlayers.isEmpty()))
                DropdownMenuItem(text = { Text("Brak wyników") }, onClick = {}, enabled = false)
            if (filter != 2) matches.forEach { team ->
                DropdownMenuItem(text = { Text(team.name, style = MaterialTheme.typography.bodyMedium) },
                    leadingIcon = { TeamEmblem(team, 28.dp) },
                    modifier = Modifier.testTag("search-team-${team.id}"),
                    onClick = { dismiss(); openTeam(team.id) })
            }
            if (filter != 1) foundPlayers.forEach { player ->
                DropdownMenuItem(text = { Column {
                    Text(player.name, style = MaterialTheme.typography.bodyMedium)
                    Text(listOfNotNull(teams.find { it.id == player.teamId }?.name, if (player.demo) "DEMO" else null).joinToString(" • "), style = MaterialTheme.typography.labelSmall)
                } }, modifier = Modifier.testTag("search-player-${player.id}"), onClick = { search.resume = true; expanded = false; keyboard?.hide(); openPlayer(player) })
            }
        }
    }
}
