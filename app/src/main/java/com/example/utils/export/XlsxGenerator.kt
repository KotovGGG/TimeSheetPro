package com.example.utils.export

import com.example.appLocale
import com.example.R
import com.example.appText
import com.example.data.local.entity.AppSettings
import com.example.data.local.entity.Client
import com.example.data.local.entity.TimeEntry
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class XlsxColumn(private val titleRes: Int) {
    DATE(R.string.copy_date),
    CLIENT(R.string.copy_client_2),
    COMMENT(R.string.copy_comment),
    SUM(R.string.copy_amount),
    CURRENCY(R.string.copy_currency);

    val title: String get() = appText(titleRes)
}

object XlsxGenerator {

    fun generateXlsx(
        entries: List<TimeEntry>,
        clients: List<Client>,
        selectedColumns: List<XlsxColumn>,
        currencySymbol: String,
        clientNames: String? = null,
        periodLabel: String? = null,
        settings: AppSettings? = null
    ): ByteArray {
        val bos = ByteArrayOutputStream()
        val zos = ZipOutputStream(bos)

        // 1. [Content_Types].xml
        zos.putNextEntry(ZipEntry("[Content_Types].xml"))
        zos.write(getContentTypesXml().toByteArray(Charsets.UTF_8))
        zos.closeEntry()

        // 2. _rels/.rels
        zos.putNextEntry(ZipEntry("_rels/.rels"))
        zos.write(getRelsXml().toByteArray(Charsets.UTF_8))
        zos.closeEntry()

        // 3. xl/_rels/workbook.xml.rels
        zos.putNextEntry(ZipEntry("xl/_rels/workbook.xml.rels"))
        zos.write(getWorkbookRelsXml().toByteArray(Charsets.UTF_8))
        zos.closeEntry()

        // 4. xl/workbook.xml
        zos.putNextEntry(ZipEntry("xl/workbook.xml"))
        zos.write(getWorkbookXml().toByteArray(Charsets.UTF_8))
        zos.closeEntry()

        // 5. xl/styles.xml
        zos.putNextEntry(ZipEntry("xl/styles.xml"))
        zos.write(getStylesXml().toByteArray(Charsets.UTF_8))
        zos.closeEntry()

        // 6. xl/worksheets/sheet1.xml
        zos.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
        zos.write(
            getSheet1Xml(
                entries = entries,
                clients = clients,
                selectedColumns = selectedColumns,
                currencySymbol = currencySymbol,
                clientNames = clientNames,
                periodLabel = periodLabel,
                settings = settings
            ).toByteArray(Charsets.UTF_8)
        )
        zos.closeEntry()

        zos.close()
        return bos.toByteArray()
    }

    private fun escapeXml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun formatAmountValue(v: Double): String {
        return if (v % 1.0 == 0.0) {
            v.toLong().toString()
        } else {
            String.format(Locale.US, "%.2f", v)
        }
    }

    private fun getContentTypesXml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
          <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
          <Default Extension="xml" ContentType="application/xml"/>
          <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
          <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
          <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
        </Types>
    """.trimIndent()

    private fun getRelsXml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
        </Relationships>
    """.trimIndent()

    private fun getWorkbookRelsXml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
          <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
        </Relationships>
    """.trimIndent()

    private fun getWorkbookXml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
          <sheets>
            <sheet name="TimeSheet" sheetId="1" r:id="rId1"/>
          </sheets>
        </workbook>
    """.trimIndent()

