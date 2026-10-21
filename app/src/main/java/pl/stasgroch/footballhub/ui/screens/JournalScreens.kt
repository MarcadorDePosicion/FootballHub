package pl.stasgroch.footballhub.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import pl.stasgroch.footballhub.ui.theme.HubSpace
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import pl.stasgroch.footballhub.data.model.*

@Composable fun EntryCard(entry: JournalEntry, edit: () -> Unit, delete: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().testTag("entry-${entry.id}"), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(start = 16.dp, top = 8.dp, end = 4.dp, bottom = 8.dp)) {
            Column(Modifier.weight(1f).padding(top = 8.dp, end = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(if (entry.kind == "NOTE") HubIcons.Notes else HubIcons.Matches, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.secondary)
                    Text(if (entry.kind == "NOTE") "NOTATKA" else "TYP WYNIKU", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelSmall)
                }
                if (entry.kind == "PREDICTION") Text("${entry.homeScore} : ${entry.awayScore}", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                if (entry.text.isNotBlank()) Text(entry.text)
            }
            Column {
                EntryAction("Usuń", HubIcons.Delete, MaterialTheme.colorScheme.error) { confirm = true }
                EntryAction("Edytuj", HubIcons.Notes, MaterialTheme.colorScheme.primary, edit)
            }
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Usunąć wpis?") },
        text = { Text("Tej operacji nie można cofnąć.") },
        confirmButton = { TextButton({ confirm = false; delete() }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Usuń wpis") } },
        dismissButton = { TextButton({ confirm = false }) { Text("Anuluj") } })
}
@Composable fun JournalScreen(data: FootballData, entries: List<JournalEntry>, kind: String, changeKind: (String) -> Unit, openMatch: (Int) -> Unit, edit: (JournalEntry) -> Unit, delete: (String) -> Unit, browse: () -> Unit) {
    val filtered = entries.filter { kind == "ALL" || it.kind == kind }
    LazyColumn(contentPadding = navigationListPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionTitle("Moje typy i notatki", "Twój osobisty dziennik meczowy") }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("ALL" to "Wszystkie", "NOTE" to "Notatki", "PREDICTION" to "Typy").forEach { (id, label) ->
                        FilterChip(kind == id, { changeKind(id) }, { Text(label, style = MaterialTheme.typography.labelSmall) })
                    }
                }
                FilledIconButton(browse, Modifier.size(48.dp), shape = MaterialTheme.shapes.small) {
                    Icon(HubIcons.Plus, "Wybierz mecz i dodaj wpis")
                }
            }
        }
        if (filtered.isEmpty()) item { EmptyState("Jeszcze nie ma wpisów w tej kategorii. Dodaj pierwszy w szczegółach meczu.") }
        filtered.groupBy { it.matchId }.forEach { (matchId, matchEntries) ->
            item(key = "match-$matchId") {
                val match = data.matches.find { it.id == matchId }
                TextButton({ openMatch(matchId) }, Modifier.testTag("journal-match-$matchId")) {
                    Text(match?.let { "${data.team(it.homeId).shortName} – ${data.team(it.awayId).shortName}" } ?: "Mecz #$matchId")
                }
            }
            items(matchEntries, key = { it.id }) { entry -> EntryCard(entry, { edit(entry) }, { delete(entry.id) }) }
        }
    }
}
@Composable fun EntryFormScreen(match: Match?, data: FootballData, kind: String, existing: JournalEntry?, missingEntry: Boolean, saving: Boolean, save: (String, String, String) -> Unit, cancel: () -> Unit) {
    if (match == null || missingEntry) { EmptyState("Nie znaleziono meczu lub wpisu."); return }
    var text by rememberSaveable(existing?.id, match.id, kind) { mutableStateOf(existing?.text ?: "") }
    var home by rememberSaveable(existing?.id, match.id, kind) { mutableStateOf(existing?.homeScore?.toString() ?: "") }
    var away by rememberSaveable(existing?.id, match.id, kind) { mutableStateOf(existing?.awayScore?.toString() ?: "") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val focus = LocalFocusManager.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionTitle(if (existing == null) "Nowy wpis" else "Edytuj wpis", if (kind == "NOTE") "Notatka do meczu" else "Przewidywany wynik")
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().padding(HubSpace.md), verticalArrangement = Arrangement.spacedBy(HubSpace.sm)) {
                listOf(data.team(match.homeId), data.team(match.awayId)).forEach { team ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(HubSpace.sm)) { TeamEmblem(team); Text(team.name, style = MaterialTheme.typography.titleSmall) }
                }
                Text("${displayDate(match.date)} • ${match.time}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (kind == "PREDICTION") Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(home, { home = it; error = null }, label = { Text(data.team(match.homeId).shortName) }, singleLine = true,
                shape = MaterialTheme.shapes.medium, isError = error != null && home.toIntOrNull() !in 0..99,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
            OutlinedTextField(away, { away = it; error = null }, label = { Text(data.team(match.awayId).shortName) }, singleLine = true,
                shape = MaterialTheme.shapes.medium, isError = error != null && away.toIntOrNull() !in 0..99,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
        }
        OutlinedTextField(text, { text = it; error = null }, label = { Text(if (kind == "NOTE") "Treść notatki" else "Komentarz (opcjonalnie)") },
            minLines = 4, maxLines = 8, shape = MaterialTheme.shapes.medium,
            isError = text.length > 2000 || (error != null && kind == "NOTE" && text.isBlank()),
            modifier = Modifier.fillMaxWidth(), supportingText = { Text("${text.length}/2000") })
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        PrimaryAction(if (saving) "Zapisywanie…" else "Zapisz", enabled = !saving, onClick = {
            error = EntryValidation.error(kind, text, home, away)
            if (error == null) { focus.clearFocus(); save(text.trim(), home, away) }
        })
        TextButton(cancel, enabled = !saving, modifier = Modifier.fillMaxWidth().heightIn(min = HubSpace.touch)) { Text("Anuluj") }
    }
}
@Composable fun FavoritesScreen(data: FootballData, favorites: Set<Int>, openTeam: (Int) -> Unit, remove: (Int) -> Unit, browse: () -> Unit) {
    LazyColumn(contentPadding = navigationListPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionTitle("Ulubione drużyny", "Twoje kluby zawsze pod ręką") }
        if (favorites.isEmpty()) item { Column(verticalArrangement = Arrangement.spacedBy(HubSpace.md)) { EmptyState("Nie masz jeszcze ulubionych. Otwórz drużynę z tabeli ligi lub szczegółów meczu.", HubIcons.Star); PrimaryAction("Przeglądaj ligi", onClick = browse) } }
        items(data.teams.filter { it.id in favorites }, key = { it.id }) { team ->
            Card(onClick = { openTeam(team.id) }, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(HubSpace.md)) {
                        TeamEmblem(team, 32.dp)
                        Column(Modifier.weight(1f)) {
                            Text(team.name, style = MaterialTheme.typography.titleMedium)
                            Text(team.city, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton({ remove(team.id) }) { Icon(HubIcons.StarFilled, "Usuń z ulubionych", tint = MaterialTheme.colorScheme.primary) }
                    }

                }
            }
        }
    }
}





