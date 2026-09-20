package com.example.core

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import androidx.compose.ui.unit.LayoutDirection
import java.util.Locale

enum class AppLanguage(
    val code: String,
    val displayName: String,
    val nativeName: String,
    val isRtl: Boolean = false
) {
    CHINESE("zh", "中文", "简体中文", false),
    ENGLISH("en", "English", "English", false),
    ARABIC("ar", "العربية", "العربية", true);

    val layoutDirection: LayoutDirection
        get() = if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr

    companion object {
        private const val PREFS_NAME = "netcheck_language_prefs"
        private const val KEY_LANG = "selected_language"

        fun fromCode(code: String?): AppLanguage {
            return entries.find { it.code.equals(code, ignoreCase = true) } ?: getDefaultLanguage()
        }

        fun getDefaultLanguage(): AppLanguage {
            return ENGLISH
        }

        fun getSavedLanguage(context: Context): AppLanguage {
            val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val code = prefs.getString(KEY_LANG, null)
            return if (code != null) fromCode(code) else getDefaultLanguage()
        }

        fun saveLanguage(context: Context, language: AppLanguage) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_LANG, language.code).apply()
        }

        fun createLocalizedContext(context: Context, language: AppLanguage): Context {
            val locale = when (language) {
                CHINESE -> Locale.SIMPLIFIED_CHINESE
                ENGLISH -> Locale.ENGLISH
                ARABIC -> Locale("ar")
            }
            Locale.setDefault(locale)
            val config = Configuration(context.resources.configuration)
            config.setLocale(locale)
            config.setLayoutDirection(locale)
            return context.createConfigurationContext(config)
        }
    }
}
