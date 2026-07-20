package com.abk.kernel.miuix.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.abk.kernel.BuildConfig
import com.abk.kernel.R
import com.abk.kernel.data.model.APP_UPDATE_LINE_DEV
import com.abk.kernel.data.model.APP_UPDATE_LINE_NORMAL
import com.abk.kernel.data.model.APP_UPDATE_STABILITY_STABLE
import com.abk.kernel.data.model.APP_UPDATE_STABILITY_UNSTABLE
import com.abk.kernel.data.model.AppUpdateCheckResult
import com.abk.kernel.data.model.ManagerSettingKind
import com.abk.kernel.data.model.normalizeAppUpdateLine
import com.abk.kernel.data.model.normalizeAppUpdateStability
import com.abk.kernel.data.repository.PreferencesRepository
import com.abk.kernel.miuix.component.MiuixTextInputDialog
import com.abk.kernel.miuix.util.BlurredBar
import com.abk.kernel.miuix.util.rememberBlurBackdrop
import com.abk.kernel.utils.DownloadDirectoryUtils
import com.abk.kernel.utils.DownloadUtils
import com.abk.kernel.utils.LocaleHelper
import com.abk.kernel.ui.navigation3.LocalNavigator
import com.abk.kernel.ui.navigation3.Route
import com.abk.kernel.viewmodel.MainUiState
import com.abk.kernel.viewmodel.MainViewModel
import androidx.core.content.FileProvider
import com.abk.kernel.viewmodel.exportDiagnosticBundle
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.text.Charsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Alignment
import com.abk.kernel.data.repository.Result
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.window.WindowDialog
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * MIUIX-styled settings screen for ABK.
 *
 * Covers all Material 3 settings sections in the same order and with the same
 * callback signatures as [com.abk.kernel.ui.screens.SettingsScreen]:
 * Account, Build, App Update, Manager Injected, Notification, Navigation,
 * Language, Theme, Extensions, About.
 *
 * Navigation to sub-pages (ThemeSettings, AppProfileTemplates, etc.) is
 * handled via [LocalNavigator] + [Route] (Navigation3 NavDisplay).
 * [onOpenInstalledModules] is kept because it performs cross-tab navigation
 * that lives outside the NavDisplay scope.
 */
