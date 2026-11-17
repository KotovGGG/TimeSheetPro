package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Layout tokens; Material owns component sizing and state layers. */
object AppSpacing {
    val hairline = 1.dp
    val micro = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val page = 20.dp
    val xl = 24.dp
    val xxl = 32.dp
}

object AppSize {
    // Keep standalone explanatory paragraphs readable in wide windows.
    val messageMaxWidth = 560.dp
    val touchTarget = 48.dp
    val primaryAction = 56.dp
    val icon = 24.dp
    val smallIcon = 20.dp
    val summaryColumnsMin = 300.dp
    val dialMin = 160.dp
    val dialMax = 320.dp
    val dialStroke = 14.dp
}

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp), small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp), large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Immutable
data class StatusColors(val container: Color, val content: Color)

@Immutable
data class StatusPalette(val paid: StatusColors, val invoiced: StatusColors, val unpaid: StatusColors)

internal val LightStatusPalette = StatusPalette(
    paid = StatusColors(Color(0xFFD6F0DC), Color(0xFF17512F)),
    invoiced = StatusColors(Color(0xFFFFE6B8), Color(0xFF684600)),
    unpaid = StatusColors(Color(0xFFFFDAD6), Color(0xFF8C1D18)),
)
internal val DarkStatusPalette = StatusPalette(
    paid = StatusColors(Color(0xFF183C28), Color(0xFFA5DBB6)),
    invoiced = StatusColors(Color(0xFF493710), Color(0xFFF1CD83)),
    unpaid = StatusColors(Color(0xFF5C2423), Color(0xFFFFB4AB)),
)
val LocalStatusPalette = staticCompositionLocalOf { LightStatusPalette }
