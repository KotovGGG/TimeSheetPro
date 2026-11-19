package com.example.ui.theme

import android.os.Build
import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontVariation
import com.example.R
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val HumanistSans = if (Build.VERSION.SDK_INT >= 26) {
    FontFamily(
        Font(R.font.google_sans, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
        Font(R.font.google_sans, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
        Font(R.font.google_sans, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
        Font(R.font.google_sans, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700)))
    )
} else {
    FontFamily.SansSerif
}
val LedgerFont = if (Build.VERSION.SDK_INT >= 26) HumanistSans else FontFamily.Monospace

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
    val AmountLong = Typography.bodyLarge.copy(fontFamily = LedgerFont, fontFeatureSettings = "tnum")
    val NumberCompact = Typography.headlineSmall.copy(fontFamily = LedgerFont, fontFeatureSettings = "tnum", fontWeight = FontWeight.Bold)
    val NumberLarge = Typography.displaySmall.copy(fontFamily = LedgerFont, fontFeatureSettings = "tnum", fontWeight = FontWeight.Bold)
    val NumberMedium = Typography.titleLarge.copy(fontFamily = LedgerFont, fontFeatureSettings = "tnum")
    val NumberSmall = Typography.bodyMedium.copy(fontFamily = LedgerFont, fontFeatureSettings = "tnum")
    val UppercaseSectionHeader = Typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp)
}
