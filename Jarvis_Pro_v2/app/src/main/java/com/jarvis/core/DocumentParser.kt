package com.jarvis.core

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipInputStream

data class QuoteItem(
    val fileName: String,
    val detectedPrice: Double?,
    val currency: String,
    val deliveryDays: String
)

object DocumentParser {

    fun parseUri(context: Context, uri: Uri): QuoteItem {
        PDFBoxResourceLoader.init(context.applicationContext)
        val name = getFileName(context, uri)
        val lower = name.lowercase(Locale.getDefault())

        val text = try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                when {
                    lower.endsWith(".pdf") -> readPdf(stream)
                    lower.endsWith(".docx") -> readDocx(stream)
                    lower.endsWith(".xlsx") -> readXlsx(stream)
                    else -> stream.bufferedReader().readText()
                }
            } ?: ""
        } catch (e: Exception) {
            ""
        }

        return QuoteItem(name, extractPrice(text), extractCurrency(text), extractDelivery(text))
    }

    private fun readPdf(input: InputStream): String {
        PDDocument.load(input).use { doc -> return PDFTextStripper().getText(doc) }
    }

    private fun readDocx(input: InputStream): String {
        val sb = StringBuilder()
        ZipInputStream(input).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    val parser = XmlPullParserFactory.newInstance().newPullParser()
                    parser.setInput(zis, "UTF-8")
                    var ev = parser.eventType
                    while (ev != XmlPullParser.END_DOCUMENT) {
                        if (ev == XmlPullParser.TEXT) sb.append(parser.text).append(" ")
                        ev = parser.next()
                    }
                    break
                }
                entry = zis.nextEntry
            }
        }
        return sb.toString()
    }

    private fun readXlsx(input: InputStream): String {
        val shared = mutableListOf<String>()
        var sheetXml = ""
        ZipInputStream(input).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                when (entry.name) {
                    "xl/sharedStrings.xml" -> {
                        val xml = zis.readBytes().toString(Charsets.UTF_8)
                        val parser = XmlPullParserFactory.newInstance().newPullParser()
                        parser.setInput(xml.reader())
                        var inT = false
                        var ev = parser.eventType
                        while (ev != XmlPullParser.END_DOCUMENT) {
                            if (ev == XmlPullParser.START_TAG && parser.name == "t") inT = true
                            else if (ev == XmlPullParser.END_TAG && parser.name == "t") inT = false
                            else if (ev == XmlPullParser.TEXT && inT) shared.add(parser.text)
                            ev = parser.next()
                        }
                    }
                    "xl/worksheets/sheet1.xml" -> sheetXml = zis.readBytes().toString(Charsets.UTF_8)
                }
                entry = zis.nextEntry
            }
        }
        if (sheetXml.isEmpty()) return shared.joinToString(" ")
        val sb = StringBuilder()
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(sheetXml.reader())
        var isShared = false
        var inV = false
        var ev = parser.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                if (parser.name == "c") isShared = parser.getAttributeValue(null, "t") == "s"
                else if (parser.name == "v") inV = true
            } else if (ev == XmlPullParser.END_TAG && parser.name == "v") {
                inV = false
            } else if (ev == XmlPullParser.TEXT && inV) {
                val raw = parser.text
                if (isShared) {
                    val idx = raw.toIntOrNull()
                    if (idx != null && idx in shared.indices) sb.append(shared[idx]).append(" ")
                } else sb.append(raw).append(" ")
            }
            ev = parser.next()
        }
        return sb.toString()
    }

    private fun extractPrice(text: String): Double? {
        val totalRegex = Regex("(?i)(yekun|cəmi|total|məbləğ|qiymət|итого|сумма)[^0-9]{0,20}([0-9][0-9\\s.,]{0,14})")
        totalRegex.find(text)?.let {
            it.groupValues[2].replace(" ", "").replace(",", ".").toDoubleOrNull()?.let { v -> return v }
        }
        val currRegex = Regex("([0-9]+(?:[.,][0-9]{1,2})?)\\s*(?i)(azn|usd|eur|manat|rub)")
        return currRegex.findAll(text).mapNotNull { it.groupValues[1].replace(",", ".").toDoubleOrNull() }.maxOrNull()
    }

    private fun extractCurrency(text: String): String {
        val lower = text.lowercase(Locale.getDefault())
        return when {
            lower.contains("usd") || lower.contains("$") -> "USD"
            lower.contains("eur") || lower.contains("€") -> "EUR"
            else -> "AZN"
        }
    }

    private fun extractDelivery(text: String): String {
        return Regex("(\\d{1,3})\\s*(?i)(iş günü|gün|day|days|дней|день|günə)").find(text)?.value ?: "—"
    }

    private fun getFileName(context: Context, uri: Uri): String {
        var result = "teklif"
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) result = c.getString(idx)
            }
        }
        return result
    }
}
