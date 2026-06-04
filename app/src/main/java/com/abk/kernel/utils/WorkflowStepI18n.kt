package com.abk.kernel.utils

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.abk.kernel.BuildConfig
import com.abk.kernel.data.model.WorkflowStepI18nBundle
import com.abk.kernel.data.repository.PreferencesRepository
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object WorkflowStepI18n {

    private const val ASSET_DIR = "i18n"
    private const val CACHE_DIR = "i18n"
    private const val FALLBACK_LANG = LocaleHelper.LANG_EN

    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private lateinit var appContext: Context
    private lateinit var prefs: PreferencesRepository

    private val mapsByLang = ConcurrentHashMap<String, Map<String, String>>()
    private val inFlightLangs = ConcurrentHashMap.newKeySet<String>()
    private val refreshMutex = Mutex()

    fun init(context: Context) {
        appContext = context.applicationContext
        prefs = PreferencesRepository(appContext)
        preloadBundled(LocaleHelper.LANG_EN)
        preloadBundled(LocaleHelper.LANG_RU)
    }

    fun translate(name: String): String {
        if (name.isBlank()) return name
        if (LocaleHelper.currentUiLanguage() == LocaleHelper.LANG_ZH) return name
        val trimmed = name.trim()
        val lang = LocaleHelper.currentUiLanguage()
        mapsByLang[lang]?.get(trimmed)?.let { return it }
        if (lang != FALLBACK_LANG) {
            mapsByLang[FALLBACK_LANG]?.get(trimmed)?.let { return it }
        }
        return name
    }

    suspend fun refresh(context: FetchContext, lang: String): RefreshResult = refreshMutex.withLock {
        if (lang == LocaleHelper.LANG_ZH) return RefreshResult.UpToDate
        val storedVersion = prefs.getWorkflowStepsVersion(lang)
        var bestBundle: WorkflowStepI18nBundle? = null

        if (!inFlightLangs.add(lang)) {
            return RefreshResult.UpToDate
        }
        try {
            for (fileLang in languagesToFetch(lang)) {
                val remote = fetchRemoteBundle(context, fileLang) ?: continue
                val current = bestBundle
                if (current == null || remote.version > current.version) {
                    bestBundle = remote
                }
            }

            if (bestBundle != null && bestBundle.version > storedVersion) {
                applyBundle(lang, bestBundle, persist = true)
                return RefreshResult.Updated
            }
            if (bestBundle != null && mapsByLang[lang].isNullOrEmpty()) {
                applyBundle(lang, bestBundle, persist = false)
                return RefreshResult.UpToDate
            }

            val cached = loadBundleFromCache(lang)
            if (cached != null && cached.steps.isNotEmpty()) {
                applyBundle(lang, cached, persist = false)
                return if (bestBundle == null) {
                    RefreshResult.UsedFallbackStaleRemote
                } else {
                    RefreshResult.UpToDate
                }
            }

            val assets = loadBundleFromAssets(lang)
            if (assets != null && assets.steps.isNotEmpty()) {
                applyBundle(lang, assets, persist = false)
                if (lang != FALLBACK_LANG) {
                    loadBundleFromAssets(FALLBACK_LANG)?.let { enBundle ->
                        if (enBundle.steps.isNotEmpty()) {
                            mapsByLang[FALLBACK_LANG] = enBundle.steps
                        }
                    }
                }
                return RefreshResult.UsedFallbackStaleRemote
            }

            if (mapsByLang[lang].isNullOrEmpty()) {
                return RefreshResult.Failed
            }
            return RefreshResult.UsedFallbackSilent
        } finally {
            inFlightLangs.remove(lang)
        }
    }

    private fun languagesToFetch(lang: String): List<String> =
        if (lang == FALLBACK_LANG) listOf(lang) else listOf(lang, FALLBACK_LANG)

    private fun fetchRemoteBundle(context: FetchContext, lang: String): WorkflowStepI18nBundle? {
        val urls = buildRemoteUrls(context, lang)
        for (url in urls) {
            val body = httpGet(url) ?: continue
            val bundle = parseJson(body) ?: continue
            if (bundle.steps.isNotEmpty()) return bundle
        }
        return null
    }

    private fun buildRemoteUrls(context: FetchContext, lang: String): List<String> {
        val path = "data/i18n/workflow-steps-$lang.json"
        val urls = mutableListOf<String>()
        val forkOwner = context.forkOwner?.trim().orEmpty()
        val forkName = context.forkName?.trim().orEmpty()
        val forkBranch = context.forkDefaultBranch?.trim().orEmpty()
        if (forkOwner.isNotEmpty() && forkName.isNotEmpty() && forkBranch.isNotEmpty()) {
            urls += rawGitUrl(forkOwner, forkName, forkBranch, path)
        }
        val upstreamBranch = context.upstreamDefaultBranch.trim().ifBlank {
            BuildConfig.SOURCE_REPO_DEFAULT_BRANCH
        }
        urls += rawGitUrl(
            BuildConfig.SOURCE_REPO_OWNER,
            BuildConfig.SOURCE_REPO_NAME,
            upstreamBranch,
            path,
        )
        return urls.distinct()
    }

    private fun rawGitUrl(owner: String, repo: String, branch: String, path: String): String =
        "https://raw.githubusercontent.com/$owner/$repo/$branch/$path"

    private fun httpGet(url: String): String? {
        return try {
            val request = Request.Builder().url(url).header("Accept", "application/json").build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                response.body?.string()
            }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun applyBundle(lang: String, bundle: WorkflowStepI18nBundle, persist: Boolean) {
        mapsByLang[lang] = bundle.steps
        if (persist) {
            saveBundleToCache(lang, bundle)
            prefs.setWorkflowStepsVersion(lang, bundle.version)
        }
        if (lang != FALLBACK_LANG && !mapsByLang.containsKey(FALLBACK_LANG)) {
            loadBundleFromAssets(FALLBACK_LANG)?.let { mapsByLang[FALLBACK_LANG] = it.steps }
        }
    }

    private fun preloadBundled(lang: String) {
        scope.launch {
            val bundle = loadBundleFromAssets(lang) ?: return@launch
            if (bundle.steps.isNotEmpty()) {
                mapsByLang.putIfAbsent(lang, bundle.steps)
            }
        }
    }

    private fun loadBundleFromAssets(lang: String): WorkflowStepI18nBundle? {
        return try {
            val raw = appContext.assets.open("$ASSET_DIR/workflow-steps-$lang.json")
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
            parseJson(raw)
        } catch (_: Exception) {
            null
        }
    }

    private fun loadBundleFromCache(lang: String): WorkflowStepI18nBundle? {
        val file = cacheFile(lang)
        if (!file.isFile) return null
        return try {
            parseJson(file.readText(Charsets.UTF_8))
        } catch (_: Exception) {
            null
        }
    }

    private fun saveBundleToCache(lang: String, bundle: WorkflowStepI18nBundle) {
        val file = cacheFile(lang)
        file.parentFile?.mkdirs()
        file.writeText(gson.toJson(WorkflowStepI18nDto(bundle.version, bundle.steps)), Charsets.UTF_8)
    }

    private fun cacheFile(lang: String): File =
        File(appContext.filesDir, "$CACHE_DIR/workflow-steps-$lang.json")

    fun parseJson(raw: String): WorkflowStepI18nBundle? {
        return try {
            val dto = gson.fromJson(raw, WorkflowStepI18nDto::class.java) ?: return null
            val version = dto.version
            if (version < 0) return null
            val steps = dto.steps.orEmpty()
                .mapKeys { it.key.trim() }
                .filter { (k, v) -> k.isNotEmpty() && v.isNotBlank() }
            WorkflowStepI18nBundle(version, steps)
        } catch (_: JsonSyntaxException) {
            null
        }
    }

    fun translateKey(bundle: WorkflowStepI18nBundle, zhKey: String): String {
        val trimmed = zhKey.trim()
        return bundle.steps[trimmed] ?: zhKey
    }

    @VisibleForTesting
    internal fun resetForTest() {
        mapsByLang.clear()
        inFlightLangs.clear()
    }

    @VisibleForTesting
    internal fun setStepsForTest(lang: String, steps: Map<String, String>) {
        mapsByLang[lang] = steps
    }

    data class FetchContext(
        val forkOwner: String?,
        val forkName: String?,
        val forkDefaultBranch: String?,
        val upstreamDefaultBranch: String,
    )

    sealed class RefreshResult {
        data object Updated : RefreshResult()
        data object UpToDate : RefreshResult()
        data object UsedFallbackSilent : RefreshResult()
        data object UsedFallbackStaleRemote : RefreshResult()
        data object Failed : RefreshResult()

        fun notifyStaleSnackbar(): Boolean =
            this is Failed || this is UsedFallbackStaleRemote
    }

    private data class WorkflowStepI18nDto(
        @SerializedName("version") val version: Int = 0,
        @SerializedName("steps") val steps: Map<String, String>? = null,
    )
}
