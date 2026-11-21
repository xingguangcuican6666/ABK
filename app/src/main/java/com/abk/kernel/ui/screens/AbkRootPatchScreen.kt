@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import com.abk.kernel.R
import com.abk.kernel.data.model.ArtifactType
import com.abk.kernel.data.model.KSU_VARIANT_BAKASU
import com.abk.kernel.data.model.KSU_VARIANT_SUKISU
import com.abk.kernel.data.model.KernelSupport
import com.abk.kernel.data.repository.PreferencesRepository
import com.abk.kernel.ui.blur.BlurConfig
import com.abk.kernel.ui.blur.BlurScreenScaffold
import com.abk.kernel.ui.components.AbkScreenHorizontalPadding
import com.abk.kernel.ui.components.AppPageBackground
import com.abk.kernel.ui.components.ExpressiveTopBar
import com.abk.kernel.ui.screens.flash.FlashAction
import com.abk.kernel.utils.ArtifactVerification
import com.abk.kernel.utils.DownloadUtils
import com.abk.kernel.utils.ForkSigningManager
import com.abk.kernel.utils.RootUtils
import com.abk.kernel.utils.SignedBundleManifest
import io.github.xingguangcuican6666.abk.ui.components.AbkButtonStyle
import io.github.xingguangcuican6666.abk.ui.components.AbkCluster
import io.github.xingguangcuican6666.abk.ui.components.AbkHeading
import io.github.xingguangcuican6666.abk.ui.components.AbkIconButton
import io.github.xingguangcuican6666.abk.ui.components.AbkOption
import io.github.xingguangcuican6666.abk.ui.components.AbkSelect
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcon
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcons
import io.github.xingguangcuican6666.abk.ui.theme.AbkTheme
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class LkmPatchInstallMode {
    SelectFile,
    DirectInstall,
    OtaInstall,
    AnyKernel3,
}

