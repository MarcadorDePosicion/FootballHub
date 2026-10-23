package pl.stasgroch.footballhub

import org.junit.Assert.*
import org.junit.Test
import pl.stasgroch.footballhub.data.model.*

class PlayedMinutesTest {
    @Test fun minutesRespectEntryExitAndDismissal() {
        val sub = MatchEvent(1, EventKind.SUBSTITUTION, MatchMinute(60), 1, 1, incomingPlayerId = 2)
        val late = MatchEvent(2, EventKind.SUBSTITUTION, MatchMinute(85), 1, 3, incomingPlayerId = 4)
        val red = MatchEvent(3, EventKind.CARD, MatchMinute(82), 1, 5, cardKind = CardKind.SECOND_YELLOW)
        val events = MatchEvents(1, EventCoverage.COMPLETE, listOf(sub, late, red))
        assertEquals(60, PlayerAppearance(1, 1, 1, true).playedMinutes(events, true))
        assertEquals(30, PlayerAppearance(2, 1, 1, false).playedMinutes(events, true))
        assertEquals(5, PlayerAppearance(4, 1, 1, false).playedMinutes(events, true))
        assertEquals(82, PlayerAppearance(5, 1, 1, true).playedMinutes(events, true))
        assertEquals(90, PlayerAppearance(6, 1, 1, true).playedMinutes(events, true))
        assertNull(PlayerAppearance(7, 1, 1, false).playedMinutes(events, true))
        assertNull(PlayerAppearance(6, 1, 1, true).playedMinutes(events.copy(coverage = EventCoverage.PARTIAL), true))
    }
}
