package pl.stasgroch.footballhub.data.model

/** Provider IDs never replace the stable IDs used by UI, lineups and journal records. */
data class ExternalId(val provider: String, val id: String)
enum class PreferredFoot(val label: String) { LEFT("Lewa"), RIGHT("Prawa"), BOTH("Obie") }
data class Coach(val name: String, val externalId: ExternalId? = null)
enum class EventCoverage { UNAVAILABLE, PARTIAL, COMPLETE }
enum class EventKind { GOAL, CARD, SUBSTITUTION }
enum class GoalKind(val label: String) { REGULAR("Gol"), PENALTY("Rzut karny"), OWN_GOAL("Gol samobójczy") }
enum class CardKind(val label: String) { YELLOW("Żółta kartka"), SECOND_YELLOW("Druga żółta kartka"), RED("Czerwona kartka") }
data class MatchMinute(val minute: Int, val added: Int? = null) : Comparable<MatchMinute> {
    val label: String get() = "$minute${added?.takeIf { it > 0 }?.let { "+$it" }.orEmpty()}′"
    override fun compareTo(other: MatchMinute): Int = compareValuesBy(this, other, { it.minute }, { it.added ?: 0 })
}
/** teamId is the scoring team for a goal (also for own goals).
 * playerId is the scorer/card recipient/player leaving; incomingPlayerId is the substitute. */
data class MatchEvent(val id: Int, val kind: EventKind, val time: MatchMinute, val teamId: Int,
    val playerId: Int? = null, val assistPlayerId: Int? = null, val incomingPlayerId: Int? = null,
    val goalKind: GoalKind? = null, val cardKind: CardKind? = null,
    val homeScore: Int? = null, val awayScore: Int? = null) {
    fun involves(player: Int) = player == playerId || player == assistPlayerId || player == incomingPlayerId
}
/** Missing/partial coverage is never interpreted as an event-free real match. */
data class MatchEvents(val matchId: Int, val coverage: EventCoverage = EventCoverage.UNAVAILABLE,
    val events: List<MatchEvent> = emptyList(), val demo: Boolean = false)
data class PlayerAppearance(val playerId: Int, val matchId: Int, val teamId: Int,
    val started: Boolean? = null, val minutesPlayed: Int? = null)

/** Regulation minutes; added time is not included in this compact display. */
fun PlayerAppearance.playedMinutes(events: MatchEvents?, finished: Boolean): Int? {
    minutesPlayed?.let { return it }
    if (!finished || events?.coverage != EventCoverage.COMPLETE) return null
    val start = if (started == true) 0 else events.events.firstOrNull {
        it.kind == EventKind.SUBSTITUTION && it.incomingPlayerId == playerId
    }?.time?.minute ?: return null
    val end = events.events.filter {
        it.playerId == playerId && (it.kind == EventKind.SUBSTITUTION ||
            (it.kind == EventKind.CARD && it.cardKind in listOf(CardKind.RED, CardKind.SECOND_YELLOW)))
    }.minOfOrNull { it.time.minute } ?: 90
    return (end.coerceAtMost(90) - start.coerceAtMost(90)).coerceAtLeast(0)
}

fun FootballData.eventsFor(matchId: Int): List<MatchEvent> =
    matchEvents.find { it.matchId == matchId }?.events.orEmpty().sortedWith(compareBy({ it.time }, { it.id }))

fun MatchEvent.playerSummary(id: Int): String = when {
    kind == EventKind.GOAL && assistPlayerId == id -> "Asysta ${time.label}"
    kind == EventKind.GOAL -> "${goalKind?.label ?: "Gol"} ${time.label}"
    kind == EventKind.CARD -> "${cardKind?.label ?: "Kartka"} ${time.label}"
    incomingPlayerId == id -> "Wejście ${time.label}"
    else -> "Zejście ${time.label}"
}
