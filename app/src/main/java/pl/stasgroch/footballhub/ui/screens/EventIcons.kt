package pl.stasgroch.footballhub.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import pl.stasgroch.footballhub.data.model.*

@Composable fun FootballSymbol(ownGoal: Boolean = false) {
    val color = if (ownGoal) Color(0xFFFF7979) else MaterialTheme.colorScheme.primary
    Canvas(Modifier.size(18.dp).semantics { contentDescription = if (ownGoal) "Gol samobójczy" else "Gol" }) {
        val c = center; val radius = size.minDimension * .45f
        drawCircle(color, radius, style = Stroke(1.5.dp.toPx()))
        val vertices = (0..4).map { i ->
            val angle = Math.toRadians(-90.0 + i * 72)
            Offset(c.x + radius * .43f * kotlin.math.cos(angle).toFloat(), c.y + radius * .43f * kotlin.math.sin(angle).toFloat())
        }
        val path = Path().apply { moveTo(vertices[0].x, vertices[0].y); vertices.drop(1).forEach { lineTo(it.x, it.y) }; close() }
        drawPath(path, color)
        vertices.forEach { v -> drawLine(color, v, c + (v - c) * 2.2f, 1.dp.toPx()) }
    }
}

@Composable fun UnknownEventSymbol() {
    Text("?", modifier = Modifier.size(20.dp).semantics { contentDescription = "Nieznane zdarzenie" },
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable fun CardSymbol(kind: CardKind?) {
    if (kind == null) { UnknownEventSymbol(); return }
    Canvas(Modifier.size(20.dp).semantics { contentDescription = kind.label }) {
        val yellow = Color(0xFFFFD65A); val red = Color(0xFFFF7979)
        if (kind == CardKind.SECOND_YELLOW) {
            drawRoundRect(yellow, Offset(size.width * .12f, size.height * .28f), Size(size.width * .5f, size.height * .66f))
            drawRoundRect(red, Offset(size.width * .4f, 0f), Size(size.width * .5f, size.height * .66f))
        } else drawRoundRect(if (kind == CardKind.RED) red else yellow, Offset(size.width * .25f, 0f), Size(size.width * .5f, size.height * .9f))
    }
}

@Composable fun AssistSymbol() {
    val color = MaterialTheme.colorScheme.secondary
    Canvas(Modifier.size(20.dp).semantics { contentDescription = "Asysta" }) {
        val w = size.width; val h = size.height
        drawPath(Path().apply {
            moveTo(w*.1f,h*.22f); lineTo(w*.32f,h*.32f); lineTo(w*.47f,h*.24f)
            lineTo(w*.62f,h*.47f); lineTo(w*.87f,h*.55f)
            quadraticTo(w*.98f,h*.59f,w*.92f,h*.75f)
            lineTo(w*.1f,h*.75f); close()
        }, color, style = Stroke(1.5.dp.toPx()))
        drawLine(color, Offset(w*.12f,h*.62f), Offset(w*.9f,h*.62f), 1.dp.toPx())
        listOf(.25f,.5f,.77f).forEach { x -> drawLine(color, Offset(w*x,h*.76f), Offset(w*x,h*.9f), 2.dp.toPx()) }
        listOf(.45f,.54f).forEach { x -> drawLine(color, Offset(w*x,h*.37f), Offset(w*(x-.1f),h*.47f), 1.dp.toPx()) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun EventLegend() {
    Surface(shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Oznaczenia", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LegendItem("Gol") { FootballSymbol() }
                LegendItem("Asysta") { AssistSymbol() }
                LegendItem("Samobójczy") { FootballSymbol(true) }
                LegendItem("Żółta kartka") { CardSymbol(CardKind.YELLOW) }
                LegendItem("Druga żółta / czerwona") { CardSymbol(CardKind.SECOND_YELLOW) }
                LegendItem("Czerwona kartka") { CardSymbol(CardKind.RED) }
                LegendItem("Wejście") { Text("↑", color = MaterialTheme.colorScheme.primary) }
                LegendItem("Zejście") { Text("↓", color = Color(0xFFFF7979)) }
            }
        }
    }
}

@Composable private fun LegendItem(label: String, symbol: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        symbol()
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable fun BenchSymbol() {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(Modifier.size(20.dp).semantics { contentDescription = "Ławka rezerwowych — bez wejścia na boisko" }) {
        val w = size.width; val h = size.height
        drawRect(color, Offset(w*.1f,h*.2f), Size(w*.8f,h*.2f))
        drawLine(color, Offset(w*.08f,h*.6f), Offset(w*.92f,h*.6f), 2.dp.toPx())
        listOf(.2f,.8f).forEach { drawLine(color, Offset(w*it,h*.4f),Offset(w*it,h*.9f),2.dp.toPx()) }
    }
}

@Composable fun PlayerAchievements(playerId: Int, events: List<MatchEvent>) {
    val own = events.filter { it.involves(playerId) }
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(false, true).forEach { ownGoal ->
            val count = own.count { it.kind == EventKind.GOAL && it.playerId == playerId && (it.goalKind == GoalKind.OWN_GOAL) == ownGoal }
            if (count > 0) { FootballSymbol(ownGoal); if (count > 1) Text(count.toString(), style = MaterialTheme.typography.labelSmall) }
        }
        val assists = own.count { it.assistPlayerId == playerId }
        if (assists > 0) { AssistSymbol(); if (assists > 1) Text(assists.toString(), style = MaterialTheme.typography.labelSmall) }
        own.lastOrNull { it.kind == EventKind.CARD && it.playerId == playerId }?.let { CardSymbol(it.cardKind) }
    }
}
