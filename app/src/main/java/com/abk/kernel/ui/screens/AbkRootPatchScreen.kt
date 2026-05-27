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
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.abk.kernel.R
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
    hasNativeManagerPermission: Boolean,
    runtimeVariant: String,
    backgroundUri: String?,
    backgroundImageEnabled: Boolean,
    onBack: () -> Unit,
    onBackEnabledChange: (Boolean) -> Unit = {}
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
    var showAnyKernelSlotMenu by remember { mutableStateOf(false) }
    var selectedPartition by rememberSaveable { mutableStateOf(defaultPartition) }
    var hasCustomPartitionSelection by rememberSaveable { mutableStateOf(false) }
    var showPartitionMenu by remember { mutableStateOf(false) }
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
        ?: selectedAsset?.let { "${it.variantLabel} · ${it.kmi}" }
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
        Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
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
            Toast.makeText(context, context.getString(R.string.root_patch_only_anykernel_zip), Toast.LENGTH_SHORT).show()
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
            Toast.makeText(context, context.getString(R.string.root_patch_only_ko_lkm), Toast.LENGTH_SHORT).show()
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

    Box(modifier = Modifier.fillMaxSize()) {
        LkmPatchPageBackground(
            backgroundUri = backgroundUri,
            backgroundImageEnabled = backgroundImageEnabled
        )
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                ExpressiveTopBar(
                    title = stringResource(R.string.root_patch_title),
                    navigationIcon = {
                        IconButton(onClick = onBack, enabled = !running) {
                            Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
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
                    PatchDivider()
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
                    PatchDivider()
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
                    PatchDivider()
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
                }
            }

            AnimatedVisibility(
                visible = selectedMode == LkmPatchInstallMode.AnyKernel3 && supportsAnyKernelInactiveSlot,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                PatchGroupCard {
                    androidx.compose.foundation.layout.Box {
                        ExpressiveListItem(
                            title = anyKernelSlotTitle,
                            subtitle = anyKernelSlotDesc,
                            leadingIcon = Icons.Default.Edit,
                            enabled = !running,
                            trailingContent = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = when (selectedAnyKernelSlotTarget) {
                                            RootUtils.Ak3SlotTarget.INACTIVE -> anyKernelInactiveSlotLabel
                                            RootUtils.Ak3SlotTarget.CURRENT -> anyKernelCurrentSlotLabel
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        textAlign = TextAlign.End,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Icon(
                                        Icons.Default.ChevronRight,
                                        contentDescription = anyKernelSlotTitle
                                    )
                                }
                            },
                            onClick = { showAnyKernelSlotMenu = true }
                        )
                        DropdownMenu(
                            expanded = showAnyKernelSlotMenu,
                            onDismissRequest = { showAnyKernelSlotMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(anyKernelCurrentSlotLabel) },
                                onClick = {
                                    selectedAnyKernelSlotTargetName = RootUtils.Ak3SlotTarget.CURRENT.name
                                    showAnyKernelSlotMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(anyKernelInactiveSlotLabel) },
                                onClick = {
                                    selectedAnyKernelSlotTargetName = RootUtils.Ak3SlotTarget.INACTIVE.name
                                    showAnyKernelSlotMenu = false
                                }
                            )
                        }
                    }
                }
            }

            PatchGroupCard {
                androidx.compose.foundation.layout.Box {
                    ExpressiveListItem(
                        title = stringResource(R.string.root_patch_select_partition),
                        subtitle = stringResource(R.string.root_patch_partition_desc),
                        leadingIcon = Icons.Default.Edit,
                        enabled = !running && selectedMode != LkmPatchInstallMode.AnyKernel3,
                        trailingContent = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = partitionLabel(selectedPartition, defaultPartition, defaultPartitionLabel, context),
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.End,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Icon(Icons.Default.ChevronRight, contentDescription = stringResource(R.string.root_patch_select_partition))
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
                                text = { Text(partitionMenuLabel(partition, defaultPartition, defaultPartitionLabel, context)) },
                                onClick = {
                                    hasCustomPartitionSelection = true
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
                    title = stringResource(R.string.root_patch_use_local_lkm),
                    subtitle = localLkmSubtitle,
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
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.root_patch_clear_local_lkm))
                            }
                        } else {
                            Icon(Icons.Default.ChevronRight, contentDescription = stringResource(R.string.root_patch_select_local_lkm))
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
                                        selectedKmi = ""
                                        hasCustomKmiSelection = false
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
                            value = selectedKmi.ifBlank { noLkmAvailable },
                            options = kmiOptions.ifEmpty { listOf(noLkmAvailable) },
                            recommendedValue = currentKmi?.takeIf { it in kmiOptions },
                            onSelect = {
                                if (it in kmiOptions) {
                                    selectedKmi = it
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
                ExpressiveListItem(
                    title = stringResource(R.string.root_patch_advanced_options),
                    leadingIcon = Icons.Default.Tune,
                    trailingContent = {
                        Icon(
                            if (showAdvancedOptions) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = stringResource(R.string.root_patch_expand_advanced_options)
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
                        Text(
                            text = stringResource(R.string.root_patch_advanced_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                        PatchDivider()
                        PatchCheckboxItem(
                            title = stringResource(R.string.root_patch_allow_shell_root),
                            subtitle = stringResource(R.string.root_patch_allow_shell_root_desc),
                            checked = allowShell,
                            enabled = !running,
                            onCheckedChange = { allowShell = it }
                        )
                        PatchDivider()
                        PatchCheckboxItem(
                            title = stringResource(R.string.root_patch_enable_adb_debug),
                            subtitle = stringResource(R.string.root_patch_enable_adb_debug_desc),
                            checked = enableAdb,
                            enabled = !running,
                            onCheckedChange = { enableAdb = it }
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
                modifier = Modifier.fillMaxWidth()
            ) {
                if (running) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(17.dp),
                        strokeWidth = 2.dp
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
                    text = stringResource(R.string.root_patch_result),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onCopy) {
                    Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.root_patch_copy_path))
                }
                if (canFlash) {
                    AssistChip(
                        onClick = onFlash,
                        label = { Text(stringResource(R.string.root_patch_flash)) },
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
                    action.ifBlank { stringResource(R.string.root_patch_logs) },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.weight(1f))
                if (canReboot) {
                    AssistChip(
                        onClick = onReboot,
                        label = { Text(stringResource(R.string.root_patch_reboot)) },
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
                val displayLines = lines.ifEmpty { listOf(stringResource(R.string.root_patch_waiting_operation)) }
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

internal fun preferredLkmKmiSelection(
    currentSelection: String,
    options: List<String>,
    recommendedKmi: String?,
    hasCustomSelection: Boolean
): String {
    if (options.isEmpty()) return ""

    val current = currentSelection.takeIf { it in options }
    val recommended = recommendedKmi?.takeIf { it in options }
    return when {
        hasCustomSelection && current != null -> current
        recommended != null -> recommended
        current != null -> current
        else -> options.first()
    }
}

private fun String.defaultLkmVariantId(): String {
    val lower = lowercase()
    return when {
        "resukisu" in lower -> "resukisu"
        "sukisu" in lower -> "sukisu"
        else -> "kernelsu"
    }
}
