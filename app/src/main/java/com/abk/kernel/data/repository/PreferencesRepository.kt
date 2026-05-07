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
        val KEY_BUILD_CONFIG = stringPreferencesKey("build_config_json")
        val KEY_DOWNLOADED_ARTIFACTS = stringPreferencesKey("downloaded_artifacts_json")
        val KEY_REMOTE_ARTIFACTS = stringPreferencesKey("remote_artifacts_json")
        val KEY_PENDING_AUTO_DOWNLOAD_RUN_ID = longPreferencesKey("pending_auto_download_run_id")
        val KEY_DOWNLOAD_MIRROR_BASE_URL = stringPreferencesKey("download_mirror_base_url")
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
    val buildConfigJson: Flow<String?> = context.dataStore.data.map { it[KEY_BUILD_CONFIG] }
    val downloadedArtifactsJson: Flow<String?> = context.dataStore.data.map { it[KEY_DOWNLOADED_ARTIFACTS] }
    val remoteArtifactsJson: Flow<String?> = context.dataStore.data.map { it[KEY_REMOTE_ARTIFACTS] }
    val pendingAutoDownloadRunId: Flow<Long> = context.dataStore.data.map { it[KEY_PENDING_AUTO_DOWNLOAD_RUN_ID] ?: -1L }
    val downloadMirrorBaseUrl: Flow<String> = context.dataStore.data.map { it[KEY_DOWNLOAD_MIRROR_BASE_URL] ?: "" }
    val termsAcceptedVersion: Flow<Int> = context.dataStore.data.map { it[KEY_TERMS_ACCEPTED_VERSION] ?: 0 }

    suspend fun saveToken(token: String) = context.dataStore.edit { it[KEY_ACCESS_TOKEN] = token }
    suspend fun saveUsername(name: String) = context.dataStore.edit { it[KEY_USERNAME] = name }
    suspend fun saveAvatarUrl(url: String) = context.dataStore.edit { it[KEY_AVATAR_URL] = url }
    suspend fun saveForkRepoName(name: String) = context.dataStore.edit { it[KEY_FORK_REPO_NAME] = name }
    suspend fun setAutoDownload(v: Boolean) = context.dataStore.edit { it[KEY_AUTO_DOWNLOAD] = v }
    suspend fun setNotifyBuild(v: Boolean) = context.dataStore.edit { it[KEY_NOTIFY_BUILD] = v }
    suspend fun saveLastRunId(id: Long) = context.dataStore.edit { it[KEY_LAST_RUN_ID] = id }
    suspend fun setThemeMode(mode: String) = context.dataStore.edit { it[KEY_THEME] = mode }
    suspend fun saveBuildConfigJson(json: String) = context.dataStore.edit { it[KEY_BUILD_CONFIG] = json }
    suspend fun saveDownloadedArtifactsJson(json: String) = context.dataStore.edit { it[KEY_DOWNLOADED_ARTIFACTS] = json }
    suspend fun saveRemoteArtifactsJson(json: String) = context.dataStore.edit { it[KEY_REMOTE_ARTIFACTS] = json }
    suspend fun savePendingAutoDownloadRunId(id: Long) = context.dataStore.edit { it[KEY_PENDING_AUTO_DOWNLOAD_RUN_ID] = id }
    suspend fun setDownloadMirrorBaseUrl(url: String) = context.dataStore.edit { it[KEY_DOWNLOAD_MIRROR_BASE_URL] = url }
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
        it.remove(KEY_REMOTE_ARTIFACTS)
        it.remove(KEY_PENDING_AUTO_DOWNLOAD_RUN_ID)
    }
}
