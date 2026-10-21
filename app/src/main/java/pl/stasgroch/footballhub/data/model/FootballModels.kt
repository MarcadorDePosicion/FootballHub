package pl.stasgroch.footballhub.data.model

data class Team(val id: Int, val name: String, val shortName: String, val city: String, val stadium: String, val founded: Int, val leagueId: Int)
data class League(val id: Int, val name: String, val country: String)
data class Match(val id: Int, val leagueId: Int, val homeId: Int, val awayId: Int, val date: String, val time: String, val status: String, val homeScore: Int?, val awayScore: Int?, val round: Int)
data class Standing(val leagueId: Int, val teamId: Int, val played: Int, val won: Int, val drawn: Int, val lost: Int, val goalsFor: Int, val goalsAgainst: Int) {
    val points: Int get() = won * 3 + drawn
}
data class FootballData(val teams: List<Team> = emptyList(), val leagues: List<League> = emptyList(), val matches: List<Match> = emptyList(), val standings: List<Standing> = emptyList()) {
    fun team(id: Int) = teams.first { it.id == id }
}

