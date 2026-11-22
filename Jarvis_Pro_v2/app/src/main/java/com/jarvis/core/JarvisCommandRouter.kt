package com.jarvis.core

import android.Manifest
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class JarvisCommandRouter(
    private val context: Context,
    private val onResponse: (String) -> Unit
) {
    private val appAliases = mapOf(
        "yutub" to "youtube", "ютуб" to "youtube",
        "veyz" to "waze", "вейз" to "waze",
        "teleqram" to "telegram", "телеграм" to "telegram",
        "vatsap" to "whatsapp", "ватсап" to "whatsapp",
        "instaqram" to "instagram", "инстаграм" to "instagram",
        "xəritə" to "maps", "xerite" to "maps", "карты" to "maps", "harita" to "maps",
        "şəkillər" to "photos", "qalereya" to "gallery", "галерея" to "gallery",
        "kalkulyator" to "calculator", "калькулятор" to "calculator",
        "brauzer" to "chrome", "xrom" to "chrome", "хром" to "chrome",
        "poçt" to "gmail", "mail" to "gmail", "почта" to "gmail"
    )

    fun handle(raw: String) {
        val cmd = raw.lowercase(Locale.getDefault())
            .removePrefix("jarvis ")
            .removePrefix("carvis ")
            .removePrefix("джарвис ")
            .trim()

        val acc = JarvisAccessibilityService.instance
        val callTarget = extractCallTarget(cmd)

        when {
            cmd.contains("zəngi aç") || cmd.contains("zengi ac") || cmd.contains("cavab ver") ||
            cmd.contains("answer call") || cmd.contains("ответь") || cmd.contains("telefonu aç") -> {
                answerIncomingCall()
            }
            cmd.contains("satınalma") || cmd.contains("satinalma") || cmd.contains("müqayisə") || cmd.contains("procurement") -> {
                launchIntent(Intent(context, ProcurementActivity::class.java), "Satınalma Excel bölməsi açılır")
            }
            cmd.contains("rus dili") || cmd.contains("russian") || cmd.contains("по-русски") -> {
                LangManager.setLang(context, "ru-RU"); onResponse("Язык переключен на русский")
            }
            cmd.contains("ingilis") || cmd.contains("english") -> {
                LangManager.setLang(context, "en-US"); onResponse("Language switched to English")
            }
            cmd.contains("türk") || cmd.contains("turk") -> {
                LangManager.setLang(context, "tr-TR"); onResponse("Dil Türkçe olarak ayarlandı")
            }
            cmd.contains("azərbaycan") || cmd.contains("azerbaycan") -> {
                LangManager.setLang(context, "az-AZ"); onResponse("Azərbaycan dili aktiv edildi")
            }
            callTarget != null -> {
                makeCall(callTarget)
            }
            cmd.startsWith("waze ") || cmd.startsWith("veyz ") -> {
                val dest = raw.substringAfter(" ").trim()
                launchIntent(Intent(Intent.ACTION_VIEW, Uri.parse("https://waze.com/ul?q=${Uri.encode(dest)}&navigate=yes")), "$dest istiqamətində Waze açılır")
            }
            cmd.startsWith("youtube ") || cmd.startsWith("yutub ") -> {
                val q = raw.substringAfter(" ").trim()
                launchIntent(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(q)}")), "YouTube-da $q axtarılır")
            }
            cmd.startsWith("whatsapp ") || cmd.startsWith("vatsap ") -> {
                openWhatsApp(raw.substringAfter(" ").trim())
            }
            cmd.startsWith("email ") || cmd.startsWith("e-poçt ") || cmd.startsWith("mail ") -> {
                openEmail(raw.substringAfter(" ").trim())
            }
            cmd.startsWith("axtar ") || cmd.startsWith("google ") || cmd.startsWith("search ") || cmd.startsWith("найди ") -> {
                googleSearch(raw.substringAfter(" ").trim())
            }
            cmd.contains("şəkilləri aç") || cmd.contains("qalereya") || cmd.contains("gallery") || cmd.contains("галерея") -> {
                launchIntent(Intent(Intent.ACTION_VIEW, MediaStore.Images.Media.EXTERNAL_CONTENT_URI), "Şəkillər açılır")
            }
            cmd.contains("kamera") || cmd.contains("camera") || cmd.contains("камера") -> {
                launchIntent(Intent(MediaStore.ACTION_IMAGE_CAPTURE), "Kamera açılır")
            }
            cmd.contains("geri qayıt") || cmd.contains("back") || cmd.contains("назад") -> {
                if (acc?.goBack() == true) onResponse("Geri qayıdıldı") else onResponse("Əlçatanlıq icazəsini aktiv edin")
            }
            cmd.contains("ana ekran") || cmd.contains("home") || cmd.contains("домой") -> {
                if (acc?.goHome() == true) onResponse("Ana ekrana keçildi") else onResponse("Əlçatanlıq icazəsini aktiv edin")
            }
            cmd.contains("bildiriş") || cmd.contains("notifications") -> {
                if (acc?.openNotifications() == true) onResponse("Bildirişlər açılır") else onResponse("Əlçatanlıq icazəsini aktiv edin")
            }
            cmd.contains("zəngli saat") || cmd.contains("alarm") || cmd.contains("будильник") -> {
                setAlarm(cmd)
            }
            cmd.contains("saat neçə") || cmd.contains("what time") || cmd.contains("сколько времени") -> {
                val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                onResponse("Saat $time-dir.")
            }
            else -> {
                if (!tryOpenAnyAppFromSentence(cmd)) {
                    onResponse("Əmri anlamadım, Ramil.")
                }
            }
        }
    }

    private fun answerIncomingCall() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED
        ) {
            val tm = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            @Suppress("DEPRECATION")
            tm.acceptRingingCall()
            onResponse("Zəng açılır")
        } else {
            onResponse("Zəngi açmaq üçün telefon icazəsini verin")
        }
    }

    private fun tryOpenAnyAppFromSentence(cmd: String): Boolean {
        val stopWords = setOf("aç", "ac", "open", "открой", "gir", "işə", "sal", "tətbiqi", "proqramı")
        val tokens = cmd.split(Regex("[\s,.-]+"))
            .map { cleanAzSuffix(it) }
            .filter { it.length >= 2 && it !in stopWords }

        val expanded = tokens.flatMap { t -> appAliases[t]?.let { listOf(it, t) } ?: listOf(t) }
        val pm = context.packageManager
        val apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER), 0)

        for (query in expanded) {
            val match = apps.firstOrNull { it.loadLabel(pm).toString().lowercase(Locale.getDefault()).contains(query) }
            if (match != null) {
                pm.getLaunchIntentForPackage(match.activityInfo.packageName)?.let {
                    launchIntent(it, "${match.loadLabel(pm)} açılır")
                    return true
                }
            }
        }
        return false
    }

    private fun cleanAzSuffix(word: String): String {
        val suffixes = listOf("ını", "ini", "unu", "ünü", "nı", "ni", "nu", "nü", "ya", "yə", "da", "də", "a", "ə", "ı", "i", "u", "ü")
        for (s in suffixes) {
            if (word.length > s.length + 2 && word.endsWith(s)) return word.removeSuffix(s)
        }
        return word
    }

    private fun extractCallTarget(cmd: String): String? {
        val prefixes = listOf("zəng et ", "zeng et ", "zəng vur ", "yığ ", "call ", "позвони ", "ara ")
        for (p in prefixes) if (cmd.startsWith(p)) return cmd.removePrefix(p).trim()
        val suffixes = listOf(" zəng et", " zeng et", " zəng vur", " yığ")
        for (s in suffixes) if (cmd.endsWith(s)) return cmd.removeSuffix(s).trim()
        return null
    }

    private fun googleSearch(query: String) {
        val intent = Intent(Intent.ACTION_WEB_SEARCH).apply { putExtra(SearchManager.QUERY, query) }
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            onResponse("$query üçün axtarış açılır")
        } catch (e: Exception) {
            launchIntent(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")), "$query axtarılır")
        }
    }

    private fun makeCall(target: String) {
        val digits = target.filter { it.isDigit() || it == '+' }
        val number = if (digits.length >= 6) digits else findContactPhoneSmart(target)
        if (number.isNullOrEmpty()) {
            onResponse("$target adlı kontakt tapılmadı.")
            return
        }
        val canCall = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        val action = if (canCall) Intent.ACTION_CALL else Intent.ACTION_DIAL
        launchIntent(Intent(action, Uri.parse("tel:$number")), "$target nömrəsinə zəng edilir")
    }

    private fun findContactPhoneSmart(rawName: String): String? {
        findContactPhone(rawName)?.let { return it }
        val trimmed = cleanAzSuffix(rawName)
        return if (trimmed.length >= 2 && trimmed != rawName) findContactPhone(trimmed) else null
    }

    private fun findContactPhone(nameQuery: String): String? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$nameQuery%"),
            null
        )
        cursor?.use { if (it.moveToFirst()) return it.getString(1) }
        return null
    }

    private fun openWhatsApp(text: String) {
        val parts = text.split(" ", limit = 2)
        val first = parts.getOrNull(0) ?: ""
        val msg = parts.getOrNull(1) ?: ""
        val digits = first.filter { it.isDigit() }
        val phone = if (digits.length >= 9) digits else (findContactPhoneSmart(first)?.filter { it.isDigit() } ?: "")
        val url = if (phone.length >= 9) "https://wa.me/$phone?text=${Uri.encode(msg)}" else "https://wa.me/?text=${Uri.encode(text)}"
        launchIntent(Intent(Intent.ACTION_VIEW, Uri.parse(url)), "WhatsApp açılır")
    }

    private fun openEmail(subject: String) {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_SUBJECT, subject)
        }
        launchIntent(intent, "E-poçt açılır")
    }

    private fun setAlarm(cmd: String) {
        val match = Regex("(\d{1,2})[:\s](\d{2})").find(cmd)
        val hour = match?.groupValues?.get(1)?.toIntOrNull() ?: 8
        val min = match?.groupValues?.get(2)?.toIntOrNull() ?: 0
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, min)
            putExtra(AlarmClock.EXTRA_MESSAGE, "J.A.R.V.I.S.")
        }
        launchIntent(intent, String.format("%02d:%02d üçün zəngli saat qurulur", hour, min))
    }

    private fun launchIntent(intent: Intent, msg: String) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            onResponse(msg)
        } catch (e: Exception) {
            onResponse("Bu əməliyyat üçün uyğun tətbiq tapılmadı.")
        }
    }
}
