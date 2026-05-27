package com.example.worktr.util

import android.content.Context

object LanguagePreferences {
    const val PREFS = "app_prefs"
    const val PREF_APP_LANGUAGE = "app_language"

    fun appLanguage(context: Context): AppLanguage =
        AppLanguage.fromCode(
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(PREF_APP_LANGUAGE, AppLanguage.UKRAINIAN.code)
        )

    fun saveAppLanguage(context: Context, language: AppLanguage) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_APP_LANGUAGE, language.code)
            .apply()
    }
}
