package com.example

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.example.ui.theme.*
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignContrastTest {
    private fun contrast(foreground: Color, background: Color): Double {
        val a = foreground.luminance().toDouble()
        val b = background.luminance().toDouble()
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }

    @Test fun textRolesMeetNormalTextContrast() {
        for (scheme in listOf(LightColorScheme, DarkColorScheme)) {
            val pairs = listOf(
                scheme.onPrimary to scheme.primary, scheme.onPrimaryContainer to scheme.primaryContainer,
                scheme.onSecondary to scheme.secondary, scheme.onSecondaryContainer to scheme.secondaryContainer,
                scheme.onTertiary to scheme.tertiary, scheme.onTertiaryContainer to scheme.tertiaryContainer,
                scheme.onError to scheme.error, scheme.onErrorContainer to scheme.errorContainer,
                scheme.onSurface to scheme.surface, scheme.onSurfaceVariant to scheme.surfaceContainerLow,
                scheme.onSurfaceVariant to scheme.surfaceContainerHighest,
            )
            pairs.forEach { (text, surface) ->
                assertTrue("Text contrast: ${contrast(text, surface)}", contrast(text, surface) >= 4.5)
            }
        }
    }

    @Test fun statusTextMeetsContrastOnContainersAndListRows() {
        for ((palette, scheme) in listOf(LightStatusPalette to LightColorScheme, DarkStatusPalette to DarkColorScheme)) {
            for (status in listOf(palette.paid, palette.invoiced, palette.unpaid)) {
                assertTrue(contrast(status.content, status.container) >= 4.5)
                assertTrue(contrast(status.content, scheme.surfaceContainerLow) >= 4.5)
            }
        }
    }

    @Test fun outlinesMeetNonTextContrastOnFormSurfaces() {
        for (scheme in listOf(LightColorScheme, DarkColorScheme)) {
            assertTrue(contrast(scheme.outline, scheme.surface) >= 3.0)
            assertTrue(contrast(scheme.outline, scheme.surfaceContainerLow) >= 3.0)
        }
    }
}
