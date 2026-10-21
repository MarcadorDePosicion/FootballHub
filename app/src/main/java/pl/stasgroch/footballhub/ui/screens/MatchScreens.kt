package pl.stasgroch.footballhub.ui.screens

import pl.stasgroch.footballhub.ui.theme.hubGlass
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pl.stasgroch.footballhub.data.model.*
import pl.stasgroch.footballhub.ui.theme.HubSpace
import java.time.LocalDate

@Composable fun HomeScreen(data: FootballData, listState: LazyListState, openMatch: (Int) -> Unit) {
    val today = data.matches.filter { it.date == LocalDate.now().toString() }.sortedBy { it.time }
    val recent = data.matches.filter { it.status == "FINISHED" }.sortedWith(compareByDescending<Match> { it.date }.thenByDescending { it.time })
    LazyColumn(state = listState, modifier = Modifier.testTag("match-list"), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 112.dp)) {
        item { Text("FOOTBALL HUB • lokalny mock, bez danych na żywo", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { SectionTitle("Mecze dnia", "${displayDate(LocalDate.now().toString())} • Spotkania: ${today.size}") }
        if (today.isEmpty()) item { EmptyState("Brak spotkań na dziś. Sprawdź pełny terminarz w zakładce Mecze.", HubIcons.Matches) }
        groupedMatches("today", today, data, openMatch)
        item { SectionTitle("Ostatnie wyniki", "Zakończone spotkania") }
        if (recent.isEmpty()) item { EmptyState("Nie ma jeszcze zakończonych spotkań.", HubIcons.Matches) }
        groupedMatches("recent", recent, data, openMatch)
    }
}
@Composable fun MatchesScreen(data: FootballData, filters: MatchFilters, listState: LazyListState, openMatch: (Int) -> Unit) {
    val league = filters.league
    val status = filters.status
    val filtered = data.matches.filter {
        it.date == filters.date && (league == 0 || it.leagueId == league) && (status == "ALL" || it.status == status || (status == "LIVE" && it.status in listOf("IN_PLAY", "IN_PROGRESS")))
    }.sortedWith(compareBy<Match> { it.date }.thenBy { it.time })
    LazyColumn(state = listState, modifier = Modifier.testTag("match-list"), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 112.dp)) {
        item { SectionTitle("Centrum meczowe", "Terminarz i wyniki • ${filtered.size} spotkań") }
        item { MatchFilterPanel(data, filters) }
        if (filtered.isEmpty()) item { EmptyState("Brak meczów dla wybranych filtrów.", HubIcons.Search) }
        if (filters.chronological) filtered.forEach { match ->
            groupedMatches("time-${match.id}", listOf(match), data, openMatch)
        } else groupedMatches("filtered", filtered, data, openMatch)
    }
}
@Composable fun MatchDetailsScreen(id: Int, data: FootballData, openTeam: (Int) -> Unit, openLeague: (Int) -> Unit, content: @Composable ColumnScope.(Match) -> Unit = {}) {
    val match = data.matches.find { it.id == id }
    if (match == null) { EmptyState("Nie znaleziono meczu."); return }
    val home = data.team(match.homeId)
    val away = data.team(match.awayId)
    LazyColumn(contentPadding = navigationListPadding(), verticalArrangement = Arrangement.spacedBy(HubSpace.md)) {
        item { SectionTitle("Szczegóły meczu", "${displayDate(match.date)} • Kolejka ${match.round}") }
        item {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxWidth().padding(HubSpace.lg), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(HubSpace.lg)) {
                    Text(data.leagues.first { it.id == match.leagueId }.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(HubSpace.sm)) {
                        Column(Modifier.weight(1f).clickable(role = Role.Button, onClickLabel = "Otwórz profil drużyny", onClick = { openTeam(home.id) }).testTag("match-home-team"), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(HubSpace.sm)) { TeamEmblem(home, 48.dp)
                            Text(home.name, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleSmall)
                        }
                        Column(Modifier.widthIn(min = 80.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(HubSpace.sm)) {
                            Text(if (match.homeScore != null && match.awayScore != null) "${match.homeScore} : ${match.awayScore}" else match.time,
                                style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                            MatchStatus(match.status)
                        }
                        Column(Modifier.weight(1f).clickable(role = Role.Button, onClickLabel = "Otwórz profil drużyny", onClick = { openTeam(away.id) }).testTag("match-away-team"), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(HubSpace.sm)) { TeamEmblem(away, 48.dp)
                            Text(away.name, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleSmall)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text("Stadion: ${home.stadium}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
        }
        item { Column(verticalArrangement = Arrangement.spacedBy(HubSpace.sm)) { content(match) } }
        item {
            TextButton({ openLeague(match.leagueId) }, Modifier.fillMaxWidth()) {
                Text("Tabela ligi", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.weight(1f))
                Text("Zobacz ligę", style = MaterialTheme.typography.labelSmall)
                Icon(HubIcons.Next, null)
            }
        }
        item {
            val rows = data.standings.filter { it.leagueId == match.leagueId }.sortedByDescending { it.points }
            if (rows.isEmpty()) EmptyState("Tabela tej ligi nie jest jeszcze dostępna.")
            else StandingsTable(rows, data, emptySet(), openTeam, highlightedTeamIds = setOf(home.id, away.id))
        }
    }
}












