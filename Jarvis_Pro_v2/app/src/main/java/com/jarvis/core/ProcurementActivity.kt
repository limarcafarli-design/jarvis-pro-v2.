package com.jarvis.core

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.android.material.button.MaterialButton
import java.io.File
import java.util.Locale

class ProcurementActivity : AppCompatActivity() {

    private lateinit var tableCompare: TableLayout
    private lateinit var tvSummary: TextView
    private val items = mutableListOf<QuoteItem>()

    private val pickFilesLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNullOrEmpty()) return@registerForActivityResult
        items.clear()
        uris.forEach { uri -> items.add(DocumentParser.parseUri(this, uri)) }
        renderTable()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_procurement)

        tableCompare = findViewById(R.id.tableCompare)
        tvSummary = findViewById(R.id.tvProcSummary)

        findViewById<MaterialButton>(R.id.btnPickFiles).setOnClickListener {
            pickFilesLauncher.launch(arrayOf("*/*"))
        }

        findViewById<MaterialButton>(R.id.btnExportExcel).setOnClickListener {
            exportExcel()
        }
    }

    private fun renderTable() {
        tableCompare.removeAllViews()
        if (items.isEmpty()) return

        val minPrice = items.mapNotNull { it.detectedPrice }.minOrNull()
        val bestItem = items.firstOrNull { it.detectedPrice != null && it.detectedPrice == minPrice }
        tvSummary.text = if (bestItem != null) {
            "Ən sərfəli: ${bestItem.fileName} (${String.format(Locale.US, "%.2f", bestItem.detectedPrice)} ${bestItem.currency})"
        } else "${items.size} fayl oxundu"

        addRow(listOf("Fayl / Təchizatçı", "Qiymət", "Valyuta", "Çatdırılma", "Status"), true, false)
        items.forEach { item ->
            val isBest = minPrice != null && item.detectedPrice == minPrice
            val priceText = item.detectedPrice?.let { String.format(Locale.US, "%.2f", it) } ?: "Tapılmadı"
            addRow(listOf(item.fileName, priceText, item.currency, item.deliveryDays, if (isBest) "Ən Sərfəli" else "Alternativ"), false, isBest)
        }
    }

    private fun addRow(cols: List<String>, isHeader: Boolean, isBest: Boolean) {
        val row = TableRow(this)
        if (isBest) row.setBackgroundColor(Color.parseColor("#133329"))
        cols.forEach { txt ->
            val tv = TextView(this).apply {
                text = txt
                setPadding(18, 14, 18, 14)
                setTextColor(when {
                    isHeader -> Color.parseColor("#22D3EE")
                    isBest -> Color.parseColor("#34D399")
                    else -> Color.parseColor("#E2E8F0")
                })
                textSize = if (isHeader) 13f else 14f
                if (isHeader || isBest) setTypeface(null, Typeface.BOLD)
            }
            row.addView(tv)
        }
        tableCompare.addView(row)
    }

    private fun exportExcel() {
        if (items.isEmpty()) {
            Toast.makeText(this, "Əvvəlcə faylları yükləyin", Toast.LENGTH_SHORT).show()
            return
        }
        val outFile = File(cacheDir, "rfq_muqayise.xlsx")
        XlsxWriter.writeComparisonXlsx(outFile, items)
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", outFile)
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(share, "Excel müqayisə cədvəlini paylaş"))
    }
}
