package pl.stasgroch.footballhub

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import org.junit.Assert.*
import org.junit.Test
import pl.stasgroch.footballhub.ui.navigation.NavigationScrollState

class NavigationScrollStateTest {
    @Test fun smallReversalsAndUnconsumedOverscrollDoNotToggleBar() {
        val state = NavigationScrollState(24f)
        fun move(y: Float) = state.onPreScroll(Offset(0f, y), NestedScrollSource.UserInput)
        state.onPostScroll(Offset.Zero, Offset(0f, -100f), NestedScrollSource.UserInput)
        assertTrue(state.expanded)
        repeat(10) { move(-3f); move(3f) }
        assertTrue(state.expanded)
        move(-30f)
        assertFalse(state.expanded)
        move(5f)
        assertFalse(state.expanded)
        move(20f)
        assertTrue(state.expanded)
        move(-30f)
        state.expand()
        assertTrue(state.expanded)
    }
}

