package pl.stasgroch.footballhub.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import pl.stasgroch.footballhub.data.model.FootballData

private val statuses = listOf("ALL" to "Wszystkie", "SCHEDULED" to "Nadchodzące", "FINISHED" to "Zakończone")

@OptIn(ExperimentalLayoutApi::class)
@Composable fun MatchFilterPanel(data: FootballData, filters: MatchFilters, pager: androidx.compose.foundation.pager.PagerState, anchor: Long) {
    var show by rememberSaveable { mutableStateOf(false) }
    var league by rememberSaveable { mutableIntStateOf(0) }
    var status by rememberSaveable { mutableStateOf("ALL") }
    var chronological by rememberSaveable { mutableStateOf(false) }
    val count = (if (filters.league != 0) 1 else 0) + (if (filters.status != "ALL") 1 else 0)
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { league = filters.league; status = filters.status; chronological = filters.chronological; show = true }) {
            Text(if (count == 0) "Filtry" else "Filtry ($count)")
        }
        MatchDateSelector(filters, pager, anchor, Modifier.weight(1f))
    }
    if (count > 0) TextButton(onClick = { filters.league = 0; filters.status = "ALL" }) { Text("Wyczyść") }
    if (count > 0) Text(
        listOfNotNull(data.leagues.find { it.id == filters.league }?.name,
            statuses.find { it.first == filters.status && it.first != "ALL" }?.second).joinToString(" • "),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (show) AlertDialog(onDismissRequest = { show = false }, title = { Text("Filtry meczów") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Kolejność", style = MaterialTheme.typography.titleSmall)
                FilterChip(!chronological, { chronological = false }, { Text("Według lig") })
                FilterChip(chronological, { chronological = true }, { Text("Chronologicznie") },
                    leadingIcon = { Icon(HubIcons.Clock, null, Modifier.size(18.dp)) })
                Spacer(Modifier.height(12.dp))
                Text("Stan meczu", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    statuses.forEach { (value, label) -> FilterChip(status == value, { status = value }, { Text(label) }) }
                }
                Spacer(Modifier.height(12.dp))
                Text("Liga", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(league == 0, { league = 0 }, { Text("Wszystkie ligi") })
                    data.leagues.forEach { item -> FilterChip(league == item.id, { league = item.id }, { Text(item.name) }) }
                }
            }
        }, confirmButton = { TextButton(onClick = { filters.league = league; filters.status = status; filters.chronological = chronological; show = false }) { Text("Zastosuj") } },
        dismissButton = { TextButton(onClick = { show = false }) { Text("Anuluj") } })
}