@Composable
fun AbkRootPatchScreen(
    rootGranted: Boolean,
    hasNativeManagerPermission: Boolean,
    runtimeVariant: String,
    backgroundUri: String?,
    backgroundImageEnabled: Boolean,
    blurEnabled: Boolean,
    blurBackgroundExpEnabled: Boolean,
    onBack: () -> Unit,
    onBackEnabledChange: (Boolean) -> Unit = {},
    downloadDirectory: String? = null
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
    var selectedAnyKernelManifest by remember { mutableStateOf<SignedBundleManifest?>(null) }
    var showAnyKernelManifestNotice by remember { mutableStateOf(false) }
    var showAnyKernelFlashConfirm by remember { mutableStateOf(false) }
    val sessionManifestNoticeHashes = remember { mutableSetOf<String>() }
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
            val prepared = runCatching {
                withContext(Dispatchers.IO) {
                    val staged = stageContentUri(context, uri, "abk-anykernel3", "AnyKernel3.zip")
                    prepareLocalAnyKernelSelection(context, staged.first, staged.second)
                }
            }.getOrElse { error ->
                Toast.makeText(
                    context,
                    context.getString(
                        R.string.root_patch_bundle_verification_failed,
                        error.message ?: error::class.java.simpleName
                    ),
                    Toast.LENGTH_LONG
                ).show()
                return@launch
            }
            selectedAnyKernelPath = prepared.payload.absolutePath
            selectedAnyKernelName = prepared.displayName
            selectedAnyKernelManifest = prepared.manifest
            selectedMode = LkmPatchInstallMode.AnyKernel3
            patchedImagePath = ""
            success = null
            logLines = listOf(context.getString(R.string.root_patch_selected_file, prepared.displayName))
            val bundleHash = prepared.bundleFile?.let { bundle ->
                withContext(Dispatchers.IO) { DownloadUtils.fileSha256Hex(bundle) }
            }
            if (prepared.manifest?.clientNotice != null &&
                (bundleHash == null || sessionManifestNoticeHashes.add(bundleHash))
            ) {
                showAnyKernelManifestNotice = true
            }
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
                    onOutput = ::appendLog,
                    downloadDirectory = downloadDirectory
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
                    onOutput = ::appendLog,
                    downloadDirectory = downloadDirectory
                )
            }
            finishPatchResult(result)
        }
    }

    fun performAnyKernel3Flash() {
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

    fun startAnyKernel3Flash() {
        if (!canFlashAnyKernel3) return
        if (selectedAnyKernelManifest?.clientNotice != null) {
            showAnyKernelFlashConfirm = true
            return
        }
        performAnyKernel3Flash()
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

    selectedAnyKernelManifest
        ?.takeIf { it.clientNotice != null }
        ?.let { manifest ->
            if (showAnyKernelManifestNotice || showAnyKernelFlashConfirm) {
                val dismissNotice = {
                    showAnyKernelManifestNotice = false
                    showAnyKernelFlashConfirm = false
                }
                RootPatchManifestDialog(
                    manifest = manifest,
                    confirmFlash = showAnyKernelFlashConfirm,
                    onDismiss = dismissNotice,
                    onConfirm = {
                        val confirmFlash = showAnyKernelFlashConfirm
                        dismissNotice()
                        if (confirmFlash) performAnyKernel3Flash()
                    },
                )
            }
        }

    Box(modifier = Modifier.fillMaxSize()) {
        LkmPatchPageBackground(
            backgroundUri = backgroundUri,
            backgroundImageEnabled = backgroundImageEnabled,
        )
        BlurScreenScaffold(
            blurConfig =
                BlurConfig(
                    blurEnabled = blurEnabled,
                    backgroundExpEnabled = blurBackgroundExpEnabled,
                    backgroundUri = backgroundUri,
                    backgroundImageEnabled = backgroundImageEnabled,
                ),
            containerColor = Color.Transparent,
            topBar = {
                ExpressiveTopBar(
                    title = stringResource(R.string.root_patch_title),
                    navigationIcon = {
                        AbkIconButton(
                            AbkIcons.Back,
                            stringResource(R.string.back),
                            onBack,
                            enabled = !running,
                        )
                    },
                    enableBlur = blurEnabled,
                )
            },
        ) { topBarHeight ->
            Column(
                modifier =
                    Modifier.fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = AbkScreenHorizontalPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Spacer(Modifier.height(topBarHeight + 16.dp))
                RootPatchGroupCard {
                    RootPatchModeOptions(
                        selected = selectedMode,
                        rootGranted = showRootInstallModes,
                        running = running,
                        selectedBootName = selectedBootName,
                        selectedAnyKernelName = selectedAnyKernelName,
                        onSelect = { mode ->
                            selectedMode = mode
                            when (mode) {
                                LkmPatchInstallMode.SelectFile ->
                                    bootPicker.launch(
                                        arrayOf("application/octet-stream", "image/*", "*/*")
                                    )
                                else -> {
                                    patchedImagePath = ""
                                    success = null
                                    currentAction = ""
                                    logLines = emptyList()
                                    if (mode == LkmPatchInstallMode.AnyKernel3) {
                                        anyKernelPicker.launch(
                                            arrayOf(
                                                "application/zip",
                                                "application/octet-stream",
                                                "*/*",
                                            )
                                        )
                                    }
                                }
                            }
                        },
                    )
                }

                AnimatedVisibility(
                    visible =
                        selectedMode == LkmPatchInstallMode.AnyKernel3 &&
                            supportsAnyKernelInactiveSlot,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    RootPatchGroupCard {
                        AbkSelect(
                            options =
                                listOf(
                                    AbkOption(
                                        RootUtils.Ak3SlotTarget.CURRENT.name,
                                        anyKernelCurrentSlotLabel,
                                    ),
                                    AbkOption(
                                        RootUtils.Ak3SlotTarget.INACTIVE.name,
                                        anyKernelInactiveSlotLabel,
                                    ),
                                ),
                            selectedId = selectedAnyKernelSlotTarget.name,
                            onSelect = { selectedAnyKernelSlotTargetName = it },
                            label = anyKernelSlotTitle,
                            hint = anyKernelSlotDesc,
                            enabled = !running,
                        )
                    }
                }

                RootPatchGroupCard {
                    AbkSelect(
                        options =
                            partitionOptions.distinct().map {
                                AbkOption(
                                    it,
                                    partitionMenuLabel(
                                        it,
                                        defaultPartition,
                                        defaultPartitionLabel,
                                        context,
                                    ),
                                )
                            },
                        selectedId = selectedPartition,
                        onSelect = {
                            hasCustomPartitionSelection = true
                            selectedPartition = it
                        },
                        label = stringResource(R.string.root_patch_select_partition),
                        placeholder =
                            partitionMenuLabel(
                                selectedPartition,
                                defaultPartition,
                                defaultPartitionLabel,
                                context,
                            ),
                        hint = stringResource(R.string.root_patch_partition_desc),
                        enabled =
                            !running &&
                                selectedMode != LkmPatchInstallMode.AnyKernel3 &&
                                partitionOptions.isNotEmpty(),
                    )
                }

                RootPatchGroupCard {
                    AbkHeading(stringResource(R.string.root_patch_use_local_lkm))
                    Text(
                        localLkmSubtitle,
                        color = AbkTheme.colors.inkMuted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    AbkCluster(Modifier.fillMaxWidth()) {
                        FlashAction(
                            label = stringResource(R.string.root_patch_select_local_lkm),
                            icon = AbkIcons.Upload,
                            enabled = !running,
                            onClick = {
                                localLkmPicker.launch(arrayOf("application/octet-stream", "*/*"))
                            },
                        )
                        if (hasLocalLkm) {
                            FlashAction(
                                label = stringResource(R.string.root_patch_clear_local_lkm),
                                icon = AbkIcons.Close,
                                enabled = !running,
                                onClick = {
                                    selectedLocalLkmPath = ""
                                    selectedLocalLkmName = ""
                                },
                            )
                        }
                    }
                    AnimatedVisibility(
                        visible = selectedLocalLkmPath.isBlank(),
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            AbkSelect(
                                options =
                                    RootUtils.ABK_LKM_VARIANTS.map { AbkOption(it.id, it.label) },
                                selectedId = selectedVariant,
                                label = stringResource(R.string.build_kernelsu_variant),
                                placeholder = selectedVariant,
                                enabled = !running,
                                onSelect = { variant ->
                                    if (variant != selectedVariant) {
                                        selectedVariant = variant
                                        selectedKmi = ""
                                        hasCustomKmiSelection = false
                                        patchedImagePath = ""
                                        success = null
                                        currentAction = ""
                                        logLines = emptyList()
                                    }
                                },
                            )
                            AbkSelect(
                                options =
                                    kmiOptions.map {
                                        AbkOption(
                                            it,
                                            it +
                                                if (it == currentKmi)
                                                    stringResource(
                                                        R.string.build_recommended_suffix
                                                    )
                                                else "",
                                        )
                                    },
                                selectedId = selectedKmi,
                                label = "KMI",
                                placeholder = selectedKmi.ifBlank { noLkmAvailable },
                                enabled = !running && kmiOptions.isNotEmpty(),
                                onSelect = {
                                    if (it in kmiOptions) {
                                        selectedKmi = it
                                        hasCustomKmiSelection = true
                                        patchedImagePath = ""
                                        success = null
                                        currentAction = ""
                                        logLines = emptyList()
                                    }
                                },
                            )
                        }
                    }
                }

                RootPatchGroupCard {
                    RootPatchAdvancedOptions(
                        expanded = showAdvancedOptions,
                        running = running,
                        allowShell = allowShell,
                        enableAdb = enableAdb,
                        onExpandedChange = { showAdvancedOptions = it },
                        onAllowShellChange = { allowShell = it },
                        onEnableAdbChange = { enableAdb = it },
                    )
                }

                if (!hasLkmSource && selectedMode != LkmPatchInstallMode.AnyKernel3) {
                    InlineWarning(stringResource(R.string.root_patch_warn_no_lkm))
                }
                if (
                    selectedMode == LkmPatchInstallMode.SelectFile &&
                        !rootGranted &&
                        !hasUserlandKsud
                ) {
                    InlineWarning(stringResource(R.string.root_patch_warn_no_ksud))
                }

                FlashAction(
                    label =
                        stringResource(
                            if (running) R.string.root_patch_processing
                            else R.string.root_patch_next
                        ),
                    icon = AbkIcons.Forward,
                    onClick = ::startNext,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = canProceed,
                    loading = running,
                    style = AbkButtonStyle.Primary,
                )

                if (patchedImagePath.isNotBlank()) {
                    RootPatchedImageCard(
                        path = patchedImagePath,
                        canFlash =
                            selectedMode == LkmPatchInstallMode.SelectFile &&
                                rootGranted &&
                                !running,
                        onCopy = {
                            copyText(
                                context.getString(R.string.root_patch_clip_label_patched_boot),
                                patchedImagePath,
                            )
                        },
                        onFlash = ::startFlashPatchedImage,
                    )
                }

                if (running || success != null || logLines.isNotEmpty()) {
                    RootPatchLogCard(
                        running = running,
                        success = success,
                        action = currentAction,
                        lines = logLines,
                        canReboot = success == true && currentAction != actionPatchImage,
                        onReboot = {
                            if (!running) scope.launch(Dispatchers.IO) { RootUtils.reboot() }
                        },
                    )
                }

                Spacer(Modifier.height(80.dp))
            }
        }
    }
}

@Composable
private fun LkmPatchPageBackground(backgroundUri: String?, backgroundImageEnabled: Boolean) {
    AppPageBackground(
        backgroundUri = backgroundUri,
        backgroundImageEnabled = backgroundImageEnabled,
    )
}

@Composable
private fun InlineWarning(text: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AbkIcon(AbkIcons.Warning, null, tint = AbkTheme.colors.warning.color, size = 18.dp)
        Text(
            text,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = AbkTheme.colors.warning.color,
        )
    }
}

