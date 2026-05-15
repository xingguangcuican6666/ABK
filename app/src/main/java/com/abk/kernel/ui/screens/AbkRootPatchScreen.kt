@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package com.abk.kernel.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.abk.kernel.ui.components.AbkScreenHorizontalPadding
import com.abk.kernel.ui.components.ExpressiveListItem
import com.abk.kernel.ui.components.ExpressiveTopBar
import com.abk.kernel.ui.theme.uiSurfaceColor
import com.abk.kernel.utils.RootUtils
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class LkmPatchInstallMode {
    SelectFile,
    DirectInstall,
    OtaInstall,
    AnyKernel3
}

@Composable
fun AbkRootPatchScreen(
    rootGranted: Boolean,
    runtimeVariant: String,
    backgroundUri: String?,
    backgroundImageEnabled: Boolean,
    onBack: () -> Unit,
    onBackEnabledChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bundledAssets = remember(context) { RootUtils.listBundledAbkLkmAssets(context) }
    val currentKmi = remember { RootUtils.detectCurrentKmi() }
    val partitionOptions = remember { detectBootPartitionOptions() }
    val defaultPartition = remember(partitionOptions) {
        when {
            "init_boot" in partitionOptions -> "init_boot"
            "boot" in partitionOptions -> "boot"
            else -> "boot"
        }
    }

    var selectedMode by rememberSaveable { mutableStateOf<LkmPatchInstallMode?>(null) }
    var selectedVariant by rememberSaveable(runtimeVariant) {
        mutableStateOf(runtimeVariant.defaultLkmVariantId())
    }
    val kmiOptions = remember(bundledAssets, selectedVariant) {
        bundledAssets
            .filter { it.variantId == selectedVariant }
            .map { it.kmi }
            .distinct()
            .sortedWith(
                compareBy<String> {
                    it.substringAfter("android").substringBefore("-").toIntOrNull() ?: 0
                }.thenBy { it.substringAfter("-") }
            )
    }
    var selectedKmi by rememberSaveable { mutableStateOf(currentKmi.orEmpty()) }
    val selectedAsset = bundledAssets.firstOrNull {
        it.variantId == selectedVariant && it.kmi == selectedKmi
    }

    var selectedBootPath by rememberSaveable { mutableStateOf("") }
    var selectedBootName by rememberSaveable { mutableStateOf("") }
    var selectedAnyKernelPath by rememberSaveable { mutableStateOf("") }
    var selectedAnyKernelName by rememberSaveable { mutableStateOf("") }
    var selectedLocalLkmPath by rememberSaveable { mutableStateOf("") }
    var selectedLocalLkmName by rememberSaveable { mutableStateOf("") }
    var selectedPartition by rememberSaveable { mutableStateOf(defaultPartition) }
    var showPartitionMenu by remember { mutableStateOf(false) }
    var showAdvancedOptions by rememberSaveable { mutableStateOf(false) }
    var allowShell by rememberSaveable { mutableStateOf(false) }
    var enableAdb by rememberSaveable { mutableStateOf(false) }
    var patchedImagePath by rememberSaveable { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var success by remember { mutableStateOf<Boolean?>(null) }
    var logLines by remember { mutableStateOf(emptyList<String>()) }
    var currentAction by remember { mutableStateOf("") }

    val userlandKsudPath = remember(context) { RootUtils.resolveUserlandKsudPath(context) }
    val hasLocalLkm = selectedLocalLkmPath.isNotBlank()
    val activeLkmLabel = selectedLocalLkmName.takeIf { it.isNotBlank() }
        ?: selectedAsset?.let { "${it.variantLabel} · ${it.kmi}" }
        ?: ""
    val hasLkmSource = hasLocalLkm || selectedAsset != null
    val canPatchSelectedFile = selectedBootPath.isNotBlank() &&
        hasLkmSource &&
        !running &&
        (userlandKsudPath != null || rootGranted)
    val canDirectInstall = rootGranted && hasLkmSource && !running
    val canFlashAnyKernel3 = rootGranted && selectedAnyKernelPath.isNotBlank() && !running
    val canProceed = when (selectedMode) {
        LkmPatchInstallMode.SelectFile -> canPatchSelectedFile
        LkmPatchInstallMode.DirectInstall,
        LkmPatchInstallMode.OtaInstall -> canDirectInstall
        LkmPatchInstallMode.AnyKernel3 -> canFlashAnyKernel3
        null -> false
    }

    LaunchedEffect(selectedVariant, kmiOptions) {
        if (selectedKmi !in kmiOptions) {
            selectedKmi = currentKmi?.takeIf { it in kmiOptions } ?: kmiOptions.firstOrNull().orEmpty()
        }
    }

    LaunchedEffect(running) {
        onBackEnabledChange(!running)
    }

    DisposableEffect(Unit) {
        onDispose { onBackEnabledChange(true) }
    }

    fun appendLog(line: String) {
        scope.launch(Dispatchers.Main.immediate) {
            logLines = logLines + line
        }
    }

    fun copyText(label: String, value: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
        Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
    }

    val bootPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val staged = withContext(Dispatchers.IO) { stageContentUri(context, uri, "abk-lkm-boot", "boot.img") }
            selectedBootPath = staged.first.absolutePath
            selectedBootName = staged.second
            selectedMode = LkmPatchInstallMode.SelectFile
            patchedImagePath = ""
            success = null
            logLines = listOf("已选择 ${staged.second}")
        }
    }

    val anyKernelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        if (!isZipFile(context, uri)) {
            Toast.makeText(context, "仅支持 AnyKernel3 zip 文件", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val staged = withContext(Dispatchers.IO) {
                stageContentUri(context, uri, "abk-anykernel3", "AnyKernel3.zip")
            }
            selectedAnyKernelPath = staged.first.absolutePath
            selectedAnyKernelName = staged.second
            selectedMode = LkmPatchInstallMode.AnyKernel3
            patchedImagePath = ""
            success = null
            logLines = listOf("已选择 ${staged.second}")
        }
    }

    val localLkmPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        if (!isKoFile(context, uri)) {
            Toast.makeText(context, "仅支持 .ko LKM 文件", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val staged = withContext(Dispatchers.IO) { stageContentUri(context, uri, "abk-local-lkm", "kernelsu.ko") }
            selectedLocalLkmPath = staged.first.absolutePath
            selectedLocalLkmName = staged.second
            patchedImagePath = ""
            success = null
            logLines = listOf("已选择 ${staged.second}")
        }
    }

    fun beginOperation(action: String, lines: List<String>) {
        running = true
        success = null
        currentAction = action
        patchedImagePath = ""
        logLines = lines
    }

    fun finishPatchResult(result: RootUtils.BootPatchResult) {
        running = false
        success = result.success
        patchedImagePath = result.patchedImagePath.orEmpty()
        if (result.output.isNotEmpty()) logLines = result.output
        if (result.success && patchedImagePath.isNotBlank()) {
            logLines = logLines + "[ABK] 输出镜像: $patchedImagePath"
        }
    }

    fun startPatchSelectedFile() {
        if (!canPatchSelectedFile) return
        val modulePath = selectedLocalLkmPath.takeIf { it.isNotBlank() }
        beginOperation(
            action = "修补镜像",
            lines = listOf(
                "${'$'} ksud boot-patch --boot $selectedBootName --module ${activeLkmLabel.ifBlank { "LKM" }}",
                "partition: $selectedPartition"
            )
        )
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                RootUtils.patchAbkLkmBootImage(
                    context = context,
                    bootImagePath = selectedBootPath,
                    variantId = selectedVariant,
                    kmi = selectedKmi,
                    allowRootFallback = rootGranted,
                    partition = selectedPartition,
                    allowShell = allowShell,
                    enableAdb = enableAdb,
                    localModulePath = modulePath,
                    onOutput = ::appendLog
                )
            }
            finishPatchResult(result)
        }
    }

    fun startDirectInstall(ota: Boolean) {
        if (!canDirectInstall) return
        val modulePath = selectedLocalLkmPath.takeIf { it.isNotBlank() }
        val action = if (ota) "OTA 安装" else "直接安装"
        beginOperation(
            action = action,
            lines = listOf(
                "${'$'} ksud boot-patch --flash${if (ota) " --ota" else ""} --partition $selectedPartition",
                "module: ${activeLkmLabel.ifBlank { "LKM" }}"
            )
        )
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                RootUtils.patchAbkLkmBootImage(
                    context = context,
                    bootImagePath = null,
                    variantId = selectedVariant,
                    kmi = selectedKmi,
                    allowRootFallback = rootGranted,
                    flash = true,
                    ota = ota,
                    partition = selectedPartition,
                    allowShell = allowShell,
                    enableAdb = enableAdb,
                    localModulePath = modulePath,
                    onOutput = ::appendLog
                )
            }
            finishPatchResult(result)
        }
    }

    fun startAnyKernel3Flash() {
        if (!canFlashAnyKernel3) return
        beginOperation(
            action = "刷入 AnyKernel3",
            lines = listOf(
                "${'$'} flash AnyKernel3",
                "file: $selectedAnyKernelPath"
            )
        )
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                RootUtils.flashAnyKernel3(context, selectedAnyKernelPath, ::appendLog)
            }
            running = false
            success = result.success
            if (result.output.isNotEmpty()) logLines = result.output
        }
    }

    fun startFlashPatchedImage() {
        if (patchedImagePath.isBlank() || running) return
        beginOperation(
            action = "刷入已修补镜像",
            lines = listOf(
                "${'$'} dd $selectedPartition <- ${File(patchedImagePath).name}",
                "file: $patchedImagePath"
            )
        )
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                RootUtils.flashImage(
                    imagePath = patchedImagePath,
                    partition = selectedPartition,
                    onOutput = ::appendLog
                )
            }
            running = false
            success = result.success
            if (result.output.isNotEmpty()) logLines = result.output
        }
    }

    fun startNext() {
        when (selectedMode) {
            LkmPatchInstallMode.SelectFile -> startPatchSelectedFile()
            LkmPatchInstallMode.DirectInstall -> startDirectInstall(ota = false)
            LkmPatchInstallMode.OtaInstall -> startDirectInstall(ota = true)
            LkmPatchInstallMode.AnyKernel3 -> startAnyKernel3Flash()
            null -> Unit
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LkmPatchPageBackground(
            backgroundUri = backgroundUri,
            backgroundImageEnabled = backgroundImageEnabled
        )
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                ExpressiveTopBar(
                    title = "安装",
                    navigationIcon = {
                        IconButton(onClick = onBack, enabled = !running) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = AbkScreenHorizontalPadding)
                    .padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
            PatchGroupCard {
                PatchModeRow(
                    title = "选择一个文件",
                    subtitle = selectedBootName.ifBlank { "建议选择 init_boot 分区镜像" },
                    selected = selectedMode == LkmPatchInstallMode.SelectFile,
                    enabled = !running,
                    onClick = {
                        selectedMode = LkmPatchInstallMode.SelectFile
                        bootPicker.launch(arrayOf("application/octet-stream", "image/*", "*/*"))
                    }
                )
                PatchDivider()
                PatchModeRow(
                    title = "直接安装（推荐）",
                    subtitle = if (rootGranted) "自动识别当前 boot / init_boot 并直接修补" else "需要 Root 权限",
                    selected = selectedMode == LkmPatchInstallMode.DirectInstall,
                    enabled = !running && rootGranted,
                    onClick = {
                        selectedMode = LkmPatchInstallMode.DirectInstall
                        patchedImagePath = ""
                        success = null
                        currentAction = ""
                        logLines = emptyList()
                    }
                )
                PatchDivider()
                PatchModeRow(
                    title = "安装到未使用的槽位（OTA 后）",
                    subtitle = if (rootGranted) "修补并写入另一槽位" else "需要 Root 权限",
                    selected = selectedMode == LkmPatchInstallMode.OtaInstall,
                    enabled = !running && rootGranted,
                    onClick = {
                        selectedMode = LkmPatchInstallMode.OtaInstall
                        patchedImagePath = ""
                        success = null
                        currentAction = ""
                        logLines = emptyList()
                    }
                )
                PatchDivider()
                PatchModeRow(
                    title = "AnyKernel3 内核",
                    subtitle = selectedAnyKernelName.ifBlank { "刷入 AnyKernel3 格式的内核 zip 包" },
                    selected = selectedMode == LkmPatchInstallMode.AnyKernel3,
                    enabled = !running && rootGranted,
                    onClick = {
                        selectedMode = LkmPatchInstallMode.AnyKernel3
                        patchedImagePath = ""
                        success = null
                        currentAction = ""
                        logLines = emptyList()
                        anyKernelPicker.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                    }
                )
            }

            PatchGroupCard {
                androidx.compose.foundation.layout.Box {
                    ExpressiveListItem(
                        title = "选择分区",
                        subtitle = "当前槽位目标分区",
                        leadingIcon = Icons.Default.Edit,
                        enabled = !running && selectedMode != LkmPatchInstallMode.AnyKernel3,
                        trailingContent = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = partitionLabel(selectedPartition, defaultPartition),
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.End,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Icon(Icons.Default.ChevronRight, contentDescription = "选择分区")
                            }
                        },
                        onClick = { showPartitionMenu = true }
                    )
                    DropdownMenu(
                        expanded = showPartitionMenu,
                        onDismissRequest = { showPartitionMenu = false }
                    ) {
                        partitionOptions.forEach { partition ->
                            DropdownMenuItem(
                                text = { Text(partitionMenuLabel(partition, defaultPartition)) },
                                onClick = {
                                    selectedPartition = partition
                                    showPartitionMenu = false
                                }
                            )
                        }
                    }
                }
            }

            PatchGroupCard {
                ExpressiveListItem(
                    title = "使用本地 LKM 文件",
                    subtitle = selectedLocalLkmName.ifBlank {
                        selectedAsset?.let { "当前内置: ${it.variantLabel} · ${it.kmi}" }
                            ?: "选择本地 .ko 文件或使用内置 LKM"
                    },
                    leadingIcon = Icons.Default.FolderOpen,
                    enabled = !running,
                    trailingContent = {
                        if (selectedLocalLkmPath.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    selectedLocalLkmPath = ""
                                    selectedLocalLkmName = ""
                                },
                                enabled = !running
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "清除本地 LKM")
                            }
                        } else {
                            Icon(Icons.Default.ChevronRight, contentDescription = "选择本地 LKM")
                        }
                    },
                    onClick = { localLkmPicker.launch(arrayOf("application/octet-stream", "*/*")) }
                )
                AnimatedVisibility(
                    visible = selectedLocalLkmPath.isBlank(),
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RootUtils.ABK_LKM_VARIANTS.forEach { variant ->
                                FilterChip(
                                    selected = selectedVariant == variant.id,
                                    onClick = {
                                        selectedVariant = variant.id
                                        patchedImagePath = ""
                                        success = null
                                        currentAction = ""
                                        logLines = emptyList()
                                    },
                                    enabled = !running,
                                    label = { Text(variant.label) }
                                )
                            }
                        }
                        DropdownField(
                            label = "KMI",
                            value = selectedKmi.ifBlank { "无可用 LKM" },
                            options = kmiOptions.ifEmpty { listOf("无可用 LKM") },
                            recommendedValue = currentKmi?.takeIf { it in kmiOptions },
                            onSelect = {
                                if (it in kmiOptions) {
                                    selectedKmi = it
                                    patchedImagePath = ""
                                    success = null
                                    currentAction = ""
                                    logLines = emptyList()
                                }
                            }
                        )
                    }
                }
            }

            PatchGroupCard {
                ExpressiveListItem(
                    title = "高级选项",
                    leadingIcon = Icons.Default.Tune,
                    trailingContent = {
                        Icon(
                            if (showAdvancedOptions) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "展开高级选项"
                        )
                    },
                    onClick = { showAdvancedOptions = !showAdvancedOptions }
                )
                AnimatedVisibility(
                    visible = showAdvancedOptions,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column {
                        PatchDivider()
                        PatchCheckboxItem(
                            title = "总是给 shell 授予 root 权限",
                            subtitle = "总是允许 adb shell 调用 su，非必要请勿开启。",
                            checked = allowShell,
                            enabled = !running,
                            onCheckedChange = { allowShell = it }
                        )
                        PatchDivider()
                        PatchCheckboxItem(
                            title = "启动时强制启用 ADB 调试",
                            subtitle = "强制允许 USB 调试并取消 adb 认证，非必要请勿开启。",
                            checked = enableAdb,
                            enabled = !running,
                            onCheckedChange = { enableAdb = it }
                        )
                    }
                }
            }

            if (!hasLkmSource && selectedMode != LkmPatchInstallMode.AnyKernel3) {
                InlineWarning("当前变体和 KMI 没有内置 LKM，请选择本地 .ko 文件。")
            }
            if (
                selectedMode == LkmPatchInstallMode.SelectFile &&
                userlandKsudPath == null &&
                !rootGranted
            ) {
                InlineWarning("未检测到可直接执行的 ksud；未授权 Root 时无法进行本地修补。")
            }

            Button(
                onClick = ::startNext,
                enabled = canProceed,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (running) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(17.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("处理中")
                } else {
                    Text("下一步")
                }
            }

            if (patchedImagePath.isNotBlank()) {
                PatchedImageCard(
                    path = patchedImagePath,
                    canFlash = selectedMode == LkmPatchInstallMode.SelectFile && rootGranted && !running,
                    onCopy = { copyText("patched boot", patchedImagePath) },
                    onFlash = ::startFlashPatchedImage
                )
            }

            if (running || success != null || logLines.isNotEmpty()) {
                PatchLogCard(
                    running = running,
                    success = success,
                    action = currentAction,
                    lines = logLines,
                    canReboot = success == true && currentAction != "修补镜像",
                    onReboot = {
                        if (!running) scope.launch(Dispatchers.IO) { RootUtils.reboot() }
                    }
                )
            }

                Spacer(Modifier.height(80.dp))
            }
        }
    }
}

