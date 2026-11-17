package com.example.ui.screens.records

import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.content.ContentResolver
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.ui.theme.AppSize
import com.example.ui.theme.AppSpacing

@Composable
internal fun BoxScope.ReceiptExpandHint() {
    // Decorative hint; the image beneath owns the click target and its accessible label.
    Box(Modifier.align(Alignment.BottomEnd).padding(AppSpacing.sm).size(AppSize.touchTarget)
        .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape), contentAlignment = Alignment.Center) {
        Icon(Icons.Default.OpenInFull, contentDescription = null, modifier = Modifier.size(AppSize.icon))
    }
}

internal sealed interface ReceiptPreview {
    data object Empty : ReceiptPreview
    data object Loading : ReceiptPreview
    data object Unavailable : ReceiptPreview
    data class Ready(val bitmap: ImageBitmap) : ReceiptPreview
}

/** Caller runs on IO; bounds are read before allocating the preview bitmap. */
internal fun decodeReceiptBitmap(resolver: ContentResolver, uri: Uri, maxDimension: Int): Bitmap? {
    require(maxDimension > 0)
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    if (options.outWidth <= 0 || options.outHeight <= 0) return null
    // Camera JPEGs often store orientation separately from their pixel data. Read only;
    // the attachment itself must retain its bytes and persistent URI.
    val orientation = if (options.outMimeType == "image/jpeg") {
        resolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
    } else ExifInterface.ORIENTATION_NORMAL
    options.inSampleSize = 1
    while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > maxDimension) {
        options.inSampleSize *= 2
    }
    options.inJustDecodeBounds = false
    val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
    val transform = Matrix().apply {
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(270f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(270f)
        }
    }
    if (transform.isIdentity) return decoded
    try {
        return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, transform, true)
    } finally {
        decoded.recycle()
    }
}

/** Decode only the preview resolution, off main; the original payment attachment is untouched. */
@Composable
internal fun receiptPreview(uriString: String?, maxDimension: Int = 1024): ReceiptPreview {
    require(maxDimension > 0)
    val resolver = LocalContext.current.contentResolver
    val preview by produceState<ReceiptPreview>(ReceiptPreview.Empty, uriString, maxDimension) {
        value = if (uriString == null) ReceiptPreview.Empty else ReceiptPreview.Loading
        if (uriString == null) return@produceState
        value = withContext(Dispatchers.IO) {
            try {
                val uri = Uri.parse(uriString)
                decodeReceiptBitmap(resolver, uri, maxDimension)?.asImageBitmap()
                    ?.let { ReceiptPreview.Ready(it) } ?: ReceiptPreview.Unavailable
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                android.util.Log.w("TimeSheetPro", "Payment preview unavailable", error)
                ReceiptPreview.Unavailable
            }
        }
    }
    return preview
}
