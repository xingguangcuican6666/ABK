package com.abk.kernel.ui.screens.miuix

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.abk.kernel.BuildConfig
import com.abk.kernel.R
import com.abk.kernel.data.model.*
import com.abk.kernel.data.repository.PreferencesRepository
import com.abk.kernel.utils.DownloadDirectoryUtils
import com.abk.kernel.utils.LocaleHelper
import com.abk.kernel.viewmodel.MainViewModel
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import androidx.core.net.toUri

@Composable
fun MiuixSettingsScreen(
    vm: MainViewModel,
    outerPadding: PaddingValues,
    onOpenTheme: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    onOpenLicenses: () -> Unit = {}
) {
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val scrollBehavior = LocalMiuixScrollBehavior.current
    var showLogoutDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.refreshManagerSettings(force = true) }
    LaunchedEffect(state.appUpdatePendingInstallPath) {
        val apkPath = state.appUpdatePendingInstallPath ?: return@LaunchedEffect
        launchAppUpdateInstaller(context, apkPath)
        vm.consumeAppUpdatePendingInstallPath()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(outerPadding)
            .then(if (scrollBehavior != null) Modifier.nestedScroll(scrollBehavior.nestedScrollConnection) else Modifier)
            .verticalScroll(scrollState).padding(horizontal = 12.dp).padding(bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(Modifier.height(4.dp))

        // ── Account ──
        SectionHeader(stringResource(R.string.settings_account))
        Card {
            if (state.user != null) {
                val user = state.user!!
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (user.avatarUrl.isNotBlank()) {
                            AsyncImage(model = user.avatarUrl, contentDescription = null, modifier = Modifier.size(40.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                            Spacer(Modifier.width(12.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(user.login, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = colorScheme.onSurface)
                            state.forkRepo?.let { Text(it.fullName, fontSize = 13.sp, color = colorScheme.onSurfaceVariantSummary) }
                        }
                        IconButton(onClick = { showLogoutDialog = true }) { Icon(MiuixIcons.Remove, contentDescription = stringResource(R.string.logout)) }
                    }
                }
                // Fork repo link
                val forkUrl = state.forkRepo?.let { r -> r.htmlUrl.takeIf { it.isNotBlank() } ?: "https://github.com/${r.fullName}" }
                if (forkUrl != null) {
                    HorizontalDivider()
                    ArrowPreference(
                        title = stringResource(R.string.settings_fork_repo),
                        summary = state.forkRepo?.fullName ?: "",
                        onClick = { openUrl(context, forkUrl) },
                        startAction = { Icon(Icons.Filled.ForkRight, null, tint = colorScheme.onBackground, modifier = Modifier.padding(end = 12.dp)) }
                    )
                }
            } else {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.AccountCircle, null, tint = colorScheme.onSurfaceVariantActions, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.settings_not_logged_in), fontSize = 14.sp, color = colorScheme.onSurfaceVariantSummary)
                }
            }
        }

        // ── Build ──
        SectionHeader(stringResource(R.string.nav_build))
        Card {
            SwitchPreference(title = stringResource(R.string.settings_workflow_foreground_refresh), summary = stringResource(R.string.settings_workflow_foreground_refresh_desc), checked = state.workflowForegroundRefreshEnabled, onCheckedChange = { vm.setWorkflowForegroundRefreshEnabled(it) })
            if (state.workflowForegroundRefreshEnabled) {
                HorizontalDivider()
                val refreshIntervals = PreferencesRepository.WORKFLOW_FOREGROUND_REFRESH_INTERVALS_SEC.sorted()
                OverlayDropdownPreference(
                    title = stringResource(R.string.settings_workflow_foreground_refresh_interval),
                    items = refreshIntervals.map { stringResource(R.string.settings_workflow_foreground_refresh_interval_sec, it) },
                    selectedIndex = refreshIntervals.indexOf(state.workflowForegroundRefreshIntervalSec).coerceAtLeast(0),
                    onSelectedIndexChange = { vm.setWorkflowForegroundRefreshIntervalSec(refreshIntervals[it]) }
                )
            }
            HorizontalDivider()
            SwitchPreference(title = stringResource(R.string.settings_auto_download), summary = stringResource(R.string.settings_auto_download_desc), checked = state.autoDownload, onCheckedChange = { vm.setAutoDownload(it) })
            HorizontalDivider()
            SwitchPreference(title = stringResource(R.string.settings_prebuilt_gki), summary = stringResource(R.string.settings_prebuilt_gki_desc), checked = state.prebuiltGkiEnabled, onCheckedChange = { vm.setPrebuiltGkiEnabled(it) })
            HorizontalDivider()
            DownloadDirectoryRow(state.downloadDirectory) { vm.setDownloadDirectory(it) }
            HorizontalDivider()
            MirrorRow(state.downloadMirrorBaseUrl) { vm.setDownloadMirrorBaseUrl(it) }
        }

        // ── App Update ──
        SectionHeader(stringResource(R.string.settings_app_update))
        Card {
            // Stability + Line pickers
            val stabilityValues = listOf(APP_UPDATE_STABILITY_STABLE, APP_UPDATE_STABILITY_UNSTABLE)
            val stabilityLabels = listOf(stringResource(R.string.settings_app_update_stable), stringResource(R.string.settings_app_update_unstable))
            OverlayDropdownPreference(
                title = stringResource(R.string.settings_app_update_stability),
                items = stabilityLabels,
                selectedIndex = stabilityValues.indexOf(normalizeAppUpdateStability(state.appUpdateStability)).coerceAtLeast(0),
                onSelectedIndexChange = { vm.setAppUpdateStability(stabilityValues[it]) }
            )
            HorizontalDivider()
            val lineValues = listOf(APP_UPDATE_LINE_NORMAL, APP_UPDATE_LINE_DEV)
            val lineLabels = listOf(stringResource(R.string.settings_app_update_line_normal), stringResource(R.string.settings_app_update_line_dev))
            OverlayDropdownPreference(
                title = stringResource(R.string.settings_app_update_line),
                items = lineLabels,
                selectedIndex = lineValues.indexOf(normalizeAppUpdateLine(state.appUpdateLine)).coerceAtLeast(0),
                onSelectedIndexChange = { vm.setAppUpdateLine(lineValues[it]) }
            )
            HorizontalDivider()
            // Check update
            ArrowPreference(
                title = stringResource(R.string.settings_check_app_update),
                summary = appUpdateCheckSubtitle(state),
                onClick = { vm.checkAppUpdate() },
                startAction = { Icon(MiuixIcons.Update, null, tint = colorScheme.onBackground, modifier = Modifier.padding(end = 12.dp)) },
                endActions = { if (state.appUpdateChecking) CircularProgressIndicator(size = 20.dp, strokeWidth = 2.dp) else Icon(Icons.Filled.Refresh, null, tint = colorScheme.onSurfaceVariantActions, modifier = Modifier.size(20.dp)) }
            )
            // Update info
            state.appUpdateInfo?.let { info ->
                HorizontalDivider()
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (info.hasUpdate) Icons.Filled.Download else Icons.Filled.Verified, null, tint = if (info.hasUpdate) colorScheme.primary else colorScheme.onSurfaceVariantActions, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(if (info.hasUpdate) stringResource(R.string.settings_app_update_available) else stringResource(R.string.settings_app_update_latest), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colorScheme.onSurface)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(appUpdateResultSubtitle(info), fontSize = 12.sp, color = colorScheme.onSurfaceVariantSummary)
                }
                if (info.hasUpdate && info.remote.downloadUrl.isNotBlank()) {
                    HorizontalDivider()
                    ArrowPreference(
                        title = stringResource(R.string.settings_download_install_update),
                        summary = if (state.appUpdateDownloading) stringResource(R.string.settings_app_update_downloading_progress, state.appUpdateDownloadProgress) else info.remote.downloadUrl,
                        onClick = { vm.downloadAndInstallAppUpdate() },
                        startAction = { Icon(Icons.Filled.InstallMobile, null, tint = colorScheme.onBackground, modifier = Modifier.padding(end = 12.dp)) },
                        endActions = { if (state.appUpdateDownloading) CircularProgressIndicator(size = 20.dp, strokeWidth = 2.dp) else Icon(Icons.Filled.Download, null, tint = colorScheme.onSurfaceVariantActions, modifier = Modifier.size(20.dp)) }
                    )
                }
            }
            state.appUpdateError?.takeIf { it.isNotBlank() }?.let { _ ->
                HorizontalDivider()
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Error, null, tint = colorScheme.error, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.settings_app_update_error), fontSize = 14.sp, color = colorScheme.error)
                }
            }
        }

        // ── Manager Settings (injected) ──
        if (state.hasNativeManagerPermission) {
            ManagerSettingsGroup(state, vm)
        }

        // ── Notification ──
        SectionHeader(stringResource(R.string.settings_notification))
        Card { SwitchPreference(title = stringResource(R.string.settings_notify_build), summary = stringResource(R.string.settings_notify_build_desc), checked = state.notifyBuild, onCheckedChange = { vm.setNotifyBuild(it) }) }

        // ── Navigation ──
        SectionHeader(stringResource(R.string.settings_navigation))
        Card { SwitchPreference(title = stringResource(R.string.settings_predictive_back), summary = if (state.themeStyle == "miuix") stringResource(R.string.settings_predictive_back_miuix_desc) else stringResource(R.string.settings_predictive_back_desc), checked = state.predictiveBackEnabled, enabled = state.themeStyle != "miuix", onCheckedChange = { vm.setPredictiveBackEnabled(it) }) }

        // ── Language ──
        SectionHeader(stringResource(R.string.settings_language))
        Card {
            val langCodes = listOf("zh", "en", "ru")
            val langLabels = listOf(
                stringResource(R.string.settings_language_zh),
                stringResource(R.string.settings_language_en),
                stringResource(R.string.settings_language_ru)
            )
            OverlayDropdownPreference(
                title = stringResource(R.string.settings_language_switch),
                items = langLabels,
                selectedIndex = langCodes.indexOf(LocaleHelper.currentUiLanguage()).coerceAtLeast(0),
                onSelectedIndexChange = { idx ->
                    val code = langCodes[idx]
                    LocaleHelper.setLanguage(context, code)
                    vm.onUiLanguageChanged()
                    (context as? Activity)?.recreate()
                }
            )
        }

        // ── Theme ──
        SectionHeader(stringResource(R.string.settings_theme))
        Card {
            ArrowPreference(
                title = stringResource(R.string.settings_color_appearance),
                summary = "${themeModeLabel(state.themeMode)} · ${if (state.dynamicColorEnabled) stringResource(R.string.settings_monet) else stringResource(R.string.settings_custom_palette)}",
                onClick = onOpenTheme,
                startAction = { Icon(Icons.Filled.Palette, null, tint = colorScheme.onBackground, modifier = Modifier.padding(end = 12.dp)) }
            )
        }

        // ── About ──
        SectionHeader(stringResource(R.string.settings_about))
        Card {
            ArrowPreference(
                title = stringResource(R.string.app_full_name),
                onClick = { openUrl(context, "https://github.com/xingguangcuican6666/ABK") },
                startAction = { Icon(Icons.Filled.Info, null, tint = colorScheme.onBackground, modifier = Modifier.padding(end = 12.dp)) },
                endActions = { Text("v${BuildConfig.VERSION_NAME}", fontSize = 12.sp, color = colorScheme.onSurfaceVariantSummary) }
            )
            HorizontalDivider()
            ArrowPreference(
                title = stringResource(R.string.settings_about),
                summary = stringResource(R.string.settings_about_desc),
                onClick = onOpenAbout,
                startAction = { Icon(Icons.Filled.AutoAwesome, null, tint = colorScheme.onBackground, modifier = Modifier.padding(end = 12.dp)) }
            )
            HorizontalDivider()
            ArrowPreference(
                title = stringResource(R.string.settings_open_source_licenses),
                summary = stringResource(R.string.settings_open_source_licenses_desc),
                onClick = onOpenLicenses,
                startAction = { Icon(Icons.AutoMirrored.Filled.Article, null, tint = colorScheme.onBackground, modifier = Modifier.padding(end = 12.dp)) }
            )
        }
        Spacer(Modifier.height(30.dp))
    }

    if (showLogoutDialog) {
        OverlayDialog(
            title = stringResource(R.string.settings_logout_title),
            summary = stringResource(R.string.settings_logout_message),
            show = showLogoutDialog,
            onDismissRequest = { showLogoutDialog = false }
        ) {
            Row(horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(text = stringResource(R.string.cancel), onClick = { showLogoutDialog = false }, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(20.dp))
                TextButton(text = stringResource(R.string.confirm), onClick = { showLogoutDialog = false; vm.logout() }, modifier = Modifier.weight(1f), colors = ButtonDefaults.textButtonColorsPrimary())
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Manager Injected Settings
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun ManagerSettingsGroup(state: com.abk.kernel.viewmodel.MainUiState, vm: MainViewModel) {
    val hasItems = state.managerSettingsItems.isNotEmpty()
    if (!hasItems && !state.managerSettingsLoading && state.managerSettingsError == null) return

    SectionHeader(state.managerSettingsTitle.ifBlank { stringResource(R.string.settings_manager_settings) })
    Card {
        when {
            state.managerSettingsLoading && !hasItems -> {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(size = 20.dp, strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.settings_manager_loading_desc), fontSize = 14.sp, color = colorScheme.onSurfaceVariantSummary)
                }
            }
            state.managerSettingsError != null -> {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(state.managerSettingsError, fontSize = 14.sp, color = colorScheme.error)
                    Button(onClick = { vm.refreshManagerSettings(force = true) }, colors = ButtonDefaults.buttonColors(), modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.retry)) }
                }
            }
        }
        state.managerSettingsItems.forEachIndexed { i, item ->
            if (i > 0) HorizontalDivider()
            ManagerSettingRow(item, state.managerSettingActionId == item.id) { action ->
                when (action) {
                    is ManagerAction.Checked -> vm.setManagerSettingChecked(item.id, action.value)
                    is ManagerAction.ModeSelected -> vm.setManagerSettingMode(item.id, action.index)
                }
            }
        }
    }
}

