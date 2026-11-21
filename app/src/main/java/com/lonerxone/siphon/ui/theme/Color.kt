package com.lonerxone.siphon.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val DarkBackground = Color(0xFF000000)
val DarkSurface = Color(0xFF0C0C0E)
val DarkElevated = Color(0xFF151518)
val DarkBorder = Color(0xFF232327)

// Monochrome accent: white to silver
val PrimaryGradientStart = Color(0xFFF4F4F6)
val PrimaryGradientEnd = Color(0xFFC9CAD1)

// Text and icons placed on top of the accent color
val OnAccent = Color(0xFF0A0A0B)

val AccentSuccess = Color(0xFF5CC48A)
val AccentWarning = Color(0xFFE3B65B)
val AccentError = Color(0xFFE5726E)

val TextPrimary = Color(0xFFF5F5F7)
val TextSecondary = Color(0xFF9A9AA3)
val TextMuted = Color(0xFF5C5C64)

val SiphonPrimaryGradient = Brush.horizontalGradient(
    colors = listOf(PrimaryGradientStart, PrimaryGradientEnd)
)

val SiphonCardBorderGradient = Brush.linearGradient(
    colors = listOf(DarkBorder, Color(0xFF34343A))
)