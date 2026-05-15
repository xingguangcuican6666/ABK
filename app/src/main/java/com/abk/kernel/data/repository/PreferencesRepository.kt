package com.abk.kernel.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "abk_prefs")

class PreferencesRepository(private val context: Context) {

    companion object {
        const val CURRENT_TERMS_VERSION = 1

        val KEY_ACCESS_TOKEN = stringPreferencesKey("github_access_token")
        val KEY_USERNAME = stringPreferencesKey("github_username")
        val KEY_AVATAR_URL = stringPreferencesKey("github_avatar_url")
        val KEY_FORK_REPO_NAME = stringPreferencesKey("fork_repo_name")
        val KEY_AUTO_DOWNLOAD = booleanPreferencesKey("auto_download")
        val KEY_NOTIFY_BUILD = booleanPreferencesKey("notify_build")
        val KEY_LAST_RUN_ID = longPreferencesKey("last_run_id")
        val KEY_THEME = stringPreferencesKey("theme_mode") // "system" | "light" | "dark"
        val KEY_DYNAMIC_COLOR_ENABLED = booleanPreferencesKey("dynamic_color_enabled")
        val KEY_CUSTOM_THEME_COLOR = intPreferencesKey("custom_theme_color_argb")
        val KEY_CUSTOM_ACCENT_COLOR = intPreferencesKey("custom_accent_color_argb")
        val KEY_CUSTOM_COLORS_USER_SET = booleanPreferencesKey("custom_colors_user_set")
        val KEY_CUSTOM_BACKGROUND_URI = stringPreferencesKey("custom_background_uri")
        val KEY_BACKGROUND_IMAGE_ENABLED = booleanPreferencesKey("background_image_enabled")
        val KEY_UI_SURFACE_ALPHA = floatPreferencesKey("ui_surface_alpha")
        val KEY_BUILD_CONFIG = stringPreferencesKey("build_config_json")
        val KEY_BUILD_PLANS = stringPreferencesKey("build_plans_json")
        val KEY_BUILD_QUEUE = stringPreferencesKey("build_queue_json")
        val KEY_MODULE_CATALOG_REPOSITORIES = stringPreferencesKey("module_catalog_repositories_json")
        val KEY_DOWNLOADED_ARTIFACTS = stringPreferencesKey("downloaded_artifacts_json")
        val KEY_REMOTE_ARTIFACTS = stringPreferencesKey("remote_artifacts_json")
        val KEY_BUILD_PARAMETER_SUMMARIES = stringPreferencesKey("build_parameter_summaries_json")
        val KEY_PENDING_AUTO_DOWNLOAD_RUN_ID = longPreferencesKey("pending_auto_download_run_id")
        val KEY_DOWNLOAD_MIRROR_BASE_URL = stringPreferencesKey("download_mirror_base_url")
        val KEY_PREBUILT_GKI_ENABLED = booleanPreferencesKey("prebuilt_gki_enabled")
        val KEY_PREDICTIVE_BACK_ENABLED = booleanPreferencesKey("predictive_back_enabled")
        val KEY_RUNTIME_NAVIGATION_ENABLED = booleanPreferencesKey("runtime_navigation_enabled")
        val KEY_WEBVIEW_DEBUG_ENABLED = booleanPreferencesKey("webview_debug_enabled")
        val KEY_TERMS_ACCEPTED_VERSION = intPreferencesKey("terms_accepted_version")
    }