    private fun getStylesXml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
          <fonts count="4">
            <font><sz val="11"/><name val="Calibri"/><color rgb="FF1F2937"/></font>
            <font><sz val="11"/><b val="1"/><name val="Calibri"/><color rgb="FF1F2937"/></font>
            <font><sz val="14"/><b val="1"/><name val="Calibri"/><color rgb="FF111827"/></font>
            <font><sz val="12"/><b val="1"/><name val="Calibri"/><color rgb="FF065F46"/></font>
          </fonts>
          <fills count="4">
            <fill><patternFill patternType="none"/></fill>
            <fill><patternFill patternType="gray125"/></fill>
            <fill><patternFill patternType="solid"><fgColor rgb="FFE5E7EB"/><bgColor indexed="64"/></patternFill></fill>
            <fill><patternFill patternType="solid"><fgColor rgb="FFD1FAE5"/><bgColor indexed="64"/></patternFill></fill>
          </fills>
          <borders count="3">
            <border><left/><right/><top/><bottom/></border>
            <border>
              <left style="thin"><color rgb="FFD1D5DB"/></left>
              <right style="thin"><color rgb="FFD1D5DB"/></right>
              <top style="thin"><color rgb="FFD1D5DB"/></top>
              <bottom style="thin"><color rgb="FFD1D5DB"/></bottom>
            </border>
            <border>
              <left style="thin"><color rgb="FF059669"/></left>
              <right style="thin"><color rgb="FF059669"/></right>
              <top style="thin"><color rgb="FF059669"/></top>
              <bottom style="thin"><color rgb="FF059669"/></bottom>
            </border>
          </borders>
          <cellStyleXfs count="1">
            <xf numFmtId="0" fontId="0" fillId="0" borderId="0"/>
          </cellStyleXfs>
          <cellXfs count="8">
            <xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0"><alignment horizontal="left" vertical="center"/></xf>
            <xf numFmtId="0" fontId="1" fillId="2" borderId="1" xfId="0"><alignment horizontal="left" vertical="center"/></xf>
            <xf numFmtId="0" fontId="2" fillId="0" borderId="0" xfId="0"/>
            <xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0"/>
            <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
            <xf numFmtId="0" fontId="3" fillId="3" borderId="2" xfId="0"><alignment horizontal="left" vertical="center"/></xf>
            <xf numFmtId="0" fontId="3" fillId="3" borderId="2" xfId="0"><alignment horizontal="right" vertical="center"/></xf>
            <xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0"><alignment horizontal="right" vertical="center"/></xf>
          </cellXfs>
        </styleSheet>
    """.trimIndent()

    private fun getSheet1Xml(
        entries: List<TimeEntry>,
        clients: List<Client>,
        selectedColumns: List<XlsxColumn>,
        currencySymbol: String,
        clientNames: String?,
        periodLabel: String?,
        settings: AppSettings?
    ): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
        sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">\n")

        // Exclude CLIENT column from table columns because client is placed in header block above table
        val requested = selectedColumns.filter { it != XlsxColumn.CLIENT && it != XlsxColumn.CURRENCY }.ifEmpty {
            listOf(XlsxColumn.DATE, XlsxColumn.COMMENT, XlsxColumn.SUM)
        }
        val tableColumns = if (XlsxColumn.SUM in requested) {
            requested.toMutableList().apply { add(indexOf(XlsxColumn.SUM) + 1, XlsxColumn.CURRENCY) }
        } else requested
        val totals = sortedMapOf<String, Double>()


        // Set column widths
        sb.append("  <cols>\n")
        tableColumns.forEachIndexed { colIdx, col ->
            val minMax = colIdx + 1
            val width = when (col) {
                XlsxColumn.DATE -> 16
                XlsxColumn.COMMENT -> 45
                XlsxColumn.SUM -> 20
                XlsxColumn.CURRENCY -> 13
                else -> 20
            }
            sb.append("    <col min=\"$minMax\" max=\"$minMax\" width=\"$width\" customWidth=\"1\"/>\n")
        }
        sb.append("  </cols>\n")

        sb.append("  <sheetData>\n")

        fun colLetter(idx: Int): String = ('A' + idx).toString()

        var rowIdx = 1

        // Title Row
        sb.append("    <row r=\"$rowIdx\" ht=\"24\" customHeight=\"1\">\n")
        sb.append("      <c r=\"A$rowIdx\" t=\"inlineStr\" s=\"2\"><is><t>${escapeXml(appText(R.string.copy_time_tracking_report))}</t></is></c>\n")
        sb.append("    </row>\n")
        rowIdx++

        // Determine Client Name for header
        val resolvedClientName = when {
            !clientNames.isNullOrBlank() -> clientNames
            entries.isNotEmpty() -> {
                val distinctIds = entries.map { it.clientId }.distinct()
                val clientMap = clients.associateBy { it.id }
                if (distinctIds.size == 1) {
                    clientMap[distinctIds.first()]?.name ?: appText(R.string.copy_all_clients)
                } else if (distinctIds.size <= 3) {
                    distinctIds.mapNotNull { clientMap[it]?.name }.joinToString(", ")
                } else {
                    appText(R.string.copy_all_clients_1, distinctIds.size)
                }
            }
            else -> appText(R.string.copy_all_clients)
        }

        // Client Row
        sb.append("    <row r=\"$rowIdx\">\n")
        sb.append("      <c r=\"A$rowIdx\" t=\"inlineStr\" s=\"3\"><is><t>${escapeXml(appText(R.string.copy_client))}</t></is></c>\n")
        sb.append("      <c r=\"B$rowIdx\" t=\"inlineStr\" s=\"4\"><is><t>${escapeXml(resolvedClientName)}</t></is></c>\n")
        sb.append("    </row>\n")
        rowIdx++

        // Period Row (if available)
        if (!periodLabel.isNullOrBlank()) {
            sb.append("    <row r=\"$rowIdx\">\n")
            sb.append("      <c r=\"A$rowIdx\" t=\"inlineStr\" s=\"3\"><is><t>${escapeXml(appText(R.string.copy_period))}</t></is></c>\n")
            sb.append("      <c r=\"B$rowIdx\" t=\"inlineStr\" s=\"4\"><is><t>${escapeXml(periodLabel)}</t></is></c>\n")
            sb.append("    </row>\n")
            rowIdx++
        }

        // Blank spacer row
        sb.append("    <row r=\"$rowIdx\"/>\n")
        rowIdx++

        // Table Header Row
        sb.append("    <row r=\"$rowIdx\" ht=\"20\" customHeight=\"1\">\n")
        tableColumns.forEachIndexed { colIdx, col ->
            val ref = "${colLetter(colIdx)}$rowIdx"
            val title = col.title
            sb.append("      <c r=\"$ref\" t=\"inlineStr\" s=\"1\"><is><t>${escapeXml(title)}</t></is></c>\n")
        }
        sb.append("    </row>\n")
        rowIdx++

        val dateFormat = SimpleDateFormat("dd.MM.yyyy", appLocale())

        // Data Rows
        entries.forEach { entry ->
            val roundedAmount = entry.amount
            totals[entry.currencyCode] = (totals[entry.currencyCode] ?: 0.0) + roundedAmount

            sb.append("    <row r=\"$rowIdx\">\n")
            tableColumns.forEachIndexed { colIdx, col ->
                val ref = "${colLetter(colIdx)}$rowIdx"
                when (col) {
                    XlsxColumn.DATE -> {
                        val dateStr = dateFormat.format(Date(entry.startTime))
                        sb.append("      <c r=\"$ref\" t=\"inlineStr\" s=\"0\"><is><t>${escapeXml(dateStr)}</t></is></c>\n")
                    }
                    XlsxColumn.COMMENT -> {
                        val comment = entry.comment ?: ""
                        sb.append("      <c r=\"$ref\" t=\"inlineStr\" s=\"0\"><is><t>${escapeXml(comment)}</t></is></c>\n")
                    }
                    XlsxColumn.SUM -> {
                        val formattedVal = formatAmountValue(roundedAmount)
                        sb.append("      <c r=\"$ref\" t=\"n\" s=\"7\"><v>$formattedVal</v></c>\n")
                    }
                    XlsxColumn.CURRENCY -> {
                        sb.append("      <c r=\"$ref\" t=\"inlineStr\" s=\"0\"><is><t>${escapeXml(entry.currencyCode)}</t></is></c>\n")
                    }
                    XlsxColumn.CLIENT -> Unit
                }
            }
            sb.append("    </row>\n")
            rowIdx++
        }

        // Total Row
        val sumColIdx = tableColumns.indexOf(XlsxColumn.SUM)
        val currencyColIdx = tableColumns.indexOf(XlsxColumn.CURRENCY)
        if (sumColIdx >= 0) for ((code, amount) in totals.ifEmpty { mapOf(currencySymbol to 0.0) }) {
            val formattedTotal = formatAmountValue(amount)

            sb.append("    <row r=\"$rowIdx\" ht=\"22\" customHeight=\"1\">\n")
            tableColumns.forEachIndexed { colIdx, _ ->
                val ref = "${colLetter(colIdx)}$rowIdx"
                if (colIdx == sumColIdx) {
                    // Highlighted Total Sum Cell
                    sb.append("      <c r=\"$ref\" t=\"n\" s=\"6\"><v>$formattedTotal</v></c>\n")
                } else if (colIdx == currencyColIdx) {
                    sb.append("      <c r=\"$ref\" t=\"inlineStr\" s=\"5\"><is><t>${escapeXml(code)}</t></is></c>\n")
                } else if (colIdx == 0) {
                    // "Итого" label
                    sb.append("      <c r=\"$ref\" t=\"inlineStr\" s=\"5\"><is><t>${escapeXml(appText(R.string.copy_total))}</t></is></c>\n")
                } else {
                    // Spacer cell in total row with same style
                    sb.append("      <c r=\"$ref\" t=\"inlineStr\" s=\"5\"><is><t></t></is></c>\n")
                }
            }
            sb.append("    </row>\n")

            rowIdx++
        }

        sb.append("  </sheetData>\n")
        sb.append("</worksheet>\n")
        return sb.toString()
    }
}
