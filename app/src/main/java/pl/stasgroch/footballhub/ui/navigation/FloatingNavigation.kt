package pl.stasgroch.footballhub.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp

/** Observe vertical nested scrolling before a collapsing header consumes it. Never consume it. */
class NavigationScrollState(private val threshold: Float) : NestedScrollConnection {
    var expanded by mutableStateOf(true)
        private set
    private var travel = 0f
    fun expand() { expanded = true; travel = 0f }
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source == NestedScrollSource.UserInput) track(available.y)
        return Offset.Zero
    }
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        // Drag was already observed in pre-scroll; count only actual fling travel here.
        if (source == NestedScrollSource.SideEffect) track(consumed.y)
        return Offset.Zero
    }
    private fun track(delta: Float) {
        if (delta == 0f) return
        if (travel * delta < 0f) travel = 0f
        travel += delta
        if (travel <= -threshold) { expanded = false; travel = -threshold }
        if (travel >= threshold) { expanded = true; travel = threshold }
    }
}

@Composable fun rememberNavigationScrollState(): NavigationScrollState {
    val threshold = with(LocalDensity.current) { 24.dp.toPx() }
    return remember(threshold) { NavigationScrollState(threshold) }
}

@Composable fun FloatingNavigation(tabs: List<Triple<String, String, ImageVector>>, active: String,
    expanded: Boolean, modifier: Modifier = Modifier, navigate: (String) -> Unit) {
    val labelHeight by animateDpAsState(if (expanded) 22.dp else 0.dp, tween(200), label = "navigation-label-height")
    val padding by animateDpAsState(if (expanded) 6.dp else 2.dp, tween(200), label = "navigation-padding")
    val outerPadding by animateDpAsState(if (expanded) 3.dp else 1.dp, tween(200), label = "navigation-outer-padding")
    val width by animateFloatAsState(if (expanded) 1f else .88f, tween(200), label = "navigation-width")
    val alpha by animateFloatAsState(if (expanded) 1f else 0f, tween(160), label = "navigation-label-alpha")
    val shape = RoundedCornerShape(28.dp)
    Surface(modifier = modifier.fillMaxWidth(width).clip(shape)
        .background(Brush.verticalGradient(listOf(Color(0xF026343D), Color(0xF01C2931))))
        .border(.5.dp, Color.White.copy(alpha = .09f), shape)
        .testTag("floating-navigation").semantics { stateDescription = if (expanded) "Rozwinięta" else "Zwinięta" },
        shape = RoundedCornerShape(28.dp), color = Color.Transparent) {
        Row(Modifier.fillMaxWidth().selectableGroup().padding(outerPadding)) {
            tabs.forEach { (path, label, icon) ->
                val selected = active == path
                Surface(shape = RoundedCornerShape(24.dp), color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    modifier = Modifier.weight(1f)) {
                    Column(Modifier.heightIn(min = 48.dp).selectable(selected, role = Role.Tab, onClick = { navigate(path) })
                        .semantics { contentDescription = label }.padding(vertical = padding), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center) {
                        Icon(icon, null, Modifier.size(24.dp), tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        Box(Modifier.height(labelHeight).clipToBounds().graphicsLayer { this.alpha = alpha }, contentAlignment = Alignment.BottomCenter) {
                            Text(label, Modifier.clearAndSetSemantics {}, maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelSmall,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}


