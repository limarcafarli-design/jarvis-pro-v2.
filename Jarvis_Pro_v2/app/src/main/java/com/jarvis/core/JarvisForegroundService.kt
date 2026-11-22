package com.jarvis.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import java.util.Locale

class JarvisForegroundService : Service(), TextToSpeech.OnInitListener {

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var wakeLock: PowerManager.WakeLock? = null
    private var isRunning = false
    private var awaitingCommandUntil = 0L
    private var consecutiveErrors = 0
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var router: JarvisCommandRouter
    private lateinit var health: JarvisHealthManager

    companion object {
        const val CHANNEL_ID = "jarvis_core_channel"
        const val NOTIF_ID = 101
        const val ACTION_SPEAK = "com.jarvis.core.ACTION_SPEAK"
        const val EXTRA_SPEAK_TEXT = "speak_text"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("'Jarvis' sözünü gözləyir..."))

        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Jarvis::CoreWakeLock")
        wakeLock?.acquire(10 * 60 * 60 * 1000L)

        tts = TextToSpeech(this, this)
        router = JarvisCommandRouter(this) { reply -> speak(reply) }
        health = JarvisHealthManager(this) {
            consecutiveErrors = 0
            JarvisUpdater.checkForUpdate(this, silent = true) {}
        }
        health.startMonitoring()
        setupRecognizer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_SPEAK) {
            val txt = intent.getStringExtra(EXTRA_SPEAK_TEXT)
            if (!txt.isNullOrBlank()) speak(txt)
            return START_STICKY
        }
        if (!isRunning) {
            isRunning = true
            startListeningLoop()
        }
        return START_STICKY
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val res = tts?.setLanguage(LangManager.getLocale(this))
            if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.language = Locale("tr", "TR")
            }
            ttsReady = true
        }
    }

    private fun setupRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                consecutiveErrors++
                val delay = if (consecutiveErrors > 5) { setupRecognizer(); 3000L } else 800L
                scheduleRestart(delay)
            }
            override fun onResults(results: Bundle?) {
                consecutiveErrors = 0
                val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!spoken.isNullOrBlank()) processWakeWordAndCommand(spoken)
                scheduleRestart(600)
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun processWakeWordAndCommand(raw: String) {
        val lower = raw.lowercase(Locale.getDefault()).trim()
        val now = System.currentTimeMillis()
        val wakeVariants = listOf("jarvis", "carvis", "hey jarvis", "ey jarvis", "jarviz", "джарвис")
        val matchedWake = wakeVariants.firstOrNull { lower == it || lower.startsWith("$it ") }

        if (matchedWake != null) {
            val rest = raw.substring(matchedWake.length).trim()
            if (rest.isEmpty()) {
                awaitingCommandUntil = now + 12_000L
                speak("Bəli Ramil, dinləyirəm")
            } else {
                awaitingCommandUntil = 0L
                router.handle(rest)
            }
            return
        }
        if (now <= awaitingCommandUntil) {
            awaitingCommandUntil = 0L
            router.handle(raw)
        }
    }

    private fun startListeningLoop() {
        if (!isRunning) return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, LangManager.getLang(this@JarvisForegroundService))
        }
        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            setupRecognizer()
            scheduleRestart(2000)
        }
    }

    private fun scheduleRestart(delayMs: Long) {
        if (!isRunning) return
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.postDelayed({ startListeningLoop() }, delayMs)
    }

    private fun speak(text: String) {
        if (ttsReady) {
            tts?.language = LangManager.getLocale(this)
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis_bg")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "J.A.R.V.I.S. Arxa Fon", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("J.A.R.V.I.S. Aktivdir")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        isRunning = false
        mainHandler.removeCallbacksAndMessages(null)
        health.stopMonitoring()
        speechRecognizer?.destroy()
        tts?.shutdown()
        if (wakeLock?.isHeld == true) wakeLock?.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
