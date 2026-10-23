package pl.stasgroch.footballhub.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pl.stasgroch.footballhub.data.model.*

@Composable fun PlayerRow(player: Player, highlighted: Boolean = false, openPlayer: (Int) -> Unit = {}, events: List<MatchEvent> = emptyList(), players: List<Player> = emptyList()) {
    Surface(color = if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "Otwórz profil zawodnika") { openPlayer(player.id) }.testTag("player-${player.id}")) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(player.number?.toString() ?: "—", Modifier.width(28.dp), color = MaterialTheme.colorScheme.secondary, textAlign = TextAlign.Center)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(player.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    PlayerAchievements(player.id, events)
                }
                Text(listOfNotNull(player.position?.label, player.nationality).joinToString(" • ").ifBlank { "Brak informacji o pozycji i narodowości" },
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                events.filter { it.kind == EventKind.SUBSTITUTION && it.involves(player.id) }.forEach { event ->
                    val entering = event.incomingPlayerId == player.id
                    val otherId = if (entering) event.playerId else event.incomingPlayerId
                    val other = players.find { it.id == otherId }?.name ?: "Zawodnik"
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${if (entering) "↑" else "↓"} ${event.time.label}", style = MaterialTheme.typography.labelSmall,
                            color = if (entering) MaterialTheme.colorScheme.primary else Color(0xFFFF7979))
                        Text("${if (entering) "Schodzi" else "Wchodzi"}: $other", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

fun LazyListScope.playerRoster(players: List<Player>, highlightedId: Int? = null, openPlayer: (Int) -> Unit = {}) {
    if (players.isEmpty()) item { EmptyState("Lista zawodników nie została jeszcze udostępniona.") }
    if (players.any { it.demo }) item { DemoSquadLabel() }
    (PlayerPosition.entries.map { it as PlayerPosition? } + listOf(null)).forEach { position ->
        val group = players.filter { it.position == position }.sortedBy { it.number ?: Int.MAX_VALUE }
        if (group.isNotEmpty()) {
            item { Text(position?.label ?: "Pozostałe pozycje", style = MaterialTheme.typography.titleSmall) }
            items(group, key = { "player-${it.id}" }) { PlayerRow(it, it.id == highlightedId, openPlayer) }
        }
    }
}

@Composable private fun DemoSquadLabel() {
    Text("DEMO • fikcyjni zawodnicy i ustawienie, nie rzeczywista kadra klubu",
        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
}

@Composable fun MatchLineups(match: Match, data: FootballData, openPlayer: (Int) -> Unit = {}) {
    val events = data.eventsFor(match.id)
    var away by rememberSaveable(match.id) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!away, { away = false }, { Text("Gospodarze") }, modifier = Modifier.weight(1f).testTag("lineup-home"))
            FilterChip(away, { away = true }, { Text("Goście") }, modifier = Modifier.weight(1f).testTag("lineup-away"))
        }
        // Measure both teams before placement. A shorter bench must not shrink the
        // enclosing pager and make LazyColumn clamp its current scroll position.
        Layout(modifier = Modifier.testTag("lineup-sides"), content = {
            listOf(match.homeId, match.awayId).forEach { teamId ->
                val visible = teamId == if (away) match.awayId else match.homeId
                Column(Modifier.then(if (visible) Modifier else Modifier.clearAndSetSemantics { }),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LineupTeam(match, data, teamId, events, openPlayer)
                }
            }
        }) { measurables, constraints ->
            val sides = measurables.map { it.measure(constraints.copy(minHeight = 0)) }
            layout(sides.maxOf { it.width }, sides.maxOf { it.height }) {
                sides[if (away) 1 else 0].placeRelative(0, 0)
            }
        }
    }
}

