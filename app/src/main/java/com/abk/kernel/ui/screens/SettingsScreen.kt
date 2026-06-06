@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.abk.kernel.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import coil.compose.AsyncImage
import com.abk.kernel.BuildConfig
import com.abk.kernel.R
import com.abk.kernel.utils.DownloadDirectoryUtils
import com.abk.kernel.utils.LocaleHelper
import com.abk.kernel.ui.components.AbkScreenHorizontalPadding
import com.abk.kernel.ui.components.ObserveChildPageVisibility
import com.abk.kernel.ui.components.childPageOverlayEnterTransition
import com.abk.kernel.ui.components.childPageOverlayExitTransition
import com.abk.kernel.ui.components.childPageScrimExitTransition
import com.abk.kernel.ui.components.rememberChildPageBackController
import com.abk.kernel.ui.components.rememberChildPageOverlayTransition
import com.abk.kernel.ui.components.ExpressiveHeroCard
import com.abk.kernel.ui.components.ExpressiveListItem
import com.abk.kernel.ui.components.ExpressiveSectionCard
import com.abk.kernel.ui.components.ExpressiveStatusChip
import com.abk.kernel.ui.components.ExpressiveSwitchItem
import com.abk.kernel.ui.components.ExpressiveTopBar
import com.abk.kernel.ui.theme.uiSurfaceColor
import com.abk.kernel.data.repository.PreferencesRepository
import com.abk.kernel.data.model.ManagerSettingItem
import com.abk.kernel.data.model.ManagerSettingKind
import com.abk.kernel.viewmodel.MainUiState
import com.abk.kernel.viewmodel.MainViewModel
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    vm: MainViewModel,
    outerPadding: PaddingValues = PaddingValues(0.dp),
    onThemePageVisibleChange: (Boolean) -> Unit = {},
    onOpenInstalledModules: () -> Unit = {}
) {
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showThemeSettings by rememberSaveable { mutableStateOf(false) }
    var showAppProfileTemplates by rememberSaveable { mutableStateOf(false) }
    var showManagerTools by rememberSaveable { mutableStateOf(false) }
    var showAboutPage by rememberSaveable { mutableStateOf(false) }
    var showOpenSourceLicenses by rememberSaveable { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val showChildPage = showThemeSettings || showAppProfileTemplates || showManagerTools ||
        showAboutPage || showOpenSourceLicenses
    val childPageTransition = rememberChildPageOverlayTransition(
        visible = showChildPage,
        label = "settings-child-page"
    )
    val motionScheme = MaterialTheme.motionScheme

    LaunchedEffect(Unit) {
        vm.refreshManagerSettings(force = true)
    }

    LaunchedEffect(state.hasNativeManagerPermission) {
        if (!state.hasNativeManagerPermission) {
            showAppProfileTemplates = false
            showManagerTools = false
        }
    }

    fun closeChildPage() {
        showThemeSettings = false
        showAppProfileTemplates = false
        showManagerTools = false
        showAboutPage = false
        showOpenSourceLicenses = false
    }

    val childPageBack = rememberChildPageBackController(
        enabled = showChildPage,
        predictiveBackEnabled = state.predictiveBackEnabled,
        onBack = ::closeChildPage,
    )

    ObserveChildPageVisibility(
        transition = childPageTransition,
        onVisibleChange = onThemePageVisibleChange,
        onAfterExitAnimation = { childPageBack.resetProgress() }
    )

    DisposableEffect(Unit) {
        onDispose { onThemePageVisibleChange(false) }
    }

    fun openThemeSettings() {
        childPageBack.resetProgress()
        showAppProfileTemplates = false
        showManagerTools = false
        showAboutPage = false
        showOpenSourceLicenses = false
        showThemeSettings = true
    }

    fun openAppProfileTemplates() {
        childPageBack.resetProgress()
        showThemeSettings = false
        showManagerTools = false
        showAboutPage = false
        showOpenSourceLicenses = false
        showAppProfileTemplates = true
        vm.refreshAppProfileTemplates()
    }

    fun openManagerTools() {
        childPageBack.resetProgress()
        showThemeSettings = false
        showAppProfileTemplates = false
        showAboutPage = false
        showOpenSourceLicenses = false
        showManagerTools = true
        vm.refreshManagerTools(force = true)
    }

    fun openAboutPage() {
        childPageBack.resetProgress()
        showThemeSettings = false
        showAppProfileTemplates = false
        showManagerTools = false
        showOpenSourceLicenses = false
        showAboutPage = true
    }

    fun openOpenSourceLicenses() {
        childPageBack.resetProgress()
        showThemeSettings = false
        showAppProfileTemplates = false
        showManagerTools = false
        showAboutPage = false
        showOpenSourceLicenses = true
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            icon = { Icon(Icons.Default.Logout, null) },
            title = { Text(stringResource(R.string.settings_logout_title)) },
            text = { Text(stringResource(R.string.settings_logout_message)) },
            confirmButton = {
                Button(
                    onClick = { showLogoutDialog = false; vm.logout() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val childPageTopInset = outerPadding.calculateTopPadding()
        val childPageBottomInset = outerPadding.calculateBottomPadding()
        val childPageModifier = Modifier
            .fillMaxWidth()
            .height(maxHeight + childPageTopInset + childPageBottomInset)
            .offset(y = -childPageTopInset)
        Scaffold(
            containerColor = uiSurfaceColor(MaterialTheme.colorScheme.surface),
            topBar = {
                ExpressiveTopBar(
                    title = stringResource(R.string.settings_title),
                    scrollBehavior = scrollBehavior
                )
            }
        ) {
            SettingsMainContent(
                padding = it,
                outerPadding = outerPadding,
                state = state,
                vm = vm,
                scrollBehavior = scrollBehavior,
                onLogout = { showLogoutDialog = true },
                onOpenThemeSettings = ::openThemeSettings,
                onOpenAppProfileTemplates = ::openAppProfileTemplates,
                onOpenManagerTools = ::openManagerTools,
                onOpenInstalledModules = onOpenInstalledModules,
                onAbout = ::openAboutPage,
                onOpenSourceLicenses = ::openOpenSourceLicenses
            )
        }

        childPageTransition.AnimatedVisibility(
            visible = { it },
            enter = fadeIn(animationSpec = motionScheme.defaultEffectsSpec()),
            exit = childPageScrimExitTransition(state.predictiveBackEnabled, motionScheme),
            modifier = childPageModifier
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = childPageBack.scrimAlpha))
            )
        }

        childPageTransition.AnimatedVisibility(
            visible = { it && showThemeSettings },
            enter = childPageOverlayEnterTransition(state.predictiveBackEnabled, motionScheme),
            exit = childPageOverlayExitTransition(state.predictiveBackEnabled, motionScheme),
            modifier = childPageModifier
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(childPageBack.backTransformModifier())
            ) {
                SettingsPageBackground(
                    backgroundUri = state.customBackgroundUri,
                    backgroundImageEnabled = state.backgroundImageEnabled
                )
                Scaffold(
                    containerColor = Color.Transparent,
                    topBar = {
                        ExpressiveTopBar(
                            title = stringResource(R.string.settings_theme),
                            navigationIcon = {
                                IconButton(onClick = childPageBack::requestDismiss) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.settings_back))
                                }
                            }
                        )
                    }
                ) {
                    ThemeSettingsScreen(
                        padding = it,
                        themeMode = state.themeMode,
                        dynamicColorEnabled = state.dynamicColorEnabled,
                        customThemeColorArgb = state.customThemeColorArgb,
                        customAccentColorArgb = state.customAccentColorArgb,
                        backgroundUri = state.customBackgroundUri,
                        backgroundImageEnabled = state.backgroundImageEnabled,
                        uiSurfaceAlpha = state.uiSurfaceAlpha,
                        onThemeModeChange = { value -> vm.setThemeMode(value) },
                        onDynamicColorEnabledChange = { enabled, themeColor, accentColor ->
                            vm.setDynamicColorEnabled(enabled, themeColor, accentColor)
                        },
                        onCustomThemeColorsChange = { themeColor, accentColor ->
                            vm.setCustomThemeColors(themeColor, accentColor)
                        },
                        onBackgroundImageChange = { uri -> vm.setBackgroundImageUri(uri) },
                        onBackgroundImageEnabledChange = { enabled -> vm.setBackgroundImageEnabled(enabled) },
                        onUiSurfaceAlphaChange = { alpha -> vm.setUiSurfaceAlpha(alpha) }
                    )
                }
            }
        }

        childPageTransition.AnimatedVisibility(
            visible = { it && showAppProfileTemplates },
            enter = childPageOverlayEnterTransition(state.predictiveBackEnabled, motionScheme),
            exit = childPageOverlayExitTransition(state.predictiveBackEnabled, motionScheme),
            modifier = childPageModifier
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(childPageBack.backTransformModifier())
            ) {
                SettingsPageBackground(
                    backgroundUri = state.customBackgroundUri,
                    backgroundImageEnabled = state.backgroundImageEnabled
                )
                Scaffold(
                    containerColor = Color.Transparent,
                    topBar = {
                        ExpressiveTopBar(
                            title = stringResource(R.string.settings_app_profile_templates),
                            navigationIcon = {
                                IconButton(onClick = childPageBack::requestDismiss) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.settings_back))
                                }
                            },
                            actions = {
                                IconButton(onClick = { vm.refreshAppProfileTemplates() }) {
                                    Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                                }
                            }
                        )
                    }
                ) {
                    AppProfileTemplateSettingsScreen(
                        padding = it,
                        state = state,
                        onRefresh = vm::refreshAppProfileTemplates,
                        onSelect = vm::selectAppProfileTemplate,
                        onSave = vm::saveAppProfileTemplate,
                        onDelete = vm::deleteAppProfileTemplate
                    )
                }
            }
        }

        childPageTransition.AnimatedVisibility(
            visible = { it && showManagerTools },
            enter = childPageOverlayEnterTransition(state.predictiveBackEnabled, motionScheme),
            exit = childPageOverlayExitTransition(state.predictiveBackEnabled, motionScheme),
            modifier = childPageModifier
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(childPageBack.backTransformModifier())
            ) {
                SettingsPageBackground(
                    backgroundUri = state.customBackgroundUri,
                    backgroundImageEnabled = state.backgroundImageEnabled
                )
                Scaffold(
                    containerColor = Color.Transparent,
                    topBar = {
                        ExpressiveTopBar(
                            title = stringResource(R.string.settings_tools),
                            navigationIcon = {
                                IconButton(onClick = childPageBack::requestDismiss) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.settings_back))
                                }
                            },
                            actions = {
                                IconButton(onClick = { vm.refreshManagerTools(force = true) }) {
                                    Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                                }
                            }
                        )
                    }
                ) {
                    ManagerToolsSettingsScreen(
                        padding = it,
                        state = state,
                        onSelinuxChange = vm::setSelinuxEnforcing,
                        onBackupAllowlist = vm::backupRootGrantAllowlist,
                        onRestoreAllowlist = vm::restoreRootGrantAllowlist
                    )
                }
            }
        }

        childPageTransition.AnimatedVisibility(
            visible = { it && showAboutPage },
            enter = childPageOverlayEnterTransition(state.predictiveBackEnabled, motionScheme),
            exit = childPageOverlayExitTransition(state.predictiveBackEnabled, motionScheme),
            modifier = childPageModifier
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(childPageBack.backTransformModifier())
            ) {
                SettingsPageBackground(
                    backgroundUri = state.customBackgroundUri,
                    backgroundImageEnabled = state.backgroundImageEnabled
                )
                Scaffold(
                    containerColor = Color.Transparent,
                    topBar = {
                        ExpressiveTopBar(
                            title = stringResource(R.string.settings_about_title),
                            navigationIcon = {
                                IconButton(onClick = childPageBack::requestDismiss) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.settings_back))
                                }
                            }
                        )
                    }
                ) {
                    AboutRepositoryScreen(
                        padding = it,
                        onOpenUrl = { openUrl(context, it) },
                        onOpenSourceLicenses = ::openOpenSourceLicenses
                    )
                }
            }
        }

        childPageTransition.AnimatedVisibility(
            visible = { it && showOpenSourceLicenses },
            enter = childPageOverlayEnterTransition(state.predictiveBackEnabled, motionScheme),
            exit = childPageOverlayExitTransition(state.predictiveBackEnabled, motionScheme),
            modifier = childPageModifier
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(childPageBack.backTransformModifier())
            ) {
                SettingsPageBackground(
                    backgroundUri = state.customBackgroundUri,
                    backgroundImageEnabled = state.backgroundImageEnabled
                )
                Scaffold(
                    containerColor = Color.Transparent,
                    topBar = {
                        ExpressiveTopBar(
                            title = stringResource(R.string.settings_open_source_licenses),
                            navigationIcon = {
                                IconButton(onClick = childPageBack::requestDismiss) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.settings_back))
                                }
                            }
                        )
                    }
                ) {
                    OpenSourceLicensesScreen(
                        padding = it,
                        onOpenUrl = { openUrl(context, it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsPageBackground(
    backgroundUri: String?,
    backgroundImageEnabled: Boolean
) {
    val colorScheme = MaterialTheme.colorScheme
    val hasBackground = backgroundImageEnabled && !backgroundUri.isNullOrBlank()
    val scrimColor = if (colorScheme.surface.luminance() > 0.5f) {
        colorScheme.surface.copy(alpha = 0.28f)
    } else {
        Color.Black.copy(alpha = 0.38f)
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.surface)
    ) {
        if (hasBackground) {
            AsyncImage(
                model = backgroundUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(scrimColor)
            )
        }
    }
}

@Composable
private fun SettingsMainContent(
    padding: PaddingValues,
    outerPadding: PaddingValues,
    state: MainUiState,
    vm: MainViewModel,
    scrollBehavior: TopAppBarScrollBehavior,
    onLogout: () -> Unit,
    onOpenThemeSettings: () -> Unit,
    onOpenAppProfileTemplates: () -> Unit,
    onOpenManagerTools: () -> Unit,
    onOpenInstalledModules: () -> Unit,
    onAbout: () -> Unit,
    onOpenSourceLicenses: () -> Unit
) {
    Column(
        modifier = Modifier
            .padding(padding)
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AbkScreenHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SettingsGroup(title = stringResource(R.string.settings_account)) {
            state.user?.let { user ->
                ExpressiveListItem(
                    title = user.login,
                    subtitle = user.name ?: user.htmlUrl,
                    leadingContent = {
                        AsyncImage(
                            model = user.avatarUrl,
                            contentDescription = null,
                            modifier = Modifier.size(42.dp)
                                .clip(CircleShape)
                        )
                    },
                    trailingContent = {
                        IconButton(onClick = onLogout) {
                            Icon(
                                Icons.Default.Logout,
                                contentDescription = stringResource(R.string.settings_logout_desc),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                )
                val forkUrl = state.forkRepo?.let { repo ->
                    repo.htmlUrl.takeIf { it.isNotBlank() } ?: "https://github.com/${repo.fullName}"
                }
                val openCtx = LocalContext.current
                val onForkClick: (() -> Unit)? = if (forkUrl != null) {
                    { openUrl(openCtx, forkUrl) }
                } else null
                ExpressiveListItem(
                    title = stringResource(R.string.settings_fork_repo),
                    subtitle = state.forkRepo?.fullName ?: stringResource(R.string.settings_waiting_fork),
                    leadingIcon = Icons.Default.ForkRight,
                    onClick = onForkClick
                )
            } ?: ExpressiveListItem(
                title = stringResource(R.string.settings_not_logged_in),
                leadingIcon = Icons.Default.AccountCircle
            )
        }

        SettingsGroup(title = stringResource(R.string.settings_build)) {
            SwitchSettingsItem(
                icon = Icons.Default.Sync,
                title = stringResource(R.string.settings_workflow_foreground_refresh),
                subtitle = stringResource(R.string.settings_workflow_foreground_refresh_desc),
                checked = state.workflowForegroundRefreshEnabled,
                onCheckedChange = { vm.setWorkflowForegroundRefreshEnabled(it) }
            )
            AnimatedVisibility(
                visible = state.workflowForegroundRefreshEnabled,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                WorkflowForegroundRefreshIntervalPicker(
                    selectedSec = state.workflowForegroundRefreshIntervalSec,
                    onSelect = { vm.setWorkflowForegroundRefreshIntervalSec(it) }
                )
            }
            SwitchSettingsItem(
                icon = Icons.Default.Download,
                title = stringResource(R.string.settings_auto_download),
                subtitle = stringResource(R.string.settings_auto_download_desc),
                checked = state.autoDownload,
                onCheckedChange = { vm.setAutoDownload(it) }
            )
            SwitchSettingsItem(
                icon = Icons.Default.CloudDownload,
                title = stringResource(R.string.settings_prebuilt_gki),
                subtitle = stringResource(R.string.settings_prebuilt_gki_desc),
                checked = state.prebuiltGkiEnabled,
                onCheckedChange = { vm.setPrebuiltGkiEnabled(it) }
            )
            Spacer(Modifier.height(10.dp))
            DownloadDirectorySettingsItem(
                value = state.downloadDirectory,
                onValueChange = { vm.setDownloadDirectory(it) }
            )
            Spacer(Modifier.height(10.dp))
            MirrorSettingsItem(
                value = state.downloadMirrorBaseUrl,
                onValueChange = { vm.setDownloadMirrorBaseUrl(it) }
            )
        }

        ManagerInjectedSettingsGroup(
            state = state,
            vm = vm,
            onOpenAppProfileTemplates = onOpenAppProfileTemplates,
            onOpenManagerTools = onOpenManagerTools,
            onOpenInstalledModules = onOpenInstalledModules
        )

        SettingsGroup(title = stringResource(R.string.settings_notification)) {
            SwitchSettingsItem(
                icon = Icons.Default.Notifications,
                title = stringResource(R.string.settings_notify_build),
                subtitle = stringResource(R.string.settings_notify_build_desc),
                checked = state.notifyBuild,
                onCheckedChange = { vm.setNotifyBuild(it) }
            )
        }

        SettingsGroup(title = stringResource(R.string.settings_navigation)) {
            SwitchSettingsItem(
                icon = Icons.Default.ArrowBack,
                title = stringResource(R.string.settings_predictive_back),
                subtitle = stringResource(R.string.settings_predictive_back_desc),
                checked = state.predictiveBackEnabled,
                onCheckedChange = { vm.setPredictiveBackEnabled(it) }
            )
        }

        SettingsGroup(title = stringResource(R.string.settings_language)) {
            val ctx = LocalContext.current
            val currentLang = LocaleHelper.getLanguage(ctx)
            val activity = ctx as? Activity
            LanguageSettingsItem(
                current = currentLang,
                onSelect = { lang ->
                    LocaleHelper.setLanguage(ctx, lang)
                    vm.onUiLanguageChanged()
                    activity?.recreate()
                }
            )
        }

        SettingsGroup(title = stringResource(R.string.settings_theme)) {
            ExpressiveListItem(
                title = stringResource(R.string.settings_color_appearance),
                subtitle = "${themeModeLabel(state.themeMode)} · ${dynamicColorLabel(state.dynamicColorEnabled)}",
                leadingIcon = Icons.Default.Palette,
                trailingContent = {
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = stringResource(R.string.settings_enter_color_appearance)
                    )
                },
                onClick = onOpenThemeSettings
            )
        }

        SettingsGroup(title = stringResource(R.string.settings_about)) {
            ExpressiveListItem(
                title = stringResource(R.string.app_full_name),
                subtitle = "AnyBase Kernel v${BuildConfig.VERSION_NAME}",
                leadingIcon = Icons.Default.Info
            )
            ExpressiveListItem(
                title = stringResource(R.string.settings_about),
                subtitle = stringResource(R.string.settings_about_desc),
                leadingIcon = Icons.Default.AutoAwesome,
                trailingContent = {
                    Icon(Icons.Default.ChevronRight, contentDescription = stringResource(R.string.settings_enter_about))
                },
                onClick = onAbout
            )
            ExpressiveListItem(
                title = stringResource(R.string.settings_open_source_licenses),
                subtitle = stringResource(R.string.settings_open_source_licenses_desc),
                leadingIcon = Icons.Default.Article,
                trailingContent = {
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = stringResource(R.string.settings_enter_open_source_licenses)
                    )
                },
                onClick = onOpenSourceLicenses
            )
        }

        Spacer(Modifier.height(80.dp + outerPadding.calculateBottomPadding()))
    }
}

@Composable
private fun ManagerInjectedSettingsGroup(
    state: MainUiState,
    vm: MainViewModel,
    onOpenAppProfileTemplates: () -> Unit,
    onOpenManagerTools: () -> Unit,
    onOpenInstalledModules: () -> Unit
) {
    if (!state.hasNativeManagerPermission) return
    val hasInjectedSettings = state.managerSettingsItems.isNotEmpty()
    if (!hasInjectedSettings && !state.managerSettingsLoading && state.managerSettingsError == null) return

    SettingsGroup(title = state.managerSettingsTitle.ifBlank { stringResource(R.string.settings_manager_settings) }) {
        when {
            state.managerSettingsLoading && !hasInjectedSettings -> {
                ExpressiveListItem(
                    title = stringResource(R.string.settings_manager_loading_title),
                    subtitle = stringResource(R.string.settings_manager_loading_desc),
                    leadingContent = { LoadingIndicator(Modifier.size(24.dp)) }
                )
            }
            state.managerSettingsError != null -> {
                ExpressiveListItem(
                    title = stringResource(R.string.settings_manager_load_failed),
                    subtitle = state.managerSettingsError,
                    leadingIcon = Icons.Default.Error,
                    trailingContent = {
                        IconButton(onClick = { vm.refreshManagerSettings(force = true) }) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.retry))
                        }
                    }
                )
            }
        }

        state.managerSettingsItems.forEach { item ->
            val actionInFlight = state.managerSettingActionId == item.id
            when (item.kind) {
                ManagerSettingKind.NAVIGATION -> ExpressiveListItem(
                    title = item.title,
                    subtitle = item.subtitle,
                    leadingIcon = managerSettingIcon(item.id),
                    enabled = item.enabled && !actionInFlight,
                    trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = stringResource(R.string.settings_enter)) },
                    onClick = {
                        when (item.id) {
                            "app_profile_templates" -> onOpenAppProfileTemplates()
                            "manager_tools" -> onOpenManagerTools()
                            "kpm" -> onOpenInstalledModules()
                        }
                    }
                )
                ManagerSettingKind.MODE -> ManagerModeSettingItem(
                    item = item,
                    actionInFlight = actionInFlight,
                    onSelected = { index -> vm.setManagerSettingMode(item.id, index) }
                )
                ManagerSettingKind.SWITCH -> SwitchSettingsItem(
                    icon = managerSettingIcon(item.id),
                    title = item.title,
                    subtitle = item.subtitle,
                    checked = item.checked,
                    enabled = item.enabled && !actionInFlight,
                    onCheckedChange = { checked -> vm.setManagerSettingChecked(item.id, checked) }
                )
            }
        }
    }
}

