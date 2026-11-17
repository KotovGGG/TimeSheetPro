package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

internal val LocalAppDarkTheme = staticCompositionLocalOf { false }

internal val LightColorScheme = lightColorScheme(
    primary = Color(0xFF625DA0),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE6DFFF),
    onPrimaryContainer = Color(0xFF21194F),
    inversePrimary = Color(0xFFC4C3EF),
    secondary = Color(0xFF5E5D72),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE4E1F5),
    onSecondaryContainer = Color(0xFF1B1B2C),
    tertiary = Color(0xFF5A608A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDFE1FF),
    onTertiaryContainer = Color(0xFF171C43),
    background = Color(0xFFF7F6FB),
    onBackground = Color(0xFF1C1B22),
    surface = Color(0xFFF7F6FB),
    onSurface = Color(0xFF1C1B22),
    surfaceDim = Color(0xFFDCD9E1),
    surfaceBright = Color(0xFFFBF8FF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2EFF7),
    surfaceContainer = Color(0xFFECE9F1),
    surfaceContainerHigh = Color(0xFFE6E3EC),
    surfaceContainerHighest = Color(0xFFE0DDE6),
    surfaceVariant = Color(0xFFE5E1ED),
    onSurfaceVariant = Color(0xFF494550),
    surfaceTint = Color(0xFF625DA0),
    outline = Color(0xFF797580),
    outlineVariant = Color(0xFFCBC5D2),
    inverseSurface = Color(0xFF312E36),
    inverseOnSurface = Color(0xFFF4EFF8),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    scrim = Color(0xFF000000),
)

internal val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFC4C3EF),
    onPrimary = Color(0xFF2D285D),
    primaryContainer = Color(0xFF454076),
    onPrimaryContainer = Color(0xFFE6DFFF),
    inversePrimary = Color(0xFF625DA0),
    secondary = Color(0xFFC7C3DB),
    onSecondary = Color(0xFF302F42),
    secondaryContainer = Color(0xFF464456),
    onSecondaryContainer = Color(0xFFE4E1F5),
    tertiary = Color(0xFFBFC4F3),
    onTertiary = Color(0xFF292F59),
    tertiaryContainer = Color(0xFF404670),
    onTertiaryContainer = Color(0xFFDFE1FF),
    background = Color(0xFF17171F),
    onBackground = Color(0xFFE7E0EC),
    surface = Color(0xFF17171F),
    onSurface = Color(0xFFE7E0EC),
    surfaceDim = Color(0xFF17171F),
    surfaceBright = Color(0xFF3D3A44),
    surfaceContainerLowest = Color(0xFF111118),
    surfaceContainerLow = Color(0xFF1F1E27),
    surfaceContainer = Color(0xFF25242D),
    surfaceContainerHigh = Color(0xFF302E38),
    surfaceContainerHighest = Color(0xFF3B3943),
    surfaceVariant = Color(0xFF494550),
    onSurfaceVariant = Color(0xFFCBC5D2),
    surfaceTint = Color(0xFFC4C3EF),
    outline = Color(0xFF958E9D),
    outlineVariant = Color(0xFF494550),
    inverseSurface = Color(0xFFE7E0EC),
    inverseOnSurface = Color(0xFF312E36),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    scrim = Color(0xFF000000),
)

@Composable
fun MyApplicationTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAppDarkTheme provides darkTheme,
        LocalStatusPalette provides if (darkTheme) DarkStatusPalette else LightStatusPalette) {
        MaterialExpressiveTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = Typography,
            shapes = AppShapes,
            motionScheme = MotionScheme.expressive(),
            content = content,
        )
    }
}
