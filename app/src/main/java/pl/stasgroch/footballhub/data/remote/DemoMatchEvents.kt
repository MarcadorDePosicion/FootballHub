package pl.stasgroch.footballhub.data.remote

import pl.stasgroch.footballhub.data.model.*

/** Fictional scenarios for the existing mock scores, not records of real fixtures. */
object DemoMatchEvents {
    fun create(): List<MatchEvents> = listOf(
        MatchEvents(3, EventCoverage.COMPLETE, listOf(
            goal(301, 18, 9, 908, 906, 1, 0),
            MatchEvent(302, EventKind.CARD, MatchMinute(31), 10, 1002, cardKind = CardKind.YELLOW),
            goal(303, 45, 10, 1009, null, 1, 1, GoalKind.PENALTY, 2),
            MatchEvent(304, EventKind.SUBSTITUTION, MatchMinute(63), 9, 908, incomingPlayerId = 913),
            goal(305, 78, 9, 913, 907, 2, 1),
            MatchEvent(306, EventKind.CARD, MatchMinute(82), 10, 1002, cardKind = CardKind.SECOND_YELLOW)
        ), demo = true),
        MatchEvents(4, EventCoverage.COMPLETE, listOf(
            goal(401, 12, 3, 308, 306, 1, 0),
            goal(402, 35, 4, 409, null, 1, 1),
            goal(403, 54, 3, 308, 307, 2, 1),
            MatchEvent(404, EventKind.SUBSTITUTION, MatchMinute(67), 4, 409, incomingPlayerId = 413),
            goal(405, 80, 3, 402, null, 3, 1, GoalKind.OWN_GOAL),
            MatchEvent(406, EventKind.CARD, MatchMinute(90, 3), 4, 401, cardKind = CardKind.RED)
        ), demo = true),
        MatchEvents(5, EventCoverage.COMPLETE, listOf(
            goal(501, 24, 7, 708, null, 1, 0),
            goal(502, 70, 8, 809, 806, 1, 1)
        ), demo = true)
    )
    private fun goal(id: Int, minute: Int, team: Int, scorer: Int, assist: Int?, home: Int, away: Int,
        type: GoalKind = GoalKind.REGULAR, added: Int? = null) =
        MatchEvent(id, EventKind.GOAL, MatchMinute(minute, added), team, scorer, assist,
            goalKind = type, homeScore = home, awayScore = away)

    fun appearances(matches: List<Match>, lineups: List<MatchLineup>): List<PlayerAppearance> {
        val events = create().associateBy { it.matchId }
        return lineups.filter { lineup -> matches.any { it.id == lineup.matchId && it.status == "FINISHED" } }
            .flatMap { lineup ->
                val starters = lineup.starterRows.orEmpty().flatten()
                val entered = events[lineup.matchId]?.events.orEmpty()
                    .filter { it.kind == EventKind.SUBSTITUTION && it.teamId == lineup.teamId }
                    .mapNotNull { it.incomingPlayerId }
                (starters + entered).distinct().map { PlayerAppearance(it, lineup.matchId, lineup.teamId, it in starters) }
            }
    }
}
