package pl.stasgroch.footballhub.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.Color
import pl.stasgroch.footballhub.ui.theme.hubGlass
import pl.stasgroch.footballhub.ui.theme.hubHeaderGradient
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import pl.stasgroch.footballhub.data.model.*
import pl.stasgroch.footballhub.ui.theme.HubSpace

@Composable fun LeaguesScreen(data: FootballData, openLeague: (Int) -> Unit) {
    LazyColumn(contentPadding = navigationListPadding(), verticalArrangement = Arrangement.spacedBy(HubSpace.sm)) {
        item { SectionTitle("Rozgrywki", "Tabele i drużyny • ${data.leagues.size} ligi") }
        items(data.leagues) { league ->
            Card(onClick = { openLeague(league.id) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(HubSpace.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(HubSpace.md)) {
                    Icon(HubIcons.Leagues, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.secondary)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(HubSpace.xs)) {
                        Text(league.name, style = MaterialTheme.typography.titleMedium)
                        Text(league.country, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(HubIcons.Next, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun StandingsScreen(id: Int, data: FootballData, favorites: Set<Int> = emptySet(),
    openMatch: (Int) -> Unit = {}, back: (() -> Unit)? = null, openTeam: (Int) -> Unit) {
    val league = data.leagues.find { it.id == id }
    if (league == null) { EmptyState("Nie znaleziono ligi."); return }
    var tab by rememberSaveable(id) { mutableIntStateOf(0) }
    val tableState = rememberLazyListState()
    val matchesState = rememberLazyListState()
    val behavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(modifier = Modifier.nestedScroll(behavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0), topBar = {
        Column {
            MediumTopAppBar(
                modifier = Modifier.hubGlass(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)).testTag("league-header"),
                title = { Column(Modifier.padding(bottom = 8.dp)) {
                    Text(league.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${league.country}", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } },
                navigationIcon = { if (back != null) IconButton(back) { Icon(HubIcons.Back, "Wstecz") } },
                scrollBehavior = behavior, windowInsets = WindowInsets(0, 0, 0, 0),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent))
            SecondaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                listOf("Tabela", "Mecze").forEachIndexed { index, label ->
                    Tab(selected = tab == index, onClick = { tab = index }, text = { Text(label) },
                        modifier = Modifier.testTag("league-tab-$index"))
                }
            }
        }
    }) { padding ->
        LazyColumn(state = if (tab == 0) tableState else matchesState,
            modifier = Modifier.fillMaxSize().padding(padding).testTag("league-list"),
            contentPadding = navigationListPadding(), verticalArrangement = Arrangement.spacedBy(if (tab == 0) 8.dp else 0.dp)) {
            if (tab == 0) {
                item { StandingsTable(data.standings.filter { it.leagueId == id }.sortedByDescending { it.points }, data, favorites, openTeam) }
                item { Text("M — mecze   B — bramki   Pkt — punkty", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                item { Text("Fragment tabeli demonstracyjnej • dotknij drużyny, aby otworzyć profil.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                val matches = data.matches.filter { it.leagueId == id }.sortedWith(compareBy<Match> { it.date }.thenBy { it.time })
                if (matches.isEmpty()) item { EmptyState("Brak spotkań w tych rozgrywkach.", HubIcons.Matches) }
                groupedMatches("league-$id", matches, data, openMatch)
            }
        }
    }
}
@Composable fun TeamScreen(id: Int, data: FootballData, openMatch: (Int) -> Unit,
    openTeam: (Int) -> Unit = {}, back: () -> Unit = {}, favorite: @Composable (Team) -> Unit = {}) {
    val team = data.teams.find { it.id == id }
    if (team == null) { EmptyState("Nie znaleziono drużyny."); return }
    val league = data.leagues.find { it.id == team.leagueId }
    var tab by rememberSaveable(id) { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().hubHeaderGradient()) {
            IconButton(back) { Icon(HubIcons.Back, "Wstecz") }
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TeamEmblem(team, 48.dp)
                Column(Modifier.weight(1f)) {
                    Text(team.name, style = MaterialTheme.typography.titleLarge)
                    league?.let { Text(it.country, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                favorite(team)
            }
            SecondaryTabRow(tab, containerColor = Color.Transparent) {
                listOf("Mecze", "Tabela", "Informacje").forEachIndexed { index, label ->
                    Tab(tab == index, { tab = index }, text = { Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1) },
                        modifier = Modifier.testTag("team-tab-$index"))
                }
            }
        }
        key(id, tab) {
            LazyColumn(Modifier.weight(1f), contentPadding = navigationListPadding(), verticalArrangement = Arrangement.spacedBy(HubSpace.md)) {
                when (tab) {
                    0 -> {
                        item { SectionTitle("Mecze drużyny") }
                        val matches = data.matches.filter { it.homeId == id || it.awayId == id }
                        if (matches.isEmpty()) item { EmptyState("Brak meczów tej drużyny.") }
                        items(matches) { MatchCard(it, data) { openMatch(it.id) } }
                    }
                    1 -> {
                        item { SectionTitle(league?.name ?: "Tabela ligi", "Wyróżniona drużyna: ${team.name}") }
                        val rows = data.standings.filter { it.leagueId == team.leagueId }.sortedByDescending { it.points }
                        if (rows.isEmpty()) item { EmptyState("Tabela tej ligi nie jest jeszcze dostępna.") }
                        else item { StandingsTable(rows, data, emptySet(), openTeam, highlightedTeamId = id) }
                    }
                    2 -> {
                        val facts = listOf("Miasto" to team.city, "Rok założenia" to team.founded.toString(),
                            "Stadion" to team.stadium, "Rozgrywki" to (league?.name ?: "Brak informacji"))
                        items(facts) { (label, value) ->
                            Surface(shape = MaterialTheme.shapes.medium) {
                                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                                    Text(value, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
