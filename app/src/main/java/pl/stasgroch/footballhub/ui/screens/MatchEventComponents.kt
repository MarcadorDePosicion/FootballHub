package pl.stasgroch.footballhub.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pl.stasgroch.footballhub.data.model.*

private fun shortName(name: String): String {
    val parts = name.trim().split(" ").filter { it.isNotBlank() }
    return if (parts.size < 2) name else parts.first().take(1) + ". " + parts.drop(1).joinToString(" ")
}

@Composable fun PlayerLink(id: Int?, data: FootballData, openPlayer: (Int) -> Unit,
    muted: Boolean = false, suffix: String = "", align: TextAlign = TextAlign.Start, compact: Boolean = false) {
    val player = data.players.find { it.id == id }
    Text((player?.let { shortName(it.name) } ?: "Zawodnik nieznany") + suffix,
        modifier = Modifier.then(if (player != null) Modifier.clickable(role = Role.Button,
            onClickLabel = "Profil: ${player.name}") { openPlayer(player.id) }.testTag("event-player-${player.id}") else Modifier).padding(vertical = if (compact) 1.dp else 6.dp),
        style = MaterialTheme.typography.bodySmall, textAlign = align,
        color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
}

@Composable fun MatchScorers(match: Match, data: FootballData, openPlayer: (Int) -> Unit) {
    val goals = data.eventsFor(match.id).filter { it.kind == EventKind.GOAL }
    if (goals.isEmpty()) return
    Column {
        if (data.matchEvents.find { it.matchId == match.id }?.demo == true)
            Text("DEMO • strzelcy", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(match.homeId, match.awayId).forEach { teamId ->
                if (teamId == match.awayId) FootballSymbol()
                val away = teamId == match.awayId
                Column(Modifier.weight(1f), horizontalAlignment = if (away) Alignment.End else Alignment.Start) {
                    goals.filter { it.teamId == teamId }.groupBy { it.playerId }.forEach { (id, scored) ->
                        val minutes = scored.joinToString(", ") { goal -> goal.time.label + when (goal.goalKind) {
                            GoalKind.PENALTY -> " (k.)"; GoalKind.OWN_GOAL -> " (s.)"; else -> ""
                        } }
                        PlayerLink(id, data, openPlayer, muted = true, suffix = " $minutes", align = if (away) TextAlign.End else TextAlign.Start, compact = true)
                    }
                }
            }
        }
    }
}

@Composable fun MatchTimeline(match: Match, data: FootballData, openPlayer: (Int) -> Unit) {
    val record = data.matchEvents.find { it.matchId == match.id }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (record == null || record.coverage == EventCoverage.UNAVAILABLE) {
            Text("Zdarzenia meczu nie zostały jeszcze udostępnione.", style = MaterialTheme.typography.bodySmall)
        } else {
            if (record.demo) Text("DEMO • przykładowe zdarzenia", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            if (record.coverage == EventCoverage.PARTIAL) Text("Dostępny jest tylko fragment przebiegu meczu.", style = MaterialTheme.typography.labelSmall)
            if (record.events.isEmpty()) Text("Brak zarejestrowanych zdarzeń.", style = MaterialTheme.typography.bodySmall)
            if (record.events.any { it.teamId != match.homeId && it.teamId != match.awayId })
                Text("Pominięto zdarzenia z nieznanym powiązaniem drużyny.", style = MaterialTheme.typography.labelSmall)
            Surface(shape = MaterialTheme.shapes.large) {
                Column(Modifier.fillMaxWidth().padding(8.dp)) {
                    data.eventsFor(match.id).filter { it.teamId == match.homeId || it.teamId == match.awayId }.asReversed().forEach { event ->
                        val away = event.teamId == match.awayId
                        Row(Modifier.fillMaxWidth().padding(start = if (away) 28.dp else 0.dp, end = if (away) 0.dp else 28.dp)
                            .heightIn(min = 48.dp).testTag("event-${event.id}"), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (!away) EventMinute(event)
                            if (!away) EventSymbol(event)
                            Column(Modifier.weight(1f), horizontalAlignment = if (away) Alignment.End else Alignment.Start) {
                                val alignment = if (away) TextAlign.End else TextAlign.Start
                                if (event.kind == EventKind.SUBSTITUTION) {
                                    PlayerLink(event.incomingPlayerId, data, openPlayer, suffix = " ↑", align = alignment)
                                    PlayerLink(event.playerId, data, openPlayer, muted = true, suffix = " ↓", align = alignment)
                                } else {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (event.kind == EventKind.GOAL && !away) EventScore(event)
                                        Box(Modifier.weight(1f, fill = false)) { PlayerLink(event.playerId, data, openPlayer, align = alignment) }
                                        if (event.kind == EventKind.GOAL && away) EventScore(event)
                                    }
                                    event.assistPlayerId?.let { PlayerLink(it, data, openPlayer, muted = true, suffix = " • asysta", align = alignment) }
                                    if (event.goalKind == GoalKind.PENALTY || event.goalKind == GoalKind.OWN_GOAL)
                                        Text(event.goalKind.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (away) EventSymbol(event)
                            if (away) EventMinute(event)
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun EventScore(event: MatchEvent) {
    Text("${event.homeScore ?: "—"} : ${event.awayScore ?: "—"}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable private fun EventMinute(event: MatchEvent) {
    Text(event.time.label, Modifier.widthIn(min = 36.dp), style = MaterialTheme.typography.labelSmall)
}

@Composable private fun EventSymbol(event: MatchEvent) {
    when (event.kind) {
        EventKind.GOAL -> FootballSymbol(event.goalKind == GoalKind.OWN_GOAL)
        EventKind.SUBSTITUTION -> Text("⇄", color = MaterialTheme.colorScheme.secondary)
        EventKind.CARD -> CardSymbol(event.cardKind ?: CardKind.YELLOW)
    }
}

/** Compact marks; full, accessible descriptions remain on the player's appearance history. */
fun playerEventMarks(playerId: Int, events: List<MatchEvent>): String {
    val own = events.filter { it.involves(playerId) }
    return listOfNotNull(
        own.count { it.kind == EventKind.GOAL && it.playerId == playerId && it.goalKind != GoalKind.OWN_GOAL }.takeIf { it > 0 }?.let { "G×$it" },
        own.count { it.assistPlayerId == playerId }.takeIf { it > 0 }?.let { "A×$it" },
        own.find { it.kind == EventKind.GOAL && it.playerId == playerId && it.goalKind == GoalKind.OWN_GOAL }?.let { "GS" },
        own.filter { it.kind == EventKind.CARD }.lastOrNull()?.let { when (it.cardKind) { CardKind.YELLOW -> "ŻK"; CardKind.SECOND_YELLOW -> "2ŻK → CK"; CardKind.RED -> "CK"; null -> "K" } },
        own.find { it.kind == EventKind.SUBSTITUTION }?.let { "${if (it.incomingPlayerId == playerId) "↑" else "↓"}${it.time.label}" }
    ).joinToString(" · ")
}
