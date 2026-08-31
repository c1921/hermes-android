package com.nousresearch.hermes.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

internal enum class AppLanguage(val languageTag: String) {
    SYSTEM(""),
    ENGLISH("en-US"),
    SIMPLIFIED_CHINESE("zh-CN"),
    ;

    fun apply() {
        val locales = if (this == SYSTEM) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(languageTag)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    companion object {
        fun current(): AppLanguage = fromLanguageTag(
            AppCompatDelegate.getApplicationLocales().get(0)?.toLanguageTag(),
        )

        internal fun fromLanguageTag(tag: String?): AppLanguage = when {
            tag.isNullOrBlank() -> SYSTEM
            tag.startsWith("zh", ignoreCase = true) -> SIMPLIFIED_CHINESE
            tag.startsWith("en", ignoreCase = true) -> ENGLISH
            else -> SYSTEM
        }
    }
}
