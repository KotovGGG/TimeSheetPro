package com.example

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.ui.screens.records.ReceiptViewer
import com.example.ui.screens.records.decodeReceiptBitmap
import com.example.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

/** Uses synthetic cache files only; does not write records, reports or attachments. */
@RunWith(AndroidJUnit4::class)
class ReceiptImageAuditTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun nonCameraFormatsAndInvalidFile() {
        check(BuildConfig.DEBUG && context.packageName == "com.example.preview")
        val bitmap = Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        try {
            for (format in listOf(Bitmap.CompressFormat.PNG, Bitmap.CompressFormat.WEBP, Bitmap.CompressFormat.JPEG)) {
                val file = File(context.cacheDir, "audit-image-${System.nanoTime()}")
                try {
                    file.outputStream().use { check(bitmap.compress(format, 100, it)) }
                    val digest = hash(file)
                    val decoded = checkNotNull(decodeReceiptBitmap(context.contentResolver, Uri.fromFile(file), 64))
                    try {
                        check(decoded.width == 60 && decoded.height == 40)
                        check(Color.blue(decoded.getPixel(30, 20)) >= 235)
                    } finally { decoded.recycle() }
                    check(hash(file) == digest)
                } finally { check(file.delete() || !file.exists()) }
            }
            val invalid = File(context.cacheDir, "audit-invalid-${System.nanoTime()}")
            try {
                invalid.writeText("This is not an image")
                val digest = hash(invalid)
                check(decodeReceiptBitmap(context.contentResolver, Uri.fromFile(invalid), 64) == null)
                check(hash(invalid) == digest)
            } finally { check(invalid.delete() || !invalid.exists()) }
        } finally { bitmap.recycle() }
    }

    @Test fun jpegOrientationAndBounds() {
        check(BuildConfig.DEBUG && context.packageName == "com.example.preview")
        val corners = listOf(
            listOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW),
            listOf(Color.GREEN, Color.RED, Color.YELLOW, Color.BLUE),
            listOf(Color.YELLOW, Color.BLUE, Color.GREEN, Color.RED),
            listOf(Color.BLUE, Color.YELLOW, Color.RED, Color.GREEN),
            listOf(Color.RED, Color.BLUE, Color.GREEN, Color.YELLOW),
            listOf(Color.BLUE, Color.RED, Color.YELLOW, Color.GREEN),
            listOf(Color.YELLOW, Color.GREEN, Color.BLUE, Color.RED),
            listOf(Color.GREEN, Color.YELLOW, Color.RED, Color.BLUE),
        )
        val bitmap = Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            val paint = Paint()
            listOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW).forEachIndexed { index, color ->
                paint.color = color
                val x = if (index % 2 == 0) 0f else 60f
                val y = if (index < 2) 0f else 40f
                drawRect(x, y, x + 60f, y + 40f, paint)
            }
        }
        try {
            for (orientation in 1..8) {
                val file = File(context.cacheDir, "audit-exif-${System.nanoTime()}-$orientation.jpg")
                try {
                    file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)) }
                    ExifInterface(file.absolutePath).apply {
                        setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString()); saveAttributes()
                    }
                    val digest = hash(file)
                    val decoded = checkNotNull(decodeReceiptBitmap(context.contentResolver, Uri.fromFile(file), 64))
                    try {
                        val expectedWidth = if (orientation >= 5) 40 else 60
                        val expectedHeight = if (orientation >= 5) 60 else 40
                        check(decoded.width == expectedWidth && decoded.height == expectedHeight) {
                            "EXIF $orientation: ${decoded.width}x${decoded.height}, expected ${expectedWidth}x$expectedHeight"
                        }
                        for (index in 0..3) {
                            val x = decoded.width * (if (index % 2 == 0) 1 else 3) / 4
                            val y = decoded.height * (if (index < 2) 1 else 3) / 4
                            val actual = decoded.getPixel(x, y)
                            val expected = corners[orientation - 1][index]
                            check(listOf(Color.red(actual) - Color.red(expected), Color.green(actual) - Color.green(expected), Color.blue(actual) - Color.blue(expected)).all { kotlin.math.abs(it) <= 20 }) {
                                "EXIF $orientation corner $index: $actual / expected $expected"
                            }
                        }
                    } finally { decoded.recycle() }
                    check(hash(file) == digest) { "Decoder changed the original JPEG" }
                } finally { check(file.delete() || !file.exists()) }
            }
        } finally { bitmap.recycle() }
    }

    @Test fun rotatedJpegViewer() {
        check(BuildConfig.DEBUG && context.packageName == "com.example.preview")
        compose.waitUntil(10000) { compose.onAllNodesWithTag("nav_item_timer").fetchSemanticsNodes().isNotEmpty() }
        val file = File(context.cacheDir, "audit-camera-${System.nanoTime()}.jpg")
        val portrait = Bitmap.createBitmap(600, 900, Bitmap.Config.ARGB_8888)
        Canvas(portrait).apply {
            drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 32f }
            drawText("TimeSheetPro / camera receipt", 36f, 90f, paint)
            drawText("Amount: 100.00 RUB", 36f, 160f, paint)
            drawText("EXIF orientation: rotate 90", 36f, 230f, paint)
            paint.color = Color.rgb(98, 93, 160)
            drawRect(36f, 300f, 564f, 360f, paint)
        }
        val raw = Bitmap.createBitmap(portrait, 0, 0, portrait.width, portrait.height, Matrix().apply { postRotate(270f) }, true)
        portrait.recycle()
        try {
            file.outputStream().use { check(raw.compress(Bitmap.CompressFormat.JPEG, 98, it)) }
            ExifInterface(file.absolutePath).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString()); saveAttributes()
            }
            val digest = hash(file)
            for (dark in listOf(false, true)) {
                compose.activity.setContent { MyApplicationTheme(darkTheme = dark) { ReceiptViewer(Uri.fromFile(file).toString(), {}) } }
                compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("Чек целиком").fetchSemanticsNodes().isNotEmpty() }
                capture(if (dark) "jpeg-dark-viewer" else "jpeg-light-viewer")
            }
            val decoded = checkNotNull(decodeReceiptBitmap(context.contentResolver, Uri.fromFile(file), 2048))
            try { check(decoded.width == 600 && decoded.height == 900) { "Rotated receipt remained sideways: ${decoded.width}x${decoded.height}" } }
            finally { decoded.recycle() }
            check(hash(file) == digest)
        } finally { raw.recycle(); check(file.delete() || !file.exists()) }
    }
    private fun hash(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
    private fun capture(name: String) {
        compose.waitForIdle(); instrumentation.waitForIdleSync()
        val committed = java.util.concurrent.CountDownLatch(1)
        instrumentation.runOnMainSync {
            val view = checkNotNull(android.view.inspector.WindowInspector.getGlobalWindowViews().lastOrNull { it.hasWindowFocus() && it.isShown && it.isHardwareAccelerated })
            view.viewTreeObserver.registerFrameCommitCallback { committed.countDown() }; view.invalidate()
        }
        check(committed.await(5, java.util.concurrent.TimeUnit.SECONDS))
        instrumentation.uiAutomation.waitForIdle(500, 5000)
        val directory = checkNotNull(context.getExternalFilesDir("visual-audit")).apply { mkdirs() }
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(directory, "$name.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }
}
