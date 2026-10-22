package pl.stasgroch.footballhub.data.model

data class Team(val id: Int, val name: String, val shortName: String, val city: String, val stadium: String, val founded: Int, val leagueId: Int)
data class League(val id: Int, val name: String, val country: String)
data class Match(val id: Int, val leagueId: Int, val homeId: Int, val awayId: Int, val date: String, val time: String, val status: String, val homeScore: Int?, val awayScore: Int?, val round: Int)
data class Standing(val leagueId: Int, val teamId: Int, val played: Int, val won: Int, val drawn: Int, val lost: Int, val goalsFor: Int, val goalsAgainst: Int) {
    val points: Int get() = won * 3 + drawn
}
enum class PlayerPosition(val label: String) { GOALKEEPER("Bramkarze"), DEFENDER("Obrońcy"), MIDFIELDER("Pomocnicy"), FORWARD("Napastnicy") }
data class Player(val id: Int, val teamId: Int, val name: String, val number: Int? = null,
    val position: PlayerPosition? = null, val nationality: String? = null, val demo: Boolean = false)
/** Rows run from goalkeeper to attack; order within a row runs from left to right. */
data class MatchLineup(val matchId: Int, val teamId: Int, val formation: String? = null,
    val starterRows: List<List<Int>>? = null, val substitutes: List<Int>? = null, val demo: Boolean = false) {
    fun hasValidFormation(): Boolean {
        val lines = formation?.split("-")?.map { it.toIntOrNull() ?: return false } ?: return false
        val rows = starterRows ?: return false
        return lines.isNotEmpty() && lines.all { it in 1..5 } && lines.sum() == 10 &&
            rows.map { it.size } == listOf(1) + lines && rows.flatten().distinct().size == 11
    }
}
data class FootballData(val teams: List<Team> = emptyList(), val leagues: List<League> = emptyList(), val matches: List<Match> = emptyList(), val standings: List<Standing> = emptyList(),
    val players: List<Player> = emptyList(), val lineups: List<MatchLineup> = emptyList()) {
    fun team(id: Int) = teams.first { it.id == id }
}

