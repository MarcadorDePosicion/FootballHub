package pl.stasgroch.footballhub.ui.screens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Small local vector set: no fonts, downloads or additional dependencies. */
object HubIcons {
    val Clock = icon("Clock") { moveTo(21f,12f); curveTo(21f,24f,3f,24f,3f,12f); curveTo(3f,0f,21f,0f,21f,12f); close(); moveTo(12f,7f); lineTo(12f,12f); lineTo(16f,14f) }
    val StarFilled = ImageVector.Builder("StarFilled", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(12f,3f); lineTo(14.8f,8.8f); lineTo(21f,9.7f); lineTo(16.5f,14.1f); lineTo(17.6f,20.5f); lineTo(12f,17.5f); lineTo(6.4f,20.5f); lineTo(7.5f,14.1f); lineTo(3f,9.7f); lineTo(9.2f,8.8f); close()
        }
    }.build()
    private fun icon(name: String, draw: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = null, stroke = SolidColor(Color.White), strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = draw)
        }.build()
    val Home = icon("Home") { moveTo(3f,10f); lineTo(12f,3f); lineTo(21f,10f); moveTo(5f,9f); lineTo(5f,21f); lineTo(10f,21f); lineTo(10f,15f); lineTo(14f,15f); lineTo(14f,21f); lineTo(19f,21f); lineTo(19f,9f) }
    val Matches = icon("Matches") { moveTo(4f,4f); lineTo(20f,4f); lineTo(20f,20f); lineTo(4f,20f); close(); moveTo(12f,4f); lineTo(12f,20f); moveTo(4f,9f); lineTo(7f,9f); lineTo(7f,15f); lineTo(4f,15f); moveTo(20f,9f); lineTo(17f,9f); lineTo(17f,15f); lineTo(20f,15f) }
    val Leagues = icon("Leagues") { moveTo(8f,3f); lineTo(16f,3f); lineTo(16f,10f); curveTo(16f,16f,8f,16f,8f,10f); close(); moveTo(8f,5f); lineTo(4f,5f); lineTo(4f,9f); lineTo(8f,11f); moveTo(16f,5f); lineTo(20f,5f); lineTo(20f,9f); lineTo(16f,11f); moveTo(12f,15f); lineTo(12f,21f); moveTo(8f,21f); lineTo(16f,21f) }
    val Star = icon("Star") { moveTo(12f,3f); lineTo(14.8f,8.8f); lineTo(21f,9.7f); lineTo(16.5f,14.1f); lineTo(17.6f,20.5f); lineTo(12f,17.5f); lineTo(6.4f,20.5f); lineTo(7.5f,14.1f); lineTo(3f,9.7f); lineTo(9.2f,8.8f); close() }
    val Notes = icon("Notes") { moveTo(16f,4f); lineTo(5f,4f); lineTo(5f,21f); lineTo(19f,21f); lineTo(19f,12f); moveTo(10f,15f); lineTo(11f,11f); lineTo(19f,3f); lineTo(22f,6f); lineTo(14f,14f); close() }
    val Back = icon("Back") { moveTo(14f,5f); lineTo(7f,12f); lineTo(14f,19f) }
    val Next = icon("Next") { moveTo(9f,5f); lineTo(16f,12f); lineTo(9f,19f) }
    val Search = icon("Search") { moveTo(16f,10f); curveTo(16f,18f,4f,18f,4f,10f); curveTo(4f,2f,16f,2f,16f,10f); moveTo(15f,15f); lineTo(21f,21f) }
    val Plus = icon("Plus") { moveTo(12f,5f); lineTo(12f,19f); moveTo(5f,12f); lineTo(19f,12f) }
    val Delete = icon("Delete") { moveTo(4f,6f); lineTo(20f,6f); moveTo(9f,6f); lineTo(9f,3f); lineTo(15f,3f); lineTo(15f,6f); moveTo(6f,6f); lineTo(7f,21f); lineTo(17f,21f); lineTo(18f,6f); moveTo(10f,10f); lineTo(10f,17f); moveTo(14f,10f); lineTo(14f,17f) }
}

