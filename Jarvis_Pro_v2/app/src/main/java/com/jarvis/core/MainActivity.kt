package com.jarvis.core

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var tvLog: TextView
    private lateinit var tvStatus: TextView
    private lateinit var btnMic: MaterialButton
    private lateinit var btnBgService: MaterialButton
    private lateinit var spLang: Spinner

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var bgRunning = false
    private lateinit var router: JarvisCommandRouter

    private val langs = listOf("AZ (Azərbaycan)" to "az-AZ", "EN (English)" to "en-US", "RU (Русский)" to "ru-RU", "TR (Türkçe)" to "tr-TR")

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results[Manifest.permission.RECORD_AUDIO] == true) appendLog("J.A.R.V.I.S.: İcazələr verildi.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvLog = findViewById(R.id.tvLog)
        tvStatus = findViewById(R.id.tvStatus)
        btnMic = findViewById(R.id.btnMic)
        btnBgService = findViewById(R.id.btnBgService)
        spLang = findViewById(R.id.spLang)

        setupLangSpinner()
        tts = TextToSpeech(this, this)
        router = JarvisCommandRouter(this) { reply ->
            appendLog("J.A.R.V.I.S.: $reply")
            speak(reply)
        }
        setupRecognizer()
        requestNeededPermissions()

        JarvisUpdater.checkForUpdate(this, silent = true) { msg -> appendLog("Avto-Yeniləmə: $msg") }

        btnMic.setOnClickListener {
            if (hasMicPermission()) startListening() else requestNeededPermissions()
        }

        findViewById<MaterialButton>(R.id.btnProcurement).setOnClickListener {
            startActivity(Intent(this, ProcurementActivity::class.java))
        }

        btnBgService.setOnClickListener {
            if (!hasMicPermission()) { requestNeededPermissions(); return@setOnClickListener }
            val svc = Intent(this, JarvisForegroundService::class.java)
            if (!bgRunning) {
                ContextCompat.startForegroundService(this, svc)
                bgRunning = true
                btnBgService.text = "Arxa Fonu Dayandır"
                tvStatus.text = "Arxa fon aktivdir ('Jarvis' gözlənilir)"
            } else {
                stopService(svc)
                bgRunning = false
                btnBgService.text = "Arxa Fonu Başlat"
                tvStatus.text = "Hazırdır"
            }
        }

        findViewById<MaterialButton>(R.id.btnAccess).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<MaterialButton>(R.id.btnNotif).setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        findViewById<MaterialButton>(R.id.btnBattery).setOnClickListener {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                })
            } else appendLog("Batareya optimizasiyası artıq söndürülüb.")
        }

        findViewById<MaterialButton>(R.id.btnUpdate).setOnClickListener {
            JarvisUpdater.checkForUpdate(this, silent = false) { msg -> appendLog("Yeniləmə: $msg") }
        }
    }

    private fun setupLangSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, langs.map { it.first })
        spLang.adapter = adapter
        val cur = LangManager.getLang(this)
        val idx = langs.indexOfFirst { it.second == cur }.coerceAtLeast(0)
        spLang.setSelection(idx)
        spLang.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                LangManager.setLang(this@MainActivity, langs[pos].second)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun hasMicPermission() = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun requestNeededPermissions() {
        val list = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_PHONE_STATE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) list.add(Manifest.permission.ANSWER_PHONE_CALLS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) list.add(Manifest.permission.POST_NOTIFICATIONS)
        permissionLauncher.launch(list.toTypedArray())
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = LangManager.getLocale(this)
            ttsReady = true
        }
    }

    private fun setupRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { tvStatus.text = "Dinləyirəm..." }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { tvStatus.text = "Emal edilir..." }
            override fun onError(error: Int) { tvStatus.text = "Hazırdır" }
            override fun onResults(results: Bundle?) {
                tvStatus.text = "Hazırdır"
                val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: return
                appendLog("Ramil: $spoken")
                router.handle(spoken)
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, LangManager.getLang(this@MainActivity))
        }
        speechRecognizer?.startListening(intent)
    }

    private fun speak(text: String) {
        if (ttsReady) {
            tts?.language = LangManager.getLocale(this)
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis_fg")
        }
    }

    private fun appendLog(line: String) {
        tvLog.append("

$line")
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        tts?.shutdown()
        super.onDestroy()
    }
}
