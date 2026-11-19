package com.example.utils.export

import com.example.appLocale
import com.example.R
import com.example.appText
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.ui.graphics.toArgb
import com.example.data.local.entity.Client
import com.example.data.local.entity.TimeEntry
import com.example.data.local.entity.currencyTotals
import com.example.ui.theme.LightColorScheme
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.floor

/** Print uses the light palette, independently of the screen's selected theme. */
object PdfGenerator {
    fun generatePdf(entries: List<TimeEntry>, clients: List<Client>, selectedClientNames: String,
                    periodLabel: String, currencySymbol: String): ByteArray {
        val document = PdfDocument()
        val colors = LightColorScheme
        val ink = colors.onSurface.toArgb()
        val secondaryInk = colors.onSurfaceVariant.toArgb()
        val accent = colors.primary.toArgb()
        val outline = colors.outlineVariant.toArgb()
        val sans = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        val bold = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        val mono = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
        val locale = appLocale()
        val dateFormat = SimpleDateFormat("dd.MM.yy HH:mm", locale)
        val issuedAt = SimpleDateFormat("yyyy-MM-dd HH:mm", locale).format(Date())
        val clientMap = clients.associateBy { it.id }
        val margin = 40f
        val right = 555f
        val bottom = 794f
        val lineHeight = 14f
        val widths = listOf(82f, 110f, 165f, 60f, 98f)
        val headers = listOf(appText(R.string.copy_date), appText(R.string.copy_client_2), appText(R.string.copy_comment), appText(R.string.copy_time), appText(R.string.copy_amount))
        var pageNumber = 0
        var page: PdfDocument.Page? = null
        var canvas: Canvas? = null
        var y = 0f
        fun font(size: Float, typeface: Typeface = sans, color: Int = ink) {
            paint.textSize = size; paint.typeface = typeface; paint.color = color
            paint.style = Paint.Style.FILL; paint.textAlign = Paint.Align.LEFT; paint.letterSpacing = 0f
        }
        fun lines(text: String, width: Float): List<String> {
            if (text.isEmpty()) return listOf("")
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width.toInt().coerceAtLeast(1))
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).build()
            return (0 until layout.lineCount).map { index ->
                text.substring(layout.getLineStart(index), layout.getLineEnd(index)).trimEnd('\r', '\n')
            }
        }
        fun startPage(table: Boolean) {
            page?.let(document::finishPage)
            page = null
            pageNumber++
            page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNumber).create())
            val target = checkNotNull(page).canvas
            canvas = target
            target.drawColor(colors.surfaceContainerLowest.toArgb())
            font(10f, bold, accent); target.drawText("TimeSheetPro", margin, 42f, paint)
            font(9f, sans, secondaryInk)
            val pageLabel = appText(R.string.copy_page_1, pageNumber)
            target.drawText(pageLabel, right - paint.measureText(pageLabel), 42f, paint)
            font(26f, bold); target.drawText(appText(R.string.copy_invoice), margin, 80f, paint)
            paint.color = accent; paint.strokeWidth = 2f
            target.drawLine(margin, 94f, right, 94f, paint)
            y = 113f
            font(9f, sans, secondaryInk)
            // This summary is bounded; full client names are printed in the table.
            listOf(appText(R.string.copy_issued_1, issuedAt), appText(R.string.copy_clients_1, selectedClientNames), appText(R.string.copy_period_1, periodLabel)).forEach { value ->
                val wrapped = lines(value, right - margin)
                wrapped.take(3).forEachIndexed { index, line ->
                    var visible = line
                    if (index == 2 && wrapped.size > 3) {
                        while (visible.isNotEmpty() && paint.measureText("$visible…") > right - margin) visible = visible.dropLast(1)
                        visible += "…"
                    }
                    target.drawText(visible, margin, y, paint); y += lineHeight
                }
            }
            y += 14f
            if (table) {
                paint.color = colors.surfaceContainerLow.toArgb()
                target.drawRect(margin, y, right, y + 28f, paint)
                font(10f, bold)
                var x = margin
                headers.forEachIndexed { index, label ->
                    val textX = if (index >= 3) x + widths[index] - 6f - paint.measureText(label) else x + 6f
                    target.drawText(label, textX, y + 18f, paint); x += widths[index]
                }
                y += 28f
            }
        }
        try {
            startPage(table = true)
            entries.forEachIndexed { rowIndex, entry ->
                // Stored monetary values and the existing duration/formatting rules stay intact.
                val cells = listOf(dateFormat.format(Date(entry.startTime)),
                    clientMap[entry.clientId]?.name ?: appText(R.string.copy_client_1_2, entry.clientId), entry.comment ?: "—",
                    String.format(locale, appText(R.string.copy_dh_02dm), entry.durationSeconds / 3600, (entry.durationSeconds % 3600) / 60),
                    String.format(locale, "%.2f %s", entry.amount, entry.currencyCode))
                val wrapped = cells.mapIndexed { index, value ->
                    font(9.5f, if (index >= 3) mono else sans); lines(value, widths[index] - 12f)
                }
                val count = wrapped.maxOf { it.size }
                var firstLine = 0
                // A long comment continues across pages without dropping any text.
                while (firstLine < count) {
                    val available = floor((bottom - y - 12f) / lineHeight).toInt()
                    if (available <= 0) { startPage(table = true); continue }
                    val take = minOf(count - firstLine, available)
                    val height = maxOf(26f, take * lineHeight + 12f)
                    val target = checkNotNull(canvas)
                    if (rowIndex % 2 == 0) {
                        paint.color = colors.surfaceContainerLow.toArgb()
                        target.drawRect(margin, y, right, y + height, paint)
                    }
                    var x = margin
                    wrapped.forEachIndexed { index, cellLines ->
                        font(9.5f, if (index >= 3) mono else sans)
                        repeat(take) { lineIndex ->
                            cellLines.getOrNull(firstLine + lineIndex)?.let { line ->
                                val textX = if (index >= 3) x + widths[index] - 6f - paint.measureText(line) else x + 6f
                                target.drawText(line, textX, y + 16f + lineIndex * lineHeight, paint)
                            }
                        }
                        x += widths[index]
                    }
                    paint.color = outline; paint.strokeWidth = 0.5f
                    target.drawLine(margin, y + height, right, y + height, paint)
                    y += height; firstLine += take
                    if (firstLine < count) startPage(table = true)
                }
            }
            if (y + 130f > bottom) startPage(table = false)
            y += 24f
            paint.color = accent; paint.strokeWidth = 2f
            checkNotNull(canvas).drawLine(margin, y, right, y, paint)
            y += 25f
            font(12f, bold); checkNotNull(canvas).drawText(appText(R.string.copy_total_due), margin, y, paint)
            y += 30f
            val totals = entries.currencyTotals().ifEmpty { mapOf(currencySymbol to 0.0) }
            for ((code, amount) in totals) {
                val total = String.format(locale, "%,.2f %s", amount, code)
                font(20f, mono)
                while (paint.measureText(total) > right - margin && paint.textSize > 12f) paint.textSize -= 1f
                lines(total, right - margin).forEach { line ->
                    if (y > bottom) { startPage(table = false); font(12f, mono) }
                    checkNotNull(canvas).drawText(line, right - paint.measureText(line), y, paint)
                    y += maxOf(24f, paint.fontSpacing)
                }
            }
            y += 14f
            font(9f, sans, secondaryInk)
            lines(appText(R.string.copy_thank_you_this_invoice_was_made_with), right - margin).forEach { line ->
                if (y > bottom) { startPage(table = false); font(9f, sans, secondaryInk) }
                checkNotNull(canvas).drawText(line, margin, y, paint); y += lineHeight
            }
            document.finishPage(checkNotNull(page))
            page = null
            return ByteArrayOutputStream().use { output -> document.writeTo(output); output.toByteArray() }
        } finally {
            page?.let(document::finishPage)
            document.close()
        }
    }
}