private data class LocalAnyKernelSelection(
    val payload: File,
    val displayName: String,
    val manifest: SignedBundleManifest? = null,
    val bundleFile: File? = null
)

private fun prepareLocalAnyKernelSelection(
    context: Context,
    stagedFile: File,
    displayName: String
): LocalAnyKernelSelection {
    val manifest = ArtifactVerification.readBundleManifest(stagedFile)
    if (manifest == null && !stagedFile.name.endsWith(".bundle.zip", ignoreCase = true)) {
        return LocalAnyKernelSelection(stagedFile, displayName)
    }
    if (manifest == null) {
        error("Missing signed bundle manifest")
    }
    val storedKey = PreferencesRepository(context).readForkArtifactSigningPublicKeyBlocking()
    val publicKeyPem = storedKey?.let(ForkSigningManager::publicKeyPemFromStoredValue)
    val verification = ArtifactVerification.verifyBundleFile(
        bundleFile = stagedFile,
        expectedType = ArtifactType.ANYKERNEL3,
        publicKeyPem = publicKeyPem
    )
    if (!verification.success) {
        error(verification.message)
    }
    val payloadName = verification.manifest.payloadName
    val payloadFile = File(
        stagedFile.parentFile ?: error("Selected bundle has no staging directory"),
        "verified-${safeLocalFileName(payloadName)}"
    )
    ZipFile(stagedFile).use { zip ->
        val payloadEntry = zip.getEntry(payloadName) ?: error("Missing signed AnyKernel3 payload")
        zip.getInputStream(payloadEntry).use { input ->
            FileOutputStream(payloadFile).use { output -> input.copyTo(output) }
        }
    }
    return LocalAnyKernelSelection(
        payload = payloadFile,
        displayName = displayName,
        manifest = verification.manifest,
        bundleFile = stagedFile
    )
}

private fun safeLocalFileName(value: String): String =
    value.replace(Regex("""[^A-Za-z0-9._-]"""), "_").ifBlank { "payload.zip" }

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

private fun String.defaultLkmVariantId(): String =
    // Shared classifier: the retired "ReSukiSU" spelling contains "sukisu" and must
    // resolve to the BakaSU LKM bundle, not the SukiSU one.
    when (KernelSupport.inferRuntimeKsuFamily(this)) {
        KSU_VARIANT_BAKASU -> "bakasu"
        KSU_VARIANT_SUKISU -> "sukisu"
        else -> "kernelsu"
    }
