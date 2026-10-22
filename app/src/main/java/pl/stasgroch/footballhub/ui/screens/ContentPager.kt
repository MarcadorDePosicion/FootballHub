package pl.stasgroch.footballhub.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.clearAndSetSemantics

/** Non-lazy detail pages inside a vertically scrolling parent. Both pages are measured
 * before a swipe; their common height stays stable throughout the gesture. */
@Composable
fun MeasuredContentPager(state: PagerState, modifier: Modifier = Modifier,
    content: @Composable (Int) -> Unit) {
    // LazyColumn may dispose this item while a profile is open or it is off screen.
    // Restoring its measured height prevents clamping the parent's saved scroll offset.
    var heights by rememberSaveable { mutableStateOf(List(state.pageCount) { 1 }) }
    val height = with(LocalDensity.current) { (heights.maxOrNull() ?: 1).toDp() }
    HorizontalPager(state, modifier.height(height.coerceAtLeast(1.dp)),
        beyondViewportPageCount = state.pageCount - 1, verticalAlignment = Alignment.Top) { page ->
        Column(Modifier.pageSemantics(state, page).fillMaxWidth().wrapContentHeight(unbounded = true)
            .onSizeChanged { size ->
                if (heights[page] != size.height) heights = heights.toMutableList().also { it[page] = size.height }
            }) {
            content(page)
        }
    }
}

/** Keep prefetched pages out of accessibility traversal once the pager is at rest. */
fun Modifier.pageSemantics(state: PagerState, page: Int): Modifier =
    if (!state.isScrollInProgress && page != state.currentPage) clearAndSetSemantics { } else this
