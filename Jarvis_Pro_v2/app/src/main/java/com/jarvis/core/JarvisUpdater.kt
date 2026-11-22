package com.jarvis.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object JarvisUpdater {
    private const val UPDATE_JSON_URL = "https://example.com/jarvis/version.json"
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun checkForUpdate(context: Context, silent: Boolean = false, onStatus: (String) -> Unit) {
        if (UPDATE_JSON_URL.contains("example.com")) {
            if (!silent) onStatus("Yeniləmə serveri təyin edilməyib (JarvisUpdater.kt -> UPDATE_JSON_URL).")
            return
        }
        if (!silent) onStatus("Yeniləmə yoxlanılır...")
        executor.execute {
            try {
                val conn = URL(UPDATE_JSON_URL).openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                val json = JSONObject(conn.inputStream.bufferedReader().readText())
                val remoteCode = json.getInt("versionCode")
                val remoteName = json.optString("versionName", "")
                val apkUrl = json.getString("apkUrl")
                val currentCode = context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toInt()

                if (remoteCode > currentCode) {
                    mainHandler.post { onStatus("Yeni versiya ($remoteName) endirilir...") }
                    val outFile = File(context.getExternalFilesDir(null), "jarvis_update.apk")
                    URL(apkUrl).openStream().use { inp -> outFile.outputStream().use { out -> inp.copyTo(out) } }
                    val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
                    val install = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/vnd.android.package-archive")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    mainHandler.post { context.startActivity(install) }
                } else if (!silent) {
                    mainHandler.post { onStatus("Tətbiq ən son versiyadadır.") }
                }
            } catch (e: Exception) {
                if (!silent) mainHandler.post { onStatus("Yeniləmə xətası: ${e.localizedMessage}") }
            }
        }
    }
}
