package com.jarvis.core

import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object XlsxWriter {

    fun writeComparisonXlsx(outFile: File, items: List<QuoteItem>) {
        val minPrice = items.mapNotNull { it.detectedPrice }.minOrNull()
        val rowsXml = StringBuilder()

        rowsXml.append(buildRow(1, listOf("Fayl / Techizatci", "Qiymet", "Valyuta", "Catdirilma", "Status")))
        items.forEachIndexed { idx, item ->
            val r = idx + 2
            val isBest = minPrice != null && item.detectedPrice == minPrice
            val priceStr = item.detectedPrice?.let { String.format(Locale.US, "%.2f", it) } ?: "Tapilmadi"
            val statusStr = if (isBest) "En Serfeli" else "Alternativ"
            rowsXml.append(buildRow(r, listOf(item.fileName, priceStr, item.currency, item.deliveryDays, statusStr)))
        }

        val sheetXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <sheetData>$rowsXml</sheetData>
</worksheet>"""

        val contentTypes = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
</Types>"""

        val rootRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

        val workbook = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets><sheet name="Muqayise" sheetId="1" r:id="rId1"/></sheets>
</workbook>"""

        val wbRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
</Relationships>"""

        ZipOutputStream(FileOutputStream(outFile)).use { zos ->
            putEntry(zos, "[Content_Types].xml", contentTypes)
            putEntry(zos, "_rels/.rels", rootRels)
            putEntry(zos, "xl/workbook.xml", workbook)
            putEntry(zos, "xl/_rels/workbook.xml.rels", wbRels)
            putEntry(zos, "xl/worksheets/sheet1.xml", sheetXml)
        }
    }

    private fun buildRow(rowNum: Int, cells: List<String>): String {
        val sb = StringBuilder("<row r=\"$rowNum\">")
        cells.forEachIndexed { colIdx, value ->
            val colChar = ('A'.code + colIdx).toChar()
            val num = value.toDoubleOrNull()
            if (num != null) {
                sb.append("<c r=\"$colChar$rowNum\"><v>$num</v></c>")
            } else {
                val escaped = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                sb.append("<c r=\"$colChar$rowNum\" t=\"inlineStr\"><is><t>$escaped</t></is></c>")
            }
        }
        sb.append("</row>")
        return sb.toString()
    }

    private fun putEntry(zos: ZipOutputStream, name: String, content: String) {
        zos.putNextEntry(ZipEntry(name))
        zos.write(content.toByteArray(Charsets.UTF_8))
        zos.closeEntry()
    }
}