@Composable
fun SettingsScreenMiuix(
    vm: MainViewModel,
    outerPadding: PaddingValues = PaddingValues(0.dp),
    onLogout: () -> Unit = {},
    onOpenInstalledModules: () -> Unit = {},
) {
    val state by vm.uiState.collectAsState()
    val scrollBehavior = MiuixScrollBehavior()
    val iconTint = MiuixTheme.colorScheme.onSurfaceSecondary
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var showLogoutDialog by remember { mutableStateOf(false) }
    var showClearArtifactsDialog by remember { mutableStateOf(false) }
    var exportingDiagnostics by remember { mutableStateOf(false) }

    // Refresh manager settings on first composition (mirrors MD3 LaunchedEffect).
    LaunchedEffect(Unit) {
        vm.refreshManagerSettings(force = true)
        vm.refreshSusfsState(force = true)
    }

    // Auto-install pending app update APK (mirrors MD3 LaunchedEffect).
    LaunchedEffect(state.appUpdatePendingInstallPath) {
        val apkPath = state.appUpdatePendingInstallPath ?: return@LaunchedEffect
        vm.consumeAppUpdatePendingInstallPath()
    }

    fun exportDiagnostics() {
        if (exportingDiagnostics) return
        scope.launch {
            exportingDiagnostics = true
            runCatching {
                exportDiagnosticBundle(context, state)
            }.onSuccess { result ->
                shareDiagnosticBundle(context, result.zipFile)
                if (result.warnings.isNotEmpty()) {
                    vm.showSnackbar(
                        context.getString(
                            R.string.settings_export_diagnostics_partial,
                            result.warnings.size
                        ),
                        longDuration = true
                    )
                }
            }.onFailure { error ->
                vm.showSnackbar(
                    context.getString(
                        R.string.settings_export_diagnostics_failed,
                        error.message ?: error::class.java.simpleName
                    ),
                    longDuration = true
                )
            }
            exportingDiagnostics = false
        }
    }

    // ── Main layout ────────────────────────────────────────────────────────
    Box(Modifier.fillMaxSize()) {
        // ── Logout confirmation dialog ──────────────────────────────────────
    
    if (showLogoutDialog) {
            WindowDialog(
                show = true,
                title = stringResource(R.string.settings_logout_title),
                onDismissRequest = { showLogoutDialog = false }
            ) {
                Column {
                    MiuixText(
                        text = stringResource(R.string.settings_logout_message),
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MiuixTextButton(
                            modifier = Modifier.weight(1f),
                            text = stringResource(android.R.string.cancel),
                            onClick = { showLogoutDialog = false }
                        )
                        Spacer(Modifier.width(20.dp))
                        MiuixTextButton(
                            modifier = Modifier.weight(1f),
                            text = stringResource(R.string.confirm),
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                            onClick = {
                                showLogoutDialog = false
                                vm.logout()
                                onLogout()
                            }
                        )
                    }
                }
            }
        }

        // ── Clear artifacts confirmation dialog ─────────────────────────────
        if (showClearArtifactsDialog) {
            WindowDialog(
                show = true,
                title = stringResource(R.string.settings_clear_artifacts_title),
                onDismissRequest = { showClearArtifactsDialog = false }
            ) {
                Column {
                    MiuixText(
                        text = stringResource(R.string.settings_clear_artifacts_message),
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MiuixTextButton(
                            modifier = Modifier.weight(1f),
                            text = stringResource(android.R.string.cancel),
                            onClick = { showClearArtifactsDialog = false }
                        )
                        Spacer(Modifier.width(20.dp))
                        MiuixTextButton(
                            modifier = Modifier.weight(1f),
                            text = stringResource(R.string.settings_clear_artifacts_confirm),
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                            onClick = {
                                vm.clearAllDownloadedArtifacts()
                                showClearArtifactsDialog = false
                            }
                        )
                    }
                }
            }
        }

        val surfaceColor = MiuixTheme.colorScheme.surface
        val backdrop = rememberBlurBackdrop(state.miuixBlurEnabled, surfaceColor)
        val barColor = if (backdrop != null) Color.Transparent else surfaceColor

        Scaffold(
            topBar = {
                BlurredBar(backdrop, surfaceColor) {
                    TopAppBar(
                        color = barColor,
                        title = stringResource(R.string.settings_title),
                        scrollBehavior = scrollBehavior
                    )
                }
            },
            contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
        ) { innerPadding ->
            val listState = rememberScrollState()
            Box(
                modifier = Modifier.then(
                    if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection)
                        .verticalScroll(listState)
                        .overScrollVertical()
                        .scrollEndHaptic()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Spacer(Modifier.height(innerPadding.calculateTopPadding() + 8.dp))

                // ═══════════════════════════════════════════════════════════
                // 1. ACCOUNT
                // ═══════════════════════════════════════════════════════════
                SectionTitle(stringResource(R.string.settings_account))
                Card(modifier = Modifier.fillMaxWidth()) {
                    state.user?.let { user ->
                        // User row: avatar + login + subtitle + logout button
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Avatar (MD3 uses 42.dp CircleShape AsyncImage)
                            AsyncImage(
                                model = user.avatarUrl,
                                contentDescription = user.login,
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                            )
                            // Name + subtitle
                            Column(modifier = Modifier.weight(1f)) {
                                top.yukonga.miuix.kmp.basic.Text(
                                    text = user.login,
                                    style = MiuixTheme.textStyles.main
                                )
                                top.yukonga.miuix.kmp.basic.Text(
                                    text = user.name ?: user.htmlUrl,
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                )
                            }
                            // Logout button (MD3 IconButton + error tint)
                            IconButton(onClick = { showLogoutDialog = true }) {
                                Icon(
                                    imageVector = Icons.Default.Logout,
                                    contentDescription = stringResource(R.string.settings_logout_desc),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        // Fork repository link (clickable)
                        val context = LocalContext.current
                        val forkUrl = state.forkRepo?.let { repo ->
                            repo.htmlUrl.takeIf { it.isNotBlank() }
                                ?: "https://github.com/${repo.fullName}"
                        }
                        ArrowPreference(
                            title = stringResource(R.string.settings_fork_repo),
                            summary = state.forkRepo?.fullName
                                ?: stringResource(R.string.settings_waiting_fork),
                            startAction = { Icon(Icons.Default.ForkRight, contentDescription = null, tint = iconTint) },
                            onClick = forkUrl?.let { url -> { openUrl(context, url) } }
                        )
                    } ?: run {
                        // Not logged in
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = null,
                                modifier = Modifier.size(42.dp),
                                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary
                            )
                            top.yukonga.miuix.kmp.basic.Text(
                                text = stringResource(R.string.settings_not_logged_in),
                                style = MiuixTheme.textStyles.main,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // ═══════════════════════════════════════════════════════════
                // 2. BUILD
                // ═══════════════════════════════════════════════════════════
                SectionTitle(stringResource(R.string.settings_build))
                Card(modifier = Modifier.fillMaxWidth()) {
                    // Foreground refresh switch
                    SwitchPreference(
                        title = stringResource(R.string.settings_workflow_foreground_refresh),
                        summary = stringResource(R.string.settings_workflow_foreground_refresh_desc),
                        startAction = { Icon(Icons.Default.Sync, contentDescription = null, tint = iconTint) },
                        checked = state.workflowForegroundRefreshEnabled,
                        onCheckedChange = { vm.setWorkflowForegroundRefreshEnabled(it) }
                    )
                    // Conditional interval picker (animated)
                    AnimatedVisibility(
                        visible = state.workflowForegroundRefreshEnabled,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        val intervalOptions = PreferencesRepository.WORKFLOW_FOREGROUND_REFRESH_INTERVALS_SEC.sorted()
                        val selectedIndex = intervalOptions.indexOf(state.workflowForegroundRefreshIntervalSec)
                            .takeIf { it >= 0 } ?: 1 // default to 20s (index 1)
                        OverlayDropdownPreference(
                            title = stringResource(R.string.settings_workflow_foreground_refresh_interval),
                            items = intervalOptions.map { stringResource(R.string.settings_workflow_foreground_refresh_interval_sec, it) },
                            selectedIndex = selectedIndex,
                            renderInRootScaffold = true,
                            onSelectedIndexChange = { index ->
                                vm.setWorkflowForegroundRefreshIntervalSec(intervalOptions[index])
                            }
                        )
                    }
                    // Auto download
                    SwitchPreference(
                        title = stringResource(R.string.settings_auto_download),
                        summary = stringResource(R.string.settings_auto_download_desc),
                        startAction = { Icon(Icons.Default.Download, contentDescription = null, tint = iconTint) },
                        checked = state.autoDownload,
                        onCheckedChange = { vm.setAutoDownload(it) }
                    )
                    // Prebuilt GKI
                    SwitchPreference(
                        title = stringResource(R.string.settings_prebuilt_gki),
                        summary = stringResource(R.string.settings_prebuilt_gki_desc),
                        startAction = { Icon(Icons.Default.CloudDownload, contentDescription = null, tint = iconTint) },
                        checked = state.prebuiltGkiEnabled,
                        onCheckedChange = { vm.setPrebuiltGkiEnabled(it) }
                    )
                    // Download directory
                    DownloadDirectoryItem(
                        value = state.downloadDirectory,
                        onValueChange = { vm.setDownloadDirectory(it) },
                        onFeedback = { vm.showSnackbar(it) },
                        leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null, tint = iconTint) }
                    )
                    // Mirror URL
                    MirrorUrlItem(
                        value = state.downloadMirrorBaseUrl,
                        onValueChange = { vm.setDownloadMirrorBaseUrl(it) },
                        leadingIcon = { Icon(Icons.Default.Link, contentDescription = null, tint = iconTint) }
                    )
                    // Clear artifacts
                    val hasArtifacts = state.downloadedArtifacts.isNotEmpty()
                    ArrowPreference(
                        title = stringResource(R.string.settings_clear_artifacts),
                        summary = if (hasArtifacts) {
                            val count = state.downloadedArtifacts.size
                            val totalBytes = state.downloadedArtifacts.sumOf { it.sizeBytes }
                            "$count ${stringResource(R.string.settings_clear_artifacts_files)} · ${DownloadUtils.formatSize(totalBytes)}"
                        } else {
                            stringResource(R.string.settings_clear_artifacts_empty)
                        },
                        startAction = { Icon(Icons.Default.Delete, contentDescription = null, tint = iconTint) },
                        onClick = if (hasArtifacts) {
                            { showClearArtifactsDialog = true }
                        } else null
                    )
                }

                // ═══════════════════════════════════════════════════════════
                // 3. SECURITY
                // ═══════════════════════════════════════════════════════════
                SecuritySettingsGroupMiuix(state = state, vm = vm, iconTint = iconTint)

                // ═══════════════════════════════════════════════════════════
                // 4. APP UPDATE
                // ═══════════════════════════════════════════════════════════
                SectionTitle(stringResource(R.string.settings_app_update))
                Card(modifier = Modifier.fillMaxWidth()) {
                    // Stability picker — OverlayDropdown
                    val stabilityOptions = listOf(
                        APP_UPDATE_STABILITY_STABLE to stringResource(R.string.settings_app_update_stable),
                        APP_UPDATE_STABILITY_UNSTABLE to stringResource(R.string.settings_app_update_unstable)
                    )
                    val stabilityIndex = stabilityOptions.indexOfFirst {
                        it.first == normalizeAppUpdateStability(state.appUpdateStability)
                    }.takeIf { it >= 0 } ?: 0
                    OverlayDropdownPreference(
                        title = stringResource(R.string.settings_app_update_stability),
                        items = stabilityOptions.map { it.second },
                        selectedIndex = stabilityIndex,
                        renderInRootScaffold = true,
                        onSelectedIndexChange = { index ->
                            vm.setAppUpdateStability(stabilityOptions[index].first)
                        }
                    )
                    // Line picker — OverlayDropdown
                    val lineOptions = listOf(
                        APP_UPDATE_LINE_NORMAL to stringResource(R.string.settings_app_update_line_normal),
                        APP_UPDATE_LINE_DEV to stringResource(R.string.settings_app_update_line_dev)
                    )
                    val lineIndex = lineOptions.indexOfFirst {
                        it.first == normalizeAppUpdateLine(state.appUpdateLine)
                    }.takeIf { it >= 0 } ?: 0
                    OverlayDropdownPreference(
                        title = stringResource(R.string.settings_app_update_line),
                        items = lineOptions.map { it.second },
                        selectedIndex = lineIndex,
                        renderInRootScaffold = true,
                        onSelectedIndexChange = { index ->
                            vm.setAppUpdateLine(lineOptions[index].first)
                        }
                    )
                    // Check for update
                    ArrowPreference(
                        title = stringResource(R.string.settings_check_app_update),
                        summary = appUpdateCheckSubtitle(state),
                        endActions = {
                            if (state.appUpdateChecking) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = MiuixTheme.colorScheme.primary
                                )
                            }
                        },
                        onClick = { if (!state.appUpdateChecking) vm.checkAppUpdate() }
                    )
                    // Update info display (merged: available-status + download-install action)
                    AnimatedVisibility(visible = state.appUpdateInfo != null) {
                        state.appUpdateInfo?.let { info ->
                            val downloadUrl = info.remote.downloadUrl
                            ArrowPreference(
                                title = if (info.hasUpdate) {
                                    stringResource(R.string.settings_app_update_available)
                                } else {
                                    stringResource(R.string.settings_app_update_latest)
                                },
                                summary = when {
                                    state.appUpdateDownloading -> stringResource(
                                        R.string.settings_app_update_downloading_progress,
                                        state.appUpdateDownloadProgress
                                    )
                                    info.hasUpdate -> {
                                        if (downloadUrl.isBlank()) stringResource(R.string.settings_app_update_link_missing) else downloadUrl
                                    }
                                    else -> appUpdateResultSubtitle(info)
                                },
                                startAction = {
                                    Icon(
                                        imageVector = if (info.hasUpdate) Icons.Default.Download else Icons.Default.Verified,
                                        contentDescription = null,
                                        tint = iconTint
                                    )
                                },
                                endActions = {
                                    if (info.hasUpdate) {
                                        if (state.appUpdateDownloading) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(22.dp),
                                                color = MiuixTheme.colorScheme.primary
                                            )
                                        } else {
                                            Icon(Icons.Default.Download, contentDescription = null, tint = iconTint)
                                        }
                                    }
                                },
                                onClick = if (info.hasUpdate && downloadUrl.isNotBlank()) {
                                    { vm.downloadAndInstallAppUpdate() }
                                } else null
                            )
                        }
                    }
                    // Error display
                    state.appUpdateError?.takeIf { it.isNotBlank() }?.let { error ->
                        ArrowPreference(
                            title = stringResource(R.string.settings_app_update_error),
                            summary = error,
                            startAction = { Icon(Icons.Default.Error, contentDescription = null, tint = iconTint) }
                        )
                    }
                }

                // ═══════════════════════════════════════════════════════════
                // 4. MANAGER INJECTED SETTINGS (conditional)
                // ═══════════════════════════════════════════════════════════
                if (state.hasNativeManagerPermission) {
                    val hasInjectedSettings = state.managerSettingsItems.isNotEmpty()
                    if (hasInjectedSettings ||
                        state.managerSettingsLoading ||
                        state.managerSettingsError != null
                    ) {
                        SectionTitle(
                            state.managerSettingsTitle.ifBlank {
                                stringResource(R.string.settings_manager_settings)
                            }
                        )
                        Card(modifier = Modifier.fillMaxWidth()) {
                            // Loading state
                            if (state.managerSettingsLoading && !hasInjectedSettings) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MiuixTheme.colorScheme.primary)
                                    Column {
                                        top.yukonga.miuix.kmp.basic.Text(
                                            text = stringResource(R.string.settings_manager_loading_title),
                                    style = MiuixTheme.textStyles.main
                                        )
                                        top.yukonga.miuix.kmp.basic.Text(
                                            text = stringResource(R.string.settings_manager_loading_desc),
                                            style = MiuixTheme.textStyles.body2,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                        )
                                    }
                                }
                            }
                            // Error state
                            state.managerSettingsError?.let { error ->
                                ArrowPreference(
                                    title = stringResource(R.string.settings_manager_load_failed),
                                    summary = error,
                                    onClick = { vm.refreshManagerSettings(force = true) }
                                )
                            }
                            // Render each item based on kind
                            state.managerSettingsItems.forEach { item ->
                                val actionInFlight = state.managerSettingActionId == item.id
                                when (item.kind) {
                                    ManagerSettingKind.NAVIGATION -> ArrowPreference(
                                        title = item.title,
                                        summary = item.subtitle,
                                        startAction = { Icon(managerSettingIcon(item.id), contentDescription = null, tint = iconTint) },
                                        onClick = if (item.enabled && !actionInFlight) {
                                            {
                                                when (item.id) {
                                                    "app_profile_templates" -> navigator.push(Route.AppProfileTemplates)
                                                    "manager_tools" -> navigator.push(Route.ManagerTools)
                                                    "kpm" -> onOpenInstalledModules()
                                                    "susfs_control" -> {
                                                        vm.refreshSusfsState(force = true)
                                                        navigator.push(Route.SusfsControl)
                                                    }
                                                }
                                            }
                                        } else null
                                    )
                                    ManagerSettingKind.SWITCH -> SwitchPreference(
                                        title = item.title,
                                        summary = item.subtitle,
                                        startAction = { Icon(managerSettingIcon(item.id), contentDescription = null, tint = iconTint) },
                                        checked = item.checked,
                                        onCheckedChange = { checked ->
                                            if (item.enabled && !actionInFlight) {
                                                vm.setManagerSettingChecked(item.id, checked)
                                            }
                                        }
                                    )
                                    ManagerSettingKind.MODE -> {
                                        val options = item.options
                                            .map { it.trim() }
                                            .filter { it.isNotBlank() }
                                        val selectedIndex = if (options.isNotEmpty()) {
                                            item.selectedIndex.coerceIn(0, options.lastIndex)
                                        } else 0
                                        OverlayDropdownPreference(
                                            title = item.title,
                                            summary = item.subtitle,
                                            startAction = { Icon(managerSettingIcon(item.id), contentDescription = null, tint = iconTint) },
                                            items = options,
                                            selectedIndex = selectedIndex,
                                            renderInRootScaffold = true,
                                            onSelectedIndexChange = { index ->
                                                if (item.enabled && !actionInFlight) {
                                                    vm.setManagerSettingMode(item.id, index)
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                val susfsAvailable = state.susfsRuntimeStatus?.available == true
                if (susfsAvailable) {
                    SectionTitle(stringResource(R.string.susfs_title))
                    Card(modifier = Modifier.fillMaxWidth()) {
                        val runtime = state.susfsRuntimeStatus!!
                        ArrowPreference(
                            title = stringResource(R.string.susfs_title),
                            summary = stringResource(
                                R.string.settings_susfs_control_summary,
                                runtime.kernelVersion,
                                runtime.bundledBinaryVersion
                            ),
                            startAction = {
                                Icon(
                                    imageVector = Icons.Default.Extension,
                                    contentDescription = null,
                                    tint = iconTint
                                )
                            },
                            onClick = {
                                vm.refreshSusfsState(force = true)
                                navigator.push(Route.SusfsControl)
                            }
                        )
                    }
                }

                // ═══════════════════════════════════════════════════════════
                // 6. NOTIFICATION
                // ═══════════════════════════════════════════════════════════
                SectionTitle(stringResource(R.string.settings_notification))
                Card(modifier = Modifier.fillMaxWidth()) {
                    SwitchPreference(
                        title = stringResource(R.string.settings_notify_build),
                        summary = stringResource(R.string.settings_notify_build_desc),
                        startAction = { Icon(Icons.Default.Notifications, contentDescription = null, tint = iconTint) },
                        checked = state.notifyBuild,
                        onCheckedChange = { vm.setNotifyBuild(it) }
                    )
                }

                // ═══════════════════════════════════════════════════════════
                // 6. LANGUAGE
                // ═══════════════════════════════════════════════════════════
                SectionTitle(stringResource(R.string.settings_language))
                Card(modifier = Modifier.fillMaxWidth()) {
                    val langCtx = LocalContext.current
                    val languageOptions = listOf(
                        LocaleHelper.LANG_ZH to stringResource(R.string.settings_language_zh),
                        LocaleHelper.LANG_EN to stringResource(R.string.settings_language_en),
                        LocaleHelper.LANG_RU to stringResource(R.string.settings_language_ru)
                    )
                    val currentLang = LocaleHelper.getLanguage(langCtx)
                    val languageIndex = languageOptions.indexOfFirst {
                        it.first == currentLang
                    }.takeIf { it >= 0 } ?: 0
                    OverlayDropdownPreference(
                        title = stringResource(R.string.settings_language),
                        items = languageOptions.map { it.second },
                        selectedIndex = languageIndex,
                        renderInRootScaffold = true,
                        onSelectedIndexChange = { index ->
                            val lang = languageOptions[index].first
                            LocaleHelper.setLanguage(langCtx, lang)
                            vm.onUiLanguageChanged()
                            (langCtx as? Activity)?.recreate()
                        }
                    )
                }

                // ═══════════════════════════════════════════════════════════
                // 7. THEME
                // ═══════════════════════════════════════════════════════════
                SectionTitle(stringResource(R.string.settings_theme))
                Card(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = stringResource(R.string.settings_color_appearance),
                        summary = "${themeModeLabel(state.themeMode)} · ${dynamicColorLabel(state.dynamicColorEnabled)}",
                        startAction = { Icon(Icons.Default.Palette, contentDescription = null, tint = iconTint) },
                        onClick = { navigator.push(Route.ThemeSettings) }
                    )
                }

                // ═══════════════════════════════════════════════════════════
                // 8. EXTENSIONS
                // ═══════════════════════════════════════════════════════════
                SectionTitle(stringResource(R.string.settings_extensions_title))
                Card(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = stringResource(R.string.settings_extensions_manage),
                        summary = stringResource(R.string.settings_extensions_manage_desc),
                        startAction = { Icon(Icons.Default.Extension, contentDescription = null, tint = iconTint) },
                        onClick = { navigator.push(Route.ExtensionManager) }
                    )
                }

                // ═══════════════════════════════════════════════════════════
                // 9. ABOUT
                // ═══════════════════════════════════════════════════════════
                SectionTitle(stringResource(R.string.settings_about))
                Card(modifier = Modifier.fillMaxWidth()) {
                    BasicComponent(
                        title = stringResource(R.string.app_full_name),
                        summary = "${stringResource(R.string.app_full_name)} v${BuildConfig.VERSION_NAME}",
                        startAction = { Icon(Icons.Default.Info, contentDescription = null, tint = iconTint) }
                    )
                    ArrowPreference(
                        title = stringResource(R.string.settings_about),
                        summary = stringResource(R.string.settings_about_desc),
                        startAction = { Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = iconTint) },
                        onClick = { navigator.push(Route.About) }
                    )
                    ArrowPreference(
                        title = stringResource(R.string.settings_open_source_licenses),
                        summary = stringResource(R.string.settings_open_source_licenses_desc),
                        startAction = { Icon(Icons.Default.Article, contentDescription = null, tint = iconTint) },
                        onClick = { navigator.push(Route.OpenSourceLicenses) }
                    )
                    ArrowPreference(
                        title = stringResource(R.string.settings_export_diagnostics),
                        summary = stringResource(R.string.settings_export_diagnostics_desc),
                        startAction = {
                            if (exportingDiagnostics) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            } else {
                                Icon(Icons.Default.BugReport, contentDescription = null, tint = iconTint)
                            }
                        },
                        onClick = { exportDiagnostics() }
                    )
                }

                Spacer(Modifier.height(60.dp + outerPadding.calculateBottomPadding()))
            }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Private helper composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionTitle(title: String) {
    top.yukonga.miuix.kmp.basic.Text(
        text = title,
        style = MiuixTheme.textStyles.subtitle,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp)
    )
}



/** Download directory selector — uses OpenDocumentTree + MIUIX display. */
@Composable
private fun DownloadDirectoryItem(
    value: String,
    onValueChange: (String) -> Unit,
    onFeedback: (String) -> Unit,
    leadingIcon: @Composable (() -> Unit)? = null
) {
    val context = LocalContext.current
    val defaultDirectory = remember { DownloadDirectoryUtils.defaultDirectoryPath() }
    var showEditor by remember { mutableStateOf(false) }
    val needsAllFilesAccess = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
        !Environment.isExternalStorageManager()
    val unsupportedTreeMessage = stringResource(R.string.settings_download_directory_tree_unsupported)
    val restoredMessage = stringResource(R.string.settings_download_directory_default_restored)
    val permissionNeededMessage = stringResource(R.string.settings_download_directory_storage_permission)
    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            val selectedPath = DownloadDirectoryUtils.directoryPathFromTreeUri(uri)
            if (selectedPath == null) {
                onFeedback(unsupportedTreeMessage)
            } else {
                onValueChange(selectedPath)
                showEditor = false
            }
        }
    }

    if (showEditor) {
        WindowDialog(
            show = true,
            title = stringResource(R.string.settings_download_directory),
            onDismissRequest = { showEditor = false },
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                MiuixText(
                    text = stringResource(R.string.settings_download_directory_desc),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MiuixTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(17.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = MiuixTheme.colorScheme.primary,
                            shape = RoundedCornerShape(17.dp)
                        )
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    MiuixText(
                        text = value.ifEmpty { defaultDirectory },
                        style = MiuixTheme.textStyles.body1,
                        color = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MiuixTextButton(
                        text = stringResource(R.string.settings_download_directory_choose),
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        onClick = { folderPicker.launch(null) },
                    )
                    MiuixTextButton(
                        text = stringResource(R.string.settings_download_directory_reset),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onValueChange(defaultDirectory)
                            onFeedback(restoredMessage)
                        },
                    )
                }
                if (needsAllFilesAccess) {
                    Spacer(Modifier.height(8.dp))
                    MiuixTextButton(
                        text = permissionNeededMessage,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { openAllFilesAccessSettings(context) },
                    )
                }
            }
        }
    }

    ArrowPreference(
        title = stringResource(R.string.settings_download_directory),
        summary = value.ifEmpty { defaultDirectory },
        startAction = leadingIcon,
        onClick = { showEditor = true },
    )
}