private sealed class ManagerAction {
    data class Checked(val value: Boolean) : ManagerAction()
    data class ModeSelected(val index: Int) : ManagerAction()
}

@Composable
private fun ManagerSettingRow(item: ManagerSettingItem, inFlight: Boolean, onAction: (ManagerAction) -> Unit) {
    val icon = when (item.id) {
        "app_profile_templates" -> Icons.Filled.Apps; "manager_tools" -> Icons.Filled.Build; "kpm" -> Icons.Filled.Extension
        "su_compat" -> Icons.Filled.RemoveModerator; "kernel_umount" -> Icons.Filled.RemoveCircle; "adb_root" -> Icons.Filled.Adb
        "sulog" -> Icons.AutoMirrored.Filled.Article; "selinux_hide" -> Icons.Filled.Shield; "default_umount_modules" -> Icons.Filled.FolderDelete
        "webview_debug" -> Icons.Filled.Code; else -> Icons.Filled.Settings
    }
    when (item.kind) {
        ManagerSettingKind.NAVIGATION -> {
            ArrowPreference(
                title = item.title,
                summary = item.subtitle,
                enabled = item.enabled && !inFlight,
                onClick = { /* TODO: handle navigation */ },
                startAction = { Icon(icon, null, tint = colorScheme.onBackground, modifier = Modifier.padding(end = 12.dp)) }
            )
        }
        ManagerSettingKind.SWITCH -> {
            SwitchPreference(
                title = item.title,
                summary = item.subtitle,
                checked = item.checked,
                enabled = item.enabled && !inFlight,
                onCheckedChange = { onAction(ManagerAction.Checked(it)) },
                startAction = {
                    Icon(icon, null, tint = colorScheme.onBackground, modifier = Modifier.padding(end = 12.dp))
                }
            )
        }
        ManagerSettingKind.MODE -> {
            val options = item.options.map { it.trim() }.filter { it.isNotBlank() }
            val idx = item.selectedIndex.coerceIn(0, (options.lastIndex).coerceAtLeast(0))
            var showModeDialog by remember { mutableStateOf(false) }
            Row(Modifier.fillMaxWidth().clickable(enabled = item.enabled && !inFlight) { showModeDialog = true }.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.title, fontSize = 14.sp, color = colorScheme.onSurface)
                    Text(item.subtitle, fontSize = 12.sp, color = colorScheme.onSurfaceVariantSummary)
                }
                Text(options.getOrNull(idx) ?: "", fontSize = 13.sp, color = colorScheme.onSurfaceVariantSummary)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Filled.ArrowDropDown, null, tint = colorScheme.onSurfaceVariantActions, modifier = Modifier.size(18.dp))
            }
            if (showModeDialog) {
                val cancelText = stringResource(R.string.cancel)
                OverlayDialog(title = item.title, show = true, onDismissRequest = { showModeDialog = false }) {
                    for (oi in options.indices) {
                        val opt = options[oi]
                        Row(Modifier.fillMaxWidth().clickable { showModeDialog = false; if (oi != idx) onAction(ManagerAction.ModeSelected(oi)) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(opt, Modifier.weight(1f), fontSize = 14.sp, color = colorScheme.onSurface)
                            if (oi == idx) Icon(MiuixIcons.Ok, null, tint = colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                    }
                    TextButton(text = cancelText, onClick = { showModeDialog = false }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Theme Settings (overlay page)
// ═══════════════════════════════════════════════════════════════════════════

@Composable
fun MiuixThemeSettingsScreen(vm: MainViewModel, onClose: () -> Unit) {
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current
    val backgroundPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }; vm.setBackgroundImageUri(uri.toString()) }
    }
    val topBarState = rememberTopAppBarState()
    val scrollBehavior = MiuixScrollBehavior(state = topBarState)

    Scaffold(
        topBar = { TopAppBar(title = stringResource(R.string.settings_theme), largeTitle = stringResource(R.string.settings_theme), scrollBehavior = scrollBehavior, navigationIcon = { IconButton(onClick = onClose) { Icon(MiuixIcons.Back, null) } }) },
        containerColor = colorScheme.surface
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp).nestedScroll(scrollBehavior.nestedScrollConnection).verticalScroll(rememberScrollState()).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Spacer(Modifier.height(4.dp))

            // Theme Style
            SectionHeader(stringResource(R.string.settings_theme_style))
            Card {
                RadioButtonPreference(
                    title = stringResource(R.string.settings_theme_style_expressive),
                    summary = stringResource(R.string.settings_theme_style_expressive_desc),
                    selected = state.themeStyle == "expressive",
                    onClick = { vm.setThemeStyle("expressive") }
                )
                HorizontalDivider()
                RadioButtonPreference(
                    title = stringResource(R.string.settings_theme_style_miuix),
                    summary = stringResource(R.string.settings_theme_style_miuix_desc),
                    selected = state.themeStyle == "miuix",
                    onClick = { vm.setThemeStyle("miuix") }
                )
            }

            // Appearance Mode
            SectionHeader(stringResource(R.string.settings_appearance_mode))
            Card {
                RadioButtonPreference(
                    title = stringResource(R.string.settings_theme_system),
                    selected = state.themeMode == "system",
                    onClick = { vm.setThemeMode("system") }
                )
                HorizontalDivider()
                RadioButtonPreference(
                    title = stringResource(R.string.settings_theme_light),
                    selected = state.themeMode == "light",
                    onClick = { vm.setThemeMode("light") }
                )
                HorizontalDivider()
                RadioButtonPreference(
                    title = stringResource(R.string.settings_theme_dark),
                    selected = state.themeMode == "dark",
                    onClick = { vm.setThemeMode("dark") }
                )
            }

            // Dynamic Color
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SectionHeader(stringResource(R.string.settings_color_source))
                Card {
                    val fallbackPrimary = colorScheme.primary.toArgb()
                    val fallbackSecondary = colorScheme.secondary.toArgb()
                    SwitchPreference(title = stringResource(R.string.settings_monet), summary = stringResource(R.string.settings_monet_desc), checked = state.dynamicColorEnabled, onCheckedChange = { enabled ->
                        vm.setDynamicColorEnabled(enabled, if (!enabled) fallbackPrimary else null, if (!enabled) fallbackSecondary else null)
                    })
                }
            }

            // Background
            SectionHeader(stringResource(R.string.settings_background))
            Card {
                SwitchPreference(title = stringResource(R.string.settings_custom_background), summary = state.customBackgroundUri?.let { stringResource(R.string.settings_background_selected) } ?: stringResource(R.string.settings_custom_background_desc), checked = state.backgroundImageEnabled && !state.customBackgroundUri.isNullOrBlank(), enabled = !state.customBackgroundUri.isNullOrBlank(), onCheckedChange = { vm.setBackgroundImageEnabled(it) })
                HorizontalDivider()
                ArrowPreference(
                    title = if (state.customBackgroundUri.isNullOrBlank()) stringResource(R.string.settings_choose_background) else stringResource(R.string.settings_change_background),
                    onClick = { backgroundPicker.launch(arrayOf("image/*")) },
                    startAction = { Icon(Icons.Filled.Image, null, tint = colorScheme.onBackground, modifier = Modifier.padding(end = 12.dp)) }
                )
                if (!state.customBackgroundUri.isNullOrBlank()) {
                    HorizontalDivider()
                    ArrowPreference(
                        title = stringResource(R.string.settings_remove_background),
                        onClick = { vm.setBackgroundImageUri(null) },
                        startAction = { Icon(Icons.Filled.Delete, null, tint = colorScheme.onBackground, modifier = Modifier.padding(end = 12.dp)) }
                    )
                }
            }

            // Navigation Bar Style
            SectionHeader(stringResource(R.string.settings_navigation_bar) /* Navigation Bar */)
            Card {
                SwitchPreference(
                    title = stringResource(R.string.settings_floating_navigation),
                    summary = stringResource(R.string.settings_floating_navigation_desc),
                    checked = state.floatingNavigationBarEnabled,
                    onCheckedChange = { vm.setFloatingNavigationBarEnabled(it) }
                )
                if (state.floatingNavigationBarEnabled) {
                    HorizontalDivider()
                    SwitchPreference(
                        title = stringResource(R.string.settings_glass_navigation_effect),
                        summary = stringResource(R.string.settings_glass_navigation_effect_desc),
                        checked = state.glassNavigationEffectEnabled,
                        onCheckedChange = { vm.setGlassNavigationEffectEnabled(it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeColorRow(title: String, selectedArgb: Int, onSelect: (Int) -> Unit) {
    val presets = listOf(0xFF8BC34A.toInt(), 0xFF42A5F5.toInt(), 0xFF9575CD.toInt(), 0xFFEC6A9A.toInt(), 0xFFFFA726.toInt(), 0xFF26C6DA.toInt())
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colorScheme.onSurface)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (presets.none { (it or 0xFF000000.toInt()) == (selectedArgb or 0xFF000000.toInt()) }) {
                ColorSwatch(selectedArgb, selected = true, enabled = false) {}
            }
            presets.forEach { c ->
                ColorSwatch(c, (c or 0xFF000000.toInt()) == (selectedArgb or 0xFF000000.toInt()), true) { onSelect(c) }
            }
        }
    }
}

@Composable
private fun ColorSwatch(argb: Int, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).clip(CircleShape).background(Color(argb)).border(if (selected) 3.dp else 1.dp, if (selected) colorScheme.onSurface else colorScheme.onSurfaceVariantActions, CircleShape).clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        if (selected) Icon(Icons.Filled.Check, null, tint = if (androidx.core.graphics.ColorUtils.calculateLuminance(argb) > 0.5) Color(0xFF11140F.toInt()) else Color.White, modifier = Modifier.size(18.dp))
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// About Settings (overlay page)
// ═══════════════════════════════════════════════════════════════════════════

@Composable
fun MiuixAboutSettingsScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val topBarState = rememberTopAppBarState()
    val scrollBehavior = MiuixScrollBehavior(state = topBarState)
    Scaffold(
        topBar = { TopAppBar(title = stringResource(R.string.settings_about_title), largeTitle = stringResource(R.string.settings_about_title), scrollBehavior = scrollBehavior, navigationIcon = { IconButton(onClick = onClose) { Icon(MiuixIcons.Back, null) } }) },
        containerColor = colorScheme.surface
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp).nestedScroll(scrollBehavior.nestedScrollConnection).verticalScroll(rememberScrollState()).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Spacer(Modifier.height(4.dp))

            // Hero
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Icon(Icons.Filled.Info, null, tint = colorScheme.primary, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.app_full_name), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = colorScheme.onSurface)
                    Text(stringResource(R.string.settings_about_intro), fontSize = 14.sp, color = colorScheme.onSurfaceVariantSummary)
                    Text(stringResource(R.string.settings_about_version, BuildConfig.VERSION_NAME), fontSize = 13.sp, color = colorScheme.onSurfaceVariantActions)
                }
            }

            // Repository links
            SectionHeader(stringResource(R.string.settings_repository_info))
            Card {
                val links = listOf(
                    stringResource(R.string.settings_source_repository) to sourceRepoUrl(),
                    "Releases" to "${sourceRepoUrl()}/releases",
                    "Actions" to "${sourceRepoUrl()}/actions",
                    "README" to "${sourceRepoUrl()}/blob/main/README.md",
                    stringResource(R.string.settings_third_party_notices) to "${sourceRepoUrl()}/blob/main/THIRD_PARTY_NOTICES.md"
                )
                links.forEachIndexed { i, (title, url) ->
                    ArrowPreference(
                        title = title,
                        summary = url,
                        onClick = { openUrl(context, url) },
                        startAction = { Icon(Icons.Filled.Code, null, tint = colorScheme.onBackground, modifier = Modifier.padding(end = 12.dp)) }
                    )
                    if (i < links.lastIndex) HorizontalDivider()
                }
            }

            // Contributors
            SectionHeader(stringResource(R.string.settings_contributors))
            Card {
                Text(stringResource(R.string.settings_contributors_desc), fontSize = 13.sp, color = colorScheme.onSurfaceVariantSummary, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
                HorizontalDivider()
                val contributors = listOf("Akuma-Noko", "DebugBoard", "DreamFerry", "elysias123", "Fede2782", "FixeQyt", "FunLay123", "gsf114", "guoyujie666", "guruji-byte", "huime180", "liqideqq", "LX200944", "Mazha0309", "MiRinChan", "prpjzz", "ReeViiS69", "ShirkNeko", "Starsun", "TheSillyOk", "TheWildJames", "Tools-cx-app", "ukriu", "wrnxr233", "Xiaomichael", "xingguangcuican6666", "yx1234587", "zzh20188")
                contributors.forEachIndexed { i, name ->
                    ArrowPreference(
                        title = "@$name",
                        onClick = { openUrl(context, "https://github.com/$name") },
                        startAction = { Icon(Icons.Filled.Person, null, tint = colorScheme.onBackground, modifier = Modifier.padding(end = 12.dp)) }
                    )
                    if (i < contributors.lastIndex) HorizontalDivider()
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Open Source Licenses (overlay page)
// ═══════════════════════════════════════════════════════════════════════════

@Composable
fun MiuixOpenSourceLicensesScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val topBarState = rememberTopAppBarState()
    val scrollBehavior = MiuixScrollBehavior(state = topBarState)
    Scaffold(
        topBar = { TopAppBar(title = stringResource(R.string.settings_open_source_licenses), largeTitle = stringResource(R.string.settings_open_source_licenses), scrollBehavior = scrollBehavior, navigationIcon = { IconButton(onClick = onClose) { Icon(MiuixIcons.Back, null) } }) },
        containerColor = colorScheme.surface
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp).nestedScroll(scrollBehavior.nestedScrollConnection).verticalScroll(rememberScrollState()).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Spacer(Modifier.height(4.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Icon(Icons.Filled.Gavel, null, tint = colorScheme.primary, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.settings_open_source_licenses), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = colorScheme.onSurface)
                    Text(stringResource(R.string.settings_open_source_licenses_intro), fontSize = 14.sp, color = colorScheme.onSurfaceVariantSummary)
                }
            }

            openSourceNoticeGroups().forEach { group ->
                SectionHeader(stringResource(group.titleRes))
                Card {
                    group.items.forEachIndexed { i, notice ->
                        val subtitle = listOfNotNull(notice.license, notice.source.takeIf { it.isNotBlank() }).joinToString(" · ")
                        if (notice.url != null) {
                            ArrowPreference(
                                title = notice.name,
                                summary = subtitle,
                                onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, notice.url.toUri())) } },
                                startAction = { Icon(Icons.Filled.Source, null, tint = colorScheme.onBackground, modifier = Modifier.padding(end = 12.dp)) }
                            )
                        } else {
                            BasicComponent(
                                title = notice.name,
                                summary = subtitle,
                                startAction = { Icon(Icons.Filled.Source, null, tint = colorScheme.onBackground, modifier = Modifier.padding(end = 12.dp)) }
                            )
                        }
                        if (i < group.items.lastIndex) HorizontalDivider()
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Shared components
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun SectionHeader(title: String) {
    Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colorScheme.onSurfaceVariantActions, modifier = Modifier.padding(start = 14.dp))
}

@Composable
private fun DownloadDirectoryRow(value: String, onValueChange: (String) -> Unit) {
    val context = LocalContext.current
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            DownloadDirectoryUtils.directoryPathFromTreeUri(uri)?.let { onValueChange(it) } ?: Toast.makeText(context, "Unsupported tree URI", Toast.LENGTH_SHORT).show()
        }
    }
    Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.FolderOpen, null, tint = colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.settings_download_directory), fontSize = 14.sp, color = colorScheme.onSurface)
        }
        TextField(value = value, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth(), singleLine = true, label = DownloadDirectoryUtils.defaultDirectoryPath())
        val dirRestoredMsg = stringResource(R.string.settings_download_directory_default_restored)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(text = stringResource(R.string.settings_download_directory_reset), onClick = { onValueChange(DownloadDirectoryUtils.defaultDirectoryPath()); Toast.makeText(context, dirRestoredMsg, Toast.LENGTH_SHORT).show() }, modifier = Modifier.weight(1f))
            Button(onClick = { folderPicker.launch(null) }, colors = ButtonDefaults.buttonColorsPrimary(), modifier = Modifier.weight(1f)) { Text(stringResource(R.string.settings_download_directory_choose)) }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !android.os.Environment.isExternalStorageManager()) {
            Spacer(Modifier.height(6.dp))
            Button(onClick = { openAllFilesAccessSettings(context) }, colors = ButtonDefaults.buttonColors(), modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.FolderSpecial, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.settings_download_directory_storage_permission))
            }
        }
    }
}

