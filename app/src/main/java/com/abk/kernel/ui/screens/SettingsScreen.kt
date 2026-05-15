@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.abk.kernel.ui.screens

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
            title = { Text("退出登录") },
            text = { Text("确认退出 GitHub 账户吗？退出后需重新授权。") },
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
                                    Icon(Icons.Default.ArrowBack, contentDescription = "返回")
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
                            title = "App Profile 模板",
                            navigationIcon = {
                                IconButton(onClick = ::closeChildPage) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                                }
                            },
                            actions = {
                                IconButton(onClick = { vm.refreshAppProfileTemplates() }) {
                                    Icon(Icons.Default.Refresh, contentDescription = "刷新")
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
                            title = "工具",
                            navigationIcon = {
                                IconButton(onClick = ::closeChildPage) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                                }
                            },
                            actions = {
                                IconButton(onClick = { vm.refreshManagerTools(force = true) }) {
                                    Icon(Icons.Default.Refresh, contentDescription = "刷新")
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
                                contentDescription = "退出登录",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                )
                ExpressiveListItem(
                    title = "Fork 仓库",
                    subtitle = state.forkRepo?.fullName ?: "未 Fork",
                    leadingIcon = Icons.Default.ForkRight
                )
            } ?: ExpressiveListItem(
                title = "未登录",
                leadingIcon = Icons.Default.AccountCircle
            )
        }

        SettingsGroup(title = stringResource(R.string.settings_build)) {
            SwitchSettingsItem(
                icon = Icons.Default.Download,
                title = stringResource(R.string.settings_auto_download),
                subtitle = "仅对下一次新提交的构建生效，关闭后不会自动下载",
                checked = state.autoDownload,
                onCheckedChange = { vm.setAutoDownload(it) }
            )
            SwitchSettingsItem(
                icon = Icons.Default.CloudDownload,
                title = "预编译 GKI 获取与下载",
                subtitle = "从本仓库 Release 获取预编译 GKI，下载需手动触发",
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
                subtitle = "在通知栏显示构建状态",
                checked = state.notifyBuild,
                onCheckedChange = { vm.setNotifyBuild(it) }
            )
        }

        SettingsGroup(title = "导航") {
            SwitchSettingsItem(
                icon = Icons.Default.ArrowBack,
                title = "应用内 M3E 返回动效",
                subtitle = "使用 Material 3 Expressive 的页面返回动画，不依赖系统预测性返回",
                checked = state.predictiveBackEnabled,
                onCheckedChange = { vm.setPredictiveBackEnabled(it) }
            )
        }

        SettingsGroup(title = stringResource(R.string.settings_theme)) {
            ExpressiveListItem(
                title = "颜色与外观",
                subtitle = "${themeModeLabel(state.themeMode)} · ${dynamicColorLabel(state.dynamicColorEnabled)}",
                leadingIcon = Icons.Default.Palette,
                trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = "进入颜色与外观") },
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
                title = "关于",
                subtitle = "项目入口、源码仓库、上游项目与致谢",
                leadingIcon = Icons.Default.AutoAwesome,
                trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = "进入关于") },
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
    val hasInjectedSettings = state.managerSettingsItems.isNotEmpty()
    if (!hasInjectedSettings && !state.managerSettingsLoading && state.managerSettingsError == null) return

    SettingsGroup(title = state.managerSettingsTitle.ifBlank { "管理器设置" }) {
        when {
            state.managerSettingsLoading && !hasInjectedSettings -> {
                ExpressiveListItem(
                    title = "正在读取后端设置",
                    subtitle = "从当前 KSU 后端加载可用功能",
                    leadingContent = { LoadingIndicator(Modifier.size(24.dp)) }
                )
            }
            state.managerSettingsError != null -> {
                ExpressiveListItem(
                    title = "后端设置读取失败",
                    subtitle = state.managerSettingsError,
                    leadingIcon = Icons.Default.Error,
                    trailingContent = {
                        IconButton(onClick = { vm.refreshManagerSettings(force = true) }) {
                            Icon(Icons.Default.Refresh, contentDescription = "重试")
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
                    trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = "进入") },
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
    val enabled = item.enabled && !actionInFlight
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
                        Text(item.options.getOrElse(item.selectedIndex) { "选择" })
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    item.options.forEachIndexed { index, option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            leadingIcon = {
                                if (index == item.selectedIndex) {
                                    Icon(Icons.Default.Check, contentDescription = null)
                                }
                            },
                            onClick = {
                                expanded = false
                                if (index != item.selectedIndex) onSelected(index)
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
        SettingsGroup(title = "系统工具") {
            SwitchSettingsItem(
                icon = Icons.Default.Security,
                title = "SELinux 模式",
                subtitle = "当前：${selinuxModeLabel(state.selinuxModeText)}",
                checked = state.selinuxEnforcing,
                enabled = !state.managerToolsLoading && !selinuxBusy,
                onCheckedChange = onSelinuxChange
            )
            ExpressiveListItem(
                title = "Umount 路径管理",
                subtitle = if (state.umountPaths.isEmpty()) {
                    "当前无自定义路径"
                } else {
                    "已配置 ${state.umountPaths.size} 条路径"
                },
                leadingIcon = Icons.Default.FolderDelete,
                trailingContent = {
                    if (state.managerToolsLoading) LoadingIndicator(Modifier.size(22.dp))
                }
            )
        }

        SettingsGroup(title = "授权列表") {
            ExpressiveListItem(
                title = "备份允许列表",
                subtitle = "选择位置导出允许列表",
                leadingIcon = Icons.Default.CloudUpload,
                enabled = !backupBusy,
                trailingContent = {
                    if (backupBusy) {
                        LoadingIndicator(Modifier.size(22.dp))
                    } else {
                        Icon(Icons.Default.ChevronRight, contentDescription = "导出")
                    }
                },
                onClick = { backupLauncher.launch("abk-root-allowlist.json") }
            )
            ExpressiveListItem(
                title = "还原允许列表",
                subtitle = "选择备份文件进行导入",
                leadingIcon = Icons.Default.History,
                enabled = !restoreBusy,
                trailingContent = {
                    if (restoreBusy) {
                        LoadingIndicator(Modifier.size(22.dp))
                    } else {
                        Icon(Icons.Default.ChevronRight, contentDescription = "导入")
                    }
                },
                onClick = { restoreLauncher.launch(arrayOf("application/json", "text/*", "*/*")) }
            )
        }

        if (state.managerToolsError != null) {
            SettingsGroup(title = "工具状态") {
                ExpressiveListItem(
                    title = "操作未完成",
                    subtitle = state.managerToolsError,
                    leadingIcon = Icons.Default.Error
                )
            }
        }

        Spacer(Modifier.height(80.dp))
    }
}

private fun selinuxModeLabel(mode: String): String =
    when (mode.trim().lowercase()) {
        "enforcing" -> "强制执行"
        "permissive" -> "宽容"
        "disabled" -> "已禁用"
        else -> mode.ifBlank { "未知" }
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
        SettingsGroup(title = "本地模板") {
            when {
                state.appProfileTemplatesLoading -> ExpressiveListItem(
                    title = "正在读取模板",
                    subtitle = "从 ReSukiSU profile 存储加载",
                    leadingContent = { LoadingIndicator(Modifier.size(24.dp)) }
                )
                state.appProfileTemplates.isEmpty() -> ExpressiveListItem(
                    title = "暂无模板",
                    subtitle = "保存后会出现在这里",
                    leadingIcon = Icons.Default.Description
                )
            }
            state.appProfileTemplates.forEach { template ->
                ExpressiveListItem(
                    title = template.id,
                    subtitle = if (state.selectedAppProfileTemplateId == template.id) "正在编辑" else "App Profile 模板",
                    leadingIcon = Icons.Default.Description,
                    selected = state.selectedAppProfileTemplateId == template.id,
                    trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = "编辑") },
                    onClick = { onSelect(template.id) }
                )
            }
            ExpressiveListItem(
                title = "新建模板",
                subtitle = "创建新的 App Profile 模板 JSON",
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
            SettingsGroup(title = "状态") {
                ExpressiveListItem(
                    title = "操作未完成",
                    subtitle = state.appProfileTemplatesError,
                    leadingIcon = Icons.Default.Error,
                    trailingContent = {
                        IconButton(onClick = onRefresh) {
                            Icon(Icons.Default.Refresh, contentDescription = "刷新")
                        }
                    }
                )
            }
        }

        val hasEditor = creating || !state.selectedAppProfileTemplateId.isNullOrBlank()
        if (hasEditor) {
            SettingsGroup(title = "编辑模板") {
                OutlinedTextField(
                    value = editingId,
                    onValueChange = { editingId = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("模板名称") },
                    singleLine = true,
                    enabled = state.selectedAppProfileTemplateId.isNullOrBlank()
                )
                OutlinedTextField(
                    value = editingContent,
                    onValueChange = { editingContent = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 220.dp),
                    label = { Text("模板 JSON") },
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
                            Text("删除")
                        }
                    }
                    Button(
                        onClick = { onSave(editingId, editingContent) },
                        enabled = !state.appProfileTemplateSaving && editingId.isNotBlank()
                    ) {
                        Text("保存")
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
        SettingsGroup(title = "外观模式") {
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

        SettingsGroup(title = "颜色来源") {
            SwitchSettingsItem(
                icon = Icons.Default.AutoAwesome,
                title = "莫奈取色",
                subtitle = if (dynamicColorAvailable) {
                    "使用系统壁纸生成的 Material You 动态颜色"
                } else {
                    "Android 12 及以上可用，当前使用自定义色板"
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
            SettingsGroup(title = "自定义颜色") {
                ThemeColorPicker(
                    title = "主题色",
                    subtitle = "主操作、选中状态和主要强调区域",
                    selectedColorArgb = selectedThemeColorArgb,
                    presets = themeColorPresets(),
                    onColorSelected = { color ->
                        onCustomThemeColorsChange(color, selectedAccentColorArgb)
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ThemeColorPicker(
                    title = "强调色",
                    subtitle = "辅助状态、标签和次级强调区域",
                    selectedColorArgb = selectedAccentColorArgb,
                    presets = themeColorPresets(),
                    onColorSelected = { color ->
                        onCustomThemeColorsChange(selectedThemeColorArgb, color)
                    }
                )
            }
        }

        SettingsGroup(title = "背景") {
            SwitchSettingsItem(
                icon = Icons.Default.Image,
                title = "自定义背景",
                subtitle = if (backgroundUri.isNullOrBlank()) {
                    "选择图片后可启用全局背景"
                } else {
                    "已选择背景图片"
                },
                checked = backgroundImageEnabled && !backgroundUri.isNullOrBlank(),
                enabled = !backgroundUri.isNullOrBlank(),
                onCheckedChange = onBackgroundImageEnabledChange
            )
            ExpressiveListItem(
                title = if (backgroundUri.isNullOrBlank()) "选择背景图片" else "更换背景图片",
                subtitle = "从本机选择一张图片作为应用背景",
                leadingIcon = Icons.Default.Image,
                onClick = { backgroundPicker.launch(arrayOf("image/*")) }
            )
            if (!backgroundUri.isNullOrBlank()) {
                ExpressiveListItem(
                    title = "移除背景图片",
                    subtitle = "恢复纯色 Material 主题背景",
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
                text = "界面不透明度",
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
            text = "调低后卡片、顶部栏和底部栏会透出背景。",
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
                    preset = ThemeColorPreset("当前", selectedColorArgb),
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

private fun themeColorPresets(): List<ThemeColorPreset> = listOf(
    ThemeColorPreset("绿", 0xFF8BC34A.toInt()),
    ThemeColorPreset("蓝", 0xFF42A5F5.toInt()),
    ThemeColorPreset("紫", 0xFF9575CD.toInt()),
    ThemeColorPreset("粉", 0xFFEC6A9A.toInt()),
    ThemeColorPreset("橙", 0xFFFFA726.toInt()),
    ThemeColorPreset("青", 0xFF26C6DA.toInt())
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
    !isDynamicColorAvailable() -> "莫奈取色不可用"
    enabled -> "莫奈取色"
    else -> "自定义色板"
}

private fun isDynamicColorAvailable(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Composable
private fun AboutDialog(
    onDismiss: () -> Unit,
    onOpenUrl: (String) -> Unit
) {
    val links = remember { aboutLinks() }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Info, null) },
        title = { Text("关于 AnyBase Kernel") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "AnyBase Kernel 用于构建、分发和管理 GKI KernelSU / SUSFS 内核。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AboutLinkRow(AboutLink("源仓库", sourceRepoUrl()), onOpenUrl)
                AboutSectionTitle("致谢")
                Text(
                    "ABK 基于以下项目、仓库和社区工作继续开发。",
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

private fun aboutLinks(): List<AboutLink> {
    return listOf(
        AboutLink("上游仓库", BuildConfig.UPSTREAM_REPO_URL),
        AboutLink("顶层仓库", BuildConfig.TOP_LEVEL_REPO_URL),
        AboutLink("KernelSU", "https://github.com/tiann/KernelSU"),
        AboutLink("KernelSU Next", "https://github.com/KernelSU-Next/KernelSU-Next"),
        AboutLink("SukiSU Ultra", "https://github.com/SukiSU-Ultra/SukiSU-Ultra"),
        AboutLink("ReSukiSU", "https://github.com/ReSukiSU/ReSukiSU"),
        AboutLink("SUSFS", "https://gitlab.com/simonpunk/susfs4ksu"),
        AboutLink("SUSFS GitHub 镜像/补丁来源", "https://github.com/ShirkNeko/susfs4ksu"),
        AboutLink("SukiSU patch", "https://github.com/ShirkNeko/SukiSU_patch"),
        AboutLink("AnyKernel3", "https://github.com/WildKernels/AnyKernel3"),
        AboutLink("Kernel patches", "https://github.com/WildKernels/kernel_patches"),
        AboutLink("NTsync / IPSet / BBR 来源", "https://github.com/WildKernels/kernel_patches"),
        AboutLink("NTsync / IPSet / BBR PR by huime180", "https://github.com/huime180"),
        AboutLink("Action-Build", "https://github.com/Numbersf/Action-Build"),
        AboutLink("SUSFS 模块构建来源", "https://github.com/sidex15/susfs4ksu-module"),
        AboutLink(
            "GCC prebuilts",
            "https://github.com/LineageOS/android_prebuilts_gcc_linux-x86_aarch64_aarch64-linux-gnu-6.4.1"
        ),
        AboutLink("Baseband Guard", "https://github.com/vc-teahouse/Baseband-guard"),
        AboutLink("Re-Kernel", "https://github.com/Sakion-Team/Re-Kernel"),
        AboutLink("Droidspaces / 虚拟化支持补丁来源", "https://github.com/ravindu644/Droidspaces-OSS"),
        AboutLink("KernelSU 官方站点", "https://kernelsu.org/")
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
        title = login?.let { "已连接 GitHub：$it" } ?: "AnyBase Kernel 设置中心",
        subtitle = forkName ?: "管理构建自动化、通知、主题和仓库来源。",
        icon = Icons.Default.Tune,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        badge = {
            ExpressiveStatusChip(
                label = when (themeMode) {
                    "dark" -> "深色主题"
                    "light" -> "浅色主题"
                    else -> "跟随系统"
                },
                icon = Icons.Default.Palette,
                color = MaterialTheme.colorScheme.primary
            )
            ExpressiveStatusChip(
                label = if (forkName != null) "Fork 已连接" else "等待 Fork",
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
            stringResource(R.string.settings_account) -> "GitHub 账户、fork 仓库和退出登录。"
            stringResource(R.string.settings_build) -> "控制构建成功后的自动化行为。"
            stringResource(R.string.settings_notification) -> "同步工作流状态到系统通知。"
            "导航" -> "控制返回手势和页面切换体验。"
            stringResource(R.string.settings_theme) -> "Material 3 Expressive 主题显示模式。"
            "ReSukiSU" -> "按当前 ReSukiSU 后端能力动态加载。"
            "SukiSU" -> "按当前 SukiSU 后端能力动态加载。"
            "KernelSU" -> "按当前 KernelSU 后端能力动态加载。"
            "管理器设置" -> "按当前 KSU 后端能力动态加载。"
            "系统工具" -> "SELinux 和模块卸载路径相关工具。"
            "授权列表" -> "导出或导入当前 Root 授权列表。"
            "工具状态" -> "最近一次工具操作的执行结果。"
            "本地模板" -> "管理保存在 ReSukiSU profile 存储中的模板。"
            "状态" -> "最近一次模板操作的执行结果。"
            "编辑模板" -> "直接编辑 App Profile 模板 JSON。"
            "外观模式" -> "控制应用明暗显示方式。"
            "颜色来源" -> "选择系统动态颜色或自定义色板。"
            "自定义颜色" -> "莫奈关闭时使用的主题色和强调色。"
            "背景" -> "选择背景图片并调整上层界面透明度。"
            else -> "应用版本与源码信息。"
        },
        icon = when (title) {
            stringResource(R.string.settings_account) -> Icons.Default.AccountCircle
            stringResource(R.string.settings_build) -> Icons.Default.Build
            stringResource(R.string.settings_notification) -> Icons.Default.Notifications
            "导航" -> Icons.Default.ArrowBack
            stringResource(R.string.settings_theme) -> Icons.Default.Palette
            "ReSukiSU", "SukiSU", "KernelSU", "管理器设置" -> Icons.Default.AdminPanelSettings
            "系统工具" -> Icons.Default.Build
            "授权列表" -> Icons.Default.VerifiedUser
            "工具状态" -> Icons.Default.Info
            "本地模板" -> Icons.Default.Apps
            "状态" -> Icons.Default.Info
            "编辑模板" -> Icons.Default.Edit
            "外观模式" -> Icons.Default.BrightnessMedium
            "颜色来源" -> Icons.Default.AutoAwesome
            "自定义颜色" -> Icons.Default.Palette
            "背景" -> Icons.Default.Image
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
            title = "下载镜像站",
            subtitle = "留空直连 GitHub；填写后会先镜像到 Release 再下载",
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
