package pl.stasgroch.footballhub.data.model

data class Team(val id: Int, val name: String, val shortName: String, val city: String, val stadium: String, val founded: Int, val leagueId: Int)
data class League(val id: Int, val name: String, val country: String)
data class Match(val id: Int, val leagueId: Int, val homeId: Int, val awayId: Int, val date: String, val time: String, val status: String, val homeScore: Int?, val awayScore: Int?, val round: Int, val venue: String? = null) {
    val matchStatus: MatchStatusKind get() = MatchStatusKind.from(status)
    val venueLabel: String get() = venue?.takeIf { it.isNotBlank() } ?: "Stadion nieznany"
}
data class Standing(val leagueId: Int, val teamId: Int, val played: Int, val won: Int, val drawn: Int, val lost: Int, val goalsFor: Int, val goalsAgainst: Int,
    val position: Int? = null, val officialPoints: Int? = null) {
    val points: Int get() = won * 3 + drawn
    val displayPoints: Int get() = officialPoints ?: points
}
/** Official ranks may start above one in a partial table, but must be complete and unique. */
fun List<Standing>.hasOfficialPositions(): Boolean = isNotEmpty() &&
    map { it.leagueId }.distinct().size == 1 && all { (it.position ?: 0) > 0 } &&
    map { it.position }.distinct().size == size

fun List<Standing>.orderedStandings(): List<Standing> =
    if (hasOfficialPositions()) sortedBy { it.position } else sortedByDescending { it.displayPoints }

enum class MatchStatusKind(val label: String) {
    SCHEDULED("Planowany"), TIMED("Planowany"), IN_PLAY("Trwa"), PAUSED("Przerwa"),
    EXTRA_TIME("Dogrywka"), PENALTY_SHOOTOUT("Rzuty karne"), FINISHED("Koniec"),
    POSTPONED("Przełożony"), SUSPENDED("Wstrzymany"), CANCELLED("Odwołany"), AWARDED("Walkower"), UNKNOWN("Status nieznany");

    val isScheduled: Boolean get() = this == SCHEDULED || this == TIMED
    val isLive: Boolean get() = this in setOf(IN_PLAY, PAUSED, EXTRA_TIME, PENALTY_SHOOTOUT)
    val isFinished: Boolean get() = this == FINISHED || this == AWARDED
    val isPlayed: Boolean get() = this == FINISHED
    val canPredict: Boolean get() = isScheduled || isLive

    companion object {
        fun from(value: String?): MatchStatusKind = when (value) {
            "LIVE", "IN_PROGRESS" -> IN_PLAY
            else -> entries.firstOrNull { it.name == value } ?: UNKNOWN
        }
    }
}

fun Match.matchesStatusFilter(filter: String): Boolean = when (filter) {
    "ALL" -> true
    "SCHEDULED" -> matchStatus.isScheduled
    "FINISHED" -> matchStatus.isFinished
    "LIVE", "IN_PLAY", "IN_PROGRESS" -> matchStatus.isLive
    else -> MatchStatusKind.from(filter).let { it != MatchStatusKind.UNKNOWN && it == matchStatus }
}

enum class PlayerPosition(val label: String) { GOALKEEPER("Bramkarze"), DEFENDER("Obrońcy"), MIDFIELDER("Pomocnicy"), FORWARD("Napastnicy") }
data class Player(val id: Int, val teamId: Int?, val name: String, val number: Int? = null,
    val position: PlayerPosition? = null, val nationality: String? = null, val demo: Boolean = false,
    val dateOfBirth: String? = null, val heightCm: Int? = null, val preferredFoot: PreferredFoot? = null,
    val externalId: ExternalId? = null) {
    fun age(today: java.time.LocalDate = java.time.LocalDate.now()): Int? =
        dateOfBirth?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }
            ?.takeUnless { it.isAfter(today) }?.let { java.time.Period.between(it, today).years }
}
/** Rows run from goalkeeper to attack; order within a row runs from left to right. */
data class MatchLineup(val matchId: Int, val teamId: Int, val formation: String? = null,
    val starterRows: List<List<Int>>? = null, val substitutes: List<Int>? = null, val demo: Boolean = false, val coach: Coach? = null) {
    fun hasValidFormation(): Boolean {
        val lines = formation?.split("-")?.map { it.toIntOrNull() ?: return false } ?: return false
        val rows = starterRows ?: return false
        return lines.isNotEmpty() && lines.all { it in 1..5 } && lines.sum() == 10 &&
            rows.map { it.size } == listOf(1) + lines && rows.flatten().distinct().size == 11
    }
}
data class FootballData(val teams: List<Team> = emptyList(), val leagues: List<League> = emptyList(), val matches: List<Match> = emptyList(), val standings: List<Standing> = emptyList(),
    val players: List<Player> = emptyList(), val lineups: List<MatchLineup> = emptyList(),
    val matchEvents: List<MatchEvents> = emptyList(), val appearances: List<PlayerAppearance> = emptyList()) {
    fun team(id: Int): Team? = teams.firstOrNull { it.id == id }
    fun league(id: Int): League? = leagues.firstOrNull { it.id == id }
}