@Composable
private fun LkmPatchPageBackground(
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
private fun PatchGroupCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = uiSurfaceColor(MaterialTheme.colorScheme.surfaceContainer)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(content = content)
    }
}

@Composable
private fun PatchModeRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    ExpressiveListItem(
        title = title,
        subtitle = subtitle,
        enabled = enabled,
        selected = selected,
        leadingContent = {
            RadioButton(
                selected = selected,
                onClick = null,
                enabled = enabled
            )
        },
        onClick = onClick
    )
}

@Composable
private fun PatchCheckboxItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ExpressiveListItem(
        title = title,
        subtitle = subtitle,
        enabled = enabled,
        leadingContent = {
            Checkbox(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled
            )
        },
        onClick = { onCheckedChange(!checked) }
    )
}

@Composable
private fun PatchDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 14.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    )
}

@Composable
private fun InlineWarning(text: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
            Icons.Default.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(18.dp)
        )
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun PatchedImageCard(
    path: String,
    canFlash: Boolean,
    onCopy: () -> Unit,
    onFlash: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = uiSurfaceColor(MaterialTheme.colorScheme.surfaceContainer)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(20.dp))
                Text(
                    text = "修补结果",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onCopy) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "复制路径")
                }
                if (canFlash) {
                    AssistChip(
                        onClick = onFlash,
                        label = { Text("刷入") },
                        leadingIcon = { Icon(Icons.Default.FlashOn, null, modifier = Modifier.size(16.dp)) }
                    )
                }
            }
            Text(
                path,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PatchLogCard(
    running: Boolean,
    success: Boolean?,
    action: String,
    lines: List<String>,
    canReboot: Boolean,
    onReboot: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = uiSurfaceColor(MaterialTheme.colorScheme.surfaceContainer)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val icon = when {
                    running -> Icons.Default.Terminal
                    success == true -> Icons.Default.CheckCircle
                    success == false -> Icons.Default.Error
                    else -> Icons.Default.Info
                }
                Icon(icon, null, modifier = Modifier.size(20.dp))
                Text(
                    action.ifBlank { "日志" },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.weight(1f))
                if (canReboot) {
                    AssistChip(
                        onClick = onReboot,
                        label = { Text("重启") },
                        leadingIcon = { Icon(Icons.Default.RestartAlt, null, modifier = Modifier.size(16.dp)) }
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 84.dp, max = 280.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                val displayLines = lines.ifEmpty { listOf("等待操作") }
                displayLines.forEach { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = if (line.firstOrNull() == '$') {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }
        }
    }
}

private suspend fun stageContentUri(
    context: Context,
    uri: Uri,
    directoryName: String,
    fallbackName: String
): Pair<File, String> = withContext(Dispatchers.IO) {
    val displayName = displayNameForUri(context, uri).takeIf { it.isNotBlank() } ?: fallbackName
    val safeName = displayName.replace(Regex("""[^A-Za-z0-9._-]"""), "_")
    val dir = File(context.cacheDir, directoryName).apply {
        deleteRecursively()
        mkdirs()
    }
    val target = File(dir, safeName)
    context.contentResolver.openInputStream(uri)?.use { input ->
        target.outputStream().use { output -> input.copyTo(output) }
    } ?: error("无法读取选择的文件")
    target to displayName
}

private fun displayNameForUri(context: Context, uri: Uri): String {
    return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && index >= 0) cursor.getString(index).orEmpty() else ""
        }
        .orEmpty()
        .ifBlank { uri.lastPathSegment.orEmpty().substringAfterLast('/') }
}

