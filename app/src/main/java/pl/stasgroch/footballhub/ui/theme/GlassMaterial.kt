package pl.stasgroch.footballhub.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** Soft atmospheric header, rendered directly without a costly blur layer. */
fun Modifier.hubHeaderGradient(): Modifier = background(
    Brush.verticalGradient(listOf(Color(0xFF263C45), Color(0xFF182C32), Color(0xFF0B1117)))
)

/** Lightweight glass finish: no offscreen backdrop capture or blur passes. */
fun Modifier.hubGlass(shape: Shape = RoundedCornerShape(20.dp)): Modifier =
    shadow(6.dp, shape).clip(shape)
        .background(Brush.verticalGradient(listOf(Color(0xEE2C414B), Color(0xED14212A))))
        .border(0.7.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = .21f), Color(0xFF55DED2).copy(alpha = .04f))), shape)
