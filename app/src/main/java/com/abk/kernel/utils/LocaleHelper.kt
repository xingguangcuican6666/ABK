package com.abk.kernel.utils

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

object LocaleHelper {
    private const val PREF = "abk_locale"
    private const val KEY = "language"
    const val LANG_ZH = "zh"
    const val LANG_EN = "en"

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = wrap(context.applicationContext, Locale(getLanguage(context)))
    }

    fun str(resId: Int, vararg args: Any?): String {
        val c = appContext ?: return ""
        return if (args.isEmpty()) c.getString(resId) else c.getString(resId, *args)
    }

    fun getLanguage(context: Context): String =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY, detectDefault()) ?: detectDefault()

    fun setLanguage(context: Context, language: String) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY, language).apply()
        appContext = wrap(context.applicationContext, Locale(language))
    }

    fun applyLocale(context: Context): Context =
        wrap(context, Locale(getLanguage(context)))

    private fun wrap(context: Context, locale: Locale): Context {
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocales(LocaleList(locale))
        return context.createConfigurationContext(config)
    }

    private fun detectDefault(): String =
        if (Locale.getDefault().language == LANG_ZH) LANG_ZH else LANG_EN
}
