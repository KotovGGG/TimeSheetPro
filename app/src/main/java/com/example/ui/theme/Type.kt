package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val HumanistSans = FontFamily.SansSerif
val InstrumentMonospace = FontFamily.Monospace

// Complete Material type scale; numeric roles below preserve ledger alignment.
val Typography = Typography(
    displayLarge = TextStyle(fontFamily = HumanistSans, fontWeight = FontWeight.Normal, fontSize = 57.sp, lineHeight = 64.sp),
    displayMedium = TextStyle(fontFamily = HumanistSans, fontWeight = FontWeight.Normal, fontSize = 45.sp, lineHeight = 52.sp),
    displaySmall = TextStyle(fontFamily = HumanistSans, fontWeight = FontWeight.Normal, fontSize = 36.sp, lineHeight = 44.sp),
    headlineLarge = TextStyle(fontFamily = HumanistSans, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = HumanistSans, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontFamily = HumanistSans, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = HumanistSans, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = HumanistSans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontFamily = HumanistSans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = HumanistSans, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = HumanistSans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = HumanistSans, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = HumanistSans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = HumanistSans, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = HumanistSans, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp),
 )

object LedgerStyles {
    val MonospaceAmountLong = Typography.bodyLarge.copy(fontFamily = InstrumentMonospace)
    val MonospaceNumberCompact = Typography.headlineSmall.copy(fontFamily = InstrumentMonospace, fontWeight = FontWeight.Bold)
    val MonospaceNumberLarge = Typography.displaySmall.copy(fontFamily = InstrumentMonospace, fontWeight = FontWeight.Bold)
    val MonospaceNumberMedium = Typography.titleLarge.copy(fontFamily = InstrumentMonospace)
    val MonospaceNumberSmall = Typography.bodyMedium.copy(fontFamily = InstrumentMonospace)
    val UppercaseSectionHeader = Typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp)
}