private fun isKoFile(context: Context, uri: Uri): Boolean {
    val name = displayNameForUri(context, uri)
    return uri.lastPathSegment.orEmpty().endsWith(".ko", ignoreCase = true) ||
        name.endsWith(".ko", ignoreCase = true)
}

private fun isZipFile(context: Context, uri: Uri): Boolean {
    val name = displayNameForUri(context, uri)
    return uri.lastPathSegment.orEmpty().endsWith(".zip", ignoreCase = true) ||
        name.endsWith(".zip", ignoreCase = true)
}

private fun detectBootPartitionOptions(): List<String> {
    val candidates = listOf("init_boot", "boot", "vendor_boot")
    val detected = candidates.filter(::partitionExists)
    return detected.ifEmpty { candidates }
}

private fun partitionExists(name: String): Boolean {
    return listOf(
        "/dev/block/by-name/$name",
        "/dev/block/bootdevice/by-name/$name",
        "/dev/block/mapper/$name"
    ).any { File(it).exists() }
}

private fun partitionLabel(partition: String, defaultPartition: String): String =
    if (partition == defaultPartition) "$partition\n(default)" else partition

private fun partitionMenuLabel(partition: String, defaultPartition: String): String =
    if (partition == defaultPartition) "$partition (default)" else partition

private fun String.defaultLkmVariantId(): String {
    val lower = lowercase()
    return when {
        "resukisu" in lower -> "resukisu"
        "sukisu" in lower -> "sukisu"
        else -> "kernelsu"
    }
}
