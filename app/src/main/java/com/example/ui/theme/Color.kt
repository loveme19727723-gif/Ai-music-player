package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val ObsidianBlack = Color(0xFF07070D)
val DeepSpace = Color(0xFF0D0E18)
val GlassSurface = Color(0xFF141626)
val GlassElevated = Color(0xFF1C1F35)
val GlassBorder = Color(0xFF2B2F4A)

val ElectricViolet = Color(0xFF8B5CF6)
val DeepPurple = Color(0xFF6D28D9)
val NeonCyan = Color(0xFF06B6D4)
val SkyPulse = Color(0xFF38BDF8)
val MagentaPulse = Color(0xFFEC4899)
val EmeraldLive = Color(0xFF10B981)
val AmberGold = Color(0xFFF59E0B)
val CrimsonError = Color(0xFFEF4444)

val TextPrimary = Color(0xFFF8FAFC)
val TextSecondary = Color(0xFF94A3B8)
val TextMuted = Color(0xFF64748B)

object MusicGradients {
    val primaryBrand = Brush.linearGradient(
        colors = listOf(ElectricViolet, NeonCyan)
    )
    val neonHero = Brush.linearGradient(
        colors = listOf(DeepPurple, ElectricViolet, NeonCyan)
    )
    val djPulse = Brush.linearGradient(
        colors = listOf(MagentaPulse, ElectricViolet, NeonCyan)
    )
    val classicGold = Brush.linearGradient(
        colors = listOf(AmberGold, DeepPurple)
    )
    val romanticAurora = Brush.linearGradient(
        colors = listOf(MagentaPulse, DeepPurple, SkyPulse)
    )
    val darkBackdrop = Brush.verticalGradient(
        colors = listOf(DeepSpace, ObsidianBlack)
    )
}
