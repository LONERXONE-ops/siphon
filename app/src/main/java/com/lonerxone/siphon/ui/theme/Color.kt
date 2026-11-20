package com.lonerxone.siphon.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val DarkBackground = Color(0xFF0B0D12)
val DarkSurface = Color(0xFF141821)
val DarkElevated = Color(0xFF1B2030)
val DarkBorder = Color(0xFF282F44)

val PrimaryGradientStart = Color(0xFF7C5CFF)
val PrimaryGradientEnd = Color(0xFF22D3EE)

val AccentSuccess = Color(0xFF34D399)
val AccentWarning = Color(0xFFFBBF24)
val AccentError = Color(0xFFF87171)

val TextPrimary = Color(0xFFE8EAF2)
val TextSecondary = Color(0xFF8B93A7)
val TextMuted = Color(0xFF555D71)

// Gradient brushes for buttons, progress bars, and highlights
val SiphonPrimaryGradient = Brush.horizontalGradient(
    colors = listOf(PrimaryGradientStart, PrimaryGradientEnd)
)

val SiphonCardBorderGradient = Brush.linearGradient(
    colors = listOf(DarkBorder, Color(0xFF374151))
)
