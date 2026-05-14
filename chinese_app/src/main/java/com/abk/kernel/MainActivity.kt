package com.abk.kernel

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.abk.kernel.ui.screens.AuthGateScreen
import com.abk.kernel.ui.screens.BuildScreen
import com.abk.kernel.ui.screens.FlashScreen
import com.abk.kernel.ui.screens.InstalledModulesScreen
import com.abk.kernel.ui.screens.ModuleRepositoryScreen
import com.abk.kernel.ui.screens.RootAuthorizationScreen
import com.abk.kernel.ui.screens.RuntimeHomeScreen
import com.abk.kernel.ui.screens.SettingsScreen
import com.abk.kernel.ui.screens.StatusScreen
import com.abk.kernel.ui.theme.AbkTheme
import com.abk.kernel.ui.theme.LocalUiSurfaceAlpha
import com.abk.kernel.ui.theme.uiSurfaceColor
import com.abk.kernel.viewmodel.AuthStep
import com.abk.kernel.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val requestNotifications = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val vm: MainViewModel = viewModel()
            val state by vm.uiState.collectAsState()

            LaunchedEffect(state.termsAccepted) {
                if (state.termsAccepted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            AbkTheme(
                themeMode = state.themeMode,
                dynamicColorEnabled = state.dynamicColorEnabled,
                customThemeColorArgb = state.customThemeColorArgb,
                customAccentColorArgb = state.customAccentColorArgb
            ) {
                AppBackgroundHost(
                    backgroundUri = state.customBackgroundUri,
                    backgroundEnabled = state.backgroundImageEnabled,
                    uiSurfaceAlpha = state.uiSurfaceAlpha
                ) {
                    when {
                        !state.termsLoaded -> Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.surface
                        ) {}
                        !state.termsAccepted -> TermsAgreementDialog(
                            onAccept = vm::acceptTerms,
                            onDecline = { finishAffinity() }
                        )
                        state.authStep != AuthStep.READY -> AuthGateScreen(vm)
                        else -> AbkMainScaffold(vm)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppBackgroundHost(
    backgroundUri: String?,
    backgroundEnabled: Boolean,
    uiSurfaceAlpha: Float,
    content: @Composable () -> Unit
) {
    val hasBackground = backgroundEnabled && !backgroundUri.isNullOrBlank()
    val colorScheme = MaterialTheme.colorScheme
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
        CompositionLocalProvider(
            LocalUiSurfaceAlpha provides if (hasBackground) uiSurfaceAlpha.coerceIn(0f, 1f) else 1f
        ) {
            content()
        }
    }
}

@Composable
private fun TermsAgreementDialog(
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    val scrollState = rememberScrollState()
    val canAccept by remember {
        derivedStateOf { scrollState.maxValue > 0 && scrollState.value >= scrollState.maxValue }
    }

    AlertDialog(
        onDismissRequest = {},
        title = {
            Text(
                text = "ABK 用户协议与免责声明",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TermsText("版本：1")
                TermsText("生效日期：2026-05-07")
                TermsText("请完整阅读本协议。你点击“同意并继续”即表示已理解并接受全部条款；如果不同意，请点击“不同意并退出”。")

                TermsSection(
                    "一、软件用途",
                    "ABK 用于触发 GitHub Actions 构建、下载、刷写或安装 GKI KernelSU / SUSFS 相关产物，并提供 Root 检查、GitHub 授权、Fork 检查、自定义外部模块注入、构建进度同步和产物管理等功能。",
                    "ABK 面向合法授权设备上的学习、研究、自用和调试场景，不提供任何适配、启动成功、刷写成功、Root 可用、绕过检测或长期稳定性的保证。"
                )
                TermsSection(
                    "二、高风险操作提示",
                    "构建、修改、刷写 boot、init_boot、vendor_boot、内核镜像、AnyKernel3 包或安装底层模块均属于高风险操作，可能导致无法开机、反复重启、数据损坏、分区异常、系统服务不可用、保修或售后受限。",
                    "发生异常时，你可能需要恢复官方镜像、重新刷机、清除数据、解锁或重新锁定 Bootloader，相关成本和后果均由你自行承担。",
                    "在不确定设备型号、分区布局、Android 版本、内核版本、安全补丁级别、KMI 兼容性和回滚方案时，不应继续构建或刷写。"
                )
                TermsSection(
                    "三、合法使用限制",
                    "你只能在本人拥有或已获得明确授权的设备、账号、仓库和网络环境中使用 ABK。",
                    "禁止将 ABK、工作流、补丁、自定义外部模块或构建产物用于灰黑产、未授权访问、绕过风控、作弊、恶意隐藏行为、窃取数据、破坏服务、规避审计、批量滥用、侵犯他人权益或任何违法违规用途。",
                    "你应自行确认所在地法律法规、平台规则、设备厂商条款和上游项目许可证要求。"
                )
                TermsSection(
                    "四、第三方项目与外部模块",
                    "ABK 聚合多个第三方项目、补丁、脚本和下载来源。第三方代码、许可证、稳定性、安全性和适配性由对应上游负责。",
                    "启用自定义外部模块会 clone 外部仓库并执行仓库根目录的 setup.sh。执行前你应审查脚本内容、提交历史、来源可信度和权限影响。",
                    "外部模块可能修改内核源码、defconfig、构建脚本或产物内容。由外部模块造成的构建失败、设备异常、安全风险或合规问题，由启用者和模块提供者自行承担。"
                )
                TermsSection(
                    "五、授权、隐私与账号风险",
                    "ABK 使用 GitHub Device Flow 获取授权 token，以检查或管理 Fork、触发 Actions、读取构建状态和下载产物。你应理解授权范围并自行管理 GitHub 账号安全。",
                    "ABK 在需要刷写、安装模块或识别本机状态时可能请求 Root。授予 Root 权限会提高系统风险，你应自行确认操作必要性。",
                    "你不应在自定义模块、公开仓库、日志或 issue 中暴露 token、密钥、隐私数据、设备敏感信息或不可公开的构建产物。"
                )
                TermsSection(
                    "六、免责声明",
                    "在法律允许范围内，ABK 的开发者、维护者和贡献者不对因使用、修改、分发、依赖 ABK 或其构建产物造成的设备损坏、数据丢失、账号风险、服务中断、合规问题、第三方索赔或任何直接/间接损失承担责任。",
                    "ABK 按现状提供，不承诺无缺陷、无中断、无安全风险，也不承诺任何特定设备、系统版本、内核分支或第三方模块可用。",
                    "你继续使用 ABK 即表示你已具备必要知识、备份和恢复能力，并愿意自行承担全部风险。"
                )
                TermsText("已阅读到本协议末尾后，才可点击“同意并继续”。")
            }
        },
        dismissButton = {
            TextButton(onClick = onDecline) {
                Text("不同意并退出")
            }
        },
        confirmButton = {
            Button(
                onClick = onAccept,
                enabled = canAccept
            ) {
                Text(if (canAccept) "同意并继续" else "请滑到底部")
            }
        }
    )
}

@Composable
private fun TermsSection(title: String, vararg paragraphs: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
    )
    paragraphs.forEach { paragraph ->
        TermsText(paragraph)
    }
}

@Composable
private fun TermsText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private enum class AbkTab(val label: String) {
    Status("当前状态"),
    Build("构建内核"),
    Modules("模块仓库"),
    Flash("刷写"),
    RuntimeHome("首页"),
    InstalledModules("已安装模块"),
    RootAuth("超级用户"),
    Settings("设置")
}

@Composable
private fun AbkMainScaffold(vm: MainViewModel) {
    val state by vm.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    var selectedTab by rememberSaveable { mutableStateOf(AbkTab.Status) }
    var flashDetailPageVisible by rememberSaveable { mutableStateOf(false) }
    var settingsThemePageVisible by rememberSaveable { mutableStateOf(false) }
    var buildPlanPageVisible by rememberSaveable { mutableStateOf(false) }
    var moduleRepositoryPageVisible by rememberSaveable { mutableStateOf(false) }
    var lastBackAt by remember { mutableStateOf(0L) }
    val runtimeManagerActive = state.abkRuntimeStatus != null
    val runtimeNativeManagerActive = state.abkRuntimeStatus?.runtimeBackend?.backend == "native"
    val visibleTabs = remember(state.runtimeNavigationEnabled, runtimeManagerActive, runtimeNativeManagerActive) {
        if (state.runtimeNavigationEnabled) {
            if (runtimeManagerActive) {
                buildList {
                    add(AbkTab.RuntimeHome)
                    add(AbkTab.InstalledModules)
                    if (runtimeNativeManagerActive) add(AbkTab.RootAuth)
                    add(AbkTab.Settings)
                }
            } else {
                listOf(AbkTab.RuntimeHome, AbkTab.Settings)
            }
        } else {
            listOf(AbkTab.Status, AbkTab.Build, AbkTab.Modules, AbkTab.Flash, AbkTab.Settings)
        }
    }
    val activeTab = if (selectedTab in visibleTabs) selectedTab else visibleTabs.first()
    val motionScheme = MaterialTheme.motionScheme
    val hideBottomBar = when (activeTab) {
        AbkTab.Build -> buildPlanPageVisible
        AbkTab.Modules -> moduleRepositoryPageVisible
        AbkTab.Flash -> flashDetailPageVisible
        AbkTab.Settings -> settingsThemePageVisible
        else -> false
    }

    LaunchedEffect(activeTab) {
        when (activeTab) {
            AbkTab.Build -> {
                moduleRepositoryPageVisible = false
                flashDetailPageVisible = false
                settingsThemePageVisible = false
            }
            AbkTab.Flash -> {
                buildPlanPageVisible = false
                moduleRepositoryPageVisible = false
                settingsThemePageVisible = false
            }
            AbkTab.Modules -> {
                buildPlanPageVisible = false
                flashDetailPageVisible = false
                settingsThemePageVisible = false
            }
            AbkTab.Settings -> {
                buildPlanPageVisible = false
                moduleRepositoryPageVisible = false
                flashDetailPageVisible = false
            }
            else -> {
                buildPlanPageVisible = false
                moduleRepositoryPageVisible = false
                flashDetailPageVisible = false
                settingsThemePageVisible = false
            }
        }
    }

    LaunchedEffect(visibleTabs, selectedTab, state.runtimeNavigationEnabled) {
        if (selectedTab !in visibleTabs) {
            selectedTab = if (state.runtimeNavigationEnabled) AbkTab.RuntimeHome else AbkTab.Status
        }
    }

    fun handleTopLevelBack() {
        val now = System.currentTimeMillis()
        if (now - lastBackAt <= EXIT_BACK_INTERVAL_MS) {
            context.findActivity()?.finish()
        } else {
            lastBackAt = now
            Toast.makeText(context, "再按一次退出 AnyBase Kernel", Toast.LENGTH_SHORT).show()
        }
    }

    if (!hideBottomBar) {
        BackHandler(onBack = ::handleTopLevelBack)
    }

    Scaffold(
        containerColor = uiSurfaceColor(MaterialTheme.colorScheme.surface),
        bottomBar = {
            AnimatedVisibility(
                visible = !hideBottomBar,
                enter = fadeIn(animationSpec = motionScheme.fastEffectsSpec()) +
                    slideInVertically(animationSpec = motionScheme.fastSpatialSpec()) { height -> height },
                exit = ExitTransition.None
            ) {
                NavigationBar(
                    containerColor = uiSurfaceColor(MaterialTheme.colorScheme.surfaceContainer),
                    tonalElevation = 0.dp
                ) {
                    visibleTabs.forEach { tab ->
                        NavigationBarItem(
                            selected = activeTab == tab,
                            onClick = { selectedTab = tab },
                            alwaysShowLabel = false,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            icon = {
                                Icon(
                                    imageVector = when (tab) {
                                        AbkTab.Status -> Icons.Default.Home
                                        AbkTab.Build -> Icons.Default.RocketLaunch
                                        AbkTab.Modules -> Icons.Default.LibraryBooks
                                        AbkTab.Flash -> if (state.rootGranted) Icons.Default.FlashOn else Icons.Default.FolderOpen
                                        AbkTab.RuntimeHome -> Icons.Default.Memory
                                        AbkTab.InstalledModules -> Icons.Default.Extension
                                        AbkTab.RootAuth -> Icons.Default.AdminPanelSettings
                                        AbkTab.Settings -> Icons.Default.Settings
                                    },
                                    contentDescription = tab.displayLabel(state.rootGranted)
                                )
                            },
                            label = {
                                Text(text = tab.displayLabel(state.rootGranted))
                            }
                        )
                    }
                }
            }
        }
    ) { padding ->
        val contentPadding = if (hideBottomBar) PaddingValues(0.dp) else padding
        androidx.compose.foundation.layout.Box(modifier = Modifier.padding(contentPadding).fillMaxSize()) {
            AnimatedContent(
                targetState = activeTab,
                transitionSpec = {
                    val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    (
                        fadeIn(animationSpec = motionScheme.defaultEffectsSpec()) +
                            slideInHorizontally(
                                animationSpec = motionScheme.defaultSpatialSpec()
                            ) { width -> direction * width / 4 }
                        ) togetherWith (
                        fadeOut(animationSpec = motionScheme.fastEffectsSpec()) +
                            slideOutHorizontally(
                                animationSpec = motionScheme.fastSpatialSpec()
                            ) { width -> -direction * width / 6 }
                        )
                },
                label = "abk-tab"
            ) { tab ->
                when (tab) {
                    AbkTab.Status -> StatusScreen(
                        vm = vm,
                        runtimeNavigationEnabled = state.runtimeNavigationEnabled,
                        onToggleRuntimeNavigation = { vm.setRuntimeNavigationEnabled(true) }
                    )
                    AbkTab.Build -> BuildScreen(
                        vm = vm,
                        outerPadding = contentPadding,
                        onPlanPageVisibleChange = { buildPlanPageVisible = it }
                    )
                    AbkTab.Modules -> ModuleRepositoryScreen(
                        vm = vm,
                        outerPadding = contentPadding,
                        onRepositoryPageVisibleChange = { moduleRepositoryPageVisible = it }
                    )
                    AbkTab.Flash -> FlashScreen(
                        vm = vm,
                        outerPadding = contentPadding,
                        onDetailPageVisibleChange = { flashDetailPageVisible = it }
                    )
                    AbkTab.RuntimeHome -> RuntimeHomeScreen(
                        vm = vm,
                        onSwitchToClassic = { vm.setRuntimeNavigationEnabled(false) }
                    )
                    AbkTab.InstalledModules -> InstalledModulesScreen(vm)
                    AbkTab.RootAuth -> RootAuthorizationScreen(vm)
                    AbkTab.Settings -> SettingsScreen(
                        vm = vm,
                        outerPadding = contentPadding,
                        onThemePageVisibleChange = { settingsThemePageVisible = it }
                    )
                }
            }
        }
    }
}

private fun AbkTab.displayLabel(rootGranted: Boolean): String = when (this) {
    AbkTab.Flash -> if (rootGranted) label else "文件"
    else -> label
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private const val EXIT_BACK_INTERVAL_MS = 2_000L
