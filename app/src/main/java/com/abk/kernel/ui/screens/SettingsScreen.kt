@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.abk.kernel.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import coil.compose.AsyncImage
import com.abk.kernel.BuildConfig
import com.abk.kernel.R
import com.abk.kernel.utils.LocaleHelper
import com.abk.kernel.ui.components.AbkScreenHorizontalPadding
import com.abk.kernel.ui.components.ExpressiveHeroCard
import com.abk.kernel.ui.components.ExpressiveListItem
import com.abk.kernel.ui.components.ExpressiveSectionCard
import com.abk.kernel.ui.components.ExpressiveStatusChip
import com.abk.kernel.ui.components.ExpressiveSwitchItem
import com.abk.kernel.ui.components.ExpressiveTopBar
import com.abk.kernel.ui.theme.uiSurfaceColor
import com.abk.kernel.data.model.ManagerSettingItem
import com.abk.kernel.data.model.ManagerSettingKind
import com.abk.kernel.viewmodel.MainUiState
import com.abk.kernel.viewmodel.MainViewModel
import kotlin.math.pow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect

private const val THEME_BACK_VISUAL_EXPONENT = 1.8f
private const val THEME_BACK_SCALE_DELTA = 0.09f
private const val THEME_BACK_SCRIM_ALPHA = 0.32f
private const val THEME_PAGE_EXIT_DELAY_MS = 280L
private val THEME_BACK_MAX_OFFSET = 56.dp
private val THEME_BACK_MAX_CORNER = 32.dp

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
    var showAboutDialog by remember { mutableStateOf(false) }
    var showThemeSettings by rememberSaveable { mutableStateOf(false) }
    var showAppProfileTemplates by rememberSaveable { mutableStateOf(false) }
    var showManagerTools by rememberSaveable { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    var themeBackProgress by remember { mutableFloatStateOf(0f) }
    val showChildPage = showThemeSettings || showAppProfileTemplates || showManagerTools
    val motionScheme = MaterialTheme.motionScheme
    val animatedThemeBackProgress by animateFloatAsState(
        targetValue = themeBackProgress.coerceIn(0f, 1f),
        animationSpec = motionScheme.fastSpatialSpec(),
        label = "settings-theme-back-progress"
    )
    val visualThemeBackProgress = animatedThemeBackProgress
        .coerceIn(0f, 1f)
        .pow(THEME_BACK_VISUAL_EXPONENT)
    val density = LocalDensity.current
    val themeBackOffsetPx = with(density) { THEME_BACK_MAX_OFFSET.toPx() }
    val themeBackCorner = with(density) { (THEME_BACK_MAX_CORNER.toPx() * visualThemeBackProgress).toDp() }

    LaunchedEffect(Unit) {
        vm.refreshManagerSettings(force = true)
    }

    LaunchedEffect(state.hasNativeManagerPermission) {
        if (!state.hasNativeManagerPermission) {
            showAppProfileTemplates = false
            showManagerTools = false
        }
    }

    LaunchedEffect(showChildPage) {
        if (showChildPage) {
            onThemePageVisibleChange(true)
        } else {
            delay(THEME_PAGE_EXIT_DELAY_MS)
            themeBackProgress = 0f
            onThemePageVisibleChange(false)
        }
    }

    DisposableEffect(Unit) {
        onDispose { onThemePageVisibleChange(false) }
    }

    fun openThemeSettings() {
        themeBackProgress = 0f
        onThemePageVisibleChange(true)
        showAppProfileTemplates = false
        showManagerTools = false
        showThemeSettings = true
    }

    fun closeThemeSettings() {
        showThemeSettings = false
    }

    fun openAppProfileTemplates() {
        themeBackProgress = 0f
        onThemePageVisibleChange(true)
        showThemeSettings = false
        showManagerTools = false
        showAppProfileTemplates = true
        vm.refreshAppProfileTemplates()
    }

    fun openManagerTools() {
        themeBackProgress = 0f
        onThemePageVisibleChange(true)
        showThemeSettings = false
        showAppProfileTemplates = false
        showManagerTools = true
        vm.refreshManagerTools(force = true)
    }

    fun closeChildPage() {
        showThemeSettings = false
        showAppProfileTemplates = false
        showManagerTools = false
    }

    PredictiveBackHandler(enabled = showChildPage && state.predictiveBackEnabled) { progress ->
        try {
            progress.collect { backEvent ->
                themeBackProgress = backEvent.progress.coerceIn(0f, 1f)
            }
            closeChildPage()
        } catch (_: CancellationException) {
            themeBackProgress = 0f
        }
    }

    BackHandler(enabled = showChildPage && !state.predictiveBackEnabled) {
        closeChildPage()
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

    if (showAboutDialog) {
        AboutDialog(
            onDismiss = { showAboutDialog = false },
            onOpenUrl = { openUrl(context, it) }
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
                state = state,
                vm = vm,
                scrollBehavior = scrollBehavior,
                onLogout = { showLogoutDialog = true },
                onOpenThemeSettings = ::openThemeSettings,
                onOpenAppProfileTemplates = ::openAppProfileTemplates,
                onOpenManagerTools = ::openManagerTools,
                onOpenInstalledModules = onOpenInstalledModules,
                onAbout = { showAboutDialog = true }
            )
        }

        AnimatedVisibility(
            visible = showChildPage,
            enter = fadeIn(animationSpec = motionScheme.defaultEffectsSpec()),
            exit = fadeOut(animationSpec = motionScheme.fastEffectsSpec()),
            modifier = childPageModifier
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = THEME_BACK_SCRIM_ALPHA * visualThemeBackProgress))
            )
        }

        AnimatedVisibility(
            visible = showThemeSettings,
            enter = fadeIn(animationSpec = motionScheme.defaultEffectsSpec()) +
                slideInHorizontally(animationSpec = motionScheme.defaultSpatialSpec()) { width -> width / 4 },
            exit = fadeOut(animationSpec = motionScheme.fastEffectsSpec()) +
                slideOutHorizontally(animationSpec = motionScheme.fastSpatialSpec()) { width -> width },
            modifier = childPageModifier
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = themeBackOffsetPx * visualThemeBackProgress
                        scaleX = 1f - THEME_BACK_SCALE_DELTA * visualThemeBackProgress
                        scaleY = 1f - THEME_BACK_SCALE_DELTA * visualThemeBackProgress
                        alpha = 1f - 0.06f * visualThemeBackProgress
                        shape = RoundedCornerShape(themeBackCorner)
                        clip = visualThemeBackProgress > 0.01f
                    }
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
                                IconButton(onClick = ::closeThemeSettings) {
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

        AnimatedVisibility(
            visible = showAppProfileTemplates,
            enter = fadeIn(animationSpec = motionScheme.defaultEffectsSpec()) +
                slideInHorizontally(animationSpec = motionScheme.defaultSpatialSpec()) { width -> width / 4 },
            exit = fadeOut(animationSpec = motionScheme.fastEffectsSpec()) +
                slideOutHorizontally(animationSpec = motionScheme.fastSpatialSpec()) { width -> width },
            modifier = childPageModifier
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = themeBackOffsetPx * visualThemeBackProgress
                        scaleX = 1f - THEME_BACK_SCALE_DELTA * visualThemeBackProgress
                        scaleY = 1f - THEME_BACK_SCALE_DELTA * visualThemeBackProgress
                        alpha = 1f - 0.06f * visualThemeBackProgress
                        shape = RoundedCornerShape(themeBackCorner)
                        clip = visualThemeBackProgress > 0.01f
                    }
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
                                IconButton(onClick = ::closeChildPage) {
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

        AnimatedVisibility(
            visible = showManagerTools,
            enter = fadeIn(animationSpec = motionScheme.defaultEffectsSpec()) +
                slideInHorizontally(animationSpec = motionScheme.defaultSpatialSpec()) { width -> width / 4 },
            exit = fadeOut(animationSpec = motionScheme.fastEffectsSpec()) +
                slideOutHorizontally(animationSpec = motionScheme.fastSpatialSpec()) { width -> width },
            modifier = childPageModifier
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = themeBackOffsetPx * visualThemeBackProgress
                        scaleX = 1f - THEME_BACK_SCALE_DELTA * visualThemeBackProgress
                        scaleY = 1f - THEME_BACK_SCALE_DELTA * visualThemeBackProgress
                        alpha = 1f - 0.06f * visualThemeBackProgress
                        shape = RoundedCornerShape(themeBackCorner)
                        clip = visualThemeBackProgress > 0.01f
                    }
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
                                IconButton(onClick = ::closeChildPage) {
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
    state: MainUiState,
    vm: MainViewModel,
    scrollBehavior: TopAppBarScrollBehavior,
    onLogout: () -> Unit,
    onOpenThemeSettings: () -> Unit,
    onOpenAppProfileTemplates: () -> Unit,
    onOpenManagerTools: () -> Unit,
    onOpenInstalledModules: () -> Unit,
    onAbout: () -> Unit
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
                ExpressiveListItem(
                    title = stringResource(R.string.settings_fork_repo),
                    subtitle = state.forkRepo?.fullName ?: stringResource(R.string.settings_waiting_fork),
                    leadingIcon = Icons.Default.ForkRight
                )
            } ?: ExpressiveListItem(
                title = stringResource(R.string.settings_not_logged_in),
                leadingIcon = Icons.Default.AccountCircle
            )
        }

        SettingsGroup(title = stringResource(R.string.settings_build)) {
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
        }

        Spacer(Modifier.height(80.dp))
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
private fun AboutDialog(
    onDismiss: () -> Unit,
    onOpenUrl: (String) -> Unit
) {
    val links = aboutLinks()
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Info, null) },
        title = { Text(stringResource(R.string.settings_about_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    stringResource(R.string.settings_about_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AboutLinkRow(AboutLink(stringResource(R.string.settings_source_repository), sourceRepoUrl()), onOpenUrl)
                AboutSectionTitle(stringResource(R.string.settings_acknowledgements))
                Text(
                    stringResource(R.string.settings_acknowledgements_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                links.forEach {
                    AboutLinkRow(it, onOpenUrl)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}

@Composable
private fun AboutSectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
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

private data class AboutLink(
    val title: String,
    val url: String
)

@Composable
private fun aboutLinks(): List<AboutLink> {
    return listOf(
        AboutLink(stringResource(R.string.settings_upstream_repository), BuildConfig.UPSTREAM_REPO_URL),
        AboutLink(stringResource(R.string.settings_top_level_repository), BuildConfig.TOP_LEVEL_REPO_URL),
        AboutLink(stringResource(R.string.settings_third_party_notices), "${sourceRepoUrl()}/blob/main/THIRD_PARTY_NOTICES.md"),
        AboutLink("KernelSU", "https://github.com/tiann/KernelSU"),
        AboutLink("KernelSU Next", "https://github.com/KernelSU-Next/KernelSU-Next"),
        AboutLink("SukiSU Ultra", "https://github.com/SukiSU-Ultra/SukiSU-Ultra"),
        AboutLink("ReSukiSU", "https://github.com/ReSukiSU/ReSukiSU"),
        AboutLink("SUSFS", "https://gitlab.com/simonpunk/susfs4ksu"),
        AboutLink(stringResource(R.string.settings_susfs_github_source), "https://github.com/ShirkNeko/susfs4ksu"),
        AboutLink("SukiSU patch", "https://github.com/ShirkNeko/SukiSU_patch"),
        AboutLink("AnyKernel3", "https://github.com/WildKernels/AnyKernel3"),
        AboutLink("Kernel patches", "https://github.com/WildKernels/kernel_patches"),
        AboutLink(stringResource(R.string.settings_kernel_patches_source), "https://github.com/WildKernels/kernel_patches"),
        AboutLink("NTsync / IPSet / BBR PR by huime180", "https://github.com/huime180"),
        AboutLink("Action-Build", "https://github.com/Numbersf/Action-Build"),
        AboutLink(stringResource(R.string.settings_susfs_module_source), "https://github.com/sidex15/susfs4ksu-module"),
        AboutLink(
            "GCC prebuilts",
            "https://github.com/LineageOS/android_prebuilts_gcc_linux-x86_aarch64_aarch64-linux-gnu-6.4.1"
        ),
        AboutLink("Baseband Guard", "https://github.com/vc-teahouse/Baseband-guard"),
        AboutLink("Re-Kernel", "https://github.com/Sakion-Team/Re-Kernel"),
        AboutLink(stringResource(R.string.settings_droidspaces_source), "https://github.com/ravindu644/Droidspaces-OSS"),
        AboutLink(stringResource(R.string.settings_kernelsu_site), "https://kernelsu.org/")
    )
}

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
        Triple(LocaleHelper.LANG_EN, stringResource(R.string.settings_language_en), Icons.Default.Language)
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
