package com.abk.kernel.miuix.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility

import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut

import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abk.kernel.R
import com.abk.kernel.miuix.util.BlurredBar
import com.abk.kernel.miuix.util.rememberBlurBackdrop
import com.abk.kernel.ui.components.AbkScreenHorizontalPadding
import com.abk.kernel.ui.components.AppPageBackground
import com.abk.kernel.ui.screens.preferredLkmKmiSelection
import com.abk.kernel.utils.RootUtils
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator

import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

private enum class LkmPatchInstallMode {
    SelectFile,
    DirectInstall,
    OtaInstall,
    AnyKernel3
}

@Composable
fun AbkRootPatchScreenMiuix(
    rootGranted: Boolean,
    hasNativeManagerPermission: Boolean,
    runtimeVariant: String,
    backgroundUri: String?,
    backgroundImageEnabled: Boolean,
    onBack: () -> Unit,
    onBackEnabledChange: (Boolean) -> Unit = {},
    onFeedback: (String, Boolean) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bundledAssets = remember(context) { RootUtils.listBundledAbkLkmAssets(context) }
    val currentKmi by produceState<String?>(initialValue = null, context, rootGranted) {
        value = withContext(Dispatchers.IO) { RootUtils.detectCurrentKmi() }
    }
    val partitionOptions by produceState(initialValue = emptyList<String>(), context, rootGranted) {
        value = withContext(Dispatchers.IO) { RootUtils.listBootPatchPartitions() }
    }
    val defaultPartition by produceState(initialValue = "boot", context, rootGranted) {
        value = withContext(Dispatchers.IO) { RootUtils.detectDefaultBootPartition() }
    }
    val supportsAnyKernelInactiveSlot by produceState(initialValue = false, context, rootGranted) {
        value = withContext(Dispatchers.IO) { RootUtils.supportsAnyKernelInactiveSlot() }
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
    var hasCustomKmiSelection by rememberSaveable { mutableStateOf(false) }
    val selectedAsset = bundledAssets.firstOrNull {
        it.variantId == selectedVariant && it.kmi == selectedKmi
    }

    var selectedBootPath by rememberSaveable { mutableStateOf("") }
    var selectedBootName by rememberSaveable { mutableStateOf("") }
    var selectedAnyKernelPath by rememberSaveable { mutableStateOf("") }
    var selectedAnyKernelName by rememberSaveable { mutableStateOf("") }
    var selectedLocalLkmPath by rememberSaveable { mutableStateOf("") }
    var selectedLocalLkmName by rememberSaveable { mutableStateOf("") }
    var selectedAnyKernelSlotTargetName by rememberSaveable {
        mutableStateOf(RootUtils.Ak3SlotTarget.CURRENT.name)
    }
    var selectedPartition by rememberSaveable { mutableStateOf(defaultPartition) }
    var hasCustomPartitionSelection by rememberSaveable { mutableStateOf(false) }
    var showAdvancedOptions by rememberSaveable { mutableStateOf(false) }
    var allowShell by rememberSaveable { mutableStateOf(false) }
    var enableAdb by rememberSaveable { mutableStateOf(false) }
    var patchedImagePath by rememberSaveable { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var success by remember { mutableStateOf<Boolean?>(null) }
    var logLines by remember { mutableStateOf(emptyList<String>()) }
    var currentAction by remember { mutableStateOf("") }

    val userlandKsudPath by produceState<String?>(initialValue = null, context) {
        value = withContext(Dispatchers.IO) { RootUtils.resolveUserlandKsudPath(context) }
    }
    val hasLocalLkm = selectedLocalLkmPath.isNotBlank()
    val activeLkmLabel = selectedLocalLkmName.takeIf { it.isNotBlank() }
        ?: selectedAsset?.let { "${it.variantLabel} \u00B7 ${it.kmi}" }
        ?: ""
    val hasLkmSource = hasLocalLkm || selectedAsset != null
    val showRootInstallModes = rootGranted
    val hasUserlandKsud = userlandKsudPath != null
    val canPatchSelectedFile = selectedBootPath.isNotBlank() &&
        hasLkmSource &&
        !running &&
        (rootGranted || hasUserlandKsud)
    val canDirectInstall = rootGranted && hasLkmSource && !running
    val canFlashAnyKernel3 = rootGranted && selectedAnyKernelPath.isNotBlank() && !running
    val canProceed = when (selectedMode) {
        LkmPatchInstallMode.SelectFile -> canPatchSelectedFile
        LkmPatchInstallMode.DirectInstall,
        LkmPatchInstallMode.OtaInstall -> canDirectInstall
        LkmPatchInstallMode.AnyKernel3 -> canFlashAnyKernel3
        null -> false
    }
    val copiedMessage = stringResource(R.string.copied)
    val actionPatchImage = stringResource(R.string.root_patch_action_patch_image)
    val actionDirectInstall = stringResource(R.string.root_patch_action_direct_install)
    val actionOtaInstall = stringResource(R.string.root_patch_action_ota_install)
    val actionFlashAnyKernel = stringResource(R.string.root_patch_action_flash_anykernel)
    val actionFlashPatchedImage = stringResource(R.string.root_patch_action_flash_patched_image)
    val selectFileDesc = stringResource(R.string.root_patch_select_file_desc)
    val anyKernelDesc = stringResource(R.string.root_patch_anykernel_desc)
    val anyKernelSlotTitle = stringResource(R.string.root_patch_ak3_slot_title)
    val anyKernelSlotDesc = stringResource(R.string.root_patch_ak3_slot_desc)
    val anyKernelCurrentSlotLabel = stringResource(R.string.root_patch_ak3_slot_current)
    val anyKernelInactiveSlotLabel = stringResource(R.string.root_patch_ak3_slot_inactive)
    val localLkmDesc = stringResource(R.string.root_patch_local_lkm_desc)
    val noLkmAvailable = stringResource(R.string.root_patch_no_lkm_available)
    val defaultPartitionLabel = stringResource(R.string.root_patch_default_label)
    val lkmFallbackLabel = stringResource(R.string.root_patch_lkm_fallback)
    val currentBuiltinLkm = selectedAsset?.let {
        stringResource(R.string.root_patch_current_builtin_lkm, it.variantLabel, it.kmi)
    }
    val localLkmSubtitle = selectedLocalLkmName.ifBlank { currentBuiltinLkm ?: localLkmDesc }
    val activeLkmLogLabel = activeLkmLabel.ifBlank { lkmFallbackLabel }
    val selectedAnyKernelSlotTarget = runCatching {
        RootUtils.Ak3SlotTarget.valueOf(selectedAnyKernelSlotTargetName)
    }.getOrDefault(RootUtils.Ak3SlotTarget.CURRENT)

    LaunchedEffect(selectedVariant, kmiOptions, currentKmi, hasCustomKmiSelection) {
        val preferredKmi = preferredLkmKmiSelection(
            currentSelection = selectedKmi,
            options = kmiOptions,
            recommendedKmi = currentKmi,
            hasCustomSelection = hasCustomKmiSelection
        )
        if (selectedKmi != preferredKmi) {
            selectedKmi = preferredKmi
        }
    }

    LaunchedEffect(defaultPartition, partitionOptions, hasCustomPartitionSelection) {
        if (partitionOptions.isEmpty()) return@LaunchedEffect
        if (!hasCustomPartitionSelection) {
            selectedPartition = defaultPartition.takeIf { it in partitionOptions }
                ?: partitionOptions.first()
        } else if (selectedPartition !in partitionOptions) {
            selectedPartition = defaultPartition.takeIf { it in partitionOptions }
                ?: partitionOptions.first()
        }
    }

    LaunchedEffect(running) {
        onBackEnabledChange(!running)
    }

    LaunchedEffect(showRootInstallModes) {
        if (!showRootInstallModes && selectedMode != null && selectedMode != LkmPatchInstallMode.SelectFile) {
            selectedMode = null
            patchedImagePath = ""
            success = null
            currentAction = ""
            logLines = emptyList()
        }
    }

    LaunchedEffect(supportsAnyKernelInactiveSlot) {
        if (!supportsAnyKernelInactiveSlot) {
            selectedAnyKernelSlotTargetName = RootUtils.Ak3SlotTarget.CURRENT.name
        }
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
        onFeedback(copiedMessage, false)
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
            logLines = listOf(context.getString(R.string.root_patch_selected_file, staged.second))
        }
    }

    val anyKernelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        if (!isZipFile(context, uri)) {
            onFeedback(context.getString(R.string.root_patch_only_anykernel_zip), false)
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
            logLines = listOf(context.getString(R.string.root_patch_selected_file, staged.second))
        }
    }

    val localLkmPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        if (!isKoFile(context, uri)) {
            onFeedback(context.getString(R.string.root_patch_only_ko_lkm), false)
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val staged = withContext(Dispatchers.IO) { stageContentUri(context, uri, "abk-local-lkm", "kernelsu.ko") }
            selectedLocalLkmPath = staged.first.absolutePath
            selectedLocalLkmName = staged.second
            patchedImagePath = ""
            success = null
            logLines = listOf(context.getString(R.string.root_patch_selected_file, staged.second))
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
            logLines = logLines + context.getString(R.string.root_patch_output_image, patchedImagePath)
        }
    }

    fun startPatchSelectedFile() {
        if (!canPatchSelectedFile) return
        val modulePath = selectedLocalLkmPath.takeIf { it.isNotBlank() }
        beginOperation(
            action = actionPatchImage,
            lines = listOf(
                "${'$'} ksud boot-patch --boot $selectedBootName --module $activeLkmLogLabel",
                context.getString(R.string.root_patch_log_partition, selectedPartition)
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
        val action = if (ota) actionOtaInstall else actionDirectInstall
        beginOperation(
            action = action,
            lines = listOf(
                "${'$'} ksud boot-patch --flash${if (ota) " --ota" else ""} --partition $selectedPartition",
                context.getString(R.string.root_patch_log_module, activeLkmLogLabel)
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
            action = actionFlashAnyKernel,
            lines = listOf(
                "${'$'} flash AnyKernel3",
                context.getString(R.string.root_patch_log_file, selectedAnyKernelPath),
                context.getString(
                    R.string.root_patch_log_slot,
                    when (selectedAnyKernelSlotTarget) {
                        RootUtils.Ak3SlotTarget.INACTIVE -> anyKernelInactiveSlotLabel
                        RootUtils.Ak3SlotTarget.CURRENT -> anyKernelCurrentSlotLabel
                    }
                )
            )
        )
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                RootUtils.flashAnyKernel3(
                    context,
                    selectedAnyKernelPath,
                    targetSlot = selectedAnyKernelSlotTarget,
                    onOutput = ::appendLog
                )
            }
            running = false
            success = result.success
            if (result.output.isNotEmpty()) logLines = result.output
        }
    }

    fun startFlashPatchedImage() {
        if (patchedImagePath.isBlank() || running) return
        beginOperation(
            action = actionFlashPatchedImage,
            lines = listOf(
                "${'$'} dd $selectedPartition <- ${File(patchedImagePath).name}",
                context.getString(R.string.root_patch_log_file, patchedImagePath)
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

    val surfaceColor = MiuixTheme.colorScheme.surface
    val backdrop = rememberBlurBackdrop(enableBlur = true, surfaceColor)
    val barColor = if (backdrop != null) Color.Transparent else surfaceColor

    Box(modifier = Modifier.fillMaxSize()) {
        AppPageBackground(
            backgroundUri = backgroundUri,
            backgroundImageEnabled = backgroundImageEnabled
        )
        Scaffold(
            topBar = {
                BlurredBar(backdrop, surfaceColor) {
                    TopAppBar(
                        color = barColor,
                        title = stringResource(R.string.root_patch_title),
                        navigationIcon = {
                            IconButton(onClick = onBack, enabled = !running) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.back)
                                )
                            }
                        }
                    )
                }
            }
        ) { padding ->
            Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
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
                            title = stringResource(R.string.root_patch_select_file),
                            subtitle = selectedBootName.ifBlank { selectFileDesc },
                            selected = selectedMode == LkmPatchInstallMode.SelectFile,
                            enabled = !running,
                            onClick = {
                                selectedMode = LkmPatchInstallMode.SelectFile
                                bootPicker.launch(arrayOf("application/octet-stream", "image/*", "*/*"))
                            }
                        )
                        if (showRootInstallModes) {
                            PatchModeRow(
                                title = stringResource(R.string.root_patch_direct_install),
                                subtitle = stringResource(R.string.root_patch_direct_install_desc),
                                selected = selectedMode == LkmPatchInstallMode.DirectInstall,
                                enabled = !running,
                                onClick = {
                                    selectedMode = LkmPatchInstallMode.DirectInstall
                                    patchedImagePath = ""
                                    success = null
                                    currentAction = ""
                                    logLines = emptyList()
                                }
                            )
                            PatchModeRow(
                                title = stringResource(R.string.root_patch_ota_install),
                                subtitle = stringResource(R.string.root_patch_ota_install_desc),
                                selected = selectedMode == LkmPatchInstallMode.OtaInstall,
                                enabled = !running,
                                onClick = {
                                    selectedMode = LkmPatchInstallMode.OtaInstall
                                    patchedImagePath = ""
                                    success = null
                                    currentAction = ""
                                    logLines = emptyList()
                                }
                            )
                            PatchModeRow(
                                title = stringResource(R.string.root_patch_anykernel),
                                subtitle = selectedAnyKernelName.ifBlank { anyKernelDesc },
                                selected = selectedMode == LkmPatchInstallMode.AnyKernel3,
                                enabled = !running,
                                onClick = {
                                    selectedMode = LkmPatchInstallMode.AnyKernel3
                                    patchedImagePath = ""
                                    success = null
                                    currentAction = ""
                                    logLines = emptyList()
                                    anyKernelPicker.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                                }
                            )
                            AnimatedVisibility(
                                visible = selectedMode == LkmPatchInstallMode.AnyKernel3 && supportsAnyKernelInactiveSlot,
                                enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
                            ) {
                                val slotItems = listOf(anyKernelCurrentSlotLabel, anyKernelInactiveSlotLabel)
                                val slotIndex = if (selectedAnyKernelSlotTarget == RootUtils.Ak3SlotTarget.CURRENT) 0 else 1
                                OverlayDropdownPreference(
                                    title = anyKernelSlotTitle,
                                    startAction = {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = null,
                                            modifier = Modifier.padding(end = 6.dp),
                                            tint = MiuixTheme.colorScheme.onBackground
                                        )
                                    },
                                    items = slotItems,
                                    selectedIndex = slotIndex,
                                    renderInRootScaffold = true,
                                    onSelectedIndexChange = { index ->
                                        selectedAnyKernelSlotTargetName = if (index == 0) {
                                            RootUtils.Ak3SlotTarget.CURRENT.name
                                        } else {
                                            RootUtils.Ak3SlotTarget.INACTIVE.name
                                        }
                                    }
                                )
                            }
                        }
                    }

                    PatchGroupCard {
                        val partitionItems = partitionOptions.map {
                            partitionMenuLabel(it, defaultPartition, defaultPartitionLabel, context)
                        }
                        val partitionIndex = partitionOptions.indexOf(selectedPartition).coerceAtLeast(0)
                        OverlayDropdownPreference(
                            title = stringResource(R.string.root_patch_select_partition),
                            startAction = {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 6.dp),
                                    tint = MiuixTheme.colorScheme.onBackground
                                )
                            },
                            items = partitionItems,
                            selectedIndex = partitionIndex,
                            renderInRootScaffold = true,
                            onSelectedIndexChange = { index ->
                                hasCustomPartitionSelection = true
                                selectedPartition = partitionOptions[index]
                            }
                        )
                    }

                    PatchGroupCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !running) {
                                    localLkmPicker.launch(arrayOf("application/octet-stream", "*/*"))
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onBackground
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.root_patch_use_local_lkm),
                                    style = MiuixTheme.textStyles.subtitle
                                )
                                Text(
                                    text = localLkmSubtitle,
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (selectedLocalLkmPath.isNotBlank()) {
                                IconButton(
                                    onClick = {
                                        selectedLocalLkmPath = ""
                                        selectedLocalLkmName = ""
                                    },
                                    enabled = !running
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = stringResource(R.string.root_patch_clear_local_lkm)
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = stringResource(R.string.root_patch_select_local_lkm)
                                )
                            }
                        }

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
                                        PatchChip(
                                            label = variant.label,
                                            selected = selectedVariant == variant.id,
                                            enabled = !running,
                                            onClick = {
                                                selectedVariant = variant.id
                                                selectedKmi = ""
                                                hasCustomKmiSelection = false
                                                patchedImagePath = ""
                                                success = null
                                                currentAction = ""
                                                logLines = emptyList()
                                            }
                                        )
                                    }
                                }

                                val kmiItems = kmiOptions.ifEmpty { listOf(noLkmAvailable) }
                                val kmiIndex = kmiItems.indexOf(selectedKmi).coerceAtLeast(0)
                                val kmiDisplayItems = kmiItems.map {
                                    if (it == currentKmi?.takeIf { it in kmiOptions }) {
                                        "$it ${stringResource(R.string.build_recommended_suffix)}"
                                    } else {
                                        it
                                    }
                                }
                                OverlayDropdownPreference(
                                    title = "KMI",
                                    items = kmiDisplayItems,
                                    selectedIndex = kmiIndex,
                                    renderInRootScaffold = true,
                                    onSelectedIndexChange = { index ->
                                        val selected = kmiItems[index]
                                        if (selected in kmiOptions) {
                                            selectedKmi = selected
                                            hasCustomKmiSelection = true
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
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !running) { showAdvancedOptions = !showAdvancedOptions }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onBackground
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.root_patch_advanced_options),
                                    style = MiuixTheme.textStyles.subtitle
                                )
                            }
                            Icon(
                                imageVector = if (showAdvancedOptions) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = stringResource(R.string.root_patch_expand_advanced_options)
                            )
                        }
                        AnimatedVisibility(
                            visible = showAdvancedOptions,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column {
                                Text(
                                    text = stringResource(R.string.root_patch_advanced_desc),
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                )
                                SwitchPreference(
                                    title = stringResource(R.string.root_patch_allow_shell_root),
                                    summary = stringResource(R.string.root_patch_allow_shell_root_desc),
                                    checked = allowShell,
                                    onCheckedChange = { allowShell = it },
                                    enabled = !running
                                )
                                SwitchPreference(
                                    title = stringResource(R.string.root_patch_enable_adb_debug),
                                    summary = stringResource(R.string.root_patch_enable_adb_debug_desc),
                                    checked = enableAdb,
                                    onCheckedChange = { enableAdb = it },
                                    enabled = !running
                                )
                            }
                        }
                    }

                    if (!hasLkmSource && selectedMode != LkmPatchInstallMode.AnyKernel3) {
                        InlineWarning(stringResource(R.string.root_patch_warn_no_lkm))
                    }
                    if (selectedMode == LkmPatchInstallMode.SelectFile && !rootGranted && !hasUserlandKsud) {
                        InlineWarning(stringResource(R.string.root_patch_warn_no_ksud))
                    }

                    Button(
                        onClick = ::startNext,
                        enabled = canProceed,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            color = MiuixTheme.colorScheme.primary,
                            contentColor = Color.White
                        )
                    ) {
                        if (running) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(17.dp),
                                progress = null,
                                colors = ProgressIndicatorDefaults.progressIndicatorColors(
                                    foregroundColor = Color.White,
                                    backgroundColor = Color.White.copy(alpha = 0.3f)
                                ),
                                strokeWidth = 2.dp,
                                size = 17.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.root_patch_processing))
                        } else {
                            Text(stringResource(R.string.root_patch_next))
                        }
                    }

                    if (patchedImagePath.isNotBlank()) {
                        PatchedImageCard(
                            path = patchedImagePath,
                            canFlash = selectedMode == LkmPatchInstallMode.SelectFile && rootGranted && !running,
                            onCopy = { copyText(context.getString(R.string.root_patch_clip_label_patched_boot), patchedImagePath) },
                            onFlash = ::startFlashPatchedImage
                        )
                    }

                    if (running || success != null || logLines.isNotEmpty()) {
                        PatchLogCard(
                            running = running,
                            success = success,
                            action = currentAction,
                            lines = logLines,
                            canReboot = success == true && currentAction != actionPatchImage,
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
}

@Composable
private fun PatchGroupCard(content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        PatchRadioIndicator(selected = selected, enabled = enabled)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MiuixTheme.textStyles.subtitle
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun PatchRadioIndicator(selected: Boolean, enabled: Boolean) {
    val color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary
    Box(
        modifier = Modifier
            .size(20.dp)
            .border(
                width = 2.dp,
                color = if (enabled) color else color.copy(alpha = 0.38f),
                shape = CircleShape
            )
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        color = if (enabled) color else color.copy(alpha = 0.38f),
                        shape = CircleShape
                    )
            )
        }
    }
}