/** Mirror URL text field. */
@Composable
private fun MirrorUrlItem(
    value: String,
    onValueChange: (String) -> Unit,
    leadingIcon: @Composable (() -> Unit)? = null
) {
    var showEditor by remember { mutableStateOf(false) }
    val title = stringResource(R.string.settings_download_mirror)
    val description = stringResource(R.string.settings_download_mirror_desc)

    if (showEditor) {
        MiuixTextInputDialog(
            show = true,
            title = title,
            message = description,
            value = value,
            cancelText = stringResource(android.R.string.cancel),
            confirmText = stringResource(R.string.confirm),
            onDismiss = { showEditor = false },
            onConfirm = { mirrorUrl ->
                onValueChange(mirrorUrl.trim())
                showEditor = false
            },
        )
    }

    ArrowPreference(
        title = title,
        summary = value.ifBlank { description },
        startAction = leadingIcon,
        onClick = { showEditor = true },
    )
}


// ─────────────────────────────────────────────────────────────────────────────
// Private label helpers
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun themeModeLabel(mode: String): String = when (mode) {
    "light" -> stringResource(R.string.settings_theme_light)
    "dark" -> stringResource(R.string.settings_theme_dark)
    else -> stringResource(R.string.settings_theme_system)
}

@Composable
private fun dynamicColorLabel(enabled: Boolean): String = when {
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> stringResource(R.string.settings_dynamic_color_unavailable)
    enabled -> stringResource(R.string.settings_dynamic_color)
    else -> stringResource(R.string.settings_custom)
}

