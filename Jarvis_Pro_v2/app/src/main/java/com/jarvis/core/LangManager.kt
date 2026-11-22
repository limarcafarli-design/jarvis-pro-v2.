package com.jarvis.core

import android.content.Context
import java.util.Locale

object LangManager {
    private const val PREF = "jarvis_lang"
    private const val KEY = "lang_code"

    fun getLang(context: Context): String {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY, "az-AZ") ?: "az-AZ"
    }

    fun setLang(context: Context, code: String) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY, code).apply()
    }

    fun getLocale(context: Context): Locale {
        val parts = getLang(context).split("-")
        return Locale(parts.getOrElse(0) { "az" }, parts.getOrElse(1) { "AZ" })
    }
}
