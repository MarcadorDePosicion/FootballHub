package pl.stasgroch.footballhub.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pl.stasgroch.footballhub.data.model.*

@Composable fun PlayerMatchRow(match: Match, data: FootballData, playerId: Int, teamId: Int?, unusedBench: Boolean, open: () -> Unit) {
    Card(onClick = open, modifier = Modifier.fillMaxWidth().testTag("appearance-${match.id}")) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("${displayDate(match.date)} • ${data.leagues.find { it.id == match.leagueId }?.name.orEmpty()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            listOf(match.homeId to match.homeScore, match.awayId to match.awayScore).forEach { (id, score) ->
                val opponentScore = if (id == match.homeId) match.awayScore else match.homeScore
                val won = score != null && opponentScore != null && score > opponentScore
                Row(Modifier.fillMaxWidth().heightIn(min = 28.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TeamEmblem(data.team(id), 22.dp)
                    Text(data.team(id).name, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (won) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = if (won) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                    if (id == teamId && !unusedBench) PlayerAchievements(playerId, data.eventsFor(match.id))
                }
            }
            }
            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.background) {
                Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                    if (unusedBench) BenchSymbol() else {
                        val minutes = data.appearances.find { it.matchId == match.id && it.playerId == playerId }
                            ?.playedMinutes(data.matchEvents.find { it.matchId == match.id }, match.status == "FINISHED")
                        Text(minutes?.let { "$it′" } ?: "—", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                listOf(match.homeScore to match.awayScore, match.awayScore to match.homeScore).forEach { (score, other) ->
                    Box(Modifier.width(20.dp).heightIn(min = 28.dp), contentAlignment = Alignment.Center) {
                        Text(score?.toString() ?: "–", style = MaterialTheme.typography.labelMedium,
                            color = if (score != null && other != null && score > other) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            }
        }
    }
}
