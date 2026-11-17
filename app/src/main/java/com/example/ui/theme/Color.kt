package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Custom 5-Color Design Palette
val CustomBackground = Color(0xFF17171F)
val CustomSurface = Color(0xFF2C2B33)
val CustomOutline = Color(0xFF525065)
val CustomAccentPink = Color(0xFFC59FC8)
val CustomAccentLavender = Color(0xFFC4C3EF)

// Light Theme Palette (mapped beautifully from Dark Palette based on user specification)
val LightBackground = Color(0xFFF7F6FB)
val LightSurface = Color(0xFFEEEDF6)
val LightOutline = Color(0xFFD8D5E8)
val LightSecondary = Color(0xFF9D72A5)
val LightPrimary = Color(0xFF6F6BB8)

val PaperBackground = LightBackground
val InkText = Color(0xFF181820) // Deep dark charcoal text using dark background color
val BrassAccent = LightPrimary
val InkGreenAccent = LightSecondary
val WarningRedAccent = Color(0xFFB3261E) // Standard light theme error red
val DividerLine = LightOutline

val LightSurfaceVariant = Color(0xFFE5E2F0) // Slightly darker tint for text fields and variant backgrounds
val LightPrimaryContainer = Color(0xFFE8E5FF)
val LightOnPrimaryContainer = Color(0xFF1B124C)

// Dark Theme Palette (Elegant Dark / Material 3 Dark theme)
val DarkBackground = CustomBackground
val DarkOnBackground = Color(0xFFFFFFFF)
val DarkPrimary = CustomAccentLavender
val DarkOnPrimary = CustomBackground
val DarkSecondary = CustomAccentPink
val DarkOnSecondary = CustomBackground
val DarkSurface = CustomSurface
val DarkOnSurface = Color(0xFFFFFFFF)
val DarkSurfaceVariant = CustomSurface
val DarkOnSurfaceVariant = Color(0xFFE2E1E6)
val DarkOutline = CustomOutline
val DarkBottomNavBackground = CustomBackground
val DarkWarningAccent = Color(0xFFF2B8B5)


