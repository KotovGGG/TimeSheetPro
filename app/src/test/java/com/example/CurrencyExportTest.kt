package com.example

import com.example.data.local.entity.TimeEntry
import com.example.utils.export.XlsxColumn
import com.example.utils.export.XlsxGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

@RunWith(RobolectricTestRunner::class)
@Config(application = TimeSheetApp::class, qualifiers = "en-rUS", sdk = [36])
class CurrencyExportTest {
    @Test
    fun xlsxKeepsAmountsNumericAndTotalsSeparate() {
        val entry = TimeEntry(clientId = 1, startTime = 0, endTime = 3600000,
            durationSeconds = 3600, rawDurationSeconds = 3600, comment = null,
            appliedRate = 10.0, amount = 10.0, currencyCode = "USD")
        val workbook = XlsxGenerator.generateXlsx(
            entries = listOf(entry, entry.copy(amount = 25.0, currencyCode = "EUR"),
                entry.copy(amount = 7.5)),
            clients = emptyList(),
            selectedColumns = listOf(XlsxColumn.DATE, XlsxColumn.SUM),
            currencySymbol = "USD"
        )
        val worksheet = ZipInputStream(workbook.inputStream()).use { zip ->
            generateSequence { zip.nextEntry }.first { it.name == "xl/worksheets/sheet1.xml" }
            DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(zip)
        }
        assertTrue(worksheet.documentElement.textContent.contains("Currency"))
        val rows = worksheet.getElementsByTagName("row")
        val totals = (0 until rows.length).map { rows.item(it).textContent.trim().replace(Regex("\\s+"), "") }
            .filter { it.startsWith("Total") }
        assertEquals(listOf("Total25EUR", "Total17.50USD"), totals)
        assertEquals(5, worksheet.getElementsByTagName("v").length)
    }
}
