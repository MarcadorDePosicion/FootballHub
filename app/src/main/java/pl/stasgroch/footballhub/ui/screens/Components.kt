package pl.stasgroch.footballhub.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.stasgroch.footballhub.data.model.*
import pl.stasgroch.footballhub.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// Fixed reserve for the expanded overlay; independent of the animation's current height.
fun detailListPadding() = PaddingValues(16.dp)
fun navigationListPadding() = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp)

fun displayDate(value: String): String = runCatching {
    LocalDate.parse(value).format(DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("pl")))
}.getOrDefault(value)

fun statusLabel(status: String): String = when (status) {
    "FINISHED" -> "Koniec"
    "LIVE", "IN_PLAY", "IN_PROGRESS" -> "Trwa"
    "SCHEDULED" -> "Planowany"
    else -> "Status: $status"
}

@Composable fun SectionTitle(title: String, subtitle: String? = null) {
    Column(Modifier.padding(top = HubSpace.sm, bottom = HubSpace.xs), verticalArrangement = Arrangement.spacedBy(HubSpace.xs)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
@Composable fun EmptyState(text: String, icon: ImageVector = HubIcons.Notes) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(HubSpace.xl), verticalArrangement = Arrangement.spacedBy(HubSpace.md)) {
            Icon(icon, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.secondary)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@Composable fun TeamEmblem(team: Team, size: Dp = 28.dp) {
    val tint = HubColors.BadgeColors[(team.id - 1).mod(HubColors.BadgeColors.size)]
    // Decorative initials have a fixed visual size; the adjacent accessible team name scales normally.
    val initialsSize = (size.value * .36f / LocalDensity.current.fontScale).sp
    Surface(color = tint.copy(alpha = .14f), shape = RoundedCornerShape(size / 3),
        modifier = Modifier.size(size).clearAndSetSemantics { }) {
        Box(contentAlignment = Alignment.Center) {
            Text(team.shortName.take(3), color = tint,
                style = if (size >= 48.dp) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelSmall,
                fontSize = initialsSize, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
        }
    }
}
@Composable fun MatchStatus(status: String) {
    val active = status in listOf("LIVE", "IN_PLAY", "IN_PROGRESS")
    val color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Text(if (active) "● ${statusLabel(status)}" else statusLabel(status),
        style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
}
@Composable fun MatchCard(match: Match, data: FootballData, onClick: () -> Unit) {
    val home = data.team(match.homeId)
    val away = data.team(match.awayId)
    val league = data.league(match.leagueId)
    if (home == null || away == null || league == null) {
        EmptyState("Niepełne dane meczu — drużyna lub liga niedostępna.")
        return
    }
    Card(onClick = onClick, shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(HubSpace.md), verticalArrangement = Arrangement.spacedBy(HubSpace.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(league.name, Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                MatchStatus(match.status)
            }
            listOf(home to match.homeScore, away to match.awayScore).forEach { (team, score) ->
                Row(Modifier.fillMaxWidth().heightIn(min = 30.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(HubSpace.sm)) {
                    TeamEmblem(team)
                    Text(team.name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(score?.toString() ?: "–", Modifier.width(40.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        style = MaterialTheme.typography.titleMedium, color = if (score == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = HubSpace.xs), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(displayDate(match.date), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(match.time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
@Composable fun PrimaryAction(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick, enabled = enabled, shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().heightIn(min = HubSpace.touch)) { Text(label) }
}