// ─────────────────────────────────────────────────────────────────────────────
// App update subtitle helpers (mirrored from MD3 SettingsScreen.kt)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun appUpdateCheckSubtitle(state: MainUiState): String = when {
    state.appUpdateChecking -> stringResource(R.string.settings_app_update_checking)
    state.appUpdateInfo != null -> appUpdateResultSubtitle(state.appUpdateInfo)
    state.appUpdateError?.isNotBlank() == true -> state.appUpdateError
    else -> stringResource(
        R.string.settings_app_update_desc,
        appUpdateStabilityLabel(state.appUpdateStability),
        appUpdateLineLabel(state.appUpdateLine)
    )
}

@Composable
private fun appUpdateResultSubtitle(info: AppUpdateCheckResult): String {
    val status = if (info.hasUpdate) {
        stringResource(R.string.settings_app_update_status_available)
    } else {
        stringResource(R.string.settings_app_update_status_latest)
    }
    val publishedAt = info.remote.publishedAt.ifBlank {
        stringResource(R.string.settings_unknown)
    }
    return stringResource(
        R.string.settings_app_update_result,
        info.currentVersionName,
        info.remote.versionName,
        appUpdateStabilityLabel(info.stability),
        appUpdateLineLabel(info.line),
        publishedAt,
        status
    )
}

