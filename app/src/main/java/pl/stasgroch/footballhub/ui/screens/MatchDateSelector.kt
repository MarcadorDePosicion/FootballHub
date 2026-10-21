package pl.stasgroch.footballhub.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable fun MatchDateSelector(filters: MatchFilters, modifier: Modifier = Modifier) {
    val date = LocalDate.parse(filters.date)
    val today = LocalDate.now()
    val relative = when (date) {
        today -> "Dziś"
        today.minusDays(1) -> "Wczoraj"
        today.plusDays(1) -> "Jutro"
        else -> null
    }
    Surface(modifier, shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton({ filters.date = date.minusDays(1).toString() }) { Icon(HubIcons.Back, "Poprzedni dzień") }
            Text(buildAnnotatedString {
                if (relative != null) append(relative)
                else {
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface)) {
                        append(date.format(DateTimeFormatter.ofPattern("EEE", Locale.forLanguageTag("pl"))))
                    }
                    append(" " + date.format(DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("pl"))))
                }
            }, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = MaterialTheme.colorScheme.secondary)
            IconButton({ filters.date = date.plusDays(1).toString() }) { Icon(HubIcons.Next, "Następny dzień") }
        }
    }
}