@Composable
private fun ManagerModeSettingItem(
    item: ManagerSettingItem,
    actionInFlight: Boolean,
    onSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val options = remember(item.options) {
        item.options.map { it.trim() }.filter { it.isNotBlank() }
    }
    val selectedIndex = if (options.isNotEmpty()) {
        item.selectedIndex.coerceIn(0, options.lastIndex)
    } else {
        0
    }
    val selectedLabel = options.getOrNull(selectedIndex) ?: stringResource(R.string.settings_unknown)
    val enabled = item.enabled && !actionInFlight && options.isNotEmpty()
    ExpressiveListItem(
        title = item.title,
        subtitle = item.subtitle,
        leadingIcon = managerSettingIcon(item.id),
        enabled = enabled,
        trailingContent = {
            Box {
                TextButton(
                    onClick = { expanded = true },
                    enabled = enabled
                ) {
                    if (actionInFlight) {
                        LoadingIndicator(Modifier.size(18.dp))
                    } else {
                        Text(selectedLabel)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    options.forEachIndexed { index, option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            leadingIcon = {
                                if (index == selectedIndex) {
                                    Icon(Icons.Default.Check, contentDescription = null)
                                }
                            },
                            onClick = {
                                expanded = false
                                if (index != selectedIndex) onSelected(index)
                            }
                        )
                    }
                }
            }
        },
        onClick = { if (enabled) expanded = true }
    )
}

