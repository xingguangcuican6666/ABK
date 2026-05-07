package com.abk.kernel.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.abk.kernel.R
import com.abk.kernel.data.model.BuildProgress
import com.abk.kernel.data.model.BuildStepProgress
import com.abk.kernel.data.model.BuildStatus
import com.abk.kernel.data.model.CustomExternalModule
import com.abk.kernel.data.model.CustomExternalModuleStage
import com.abk.kernel.data.model.KernelSupport
import com.abk.kernel.data.model.KernelBuildConfig
import com.abk.kernel.ui.components.ExpressiveHeroCard
import com.abk.kernel.ui.components.ExpressiveSectionCard
import com.abk.kernel.ui.components.ExpressiveStatusChip
import com.abk.kernel.ui.components.ExpressiveTopBar
import com.abk.kernel.viewmodel.MainViewModel
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuildScreen(vm: MainViewModel) {
    val state by vm.uiState.collectAsState()
    val uriHandler = LocalUriHandler.current
    val rawConfig = state.buildConfig
    val config = remember(rawConfig) { KernelSupport.normalize(rawConfig) }
    val recommended = state.recommendedBuildConfig
    val ksuBranchOptions = listOf("Stable(标准)", "Dev(开发)")
    val droidspacesOptions = remember(config.kernelVersion) {
        KernelSupport.droidspacesOptions(config.kernelVersion)
    }
    val subLevelOptions = remember(config.androidVersion, config.kernelVersion) {
        KernelSupport.subLevelOptions(config.androidVersion, config.kernelVersion)
    }
    val osPatchOptions = remember(config.androidVersion, config.kernelVersion, config.subLevel) {
        KernelSupport.patchLevelOptions(config.androidVersion, config.kernelVersion, config.subLevel)
    }
    val versionPreview = remember(config.version, config.kernelVersion, config.subLevel) {
        buildVersionPreview(config)
    }
    val buildTimePreview = remember(config.buildTime) {
        buildTimePreview(config.buildTime)
    }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var customModuleUrl by remember { mutableStateOf("") }
    var customModuleStage by remember { mutableStateOf(CustomExternalModuleStage.AFTER_PATCH) }

    LaunchedEffect(config, rawConfig) {
        if (config != rawConfig) vm.updateBuildConfig(config)
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            icon = { Icon(Icons.Default.Build, null) },
            title = { Text("确认提交构建") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("构建配置概览：", fontWeight = FontWeight.SemiBold)
                    Text("Android ${config.androidVersion} · 内核 ${config.kernelVersion}.${config.subLevel}")
                    Text("KSU: ${config.kernelsuVariant} (${config.kernelsuBranch})")
                    Text("补丁级别: ${config.osPatchLevel}")
                    Text("SUSFS: ${if (!config.cancelSusfs) "启用" else "禁用"} · ZRAM: ${if (config.useZram) "启用" else "禁用"} · KPM: ${if (config.useKpm) "启用" else "禁用"}")
                    Text("BBG: ${if (config.useBbg) "启用" else "禁用"} · DDK: ${if (config.useDdk) "启用" else "禁用"}")
                    Text("Droidspaces: ${droidspacesLabel(config.droidspaces)}")
                    Text(
                        "外部模块: ${
                            if (config.useCustomExternalModules) "${config.customExternalModules.size} 个" else "未启用"
                        }"
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    showConfirmDialog = false
                    vm.dispatchBuild(config)
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    state.workflowEnablementPrompt?.let { prompt ->
        AlertDialog(
            onDismissRequest = { vm.dismissWorkflowEnablementPrompt() },
            icon = { Icon(Icons.Default.OpenInBrowser, null) },
            title = { Text("需要启用工作流") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("提交构建前，ABK 无法确认或启用 GitHub Actions 构建工作流。")
                    Text("请在浏览器中登录 GitHub，并确认当前账号有此 Fork 仓库的 Actions/Workflow 权限。")
                    Text("打开页面后，如果看到 Enable workflow 或启用工作流，请手动启用，再返回 ABK 重新提交构建。")
                    Text(
                        text = "检查结果：${prompt.message}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        runCatching { uriHandler.openUri(prompt.actionUrl) }
                        vm.dismissWorkflowEnablementPrompt()
                    }
                ) {
                    Icon(Icons.Default.OpenInBrowser, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("打开 Actions 页面")
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.dismissWorkflowEnablementPrompt() }) {
                    Text("稍后处理")
                }
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            ExpressiveTopBar(
                title = stringResource(R.string.build_title)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            BuildPlanHero(config, recommended, state.buildStatus)

            AnimatedVisibility(
                visible = state.buildStatus != BuildStatus.IDLE,
                enter = fadeIn() + slideInVertically { -it / 3 } + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    BuildStatusBanner(state.buildStatus, state.buildProgress)
                    BuildProgressCard(state.buildProgress)
                }
            }

            // ── 内核版本配置 ──────────────────────────────────────────────
            SectionCard(title = "内核版本配置") {
                DropdownField(
                    label = "Android 版本",
                    value = config.androidVersion,
                    options = KernelSupport.androidVersions(),
                    recommendedValue = recommended?.androidVersion,
                    onSelect = {
                        vm.updateBuildConfig(
                            KernelSupport.normalize(
                                config.copy(
                                    androidVersion = it,
                                    kernelVersion = KernelSupport.kernelForAndroid(it)
                                )
                            )
                        )
                    }
                )
                DropdownField(
                    label = "内核版本",
                    value = config.kernelVersion,
                    options = KernelSupport.kernelVersions(),
                    recommendedValue = recommended?.kernelVersion,
                    onSelect = {
                        vm.updateBuildConfig(
                            KernelSupport.normalize(
                                config.copy(
                                    androidVersion = KernelSupport.androidForKernel(it),
                                    kernelVersion = it
                                )
                            )
                        )
                    }
                )
                DropdownField(
                    label = "子版本号",
                    value = config.subLevel,
                    options = subLevelOptions,
                    recommendedValue = recommended
                        ?.takeIf {
                            it.androidVersion == config.androidVersion && it.kernelVersion == config.kernelVersion
                        }
                        ?.subLevel,
                    onSelect = {
                        vm.updateBuildConfig(KernelSupport.normalize(config.copy(subLevel = it)))
                    }
                )
                DropdownField(
                    label = "安全补丁级别",
                    value = config.osPatchLevel,
                    options = osPatchOptions,
                    recommendedValue = recommended
                        ?.takeIf {
                            it.androidVersion == config.androidVersion &&
                                it.kernelVersion == config.kernelVersion &&
                                it.subLevel == config.subLevel
                        }
                        ?.osPatchLevel,
                    onSelect = {
                        vm.updateBuildConfig(config.copy(osPatchLevel = it))
                    }
                )
                if (config.kernelVersion == "5.10") {
                    OutlinedTextField(
                        value = config.revision,
                        onValueChange = { vm.updateBuildConfig(config.copy(revision = it)) },
                        label = {
                            Text(recommended?.revision?.let { "修订版本（推荐：$it）" } ?: "修订版本 (5.10 专用)")
                        },
                        placeholder = { Text("如: r11") },
                        shape = FieldShape(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }

            // ── KernelSU 配置 ────────────────────────────────────────────
            SectionCard(title = "KernelSU 配置") {
                DropdownField(
                    label = "KernelSU 变体",
                    value = config.kernelsuVariant,
                    options = listOf("Official", "SukiSU", "ReSukiSU"),
                    onSelect = { vm.updateBuildConfig(config.copy(kernelsuVariant = it)) }
                )
                DropdownField(
                    label = "KSU 分支",
                    value = config.kernelsuBranch.takeIf { it in ksuBranchOptions } ?: "Stable(标准)",
                    options = ksuBranchOptions,
                    onSelect = { vm.updateBuildConfig(config.copy(kernelsuBranch = it)) }
                )
            }

            // ── 功能开关 ─────────────────────────────────────────────────
            SectionCard(title = "功能开关") {
                SwitchRow("启用 SUSFS", !config.cancelSusfs) {
                    vm.updateBuildConfig(config.copy(cancelSusfs = !it))
                }
                SwitchRow("启用 ZRAM 增强算法", config.useZram) {
                    vm.updateBuildConfig(config.copy(useZram = it))
                }
                SwitchRow("启用 BBG 防格机", config.useBbg) {
                    vm.updateBuildConfig(config.copy(useBbg = it))
                }
                SwitchRow("启用 DDK 防格机 LSM", config.useDdk) {
                    vm.updateBuildConfig(config.copy(useDdk = it))
                }
                SwitchRow("启用 KPM 功能", config.useKpm) {
                    vm.updateBuildConfig(config.copy(useKpm = it))
                }
                SwitchRow("启用 Re-Kernel 驱动 (测试)", config.useRekernel) {
                    vm.updateBuildConfig(config.copy(useRekernel = it))
                }
                DropdownField(
                    label = "Droidspaces 容器支持",
                    value = config.droidspaces,
                    options = droidspacesOptions,
                    onSelect = { vm.updateBuildConfig(config.copy(droidspaces = it)) }
                )
                SwitchRow("启用一加 8E 支持", config.suppOp) {
                    vm.updateBuildConfig(config.copy(suppOp = it))
                }
            }

            // ── ZRAM 扩展选项 ────────────────────────────────────────────
            AnimatedVisibility(config.useZram) {
                SectionCard(title = "ZRAM 扩展选项") {
                    SwitchRow("启用完整算法支持 (LZO/LZ4/ZSTD 等)", config.zramFullAlgo) {
                        vm.updateBuildConfig(config.copy(zramFullAlgo = it))
                    }
                    if (!config.zramFullAlgo) {
                        OutlinedTextField(
                            value = config.zramExtraAlgos,
                            onValueChange = { vm.updateBuildConfig(config.copy(zramExtraAlgos = it)) },
                            label = { Text("自定义 ZRAM 算法") },
                            placeholder = { Text("如: lzo,lz4,deflate,zstd") },
                            shape = FieldShape(),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }
            }

            // ── KPM 扩展选项 ─────────────────────────────────────────────
            AnimatedVisibility(config.useKpm) {
                SectionCard(title = "KPM 扩展选项") {
                    OutlinedTextField(
                        value = config.kpmPassword,
                        onValueChange = { vm.updateBuildConfig(config.copy(kpmPassword = it)) },
                        label = { Text("KPM 超级密码 (可选)") },
                        placeholder = { Text("留空使用默认密码") },
                        shape = FieldShape(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }

            // ── 自定义外部模块 ───────────────────────────────────────────
            SectionCard(title = "自定义外部模块") {
                SwitchRow("启用自定义外部模块", config.useCustomExternalModules) {
                    vm.updateBuildConfig(config.copy(useCustomExternalModules = it))
                }
                AnimatedVisibility(config.useCustomExternalModules) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = customModuleUrl,
                            onValueChange = { customModuleUrl = it },
                            label = { Text("仓库链接") },
                            placeholder = { Text("https://github.com/user/module") },
                            shape = FieldShape(),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        DropdownField(
                            label = "注入阶段",
                            value = customModuleStage,
                            options = CustomExternalModuleStage.options,
                            onSelect = { customModuleStage = it }
                        )
                        Button(
                            onClick = {
                                val cleanUrl = customModuleUrl.trim()
                                if (cleanUrl.isNotEmpty()) {
                                    vm.updateBuildConfig(
                                        config.copy(
                                            customExternalModules = config.customExternalModules + CustomExternalModule(
                                                url = cleanUrl,
                                                stage = customModuleStage
                                            )
                                        )
                                    )
                                    customModuleUrl = ""
                                }
                            },
                            enabled = customModuleUrl.isNotBlank(),
                            shape = FieldShape(),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Icon(Icons.Default.Add, null)
                            Spacer(Modifier.width(8.dp))
                            Text("添加模块")
                        }

                        config.customExternalModules.forEachIndexed { index, module ->
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            CustomExternalModuleStage.normalize(module.stage),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            module.url,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            vm.updateBuildConfig(
                                                config.copy(
                                                    customExternalModules = config.customExternalModules
                                                        .filterIndexed { i, _ -> i != index }
                                                )
                                            )
                                        }
                                    ) {
                                        Icon(Icons.Default.Delete, null)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── 可选配置 ─────────────────────────────────────────────────
            SectionCard(title = "可选配置") {
                OutlinedTextField(
                    value = config.version,
                    onValueChange = { vm.updateBuildConfig(config.copy(version = it)) },
                    label = { Text("自定义版本名 (可选)") },
                    shape = FieldShape(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                ConfigPreviewText(versionPreview)
                OutlinedTextField(
                    value = config.buildTime,
                    onValueChange = { vm.updateBuildConfig(config.copy(buildTime = it)) },
                    label = { Text("自定义构建时间 (可选)") },
                    placeholder = { Text("留空/N=当前 UTC 时间") },
                    shape = FieldShape(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                ConfigPreviewText(buildTimePreview)
            }

            // Submit button
            Button(
                onClick = { showConfirmDialog = true },
                enabled = !state.isLoading && state.buildStatus !in listOf(
                    BuildStatus.QUEUED, BuildStatus.IN_PROGRESS
                ),
                shape = RoundedCornerShape(30.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                modifier = Modifier.fillMaxWidth().height(64.dp)
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        Modifier.size(20.dp), strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                } else {
                    Icon(Icons.Default.RocketLaunch, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.build_submit))
                }
            }

            // Error
            state.error?.let { err ->
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text(err, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f))
                        IconButton(onClick = { vm.clearError() }) {
                            Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun ConfigPreviewText(preview: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.Visibility,
                null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(17.dp)
            )
            Text(
                preview,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun BuildPlanHero(
    config: KernelBuildConfig,
    recommended: KernelBuildConfig?,
    status: BuildStatus
) {
    val isRecommended = recommended != null &&
        config.androidVersion == recommended.androidVersion &&
        config.kernelVersion == recommended.kernelVersion &&
        config.subLevel == recommended.subLevel &&
        config.osPatchLevel == recommended.osPatchLevel

    ExpressiveHeroCard(
        title = "${config.kernelVersion}.${config.subLevel} · ${config.androidVersion.removePrefix("android").let { "Android $it" }}",
        subtitle = "触发 GitHub Actions 并自动整理 img、AK3、管理器和 SUSFS 模块。",
        icon = Icons.Default.RocketLaunch,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        badge = {
            ExpressiveStatusChip(
                label = config.kernelsuVariant,
                icon = Icons.Default.Shield,
                color = MaterialTheme.colorScheme.primary
            )
            ExpressiveStatusChip(
                label = if (!config.cancelSusfs) "SUSFS 开启" else "SUSFS 关闭",
                icon = Icons.Default.Extension,
                color = if (!config.cancelSusfs) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline
            )
            if (config.droidspaces != "off") {
                ExpressiveStatusChip(
                    label = "Droidspaces ${droidspacesLabel(config.droidspaces)}",
                    icon = Icons.Default.Extension,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            ExpressiveStatusChip(
                label = if (isRecommended) "设备推荐" else buildStatusLabel(status),
                icon = if (isRecommended) Icons.Default.AutoAwesome else Icons.Default.RunCircle,
                color = if (isRecommended) MaterialTheme.colorScheme.tertiary else buildStatusColor(status)
            )
        }
    )
}

private fun droidspacesLabel(value: String): String = when (value) {
    "off" -> "关闭"
    "on" -> "开启"
    "678" -> "槽位 6/7/8"
    "123" -> "槽位 1/2/3"
    "345" -> "槽位 3/4/5"
    else -> value
}

private fun buildVersionPreview(config: KernelBuildConfig): String {
    val compact = config.version.filterNot { it.isWhitespace() }
    if (compact.isBlank()) {
        return "预览：留空时使用工作流默认本地版本"
    }
    val cleanVersion = compact.replace(Regex("""^[0-9]+\.[0-9]+\.[0-9]+"""), "")
    val preview = "${config.kernelVersion}.${config.subLevel}$cleanVersion"
    return "预览：$preview"
}

private fun buildTimePreview(buildTime: String): String {
    val input = buildTime.trim()
    if (input.isBlank() || input.equals("N", ignoreCase = true)) {
        val sample = ZonedDateTime.now(ZoneOffset.UTC).format(BUILD_TIME_FORMATTER)
        return "预览：使用工作流运行时当前 UTC（示例：$sample）"
    }
    return "预览：KBUILD_BUILD_TIMESTAMP=$input"
}

private val BUILD_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss z yyyy", Locale.US)

@Composable
private fun BuildStatusBanner(status: BuildStatus, progress: BuildProgress) {
    val (icon, text, color) = when (status) {
        BuildStatus.QUEUED -> Triple(Icons.Default.Queue, "构建已排队，等待运行…", MaterialTheme.colorScheme.tertiary)
        BuildStatus.IN_PROGRESS -> Triple(Icons.Default.RunCircle, "构建进行中…", MaterialTheme.colorScheme.secondary)
        BuildStatus.SUCCESS -> Triple(Icons.Default.CheckCircle, "构建成功！", MaterialTheme.colorScheme.primary)
        BuildStatus.FAILURE -> Triple(Icons.Default.Error, "构建失败", MaterialTheme.colorScheme.error)
        BuildStatus.CANCELLED -> Triple(Icons.Default.Cancel, "构建已取消", MaterialTheme.colorScheme.outline)
        else -> return
    }
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.13f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (status == BuildStatus.IN_PROGRESS) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = color)
            } else {
                Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(text, color = color, style = MaterialTheme.typography.bodyMedium)
                if (progress.totalSteps > 0) {
                    Text(
                        "${progress.percent}% · ${progress.currentStep}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun BuildProgressCard(progress: BuildProgress) {
    val animatedProgress by animateFloatAsState(
        targetValue = (progress.percent / 100f).coerceIn(0f, 1f),
        label = "build-progress"
    )
    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("工作流进度", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("${progress.percent}%", style = MaterialTheme.typography.labelLarge)
            }
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                progress.currentStep,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 2
            )
            AnimatedVisibility(
                visible = progress.steps.isNotEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    progress.steps.take(8).forEach { step ->
                        BuildStepRow(step)
                    }
                    if (progress.steps.size > 8) {
                        Text(
                            "还有 ${progress.steps.size - 8} 个步骤在后台跟踪",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BuildStepRow(step: BuildStepProgress) {
    val (icon, color, label) = when {
        step.status == "completed" && step.conclusion in listOf("failure", "cancelled", "timed_out") ->
            Triple(Icons.Default.Error, MaterialTheme.colorScheme.error, "失败")
        step.status == "completed" ->
            Triple(Icons.Default.CheckCircle, MaterialTheme.colorScheme.primary, "完成")
        step.status == "in_progress" ->
            Triple(Icons.Default.Sync, MaterialTheme.colorScheme.tertiary, "进行中")
        else -> Triple(Icons.Default.RadioButtonUnchecked, MaterialTheme.colorScheme.outline, "等待")
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        Text(step.name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1)
        Text(label, color = color, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    ExpressiveSectionCard(
        title = title,
        subtitle = when (title) {
            "内核版本配置" -> "优先使用设备识别出的推荐参数，避免手动填错版本线。"
            "KernelSU 配置" -> "选择内核权限方案和对应分支。"
            "功能开关" -> "按需开启模块能力，越少改动越利于排查问题。"
            "ZRAM 扩展选项" -> "为内存压缩算法加入额外内核支持。"
            "KPM 扩展选项" -> "用于 KPM 功能的可选安全参数。"
            "自定义外部模块" -> "按阶段执行外部仓库根目录的 setup.sh。"
            else -> "这些字段会被保存，下次打开不会重置。"
        },
        icon = when (title) {
            "内核版本配置" -> Icons.Default.Memory
            "KernelSU 配置" -> Icons.Default.Shield
            "功能开关" -> Icons.Default.Tune
            "ZRAM 扩展选项" -> Icons.Default.Compress
            "KPM 扩展选项" -> Icons.Default.Key
            "自定义外部模块" -> Icons.Default.Extension
            else -> Icons.Default.Edit
        },
        content = content
    )
}

@Composable
private fun buildStatusColor(status: BuildStatus) = when (status) {
    BuildStatus.IDLE -> MaterialTheme.colorScheme.outline
    BuildStatus.QUEUED -> MaterialTheme.colorScheme.tertiary
    BuildStatus.IN_PROGRESS -> MaterialTheme.colorScheme.secondary
    BuildStatus.SUCCESS -> MaterialTheme.colorScheme.primary
    BuildStatus.FAILURE -> MaterialTheme.colorScheme.error
    BuildStatus.CANCELLED -> MaterialTheme.colorScheme.outline
}

private fun buildStatusLabel(status: BuildStatus): String = when (status) {
    BuildStatus.IDLE -> "准备构建"
    BuildStatus.QUEUED -> "已排队"
    BuildStatus.IN_PROGRESS -> "构建中"
    BuildStatus.SUCCESS -> "构建成功"
    BuildStatus.FAILURE -> "构建失败"
    BuildStatus.CANCELLED -> "已取消"
}

@Composable
private fun FieldShape() = RoundedCornerShape(20.dp)

@Composable
fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = if (checked) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.78f)
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (checked) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.weight(1f)
            )
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropdownField(
    label: String,
    value: String,
    options: List<String>,
    recommendedValue: String? = null,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            shape = FieldShape(),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                val text = if (opt == recommendedValue) "$opt（推荐）" else opt
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = { onSelect(opt); expanded = false },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}
