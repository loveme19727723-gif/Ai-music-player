package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val DarkMusicColorScheme = darkColorScheme(
    primary = ElectricViolet,
    onPrimary = TextPrimary,
    primaryContainer = DeepPurple,
    onPrimaryContainer = TextPrimary,
    secondary = NeonCyan,
    onSecondary = ObsidianBlack,
    secondaryContainer = GlassElevated,
    onSecondaryContainer = SkyPulse,
    tertiary = MagentaPulse,
    onTertiary = TextPrimary,
    background = ObsidianBlack,
    onBackground = TextPrimary,
    surface = DeepSpace,
    onSurface = TextPrimary,
    surfaceVariant = GlassSurface,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLow = DeepSpace,
    surfaceContainer = GlassSurface,
    surfaceContainerHigh = GlassElevated,
    outline = GlassBorder,
    outlineVariant = GlassBorder,
    error = CrimsonError,
    onError = TextPrimary
)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun AiMusicWorldTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkMusicColorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
