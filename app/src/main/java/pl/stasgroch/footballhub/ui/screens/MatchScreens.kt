package pl.stasgroch.footballhub.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.drop
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
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
import pl.stasgroch.footballhub.ui.theme.hubHeaderGradient
import java.time.LocalDate

@Composable fun HomeScreen(data: FootballData, listState: LazyListState, openMatch: (Int) -> Unit) {
    val today = data.matches.filter { it.date == LocalDate.now().toString() }.sortedBy { it.time }
    val recent = data.matches.filter { it.matchStatus.isFinished }.sortedWith(compareByDescending<Match> { it.date }.thenByDescending { it.time })
    LazyColumn(state = listState, modifier = Modifier.testTag("match-list"), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp)) {
        item { Text("FOOTBALL HUB • lokalny mock, bez danych na żywo", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { SectionTitle("Mecze dnia", "${displayDate(LocalDate.now().toString())} • Spotkania: ${today.size}") }
        if (today.isEmpty()) item { EmptyState("Brak spotkań na dziś. Sprawdź pełny terminarz w zakładce Mecze.", HubIcons.Matches) }
        groupedMatches("today", today, data, openMatch)
        item { SectionTitle("Ostatnie wyniki", "Zakończone spotkania") }
        if (recent.isEmpty()) item { EmptyState("Nie ma jeszcze zakończonych spotkań.", HubIcons.Matches) }
        groupedMatches("recent", recent, data, openMatch)
    }
}
@Composable fun MatchesScreen(data: FootballData, filters: MatchFilters, onActiveList: (LazyListState) -> Unit, openMatch: (Int) -> Unit) {
    val anchor = rememberSaveable { LocalDate.parse(filters.date).toEpochDay() }
    val selected = (LocalDate.parse(filters.date).toEpochDay() - anchor).toInt() + 100000
    val pager = rememberPagerState(initialPage = selected) { 200001 }
    LaunchedEffect(selected) {
        if (selected != pager.currentPage) pager.animateScrollToPage(selected)
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.drop(1).collect { page ->
            filters.date = LocalDate.ofEpochDay(anchor + page - 100000).toString()
        }
    }
    val count = data.matches.count {
        it.date == filters.date && (filters.league == 0 || it.leagueId == filters.league) &&
            it.matchesStatusFilter(filters.status)
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            SectionTitle("Centrum meczowe", "Terminarz i wyniki • $count spotkań")
            MatchFilterPanel(data, filters, pager, anchor)
        }
        HorizontalPager(pager, Modifier.weight(1f).testTag("days-pager")) { page ->
            val date = LocalDate.ofEpochDay(anchor + page - 100000).toString()
            val pageList = rememberLazyListState()
            LaunchedEffect(page, pager.currentPage) {
                if (page == pager.currentPage) onActiveList(pageList)
            }
            val filtered = data.matches.filter {
                it.date == date && (filters.league == 0 || it.leagueId == filters.league) &&
                    it.matchesStatusFilter(filters.status)
            }.sortedBy { it.time }
            LazyColumn(state = pageList, modifier = Modifier.pageSemantics(pager, page).fillMaxSize()
                .testTag(if (page == pager.currentPage) "match-list" else "match-list-$page"),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp)) {
                if (filtered.isEmpty()) item { EmptyState("Brak meczów dla wybranych filtrów.", HubIcons.Search) }
                if (filters.chronological) filtered.forEach { match ->
                    groupedMatches("time-${match.id}", listOf(match), data, openMatch)
                } else groupedMatches("filtered", filtered, data, openMatch)
            }
        }
    }
}
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable fun MatchDetailsScreen(id: Int, data: FootballData, openTeam: (Int) -> Unit, openLeague: (Int) -> Unit, openPlayer: (Int) -> Unit = {}, actions: @Composable (Match) -> Unit = {}, back: () -> Unit = {}, content: @Composable ColumnScope.(Match) -> Unit = {}) {
    val match = data.matches.find { it.id == id }
    if (match == null) { EmptyState("Nie znaleziono meczu."); return }
    val home = data.team(match.homeId)
    val away = data.team(match.awayId)
    val league = data.league(match.leagueId)
    if (home == null || away == null || league == null) {
        EmptyState("Niepełne dane meczu — drużyna lub liga niedostępna.")
        return
    }
    val pager = rememberPagerState { 3 }
    val scope = rememberCoroutineScope()
    val tab = pager.currentPage
    val list = rememberLazyListState()
    val compact by remember { derivedStateOf { list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > 120 } }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().hubHeaderGradient().statusBarsPadding().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(back) { Icon(HubIcons.Back, "Wstecz") }
            if (compact) Row(Modifier.weight(1f).testTag("match-compact-header"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
                TeamEmblem(home, 32.dp)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (match.homeScore != null) "${match.homeScore} : ${match.awayScore}" else match.time, style = MaterialTheme.typography.titleSmall)
                    MatchStatus(match.status)
                }
                TeamEmblem(away, 32.dp)
            } else Text("Szczegóły meczu", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(48.dp))
        }
    LazyColumn(state = list, modifier = Modifier.weight(1f).testTag("match-detail-list"), contentPadding = detailListPadding(), verticalArrangement = Arrangement.spacedBy(HubSpace.sm)) {
        item {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxWidth().padding(HubSpace.lg), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(HubSpace.sm)) {
                    Box(Modifier.fillMaxWidth().heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
                        Surface(modifier = Modifier.padding(horizontal = if (!match.matchStatus.canPredict) 48.dp else 88.dp), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant) {
                            Text("${displayDate(match.date)} • ${match.time}", Modifier.padding(horizontal = 8.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                        }
                        Row(Modifier.align(Alignment.CenterEnd)) { actions(match) }
                    }
                    Text(league.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
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
                    MatchScorers(match, data, openPlayer)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text("Stadion: ${match.venueLabel}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
        }
        stickyHeader {
            SecondaryTabRow(tab) {
                listOf("Szczegóły", "Składy", "Tabela").forEachIndexed { index, label ->
                    Tab(tab == index, { scope.launch { pager.animateScrollToPage(index) } }, text = { Text(label) }, modifier = Modifier.testTag("match-tab-$index"))
                }
            }
        }
        item {
            MeasuredContentPager(pager, Modifier.testTag("match-pages")) { page ->
                if (page == 0) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        MatchTimeline(match, data, openPlayer)
                        content(match)
                    }
                } else if (page == 1) {
                    MatchLineups(match, data, openPlayer)
                } else {
                    Column {
                        TextButton({ openLeague(match.leagueId) }, Modifier.fillMaxWidth()) {
                            Text("Tabela ligi", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.weight(1f))
                            Text("Zobacz ligę", style = MaterialTheme.typography.labelSmall)
                            Icon(HubIcons.Next, null)
                        }
                        val rows = data.standings.filter { it.leagueId == match.leagueId }.orderedStandings()
                        if (rows.isEmpty()) EmptyState("Tabela tej ligi nie jest jeszcze dostępna.")
                        else StandingsTable(rows, data, emptySet(), openTeam, highlightedTeamIds = setOf(home.id, away.id))
                    }
                }
            }
        }
    }
}
}
