package com.lonerxone.siphon.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val DarkBackground = Color(0xFF000000)
val DarkSurface = Color(0xFF0D0D0F)
val DarkElevated = Color(0xFF161619)
val DarkBorder = Color(0xFF242428)

val PrimaryGradientStart = Color(0xFF4C8DFF)
val PrimaryGradientEnd = Color(0xFF2F6BFF)

val AccentSuccess = Color(0xFF3DD68C)
val AccentWarning = Color(0xFFF5B94A)
val AccentError = Color(0xFFF2706B)

val TextPrimary = Color(0xFFF5F5F7)
val TextSecondary = Color(0xFF9A9AA3)
val TextMuted = Color(0xFF5C5C64)

// Gradient brushes for buttons, progress bars, and highlights
val SiphonPrimaryGradient = Brush.horizontalGradient(
    colors = listOf(PrimaryGradientStart, PrimaryGradientEnd)
)

val SiphonCardBorderGradient = Brush.linearGradient(
    colors = listOf(DarkBorder, Color(0xFF34343A))
)