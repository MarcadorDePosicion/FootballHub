package pl.stasgroch.footballhub.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import pl.stasgroch.footballhub.data.model.*

@Composable fun PlayerLink(id: Int?, data: FootballData, openPlayer: (Int) -> Unit) {
    val player = data.players.find { it.id == id }
    if (player == null) Text("Zawodnik nieznany", style = MaterialTheme.typography.bodySmall)
    else TextButton({ openPlayer(player.id) }, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
        modifier = Modifier.testTag("event-player-${player.id}")) {
        Text(player.name, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable fun MatchScorers(match: Match, data: FootballData, openPlayer: (Int) -> Unit) {
    val goals = data.eventsFor(match.id).filter { it.kind == EventKind.GOAL }
    if (goals.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (data.matchEvents.find { it.matchId == match.id }?.demo == true)
            Text("DEMO • przykładowi strzelcy i asysty", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(match.homeId, match.awayId).forEach { teamId ->
                Column(Modifier.weight(1f)) {
                    Text(data.team(teamId).shortName, style = MaterialTheme.typography.labelSmall)
                    goals.filter { it.teamId == teamId }.forEach { goal ->
                        Text("${goal.time.label} • ${goal.goalKind?.label ?: "Gol"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                        PlayerLink(goal.playerId, data, openPlayer)
                        if (goal.assistPlayerId != null) {
                            Text("Asysta", style = MaterialTheme.typography.labelSmall)
                            PlayerLink(goal.assistPlayerId, data, openPlayer)
                        }
                    }
                }
            }
        }
    }
}

@Composable fun MatchTimeline(match: Match, data: FootballData, openPlayer: (Int) -> Unit) {
    val record = data.matchEvents.find { it.matchId == match.id }
    var expanded by rememberSaveable(match.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton({ expanded = !expanded }, Modifier.fillMaxWidth().testTag("match-timeline-toggle")) {
            Text("Przebieg meczu", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            Text(if (expanded) "Zwiń" else "Rozwiń", style = MaterialTheme.typography.labelSmall)
        }
        if (expanded) {
            if (record == null || record.coverage == EventCoverage.UNAVAILABLE) {
                Text("Zdarzenia meczu nie zostały jeszcze udostępnione.", style = MaterialTheme.typography.bodySmall)
            } else {
                if (record.demo) Text("DEMO • fikcyjny przebieg, nie zapis rzeczywistego meczu", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                if (record.coverage == EventCoverage.PARTIAL) Text("Dostępny jest tylko fragment przebiegu meczu.", style = MaterialTheme.typography.labelSmall)
                if (record.events.isEmpty()) Text("Brak zarejestrowanych zdarzeń.", style = MaterialTheme.typography.bodySmall)
                data.eventsFor(match.id).forEach { event ->
                    Surface(shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().testTag("event-${event.id}")) {
                        Column(Modifier.padding(10.dp)) {
                            Text("${event.time.label} • ${data.teams.find { it.id == event.teamId }?.name.orEmpty()} • ${if (event.teamId == match.homeId) "Dom" else "Goście"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                            Text(when (event.kind) {
                                EventKind.GOAL -> "${event.goalKind?.label ?: "Gol"} • ${event.homeScore ?: "—"}:${event.awayScore ?: "—"}"
                                EventKind.CARD -> event.cardKind?.label ?: "Kartka"
                                EventKind.SUBSTITUTION -> "Zmiana • schodzi"
                            }, style = MaterialTheme.typography.bodySmall)
                            PlayerLink(event.playerId, data, openPlayer)
                            event.assistPlayerId?.let { Text("Asysta", style = MaterialTheme.typography.labelSmall); PlayerLink(it, data, openPlayer) }
                            event.incomingPlayerId?.let { Text("Wchodzi", style = MaterialTheme.typography.labelSmall); PlayerLink(it, data, openPlayer) }
                        }
                    }
                }
            }
        }
    }
}

/** Compact marks; full, accessible descriptions remain on the player's appearance history. */
fun playerEventMarks(playerId: Int, events: List<MatchEvent>): String {
    val own = events.filter { it.involves(playerId) }
    return listOfNotNull(
        own.count { it.kind == EventKind.GOAL && it.playerId == playerId && it.goalKind != GoalKind.OWN_GOAL }.takeIf { it > 0 }?.let { "G×$it" },
        own.count { it.assistPlayerId == playerId }.takeIf { it > 0 }?.let { "A×$it" },
        own.find { it.kind == EventKind.GOAL && it.playerId == playerId && it.goalKind == GoalKind.OWN_GOAL }?.let { "GS" },
        own.filter { it.kind == EventKind.CARD }.lastOrNull()?.let { when (it.cardKind) { CardKind.YELLOW -> "ŻK"; CardKind.SECOND_YELLOW -> "2ŻK"; CardKind.RED -> "CK"; null -> "K" } },
        own.find { it.kind == EventKind.SUBSTITUTION }?.let { "${if (it.incomingPlayerId == playerId) "↑" else "↓"}${it.time.label}" }
    ).joinToString(" · ")
}
