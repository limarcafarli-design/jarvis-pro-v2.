package com.jarvis.core

import android.app.Notification
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class JarvisNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val pkg = sbn?.packageName ?: return
        if (pkg != "com.whatsapp" && pkg != "com.whatsapp.w4b") return

        val extras = sbn.notification?.extras ?: return
        val sender = extras.getString(Notification.EXTRA_TITLE) ?: return
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: return
        if (text.isBlank()) return

        val speakIntent = Intent(this, JarvisForegroundService::class.java).apply {
            action = JarvisForegroundService.ACTION_SPEAK
            putExtra(JarvisForegroundService.EXTRA_SPEAK_TEXT, "WhatsApp mesajı. $sender yazır: $text")
        }
        try {
            startService(speakIntent)
        } catch (_: Exception) {}
    }
}
