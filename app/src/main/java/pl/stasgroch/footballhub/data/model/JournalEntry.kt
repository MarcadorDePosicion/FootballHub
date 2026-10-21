package pl.stasgroch.footballhub.data.model

data class JournalEntry(
    val id: String,
    val matchId: Int,
    val kind: String,
    val text: String,
    val homeScore: Int? = null,
    val awayScore: Int? = null,
    val updatedAt: Long = System.currentTimeMillis()
)
object EntryValidation {
    fun error(kind: String, text: String, home: String, away: String): String? {
        if (kind !in listOf("NOTE", "PREDICTION")) return "Nieprawidłowy rodzaj wpisu."
        if (kind == "NOTE" && text.isBlank()) return "Wpisz treść notatki."
        if (text.length > 2000) return "Maksymalna długość to 2000 znaków."
        if (kind == "PREDICTION" && (home.toIntOrNull() !in 0..99 || away.toIntOrNull() !in 0..99))
            return "Podaj dwa wyniki od 0 do 99."
        return null
    }
}

