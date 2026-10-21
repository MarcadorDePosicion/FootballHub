package pl.stasgroch.footballhub.ui.screens

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup

@Composable fun EntryAction(label: String, icon: ImageVector, color: Color, action: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var showHint by remember { mutableStateOf(false) }
    LaunchedEffect(showHint) { if (showHint) { kotlinx.coroutines.delay(1800); showHint = false } }
    Box(Modifier.size(48.dp).hoverable(interaction).combinedClickable(role = Role.Button,
        onClick = { showHint = false; action() }, onLongClick = { showHint = true }), contentAlignment = Alignment.Center) {
        Icon(icon, label, Modifier.size(20.dp), tint = color)
        if (hovered || showHint) Popup(alignment = Alignment.CenterEnd,
            offset = IntOffset(-with(LocalDensity.current) { 48.dp.roundToPx() }, 0)) {
            Surface(shape = MaterialTheme.shapes.small, shadowElevation = 4.dp) {
                Text(label, Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = color, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
