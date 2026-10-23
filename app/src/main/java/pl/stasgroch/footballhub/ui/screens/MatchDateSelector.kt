package pl.stasgroch.footballhub.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable fun MatchDateSelector(filters: MatchFilters, pager: androidx.compose.foundation.pager.PagerState, anchor: Long, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton({ filters.shiftDay(-1) }) { Icon(HubIcons.Back, "Poprzedni dzień") }
            BoxWithConstraints(Modifier.weight(1f).heightIn(min = 48.dp).clipToBounds(), contentAlignment = Alignment.Center) {
                val width = constraints.maxWidth.toFloat()
                for (delta in -1..1) {
                    val page = pager.currentPage + delta
                    val date = LocalDate.ofEpochDay(anchor + page - 100000)
                    val today = LocalDate.now()
                    val relative = when (date) {
                        today -> "Dziś"
                        today.minusDays(1) -> "Wczoraj"
                        today.plusDays(1) -> "Jutro"
                        else -> null
                    }
                    Text(buildAnnotatedString {
                        if (relative != null) append(relative)
                        else {
                            withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface)) {
                                append(date.format(DateTimeFormatter.ofPattern("EEE", Locale.forLanguageTag("pl"))))
                            }
                            append(" " + date.format(DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("pl"))))
                        }
                    }, Modifier.fillMaxWidth().graphicsLayer {
                        translationX = (delta - pager.currentPageOffsetFraction) * width
                    }.then(if (delta != 0) Modifier.clearAndSetSemantics {} else Modifier),
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = MaterialTheme.colorScheme.secondary)
                }
            }
            IconButton({ filters.shiftDay(1) }) { Icon(HubIcons.Next, "Następny dzień") }
        }
    }
}
