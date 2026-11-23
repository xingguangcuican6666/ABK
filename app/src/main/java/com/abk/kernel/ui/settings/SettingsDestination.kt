package com.abk.kernel.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.abk.kernel.BuildConfig
import com.abk.kernel.R
import com.abk.kernel.extensions.AbkExtensionManagerActivity
import com.abk.kernel.ui.common.AbkPage
import com.abk.kernel.ui.common.MutedText
import com.abk.kernel.utils.DownloadDirectoryUtils
import com.abk.kernel.viewmodel.MainUiState
import com.abk.kernel.viewmodel.MainViewModel
import io.github.xingguangcuican6666.abk.ui.components.AbkAlert
import io.github.xingguangcuican6666.abk.ui.components.AbkButton
import io.github.xingguangcuican6666.abk.ui.components.AbkButtonStyle
import io.github.xingguangcuican6666.abk.ui.components.AbkCard
import io.github.xingguangcuican6666.abk.ui.components.AbkDialog
import io.github.xingguangcuican6666.abk.ui.components.AbkDivider
import io.github.xingguangcuican6666.abk.ui.components.AbkHeading
import io.github.xingguangcuican6666.abk.ui.components.AbkLinearProgress
import io.github.xingguangcuican6666.abk.ui.components.AbkList
import io.github.xingguangcuican6666.abk.ui.components.AbkListItem
import io.github.xingguangcuican6666.abk.ui.components.AbkOption
import io.github.xingguangcuican6666.abk.ui.components.AbkSegmented
import io.github.xingguangcuican6666.abk.ui.components.AbkSettingSwitch
import io.github.xingguangcuican6666.abk.ui.components.AbkSize
import io.github.xingguangcuican6666.abk.ui.components.AbkSlider
import io.github.xingguangcuican6666.abk.ui.components.AbkText
import io.github.xingguangcuican6666.abk.ui.theme.AbkSpacing
import io.github.xingguangcuican6666.abk.ui.theme.AbkTone
import kotlinx.coroutines.launch

/**
 * 设置：外观、构建自动化、存储、账户与关于。
 * Advanced security screens (signing keys, KernelSU backend, root authorization)
 * arrive in the next phase; the theme switches here are live.
 */