@Composable
private fun ManagerToolsSettingsScreen(
    padding: PaddingValues,
    state: MainUiState,
    onSelinuxChange: (Boolean) -> Unit,
    onBackupAllowlist: (Uri) -> Unit,
    onRestoreAllowlist: (Uri) -> Unit
) {
    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) onBackupAllowlist(uri)
    }
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onRestoreAllowlist(uri)
    }
    val selinuxBusy = state.managerToolActionId == "selinux_mode"
    val backupBusy = state.managerToolActionId == "backup_allowlist"
    val restoreBusy = state.managerToolActionId == "restore_allowlist"

    Column(
        modifier = Modifier
            .padding(padding)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AbkScreenHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SettingsGroup(title = stringResource(R.string.settings_system_tools)) {
            SwitchSettingsItem(
                icon = Icons.Default.Security,
                title = stringResource(R.string.settings_selinux_mode),
                subtitle = stringResource(R.string.settings_current_value, selinuxModeLabel(state.selinuxModeText)),
                checked = state.selinuxEnforcing,
                enabled = !state.managerToolsLoading && !selinuxBusy,
                onCheckedChange = onSelinuxChange
            )
            ExpressiveListItem(
                title = stringResource(R.string.settings_umount_paths),
                subtitle = if (state.umountPaths.isEmpty()) {
                    stringResource(R.string.settings_umount_no_paths)
                } else {
                    stringResource(R.string.settings_umount_path_count, state.umountPaths.size)
                },
                leadingIcon = Icons.Default.FolderDelete,
                trailingContent = {
                    if (state.managerToolsLoading) LoadingIndicator(Modifier.size(22.dp))
                }
            )
        }

        SettingsGroup(title = stringResource(R.string.settings_allowlist)) {
            ExpressiveListItem(
                title = stringResource(R.string.settings_backup_allowlist),
                subtitle = stringResource(R.string.settings_backup_allowlist_desc),
                leadingIcon = Icons.Default.CloudUpload,
                enabled = !backupBusy,
                trailingContent = {
                    if (backupBusy) {
                        LoadingIndicator(Modifier.size(22.dp))
                    } else {
                        Icon(Icons.Default.ChevronRight, contentDescription = stringResource(R.string.settings_export))
                    }
                },
                onClick = { backupLauncher.launch("abk-root-allowlist.json") }
            )
            ExpressiveListItem(
                title = stringResource(R.string.settings_restore_allowlist),
                subtitle = stringResource(R.string.settings_restore_allowlist_desc),
                leadingIcon = Icons.Default.History,
                enabled = !restoreBusy,
                trailingContent = {
                    if (restoreBusy) {
                        LoadingIndicator(Modifier.size(22.dp))
                    } else {
                        Icon(Icons.Default.ChevronRight, contentDescription = stringResource(R.string.settings_import))
                    }
                },
                onClick = { restoreLauncher.launch(arrayOf("application/json", "text/*", "*/*")) }
            )
        }

        if (state.managerToolsError != null) {
            SettingsGroup(title = stringResource(R.string.settings_tool_status)) {
                ExpressiveListItem(
                    title = stringResource(R.string.settings_operation_incomplete),
                    subtitle = state.managerToolsError,
                    leadingIcon = Icons.Default.Error
                )
            }
        }

        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun selinuxModeLabel(mode: String): String =
    when (mode.trim().lowercase()) {
        "enforcing" -> stringResource(R.string.settings_selinux_enforcing)
        "permissive" -> stringResource(R.string.settings_selinux_permissive)
        "disabled" -> stringResource(R.string.settings_selinux_disabled)
        else -> mode.ifBlank { stringResource(R.string.settings_unknown) }
    }

@Composable
private fun AppProfileTemplateSettingsScreen(
    padding: PaddingValues,
    state: MainUiState,
    onRefresh: () -> Unit,
    onSelect: (String?) -> Unit,
    onSave: (String, String) -> Unit,
    onDelete: (String) -> Unit
) {
    var editingId by rememberSaveable { mutableStateOf("") }
    var editingContent by rememberSaveable { mutableStateOf("") }
    var creating by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.selectedAppProfileTemplateId, state.selectedAppProfileTemplateContent) {
        if (!state.selectedAppProfileTemplateId.isNullOrBlank()) {
            creating = false
            editingId = state.selectedAppProfileTemplateId
            editingContent = state.selectedAppProfileTemplateContent
        }
    }

    Column(
        modifier = Modifier
            .padding(padding)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AbkScreenHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SettingsGroup(title = stringResource(R.string.settings_local_templates)) {
            when {
                state.appProfileTemplatesLoading -> ExpressiveListItem(
                    title = stringResource(R.string.settings_templates_loading),
                    subtitle = stringResource(R.string.settings_templates_loading_desc),
                    leadingContent = { LoadingIndicator(Modifier.size(24.dp)) }
                )
                state.appProfileTemplates.isEmpty() -> ExpressiveListItem(
                    title = stringResource(R.string.settings_templates_empty),
                    subtitle = stringResource(R.string.settings_templates_empty_desc),
                    leadingIcon = Icons.Default.Description
                )
            }
            state.appProfileTemplates.forEach { template ->
                ExpressiveListItem(
                    title = template.id,
                    subtitle = if (state.selectedAppProfileTemplateId == template.id) {
                        stringResource(R.string.settings_template_editing)
                    } else {
                        stringResource(R.string.settings_app_profile_templates)
                    },
                    leadingIcon = Icons.Default.Description,
                    selected = state.selectedAppProfileTemplateId == template.id,
                    trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = stringResource(R.string.settings_edit)) },
                    onClick = { onSelect(template.id) }
                )
            }
            ExpressiveListItem(
                title = stringResource(R.string.settings_new_template),
                subtitle = stringResource(R.string.settings_new_template_desc),
                leadingIcon = Icons.Default.Add,
                onClick = {
                    creating = true
                    editingId = ""
                    editingContent = defaultAppProfileTemplateJson()
                    onSelect(null)
                }
            )
        }

        if (state.appProfileTemplatesError != null) {
            SettingsGroup(title = stringResource(R.string.settings_status)) {
                ExpressiveListItem(
                    title = stringResource(R.string.settings_operation_incomplete),
                    subtitle = state.appProfileTemplatesError,
                    leadingIcon = Icons.Default.Error,
                    trailingContent = {
                        IconButton(onClick = onRefresh) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                        }
                    }
                )
            }
        }

        val hasEditor = creating || !state.selectedAppProfileTemplateId.isNullOrBlank()
        if (hasEditor) {
            SettingsGroup(title = stringResource(R.string.settings_edit_template)) {
                OutlinedTextField(
                    value = editingId,
                    onValueChange = { editingId = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.settings_template_name)) },
                    singleLine = true,
                    enabled = state.selectedAppProfileTemplateId.isNullOrBlank()
                )
                OutlinedTextField(
                    value = editingContent,
                    onValueChange = { editingContent = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 220.dp),
                    label = { Text(stringResource(R.string.settings_template_json)) },
                    minLines = 10
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (state.appProfileTemplateSaving) {
                        LoadingIndicator(Modifier.size(22.dp))
                    }
                    if (!state.selectedAppProfileTemplateId.isNullOrBlank()) {
                        TextButton(
                            onClick = { onDelete(state.selectedAppProfileTemplateId.orEmpty()) },
                            enabled = !state.appProfileTemplateSaving,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text(stringResource(R.string.delete))
                        }
                    }
                    Button(
                        onClick = { onSave(editingId, editingContent) },
                        enabled = !state.appProfileTemplateSaving && editingId.isNotBlank()
                    ) {
                        Text(stringResource(R.string.save))
                    }
                }
            }
        }

        Spacer(Modifier.height(80.dp))
    }
}

