package com.example

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.local.entity.Client
import com.example.data.local.entity.TimeEntry
import com.example.utils.export.PdfGenerator
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale

/** Uses only generated values and files; never opens or modifies a database. */
@RunWith(AndroidJUnit4::class)
class PdfLayoutAuditTest {
    @Test fun generatedDocuments() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(BuildConfig.DEBUG && context.packageName == "com.example.preview")
        val output = checkNotNull(context.getExternalFilesDir("pdf-audit")).apply { mkdirs() }
        val oldLocale = Locale.getDefault()
        val clients = listOf(
            Client(id = 1, name = "Международная студия архитектуры и проектирования Северного региона", colorHex = "#625DA0", defaultHourlyRate = 100.0),
            Client(id = 2, name = "Заказчик с очень длинным русским названием для проверки полного текста", colorHex = "#625DA0", defaultHourlyRate = 0.0),
        )
        fun entry(id: Long, comment: String, amount: Double, hours: Long = 1) = TimeEntry(id = id,
            clientId = if (id % 2 == 0L) 2 else 1, startTime = 1790848800000L,
            endTime = 1790848800000L + hours * 3600000, durationSeconds = hours * 3600,
            rawDurationSeconds = hours * 3600, comment = comment, appliedRate = 100.0, amount = amount)
        fun save(name: String, entries: List<TimeEntry>) {
            val bytes = PdfGenerator.generatePdf(entries, clients, clients.joinToString { it.name },
                "За весь период · проверка длинных названий, больших сумм и переноса страниц", "RUB")
            check(bytes.take(5).toByteArray().toString(Charsets.US_ASCII) == "%PDF-")
            val file = File(output, "$name.pdf").apply { writeBytes(bytes) }
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    check(renderer.pageCount > 0)
                    if (name == "multipage") check(renderer.pageCount > 1)
                    repeat(renderer.pageCount) { index ->
                        renderer.openPage(index).use { page ->
                            check(page.width == 595 && page.height == 842)
                            val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                            File(output, "$name-${index + 1}.png").outputStream().use {
                                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                            }
                            bitmap.recycle()
                        }
                    }
                }
            }
        }
        try {
            Locale.setDefault(Locale.US)
            save("empty", emptyList())
            save("large", listOf(entry(1, "Крупная сумма без потери цифр", 1234567890.12, 999999),
                entry(2, "Описание работы с переносом строки\nПоследняя строка комментария", 0.0)))
            val longComment = (1..160).joinToString(" ") { "Фрагмент-$it: подробный комментарий к выполненной работе." } + " END_OF_LONG_COMMENT"
            save("multipage", listOf(entry(1, longComment, 100.0),
                entry(2, "LAST_RECORD_ON_FINAL_PAGE", 200.0)))
        } finally {
            Locale.setDefault(oldLocale)
        }
    }
}