@Composable
fun SettingsDestination(
    vm: MainViewModel,
    state: MainUiState,
    onChildPageVisibleChange: (Boolean) -> Unit,
) {
    LaunchedEffect(Unit) { onChildPageVisibleChange(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showLogoutDialog by rememberSaveable { mutableStateOf(false) }
    var showClearFilesDialog by rememberSaveable { mutableStateOf(false) }
    // Slider drags are noisy: show the live value and persist on release.
    var alphaDraft by remember(state.uiSurfaceAlpha) { mutableStateOf(state.uiSurfaceAlpha) }

    val pickBackground = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            vm.setBackgroundImageUri(uri.toString())
            vm.setBackgroundImageEnabled(true)
        }
    }

    AbkPage(
        title = stringResource(R.string.settings_title),
        eyebrow = stringResource(R.string.nav_settings),
        description = stringResource(R.string.settings_center_subtitle),
    ) {
        // ── Appearance ───────────────────────────────────────────────────
        AbkCard {
            AbkHeading(stringResource(R.string.settings_theme))
            AbkSegmented(
                options = listOf(
                    AbkOption("system", stringResource(R.string.settings_theme_system)),
                    AbkOption("light", stringResource(R.string.settings_theme_light)),
                    AbkOption("dark", stringResource(R.string.settings_theme_dark)),
                ),
                selectedId = state.themeMode,
                onSelect = vm::setThemeMode,
            )
            AbkDivider()
            AbkSettingSwitch(
                title = stringResource(R.string.settings_monet),
                description = stringResource(R.string.settings_monet_desc),
                checked = state.dynamicColorEnabled,
                onCheckedChange = { vm.setDynamicColorEnabled(it) },
            )
            AbkDivider()
            AbkSettingSwitch(
                title = stringResource(R.string.settings_custom_background),
                description = stringResource(R.string.settings_custom_background_desc),
                checked = state.backgroundImageEnabled && !state.customBackgroundUri.isNullOrBlank(),
                onCheckedChange = { enabled ->
                    if (enabled && state.customBackgroundUri.isNullOrBlank()) {
                        pickBackground.launch("image/*")
                    } else {
                        vm.setBackgroundImageEnabled(enabled)
                    }
                },
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AbkSpacing.Sm)) {
                AbkButton(
                    onClick = { pickBackground.launch("image/*") },
                    style = AbkButtonStyle.Secondary,
                    size = AbkSize.Small,
                ) {
                    AbkText(
                        stringResource(
                            if (state.customBackgroundUri.isNullOrBlank()) R.string.settings_choose_background
                            else R.string.settings_change_background
                        )
                    )
                }
                if (!state.customBackgroundUri.isNullOrBlank()) {
                    AbkButton(
                        onClick = {
                            vm.setBackgroundImageUri(null)
                            vm.setBackgroundImageEnabled(false)
                        },
                        style = AbkButtonStyle.Ghost,
                        size = AbkSize.Small,
                    ) { AbkText(stringResource(R.string.settings_remove_background)) }
                }
            }
            AbkSlider(
                value = alphaDraft,
                onValueChange = { alphaDraft = it },
                label = stringResource(R.string.settings_ui_opacity),
                valueRange = 0.3f..1f,
                valueLabel = "${(alphaDraft * 100).toInt()}%",
                onValueChangeFinished = { vm.setUiSurfaceAlpha(alphaDraft) },
            )
            MutedText(stringResource(R.string.settings_ui_opacity_desc))
        }

        // ── Build automation ────────────────────────────────────────────
        AbkCard {
            AbkHeading(stringResource(R.string.settings_build))
            AbkSettingSwitch(
                title = stringResource(R.string.settings_auto_download),
                description = stringResource(R.string.settings_auto_download_desc),
                checked = state.autoDownload,
                onCheckedChange = vm::setAutoDownload,
            )
            AbkDivider()
            AbkSettingSwitch(
                title = stringResource(R.string.settings_prebuilt_gki),
                description = stringResource(R.string.settings_prebuilt_gki_desc),
                checked = state.prebuiltGkiEnabled,
                onCheckedChange = vm::setPrebuiltGkiEnabled,
            )
            AbkDivider()
            AbkSettingSwitch(
                title = stringResource(R.string.settings_predictive_back),
                description = stringResource(R.string.settings_predictive_back_desc),
                checked = state.predictiveBackEnabled,
                onCheckedChange = vm::setPredictiveBackEnabled,
            )
        }

        // ── Notifications ───────────────────────────────────────────────
        AbkCard {
            AbkHeading(stringResource(R.string.settings_notification))
            AbkSettingSwitch(
                title = stringResource(R.string.settings_notify_build),
                description = stringResource(R.string.settings_notify_build_desc),
                checked = state.notifyBuild,
                onCheckedChange = vm::setNotifyBuild,
            )
        }

        // ── Storage ─────────────────────────────────────────────────────
        AbkList {
            AbkListItem(
                title = stringResource(R.string.settings_download_directory),
                description = state.downloadDirectory.ifBlank { DownloadDirectoryUtils.defaultDirectoryPath() },
                onClick = { vm.setDownloadDirectory(DownloadDirectoryUtils.defaultDirectoryPath()) },
            )
            AbkDivider()
            AbkListItem(
                title = stringResource(R.string.settings_clear_artifacts),
                description = stringResource(R.string.settings_clear_artifacts_files, state.downloadedArtifacts.size),
                onClick = { if (state.downloadedArtifacts.isNotEmpty()) showClearFilesDialog = true },
                enabled = state.downloadedArtifacts.isNotEmpty(),
            )
            AbkDivider()
            // Thread count is numeric, not on/off; the item steps between single and multi connection.
            AbkListItem(
                title = stringResource(R.string.settings_download_threads),
                description = stringResource(R.string.settings_download_threads_desc, state.downloadThreadCount),
                onClick = {
                    val next = if (state.downloadThreadCount <= 1) 8 else 1
                    vm.setDownloadThreadCount(next)
                },
            )
        }

        // ── Account ─────────────────────────────────────────────────────
        AbkCard {
            AbkHeading(stringResource(R.string.settings_account))
            if (state.isLoggedIn && state.user != null) {
                MutedText(stringResource(R.string.settings_connected_github, state.user.login))
                state.forkRepo?.let { MutedText(stringResource(R.string.settings_fork_repo) + " · " + it.name) }
                AbkButton(
                    onClick = { showLogoutDialog = true },
                    style = AbkButtonStyle.Outline,
                ) { AbkText(stringResource(R.string.logout)) }
            } else {
                MutedText(stringResource(R.string.settings_not_logged_in))
                MutedText(stringResource(R.string.settings_login_hint))
                AbkButton(
                    onClick = { vm.openLoginOobe() },
                    style = AbkButtonStyle.Primary,
                ) { AbkText(stringResource(R.string.login_github)) }
            }
        }

        AbkList {
            AbkListItem(
                title = stringResource(R.string.settings_extensions_manage),
                description = stringResource(R.string.settings_extensions_manage_desc),
                onClick = { context.startActivity(Intent(context, AbkExtensionManagerActivity::class.java)) },
            )
            AbkDivider()
            AbkListItem(
                title = stringResource(R.string.settings_app_update),
                description = state.appUpdateInfo?.remote?.versionName?.takeIf { it.isNotBlank() }
                    ?.let { stringResource(R.string.settings_app_update_result_short, it) }
                    ?: stringResource(R.string.settings_group_app_update_desc),
                onClick = { vm.checkAppUpdate() },
                enabled = !state.appUpdateChecking,
            )
            AbkDivider()
            AbkListItem(
                title = stringResource(R.string.settings_source_repo),
                description = BuildConfig.VERSION_NAME,
                onClick = { vm.checkAppUpdate() },
            )
        }

        if (state.appUpdateChecking) {
            AbkLinearProgress(progress = null, label = stringResource(R.string.settings_app_update_checking))
        }
        state.appUpdateDownloadProgress.takeIf { state.appUpdateDownloading }?.let { progress ->
            AbkCard {
                MutedText(stringResource(R.string.settings_app_update_downloading_progress, progress))
                AbkLinearProgress(progress = progress / 100f, label = stringResource(R.string.settings_app_update))
                AbkButton(onClick = { vm.downloadAndInstallAppUpdate() }, style = AbkButtonStyle.Primary) {
                    AbkText(stringResource(R.string.settings_download_install_update))
                }
            }
        }
        state.appUpdateInfo?.let { info ->
            AbkAlert(
                title = stringResource(R.string.settings_app_update_available),
                message = stringResource(R.string.settings_app_update_result_short, info.remote.versionName),
                tone = AbkTone.Info,
            )
        }
        state.appUpdateError?.let { error ->
            AbkAlert(
                title = stringResource(R.string.settings_app_update_error),
                message = error,
                tone = AbkTone.Danger,
            )
        }

        MutedText("${stringResource(R.string.settings_about_title)} · ${BuildConfig.VERSION_NAME}")
    }

    if (showLogoutDialog) {
        AbkDialog(
            title = stringResource(R.string.settings_logout_title),
            onDismissRequest = { showLogoutDialog = false },
            actions = {
                AbkButton(onClick = { showLogoutDialog = false }, style = AbkButtonStyle.Ghost) {
                    AbkText(stringResource(R.string.cancel))
                }
                AbkButton(
                    onClick = {
                        showLogoutDialog = false
                        vm.logout()
                    },
                    style = AbkButtonStyle.Danger,
                ) { AbkText(stringResource(R.string.logout)) }
            },
        ) {
            AbkText(stringResource(R.string.settings_logout_message))
        }
    }

    if (showClearFilesDialog) {
        AbkDialog(
            title = stringResource(R.string.settings_clear_artifacts_title),
            onDismissRequest = { showClearFilesDialog = false },
            actions = {
                AbkButton(onClick = { showClearFilesDialog = false }, style = AbkButtonStyle.Ghost) {
                    AbkText(stringResource(R.string.cancel))
                }
                AbkButton(
                    onClick = {
                        showClearFilesDialog = false
                        vm.clearAllDownloadedArtifacts()
                    },
                    style = AbkButtonStyle.Danger,
                ) { AbkText(stringResource(R.string.settings_clear_artifacts_confirm)) }
            },
        ) {
            AbkText(stringResource(R.string.settings_clear_artifacts_message))
        }
    }
}