@Composable
private fun MirrorRow(value: String, onValueChange: (String) -> Unit) {
    Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Public, null, tint = colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.settings_download_mirror), fontSize = 14.sp, color = colorScheme.onSurface)
        }
        TextField(value = value, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth(), singleLine = true, label = "https://hk.gh-proxy.org/")
    }
}

@Composable
private fun appUpdateCheckSubtitle(state: com.abk.kernel.viewmodel.MainUiState): String = when {
    state.appUpdateDownloading -> stringResource(R.string.settings_app_update_downloading_progress, state.appUpdateDownloadProgress)
    state.appUpdateChecking -> stringResource(R.string.settings_app_update_checking)
    state.appUpdateInfo != null -> appUpdateResultShort(state.appUpdateInfo)
    state.appUpdateError?.isNotBlank() == true -> state.appUpdateError
    else -> "${appUpdateStabilityLabel(state.appUpdateStability)} · ${appUpdateLineLabel(state.appUpdateLine)}"
}

@Composable
private fun appUpdateResultShort(info: AppUpdateCheckResult): String = if (info.hasUpdate) stringResource(R.string.settings_app_update_available) else stringResource(R.string.settings_app_update_latest)

@Composable
private fun appUpdateResultSubtitle(info: AppUpdateCheckResult): String {
    val status = appUpdateResultShort(info)
    return "${info.currentVersionName} → ${info.remote.versionName} | ${appUpdateStabilityLabel(info.stability)} · ${appUpdateLineLabel(info.line)} | ${info.remote.publishedAt.ifBlank { "?" }.take(10)} | $status"
}

