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
    const val LANG_RU = "ru"

    private var appContext: Context? = null

    /** Localized context between [applyLocale] in attachBaseContext and [init] in onCreate. */
    private var earlyContext: Context? = null

    /** Mirrors the language code stored in prefs (used before [appContext] is ready). */
    @Volatile
    private var cachedLanguage: String? = null

    fun init(context: Context) {
        val language = getLanguage(context)
        cachedLanguage = language
        appContext = wrap(appContextFor(context), localeForLanguage(language))
        earlyContext = null
    }

    fun str(resId: Int, vararg args: Any?): String {
        val c = appContext ?: earlyContext ?: return ""
        return if (args.isEmpty()) c.getString(resId) else c.getString(resId, *args)
    }

    /** App UI language from Settings — same source as [wrap] / [str]. */
    fun currentUiLanguage(): String {
        appContext?.let { return getLanguage(it) }
        return cachedLanguage ?: detectDefault()
    }

    fun getLanguage(context: Context): String =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY, detectDefault()) ?: detectDefault()

    fun setLanguage(context: Context, language: String) {
        cachedLanguage = language
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY, language).apply()
        appContext = wrap(appContextFor(context), localeForLanguage(language))
    }

    fun applyLocale(context: Context): Context = runCatching {
        val language = getLanguage(context)
        cachedLanguage = language
        val locale = localeForLanguage(language)
        if (appContext == null) {
            // applicationContext is null during Application.attachBaseContext — use base context.
            earlyContext = wrap(context, locale)
        }
        wrap(context, locale)
    }.getOrElse { context }

    private fun localeForLanguage(language: String): Locale = when (language) {
        LANG_ZH -> Locale.SIMPLIFIED_CHINESE
        LANG_EN -> Locale.ENGLISH
        LANG_RU -> Locale.forLanguageTag("ru")
        else -> Locale.forLanguageTag(language)
    }

    private fun appContextFor(context: Context): Context =
        context.applicationContext ?: context

    private fun wrap(context: Context, locale: Locale): Context {
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        // Per-string fallback via LocaleList: Android 7+ resource framework picks the
        // first locale that has a given string. The base values/ folder is Chinese
        // (the historical "default"), so we always keep it as the last fallback.
        // For non-Chinese users, English is preferred over Chinese.
        val locales = when (locale.language) {
            LANG_ZH -> LocaleList(locale)
            LANG_EN -> LocaleList(locale, Locale.SIMPLIFIED_CHINESE)
            else -> LocaleList(locale, Locale.ENGLISH, Locale.SIMPLIFIED_CHINESE)
        }
        config.setLocales(locales)
        return context.createConfigurationContext(config)
    }

    private fun detectDefault(): String =
        when (Locale.getDefault().language) {
            LANG_ZH -> LANG_ZH
            LANG_RU -> LANG_RU
            else -> LANG_EN
        }
}
