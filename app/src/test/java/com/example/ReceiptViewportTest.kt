package com.example

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.example.ui.screens.records.receiptPanLimit
import org.junit.Assert.assertEquals
import org.junit.Test

class ReceiptViewportTest {
    @Test fun fittedImageCannotPanAtOriginalScale() {
        assertEquals(Offset.Zero, receiptPanLimit(IntSize(900, 200), IntSize(600, 900), 1f))
    }

    @Test fun portraitImageStaysCenteredHorizontallyInWideViewport() {
        val limit = receiptPanLimit(IntSize(900, 200), IntSize(600, 900), 5f)
        assertEquals(0f, limit.x, 0.01f)
        assertEquals(400f, limit.y, 0.01f)
    }

    @Test fun portraitLetterboxingUsesImageHeightRatherThanViewportHeight() {
        val limit = receiptPanLimit(IntSize(300, 600), IntSize(600, 900), 2f)
        assertEquals(150f, limit.x, 0.01f)
        assertEquals(150f, limit.y, 0.01f)
    }

    @Test fun unmeasuredViewportAndUndecodedImageCannotPan() {
        assertEquals(Offset.Zero, receiptPanLimit(IntSize.Zero, IntSize(600, 900), 5f))
        assertEquals(Offset.Zero, receiptPanLimit(IntSize(900, 200), IntSize.Zero, 5f))
    }
}
