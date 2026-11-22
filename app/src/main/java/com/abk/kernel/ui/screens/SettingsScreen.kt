@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.abk.kernel.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.abk.kernel.BuildConfig
import com.abk.kernel.R
import com.abk.kernel.extensions.AbkExtensionManagerScreen
import com.abk.kernel.utils.DownloadDirectoryUtils
import com.abk.kernel.utils.DownloadUtils
import com.abk.kernel.utils.LocaleHelper
import com.abk.kernel.ui.blur.BlurScreenScaffold
import com.abk.kernel.ui.components.AbkScreenHorizontalPadding
import com.abk.kernel.ui.components.AbkSegmentedButtonOption
import com.abk.kernel.ui.components.AbkSingleChoiceSegmentedButtonRow
import com.abk.kernel.ui.components.AbkInlineLoadingPill
import com.abk.kernel.ui.components.AppPageBackground
import com.abk.kernel.ui.components.ObserveChildPageVisibility
import com.abk.kernel.ui.components.childPageOverlayEnterTransition
import com.abk.kernel.ui.components.childPageOverlayExitTransition
import com.abk.kernel.ui.components.childPageScrimExitTransition
import com.abk.kernel.ui.components.rememberChildPageBackController
import com.abk.kernel.ui.components.rememberChildPageOverlayTransition
import com.abk.kernel.ui.components.ExpressiveHeroCard
import com.abk.kernel.ui.components.ExpressiveSectionCard
import com.abk.kernel.ui.components.ExpressiveStatusChip
import com.abk.kernel.ui.components.ExpressiveSwitchItem
import com.abk.kernel.ui.components.ExpressiveTopBar
import com.abk.kernel.ui.components.rememberAbkInteractiveRefreshPresentation
import com.abk.kernel.ui.theme.appPageBackgroundColor
import com.abk.kernel.ui.theme.uiSurfaceColor
import com.abk.kernel.data.model.APP_UPDATE_LINE_DEV
import com.abk.kernel.data.model.APP_UPDATE_LINE_NORMAL
import com.abk.kernel.data.model.APP_UPDATE_STABILITY_STABLE
import com.abk.kernel.data.model.APP_UPDATE_STABILITY_UNSTABLE
import com.abk.kernel.data.model.AppUpdateCheckResult
import com.abk.kernel.data.repository.PreferencesRepository
import com.abk.kernel.data.repository.Result
import com.abk.kernel.data.model.normalizeAppUpdateLine
import com.abk.kernel.data.model.normalizeAppUpdateStability
import com.abk.kernel.viewmodel.MainUiState
import com.abk.kernel.viewmodel.MainViewModel
import com.abk.kernel.viewmodel.exportDiagnosticBundle
import io.github.xingguangcuican6666.abk.ui.components.AbkOption
import io.github.xingguangcuican6666.abk.ui.components.AbkSettingSwitch
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcons
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    vm: MainViewModel,
    outerPadding: PaddingValues = PaddingValues(0.dp),
    onChildPageVisibleChange: (Boolean) -> Unit = {},
    onOpenInstalledModules: () -> Unit = {}
) {
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showKernelRunnerSettings by remember { mutableStateOf(false) }
    var exportingDiagnostics by remember { mutableStateOf(false) }
    var showThemeSettings by rememberSaveable { mutableStateOf(false) }
    var showAppProfileTemplates by rememberSaveable { mutableStateOf(false) }
    var showManagerTools by rememberSaveable { mutableStateOf(false) }
    var showKernelCapabilities by rememberSaveable { mutableStateOf(false) }
    var showSusfsControl by rememberSaveable { mutableStateOf(false) }
    var showAboutPage by rememberSaveable { mutableStateOf(false) }
    var showOpenSourceLicenses by rememberSaveable { mutableStateOf(false) }
    var showExtensionManagerPage by rememberSaveable { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val showChildPage = showThemeSettings || showAppProfileTemplates || showManagerTools || showKernelCapabilities ||
        showSusfsControl || showAboutPage || showOpenSourceLicenses || showExtensionManagerPage
    val childPageTransition = rememberChildPageOverlayTransition(
        visible = showChildPage,
        label = "settings-child-page"
    )
    val motionScheme = MaterialTheme.motionScheme

    LaunchedEffect(Unit) {
        vm.refreshManagerSettings(force = true)
        vm.refreshSusfsState(force = true)
    }

    LaunchedEffect(state.hasNativeManagerPermission) {
        if (!state.hasNativeManagerPermission) {
            showAppProfileTemplates = false
            showManagerTools = false
        }
    }

    LaunchedEffect(state.appUpdatePendingInstallPath) {
        val apkPath = state.appUpdatePendingInstallPath ?: return@LaunchedEffect
        launchAppUpdateInstaller(context, apkPath)
        vm.consumeAppUpdatePendingInstallPath()
    }

    fun closeChildPage() {
        showThemeSettings = false
        showAppProfileTemplates = false
        showManagerTools = false
        showKernelCapabilities = false
        showSusfsControl = false
        showAboutPage = false
        showOpenSourceLicenses = false
        showExtensionManagerPage = false
    }

    val childPageBack = rememberChildPageBackController(
        enabled = showChildPage,
        predictiveBackEnabled = state.predictiveBackEnabled,
        onBack = ::closeChildPage,
    )

    ObserveChildPageVisibility(
        transition = childPageTransition,
        onVisibleChange = onChildPageVisibleChange,
        onAfterExitAnimation = { childPageBack.resetProgress() }
    )

    DisposableEffect(Unit) {
        onDispose { onChildPageVisibleChange(false) }
    }

    fun openThemeSettings() {
        childPageBack.resetProgress()
        showAppProfileTemplates = false
        showManagerTools = false
        showKernelCapabilities = false
        showSusfsControl = false
        showAboutPage = false
        showOpenSourceLicenses = false
        showExtensionManagerPage = false
        showThemeSettings = true
    }

    fun openAppProfileTemplates() {
        childPageBack.resetProgress()
        showThemeSettings = false
        showManagerTools = false
        showKernelCapabilities = false
        showSusfsControl = false
        showAboutPage = false
        showOpenSourceLicenses = false
        showExtensionManagerPage = false
        showAppProfileTemplates = true
        vm.refreshAppProfileTemplates()
    }

    fun openManagerTools() {
        childPageBack.resetProgress()
        showThemeSettings = false
        showAppProfileTemplates = false
        showSusfsControl = false
        showKernelCapabilities = false
        showAboutPage = false
        showOpenSourceLicenses = false
        showExtensionManagerPage = false
        showManagerTools = true
        vm.refreshManagerTools(force = true)
    }

    fun openKernelCapabilities() {
        childPageBack.resetProgress()
        showThemeSettings = false
        showAppProfileTemplates = false
        showManagerTools = false
        showSusfsControl = false
        showAboutPage = false
        showOpenSourceLicenses = false
        showExtensionManagerPage = false
        showKernelCapabilities = true
        vm.refreshKernelCapabilities(force = true)
    }

    fun openSusfsControl() {
        childPageBack.resetProgress()
        showThemeSettings = false
        showAppProfileTemplates = false
        showManagerTools = false
        showKernelCapabilities = false
        showAboutPage = false
        showOpenSourceLicenses = false
        showExtensionManagerPage = false
        showSusfsControl = true
        vm.refreshSusfsState(force = true)
    }

    fun openAboutPage() {
        childPageBack.resetProgress()
        showThemeSettings = false
        showAppProfileTemplates = false
        showManagerTools = false
        showKernelCapabilities = false
        showOpenSourceLicenses = false
        showExtensionManagerPage = false
        showAboutPage = true
    }

    fun openOpenSourceLicenses() {
        childPageBack.resetProgress()
        showThemeSettings = false
        showAppProfileTemplates = false
        showManagerTools = false
        showKernelCapabilities = false
        showAboutPage = false
        showExtensionManagerPage = false
        showOpenSourceLicenses = true
    }

    fun openExtensionManagerPage() {
        childPageBack.resetProgress()
        showThemeSettings = false
        showAppProfileTemplates = false
        showManagerTools = false
        showKernelCapabilities = false
        showAboutPage = false
        showOpenSourceLicenses = false
        showExtensionManagerPage = true
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

    if (showLogoutDialog) {
        SettingsLogoutDialog(
            onDismiss = { showLogoutDialog = false },
            onConfirm = { showLogoutDialog = false; vm.logout() },
        )
    }

    if (showKernelRunnerSettings) {
        KernelRunnerSettingsDialog(
            state = state.kernelRunnerSettings,
            onRefresh = vm::refreshKernelRunnerSettings,
            onSave = vm::saveKernelRunnerSettings,
            onDismiss = { showKernelRunnerSettings = false }
        )
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val childPageTopInset = outerPadding.calculateTopPadding()
        val childPageBottomInset = outerPadding.calculateBottomPadding()
        val childPageModifier = Modifier
            .fillMaxWidth()
            .height(maxHeight + childPageTopInset + childPageBottomInset)
            .offset(y = -childPageTopInset)
        BlurScreenScaffold(
            blurConfig = state.blurConfig,
            containerColor = appPageBackgroundColor(uiSurfaceColor(MaterialTheme.colorScheme.surface)),
            topBar = {
                ExpressiveTopBar(
                    title = stringResource(R.string.settings_title),
                    scrollBehavior = scrollBehavior,
                    enableBlur = state.blurEnabled
                )
            }
        ) { topBarHeight ->
            SettingsMainContent(
                topBarHeight = topBarHeight,
                outerPadding = outerPadding,
                state = state,
                vm = vm,
                scrollBehavior = scrollBehavior,
                onLogout = { showLogoutDialog = true },
                onOpenKernelRunnerSettings = { showKernelRunnerSettings = true },
                onOpenThemeSettings = ::openThemeSettings,
                onOpenAppProfileTemplates = ::openAppProfileTemplates,
                onOpenManagerTools = ::openManagerTools,
                onOpenKernelCapabilities = ::openKernelCapabilities,
                onOpenSusfsControl = ::openSusfsControl,
                onOpenInstalledModules = onOpenInstalledModules,
                onAbout = ::openAboutPage,
                onOpenSourceLicenses = ::openOpenSourceLicenses,
                onOpenExtensionManager = ::openExtensionManagerPage,
                exportingDiagnostics = exportingDiagnostics,
                onExportDiagnostics = ::exportDiagnostics
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
                BlurScreenScaffold(
                    blurConfig = state.blurConfig,
                    containerColor = Color.Transparent,
                    topBar = {
                        ExpressiveTopBar(
                            title = stringResource(R.string.settings_theme),
                            navigationIcon = {
                                IconButton(onClick = childPageBack::requestDismiss) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.settings_back))
                                }
                            },
                            enableBlur = state.blurEnabled
                        )
                    }
                ) { topBarHeight ->
                    ThemeSettingsScreen(
                        topBarHeight = topBarHeight,
                        themeMode = state.themeMode,
                        dynamicColorEnabled = state.dynamicColorEnabled,
                        customThemeColorArgb = state.customThemeColorArgb,
                        customAccentColorArgb = state.customAccentColorArgb,
                        customFont = state.customFont,
                        backgroundUri = state.customBackgroundUri,
                        backgroundImageEnabled = state.backgroundImageEnabled,
                        uiSurfaceAlpha = state.uiSurfaceAlpha,
                        blurEnabled = state.blurEnabled,
                        blurBackgroundExpEnabled = state.blurBackgroundExpEnabled,
                        predictiveBackEnabled = state.predictiveBackEnabled,
                        onThemeModeChange = { value -> vm.setThemeMode(value) },
                        onDynamicColorEnabledChange = { enabled, themeColor, accentColor ->
                            vm.setDynamicColorEnabled(enabled, themeColor, accentColor)
                        },
                        onCustomThemeColorsChange = { themeColor, accentColor ->
                            vm.setCustomThemeColors(themeColor, accentColor)
                        },
                        onImportFont = vm::importCustomFont,
                        onUseSystemFont = vm::useSystemFont,
                        onBackgroundImageChange = { uri -> vm.setBackgroundImageUri(uri) },
                        onBackgroundImageEnabledChange = { enabled -> vm.setBackgroundImageEnabled(enabled) },
                        onUiSurfaceAlphaChange = { alpha -> vm.setUiSurfaceAlpha(alpha) },
                        onUiSurfaceAlphaPreviewChange = { alpha -> vm.setUiSurfaceAlphaPreview(alpha) },
                        onBlurEnabledChange = vm::setBlurEnabled,
                        onBlurBackgroundExpEnabledChange = vm::setBlurBackgroundExpEnabled,
                        onPredictiveBackEnabledChange = vm::setPredictiveBackEnabled,
                        onUiSurfaceAlphaSync = vm::syncUiSurfaceAlphaPreview
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
            val refreshPresentation = rememberAbkInteractiveRefreshPresentation(loading = state.appProfileTemplatesLoading)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(childPageBack.backTransformModifier())
            ) {
                SettingsPageBackground(
                    backgroundUri = state.customBackgroundUri,
                    backgroundImageEnabled = state.backgroundImageEnabled
                )
                BlurScreenScaffold(
                    blurConfig = state.blurConfig,
                    containerColor = Color.Transparent,
                    topBar = {
                        ExpressiveTopBar(
                            title = stringResource(R.string.settings_app_profile_templates),
                            navigationIcon = {
                                IconButton(onClick = childPageBack::requestDismiss) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.settings_back))
                                }
                            },
                            enableBlur = state.blurEnabled,
                            actions = {
                                IconButton(onClick = {
                                    refreshPresentation.beginRefresh()
                                    vm.refreshAppProfileTemplates()
                                }) {
                                    Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                                }
                            }
                        )
                    }
                ) { topBarHeight ->
                    AppProfileTemplateSettingsScreen(
                        topBarHeight = topBarHeight,
                        state = state,
                        showRefreshLoading = refreshPresentation.showLoading && state.appProfileTemplates.isNotEmpty(),
                        onRefresh = {
                            refreshPresentation.beginRefresh()
                            vm.refreshAppProfileTemplates()
                        },
                        onSelect = vm::selectAppProfileTemplate,
                        onSave = vm::saveAppProfileTemplate,
                        onDelete = vm::deleteAppProfileTemplate
                    )
                }
            }
        }

        childPageTransition.AnimatedVisibility(
            visible = { it && showExtensionManagerPage },
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
                AbkExtensionManagerScreen(
                    focusExtensionId = null,
                    bootstrapMode = false,
                    onBack = childPageBack::requestDismiss,
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color.Transparent,
                    blurEnabled = state.blurEnabled,
                    blurBackgroundExpEnabled = state.blurBackgroundExpEnabled,
                    backgroundUri = state.customBackgroundUri,
                    backgroundImageEnabled = state.backgroundImageEnabled,
                )
            }
        }

        childPageTransition.AnimatedVisibility(
            visible = { it && showManagerTools },
            enter = childPageOverlayEnterTransition(state.predictiveBackEnabled, motionScheme),
            exit = childPageOverlayExitTransition(state.predictiveBackEnabled, motionScheme),
            modifier = childPageModifier
        ) {
            val refreshPresentation = rememberAbkInteractiveRefreshPresentation(loading = state.managerToolsLoading)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(childPageBack.backTransformModifier())
            ) {
                SettingsPageBackground(
                    backgroundUri = state.customBackgroundUri,
                    backgroundImageEnabled = state.backgroundImageEnabled
                )
                BlurScreenScaffold(
                    blurConfig = state.blurConfig,
                    containerColor = Color.Transparent,
                    topBar = {
                        ExpressiveTopBar(
                            title = stringResource(R.string.settings_tools),
                            navigationIcon = {
                                IconButton(onClick = childPageBack::requestDismiss) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.settings_back))
                                }
                            },
                            enableBlur = state.blurEnabled,
                            actions = {
                                IconButton(onClick = {
                                    refreshPresentation.beginRefresh()
                                    vm.refreshManagerTools(force = true)
                                }) {
                                    Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                                }
                            }
                        )
                    }
                ) { topBarHeight ->
                    ManagerToolsSettingsScreen(
                        topBarHeight = topBarHeight,
                        state = state,
                        showRefreshLoading = refreshPresentation.showLoading,
                        onSelinuxChange = vm::setSelinuxEnforcing,
                        onBackupAllowlist = vm::backupRootGrantAllowlist,
                        onRestoreAllowlist = vm::restoreRootGrantAllowlist
                    )
                }
            }
        }

        childPageTransition.AnimatedVisibility(
            visible = { it && showKernelCapabilities },
            enter = childPageOverlayEnterTransition(state.predictiveBackEnabled, motionScheme),
            exit = childPageOverlayExitTransition(state.predictiveBackEnabled, motionScheme),
            modifier = childPageModifier
        ) {
            val refreshPresentation = rememberAbkInteractiveRefreshPresentation(loading = state.kernelCapabilitiesLoading)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(childPageBack.backTransformModifier())
            ) {
                SettingsPageBackground(
                    backgroundUri = state.customBackgroundUri,
                    backgroundImageEnabled = state.backgroundImageEnabled
                )
                BlurScreenScaffold(
                    blurConfig = state.blurConfig,
                    containerColor = Color.Transparent,
                    topBar = {
                        ExpressiveTopBar(
                            title = stringResource(R.string.settings_kernel_capabilities),
                            navigationIcon = {
                                IconButton(onClick = childPageBack::requestDismiss) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.settings_back))
                                }
                            },
                            enableBlur = state.blurEnabled,
                            actions = {
                                IconButton(onClick = {
                                    refreshPresentation.beginRefresh()
                                    vm.refreshKernelCapabilities(force = true)
                                }) {
                                    Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                                }
                            }
                        )
                    }
                ) { topBarHeight ->
                    KernelCapabilitiesSettingsScreen(
                        topBarHeight = topBarHeight,
                        state = state,
                        showRefreshLoading = refreshPresentation.showLoading,
                        onRefresh = {
                            refreshPresentation.beginRefresh()
                            vm.refreshKernelCapabilities(force = true)
                        },
                        onTcpAlgorithmSelected = vm::setTcpCongestionControlAlgorithm
                    )
                }
            }
        }

        childPageTransition.AnimatedVisibility(
            visible = { it && showSusfsControl },
            enter = childPageOverlayEnterTransition(state.predictiveBackEnabled, motionScheme),
            exit = childPageOverlayExitTransition(state.predictiveBackEnabled, motionScheme),
            modifier = childPageModifier
        ) {
            val refreshPresentation = rememberAbkInteractiveRefreshPresentation(loading = state.susfsLoading)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(childPageBack.backTransformModifier())
            ) {
                SettingsPageBackground(
                    backgroundUri = state.customBackgroundUri,
                    backgroundImageEnabled = state.backgroundImageEnabled
                )
                BlurScreenScaffold(
                    blurConfig = state.blurConfig,
                    containerColor = Color.Transparent,
                    topBar = {
                        ExpressiveTopBar(
                            title = stringResource(R.string.susfs_title),
                            navigationIcon = {
                                IconButton(onClick = childPageBack::requestDismiss) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.settings_back))
                                }
                            },
                            enableBlur = state.blurEnabled,
                            actions = {
                                IconButton(
                                    enabled = !state.susfsSaving,
                                    onClick = {
                                        refreshPresentation.beginRefresh()
                                        vm.refreshSusfsState(force = true)
                                    }
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                                }
                            }
                        )
                    }
                ) { topBarHeight ->
                    SusfsControlScreen(
                        topBarHeight = topBarHeight,
                        state = state,
                        showRefreshLoading = refreshPresentation.showLoading,
                        onApply = vm::applySusfsConfig,
                        onReset = vm::resetSusfsConfig,
                        onRefresh = {
                            refreshPresentation.beginRefresh()
                            vm.refreshSusfsState(force = true)
                        }
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
                BlurScreenScaffold(
                    blurConfig = state.blurConfig,
                    containerColor = Color.Transparent,
                    topBar = {
                        ExpressiveTopBar(
                            title = stringResource(R.string.settings_about_title),
                            navigationIcon = {
                                IconButton(onClick = childPageBack::requestDismiss) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.settings_back))
                                }
                            },
                            enableBlur = state.blurEnabled
                        )
                    }
                ) { topBarHeight ->
                    AboutRepositoryScreen(
                        topBarHeight = topBarHeight,
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
                BlurScreenScaffold(
                    blurConfig = state.blurConfig,
                    containerColor = Color.Transparent,
                    topBar = {
                        ExpressiveTopBar(
                            title = stringResource(R.string.settings_open_source_licenses),
                            navigationIcon = {
                                IconButton(onClick = childPageBack::requestDismiss) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.settings_back))
                                }
                            },
                            enableBlur = state.blurEnabled
                        )
                    }
                ) { topBarHeight ->
                    OpenSourceLicensesScreen(
                        topBarHeight = topBarHeight,
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
    AppPageBackground(
        backgroundUri = backgroundUri,
        backgroundImageEnabled = backgroundImageEnabled
    )
}

@Composable
private fun SettingsMainContent(
    topBarHeight: Dp,
    outerPadding: PaddingValues,
    state: MainUiState,
    vm: MainViewModel,
    scrollBehavior: TopAppBarScrollBehavior,
    onLogout: () -> Unit,
    onOpenKernelRunnerSettings: () -> Unit,
    onOpenThemeSettings: () -> Unit,
    onOpenAppProfileTemplates: () -> Unit,
    onOpenManagerTools: () -> Unit,
    onOpenKernelCapabilities: () -> Unit,
    onOpenSusfsControl: () -> Unit,
    onOpenInstalledModules: () -> Unit,
    onAbout: () -> Unit,
    onOpenSourceLicenses: () -> Unit,
    onOpenExtensionManager: () -> Unit,
    exportingDiagnostics: Boolean,
    onExportDiagnostics: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AbkScreenHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(Modifier.height(topBarHeight + 16.dp))
        val user = state.user
        val forkUrl = state.forkRepo?.let { repo ->
            repo.htmlUrl.takeIf { it.isNotBlank() } ?: "https://github.com/${repo.fullName}"
        }
        SettingsAccountCard(
            login = user?.login,
            description = user?.let { it.name ?: it.htmlUrl },
            forkName = state.forkRepo?.fullName,
            canOpenFork = forkUrl != null,
            onLogin = { vm.openLoginOobe() },
            onLogout = onLogout,
            onOpenFork = { forkUrl?.let { openUrl(context, it) } },
            onOpenRunner = onOpenKernelRunnerSettings,
            avatar = user?.let { account ->
                {
                    AsyncImage(
                        model = account.avatarUrl,
                        contentDescription = null,
                        modifier = Modifier.size(42.dp).clip(CircleShape),
                    )
                }
            },
        )

        SettingsMainCard(title = stringResource(R.string.settings_build)) {
            AbkSettingSwitch(
                title = stringResource(R.string.settings_workflow_foreground_refresh),
                description = stringResource(R.string.settings_workflow_foreground_refresh_desc),
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
            AbkSettingSwitch(
                title = stringResource(R.string.settings_auto_download),
                description = stringResource(R.string.settings_auto_download_desc),
                checked = state.autoDownload,
                onCheckedChange = { vm.setAutoDownload(it) }
            )
            AbkSettingSwitch(
                title = stringResource(R.string.settings_prebuilt_gki),
                description = stringResource(R.string.settings_prebuilt_gki_desc),
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
            SettingsDownloadThreadControl(
                count = state.downloadThreadCount,
                onCountChange = vm::setDownloadThreadCount,
            )
            Spacer(Modifier.height(10.dp))
            val hasArtifacts = state.downloadedArtifacts.isNotEmpty()
            var showClearArtifactsDialog by remember { mutableStateOf(false) }
            SettingsMainDestination(
                title = stringResource(R.string.settings_clear_artifacts),
                description = if (hasArtifacts) {
                    val count = state.downloadedArtifacts.size
                    val totalBytes = state.downloadedArtifacts.sumOf { it.sizeBytes }
                    "$count ${stringResource(R.string.settings_clear_artifacts_files)} · ${DownloadUtils.formatSize(totalBytes)}"
                } else {
                    stringResource(R.string.settings_clear_artifacts_empty)
                },
                icon = AbkIcons.Trash,
                enabled = hasArtifacts,
                onClick = { showClearArtifactsDialog = true },
            )
            if (showClearArtifactsDialog) {
                SettingsClearDownloadsDialog(
                    onDismiss = { showClearArtifactsDialog = false },
                    onConfirm = {
                        vm.clearAllDownloadedArtifacts()
                        showClearArtifactsDialog = false
                    },
                )
            }
        }

        SecuritySettingsGroup(
            state = state,
            vm = vm,
        )

        SettingsMainCard(title = stringResource(R.string.settings_app_update)) {
            AppUpdateStabilityPicker(
                selected = state.appUpdateStability,
                onSelect = vm::setAppUpdateStability
            )
            AppUpdateLinePicker(
                selected = state.appUpdateLine,
                onSelect = vm::setAppUpdateLine
            )
            SettingsAppUpdateFeedback(
                checking = state.appUpdateChecking,
                downloading = state.appUpdateDownloading,
                downloadProgress = state.appUpdateDownloadProgress,
                info = state.appUpdateInfo,
                checkSubtitle = appUpdateCheckSubtitle(state),
                resultSubtitle = state.appUpdateInfo?.let { appUpdateResultSubtitle(it) },
                error = state.appUpdateError,
                onCheck = vm::checkAppUpdate,
                onDownload = vm::downloadAndInstallAppUpdate,
            )
        }

        ManagerInjectedSettingsGroup(
            state = state,
            vm = vm,
            onOpenAppProfileTemplates = onOpenAppProfileTemplates,
            onOpenManagerTools = onOpenManagerTools,
            onOpenKernelCapabilities = onOpenKernelCapabilities,
            onOpenSusfsControl = onOpenSusfsControl,
            onOpenInstalledModules = onOpenInstalledModules
        )

        SettingsMainCard(title = stringResource(R.string.settings_notification)) {
            AbkSettingSwitch(
                title = stringResource(R.string.settings_notify_build),
                description = stringResource(R.string.settings_notify_build_desc),
                checked = state.notifyBuild,
                onCheckedChange = { vm.setNotifyBuild(it) }
            )
        }

        SettingsMainCard(title = stringResource(R.string.settings_language)) {
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

        SettingsMainCard(title = stringResource(R.string.settings_theme)) {
            SettingsMainDestination(
                title = stringResource(R.string.settings_color_appearance),
                description = "${themeModeLabel(state.themeMode)} · ${dynamicColorLabel(state.dynamicColorEnabled)}",
                icon = AbkIcons.Controls,
                onClick = onOpenThemeSettings,
            )
        }

        SettingsMainCard(title = stringResource(R.string.settings_extensions_title)) {
            SettingsMainDestination(
                title = stringResource(R.string.settings_extensions_manage),
                description = stringResource(R.string.settings_extensions_manage_desc),
                icon = AbkIcons.Modules,
                onClick = onOpenExtensionManager,
            )
        }

        SettingsMainCard(title = stringResource(R.string.settings_about)) {
            Text(stringResource(R.string.app_full_name), style = MaterialTheme.typography.titleMedium)
            Text("AnyBase Kernel v${BuildConfig.VERSION_NAME}")
            SettingsMainDestination(
                title = stringResource(R.string.settings_about),
                description = stringResource(R.string.settings_about_desc),
                icon = AbkIcons.Info,
                onClick = onAbout,
            )
            SettingsMainDestination(
                title = stringResource(R.string.settings_open_source_licenses),
                description = stringResource(R.string.settings_open_source_licenses_desc),
                icon = AbkIcons.InfoMark,
                onClick = onOpenSourceLicenses,
            )
            SettingsDiagnosticsAction(
                exporting = exportingDiagnostics,
                onExport = onExportDiagnostics,
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
    onOpenKernelCapabilities: () -> Unit,
    onOpenSusfsControl: () -> Unit,
    onOpenInstalledModules: () -> Unit
) {
    val hasInjectedSettings = state.managerSettingsItems.isNotEmpty()
    val refreshPresentation = rememberAbkInteractiveRefreshPresentation(loading = state.managerSettingsLoading)
    val showRefreshLoading = refreshPresentation.showLoading
    if (!hasInjectedSettings && !state.managerSettingsLoading && state.managerSettingsError == null) return

    SettingsManagerCard(
        title = state.managerSettingsTitle.ifBlank { stringResource(R.string.settings_manager_settings) },
        items = state.managerSettingsItems,
        loading = state.managerSettingsLoading,
        showRefreshLoading = showRefreshLoading,
        error = state.managerSettingsError,
        actionId = state.managerSettingActionId,
        onRetry = {
            refreshPresentation.beginRefresh()
            vm.refreshManagerSettings(force = true)
        },
        onNavigate = { id ->
            when (id) {
                "app_profile_templates" -> onOpenAppProfileTemplates()
                "manager_tools" -> onOpenManagerTools()
                "kernel_capabilities" -> onOpenKernelCapabilities()
                "susfs_control" -> onOpenSusfsControl()
                "kpm" -> onOpenInstalledModules()
            }
        },
        onModeChange = { id, index -> vm.setManagerSettingMode(id, index) },
        onCheckedChange = { id, checked -> vm.setManagerSettingChecked(id, checked) },
    )
}

@Composable
private fun SecuritySettingsGroup(
    state: MainUiState,
    vm: MainViewModel,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val canManageKeys = state.isLoggedIn && state.forkRepo != null
    var showImportDialog by remember { mutableStateOf(false) }
    var showDisableConfirm1 by remember { mutableStateOf(false) }
    var showDisableConfirm2 by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var showCustomSourceSecretDialog by remember { mutableStateOf(false) }
    var showDeleteCustomSourceSecretConfirm by remember { mutableStateOf(false) }
    var customSourcePat by remember { mutableStateOf("") }
    var importPublicKeyText by remember { mutableStateOf("") }
    var importPrivateKeyText by remember { mutableStateOf("") }
    var importError by remember { mutableStateOf<String?>(null) }
    var importPickerTarget by remember { mutableStateOf<SecurityKeyImportTarget?>(null) }
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
                SecurityKeyImportTarget.PUBLIC -> importPublicKeyText = text
                SecurityKeyImportTarget.PRIVATE -> importPrivateKeyText = text
            }
            importError = null
        }
    }
    LaunchedEffect(canManageKeys) {
        if (canManageKeys) vm.refreshCustomSourceSecretStatus()
    }
    SettingsSecurityCard(
        canManageKeys = canManageKeys,
        signingEnabled = state.artifactSigningVerificationEnabled,
        signingConfigured = state.artifactSigningConfigured,
        signingBusy = state.artifactSigningOperationInFlight,
        secretConfigured = state.customSourceSecretConfigured,
        secretBusy = state.customSourceSecretOperationInFlight,
        onSigningChange = { enabled ->
            when {
                enabled -> vm.enableArtifactSigningVerification()
                else -> showDisableConfirm1 = true
            }
        },
        onImportKeys = {
            importPublicKeyText = ""
            importPrivateKeyText = ""
            importError = null
            showImportDialog = true
        },
        onResetKeys = { showResetConfirm = true },
        onEditSecret = {
            customSourcePat = ""
            showCustomSourceSecretDialog = true
        },
        onDeleteSecret = { showDeleteCustomSourceSecretConfirm = true },
    )

    if (showImportDialog) {
        ImportArtifactSigningKeysDialog(
            publicKeyText = importPublicKeyText,
            privateKeyText = importPrivateKeyText,
            error = importError,
            importing = state.artifactSigningOperationInFlight,
            onPublicKeyTextChange = {
                importPublicKeyText = it
                importError = null
            },
            onPrivateKeyTextChange = {
                importPrivateKeyText = it
                importError = null
            },
            onPickPublicKey = {
                importPickerTarget = SecurityKeyImportTarget.PUBLIC
                importKeyPicker.launch(arrayOf("text/*", "*/*"))
            },
            onPickPrivateKey = {
                importPickerTarget = SecurityKeyImportTarget.PRIVATE
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
        TimedConfirmationDialog(
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
        TimedConfirmationDialog(
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
        TimedConfirmationDialog(
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

    if (showCustomSourceSecretDialog) {
        SettingsSourceSecretDialog(
            value = customSourcePat,
            busy = state.customSourceSecretOperationInFlight,
            onValueChange = { customSourcePat = it },
            onSave = {
                vm.updateCustomSourceSecret(customSourcePat)
                customSourcePat = ""
                showCustomSourceSecretDialog = false
            },
            onDismiss = {
                if (!state.customSourceSecretOperationInFlight) showCustomSourceSecretDialog = false
            },
        )
    }

    if (showDeleteCustomSourceSecretConfirm) {
        SettingsDeleteSourceSecretDialog(
            busy = state.customSourceSecretOperationInFlight,
            onConfirm = {
                vm.deleteCustomSourceSecret()
                showDeleteCustomSourceSecretConfirm = false
            },
            onDismiss = { showDeleteCustomSourceSecretConfirm = false },
        )
    }
}

private enum class SecurityKeyImportTarget {
    PUBLIC,
    PRIVATE,
}

@Composable
private fun KernelCapabilitiesSettingsScreen(
    topBarHeight: Dp,
    state: MainUiState,
    showRefreshLoading: Boolean,
    onRefresh: () -> Unit,
    onTcpAlgorithmSelected: (String) -> Unit
) {
    SettingsKernelCapabilitiesContent(
        topBarHeight = topBarHeight,
        loading = state.kernelCapabilitiesLoading,
        showRefreshLoading = showRefreshLoading,
        tcp = state.kernelTcpCongestionControl,
        error = state.kernelCapabilitiesError,
        actionId = state.kernelCapabilityActionId,
        onRefresh = onRefresh,
        onTcpAlgorithmSelected = onTcpAlgorithmSelected,
    )
}

private suspend fun readTextFromUri(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
    context.contentResolver.openInputStream(uri)?.use { input ->
        input.bufferedReader(Charsets.UTF_8).readText()
    } ?: error(context.getString(R.string.settings_security_import_read_failed))
}

@Composable
private fun ManagerToolsSettingsScreen(
    topBarHeight: Dp,
    state: MainUiState,
    showRefreshLoading: Boolean,
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
    SettingsManagerToolsContent(
        topBarHeight = topBarHeight,
        loading = state.managerToolsLoading,
        showRefreshLoading = showRefreshLoading,
        selinuxMode = selinuxModeLabel(state.selinuxModeText),
        selinuxEnforcing = state.selinuxEnforcing,
        umountPathCount = state.umountPaths.size,
        error = state.managerToolsError,
        actionId = state.managerToolActionId,
        onSelinuxChange = onSelinuxChange,
        onBackupAllowlist = { backupLauncher.launch("abk-root-allowlist.json") },
        onRestoreAllowlist = { restoreLauncher.launch(arrayOf("application/json", "text/*", "*/*")) },
    )
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
internal fun AppProfileTemplateSettingsScreen(
    topBarHeight: Dp,
    state: MainUiState,
    showRefreshLoading: Boolean,
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

    SettingsProfileTemplatesContent(
        topBarHeight = topBarHeight,
        templates = state.appProfileTemplates,
        loading = state.appProfileTemplatesLoading,
        showRefreshLoading = showRefreshLoading,
        selectedId = state.selectedAppProfileTemplateId,
        error = state.appProfileTemplatesError,
        saving = state.appProfileTemplateSaving,
        hasEditor = creating || !state.selectedAppProfileTemplateId.isNullOrBlank(),
        editingId = editingId,
        editingContent = editingContent,
        onSelect = onSelect,
        onNew = {
            creating = true
            editingId = ""
            editingContent = defaultAppProfileTemplateJson()
            onSelect(null)
        },
        onIdChange = { editingId = it },
        onContentChange = { editingContent = it },
        onRefresh = onRefresh,
        onSave = { onSave(editingId, editingContent) },
        onDelete = { onDelete(state.selectedAppProfileTemplateId.orEmpty()) },
    )
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
internal fun ThemeSettingsScreen(
    topBarHeight: Dp,
    themeMode: String,
    dynamicColorEnabled: Boolean,
    customThemeColorArgb: Int?,
    customAccentColorArgb: Int?,
    customFont: com.abk.kernel.data.model.CustomFontSelection?,
    backgroundUri: String?,
    backgroundImageEnabled: Boolean,
    uiSurfaceAlpha: Float,
    blurEnabled: Boolean,
    blurBackgroundExpEnabled: Boolean,
    predictiveBackEnabled: Boolean,
    onThemeModeChange: (String) -> Unit,
    onDynamicColorEnabledChange: (Boolean, Int?, Int?) -> Unit,
    onCustomThemeColorsChange: (Int, Int) -> Unit,
    onImportFont: (Uri, String?) -> Unit,
    onUseSystemFont: () -> Unit,
    onBackgroundImageChange: (String?) -> Unit,
    onBackgroundImageEnabledChange: (Boolean) -> Unit,
    onUiSurfaceAlphaChange: (Float) -> Unit,
    onUiSurfaceAlphaPreviewChange: (Float) -> Unit,
    onBlurEnabledChange: (Boolean) -> Unit,
    onBlurBackgroundExpEnabledChange: (Boolean) -> Unit,
    onPredictiveBackEnabledChange: (Boolean) -> Unit,
    onUiSurfaceAlphaSync: () -> Unit
) {
    // An interrupted slider drag (back gesture / backgrounding / the slider being
    // disabled mid-drag) never fires onValueChangeFinished, so the in-memory preview
    // would stay stuck on an un-persisted alpha for the whole session. Restore it from
    // prefs when the settings page goes away.
    DisposableEffect(Unit) {
        onDispose(onUiSurfaceAlphaSync)
    }
    val context = LocalContext.current
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
    val fontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    }
            }.getOrNull()
            onImportFont(uri, name)
        }
    }
    SettingsAppearanceContent(
        topBarHeight = topBarHeight,
        themeMode = themeMode,
        dynamicColorAvailable = isDynamicColorAvailable(),
        dynamicColorEnabled = dynamicColorEnabled,
        customThemeColorArgb = customThemeColorArgb,
        customAccentColorArgb = customAccentColorArgb,
        customFont = customFont,
        backgroundUri = backgroundUri,
        backgroundImageEnabled = backgroundImageEnabled,
        uiSurfaceAlpha = uiSurfaceAlpha,
        blurEnabled = blurEnabled,
        blurBackgroundExpEnabled = blurBackgroundExpEnabled,
        predictiveBackEnabled = predictiveBackEnabled,
        onThemeModeChange = onThemeModeChange,
        onDynamicColorEnabledChange = onDynamicColorEnabledChange,
        onCustomThemeColorsChange = onCustomThemeColorsChange,
        onImportFont = {
            fontPicker.launch(
                arrayOf(
                    "font/ttf",
                    "font/otf",
                    "application/x-font-ttf",
                    "application/x-font-otf",
                    "application/octet-stream",
                    "*/*",
                )
            )
        },
        onUseSystemFont = onUseSystemFont,
        onChooseBackground = { backgroundPicker.launch(arrayOf("image/*")) },
        onBackgroundImageChange = onBackgroundImageChange,
        onBackgroundImageEnabledChange = onBackgroundImageEnabledChange,
        onUiSurfaceAlphaChange = onUiSurfaceAlphaChange,
        onUiSurfaceAlphaPreviewChange = onUiSurfaceAlphaPreviewChange,
        onBlurEnabledChange = onBlurEnabledChange,
        onBlurBackgroundExpEnabledChange = onBlurBackgroundExpEnabledChange,
        onPredictiveBackEnabledChange = onPredictiveBackEnabledChange
    )
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
internal fun AboutRepositoryScreen(
    topBarHeight: Dp,
    onOpenUrl: (String) -> Unit,
    onOpenSourceLicenses: () -> Unit
) {
    SettingsAboutContent(
        topBarHeight = topBarHeight,
        appName = stringResource(R.string.app_full_name),
        intro = stringResource(R.string.settings_about_intro),
        versionLabel = stringResource(R.string.settings_about_version, BuildConfig.VERSION_NAME),
        repositoryLinks = repositoryLinks(),
        onOpenLicenses = onOpenSourceLicenses,
        contributors = contributors(),
        onOpenUrl = onOpenUrl
    )
}

@Composable
internal fun OpenSourceLicensesScreen(
    topBarHeight: Dp,
    onOpenUrl: (String) -> Unit
) {
    // Pure view state: an opened group starts collapsed to keep this long list scannable.
    var expandedGroups by rememberSaveable { mutableStateOf(emptySet<Int>()) }
    SettingsLicensesContent(
        topBarHeight = topBarHeight,
        intro = stringResource(R.string.settings_open_source_licenses_intro),
        groups = openSourceNoticeGroups(),
        expanded = expandedGroups,
        onExpandedChange = { index, open ->
            expandedGroups = if (open) expandedGroups + index else expandedGroups - index
        },
        onOpenUrl = onOpenUrl
    )
}

@Composable
private fun repositoryLinks(): List<SettingsAboutLink> = listOf(
    SettingsAboutLink(stringResource(R.string.settings_source_repository), sourceRepoUrl()),
    SettingsAboutLink("Releases", "${sourceRepoUrl()}/releases"),
    SettingsAboutLink("Actions", "${sourceRepoUrl()}/actions"),
    SettingsAboutLink("Pages", "https://${BuildConfig.SOURCE_REPO_OWNER}.github.io/${BuildConfig.SOURCE_REPO_NAME}/"),
    SettingsAboutLink("README", "${sourceRepoUrl()}/blob/main/README.md"),
    SettingsAboutLink(stringResource(R.string.settings_third_party_notices), "${sourceRepoUrl()}/blob/main/THIRD_PARTY_NOTICES.md")
)

private fun contributors(): List<SettingsContributor> = listOf(
    SettingsContributor("Akuma-Noko"),
    SettingsContributor("DebugBoard"),
    SettingsContributor("DreamFerry"),
    SettingsContributor("elysias123"),
    SettingsContributor("fanziyun"),
    SettingsContributor("Fede2782"),
    SettingsContributor("FixeQyt"),
    SettingsContributor("FunLay123"),
    SettingsContributor("gsf114"),
    SettingsContributor("guruji-byte"),
    SettingsContributor("huime180"),
    SettingsContributor("liqideqq"),
    SettingsContributor("LX200944"),
    SettingsContributor("Mazha0309"),
    SettingsContributor("MiRinChan"),
    SettingsContributor("prpjzz"),
    SettingsContributor("ReeViiS69"),
    SettingsContributor("ShirkNeko"),
    SettingsContributor("Starsun"),
    SettingsContributor("TheSillyOk"),
    SettingsContributor("TheWildJames"),
    SettingsContributor("Tools-cx-app"),
    SettingsContributor("ukriu"),
    SettingsContributor("wrnxr233"),
    SettingsContributor("Xiaomichael"),
    SettingsContributor("xingguangcuican6666"),
    SettingsContributor("yx1234587"),
    SettingsContributor("zzh20188")
)

private fun openSourceNoticeGroups(): List<SettingsLicenseGroup> = listOf(
    SettingsLicenseGroup(
        R.string.settings_license_group_repository,
        listOf(
            SettingsLicenseNotice("AnyBase Kernel", "GPL-3.0", "LICENSE", sourceRepoUrl()),
            SettingsLicenseNotice("ABK Control native bridge", "GPL-2.0", "app/src/main/cpp/uapi/abk_control.h"),
            SettingsLicenseNotice("xingguang DDK module", "GPL", "ddk/xingguang-ddk/xingguang_ddk.c"),
            SettingsLicenseNotice("DDK kernel API patch", "GPL-2.0", "ddk/patches/xingguang-ddk/0001-xingguang-ddk-api.patch"),
            SettingsLicenseNotice("ZRAM LZ4 kernel glue", "GPL-2.0-only", "zram/lz4/Makefile")
        )
    ),
    SettingsLicenseGroup(
        R.string.settings_license_group_upstream,
        listOf(
            SettingsLicenseNotice("zzh20188/GKI_KernelSU_SUSFS", "Upstream repository license", "BuildConfig.UPSTREAM_REPO_URL", BuildConfig.UPSTREAM_REPO_URL),
            SettingsLicenseNotice("WildKernels/GKI_KernelSU_SUSFS", "Upstream repository license", "BuildConfig.TOP_LEVEL_REPO_URL", BuildConfig.TOP_LEVEL_REPO_URL),
            SettingsLicenseNotice("CodeLinaro CLO LA", "Top-level upstream project licenses", "OnePlus manifest upstream", "https://git.codelinaro.org/clo/la"),
            SettingsLicenseNotice("OnePlusOSS/kernel_manifest", "Upstream repository license / no SPDX detected", "OnePlus manifest parent", "https://github.com/OnePlusOSS/kernel_manifest"),
            SettingsLicenseNotice("Xiaomichael/kernel_manifest", "Upstream repository license / no SPDX detected", "OnePlus manifest branch source", "https://github.com/Xiaomichael/kernel_manifest"),
            SettingsLicenseNotice("Xiaomichael/kernel_patches", "Upstream repository license / no SPDX detected", "OnePlus patch source", "https://github.com/Xiaomichael/kernel_patches"),
            SettingsLicenseNotice("KernelSU", "GPL-3.0", "workflow setup.sh source", "https://github.com/tiann/KernelSU"),
            SettingsLicenseNotice("KernelSU Next", "GPL-3.0", "workflow setup.sh source", "https://github.com/KernelSU-Next/KernelSU-Next"),
            SettingsLicenseNotice("SukiSU Ultra", "GPL-3.0", "kernel setup, ksud, android_bootimg", "https://github.com/SukiSU-Ultra/SukiSU-Ultra"),
            SettingsLicenseNotice("BakaSU", "GPL-3.0", "workflow setup.sh source", "https://github.com/Baka-SU/BakaSU"),
            SettingsLicenseNotice("SUSFS", "GPL-2.0", "kernel patches and module integration", "https://gitlab.com/simonpunk/susfs4ksu"),
            SettingsLicenseNotice("ShirkNeko/susfs4ksu", "GPL-2.0", "GitHub mirror / patch source", "https://github.com/ShirkNeko/susfs4ksu"),
            SettingsLicenseNotice("SukiSU_patch", "GPL-2.0", "workflow patch source", "https://github.com/ShirkNeko/SukiSU_patch"),
            SettingsLicenseNotice("AnyKernel3", "GPL-2.0", "flashable kernel packaging", "https://github.com/WildKernels/AnyKernel3"),
            SettingsLicenseNotice("Xiaomichael/AnyKernel3", "Upstream repository license / NOASSERTION", "OnePlus flashable packaging source", "https://github.com/Xiaomichael/AnyKernel3"),
            SettingsLicenseNotice("WildKernels/kernel_patches", "GPL-2.0", "NTsync, IPSet, BBR and related patches", "https://github.com/WildKernels/kernel_patches"),
            SettingsLicenseNotice("cctv18/susfs4oki", "GPL-3.0", "OnePlus/OPPO/Realme SUSFS patch source", "https://github.com/cctv18/susfs4oki"),
            SettingsLicenseNotice("SukiSU_KernelPatch_patch", "Upstream repository license", "KPM patch source", "https://github.com/SukiSU-Ultra/SukiSU_KernelPatch_patch"),
            SettingsLicenseNotice("Action-Build", "Upstream repository license", "workflow integration", "https://github.com/Numbersf/Action-Build"),
            SettingsLicenseNotice("sidex15/susfs4ksu-module", "Upstream repository license", "SUSFS module build source", "https://github.com/sidex15/susfs4ksu-module"),
            SettingsLicenseNotice("LineageOS GCC prebuilts", "GPL-family toolchain notices", "workflow toolchain source", "https://github.com/LineageOS/android_prebuilts_gcc_linux-x86_aarch64_aarch64-linux-gnu-6.4.1"),
            SettingsLicenseNotice("Baseband Guard", "Upstream repository license", "workflow setup source", "https://github.com/vc-teahouse/Baseband-guard"),
            SettingsLicenseNotice("Re-Kernel", "Upstream repository license", "workflow patch source", "https://github.com/Sakion-Team/Re-Kernel"),
            SettingsLicenseNotice("Droidspaces-OSS", "Upstream repository license", "virtualization support patches", "https://github.com/ravindu644/Droidspaces-OSS"),
            SettingsLicenseNotice("ABK_repo module catalog", "Upstream repository license", "official module catalog", "https://github.com/xingguangcuican6666/ABK_repo")
        )
    ),
    SettingsLicenseGroup(
        R.string.settings_license_group_embedded,
        listOf(
            SettingsLicenseNotice("LZ4", "BSD-2-Clause", "zram/lz4 and zram/include/linux/lz4.h", "https://github.com/lz4/lz4"),
            SettingsLicenseNotice("AOSP kernel/common", "GPL-2.0 WITH Linux-syscall-note and AOSP notices", "android.googlesource.com/kernel/common", "https://android.googlesource.com/kernel/common"),
            SettingsLicenseNotice("AOSP kernel manifest", "AOSP project notices", "android.googlesource.com/kernel/manifest", "https://android.googlesource.com/kernel/manifest"),
            SettingsLicenseNotice("AOSP mkbootimg", "Apache-2.0", "android.googlesource.com/platform/system/tools/mkbootimg", "https://android.googlesource.com/platform/system/tools/mkbootimg"),
            SettingsLicenseNotice("AOSP kernel build-tools", "AOSP project notices", "android.googlesource.com/kernel/prebuilts/build-tools", "https://android.googlesource.com/kernel/prebuilts/build-tools"),
            SettingsLicenseNotice("Android GKI certified boot images", "Android image distribution terms", "dl.google.com/android/gki"),
            SettingsLicenseNotice("Android command line tools", "Android SDK License", "Dockerfile.test", "https://developer.android.com/studio")
        )
    ),
    SettingsLicenseGroup(
        R.string.settings_license_group_android,
        androidDependencyNotices()
    ),
    SettingsLicenseGroup(
        R.string.settings_license_group_web,
        webDependencyNotices()
    )
)

private fun androidDependencyNotices(): List<SettingsLicenseNotice> = listOf(
    SettingsLicenseNotice("Android Gradle Plugin 9.1.1", "Apache-2.0", "com.android.application"),
    SettingsLicenseNotice("Kotlin Gradle/Compose plugin 2.3.21", "Apache-2.0", "org.jetbrains.kotlin.plugin.compose"),
    SettingsLicenseNotice("androidx.core:core-ktx 1.15.0", "Apache-2.0", "Gradle direct dependency"),
    SettingsLicenseNotice("androidx.lifecycle:lifecycle-runtime-ktx 2.8.7", "Apache-2.0", "Gradle direct dependency"),
    SettingsLicenseNotice("androidx.lifecycle:lifecycle-viewmodel-compose 2.8.7", "Apache-2.0", "Gradle direct dependency"),
    SettingsLicenseNotice("androidx.activity:activity-compose 1.9.3", "Apache-2.0", "Gradle direct dependency"),
    SettingsLicenseNotice("androidx.compose:compose-bom 2026.05.00", "Apache-2.0", "Gradle direct dependency"),
    SettingsLicenseNotice("androidx.compose.ui:ui", "Apache-2.0", "Gradle direct dependency"),
    SettingsLicenseNotice("androidx.compose.ui:ui-graphics", "Apache-2.0", "Gradle direct dependency"),
    SettingsLicenseNotice("androidx.compose.ui:ui-tooling-preview", "Apache-2.0", "Gradle direct dependency"),
    SettingsLicenseNotice("androidx.compose.material3:material3 1.5.0-alpha19", "Apache-2.0", "Gradle direct dependency"),
    SettingsLicenseNotice("androidx.compose.material:material-icons-extended", "Apache-2.0", "Gradle direct dependency"),
    SettingsLicenseNotice("com.google.android.material:material 1.12.0", "Apache-2.0", "Gradle direct dependency"),
    SettingsLicenseNotice("androidx.navigation:navigation-compose 2.8.5", "Apache-2.0", "Gradle direct dependency"),
    SettingsLicenseNotice("Retrofit 2.11.0", "Apache-2.0", "com.squareup.retrofit2:retrofit"),
    SettingsLicenseNotice("Retrofit Gson converter 2.11.0", "Apache-2.0", "com.squareup.retrofit2:converter-gson"),
    SettingsLicenseNotice("OkHttp 4.12.0", "Apache-2.0", "com.squareup.okhttp3:okhttp"),
    SettingsLicenseNotice("OkHttp logging-interceptor 4.12.0", "Apache-2.0", "com.squareup.okhttp3:logging-interceptor"),
    SettingsLicenseNotice("Gson 2.11.0", "Apache-2.0", "com.google.code.gson:gson"),
    SettingsLicenseNotice("kotlinx-serialization-json 1.7.3", "Apache-2.0", "org.jetbrains.kotlinx:kotlinx-serialization-json"),
    SettingsLicenseNotice("libsu core 5.2.2", "Apache-2.0", "com.github.topjohnwu.libsu:core"),
    SettingsLicenseNotice("libsu io 5.2.2", "Apache-2.0", "com.github.topjohnwu.libsu:io"),
    SettingsLicenseNotice("Coil Compose 2.7.0", "Apache-2.0", "io.coil-kt:coil-compose"),
    SettingsLicenseNotice("WorkManager runtime-ktx 2.10.0", "Apache-2.0", "androidx.work:work-runtime-ktx"),
    SettingsLicenseNotice("DataStore preferences 1.1.2", "Apache-2.0", "androidx.datastore:datastore-preferences"),
    SettingsLicenseNotice("JUnit 4.13.2", "EPL-1.0", "testImplementation"),
    SettingsLicenseNotice("AndroidX Test JUnit 1.2.1", "Apache-2.0", "androidTestImplementation"),
    SettingsLicenseNotice("Espresso Core 3.6.1", "Apache-2.0", "androidTestImplementation")
)

private fun webDependencyNotices(): List<SettingsLicenseNotice> = listOf(
    SettingsLicenseNotice("@discoveryjs/json-ext 0.6.3", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("@jridgewell/gen-mapping 0.3.13", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("@jridgewell/resolve-uri 3.1.2", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("@jridgewell/source-map 0.3.11", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("@jridgewell/sourcemap-codec 1.5.5", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("@jridgewell/trace-mapping 0.3.31", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("@parcel/watcher 2.5.6 and platform packages", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("@types/eslint 9.6.1", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("@types/eslint-scope 3.7.7", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("@types/estree 1.0.8", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("@types/json-schema 7.0.15", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("@types/node 25.3.3", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("@webassemblyjs/* 1.13.2-1.14.1", "MIT / Apache-2.0", "web/package-lock.json"),
    SettingsLicenseNotice("@webpack-cli/* 3.0.1", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("@xtuc/ieee754 1.2.0", "BSD-3-Clause", "web/package-lock.json"),
    SettingsLicenseNotice("@xtuc/long 4.2.2", "Apache-2.0", "web/package-lock.json"),
    SettingsLicenseNotice("acorn 8.16.0", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("acorn-import-phases 1.0.4", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("ajv 8.18.0", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("ajv-formats 2.1.1", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("ajv-keywords 5.1.0", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("baseline-browser-mapping 2.10.0", "Apache-2.0", "web/package-lock.json"),
    SettingsLicenseNotice("browserslist 4.28.1", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("buffer-from 1.1.2", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("caniuse-lite 1.0.30001776", "CC-BY-4.0", "web/package-lock.json"),
    SettingsLicenseNotice("chokidar 4.0.3", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("chrome-trace-event 1.0.4", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("clone-deep 4.0.1", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("colorette 2.0.20", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("commander 2.20.3 / 12.1.0", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("cross-spawn 7.0.6", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("css-loader 7.1.4", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("cssesc 3.0.0", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("detect-libc 2.1.2", "Apache-2.0", "web/package-lock.json"),
    SettingsLicenseNotice("electron-to-chromium 1.5.307", "ISC", "web/package-lock.json"),
    SettingsLicenseNotice("enhanced-resolve 5.20.0", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("envinfo 7.21.0", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("es-module-lexer 2.0.0", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("escalade 3.2.0", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("eslint-scope 5.1.1", "BSD-2-Clause", "web/package-lock.json"),
    SettingsLicenseNotice("esrecurse 4.3.0", "BSD-2-Clause", "web/package-lock.json"),
    SettingsLicenseNotice("estraverse 4.3.0 / 5.3.0", "BSD-2-Clause", "web/package-lock.json"),
    SettingsLicenseNotice("events 3.3.0", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("fast-deep-equal 3.1.3", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("fast-uri 3.1.0", "BSD-3-Clause", "web/package-lock.json"),
    SettingsLicenseNotice("fastest-levenshtein 1.0.16", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("find-up 4.1.0", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("flat 5.0.2", "BSD-3-Clause", "web/package-lock.json"),
    SettingsLicenseNotice("function-bind 1.1.2", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("glob-to-regexp 0.4.1", "BSD-2-Clause", "web/package-lock.json"),
    SettingsLicenseNotice("graceful-fs 4.2.11", "ISC", "web/package-lock.json"),
    SettingsLicenseNotice("has-flag 4.0.0", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("hasown 2.0.2", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("icss-utils 5.1.0", "ISC", "web/package-lock.json"),
    SettingsLicenseNotice("immutable 5.1.5", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("import-local 3.2.0", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("interpret 3.1.1", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("is-core-module 2.16.1", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("is-extglob 2.1.1", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("is-glob 4.0.3", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("is-plain-object 2.0.4", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("isexe 2.0.0", "ISC", "web/package-lock.json"),
    SettingsLicenseNotice("isobject 3.0.1", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("jest-worker 27.5.1", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("json-parse-even-better-errors 2.3.1", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("json-schema-traverse 1.0.0", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("kind-of 6.0.3", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("loader-runner 4.3.1", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("locate-path 5.0.0", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("merge-stream 2.0.0", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("mime-db 1.52.0", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("mime-types 2.1.35", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("mini-css-extract-plugin 2.10.0", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("nanoid 3.3.11", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("neo-async 2.6.2", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("node-addon-api 7.1.1", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("node-releases 2.0.36", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("p-limit / p-locate / p-try", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("path-exists / path-key / path-parse", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("picocolors 1.1.1", "ISC", "web/package-lock.json"),
    SettingsLicenseNotice("picomatch 4.0.3", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("pkg-dir 4.2.0", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("postcss 8.5.8", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("postcss-modules-*", "ISC / MIT", "web/package-lock.json"),
    SettingsLicenseNotice("postcss-selector-parser 7.1.1", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("postcss-value-parser 4.2.0", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("readdirp 4.1.2", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("rechoir 0.8.0", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("require-from-string 2.0.2", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("resolve 1.22.11", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("resolve-cwd 3.0.0", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("resolve-from 5.0.0", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("sass 1.97.3", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("sass-loader 16.0.7", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("schema-utils 4.3.3", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("semver 7.7.4", "ISC", "web/package-lock.json"),
    SettingsLicenseNotice("shallow-clone 3.0.1", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("shebang-command / shebang-regex", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("source-map 0.6.1", "BSD-3-Clause", "web/package-lock.json"),
    SettingsLicenseNotice("source-map-js 1.2.1", "BSD-3-Clause", "web/package-lock.json"),
    SettingsLicenseNotice("source-map-support 0.5.21", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("supports-color 8.1.1", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("supports-preserve-symlinks-flag 1.0.0", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("tapable 2.3.0", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("terser 5.46.0", "BSD-2-Clause", "web/package-lock.json"),
    SettingsLicenseNotice("terser-webpack-plugin 5.3.17", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("undici-types 7.18.2", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("update-browserslist-db 1.2.3", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("util-deprecate 1.0.2", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("watchpack 2.5.1", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("webpack 5.105.4", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("webpack-cli 6.0.1", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("webpack-merge 6.0.1", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("webpack-sources 3.3.4", "MIT", "web/package-lock.json"),
    SettingsLicenseNotice("which 2.0.2", "ISC", "web/package-lock.json"),
    SettingsLicenseNotice("wildcard 2.0.1", "MIT", "web/package-lock.json")
)

private fun sourceRepoUrl(): String =
    "https://github.com/${BuildConfig.SOURCE_REPO_OWNER}/${BuildConfig.SOURCE_REPO_NAME}"

private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

private fun launchAppUpdateInstaller(context: android.content.Context, apkPath: String) {
    val apkFile = File(apkPath)
    if (!apkFile.isFile) {
        Toast.makeText(context, context.getString(R.string.ru_apk_not_found, apkPath), Toast.LENGTH_SHORT).show()
        return
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
        !context.packageManager.canRequestPackageInstalls()
    ) {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
            .onFailure {
                Toast.makeText(context, context.getString(R.string.settings_app_update_install_permission), Toast.LENGTH_LONG).show()
            }
        return
    }
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        apkFile
    )
    val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
        data = uri
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
        putExtra(Intent.EXTRA_RETURN_RESULT, false)
    }
    runCatching { context.startActivity(intent) }
        .onFailure {
            Toast.makeText(context, context.getString(R.string.settings_app_update_install_failed), Toast.LENGTH_LONG).show()
        }
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
            stringResource(R.string.settings_security) -> stringResource(R.string.settings_group_security_desc)
            stringResource(R.string.settings_app_update) -> stringResource(R.string.settings_group_app_update_desc)
            stringResource(R.string.settings_notification) -> stringResource(R.string.settings_group_notification_desc)
            stringResource(R.string.settings_navigation) -> stringResource(R.string.settings_group_navigation_desc)
            stringResource(R.string.settings_language) -> stringResource(R.string.settings_language_desc)
            stringResource(R.string.settings_theme) -> stringResource(R.string.settings_group_theme_desc)
            "BakaSU" -> stringResource(R.string.settings_group_backend_desc, "BakaSU")
            "SukiSU" -> stringResource(R.string.settings_group_backend_desc, "SukiSU")
            "KernelSU" -> stringResource(R.string.settings_group_backend_desc, "KernelSU")
            stringResource(R.string.settings_manager_settings) -> stringResource(R.string.settings_group_manager_settings_desc)
            stringResource(R.string.settings_kernel_capabilities) -> stringResource(R.string.settings_group_kernel_capabilities_desc)
            stringResource(R.string.settings_tcp_congestion_control) -> stringResource(R.string.settings_group_tcp_congestion_control_desc)
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
            stringResource(R.string.settings_security) -> Icons.Default.VerifiedUser
            stringResource(R.string.settings_app_update) -> Icons.Default.Download
            stringResource(R.string.settings_notification) -> Icons.Default.Notifications
            stringResource(R.string.settings_navigation) -> Icons.Default.SwipeLeft
            stringResource(R.string.settings_language) -> Icons.Default.Language
            stringResource(R.string.settings_theme) -> Icons.Default.Palette
            "BakaSU", "SukiSU", "KernelSU" -> Icons.Default.AdminPanelSettings
            stringResource(R.string.settings_manager_settings) -> Icons.Default.AdminPanelSettings
            stringResource(R.string.settings_kernel_capabilities) -> Icons.Default.Tune
            stringResource(R.string.settings_tcp_congestion_control) -> Icons.Default.Tune
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
private fun AppUpdateStabilityPicker(selected: String, onSelect: (String) -> Unit) {
    SettingsPreferenceChoices(
        title = stringResource(R.string.settings_app_update_stability),
        options = listOf(
            AbkOption(APP_UPDATE_STABILITY_STABLE, stringResource(R.string.settings_app_update_stable)),
            AbkOption(APP_UPDATE_STABILITY_UNSTABLE, stringResource(R.string.settings_app_update_unstable)),
        ),
        selectedId = normalizeAppUpdateStability(selected),
        onSelect = onSelect,
    )
}

@Composable
private fun AppUpdateLinePicker(selected: String, onSelect: (String) -> Unit) {
    SettingsPreferenceChoices(
        title = stringResource(R.string.settings_app_update_line),
        options = listOf(
            AbkOption(APP_UPDATE_LINE_NORMAL, stringResource(R.string.settings_app_update_line_normal)),
            AbkOption(APP_UPDATE_LINE_DEV, stringResource(R.string.settings_app_update_line_dev)),
        ),
        selectedId = normalizeAppUpdateLine(selected),
        onSelect = onSelect,
    )
}

@Composable
private fun appUpdateCheckSubtitle(state: MainUiState): String = when {
    state.appUpdateDownloading -> stringResource(
        R.string.settings_app_update_downloading_progress,
        state.appUpdateDownloadProgress
    )
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
private fun appUpdateStabilityLabel(value: String): String = when (normalizeAppUpdateStability(value)) {
    APP_UPDATE_STABILITY_UNSTABLE -> stringResource(R.string.settings_app_update_unstable)
    else -> stringResource(R.string.settings_app_update_stable)
}

@Composable
private fun appUpdateLineLabel(value: String): String = when (normalizeAppUpdateLine(value)) {
    APP_UPDATE_LINE_DEV -> stringResource(R.string.settings_app_update_line_dev)
    else -> stringResource(R.string.settings_app_update_line_normal)
}

@Composable
private fun WorkflowForegroundRefreshIntervalPicker(selectedSec: Int, onSelect: (Int) -> Unit) {
    SettingsPreferenceChoices(
        title = stringResource(R.string.settings_workflow_foreground_refresh_interval),
        options = PreferencesRepository.WORKFLOW_FOREGROUND_REFRESH_INTERVALS_SEC.sorted().map { sec ->
            AbkOption(sec.toString(), stringResource(R.string.settings_workflow_foreground_refresh_interval_sec, sec))
        },
        selectedId = selectedSec.toString(),
        onSelect = { onSelect(it.toInt()) },
    )
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

    SettingsTextDraftField(
        value = value,
        onCommit = onValueChange,
        label = stringResource(R.string.settings_download_directory),
        description = stringResource(R.string.settings_download_directory_desc),
        placeholder = defaultDirectory,
    ) { commitDraft ->
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
                    commitDraft(selectedPath)
                }
            }
        }
        SettingsDownloadDirectoryActions(
            needsAllFilesAccess = needsAllFilesAccess,
            onChoose = { folderPicker.launch(null) },
            onReset = {
                commitDraft(defaultDirectory)
                Toast.makeText(context, restoredMessage, Toast.LENGTH_SHORT).show()
            },
            onOpenAllFilesAccess = { openAllFilesAccessSettings(context) },
        )
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
private fun MirrorSettingsItem(value: String, onValueChange: (String) -> Unit) {
    SettingsTextDraftField(
        value = value,
        onCommit = onValueChange,
        label = stringResource(R.string.settings_download_mirror),
        description = stringResource(R.string.settings_download_mirror_desc),
        placeholder = "https://hk.gh-proxy.org/",
    )
}
