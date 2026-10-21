package pl.stasgroch.footballhub.ui.screens

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver

/** UI-only state held above the navigation back stack. */
@Stable
class MatchFilters(league: Int = 0, status: String = "ALL", query: String = "", date: String = java.time.LocalDate.now().toString(), chronological: Boolean = false) {
    var league by mutableIntStateOf(league)
    var status by mutableStateOf(status)
    var query by mutableStateOf(query)

    var date by mutableStateOf(date)
    var chronological by mutableStateOf(chronological)

    companion object {
        val Saver = listSaver<MatchFilters, Any>(
            save = { listOf(it.league, it.status, it.query, it.date, it.chronological) },
            restore = { MatchFilters(it[0] as Int, it[1] as String, it[2] as String, it[3] as String, it[4] as Boolean) }
        )
    }
}

