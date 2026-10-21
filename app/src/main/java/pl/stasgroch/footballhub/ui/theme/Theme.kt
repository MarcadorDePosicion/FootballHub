package pl.stasgroch.footballhub.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val FootballColors = darkColorScheme(
    primary = HubColors.Lime, onPrimary = Color(0xFF193008),
    primaryContainer = HubColors.GreenContainer, onPrimaryContainer = HubColors.Lime,
    secondary = HubColors.Teal, onSecondary = Color(0xFF002F2C),
    secondaryContainer = HubColors.TealContainer, onSecondaryContainer = HubColors.Teal,
    background = HubColors.Background, onBackground = HubColors.Text,
    surface = HubColors.Surface, onSurface = HubColors.Text,
    surfaceVariant = HubColors.Raised, onSurfaceVariant = HubColors.Muted,
    surfaceContainerLowest = HubColors.Background, surfaceContainerLow = HubColors.Surface,
    surfaceContainer = HubColors.Surface, surfaceContainerHigh = HubColors.Raised,
    surfaceContainerHighest = HubColors.Raised,
    outline = HubColors.Outline, outlineVariant = HubColors.Raised,
    error = HubColors.Danger
)
@Composable
fun FootballHubTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FootballColors,
        typography = Typography,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(HubSpace.xs),
            small = RoundedCornerShape(HubSpace.sm),
            medium = RoundedCornerShape(HubSpace.md),
            large = RoundedCornerShape(HubSpace.lg),
            extraLarge = RoundedCornerShape(HubSpace.xl)
        ),
        content = content
    )
}