@Composable
private fun InlineWarning(text: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.error,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(18.dp)
        )
        Text(
            text = text,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.error
        )
    }
}

@Composable
private fun PatchedImageCard(
    path: String,
    canFlash: Boolean,
    onCopy: () -> Unit,
    onFlash: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(20.dp))
                Text(
                    text = stringResource(R.string.root_patch_result),
                    style = MiuixTheme.textStyles.subtitle,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onCopy) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = stringResource(R.string.root_patch_copy_path)
                    )
                }
                if (canFlash) {
                    Button(
                        onClick = onFlash,
                        colors = ButtonDefaults.buttonColors(
                            color = MiuixTheme.colorScheme.primary,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Default.FlashOn, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.root_patch_flash))
                    }
                }
            }
            Text(
                text = path,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
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
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val icon = when {
                    running -> Icons.Default.Terminal
                    success == true -> Icons.Default.CheckCircle
                    success == false -> Icons.Default.Error
                    else -> Icons.Default.Info
                }
                Icon(icon, null, modifier = Modifier.size(20.dp))
                Text(
                    text = action.ifBlank { stringResource(R.string.root_patch_logs) },
                    style = MiuixTheme.textStyles.subtitle,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.weight(1f))
                if (canReboot) {
                    Button(
                        onClick = onReboot,
                        colors = ButtonDefaults.buttonColors(
                            color = MiuixTheme.colorScheme.primary,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Default.RestartAlt, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.root_patch_reboot))
                    }
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 84.dp, max = 280.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                val displayLines = lines.ifEmpty { listOf(stringResource(R.string.root_patch_waiting_operation)) }
                displayLines.forEach { line ->
                    Text(
                        text = line,
                        style = MiuixTheme.textStyles.body2,
                        fontFamily = FontFamily.Monospace,
                        color = if (line.firstOrNull() == '$') {
                            MiuixTheme.colorScheme.primary
                        } else {
                            MiuixTheme.colorScheme.onSurface
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PatchChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (selected) MiuixTheme.colorScheme.primary else Color.Transparent
    val contentColor = if (selected) Color.White else MiuixTheme.colorScheme.onSurfaceVariantSummary
    val borderColor = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.3f)

    Box(
        modifier = Modifier
            .clickable(enabled = enabled, onClick = onClick)
            .background(color = backgroundColor, shape = RoundedCornerShape(8.dp))
            .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.body2,
            color = if (enabled) contentColor else contentColor.copy(alpha = 0.38f)
        )
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
    } ?: error(context.getString(R.string.root_patch_read_selected_file_failed))
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

private fun partitionLabel(
    partition: String,
    defaultPartition: String,
    defaultLabel: String,
    context: Context
): String =
    if (partition == defaultPartition) {
        context.getString(R.string.root_patch_partition_default_multiline, partition, defaultLabel)
    } else {
        partition
    }

private fun partitionMenuLabel(
    partition: String,
    defaultPartition: String,
    defaultLabel: String,
    context: Context
): String =
    if (partition == defaultPartition) {
        context.getString(R.string.root_patch_partition_default_inline, partition, defaultLabel)
    } else {
        partition
    }

private fun String.defaultLkmVariantId(): String {
    val lower = lowercase()
    return when {
        "resukisu" in lower -> "resukisu"
        "sukisu" in lower -> "sukisu"
        else -> "kernelsu"
    }
}