@Composable private fun LineupTeam(match: Match, data: FootballData, teamId: Int,
    events: List<MatchEvent>, openPlayer: (Int) -> Unit) {
    val team = data.team(teamId)
    val lineup = data.lineups.find { it.matchId == match.id && it.teamId == teamId }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TeamEmblem(team, 32.dp)
            Text(team.name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            Text(lineup?.formation ?: "—", color = MaterialTheme.colorScheme.secondary)
        }
        if (lineup == null) {
            EmptyState("Skład nie został jeszcze udostępniony.")
        } else {
            if (lineup.demo) DemoSquadLabel()
            val rows = lineup.starterRows.orEmpty().map { row -> row.mapNotNull { id -> data.players.find { it.id == id } } }
            if (lineup.hasValidFormation() && rows.flatten().size == 11) FootballPitch(rows, events, openPlayer)
            else {
                Text("Brak pełnego ustawienia — prezentujemy dostępnych zawodników.", style = MaterialTheme.typography.bodySmall)
                rows.flatten().distinctBy { it.id }.forEach { PlayerRow(it, openPlayer = openPlayer, events = events, players = data.players) }
            }
            lineup.coach?.let { coach ->
                Surface(shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(coach.name, style = MaterialTheme.typography.bodyMedium)
                        Text("Trener", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Text("Rezerwowi", style = MaterialTheme.typography.titleSmall)
            val bench = lineup.substitutes.orEmpty().mapNotNull { id -> data.players.find { it.id == id } }
            if (bench.isEmpty()) Text("Lista rezerwowych nie jest dostępna.", style = MaterialTheme.typography.bodySmall)
            bench.forEach { PlayerRow(it, openPlayer = openPlayer, events = events, players = data.players) }
            if (events.isNotEmpty()) EventLegend()
        }
}

@Composable private fun FootballPitch(rows: List<List<Player>>, events: List<MatchEvent>, openPlayer: (Int) -> Unit) {
    val line = Color.White.copy(alpha = .28f)
    // Height grows with row count; labels can wrap instead of colliding on narrow screens.
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("football-pitch").background(Color(0xFF12382F), MaterialTheme.shapes.medium)) {
        val pitchHeight = (maxWidth * 1.45f).coerceAtLeast(120.dp * rows.size)
        Canvas(Modifier.matchParentSize().padding(8.dp)) {
            val w = size.width; val h = size.height; val stroke = Stroke(1.dp.toPx())
            drawRect(line, style = stroke)
            drawLine(line, Offset(0f, h / 2), Offset(w, h / 2), stroke.width)
            drawCircle(line, w * .14f, Offset(w / 2, h / 2), style = stroke)
            drawCircle(line, 2.dp.toPx(), Offset(w / 2, h / 2))
            listOf(0f, h * .84f).forEach { y -> drawRect(line, Offset(w * .2f, y), Size(w * .6f, h * .16f), style = stroke) }
            listOf(0f, h * .94f).forEach { y -> drawRect(line, Offset(w * .36f, y), Size(w * .28f, h * .06f), style = stroke) }
        }
        Column(Modifier.fillMaxWidth().height(pitchHeight).padding(horizontal = 12.dp, vertical = 24.dp)) {
            rows.asReversed().forEach { row ->
                Row(Modifier.fillMaxWidth().weight(1f).padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    row.forEach { player ->
                        Column(Modifier.weight(1f).heightIn(min = 48.dp).clickable(role = Role.Button, onClickLabel = "Profil: ${player.name}") { openPlayer(player.id) }.testTag("pitch-player-${player.id}").padding(horizontal = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primaryContainer) {
                                Text(player.number?.toString() ?: "—", Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium)
                            }
                            Text(player.name.substringAfterLast(' '), Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelSmall, color = Color.White)
                            PlayerAchievements(player.id, events)
                            val marks = events.firstOrNull { it.kind == EventKind.SUBSTITUTION && it.involves(player.id) }?.let { "${if (it.incomingPlayerId == player.id) "↑" else "↓"}${it.time.label}" }.orEmpty()
                            if (marks.isNotBlank()) Text(marks, Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelSmall, color = if (marks.startsWith("↓")) Color(0xFFFF7979) else MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}