@Composable
private fun appUpdateStabilityLabel(value: String): String =
    when (normalizeAppUpdateStability(value)) {
        APP_UPDATE_STABILITY_UNSTABLE -> stringResource(R.string.settings_app_update_unstable)
        else -> stringResource(R.string.settings_app_update_stable)
    }

@Composable
private fun appUpdateLineLabel(value: String): String =
    when (normalizeAppUpdateLine(value)) {
        APP_UPDATE_LINE_DEV -> stringResource(R.string.settings_app_update_line_dev)
        else -> stringResource(R.string.settings_app_update_line_normal)
    }

// ─────────────────────────────────────────────────────────────────────────────
// Manager setting icon mapping (mirrored from MD3 SettingsScreen.kt)
// ─────────────────────────────────────────────────────────────────────────────

private fun managerSettingIcon(id: String) = when (id) {
    "app_profile_templates" -> Icons.Default.Apps
    "manager_tools" -> Icons.Default.Build
    "kpm" -> Icons.Default.Extension
    "susfs_control" -> Icons.Default.Extension
    "su_compat" -> Icons.Default.RemoveModerator
    "kernel_umount" -> Icons.Default.RemoveCircle
    "adb_root" -> Icons.Default.Adb
    "sulog" -> Icons.Default.Article
    "selinux_hide" -> Icons.Default.Shield
    "default_umount_modules" -> Icons.Default.FolderDelete
    "webview_debug" -> Icons.Default.Code
    else -> Icons.Default.Settings
}



