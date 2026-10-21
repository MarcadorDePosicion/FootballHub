package pl.stasgroch.footballhub.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import pl.stasgroch.footballhub.data.model.Team

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun HeaderSearch(teams: List<Team>, openTeam: (Int) -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    var query by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    fun dismiss() { expanded = false; query = ""; focus.clearFocus(); keyboard?.hide() }
    val matches = teams.filter { team ->
        val words = team.name.split(" ", "-")
        query.isBlank() || words.any { it.startsWith(query.trim(), ignoreCase = true) } || team.name.startsWith(query.trim(), true)
    }
    ExposedDropdownMenuBox(expanded, onExpandedChange = { if (!it) dismiss() else expanded = true }, modifier = Modifier.padding(end = 8.dp)) {
        TextField(query, { query = it; expanded = true }, singleLine = true,
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                .onFocusChanged {
                    expanded = it.isFocused
                    if (!it.isFocused) { query = ""; keyboard?.hide() }
                }.testTag("team-search"),
            placeholder = { Text("Szukaj drużyny", style = MaterialTheme.typography.bodySmall) },
            leadingIcon = { Icon(HubIcons.Search, null) },
            textStyle = MaterialTheme.typography.bodySmall,
            shape = MaterialTheme.shapes.extraLarge,
            colors = TextFieldDefaults.colors(focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surface, unfocusedContainerColor = MaterialTheme.colorScheme.surface),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { dismiss() }))
        ExposedDropdownMenu(expanded, onDismissRequest = { dismiss() }, modifier = Modifier.heightIn(max = 300.dp).testTag("team-search-results")) {
            if (matches.isEmpty()) DropdownMenuItem(text = { Text("Brak drużyn") }, onClick = {}, enabled = false)
            matches.forEach { team ->
                DropdownMenuItem(text = { Text(team.name, style = MaterialTheme.typography.bodyMedium) },
                    leadingIcon = { TeamEmblem(team, 28.dp) },
                    modifier = Modifier.testTag("search-team-${team.id}"),
                    onClick = { dismiss(); openTeam(team.id) })
            }
        }
    }
}