private fun managerSettingIcon(id: String) = when (id) {
    "app_profile_templates" -> Icons.Default.Apps
    "manager_tools" -> Icons.Default.Build
    "kpm" -> Icons.Default.Extension
    "su_compat" -> Icons.Default.RemoveModerator
    "kernel_umount" -> Icons.Default.RemoveCircle
    "adb_root" -> Icons.Default.Adb
    "sulog" -> Icons.Default.Article
    "selinux_hide" -> Icons.Default.Shield
    "default_umount_modules" -> Icons.Default.FolderDelete
    "webview_debug" -> Icons.Default.Code
    else -> Icons.Default.Settings
}

private fun defaultAppProfileTemplateJson(): String =
    """
    {
      "uid": 0,
      "gid": 0,
      "groups": [],
      "capabilities": [],
      "context": "u:r:ksu:s0",
      "namespace": 0,
      "rules": ""
    }
    """.trimIndent()

@Composable
private fun ThemeSettingsScreen(
    padding: PaddingValues,
    themeMode: String,
    dynamicColorEnabled: Boolean,
    customThemeColorArgb: Int?,
    customAccentColorArgb: Int?,
    backgroundUri: String?,
    backgroundImageEnabled: Boolean,
    uiSurfaceAlpha: Float,
    onThemeModeChange: (String) -> Unit,
    onDynamicColorEnabledChange: (Boolean, Int?, Int?) -> Unit,
    onCustomThemeColorsChange: (Int, Int) -> Unit,
    onBackgroundImageChange: (String?) -> Unit,
    onBackgroundImageEnabledChange: (Boolean) -> Unit,
    onUiSurfaceAlphaChange: (Float) -> Unit
) {
    val context = LocalContext.current
    val dynamicColorAvailable = isDynamicColorAvailable()
    val effectiveDynamicColorEnabled = dynamicColorAvailable && dynamicColorEnabled
    val colorScheme = MaterialTheme.colorScheme
    val selectedThemeColorArgb = customThemeColorArgb ?: colorScheme.primary.toArgb()
    val selectedAccentColorArgb = customAccentColorArgb ?: colorScheme.secondary.toArgb()
    val backgroundPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            onBackgroundImageChange(uri.toString())
        }
    }
    val themes = listOf(
        Triple("system", stringResource(R.string.settings_theme_system), Icons.Default.BrightnessMedium),
        Triple("light", stringResource(R.string.settings_theme_light), Icons.Default.LightMode),
        Triple("dark", stringResource(R.string.settings_theme_dark), Icons.Default.DarkMode)
    )

    Column(
        modifier = Modifier
            .padding(padding)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AbkScreenHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SettingsGroup(title = stringResource(R.string.settings_appearance_mode)) {
            themes.forEach { (key, label, icon) ->
                val selected = themeMode == key
                ExpressiveListItem(
                    title = label,
                    leadingIcon = icon,
                    selected = selected,
                    trailingContent = {
                        if (selected) {
                            Icon(Icons.Default.Check, null)
                        }
                    },
                    onClick = { onThemeModeChange(key) }
                )
            }
        }

        SettingsGroup(title = stringResource(R.string.settings_color_source)) {
            SwitchSettingsItem(
                icon = Icons.Default.AutoAwesome,
                title = stringResource(R.string.settings_monet),
                subtitle = if (dynamicColorAvailable) {
                    stringResource(R.string.settings_monet_desc)
                } else {
                    stringResource(R.string.settings_monet_unavailable_desc)
                },
                checked = effectiveDynamicColorEnabled,
                enabled = dynamicColorAvailable,
                onCheckedChange = { enabled ->
                    onDynamicColorEnabledChange(
                        enabled,
                        if (!enabled) colorScheme.primary.toArgb() else null,
                        if (!enabled) colorScheme.secondary.toArgb() else null
                    )
                }
            )
        }

        if (!effectiveDynamicColorEnabled) {
            SettingsGroup(title = stringResource(R.string.settings_custom_colors)) {
                ThemeColorPicker(
                    title = stringResource(R.string.settings_theme_color),
                    subtitle = stringResource(R.string.settings_theme_color_desc),
                    selectedColorArgb = selectedThemeColorArgb,
                    presets = themeColorPresets(),
                    onColorSelected = { color ->
                        onCustomThemeColorsChange(color, selectedAccentColorArgb)
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ThemeColorPicker(
                    title = stringResource(R.string.settings_accent_color),
                    subtitle = stringResource(R.string.settings_accent_color_desc),
                    selectedColorArgb = selectedAccentColorArgb,
                    presets = themeColorPresets(),
                    onColorSelected = { color ->
                        onCustomThemeColorsChange(selectedThemeColorArgb, color)
                    }
                )
            }
        }

        SettingsGroup(title = stringResource(R.string.settings_background)) {
            SwitchSettingsItem(
                icon = Icons.Default.Image,
                title = stringResource(R.string.settings_custom_background),
                subtitle = if (backgroundUri.isNullOrBlank()) {
                    stringResource(R.string.settings_custom_background_desc)
                } else {
                    stringResource(R.string.settings_background_selected)
                },
                checked = backgroundImageEnabled && !backgroundUri.isNullOrBlank(),
                enabled = !backgroundUri.isNullOrBlank(),
                onCheckedChange = onBackgroundImageEnabledChange
            )
            ExpressiveListItem(
                title = if (backgroundUri.isNullOrBlank()) {
                    stringResource(R.string.settings_choose_background)
                } else {
                    stringResource(R.string.settings_change_background)
                },
                subtitle = stringResource(R.string.settings_choose_background_desc),
                leadingIcon = Icons.Default.Image,
                onClick = { backgroundPicker.launch(arrayOf("image/*")) }
            )
            if (!backgroundUri.isNullOrBlank()) {
                ExpressiveListItem(
                    title = stringResource(R.string.settings_remove_background),
                    subtitle = stringResource(R.string.settings_remove_background_desc),
                    leadingIcon = Icons.Default.Delete,
                    onClick = { onBackgroundImageChange(null) }
                )
            }
            BackgroundAlphaControl(
                alpha = uiSurfaceAlpha,
                enabled = backgroundImageEnabled && !backgroundUri.isNullOrBlank(),
                onAlphaChange = onUiSurfaceAlphaChange
            )
        }

        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun BackgroundAlphaControl(
    alpha: Float,
    enabled: Boolean,
    onAlphaChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.settings_ui_opacity),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${(alpha.coerceIn(0f, 1f) * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(
            value = alpha.coerceIn(0f, 1f),
            onValueChange = onAlphaChange,
            valueRange = 0f..1f,
            enabled = enabled
        )
        Text(
            text = stringResource(R.string.settings_ui_opacity_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ThemeColorPicker(
    title: String,
    subtitle: String,
    selectedColorArgb: Int,
    presets: List<ThemeColorPreset>,
    onColorSelected: (Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (presets.none { colorsMatch(selectedColorArgb, it.argb) }) {
                ThemeColorSwatch(
                    preset = ThemeColorPreset(stringResource(R.string.settings_current_color), selectedColorArgb),
                    selected = true,
                    enabled = false,
                    onClick = {}
                )
            }
            presets.forEach { preset ->
                ThemeColorSwatch(
                    preset = preset,
                    selected = colorsMatch(selectedColorArgb, preset.argb),
                    onClick = { onColorSelected(preset.argb) }
                )
            }
        }
    }
}

@Composable
private fun ThemeColorSwatch(
    preset: ThemeColorPreset,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color(preset.argb))
            .border(
                BorderStroke(if (selected) 3.dp else 1.dp, borderColor),
                CircleShape
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = preset.label,
                tint = readableSwatchContentColor(preset.argb),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

private data class ThemeColorPreset(
    val label: String,
    val argb: Int
)

@Composable
private fun themeColorPresets(): List<ThemeColorPreset> = listOf(
    ThemeColorPreset(stringResource(R.string.settings_color_green), 0xFF8BC34A.toInt()),
    ThemeColorPreset(stringResource(R.string.settings_color_blue), 0xFF42A5F5.toInt()),
    ThemeColorPreset(stringResource(R.string.settings_color_purple), 0xFF9575CD.toInt()),
    ThemeColorPreset(stringResource(R.string.settings_color_pink), 0xFFEC6A9A.toInt()),
    ThemeColorPreset(stringResource(R.string.settings_color_orange), 0xFFFFA726.toInt()),
    ThemeColorPreset(stringResource(R.string.settings_color_cyan), 0xFF26C6DA.toInt())
)

private fun colorsMatch(left: Int, right: Int): Boolean {
    return (left or 0xFF000000.toInt()) == (right or 0xFF000000.toInt())
}

private fun readableSwatchContentColor(argb: Int): Color {
    return if (ColorUtils.calculateLuminance(argb) > 0.5) {
        Color(0xFF11140F.toInt())
    } else {
        Color(0xFFFFFFFF.toInt())
    }
}

@Composable
private fun themeModeLabel(themeMode: String): String = when (themeMode) {
    "light" -> stringResource(R.string.settings_theme_light)
    "dark" -> stringResource(R.string.settings_theme_dark)
    else -> stringResource(R.string.settings_theme_system)
}

@Composable
private fun dynamicColorLabel(enabled: Boolean): String = when {
    !isDynamicColorAvailable() -> stringResource(R.string.settings_monet_unavailable)
    enabled -> stringResource(R.string.settings_monet)
    else -> stringResource(R.string.settings_custom_palette)
}

private fun isDynamicColorAvailable(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Composable
private fun AboutRepositoryScreen(
    padding: PaddingValues,
    onOpenUrl: (String) -> Unit,
    onOpenSourceLicenses: () -> Unit
) {
    Column(
        modifier = Modifier
            .padding(padding)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AbkScreenHorizontalPadding, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ExpressiveHeroCard(
            title = stringResource(R.string.app_full_name),
            subtitle = stringResource(R.string.settings_about_intro),
            icon = Icons.Default.Info
        ) {
            Text(
                text = stringResource(R.string.settings_about_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
        }

        SettingsGroup(title = stringResource(R.string.settings_repository_info)) {
            repositoryLinks().forEach { link ->
                AboutLinkRow(link, onOpenUrl)
            }
            ExpressiveListItem(
                title = stringResource(R.string.settings_open_source_licenses),
                subtitle = stringResource(R.string.settings_open_source_licenses_desc),
                leadingIcon = Icons.Default.Article,
                trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                onClick = onOpenSourceLicenses
            )
        }

        SettingsGroup(title = stringResource(R.string.settings_contributors)) {
            Text(
                text = stringResource(R.string.settings_contributors_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
            contributors().forEach { contributor ->
                val profileUrl = "https://github.com/${contributor.username}"
                ExpressiveListItem(
                    title = "@${contributor.username}",
                    leadingIcon = Icons.Default.Person,
                    trailingContent = { Icon(Icons.Default.OpenInBrowser, null) },
                    onClick = { onOpenUrl(profileUrl) }
                )
            }
        }
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun OpenSourceLicensesScreen(
    padding: PaddingValues,
    onOpenUrl: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .padding(padding)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AbkScreenHorizontalPadding, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ExpressiveHeroCard(
            title = stringResource(R.string.settings_open_source_licenses),
            subtitle = stringResource(R.string.settings_open_source_licenses_intro),
            icon = Icons.Default.Gavel
        )

        openSourceNoticeGroups().forEach { group ->
            SettingsGroup(title = stringResource(group.titleRes)) {
                group.items.forEach { notice ->
                    OpenSourceNoticeRow(notice, onOpenUrl)
                }
            }
        }
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun AboutLinkRow(
    link: AboutLink,
    onOpenUrl: (String) -> Unit
) {
    ExpressiveListItem(
        title = link.title,
        subtitle = link.url,
        leadingIcon = Icons.Default.Code,
        trailingContent = { Icon(Icons.Default.OpenInBrowser, null) },
        onClick = { onOpenUrl(link.url) }
    )
}

@Composable
private fun OpenSourceNoticeRow(
    notice: OpenSourceNotice,
    onOpenUrl: (String) -> Unit
) {
    val subtitle = listOfNotNull(
        notice.license,
        notice.source.takeIf { it.isNotBlank() }
    ).joinToString(" · ")
    ExpressiveListItem(
        title = notice.name,
        subtitle = subtitle,
        leadingIcon = Icons.Default.Source,
        trailingContent = {
            if (!notice.url.isNullOrBlank()) {
                Icon(Icons.Default.OpenInBrowser, null)
            }
        },
        onClick = notice.url?.let { url -> { onOpenUrl(url) } }
    )
}

private data class AboutLink(
    val title: String,
    val url: String
)

private data class AboutContributor(
    val username: String
)

private data class OpenSourceNotice(
    val name: String,
    val license: String,
    val source: String,
    val url: String? = null
)

private data class OpenSourceNoticeGroup(
    val titleRes: Int,
    val items: List<OpenSourceNotice>
)

@Composable
private fun repositoryLinks(): List<AboutLink> = listOf(
    AboutLink(stringResource(R.string.settings_source_repository), sourceRepoUrl()),
    AboutLink("Releases", "${sourceRepoUrl()}/releases"),
    AboutLink("Actions", "${sourceRepoUrl()}/actions"),
    AboutLink("Pages", "https://${BuildConfig.SOURCE_REPO_OWNER}.github.io/${BuildConfig.SOURCE_REPO_NAME}/"),
    AboutLink("README", "${sourceRepoUrl()}/blob/main/README.md"),
    AboutLink(stringResource(R.string.settings_third_party_notices), "${sourceRepoUrl()}/blob/main/THIRD_PARTY_NOTICES.md")
)

private fun contributors(): List<AboutContributor> = listOf(
    AboutContributor("Akuma-Noko"),
    AboutContributor("DebugBoard"),
    AboutContributor("DreamFerry"),
    AboutContributor("elysias123"),
    AboutContributor("Fede2782"),
    AboutContributor("FixeQyt"),
    AboutContributor("FunLay123"),
    AboutContributor("gsf114"),
    AboutContributor("guruji-byte"),
    AboutContributor("huime180"),
    AboutContributor("liqideqq"),
    AboutContributor("LX200944"),
    AboutContributor("Mazha0309"),
    AboutContributor("MiRinChan"),
    AboutContributor("prpjzz"),
    AboutContributor("ReeViiS69"),
    AboutContributor("ShirkNeko"),
    AboutContributor("Starsun"),
    AboutContributor("TheSillyOk"),
    AboutContributor("TheWildJames"),
    AboutContributor("Tools-cx-app"),
    AboutContributor("ukriu"),
    AboutContributor("wrnxr233"),
    AboutContributor("Xiaomichael"),
    AboutContributor("xingguangcuican6666"),
    AboutContributor("yx1234587"),
    AboutContributor("zzh20188")
)

private fun openSourceNoticeGroups(): List<OpenSourceNoticeGroup> = listOf(
    OpenSourceNoticeGroup(
        R.string.settings_license_group_repository,
        listOf(
            OpenSourceNotice("AnyBase Kernel", "GPL-3.0", "LICENSE", sourceRepoUrl()),
            OpenSourceNotice("ABK Control native bridge", "GPL-2.0", "app/src/main/cpp/uapi/abk_control.h"),
            OpenSourceNotice("xingguang DDK module", "GPL", "ddk/xingguang-ddk/xingguang_ddk.c"),
            OpenSourceNotice("DDK kernel API patch", "GPL-2.0", "ddk/patches/xingguang-ddk/0001-xingguang-ddk-api.patch"),
            OpenSourceNotice("ZRAM LZ4 kernel glue", "GPL-2.0-only", "zram/lz4/Makefile")
        )
    ),
    OpenSourceNoticeGroup(
        R.string.settings_license_group_upstream,
        listOf(
            OpenSourceNotice("zzh20188/GKI_KernelSU_SUSFS", "Upstream repository license", "BuildConfig.UPSTREAM_REPO_URL", BuildConfig.UPSTREAM_REPO_URL),
            OpenSourceNotice("WildKernels/GKI_KernelSU_SUSFS", "Upstream repository license", "BuildConfig.TOP_LEVEL_REPO_URL", BuildConfig.TOP_LEVEL_REPO_URL),
            OpenSourceNotice("CodeLinaro CLO LA", "Top-level upstream project licenses", "OnePlus manifest upstream", "https://git.codelinaro.org/clo/la"),
            OpenSourceNotice("OnePlusOSS/kernel_manifest", "Upstream repository license / no SPDX detected", "OnePlus manifest parent", "https://github.com/OnePlusOSS/kernel_manifest"),
            OpenSourceNotice("Xiaomichael/kernel_manifest", "Upstream repository license / no SPDX detected", "OnePlus manifest branch source", "https://github.com/Xiaomichael/kernel_manifest"),
            OpenSourceNotice("Xiaomichael/kernel_patches", "Upstream repository license / no SPDX detected", "OnePlus patch source", "https://github.com/Xiaomichael/kernel_patches"),
            OpenSourceNotice("KernelSU", "GPL-3.0", "workflow setup.sh source", "https://github.com/tiann/KernelSU"),
            OpenSourceNotice("KernelSU Next", "GPL-3.0", "workflow setup.sh source", "https://github.com/KernelSU-Next/KernelSU-Next"),
            OpenSourceNotice("SukiSU Ultra", "GPL-3.0", "kernel setup, ksud, android_bootimg", "https://github.com/SukiSU-Ultra/SukiSU-Ultra"),
            OpenSourceNotice("ReSukiSU", "GPL-3.0", "workflow setup.sh source", "https://github.com/ReSukiSU/ReSukiSU"),
            OpenSourceNotice("SUSFS", "GPL-2.0", "kernel patches and module integration", "https://gitlab.com/simonpunk/susfs4ksu"),
            OpenSourceNotice("ShirkNeko/susfs4ksu", "GPL-2.0", "GitHub mirror / patch source", "https://github.com/ShirkNeko/susfs4ksu"),
            OpenSourceNotice("SukiSU_patch", "GPL-2.0", "workflow patch source", "https://github.com/ShirkNeko/SukiSU_patch"),
            OpenSourceNotice("AnyKernel3", "GPL-2.0", "flashable kernel packaging", "https://github.com/WildKernels/AnyKernel3"),
            OpenSourceNotice("Xiaomichael/AnyKernel3", "Upstream repository license / NOASSERTION", "OnePlus flashable packaging source", "https://github.com/Xiaomichael/AnyKernel3"),
            OpenSourceNotice("WildKernels/kernel_patches", "GPL-2.0", "NTsync, IPSet, BBR and related patches", "https://github.com/WildKernels/kernel_patches"),
            OpenSourceNotice("cctv18/susfs4oki", "GPL-3.0", "OnePlus/OPPO/Realme SUSFS patch source", "https://github.com/cctv18/susfs4oki"),
            OpenSourceNotice("SukiSU_KernelPatch_patch", "Upstream repository license", "KPM patch source", "https://github.com/SukiSU-Ultra/SukiSU_KernelPatch_patch"),
            OpenSourceNotice("Action-Build", "Upstream repository license", "workflow integration", "https://github.com/Numbersf/Action-Build"),
            OpenSourceNotice("sidex15/susfs4ksu-module", "Upstream repository license", "SUSFS module build source", "https://github.com/sidex15/susfs4ksu-module"),
            OpenSourceNotice("LineageOS GCC prebuilts", "GPL-family toolchain notices", "workflow toolchain source", "https://github.com/LineageOS/android_prebuilts_gcc_linux-x86_aarch64_aarch64-linux-gnu-6.4.1"),
            OpenSourceNotice("Baseband Guard", "Upstream repository license", "workflow setup source", "https://github.com/vc-teahouse/Baseband-guard"),
            OpenSourceNotice("Re-Kernel", "Upstream repository license", "workflow patch source", "https://github.com/Sakion-Team/Re-Kernel"),
            OpenSourceNotice("Droidspaces-OSS", "Upstream repository license", "virtualization support patches", "https://github.com/ravindu644/Droidspaces-OSS"),
            OpenSourceNotice("ABK_repo module catalog", "Upstream repository license", "official module catalog", "https://github.com/xingguangcuican6666/ABK_repo")
        )
    ),
    OpenSourceNoticeGroup(
        R.string.settings_license_group_embedded,
        listOf(
            OpenSourceNotice("LZ4", "BSD-2-Clause", "zram/lz4 and zram/include/linux/lz4.h", "https://github.com/lz4/lz4"),
            OpenSourceNotice("AOSP kernel/common", "GPL-2.0 WITH Linux-syscall-note and AOSP notices", "android.googlesource.com/kernel/common", "https://android.googlesource.com/kernel/common"),
            OpenSourceNotice("AOSP kernel manifest", "AOSP project notices", "android.googlesource.com/kernel/manifest", "https://android.googlesource.com/kernel/manifest"),
            OpenSourceNotice("AOSP mkbootimg", "Apache-2.0", "android.googlesource.com/platform/system/tools/mkbootimg", "https://android.googlesource.com/platform/system/tools/mkbootimg"),
            OpenSourceNotice("AOSP kernel build-tools", "AOSP project notices", "android.googlesource.com/kernel/prebuilts/build-tools", "https://android.googlesource.com/kernel/prebuilts/build-tools"),
            OpenSourceNotice("Android GKI certified boot images", "Android image distribution terms", "dl.google.com/android/gki"),
            OpenSourceNotice("Android command line tools", "Android SDK License", "Dockerfile.test", "https://developer.android.com/studio")
        )
    ),
    OpenSourceNoticeGroup(
        R.string.settings_license_group_android,
        androidDependencyNotices()
    ),
    OpenSourceNoticeGroup(
        R.string.settings_license_group_web,
        webDependencyNotices()
    )
)

private fun androidDependencyNotices(): List<OpenSourceNotice> = listOf(
    OpenSourceNotice("Android Gradle Plugin 9.1.1", "Apache-2.0", "com.android.application"),
    OpenSourceNotice("Kotlin Gradle/Compose plugin 2.3.21", "Apache-2.0", "org.jetbrains.kotlin.plugin.compose"),
    OpenSourceNotice("androidx.core:core-ktx 1.15.0", "Apache-2.0", "Gradle direct dependency"),
    OpenSourceNotice("androidx.lifecycle:lifecycle-runtime-ktx 2.8.7", "Apache-2.0", "Gradle direct dependency"),
    OpenSourceNotice("androidx.lifecycle:lifecycle-viewmodel-compose 2.8.7", "Apache-2.0", "Gradle direct dependency"),
    OpenSourceNotice("androidx.activity:activity-compose 1.9.3", "Apache-2.0", "Gradle direct dependency"),
    OpenSourceNotice("androidx.compose:compose-bom 2026.05.00", "Apache-2.0", "Gradle direct dependency"),
    OpenSourceNotice("androidx.compose.ui:ui", "Apache-2.0", "Gradle direct dependency"),
    OpenSourceNotice("androidx.compose.ui:ui-graphics", "Apache-2.0", "Gradle direct dependency"),
    OpenSourceNotice("androidx.compose.ui:ui-tooling-preview", "Apache-2.0", "Gradle direct dependency"),
    OpenSourceNotice("androidx.compose.material3:material3 1.5.0-alpha19", "Apache-2.0", "Gradle direct dependency"),
    OpenSourceNotice("androidx.compose.material:material-icons-extended", "Apache-2.0", "Gradle direct dependency"),
    OpenSourceNotice("com.google.android.material:material 1.12.0", "Apache-2.0", "Gradle direct dependency"),
    OpenSourceNotice("androidx.navigation:navigation-compose 2.8.5", "Apache-2.0", "Gradle direct dependency"),
    OpenSourceNotice("Retrofit 2.11.0", "Apache-2.0", "com.squareup.retrofit2:retrofit"),
    OpenSourceNotice("Retrofit Gson converter 2.11.0", "Apache-2.0", "com.squareup.retrofit2:converter-gson"),
    OpenSourceNotice("OkHttp 4.12.0", "Apache-2.0", "com.squareup.okhttp3:okhttp"),
    OpenSourceNotice("OkHttp logging-interceptor 4.12.0", "Apache-2.0", "com.squareup.okhttp3:logging-interceptor"),
    OpenSourceNotice("Gson 2.11.0", "Apache-2.0", "com.google.code.gson:gson"),
    OpenSourceNotice("kotlinx-serialization-json 1.7.3", "Apache-2.0", "org.jetbrains.kotlinx:kotlinx-serialization-json"),
    OpenSourceNotice("libsu core 5.2.2", "Apache-2.0", "com.github.topjohnwu.libsu:core"),
    OpenSourceNotice("libsu io 5.2.2", "Apache-2.0", "com.github.topjohnwu.libsu:io"),
    OpenSourceNotice("Coil Compose 2.7.0", "Apache-2.0", "io.coil-kt:coil-compose"),
    OpenSourceNotice("WorkManager runtime-ktx 2.10.0", "Apache-2.0", "androidx.work:work-runtime-ktx"),
    OpenSourceNotice("DataStore preferences 1.1.2", "Apache-2.0", "androidx.datastore:datastore-preferences"),
    OpenSourceNotice("JUnit 4.13.2", "EPL-1.0", "testImplementation"),
    OpenSourceNotice("AndroidX Test JUnit 1.2.1", "Apache-2.0", "androidTestImplementation"),
    OpenSourceNotice("Espresso Core 3.6.1", "Apache-2.0", "androidTestImplementation")
)

private fun webDependencyNotices(): List<OpenSourceNotice> = listOf(
    OpenSourceNotice("@discoveryjs/json-ext 0.6.3", "MIT", "web/package-lock.json"),
    OpenSourceNotice("@jridgewell/gen-mapping 0.3.13", "MIT", "web/package-lock.json"),
    OpenSourceNotice("@jridgewell/resolve-uri 3.1.2", "MIT", "web/package-lock.json"),
    OpenSourceNotice("@jridgewell/source-map 0.3.11", "MIT", "web/package-lock.json"),
    OpenSourceNotice("@jridgewell/sourcemap-codec 1.5.5", "MIT", "web/package-lock.json"),
    OpenSourceNotice("@jridgewell/trace-mapping 0.3.31", "MIT", "web/package-lock.json"),
    OpenSourceNotice("@parcel/watcher 2.5.6 and platform packages", "MIT", "web/package-lock.json"),
    OpenSourceNotice("@types/eslint 9.6.1", "MIT", "web/package-lock.json"),
    OpenSourceNotice("@types/eslint-scope 3.7.7", "MIT", "web/package-lock.json"),
    OpenSourceNotice("@types/estree 1.0.8", "MIT", "web/package-lock.json"),
    OpenSourceNotice("@types/json-schema 7.0.15", "MIT", "web/package-lock.json"),
    OpenSourceNotice("@types/node 25.3.3", "MIT", "web/package-lock.json"),
    OpenSourceNotice("@webassemblyjs/* 1.13.2-1.14.1", "MIT / Apache-2.0", "web/package-lock.json"),
    OpenSourceNotice("@webpack-cli/* 3.0.1", "MIT", "web/package-lock.json"),
    OpenSourceNotice("@xtuc/ieee754 1.2.0", "BSD-3-Clause", "web/package-lock.json"),
    OpenSourceNotice("@xtuc/long 4.2.2", "Apache-2.0", "web/package-lock.json"),
    OpenSourceNotice("acorn 8.16.0", "MIT", "web/package-lock.json"),
    OpenSourceNotice("acorn-import-phases 1.0.4", "MIT", "web/package-lock.json"),
    OpenSourceNotice("ajv 8.18.0", "MIT", "web/package-lock.json"),
    OpenSourceNotice("ajv-formats 2.1.1", "MIT", "web/package-lock.json"),
    OpenSourceNotice("ajv-keywords 5.1.0", "MIT", "web/package-lock.json"),
    OpenSourceNotice("baseline-browser-mapping 2.10.0", "Apache-2.0", "web/package-lock.json"),
    OpenSourceNotice("browserslist 4.28.1", "MIT", "web/package-lock.json"),
    OpenSourceNotice("buffer-from 1.1.2", "MIT", "web/package-lock.json"),
    OpenSourceNotice("caniuse-lite 1.0.30001776", "CC-BY-4.0", "web/package-lock.json"),
    OpenSourceNotice("chokidar 4.0.3", "MIT", "web/package-lock.json"),
    OpenSourceNotice("chrome-trace-event 1.0.4", "MIT", "web/package-lock.json"),
    OpenSourceNotice("clone-deep 4.0.1", "MIT", "web/package-lock.json"),
    OpenSourceNotice("colorette 2.0.20", "MIT", "web/package-lock.json"),
    OpenSourceNotice("commander 2.20.3 / 12.1.0", "MIT", "web/package-lock.json"),
    OpenSourceNotice("cross-spawn 7.0.6", "MIT", "web/package-lock.json"),
    OpenSourceNotice("css-loader 7.1.4", "MIT", "web/package-lock.json"),
    OpenSourceNotice("cssesc 3.0.0", "MIT", "web/package-lock.json"),
    OpenSourceNotice("detect-libc 2.1.2", "Apache-2.0", "web/package-lock.json"),
    OpenSourceNotice("electron-to-chromium 1.5.307", "ISC", "web/package-lock.json"),
    OpenSourceNotice("enhanced-resolve 5.20.0", "MIT", "web/package-lock.json"),
    OpenSourceNotice("envinfo 7.21.0", "MIT", "web/package-lock.json"),
    OpenSourceNotice("es-module-lexer 2.0.0", "MIT", "web/package-lock.json"),
    OpenSourceNotice("escalade 3.2.0", "MIT", "web/package-lock.json"),
    OpenSourceNotice("eslint-scope 5.1.1", "BSD-2-Clause", "web/package-lock.json"),
    OpenSourceNotice("esrecurse 4.3.0", "BSD-2-Clause", "web/package-lock.json"),
    OpenSourceNotice("estraverse 4.3.0 / 5.3.0", "BSD-2-Clause", "web/package-lock.json"),
    OpenSourceNotice("events 3.3.0", "MIT", "web/package-lock.json"),
    OpenSourceNotice("fast-deep-equal 3.1.3", "MIT", "web/package-lock.json"),
    OpenSourceNotice("fast-uri 3.1.0", "BSD-3-Clause", "web/package-lock.json"),
    OpenSourceNotice("fastest-levenshtein 1.0.16", "MIT", "web/package-lock.json"),
    OpenSourceNotice("find-up 4.1.0", "MIT", "web/package-lock.json"),
    OpenSourceNotice("flat 5.0.2", "BSD-3-Clause", "web/package-lock.json"),
    OpenSourceNotice("function-bind 1.1.2", "MIT", "web/package-lock.json"),
    OpenSourceNotice("glob-to-regexp 0.4.1", "BSD-2-Clause", "web/package-lock.json"),
    OpenSourceNotice("graceful-fs 4.2.11", "ISC", "web/package-lock.json"),
    OpenSourceNotice("has-flag 4.0.0", "MIT", "web/package-lock.json"),
    OpenSourceNotice("hasown 2.0.2", "MIT", "web/package-lock.json"),
    OpenSourceNotice("icss-utils 5.1.0", "ISC", "web/package-lock.json"),
    OpenSourceNotice("immutable 5.1.5", "MIT", "web/package-lock.json"),
    OpenSourceNotice("import-local 3.2.0", "MIT", "web/package-lock.json"),
    OpenSourceNotice("interpret 3.1.1", "MIT", "web/package-lock.json"),
    OpenSourceNotice("is-core-module 2.16.1", "MIT", "web/package-lock.json"),
    OpenSourceNotice("is-extglob 2.1.1", "MIT", "web/package-lock.json"),
    OpenSourceNotice("is-glob 4.0.3", "MIT", "web/package-lock.json"),
    OpenSourceNotice("is-plain-object 2.0.4", "MIT", "web/package-lock.json"),
    OpenSourceNotice("isexe 2.0.0", "ISC", "web/package-lock.json"),
    OpenSourceNotice("isobject 3.0.1", "MIT", "web/package-lock.json"),
    OpenSourceNotice("jest-worker 27.5.1", "MIT", "web/package-lock.json"),
    OpenSourceNotice("json-parse-even-better-errors 2.3.1", "MIT", "web/package-lock.json"),
    OpenSourceNotice("json-schema-traverse 1.0.0", "MIT", "web/package-lock.json"),
    OpenSourceNotice("kind-of 6.0.3", "MIT", "web/package-lock.json"),
    OpenSourceNotice("loader-runner 4.3.1", "MIT", "web/package-lock.json"),
    OpenSourceNotice("locate-path 5.0.0", "MIT", "web/package-lock.json"),
    OpenSourceNotice("merge-stream 2.0.0", "MIT", "web/package-lock.json"),
    OpenSourceNotice("mime-db 1.52.0", "MIT", "web/package-lock.json"),
    OpenSourceNotice("mime-types 2.1.35", "MIT", "web/package-lock.json"),
    OpenSourceNotice("mini-css-extract-plugin 2.10.0", "MIT", "web/package-lock.json"),
    OpenSourceNotice("nanoid 3.3.11", "MIT", "web/package-lock.json"),
    OpenSourceNotice("neo-async 2.6.2", "MIT", "web/package-lock.json"),
    OpenSourceNotice("node-addon-api 7.1.1", "MIT", "web/package-lock.json"),
    OpenSourceNotice("node-releases 2.0.36", "MIT", "web/package-lock.json"),
    OpenSourceNotice("p-limit / p-locate / p-try", "MIT", "web/package-lock.json"),
    OpenSourceNotice("path-exists / path-key / path-parse", "MIT", "web/package-lock.json"),
    OpenSourceNotice("picocolors 1.1.1", "ISC", "web/package-lock.json"),
    OpenSourceNotice("picomatch 4.0.3", "MIT", "web/package-lock.json"),
    OpenSourceNotice("pkg-dir 4.2.0", "MIT", "web/package-lock.json"),
    OpenSourceNotice("postcss 8.5.8", "MIT", "web/package-lock.json"),
    OpenSourceNotice("postcss-modules-*", "ISC / MIT", "web/package-lock.json"),
    OpenSourceNotice("postcss-selector-parser 7.1.1", "MIT", "web/package-lock.json"),
    OpenSourceNotice("postcss-value-parser 4.2.0", "MIT", "web/package-lock.json"),
    OpenSourceNotice("readdirp 4.1.2", "MIT", "web/package-lock.json"),
    OpenSourceNotice("rechoir 0.8.0", "MIT", "web/package-lock.json"),
    OpenSourceNotice("require-from-string 2.0.2", "MIT", "web/package-lock.json"),
    OpenSourceNotice("resolve 1.22.11", "MIT", "web/package-lock.json"),
    OpenSourceNotice("resolve-cwd 3.0.0", "MIT", "web/package-lock.json"),
    OpenSourceNotice("resolve-from 5.0.0", "MIT", "web/package-lock.json"),
    OpenSourceNotice("sass 1.97.3", "MIT", "web/package-lock.json"),
    OpenSourceNotice("sass-loader 16.0.7", "MIT", "web/package-lock.json"),
    OpenSourceNotice("schema-utils 4.3.3", "MIT", "web/package-lock.json"),
    OpenSourceNotice("semver 7.7.4", "ISC", "web/package-lock.json"),
    OpenSourceNotice("shallow-clone 3.0.1", "MIT", "web/package-lock.json"),
    OpenSourceNotice("shebang-command / shebang-regex", "MIT", "web/package-lock.json"),
    OpenSourceNotice("source-map 0.6.1", "BSD-3-Clause", "web/package-lock.json"),
    OpenSourceNotice("source-map-js 1.2.1", "BSD-3-Clause", "web/package-lock.json"),
    OpenSourceNotice("source-map-support 0.5.21", "MIT", "web/package-lock.json"),
    OpenSourceNotice("supports-color 8.1.1", "MIT", "web/package-lock.json"),
    OpenSourceNotice("supports-preserve-symlinks-flag 1.0.0", "MIT", "web/package-lock.json"),
    OpenSourceNotice("tapable 2.3.0", "MIT", "web/package-lock.json"),
    OpenSourceNotice("terser 5.46.0", "BSD-2-Clause", "web/package-lock.json"),
    OpenSourceNotice("terser-webpack-plugin 5.3.17", "MIT", "web/package-lock.json"),
    OpenSourceNotice("undici-types 7.18.2", "MIT", "web/package-lock.json"),
    OpenSourceNotice("update-browserslist-db 1.2.3", "MIT", "web/package-lock.json"),
    OpenSourceNotice("util-deprecate 1.0.2", "MIT", "web/package-lock.json"),
    OpenSourceNotice("watchpack 2.5.1", "MIT", "web/package-lock.json"),
    OpenSourceNotice("webpack 5.105.4", "MIT", "web/package-lock.json"),
    OpenSourceNotice("webpack-cli 6.0.1", "MIT", "web/package-lock.json"),
    OpenSourceNotice("webpack-merge 6.0.1", "MIT", "web/package-lock.json"),
    OpenSourceNotice("webpack-sources 3.3.4", "MIT", "web/package-lock.json"),
    OpenSourceNotice("which 2.0.2", "ISC", "web/package-lock.json"),
    OpenSourceNotice("wildcard 2.0.1", "MIT", "web/package-lock.json")
)

private fun sourceRepoUrl(): String =
    "https://github.com/${BuildConfig.SOURCE_REPO_OWNER}/${BuildConfig.SOURCE_REPO_NAME}"

private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

@Composable
private fun SettingsHero(
    login: String?,
    forkName: String?,
    themeMode: String
) {
    ExpressiveHeroCard(
        title = login?.let { stringResource(R.string.settings_connected_github, it) }
            ?: stringResource(R.string.settings_center_title),
        subtitle = forkName ?: stringResource(R.string.settings_center_subtitle),
        icon = Icons.Default.Tune,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        badge = {
            ExpressiveStatusChip(
                label = when (themeMode) {
                    "dark" -> stringResource(R.string.settings_dark_theme)
                    "light" -> stringResource(R.string.settings_light_theme)
                    else -> stringResource(R.string.settings_theme_system)
                },
                icon = Icons.Default.Palette,
                color = MaterialTheme.colorScheme.primary
            )
            ExpressiveStatusChip(
                label = if (forkName != null) {
                    stringResource(R.string.settings_fork_connected)
                } else {
                    stringResource(R.string.settings_waiting_fork)
                },
                icon = Icons.Default.ForkRight,
                color = if (forkName != null) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
            )
        }
    )
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    ExpressiveSectionCard(
        title = title,
        subtitle = when (title) {
            stringResource(R.string.settings_account) -> stringResource(R.string.settings_group_account_desc)
            stringResource(R.string.settings_build) -> stringResource(R.string.settings_group_build_desc)
            stringResource(R.string.settings_notification) -> stringResource(R.string.settings_group_notification_desc)
            stringResource(R.string.settings_navigation) -> stringResource(R.string.settings_group_navigation_desc)
            stringResource(R.string.settings_language) -> stringResource(R.string.settings_language_desc)
            stringResource(R.string.settings_theme) -> stringResource(R.string.settings_group_theme_desc)
            "ReSukiSU" -> stringResource(R.string.settings_group_backend_desc, "ReSukiSU")
            "SukiSU" -> stringResource(R.string.settings_group_backend_desc, "SukiSU")
            "KernelSU" -> stringResource(R.string.settings_group_backend_desc, "KernelSU")
            stringResource(R.string.settings_manager_settings) -> stringResource(R.string.settings_group_manager_settings_desc)
            stringResource(R.string.settings_system_tools) -> stringResource(R.string.settings_group_system_tools_desc)
            stringResource(R.string.settings_allowlist) -> stringResource(R.string.settings_group_allowlist_desc)
            stringResource(R.string.settings_tool_status) -> stringResource(R.string.settings_group_tool_status_desc)
            stringResource(R.string.settings_local_templates) -> stringResource(R.string.settings_group_local_templates_desc)
            stringResource(R.string.settings_status) -> stringResource(R.string.settings_group_status_desc)
            stringResource(R.string.settings_edit_template) -> stringResource(R.string.settings_group_edit_template_desc)
            stringResource(R.string.settings_appearance_mode) -> stringResource(R.string.settings_group_appearance_desc)
            stringResource(R.string.settings_color_source) -> stringResource(R.string.settings_group_color_source_desc)
            stringResource(R.string.settings_custom_colors) -> stringResource(R.string.settings_group_custom_colors_desc)
            stringResource(R.string.settings_background) -> stringResource(R.string.settings_group_background_desc)
            else -> stringResource(R.string.settings_group_about_desc)
        },
        icon = when (title) {
            stringResource(R.string.settings_account) -> Icons.Default.AccountCircle
            stringResource(R.string.settings_build) -> Icons.Default.Build
            stringResource(R.string.settings_notification) -> Icons.Default.Notifications
            stringResource(R.string.settings_navigation) -> Icons.Default.ArrowBack
            stringResource(R.string.settings_language) -> Icons.Default.Language
            stringResource(R.string.settings_theme) -> Icons.Default.Palette
            "ReSukiSU", "SukiSU", "KernelSU" -> Icons.Default.AdminPanelSettings
            stringResource(R.string.settings_manager_settings) -> Icons.Default.AdminPanelSettings
            stringResource(R.string.settings_system_tools) -> Icons.Default.Build
            stringResource(R.string.settings_allowlist) -> Icons.Default.VerifiedUser
            stringResource(R.string.settings_tool_status) -> Icons.Default.Info
            stringResource(R.string.settings_local_templates) -> Icons.Default.Apps
            stringResource(R.string.settings_status) -> Icons.Default.Info
            stringResource(R.string.settings_edit_template) -> Icons.Default.Edit
            stringResource(R.string.settings_appearance_mode) -> Icons.Default.BrightnessMedium
            stringResource(R.string.settings_color_source) -> Icons.Default.AutoAwesome
            stringResource(R.string.settings_custom_colors) -> Icons.Default.Palette
            stringResource(R.string.settings_background) -> Icons.Default.Image
            else -> Icons.Default.Info
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
private fun WorkflowForegroundRefreshIntervalPicker(
    selectedSec: Int,
    onSelect: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_workflow_foreground_refresh_interval),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PreferencesRepository.WORKFLOW_FOREGROUND_REFRESH_INTERVALS_SEC.sorted().forEach { sec ->
                FilterChip(
                    selected = selectedSec == sec,
                    onClick = { onSelect(sec) },
                    label = {
                        Text(
                            stringResource(R.string.settings_workflow_foreground_refresh_interval_sec, sec),
                            maxLines = 1
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SwitchSettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    ExpressiveSwitchItem(
        title = title,
        subtitle = subtitle,
        icon = icon,
        checked = checked,
        enabled = enabled,
        onCheckedChange = onCheckedChange
    )
}

@Composable
private fun DownloadDirectorySettingsItem(
    value: String,
    onValueChange: (String) -> Unit
) {
    val context = LocalContext.current
    val defaultDirectory = remember { DownloadDirectoryUtils.defaultDirectoryPath() }
    val needsAllFilesAccess = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()
    val unsupportedTreeMessage = stringResource(R.string.settings_download_directory_tree_unsupported)
    val restoredMessage = stringResource(R.string.settings_download_directory_default_restored)
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
                Toast.makeText(context, unsupportedTreeMessage, Toast.LENGTH_SHORT).show()
            } else {
                onValueChange(selectedPath)
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ExpressiveListItem(
            title = stringResource(R.string.settings_download_directory),
            subtitle = stringResource(R.string.settings_download_directory_desc),
            leadingIcon = Icons.Default.FolderOpen
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            placeholder = { Text(defaultDirectory) },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { folderPicker.launch(null) },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.settings_download_directory_choose))
            }
            TextButton(
                onClick = {
                    onValueChange(defaultDirectory)
                    Toast.makeText(context, restoredMessage, Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.settings_download_directory_reset))
            }
        }
        AnimatedVisibility(visible = needsAllFilesAccess) {
            OutlinedButton(
                onClick = { openAllFilesAccessSettings(context) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.FolderSpecial, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.settings_download_directory_storage_permission))
            }
        }
    }
}

private fun openAllFilesAccessSettings(context: android.content.Context) {
    val packageUri = Uri.parse("package:${context.packageName}")
    val appSettings = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, packageUri)
    runCatching {
        context.startActivity(appSettings)
    }.getOrElse {
        context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
    }
}

@Composable
private fun MirrorSettingsItem(
    value: String,
    onValueChange: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ExpressiveListItem(
            title = stringResource(R.string.settings_download_mirror),
            subtitle = stringResource(R.string.settings_download_mirror_desc),
            leadingIcon = Icons.Default.Public
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            placeholder = { Text("https://hk.gh-proxy.org/") },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun LanguageSettingsItem(
    current: String,
    onSelect: (String) -> Unit
) {
    val options = listOf(
        Triple(LocaleHelper.LANG_ZH, stringResource(R.string.settings_language_zh), Icons.Default.Language),
        Triple(LocaleHelper.LANG_EN, stringResource(R.string.settings_language_en), Icons.Default.Language),
        Triple(LocaleHelper.LANG_RU, stringResource(R.string.settings_language_ru), Icons.Default.Language)
    )
    options.forEach { (lang, label, icon) ->
        val selected = current == lang
        ExpressiveListItem(
            title = label,
            leadingIcon = icon,
            selected = selected,
            trailingContent = {
                if (selected) Icon(Icons.Default.Check, null)
            },
            onClick = { onSelect(lang) }
        )
    }
}