private fun shareDiagnosticBundle(context: Context, zipFile: File) {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        zipFile
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/zip"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, zipFile.name)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(
        Intent.createChooser(intent, context.getString(R.string.settings_export_diagnostics_share))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}


private enum class SecurityKeyImportTargetMiuix {
    PUBLIC,
    PRIVATE,
}

@Composable
private fun SecuritySettingsGroupMiuix(
    state: MainUiState,
    vm: MainViewModel,
    iconTint: Color,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val canManageKeys = state.isLoggedIn && state.forkRepo != null
    var showImportDialog by remember { mutableStateOf(false) }
    var showDisableConfirm1 by remember { mutableStateOf(false) }
    var showDisableConfirm2 by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var importPublicKeyText by remember { mutableStateOf("") }
    var importPrivateKeyText by remember { mutableStateOf("") }
    var importError by remember { mutableStateOf<String?>(null) }
    var importPickerTarget by remember { mutableStateOf<SecurityKeyImportTargetMiuix?>(null) }
    val importKeyPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val target = importPickerTarget ?: return@rememberLauncherForActivityResult
        importPickerTarget = null
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val text = try {
                readTextFromUri(context, uri)
            } catch (_: Throwable) {
                importError = context.getString(R.string.settings_security_import_read_failed)
                return@launch
            }
            when (target) {
                SecurityKeyImportTargetMiuix.PUBLIC -> importPublicKeyText = text
                SecurityKeyImportTargetMiuix.PRIVATE -> importPrivateKeyText = text
            }
            importError = null
        }
    }

    SectionTitle(stringResource(R.string.settings_security))
    Card(modifier = Modifier.fillMaxWidth()) {
        SwitchPreference(
            title = stringResource(R.string.settings_security_signing_title),
            summary = when {
                !canManageKeys -> stringResource(R.string.settings_security_requires_fork)
                state.artifactSigningVerificationEnabled && state.artifactSigningConfigured ->
                    stringResource(R.string.settings_security_status_enabled_configured)
                state.artifactSigningVerificationEnabled ->
                    stringResource(R.string.settings_security_status_enabled_pending)
                else ->
                    stringResource(R.string.settings_security_status_disabled)
            },
            checked = state.artifactSigningVerificationEnabled,
            enabled = !state.artifactSigningOperationInFlight && canManageKeys,
            onCheckedChange = { enabled ->
                when {
                    enabled -> vm.enableArtifactSigningVerification()
                    else -> showDisableConfirm1 = true
                }
            },
            startAction = { Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = iconTint) }
        )
        ArrowPreference(
            title = stringResource(R.string.settings_security_import_keys),
            summary = when {
                !canManageKeys -> stringResource(R.string.settings_security_requires_fork)
                !state.artifactSigningVerificationEnabled -> stringResource(R.string.settings_security_import_requires_enabled)
                else -> stringResource(R.string.settings_security_import_keys_desc)
            },
            startAction = { Icon(Icons.Default.UploadFile, contentDescription = null, tint = iconTint) },
            enabled = !state.artifactSigningOperationInFlight && canManageKeys && state.artifactSigningVerificationEnabled,
            onClick = {
                importPublicKeyText = ""
                importPrivateKeyText = ""
                importError = null
                showImportDialog = true
            }
        )
        ArrowPreference(
            title = stringResource(R.string.settings_security_reset_keys),
            summary = stringResource(R.string.settings_security_reset_keys_desc),
            startAction = { Icon(Icons.Default.Key, contentDescription = null, tint = iconTint) },
            enabled = !state.artifactSigningOperationInFlight && state.artifactSigningVerificationEnabled && canManageKeys,
            onClick = { showResetConfirm = true }
        )
        if (state.artifactSigningOperationInFlight) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                MiuixText(
                    text = stringResource(R.string.settings_security_operation_running),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.body2
                )
            }
        }
    }

    if (showImportDialog) {
        ImportKeysDialogMiuix(
            publicKeyText = importPublicKeyText,
            privateKeyText = importPrivateKeyText,
            error = importError,
            importing = state.artifactSigningOperationInFlight,
            onPublicKeyTextChange = { importPublicKeyText = it; importError = null },
            onPrivateKeyTextChange = { importPrivateKeyText = it; importError = null },
            onPickPublicKey = {
                importPickerTarget = SecurityKeyImportTargetMiuix.PUBLIC
                importKeyPicker.launch(arrayOf("text/*", "*/*"))
            },
            onPickPrivateKey = {
                importPickerTarget = SecurityKeyImportTargetMiuix.PRIVATE
                importKeyPicker.launch(arrayOf("text/*", "*/*"))
            },
            onImport = {
                scope.launch {
                    importError = null
                    when (val result = vm.importArtifactSigningKeys(importPublicKeyText, importPrivateKeyText)) {
                        is Result.Success -> {
                            showImportDialog = false
                            importPublicKeyText = ""
                            importPrivateKeyText = ""
                        }
                        is Result.Error -> importError = result.message
                        Result.Loading -> Unit
                    }
                }
            },
            onDismiss = {
                if (!state.artifactSigningOperationInFlight) {
                    showImportDialog = false
                }
            }
        )
    }

    if (showDisableConfirm1) {
        TimedConfirmDialogMiuix(
            title = stringResource(R.string.settings_security_disable_dialog_title_1),
            message = stringResource(R.string.settings_security_disable_dialog_message_1),
            confirmLabel = stringResource(R.string.confirm),
            onDismiss = { showDisableConfirm1 = false },
            onConfirm = {
                showDisableConfirm1 = false
                showDisableConfirm2 = true
            }
        )
    }

    if (showDisableConfirm2) {
        TimedConfirmDialogMiuix(
            title = stringResource(R.string.settings_security_disable_dialog_title_2),
            message = stringResource(R.string.settings_security_disable_dialog_message_2),
            confirmLabel = stringResource(R.string.confirm),
            onDismiss = { showDisableConfirm2 = false },
            onConfirm = {
                showDisableConfirm2 = false
                vm.disableArtifactSigningVerification()
            }
        )
    }

    if (showResetConfirm) {
        TimedConfirmDialogMiuix(
            title = stringResource(R.string.settings_security_reset_dialog_title),
            message = stringResource(R.string.settings_security_reset_dialog_message),
            confirmLabel = stringResource(R.string.confirm),
            onDismiss = { showResetConfirm = false },
            onConfirm = {
                showResetConfirm = false
                vm.resetArtifactSigningKeys()
            }
        )
    }
}