@Composable
private fun appUpdateStabilityLabel(v: String): String = when (normalizeAppUpdateStability(v)) { APP_UPDATE_STABILITY_UNSTABLE -> stringResource(R.string.settings_app_update_unstable); else -> stringResource(R.string.settings_app_update_stable) }
@Composable
private fun appUpdateLineLabel(v: String): String = when (normalizeAppUpdateLine(v)) { APP_UPDATE_LINE_DEV -> stringResource(R.string.settings_app_update_line_dev); else -> stringResource(R.string.settings_app_update_line_normal) }

@Composable
private fun themeModeLabel(mode: String): String = when (mode) { "light" -> stringResource(R.string.settings_theme_light); "dark" -> stringResource(R.string.settings_theme_dark); else -> stringResource(R.string.settings_theme_system) }

private fun sourceRepoUrl(): String = "https://github.com/${BuildConfig.SOURCE_REPO_OWNER}/${BuildConfig.SOURCE_REPO_NAME}"
private fun openUrl(context: android.content.Context, url: String) { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) } }

@SuppressLint("RequestInstallPackagesPolicy")
private fun launchAppUpdateInstaller(context: android.content.Context, apkPath: String) {
    val f = java.io.File(apkPath); if (!f.isFile) return
    if (!context.packageManager.canRequestPackageInstalls()) {
        context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply { data = "package:${context.packageName}".toUri(); addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK
        )
        })
        return
    }
    val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
    context.startActivity(Intent(Intent.ACTION_INSTALL_PACKAGE).apply { data = uri; addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true) })
}

private fun openAllFilesAccessSettings(context: android.content.Context) {
    val packageUri = android.net.Uri.parse("package:${context.packageName}")
    val appSettings = Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, packageUri)
    runCatching {
        context.startActivity(appSettings)
    }.getOrElse {
        context.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
    }
}