    val accessToken: Flow<String?> = context.dataStore.data.map { it[KEY_ACCESS_TOKEN] }
    val username: Flow<String?> = context.dataStore.data.map { it[KEY_USERNAME] }
    val avatarUrl: Flow<String?> = context.dataStore.data.map { it[KEY_AVATAR_URL] }
    val forkRepoName: Flow<String?> = context.dataStore.data.map { it[KEY_FORK_REPO_NAME] }
    val autoDownload: Flow<Boolean> = context.dataStore.data.map { it[KEY_AUTO_DOWNLOAD] ?: true }
    val notifyBuild: Flow<Boolean> = context.dataStore.data.map { it[KEY_NOTIFY_BUILD] ?: true }
    val lastRunId: Flow<Long> = context.dataStore.data.map { it[KEY_LAST_RUN_ID] ?: -1L }
    val themeMode: Flow<String> = context.dataStore.data.map { it[KEY_THEME] ?: "dark" }
    val dynamicColorEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_DYNAMIC_COLOR_ENABLED] ?: true }
    val customThemeColorArgb: Flow<Int?> = context.dataStore.data.map { it[KEY_CUSTOM_THEME_COLOR] }
    val customAccentColorArgb: Flow<Int?> = context.dataStore.data.map { it[KEY_CUSTOM_ACCENT_COLOR] }
    val customBackgroundUri: Flow<String?> = context.dataStore.data.map { it[KEY_CUSTOM_BACKGROUND_URI] }
    val backgroundImageEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_BACKGROUND_IMAGE_ENABLED] ?: false }
    val uiSurfaceAlpha: Flow<Float> = context.dataStore.data.map { it[KEY_UI_SURFACE_ALPHA] ?: 1f }
    val buildConfigJson: Flow<String?> = context.dataStore.data.map { it[KEY_BUILD_CONFIG] }
    val buildPlansJson: Flow<String?> = context.dataStore.data.map { it[KEY_BUILD_PLANS] }
    val buildQueueJson: Flow<String?> = context.dataStore.data.map { it[KEY_BUILD_QUEUE] }
    val moduleCatalogRepositoriesJson: Flow<String?> = context.dataStore.data.map {
        it[KEY_MODULE_CATALOG_REPOSITORIES]
    }
    val downloadedArtifactsJson: Flow<String?> = context.dataStore.data.map { it[KEY_DOWNLOADED_ARTIFACTS] }
    val remoteArtifactsJson: Flow<String?> = context.dataStore.data.map { it[KEY_REMOTE_ARTIFACTS] }
    val buildParameterSummariesJson: Flow<String?> = context.dataStore.data.map { it[KEY_BUILD_PARAMETER_SUMMARIES] }
    val pendingAutoDownloadRunId: Flow<Long> = context.dataStore.data.map { it[KEY_PENDING_AUTO_DOWNLOAD_RUN_ID] ?: -1L }
    val downloadMirrorBaseUrl: Flow<String> = context.dataStore.data.map { it[KEY_DOWNLOAD_MIRROR_BASE_URL] ?: "" }
    val prebuiltGkiEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_PREBUILT_GKI_ENABLED] ?: true }
    val predictiveBackEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_PREDICTIVE_BACK_ENABLED] ?: true }
    val runtimeNavigationEnabled: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_RUNTIME_NAVIGATION_ENABLED] ?: false
    }
    val webViewDebugEnabled: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_WEBVIEW_DEBUG_ENABLED] ?: false
    }
    val termsAcceptedVersion: Flow<Int> = context.dataStore.data.map { it[KEY_TERMS_ACCEPTED_VERSION] ?: 0 }

    suspend fun saveToken(token: String) = context.dataStore.edit { it[KEY_ACCESS_TOKEN] = token }
    suspend fun saveUsername(name: String) = context.dataStore.edit { it[KEY_USERNAME] = name }
    suspend fun saveAvatarUrl(url: String) = context.dataStore.edit { it[KEY_AVATAR_URL] = url }
    suspend fun saveForkRepoName(name: String) = context.dataStore.edit { it[KEY_FORK_REPO_NAME] = name }
    suspend fun setAutoDownload(v: Boolean) = context.dataStore.edit { it[KEY_AUTO_DOWNLOAD] = v }
    suspend fun setNotifyBuild(v: Boolean) = context.dataStore.edit { it[KEY_NOTIFY_BUILD] = v }
    suspend fun saveLastRunId(id: Long) = context.dataStore.edit { it[KEY_LAST_RUN_ID] = id }
    suspend fun setThemeMode(mode: String) = context.dataStore.edit { it[KEY_THEME] = mode }
    suspend fun setDynamicColorEnabled(
        v: Boolean,
        snapshotThemeColorArgb: Int? = null,
        snapshotAccentColorArgb: Int? = null
    ) = context.dataStore.edit { preferences ->
        preferences[KEY_DYNAMIC_COLOR_ENABLED] = v
        if (!v && preferences[KEY_CUSTOM_COLORS_USER_SET] != true) {
            snapshotThemeColorArgb?.let { color -> preferences[KEY_CUSTOM_THEME_COLOR] = color }
            snapshotAccentColorArgb?.let { color -> preferences[KEY_CUSTOM_ACCENT_COLOR] = color }
        }
    }
    suspend fun setCustomThemeColors(themeColorArgb: Int, accentColorArgb: Int) = context.dataStore.edit { preferences ->
        preferences[KEY_CUSTOM_THEME_COLOR] = themeColorArgb
        preferences[KEY_CUSTOM_ACCENT_COLOR] = accentColorArgb
        preferences[KEY_CUSTOM_COLORS_USER_SET] = true
    }
    suspend fun setBackgroundImageUri(uri: String?) = context.dataStore.edit { preferences ->
        if (uri.isNullOrBlank()) {
            preferences.remove(KEY_CUSTOM_BACKGROUND_URI)
            preferences[KEY_BACKGROUND_IMAGE_ENABLED] = false
        } else {
            preferences[KEY_CUSTOM_BACKGROUND_URI] = uri
            preferences[KEY_BACKGROUND_IMAGE_ENABLED] = true
        }
    }
    suspend fun setBackgroundImageEnabled(v: Boolean) = context.dataStore.edit {
        it[KEY_BACKGROUND_IMAGE_ENABLED] = v
    }
    suspend fun setUiSurfaceAlpha(alpha: Float) = context.dataStore.edit {
        it[KEY_UI_SURFACE_ALPHA] = alpha.coerceIn(0f, 1f)
    }
    suspend fun saveBuildConfigJson(json: String) = context.dataStore.edit { it[KEY_BUILD_CONFIG] = json }
    suspend fun saveBuildPlansJson(json: String) = context.dataStore.edit { it[KEY_BUILD_PLANS] = json }
    suspend fun saveBuildQueueJson(json: String) = context.dataStore.edit { it[KEY_BUILD_QUEUE] = json }
    suspend fun saveModuleCatalogRepositoriesJson(json: String) = context.dataStore.edit {
        it[KEY_MODULE_CATALOG_REPOSITORIES] = json
    }
    suspend fun saveDownloadedArtifactsJson(json: String) = context.dataStore.edit { it[KEY_DOWNLOADED_ARTIFACTS] = json }
    suspend fun saveRemoteArtifactsJson(json: String) = context.dataStore.edit { it[KEY_REMOTE_ARTIFACTS] = json }
    suspend fun saveBuildParameterSummariesJson(json: String) = context.dataStore.edit {
        it[KEY_BUILD_PARAMETER_SUMMARIES] = json
    }
    suspend fun savePendingAutoDownloadRunId(id: Long) = context.dataStore.edit { it[KEY_PENDING_AUTO_DOWNLOAD_RUN_ID] = id }
    suspend fun setDownloadMirrorBaseUrl(url: String) = context.dataStore.edit { it[KEY_DOWNLOAD_MIRROR_BASE_URL] = url }
    suspend fun setPrebuiltGkiEnabled(v: Boolean) = context.dataStore.edit { it[KEY_PREBUILT_GKI_ENABLED] = v }
    suspend fun setPredictiveBackEnabled(v: Boolean) = context.dataStore.edit { it[KEY_PREDICTIVE_BACK_ENABLED] = v }
    suspend fun setRuntimeNavigationEnabled(v: Boolean) = context.dataStore.edit {
        it[KEY_RUNTIME_NAVIGATION_ENABLED] = v
    }
    suspend fun setWebViewDebugEnabled(v: Boolean) = context.dataStore.edit {
        it[KEY_WEBVIEW_DEBUG_ENABLED] = v
    }
    suspend fun acceptCurrentTerms() = context.dataStore.edit {
        it[KEY_TERMS_ACCEPTED_VERSION] = CURRENT_TERMS_VERSION
    }
    suspend fun clearPendingAutoDownloadRunId() = context.dataStore.edit { it.remove(KEY_PENDING_AUTO_DOWNLOAD_RUN_ID) }

    suspend fun clearAuth() = context.dataStore.edit {
        it.remove(KEY_ACCESS_TOKEN)
        it.remove(KEY_USERNAME)
        it.remove(KEY_AVATAR_URL)
        it.remove(KEY_FORK_REPO_NAME)
        it.remove(KEY_LAST_RUN_ID)
        it.remove(KEY_BUILD_QUEUE)
        it.remove(KEY_REMOTE_ARTIFACTS)
        it.remove(KEY_BUILD_PARAMETER_SUMMARIES)
        it.remove(KEY_PENDING_AUTO_DOWNLOAD_RUN_ID)
    }
}