@Composable
private fun TimedConfirmDialogMiuix(
    title: String,
    message: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    delaySeconds: Int = 5,
) {
    var remainingSeconds by remember { mutableStateOf(delaySeconds) }
    LaunchedEffect(Unit) {
        while (remainingSeconds > 0) {
            delay(1_000)
            remainingSeconds -= 1
        }
    }
    WindowDialog(
        show = true,
        title = title,
        onDismissRequest = onDismiss
    ) {
        Column {
            MiuixText(text = message)
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MiuixTextButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(android.R.string.cancel),
                    onClick = onDismiss
                )
                Spacer(Modifier.width(20.dp))
                MiuixTextButton(
                    modifier = Modifier.weight(1f),
                    text = if (remainingSeconds > 0) {
                        confirmLabel + " (" + remainingSeconds + "s)"
                    } else {
                        confirmLabel
                    },
                    enabled = remainingSeconds <= 0,
                    colors = if (remainingSeconds <= 0) {
                        ButtonDefaults.textButtonColorsPrimary()
                    } else {
                        ButtonDefaults.textButtonColors()
                    },
                    onClick = onConfirm
                )
            }
        }
    }
}

@Composable
private fun ImportKeysDialogMiuix(
    publicKeyText: String,
    privateKeyText: String,
    error: String?,
    importing: Boolean,
    onPublicKeyTextChange: (String) -> Unit,
    onPrivateKeyTextChange: (String) -> Unit,
    onPickPublicKey: () -> Unit,
    onPickPrivateKey: () -> Unit,
    onImport: () -> Unit,
    onDismiss: () -> Unit,
) {
    WindowDialog(
        show = true,
        title = stringResource(R.string.settings_security_import_dialog_title),
        onDismissRequest = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MiuixText(
                text = stringResource(R.string.settings_security_import_keys_desc),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.body2
            )
            MiuixText(
                text = stringResource(R.string.settings_security_import_public_key),
                style = MiuixTheme.textStyles.body2
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MiuixTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                BasicTextField(
                    value = publicKeyText,
                    onValueChange = onPublicKeyTextChange,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                    textStyle = MiuixTheme.textStyles.body1.copy(color = MiuixTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
                    enabled = !importing
                )
            }
            MiuixTextButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.settings_security_import_pick_public_key),
                onClick = onPickPublicKey,
                enabled = !importing
            )
            MiuixText(
                text = stringResource(R.string.settings_security_import_private_key),
                style = MiuixTheme.textStyles.body2
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MiuixTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                BasicTextField(
                    value = privateKeyText,
                    onValueChange = onPrivateKeyTextChange,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                    textStyle = MiuixTheme.textStyles.body1.copy(color = MiuixTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
                    enabled = !importing
                )
            }
            MiuixTextButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.settings_security_import_pick_private_key),
                onClick = onPickPrivateKey,
                enabled = !importing
            )
            error?.let {
                MiuixText(
                    text = it,
                    color = MiuixTheme.colorScheme.error,
                    style = MiuixTheme.textStyles.body2
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MiuixTextButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(android.R.string.cancel),
                    onClick = onDismiss,
                    enabled = !importing
                )
                Spacer(Modifier.width(20.dp))
                MiuixTextButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.settings_import),
                    enabled = !importing && publicKeyText.isNotBlank() && privateKeyText.isNotBlank(),
                    colors = if (!importing && publicKeyText.isNotBlank() && privateKeyText.isNotBlank()) {
                        ButtonDefaults.textButtonColorsPrimary()
                    } else {
                        ButtonDefaults.textButtonColors()
                    },
                    onClick = onImport
                )
            }
        }
    }
}

private suspend fun readTextFromUri(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
    context.contentResolver.openInputStream(uri)?.use { input ->
        input.bufferedReader(Charsets.UTF_8).readText()
    } ?: error(context.getString(R.string.settings_security_import_read_failed))
}

// Utility functions (mirrored from MD3 SettingsScreen.kt)
// ─────────────────────────────────────────────────────────────────────────────

private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

private fun openAllFilesAccessSettings(context: Context) {
    val packageUri = Uri.parse("package:${context.packageName}")
    val appSettings = Intent(
        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
        packageUri
    )
    runCatching {
        context.startActivity(appSettings)
    }.getOrElse {
        context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
    }
}
