package pl.stasgroch.footballhub.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pl.stasgroch.footballhub.data.model.*

/** Separate lazy rows keep large league groups cheap and individually reachable. */
fun LazyListScope.groupedMatches(key: String, matches: List<Match>, data: FootballData, openMatch: (Int) -> Unit) {
    matches.groupBy { it.leagueId }.forEach { (league, games) ->
        item(key = "$key-league-$league") {
            Surface(shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp), modifier = Modifier.padding(top = 8.dp)) {
                Text(data.leagues.first { it.id == league }.name, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
            }
        }
        games.forEachIndexed { index, match ->
            item(key = "$key-match-${match.id}") {
                Surface(onClick = { openMatch(match.id) }, shape = RoundedCornerShape(
                    bottomStart = if (index == games.lastIndex) 16.dp else 0.dp,
                    bottomEnd = if (index == games.lastIndex) 16.dp else 0.dp)) {
                    Column {
                        if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .4f))
                        CompactMatchRow(match, data)
                    }
                }
            }
        }
    }
}

@Composable private fun CompactMatchRow(match: Match, data: FootballData) {
    val finished = match.status == "FINISHED"
    val scheduled = match.status == "SCHEDULED"
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            listOf(data.team(match.homeId) to match.homeScore, data.team(match.awayId) to match.awayScore).forEachIndexed { index, (team, score) ->
                val other = if (index == 0) match.awayScore else match.homeScore
                val winner = finished && score != null && other != null && score > other
                val loser = finished && score != null && other != null && score < other
                val color = if (loser) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TeamEmblem(team, 24.dp)
                    Text(team.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (winner) FontWeight.Bold else FontWeight.Normal, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (!scheduled) Text(score?.toString() ?: "–", Modifier.width(26.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        style = MaterialTheme.typography.titleMedium, color = if (winner) MaterialTheme.colorScheme.primary else color)
                }
            }
        }
        Column(Modifier.width(60.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(if (finished) "Koniec" else match.time, style = MaterialTheme.typography.labelMedium, color = if (scheduled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(displayDate(match.date), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            if (!scheduled && !finished) MatchStatus(match.status)
        }
    }
}

