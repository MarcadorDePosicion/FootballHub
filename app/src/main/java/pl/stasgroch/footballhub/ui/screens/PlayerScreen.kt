package pl.stasgroch.footballhub.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import pl.stasgroch.footballhub.data.model.*
import pl.stasgroch.footballhub.ui.theme.hubHeaderGradient
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable fun PlayerScreen(id: Int, data: FootballData, openMatch: (Int) -> Unit, openTeam: (Int) -> Unit, back: () -> Unit = {}) {
    val player = data.players.find { it.id == id }
    if (player == null) { EmptyState("Nie znaleziono zawodnika."); return }
    val team = data.teams.find { it.id == player.teamId }
    val pager = rememberPagerState { 2 }
    val scope = rememberCoroutineScope()
    val lists = listOf(rememberLazyListState(), rememberLazyListState())
    Column(Modifier.fillMaxSize().testTag("player-profile-$id")) {
        Column(Modifier.fillMaxWidth().hubHeaderGradient().statusBarsPadding()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconButton(back) { Icon(HubIcons.Back, "Wstecz") }
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(player.name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1) },
                        Modifier.padding(16.dp), color = MaterialTheme.colorScheme.secondary)
                }
                Column(Modifier.weight(1f)) {
                    Text(player.name, style = MaterialTheme.typography.titleLarge)
                    if (team != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TeamEmblem(team, 24.dp)
                        Text(team.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (player.demo) Text("DEMO • fikcyjny profil zawodnika", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelSmall)
        }
        }
        SecondaryTabRow(pager.currentPage) {
            listOf("Szczegóły", "Mecze").forEachIndexed { index, label ->
                Tab(pager.currentPage == index, { scope.launch { pager.animateScrollToPage(index) } },
                    text = { Text(label) }, modifier = Modifier.testTag("player-tab-$index"))
            }
        }
        HorizontalPager(pager, Modifier.weight(1f)) { page ->
            LazyColumn(Modifier.pageSemantics(pager, page).fillMaxSize().testTag("player-page-$page"),
                state = lists[page], contentPadding = detailListPadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (page == 0) {
                    if (team != null) item {
                        Surface(onClick = { openTeam(team.id) }, modifier = Modifier.fillMaxWidth().testTag("player-club"), shape = RoundedCornerShape(50)) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                TeamEmblem(team, 36.dp)
                                Text(team.name, style = MaterialTheme.typography.titleSmall)
                            }
                        }
                    }
                    val birth = player.dateOfBirth?.let { runCatching { LocalDate.parse(it).format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) }.getOrNull() }
                    val facts = listOfNotNull(
                        player.nationality?.let { "Narodowość" to it }, birth?.let { it to (player.age()?.let { age -> "$age " + if (age == 1) "rok" else if (age % 10 in 2..4 && age % 100 !in 12..14) "lata" else "lat" } ?: "Wiek niedostępny") }, player.heightCm?.let { "Wzrost" to "$it cm" },
                        player.preferredFoot?.let { "Lepsza noga" to it.label },
                        player.position?.let { "Pozycja" to when (it) { PlayerPosition.GOALKEEPER -> "Bramkarz"; PlayerPosition.DEFENDER -> "Obrońca"; PlayerPosition.MIDFIELDER -> "Pomocnik"; PlayerPosition.FORWARD -> "Napastnik" } }, player.number?.let { "Numer" to it.toString() })
                    item {
                        BoxWithConstraints {
                            val columns = if (maxWidth >= 380.dp) 3 else 2
                            IconButton(back) { Icon(HubIcons.Back, "Wstecz") }
                Surface(shape = MaterialTheme.shapes.large) {
                                Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    facts.chunked(columns).forEach { row ->
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            row.forEach { (label, value) ->
                                                Column(Modifier.weight(1f).padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                                                    Text(value, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
                                                }
                                            }
                                            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (birth == null) item { Text("Data urodzenia i wiek nie są dostępne.", style = MaterialTheme.typography.bodySmall) }
                    if (team == null) item { Text("Aktualny klub nie jest znany.", style = MaterialTheme.typography.bodySmall) }
                } else {
                    val appearances = data.appearances.filter { it.playerId == id }.mapNotNull { appearance ->
                        data.matches.find { it.id == appearance.matchId }?.let { appearance to it }
                    }.sortedByDescending { it.second.date + it.second.time }
                    if (appearances.isEmpty()) item { EmptyState("Brak dostępnej historii występów.") }
                    items(appearances, key = { it.second.id }) { (appearance, match) ->
                        val home = appearance.teamId == match.homeId
                        val opponent = data.teams.find { it.id == if (home) match.awayId else match.homeId }
                        Card(onClick = { openMatch(match.id) }, modifier = Modifier.fillMaxWidth().testTag("appearance-${match.id}")) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("${displayDate(match.date)} • ${data.leagues.find { it.id == match.leagueId }?.name.orEmpty()}", style = MaterialTheme.typography.labelSmall)
                                Text("${opponent?.name ?: "Nieznany przeciwnik"} • ${if (home) "Dom" else "Wyjazd"}", style = MaterialTheme.typography.titleSmall)
                                Text("${match.homeScore ?: "—"} : ${match.awayScore ?: "—"} • ${when (appearance.started) { true -> "Wyjściowy skład"; false -> "Wejście z ławki"; null -> "Udział potwierdzony" }}",
                                    color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                                data.eventsFor(match.id).filter { it.involves(id) }.forEach { Text(it.playerSummary(id), style = MaterialTheme.typography.labelSmall) }
                                val coverage = data.matchEvents.find { it.matchId == match.id }
                                if (coverage?.demo == true) Text("DEMO • przykładowy występ i zdarzenia", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                                if (coverage == null || coverage.coverage != EventCoverage.COMPLETE) Text("Informacje o zdarzeniach mogą być niepełne.", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
