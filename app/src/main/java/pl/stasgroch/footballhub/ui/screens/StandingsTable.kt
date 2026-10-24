package pl.stasgroch.footballhub.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import pl.stasgroch.footballhub.data.model.*
import pl.stasgroch.footballhub.ui.theme.HubSpace

@Composable private fun NumberCell(value: String, width: androidx.compose.ui.unit.Dp, emphasis: Boolean = false) {
    Text(value, Modifier.width(width), maxLines = 1, softWrap = false, textAlign = TextAlign.End,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (emphasis) FontWeight.Bold else FontWeight.Normal,
        color = if (emphasis) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
}
@Composable fun StandingsTable(rows: List<Standing>, data: FootballData, favorites: Set<Int>, openTeam: (Int) -> Unit, highlightedTeamId: Int? = null, highlightedTeamIds: Set<Int> = emptySet()) {
    val scale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val positionWidth = 26.dp * scale
    val playedWidth = 32.dp * scale
    val goalsWidth = 56.dp * scale
    val pointsWidth = 40.dp * scale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Grow numeric columns with text size; truncate the flexible name before scrolling.
        val minimumWidth = HubSpace.md * 2 + positionWidth + playedWidth + goalsWidth + pointsWidth + 128.dp
        val tableWidth = maxOf(maxWidth, minimumWidth)
        // A wide table owns its horizontal gesture, including motion past its edge.
        // Otherwise the residual fling would unexpectedly turn the surrounding page.
        val tableGesture = remember { object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource) = Offset(available.x, 0f)
            override suspend fun onPostFling(consumed: Velocity, available: Velocity) = Velocity(available.x, 0f)
        } }
        Column(Modifier.testTag("standings-scroll")
            .then(if (tableWidth > maxWidth) Modifier.nestedScroll(tableGesture) else Modifier)
            .horizontalScroll(rememberScrollState(), enabled = tableWidth > maxWidth)) {
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface, modifier = Modifier.width(tableWidth)) {
                Column {
                    Row(Modifier.fillMaxWidth().padding(horizontal = HubSpace.md, vertical = HubSpace.md), verticalAlignment = Alignment.CenterVertically) {
                        Text("#", Modifier.width(positionWidth), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Drużyna", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        NumberCell("M", playedWidth)
                        NumberCell("B", goalsWidth)
                        NumberCell("Pkt", pointsWidth)
                    }
                    rows.forEachIndexed { index, standing ->
                        val team = data.team(standing.teamId)
                        if (team == null || data.league(standing.leagueId) == null) {
                            Row(Modifier.fillMaxWidth().padding(HubSpace.md)) {
                                Text((index + 1).toString(), Modifier.width(positionWidth))
                                Text("Dane drużyny lub ligi są niedostępne.", Modifier.weight(1f))
                            }
                            return@forEachIndexed
                        }
                        val favorite = team.id in favorites
                        val highlighted = team.id == highlightedTeamId || team.id in highlightedTeamIds
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
                        Surface(onClick = { openTeam(team.id) },
                            modifier = Modifier.testTag("standing-${team.id}").semantics { selected = highlighted },
                            color = if (highlighted) MaterialTheme.colorScheme.primaryContainer else if (favorite) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .45f) else MaterialTheme.colorScheme.surface) {
                            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = HubSpace.md, vertical = HubSpace.sm), verticalAlignment = Alignment.CenterVertically) {
                                Text((index + 1).toString(), Modifier.width(positionWidth), style = MaterialTheme.typography.labelMedium,
                                    color = if (index == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(HubSpace.sm)) {
                                    TeamEmblem(team, 24.dp)
                                    Text(team.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Normal,
                                        color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                }
                                NumberCell(standing.played.toString(), playedWidth)
                                NumberCell("${standing.goalsFor}:${standing.goalsAgainst}", goalsWidth)
                                NumberCell(standing.points.toString(), pointsWidth, true)
                            }
                        }
                    }
                }
            }
        }
    }
}




