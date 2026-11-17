package com.example.ui.screens.records

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize

/** ContentScale.Fit can leave empty space; only actual image overflow is pannable. */
internal fun receiptPanLimit(viewport: IntSize, image: IntSize, scale: Float): Offset {
    if (viewport.width <= 0 || viewport.height <= 0 || image.width <= 0 || image.height <= 0 || scale <= 1f) return Offset.Zero
    val fit = minOf(viewport.width.toFloat() / image.width, viewport.height.toFloat() / image.height)
    return Offset(((image.width * fit * scale - viewport.width) / 2f).coerceAtLeast(0f),
        ((image.height * fit * scale - viewport.height) / 2f).coerceAtLeast(0f))
}
