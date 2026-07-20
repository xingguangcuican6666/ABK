package com.abk.kernel.miuix.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandIn
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.ForkRight
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Queue
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.RunCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abk.kernel.R
import com.abk.kernel.data.model.BUILD_TARGET_GKI
import com.abk.kernel.data.model.BUILD_TARGET_ONEPLUS
import com.abk.kernel.data.model.BuildPlan
import com.abk.kernel.data.model.BuildProgress
import com.abk.kernel.data.model.BuildQueueItem
import com.abk.kernel.data.model.BuildQueueItemStatus
import com.abk.kernel.data.model.BuildStatus
import com.abk.kernel.data.model.CustomExternalModule
import com.abk.kernel.data.model.CustomExternalModuleEntryKind
import com.abk.kernel.data.model.CustomExternalModuleStage
import com.abk.kernel.data.model.ExternalModuleMetadata
import com.abk.kernel.data.model.KSU_BRANCH_CUSTOM
import com.abk.kernel.data.model.KSU_BRANCH_LATEST
import com.abk.kernel.data.model.KSU_VARIANT_NONE
import com.abk.kernel.data.model.KSU_VARIANT_RESUKISU
import com.abk.kernel.data.model.KSU_VARIANT_SUKISU
import com.abk.kernel.data.model.KernelBuildConfig
import com.abk.kernel.data.model.KernelSupport
import com.abk.kernel.data.model.ModuleCatalogItem
import com.abk.kernel.data.model.ModuleCatalogItemKind
import com.abk.kernel.data.model.ModuleCatalogRepository
import com.abk.kernel.data.model.WorkflowRun
import com.abk.kernel.data.model.isKernelBuild
import com.abk.kernel.data.model.isManagerBuild
import com.abk.kernel.data.model.isManagerDevBuild
import com.abk.kernel.viewmodel.BuildPlanImportPreview
import com.abk.kernel.viewmodel.BuildPlanShareScope
import com.abk.kernel.viewmodel.MainViewModel
import com.abk.kernel.ui.navigation3.LocalNavigator
import com.abk.kernel.ui.navigation3.Route
import com.abk.kernel.miuix.component.MiuixTextInputDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowDialog
import com.abk.kernel.miuix.util.BlurredBar
import com.abk.kernel.miuix.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val CATALOG_MODULE_REMOVE_DELAY_MS = 300L
private val BuildPageHorizontalPadding = 20.dp
private val BuildPageTopSpacing = 8.dp
private val BuildPageBottomSpacing = 80.dp

@Composable
fun BuildScreenMiuix(
    vm: MainViewModel,
    outerPadding: PaddingValues = PaddingValues(0.dp),
    onPlanPageVisibleChange: (Boolean) -> Unit = {},
    onNavigateToStatus: () -> Unit = {}
) {
    val state by vm.uiState.collectAsState()
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val rawConfig = state.buildConfig
    val config = remember(rawConfig) { KernelSupport.normalize(rawConfig) }
    val isOnePlusBuild = config.buildTarget == BUILD_TARGET_ONEPLUS
    val recommended = state.recommendedBuildConfig
    val scrollBehavior = MiuixScrollBehavior()
    val surfaceColor = MiuixTheme.colorScheme.surface
    val backdrop = rememberBlurBackdrop(state.miuixBlurEnabled, surfaceColor)
    val barColor = if (backdrop != null) Color.Transparent else surfaceColor
    val suggestedPlanName = remember(config) { vm.suggestedBuildPlanName(config) }
    val ksuVariantOptions = remember(config.buildTarget) {
        if (config.buildTarget == BUILD_TARGET_ONEPLUS) {
            KernelSupport.onePlusKsuVariantOptions()
        } else {
            KernelSupport.ksuVariantOptions()
        }
    }
    val ksuBranchOptions = remember { KernelSupport.ksuBranchOptions() }
    val virtualizationSupportOptions = remember(config.kernelVersion) {
        KernelSupport.virtualizationSupportOptions(config.kernelVersion)
    }
    val subLevelOptions = remember(config.androidVersion, config.kernelVersion) {
        KernelSupport.subLevelOptions(config.androidVersion, config.kernelVersion)
    }
    val osPatchOptions = remember(config.androidVersion, config.kernelVersion, config.subLevel) {
        KernelSupport.patchLevelOptions(config.androidVersion, config.kernelVersion, config.subLevel)
    }
    val versionPreview = remember(context, config.version, config.kernelVersion, config.subLevel) {
        buildVersionPreview(context, config)
    }
    val buildTimePreviewText = remember(context, config.buildTime) {
        buildTimePreview(context, config.buildTime)
    }

    // ── Dialog/overlay state ──────────────────────────────────────────────
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showBuildSubmittedDialog by rememberSaveable { mutableStateOf(false) }
    var showSavePlanDialog by remember { mutableStateOf(false) }
    var showImportPlanDialog by remember { mutableStateOf(false) }

    var planToolsExpanded by rememberSaveable { mutableStateOf(false) }
    var savePlanName by remember { mutableStateOf("") }
    var importPlanCode by remember { mutableStateOf("") }
    var importPlanPreview by remember { mutableStateOf<BuildPlanImportPreview?>(null) }
    var importPlanError by remember { mutableStateOf<String?>(null) }
    var sharePlanTarget by remember { mutableStateOf<BuildPlan?>(null) }
    var renamePlanTarget by remember { mutableStateOf<BuildPlan?>(null) }
    var renamePlanName by remember { mutableStateOf("") }
    var deletePlanTarget by remember { mutableStateOf<BuildPlan?>(null) }
    var customModuleUrl by remember { mutableStateOf("") }
    var pendingCustomModuleUrl by remember { mutableStateOf("") }
    var pendingCustomModuleMetadata by remember { mutableStateOf<ExternalModuleMetadata?>(null) }
    var selectedCustomModuleStages by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var editingCustomModuleGroup by remember { mutableStateOf<BuildCustomModuleGroup?>(null) }
    var editingCustomModuleStages by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var editingModuleSetGroup by remember { mutableStateOf<BuildCustomModuleGroup?>(null) }
    var editingModuleSetMetadata by remember { mutableStateOf<ExternalModuleMetadata?>(null) }
    var editingModuleSetChildIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var editingModuleSetStageSelections by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    val coroutineScope = rememberCoroutineScope()

    val activeBuild = state.buildStatus in listOf(BuildStatus.QUEUED, BuildStatus.IN_PROGRESS)
    val pendingQueueCount = state.buildQueue.count { it.status == BuildQueueItemStatus.PENDING }
    val activeQueueCount = state.buildQueue.count {
        it.status in listOf(
            BuildQueueItemStatus.PENDING,
            BuildQueueItemStatus.DISPATCHING,
            BuildQueueItemStatus.RUNNING
        )
    }

    // ── Callbacks for BuildTargetContentMiuix (extracted target section) ──
    val onCheckCustomModuleMetadata: (String) -> Unit = { url ->
        coroutineScope.launch {
            vm.checkCustomExternalModuleMetadata(url)?.let { metadata ->
                pendingCustomModuleUrl = url
                pendingCustomModuleMetadata = metadata
                selectedCustomModuleStages = metadata.recommendedStages
                    .filter { it in metadata.supportedStages }
                    .ifEmpty { listOf(metadata.defaultStage) }
            }
        }
    }
    val onEditCustomModuleStages: (BuildCustomModuleGroup) -> Unit = { group ->
        editingCustomModuleGroup = group
        editingCustomModuleStages = group.stages
    }

    LaunchedEffect(config, rawConfig) {
        if (config != rawConfig) vm.updateBuildConfig(config)
    }

    fun openPlanLibraryPage() {
        navigator.push(Route.BuildPlanLibrary)
    }

    fun openBuildQueuePage() {
        navigator.push(Route.BuildQueue)
    }

    DisposableEffect(Unit) {
        onDispose { onPlanPageVisibleChange(false) }
    }

    fun clearModuleSetEditor() {
        editingModuleSetGroup = null
        editingModuleSetMetadata = null
        editingModuleSetChildIds = emptyList()
        editingModuleSetStageSelections = emptyMap()
    }

    fun openModuleSetEditor(group: BuildCustomModuleGroup) {
        val repoUrl = group.groupRepoUrl.ifBlank {
            group.catalogModule?.module?.repoUrl ?: group.url
        }.trim()
        if (repoUrl.isBlank()) return
        coroutineScope.launch {
            val metadata = vm.checkCustomExternalModuleMetadata(repoUrl) ?: return@launch
            if (metadata.kind != ModuleCatalogItemKind.MODULE_SET) return@launch
            val currentGroupModules = config.customExternalModules.filter {
                CustomExternalModuleEntryKind.normalize(it.entryKind) == CustomExternalModuleEntryKind.MODULE_SET_CHILD &&
                    (
                        it.groupRepoUrl.equals(repoUrl, ignoreCase = true) ||
                            (it.groupRepoUrl.isBlank() && it.url.equals(repoUrl, ignoreCase = true))
                        )
            }
            val selectedChildIds = currentGroupModules
                .mapNotNull { childId -> childId.childId.trim().takeIf { it.isNotBlank() } }
                .distinct()
            val stageSelections = metadata.children.associate { child ->
                val existingStages = currentGroupModules
                    .filter { it.childId.equals(child.id, ignoreCase = true) }
                    .map { CustomExternalModuleStage.normalize(it.stage) }
                    .distinct()
                    .filter { it in child.supportedStages }
                child.id to existingStages.ifEmpty {
                    child.recommendedStages
                        .filter { it in child.supportedStages }
                        .ifEmpty { listOf(child.defaultStage) }
                }
            }
            editingModuleSetGroup = group
            editingModuleSetMetadata = metadata
            editingModuleSetChildIds = selectedChildIds
            editingModuleSetStageSelections = stageSelections
        }
    }

    // ── Dialogs ────────────────────────────────────────────────────────────

    if (showBuildSubmittedDialog) {
        WindowDialog(
            show = true,
            title = stringResource(R.string.build_submitted_title),
            onDismissRequest = { showBuildSubmittedDialog = false }
        ) {
            Column {
                top.yukonga.miuix.kmp.basic.Text(
                    text = stringResource(R.string.build_submitted_desc),
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    top.yukonga.miuix.kmp.basic.TextButton(
                        modifier = Modifier.weight(1f),
                        text = stringResource(R.string.close),
                        onClick = { showBuildSubmittedDialog = false }
                    )
                    Spacer(Modifier.width(20.dp))
                    top.yukonga.miuix.kmp.basic.TextButton(
                        modifier = Modifier.weight(1f),
                        text = stringResource(R.string.build_submitted_ok),
                        colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.textButtonColorsPrimary(),
                        onClick = {
                            showBuildSubmittedDialog = false
                            onNavigateToStatus()
                        }
                    )
                }
            }
        }
    }

    if (showConfirmDialog) {
        WindowDialog(
            show = true,
            title = stringResource(R.string.build_confirm_submit),
            onDismissRequest = { showConfirmDialog = false }
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val noRootScheme = config.kernelsuVariant == KSU_VARIANT_NONE
                        val enabledLabel = stringResource(R.string.build_feature_enabled)
                        val disabledLabel = stringResource(R.string.build_feature_disabled)
                        top.yukonga.miuix.kmp.basic.Text(
                            text = stringResource(R.string.build_config_overview),
                            style = MiuixTheme.textStyles.body1,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (isOnePlusBuild) {
                            top.yukonga.miuix.kmp.basic.Text(
                                text = stringResource(R.string.build_target_line, buildTargetLabel(config.buildTarget)),
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceSecondary
                            )
                            top.yukonga.miuix.kmp.basic.Text(
                                text = stringResource(R.string.build_oneplus_device_line, KernelSupport.onePlusDeviceLabel(config.onePlusDeviceManifest)),
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceSecondary
                            )
                            top.yukonga.miuix.kmp.basic.Text(
                                text = stringResource(R.string.build_oneplus_kernel_line, config.androidVersion, config.kernelVersion),
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceSecondary
                            )
                            top.yukonga.miuix.kmp.basic.Text(
                                text = "KSU: ${ksuVariantDisplayName(config.kernelsuVariant)}",
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceSecondary
                            )
                            top.yukonga.miuix.kmp.basic.Text(
                                text = stringResource(
                                    R.string.build_oneplus_feature_line,
                                    if (!config.cancelSusfs) enabledLabel else disabledLabel,
                                    if (config.onePlusUseLz4kd) enabledLabel else disabledLabel,
                                    if (config.useKpm) enabledLabel else disabledLabel
                                ),
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceSecondary
                            )
                            top.yukonga.miuix.kmp.basic.Text(
                                text = stringResource(
                                    R.string.build_oneplus_network_line,
                                    if (config.onePlusUseBbr) enabledLabel else disabledLabel,
                                    if (config.onePlusUseProxyOptimization) enabledLabel else disabledLabel,
                                    if (config.onePlusUseUnicodeBypass) enabledLabel else disabledLabel
                                ),
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceSecondary
                            )
                            top.yukonga.miuix.kmp.basic.Text(
                                text = stringResource(
                                    R.string.build_protection_line,
                                    if (config.useBbg) enabledLabel else disabledLabel,
                                    disabledLabel
                                ),
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceSecondary
                            )
                        } else {
                            top.yukonga.miuix.kmp.basic.Text(
                                text = stringResource(R.string.build_kernel_line, config.androidVersion, config.kernelVersion, config.subLevel),
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceSecondary
                            )
                            top.yukonga.miuix.kmp.basic.Text(
                                text = if (noRootScheme) {
                                    "KSU: ${ksuVariantDisplayName(config.kernelsuVariant)}"
                                } else {
                                    "KSU: ${config.kernelsuVariant} (${config.kernelsuBranch})"
                                },
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceSecondary
                            )
                            top.yukonga.miuix.kmp.basic.Text(
                                text = stringResource(R.string.build_patch_level_line, config.osPatchLevel),
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceSecondary
                            )
                            top.yukonga.miuix.kmp.basic.Text(
                                text = stringResource(
                                    R.string.build_feature_line,
                                    if (!config.cancelSusfs) enabledLabel else disabledLabel,
                                    if (config.useZram) enabledLabel else disabledLabel,
                                    if (config.useKpm) enabledLabel else disabledLabel
                                ),
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceSecondary
                            )
                            top.yukonga.miuix.kmp.basic.Text(
                                text = stringResource(
                                    R.string.build_protection_line,
                                    if (config.useBbg) enabledLabel else disabledLabel,
                                    if (config.useDdk) enabledLabel else disabledLabel
                                ),
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceSecondary
                            )
                            top.yukonga.miuix.kmp.basic.Text(
                                text = stringResource(
                                    R.string.build_sync_network_line,
                                    if (config.useNtsync) enabledLabel else disabledLabel,
                                    if (config.useNetworking) enabledLabel else disabledLabel
                                ),
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceSecondary
                            )
                            top.yukonga.miuix.kmp.basic.Text(
                                text = stringResource(R.string.build_virtualization_line, virtualizationSupportLabel(config.virtualizationSupport)),
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceSecondary
                            )
                            top.yukonga.miuix.kmp.basic.Text(
                                text = stringResource(
                                    R.string.build_external_modules_line,
                                    if (config.useCustomExternalModules) {
                                        stringResource(R.string.build_external_modules_count, config.customExternalModules.size)
                                    } else {
                                        disabledLabel
                                    }
                                ),
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceSecondary
                            )
                        }
                        if (activeBuild || activeQueueCount > 0) {
                            top.yukonga.miuix.kmp.basic.Text(
                                text = stringResource(R.string.build_active_queue_notice),
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    top.yukonga.miuix.kmp.basic.TextButton(
                        text = stringResource(R.string.cancel),
                        modifier = Modifier.weight(1f),
                        onClick = { showConfirmDialog = false }
                    )
                    top.yukonga.miuix.kmp.basic.TextButton(
                        text = stringResource(R.string.confirm),
                        modifier = Modifier.weight(1f),
                        colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.textButtonColorsPrimary(),
                        onClick = {
                            showConfirmDialog = false
                            vm.dispatchBuild(config)
                            showBuildSubmittedDialog = true
                        }
                    )
                }
            }
        }
    }

    if (showSavePlanDialog) {
        SaveBuildPlanDialog(
            name = savePlanName,
            onNameChange = { savePlanName = it },
            onDismiss = { showSavePlanDialog = false },
            onConfirm = {
                vm.saveCurrentBuildPlan(savePlanName)
                showSavePlanDialog = false
                vm.showSnackbar(context.getString(R.string.build_plan_saved))
            }
        )
    }

    if (showImportPlanDialog) {
        ImportBuildPlanDialog(
            code = importPlanCode,
            preview = importPlanPreview,
            error = importPlanError,
            onCodeChange = {
                importPlanCode = it
                importPlanPreview = null
                importPlanError = null
            },
            onParse = {
                runCatching { vm.parseBuildPlanCode(importPlanCode, config) }
                    .onSuccess {
                        importPlanPreview = it
                        importPlanError = null
                    }
                    .onFailure {
                        importPlanPreview = null
                        importPlanError = it.message ?: context.getString(R.string.build_plan_parse_failed)
                    }
            },
            onApply = { preview ->
                vm.importBuildPlanToCurrentConfig(preview)
                showImportPlanDialog = false
                vm.showSnackbar(context.getString(R.string.build_plan_applied))
            },
            onSave = { preview ->
                vm.importBuildPlanToLibrary(preview)
                showImportPlanDialog = false
                vm.showSnackbar(context.getString(R.string.build_plan_saved_library))
            },
            onDismiss = { showImportPlanDialog = false }
        )
    }

    sharePlanTarget?.let { plan ->
        ShareBuildPlanScopeDialog(
            plan = plan,
            onDismiss = { sharePlanTarget = null },
            onShare = { scope ->
                copyTextToClipboard(
                    context = context,
                    label = context.getString(R.string.build_plan_clipboard_label),
                    text = vm.shareBuildPlanCode(plan.config, plan.name, scope)
                )
                sharePlanTarget = null
                vm.showSnackbar(context.getString(R.string.build_plan_code_copied))
            }
        )
    }

    renamePlanTarget?.let { plan ->
        RenameBuildPlanDialog(
            name = renamePlanName,
            onNameChange = { renamePlanName = it },
            onDismiss = { renamePlanTarget = null },
            onConfirm = {
                vm.renameBuildPlan(plan.id, renamePlanName)
                renamePlanTarget = null
                vm.showSnackbar(context.getString(R.string.build_plan_renamed))
            }
        )
    }

    deletePlanTarget?.let { plan ->
        DeleteBuildPlanDialog(
            plan = plan,
            onDismiss = { deletePlanTarget = null },
            onConfirm = {
                vm.deleteBuildPlan(plan.id)
                deletePlanTarget = null
                vm.showSnackbar(context.getString(R.string.build_plan_deleted))
            }
        )
    }

    pendingCustomModuleMetadata?.let { metadata ->
        val selectedStages = metadata.supportedStages.filter { it in selectedCustomModuleStages }
        val recommendedStages = metadata.recommendedStages.toSet()
        AlertDialog(
            onDismissRequest = {
                pendingCustomModuleMetadata = null
                pendingCustomModuleUrl = ""
                selectedCustomModuleStages = emptyList()
            },
            icon = { Icon(Icons.Default.Extension, null) },
            title = { Text(stringResource(R.string.build_select_injection_stage)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = metadata.name,
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (metadata.version.isNotBlank() || metadata.description.isNotBlank()) {
                        Text(
                            text = buildString {
                                if (metadata.version.isNotBlank()) append(stringResource(R.string.module_repo_version, metadata.version))
                                if (metadata.version.isNotBlank() && metadata.description.isNotBlank()) appendLine()
                                if (metadata.description.isNotBlank()) append(metadata.description)
                            },
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    metadata.supportedStages.forEach { stage ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Checkbox(
                                checked = stage in selectedCustomModuleStages,
                                onCheckedChange = { checked ->
                                    selectedCustomModuleStages = if (checked) {
                                        (selectedCustomModuleStages + stage).distinct()
                                    } else {
                                        selectedCustomModuleStages - stage
                                    }
                                }
                            )
                            Text(
                                text = if (stage in recommendedStages) {
                                    "$stage${stringResource(R.string.build_recommended_suffix)}"
                                } else {
                                    stage
                                },
                                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (vm.addCustomExternalModulesFromUrl(pendingCustomModuleUrl, selectedStages)) {
                            customModuleUrl = ""
                            pendingCustomModuleMetadata = null
                            pendingCustomModuleUrl = ""
                            selectedCustomModuleStages = emptyList()
                        }
                    },
                    enabled = selectedStages.isNotEmpty()
                ) {
                    Text(stringResource(R.string.module_repo_add_selected))
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            if (vm.addCustomExternalModulesFromUrl(pendingCustomModuleUrl, metadata.supportedStages)) {
                                customModuleUrl = ""
                                pendingCustomModuleMetadata = null
                                pendingCustomModuleUrl = ""
                                selectedCustomModuleStages = emptyList()
                            }
                        }
                    ) {
                        Text(stringResource(R.string.module_repo_all_stages))
                    }
                    TextButton(
                        onClick = {
                            pendingCustomModuleMetadata = null
                            pendingCustomModuleUrl = ""
                            selectedCustomModuleStages = emptyList()
                        }
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
        )
    }

    editingCustomModuleGroup?.let { group ->
        WindowDialog(
            show = true,
            title = stringResource(R.string.build_edit_injection_stage),
            onDismissRequest = {
                editingCustomModuleGroup = null
                editingCustomModuleStages = emptyList()
            }
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        top.yukonga.miuix.kmp.basic.Text(
                            text = group.displayName(stringResource(R.string.build_external_module_default)),
                            style = MiuixTheme.textStyles.title4,
                            fontWeight = FontWeight.SemiBold
                        )
                        top.yukonga.miuix.kmp.basic.Text(
                            text = group.url,
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        CustomExternalModuleStage.options.forEach { stage ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                top.yukonga.miuix.kmp.basic.Checkbox(
                                    state = ToggleableState(stage in editingCustomModuleStages),
                                    onClick = {
                                        editingCustomModuleStages = if (stage in editingCustomModuleStages) {
                                            editingCustomModuleStages - stage
                                        } else {
                                            (editingCustomModuleStages + stage).distinct()
                                        }
                                    }
                                )
                                top.yukonga.miuix.kmp.basic.Text(
                                    text = stage,
                                    style = MiuixTheme.textStyles.body1
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    top.yukonga.miuix.kmp.basic.TextButton(
                        text = stringResource(R.string.cancel),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            editingCustomModuleGroup = null
                            editingCustomModuleStages = emptyList()
                        }
                    )
                    top.yukonga.miuix.kmp.basic.TextButton(
                        text = if (editingCustomModuleStages.isEmpty()) {
                            stringResource(R.string.build_remove_module)
                        } else {
                            stringResource(R.string.build_save)
                        },
                        modifier = Modifier.weight(1f),
                        colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.textButtonColorsPrimary(),
                        onClick = {
                            vm.setCustomExternalModuleStages(group.url, editingCustomModuleStages)
                            editingCustomModuleGroup = null
                            editingCustomModuleStages = emptyList()
                        }
                    )
                }
            }
        }
    }

    val moduleSetGroup = editingModuleSetGroup
    val moduleSetMetadata = editingModuleSetMetadata
    if (moduleSetGroup != null && moduleSetMetadata != null) {
        WindowDialog(
            show = true,
            title = stringResource(R.string.build_edit_injection_stage),
            onDismissRequest = { clearModuleSetEditor() }
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        top.yukonga.miuix.kmp.basic.Text(
                            text = moduleSetMetadata.name,
                            style = MiuixTheme.textStyles.title4,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (moduleSetMetadata.version.isNotBlank() || moduleSetMetadata.description.isNotBlank()) {
                            top.yukonga.miuix.kmp.basic.Text(
                                text = buildString {
                                    if (moduleSetMetadata.version.isNotBlank()) {
                                        append(stringResource(R.string.module_repo_version, moduleSetMetadata.version))
                                    }
                                    if (moduleSetMetadata.version.isNotBlank() && moduleSetMetadata.description.isNotBlank()) {
                                        appendLine()
                                    }
                                    if (moduleSetMetadata.description.isNotBlank()) {
                                        append(moduleSetMetadata.description)
                                    }
                                },
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                            )
                        }
                        moduleSetMetadata.children.forEach { child ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                top.yukonga.miuix.kmp.basic.Checkbox(
                                    state = ToggleableState(child.id in editingModuleSetChildIds),
                                    onClick = {
                                        editingModuleSetChildIds = if (child.id in editingModuleSetChildIds) {
                                            editingModuleSetChildIds - child.id
                                        } else {
                                            (editingModuleSetChildIds + child.id).distinct()
                                        }
                                    }
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    top.yukonga.miuix.kmp.basic.Text(
                                        text = child.name,
                                        style = MiuixTheme.textStyles.body1,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (child.description.isNotBlank()) {
                                        top.yukonga.miuix.kmp.basic.Text(
                                            text = child.description,
                                            style = MiuixTheme.textStyles.body2,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                        )
                                    }
                                    if (child.id in editingModuleSetChildIds) {
                                        val options = child.supportedStages
                                        val initialStages = child.recommendedStages
                                            .filter { it in options }
                                            .ifEmpty { listOf(child.defaultStage) }
                                        val selectedStagesForChild = editingModuleSetStageSelections[child.id] ?: initialStages
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            options.forEach { stage ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    top.yukonga.miuix.kmp.basic.Checkbox(
                                                        state = ToggleableState(stage in selectedStagesForChild),
                                                        onClick = {
                                                            val updatedStages = if (stage in selectedStagesForChild) {
                                                                selectedStagesForChild - stage
                                                            } else {
                                                                (selectedStagesForChild + stage).distinct()
                                                            }
                                                            editingModuleSetStageSelections =
                                                                editingModuleSetStageSelections + (child.id to updatedStages)
                                                        }
                                                    )
                                                    top.yukonga.miuix.kmp.basic.Text(
                                                        text = buildString {
                                                            append(stage)
                                                            if (stage in child.recommendedStages) {
                                                                append(stringResource(R.string.module_repo_recommended))
                                                            }
                                                        },
                                                        style = MiuixTheme.textStyles.body2
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    top.yukonga.miuix.kmp.basic.TextButton(
                        text = stringResource(R.string.cancel),
                        modifier = Modifier.weight(1f),
                        onClick = { clearModuleSetEditor() }
                    )
                    top.yukonga.miuix.kmp.basic.TextButton(
                        text = stringResource(R.string.build_save),
                        modifier = Modifier.weight(1f),
                        enabled = editingModuleSetChildIds.isNotEmpty() && moduleSetMetadata.children
                            .filter { it.id in editingModuleSetChildIds }
                            .all { child ->
                                val selStages = editingModuleSetStageSelections[child.id]
                                    ?: child.recommendedStages
                                        .filter { it in child.supportedStages }
                                        .ifEmpty { listOf(child.defaultStage) }
                                selStages.any { it in child.supportedStages }
                            },
                        colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.textButtonColorsPrimary(),
                        onClick = {
                            val repoUrl = moduleSetGroup.groupRepoUrl.ifBlank {
                                moduleSetGroup.catalogModule?.module?.repoUrl ?: moduleSetGroup.url
                            }
                            val selections = moduleSetMetadata.children
                                .filter { it.id in editingModuleSetChildIds }
                                .map { child ->
                                    child to (
                                        editingModuleSetStageSelections[child.id]
                                            ?.distinct()
                                            ?.filter { stage -> stage in child.supportedStages }
                                            ?.ifEmpty {
                                                child.recommendedStages
                                                    .filter { stage -> stage in child.supportedStages }
                                                    .ifEmpty { listOf(child.defaultStage) }
                                            }
                                            ?: child.recommendedStages
                                                .filter { stage -> stage in child.supportedStages }
                                                .ifEmpty { listOf(child.defaultStage) }
                                        )
                                }
                                .filter { (_, stages) -> stages.isNotEmpty() }
                            if (vm.replaceModuleSetSelection(repoUrl, moduleSetMetadata, selections)) {
                                clearModuleSetEditor()
                            }
                        }
                    )
                }
            }
        }
    }

    state.workflowEnablementPrompt?.let { prompt ->
        AlertDialog(
            onDismissRequest = { vm.dismissWorkflowEnablementPrompt() },
            icon = { Icon(Icons.Default.OpenInBrowser, null) },
            title = { Text(stringResource(R.string.build_workflow_required)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.build_workflow_required_desc_1))
                    Text(stringResource(R.string.build_workflow_required_desc_2))
                    Text(stringResource(R.string.build_workflow_required_desc_3))
                    Text(
                        text = stringResource(R.string.build_workflow_check_result, prompt.message),
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall
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
                    Text(stringResource(R.string.build_open_actions_page))
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.dismissWorkflowEnablementPrompt() }) {
                    Text(stringResource(R.string.build_handle_later))
                }
            }
        )
    }

    // ── Login/Fork required early return ──────────────────────────────────
    if (!state.isLoggedIn || state.forkRepo == null) {
        val needsLogin = !state.isLoggedIn
        Scaffold(
    

            topBar = {
                BlurredBar(backdrop, surfaceColor) {
                    TopAppBar(
                        color = barColor,
                        title = stringResource(R.string.build_title),
                        scrollBehavior = scrollBehavior
                    )
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier.then(
                    if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier
                )
            ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = BuildPageHorizontalPadding)
            ) {
                Spacer(Modifier.height(padding.calculateTopPadding() + BuildPageTopSpacing))
                BuildHeroCardMiuix(
                    title = stringResource(
                        if (needsLogin) R.string.build_login_required_title
                        else R.string.build_fork_required_title
                    ),
                    subtitle = stringResource(
                        if (needsLogin) R.string.build_login_required_desc
                        else R.string.build_fork_required_desc
                    ),
                    isActivated = false,
                    themeMode = state.themeMode
                )
                Spacer(Modifier.height(12.dp))
                top.yukonga.miuix.kmp.basic.Button(
                    onClick = vm::openBuildOobe,
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    top.yukonga.miuix.kmp.basic.Text(
                        text = stringResource(
                            if (needsLogin) R.string.login_github
                            else R.string.oobe_continue_setup
                        )
                    )
                }
                Spacer(Modifier.height(BuildPageBottomSpacing + outerPadding.calculateBottomPadding()))
            }
            }
        }
        return
    }

    // ── Child page overlay ────────────────────────────────────────────────
    Scaffold(
    

            topBar = {
                BlurredBar(backdrop, surfaceColor) {
                    TopAppBar(
                        color = barColor,
                        title = stringResource(R.string.build_title),
                        scrollBehavior = scrollBehavior
                    )
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier.then(
                    if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier
                )
            ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .verticalScroll(rememberScrollState())
                    .overScrollVertical()
                    .scrollEndHaptic()
                    .padding(horizontal = BuildPageHorizontalPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Spacer(Modifier.height(padding.calculateTopPadding() + BuildPageTopSpacing))

                // ═══ 1. Hero card ═══════════════════════════════════════════
                BuildPlanHeroMiuix(
                    config = config,
                    recommended = recommended,
                    status = state.buildStatus,
                    themeMode = state.themeMode
                )

                // ═══ 2. Build Plan Tools ════════════════════════════════════
                BuildPlanToolsCardMiuix(
                    plansCount = state.buildPlans.size,
                    pendingQueueCount = pendingQueueCount,
                    activeQueueCount = activeQueueCount,
                    expanded = planToolsExpanded,
                    currentSummary = buildPlanSummary(config),
                    onExpandedChange = { planToolsExpanded = it },
                    onSave = {
                        savePlanName = suggestedPlanName
                        showSavePlanDialog = true
                    },
                    onLibrary = ::openPlanLibraryPage,
                    onQueue = ::openBuildQueuePage,
                    onShare = {
                        sharePlanTarget = BuildPlan(name = suggestedPlanName, config = config)
                    },
                    onImport = {
                        importPlanCode = ""
                        importPlanPreview = null
                        importPlanError = null
                        showImportPlanDialog = true
                    }
                )

                // ═══ 3. Build Target Selector ═══════════════════════════════
                Column(
                    modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp, bottom = 0.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = stringResource(R.string.build_target_title),
                        style = MiuixTheme.textStyles.subtitle,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.build_target_desc),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary
                    )
                }
                BuildTargetSelectorMiuix(
                    selected = config.buildTarget,
                    onSelect = { target ->
                        val next = if (target == BUILD_TARGET_ONEPLUS) {
                            config.copy(
                                buildTarget = BUILD_TARGET_ONEPLUS,
                                androidVersion = "android14",
                                kernelVersion = "6.1",
                                kernelsuVariant = KSU_VARIANT_SUKISU,
                                cancelSusfs = true,
                                useKpm = false,
                                useBbg = true,
                                onePlusCpu = "sm8650",
                                onePlusDeviceManifest = "oneplus_12_b",
                                onePlusUseLz4kd = false,
                                onePlusUseBbr = false,
                                onePlusUseProxyOptimization = true,
                                onePlusUseUnicodeBypass = false
                            )
                        } else {
                            config.copy(
                                buildTarget = BUILD_TARGET_GKI,
                                kernelsuVariant = KSU_VARIANT_RESUKISU
                            )
                        }
                        vm.updateBuildConfig(KernelSupport.normalize(next))
                    }
                )

                // ═══ 4. Build Progress ══════════════════════════════════════
                AnimatedVisibility(
                    visible = state.buildStatus != BuildStatus.IDLE,
                    enter = fadeIn() + expandIn(expandFrom = Alignment.TopStart),
                    exit = fadeOut() + shrinkVertically() + shrinkOut(shrinkTowards = Alignment.TopStart)
                ) {
                    val kernelActiveRuns = remember(state.activeBuildRuns) {
                        state.activeBuildRuns.filter { it.isKernelBuild() }
                    }
                    val managerActiveRuns = remember(state.activeBuildRuns) {
                        state.activeBuildRuns.filter { it.isManagerBuild() }
                    }
                    val kernelRunningChips = remember(kernelActiveRuns, state.buildQueue) {
                        buildRunChipsForStatus(kernelActiveRuns, state.buildQueue, running = true)
                    }
                    val kernelQueuedChips = remember(kernelActiveRuns, state.buildQueue) {
                        buildRunChipsForStatus(kernelActiveRuns, state.buildQueue, running = false)
                    }
                    val managerRunningChips = remember(managerActiveRuns, state.buildQueue) {
                        buildRunChipsForStatus(managerActiveRuns, state.buildQueue, running = true)
                    }
                    val managerQueuedChips = remember(managerActiveRuns, state.buildQueue) {
                        buildRunChipsForStatus(managerActiveRuns, state.buildQueue, running = false)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        BuildKindProgressBlockMiuix(
                            title = stringResource(R.string.status_build),
                            status = state.kernelBuildStatus,
                            progress = state.kernelBuildProgress,
                            currentRun = state.kernelCurrentRun,
                            activeRunCount = state.kernelActiveBuildRuns.size,
                            cancellingRunIds = state.cancellingWorkflowRunIds,
                            runningChips = kernelRunningChips,
                            queuedChips = kernelQueuedChips,
                            onCancel = vm::cancelWorkflowRun
                        )
                        if (state.managerBuildStatus != BuildStatus.IDLE || state.managerCurrentRun != null) {
                            BuildKindProgressBlockMiuix(
                                title = stringResource(R.string.status_manager_build),
                                status = state.managerBuildStatus,
                                progress = state.managerBuildProgress,
                                currentRun = state.managerCurrentRun,
                                activeRunCount = state.managerActiveBuildRuns.size,
                                cancellingRunIds = state.cancellingWorkflowRunIds,
                                runningChips = managerRunningChips,
                                queuedChips = managerQueuedChips,
                                onCancel = vm::cancelWorkflowRun
                            )
                        }
                    }
                }

                // ═══ 5-10. Target Content (animated slide) ═════════════════════
                AnimatedContent(
                    targetState = config.buildTarget,
                    transitionSpec = {
                        val targetOrder = listOf(BUILD_TARGET_GKI, BUILD_TARGET_ONEPLUS)
                        val targetIndex = targetOrder.indexOf(targetState)
                        val initialIndex = targetOrder.indexOf(initialState).coerceAtLeast(0)
                        val direction = if (targetIndex > initialIndex) 1 else -1
                        (
                            slideInHorizontally { width -> direction * width / 4 } + fadeIn()
                        ) togetherWith (
                            slideOutHorizontally { width -> -direction * width / 4 } + fadeOut()
                        )
                    },
                    label = "buildTargetSlide"
                ) { _ ->
                    BuildTargetContentMiuix(
                        modifier = Modifier.fillMaxWidth(),
                        config = config,
                        vm = vm,
                        customModuleUrl = customModuleUrl,
                        onCustomModuleUrlChange = { customModuleUrl = it },
                        onCheckCustomModuleMetadata = onCheckCustomModuleMetadata,
                        onEditCustomModuleStages = onEditCustomModuleStages,
                        onOpenModuleSetEditor = ::openModuleSetEditor,
                    )
                }

                // ═══ 11. Optional Config Section ════════════════════════════
                SectionTitle(stringResource(R.string.build_optional_config))
                Card(modifier = Modifier.fillMaxWidth()) {
                    BuildTextFieldItem(
                        value = config.version,
                        onValueChange = { vm.updateBuildConfig(config.copy(version = it)) },
                        label = stringResource(R.string.build_custom_version_optional),
                        placeholder = ""
                    )
                    ConfigPreviewItemMiuix(
                        icon = Icons.Default.Visibility,
                        title = stringResource(R.string.build_config_preview),
                        preview = versionPreview
                    )
                    BuildTextFieldItem(
                        value = config.buildTime,
                        onValueChange = { vm.updateBuildConfig(config.copy(buildTime = it)) },
                        label = stringResource(R.string.build_custom_time_optional),
                        placeholder = stringResource(R.string.build_time_placeholder)
                    )
                    ConfigPreviewItemMiuix(
                        icon = Icons.Default.Visibility,
                        title = stringResource(R.string.build_config_preview),
                        preview = buildTimePreviewText
                        )
                }

                // ═══ 12. Submit Button ══════════════════════════════════════
                top.yukonga.miuix.kmp.basic.Button(
                    onClick = { showConfirmDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.buttonColors(
                        color = MiuixTheme.colorScheme.primary,
                        contentColor = Color.White
                    )
                ) {
                    top.yukonga.miuix.kmp.basic.Text(
                        text = if (activeBuild || activeQueueCount > 0 || state.buildQueueProcessing) {
                            stringResource(R.string.build_add_queue)
                        } else {
                            stringResource(R.string.build_submit)
                        }
                    )
                }

                Spacer(Modifier.height(BuildPageBottomSpacing + outerPadding.calculateBottomPadding()))
            }
            }
        }
}

// ═════════════════════════════════════════════════════════════════════════════
// Extracted target-related content composable for AnimatedContent slide
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun BuildTargetContentMiuix(
    modifier: Modifier = Modifier,
    config: KernelBuildConfig,
    vm: MainViewModel,
    customModuleUrl: String,
    onCustomModuleUrlChange: (String) -> Unit,
    onCheckCustomModuleMetadata: (url: String) -> Unit,
    onEditCustomModuleStages: (group: BuildCustomModuleGroup) -> Unit,
    onOpenModuleSetEditor: (group: BuildCustomModuleGroup) -> Unit,
) {
    val state by vm.uiState.collectAsState()
    val isOnePlusBuild = config.buildTarget == BUILD_TARGET_ONEPLUS

    val subLevelOptions = remember(config.androidVersion, config.kernelVersion) {
        KernelSupport.subLevelOptions(config.androidVersion, config.kernelVersion)
    }
    val osPatchOptions = remember(config.androidVersion, config.kernelVersion, config.subLevel) {
        KernelSupport.patchLevelOptions(config.androidVersion, config.kernelVersion, config.subLevel)
    }
    val ksuVariantOptions = remember(config.buildTarget) {
        if (config.buildTarget == BUILD_TARGET_ONEPLUS) {
            KernelSupport.onePlusKsuVariantOptions()
        } else {
            KernelSupport.ksuVariantOptions()
        }
    }
    val ksuBranchOptions = remember { KernelSupport.ksuBranchOptions() }
    val virtualizationSupportOptions = remember(config.kernelVersion) {
        KernelSupport.virtualizationSupportOptions(config.kernelVersion)
    }

    val catalogModules = remember(state.buildModuleRepositories) {
        mergeBuildCatalogModules(state.buildModuleRepositories)
    }
    val catalogModuleByUrl = remember(catalogModules) {
        catalogModules.associateBy { it.module.repoUrl.trim().lowercase() }
    }
    val customModuleGroups = remember(config.customExternalModules, catalogModuleByUrl) {
        groupBuildCustomExternalModules(config.customExternalModules, catalogModuleByUrl)
    }

    var removingCustomModuleKeys by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ═══ 5. Kernel Version Section ══════════════════════════════
        SectionTitle(stringResource(R.string.build_kernel_version_config))
        Card(modifier = Modifier.fillMaxWidth()) {
            if (isOnePlusBuild) {
                val deviceOptions = KernelSupport.onePlusDeviceManifestOptions
                val deviceLabels = deviceOptions.map { KernelSupport.onePlusDeviceLabel(it) }
                val deviceIndex = deviceOptions.indexOf(config.onePlusDeviceManifest).coerceAtLeast(0)
                OverlayDropdownPreference(
                    title = stringResource(R.string.build_oneplus_device_manifest),
                    items = deviceLabels,
                    selectedIndex = deviceIndex,
                    renderInRootScaffold = true,
                    maxHeight = 336.dp,
                    onSelectedIndexChange = { index ->
                        val manifest = deviceOptions[index]
                        val profile = KernelSupport.onePlusDeviceProfile(manifest)
                        vm.updateBuildConfig(
                            KernelSupport.normalize(
                                config.copy(
                                    onePlusDeviceManifest = manifest,
                                    onePlusCpu = profile?.cpu ?: config.onePlusCpu,
                                    androidVersion = profile?.androidVersion ?: config.androidVersion,
                                    kernelVersion = profile?.kernelVersion ?: config.kernelVersion
                                )
                            )
                        )
                    }
                )
                BasicComponent(
                    title = stringResource(R.string.build_oneplus_cpu),
                    summary = config.onePlusCpu
                )
                BasicComponent(
                    title = stringResource(R.string.build_android_version),
                    summary = config.androidVersion
                )
                BasicComponent(
                    title = stringResource(R.string.build_kernel_version),
                    summary = config.kernelVersion
                )
            } else {
                val androidOptions = KernelSupport.androidVersions()
                val androidIndex = androidOptions.indexOf(config.androidVersion).coerceAtLeast(0)
                OverlayDropdownPreference(
                    title = stringResource(R.string.build_android_version),
                    items = androidOptions,
                    selectedIndex = androidIndex,
                    renderInRootScaffold = true,
                    onSelectedIndexChange = { index ->
                        val v = androidOptions[index]
                        vm.updateBuildConfig(
                            KernelSupport.normalize(
                                config.copy(
                                    androidVersion = v,
                                    kernelVersion = KernelSupport.kernelForAndroid(v)
                                )
                            )
                        )
                    }
                )
                val kernelOptions = KernelSupport.kernelVersions()
                val kernelIndex = kernelOptions.indexOf(config.kernelVersion).coerceAtLeast(0)
                OverlayDropdownPreference(
                    title = stringResource(R.string.build_kernel_version),
                    items = kernelOptions,
                    selectedIndex = kernelIndex,
                    renderInRootScaffold = true,
                    onSelectedIndexChange = { index ->
                        val v = kernelOptions[index]
                        vm.updateBuildConfig(
                            KernelSupport.normalize(
                                config.copy(
                                    androidVersion = KernelSupport.androidForKernel(v),
                                    kernelVersion = v
                                )
                            )
                        )
                    }
                )
                val subIndex = subLevelOptions.indexOf(config.subLevel).coerceAtLeast(0)
                OverlayDropdownPreference(
                    title = stringResource(R.string.build_sub_level),
                    items = subLevelOptions,
                    selectedIndex = subIndex,
                    renderInRootScaffold = true,
                    maxHeight = 240.dp,
                    onSelectedIndexChange = { index ->
                        vm.updateBuildConfig(KernelSupport.normalize(config.copy(subLevel = subLevelOptions[index])))
                    }
                )
                val patchIndex = osPatchOptions.indexOf(config.osPatchLevel).coerceAtLeast(0)
                OverlayDropdownPreference(
                    title = stringResource(R.string.build_security_patch_level),
                    items = osPatchOptions,
                    selectedIndex = patchIndex,
                    renderInRootScaffold = true,
                    onSelectedIndexChange = { index ->
                        vm.updateBuildConfig(config.copy(osPatchLevel = osPatchOptions[index]))
                    }
                )
            }
        }

        // ═══ 6. KernelSU Section ════════════════════════════════════
        SectionTitle(stringResource(R.string.build_kernelsu_config))
        Card(modifier = Modifier.fillMaxWidth()) {
            val noRootScheme = config.kernelsuVariant == KSU_VARIANT_NONE
            val variantIndex = ksuVariantOptions.indexOf(config.kernelsuVariant).coerceAtLeast(0)
            OverlayDropdownPreference(
                title = stringResource(R.string.build_kernelsu_variant),
                items = ksuVariantOptions.map { ksuVariantDisplayName(it) },
                selectedIndex = variantIndex,
                renderInRootScaffold = true,
                onSelectedIndexChange = { index ->
                    vm.updateBuildConfig(KernelSupport.normalize(config.copy(kernelsuVariant = ksuVariantOptions[index])))
                }
            )
            AnimatedVisibility(
                visible = !noRootScheme && !isOnePlusBuild,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                val branchIndex = ksuBranchOptions.indexOf(KernelSupport.normalizeKsuBranch(config.kernelsuBranch)).coerceAtLeast(0)
                OverlayDropdownPreference(
                    title = stringResource(R.string.build_ksu_branch),
                    items = ksuBranchOptions,
                    selectedIndex = branchIndex,
                    renderInRootScaffold = true,
                    onSelectedIndexChange = { index ->
                        vm.updateBuildConfig(
                            KernelSupport.normalize(config.copy(kernelsuBranch = ksuBranchOptions[index]))
                        )
                    }
                )
            }
        }

        // ═══ 7. Features Section ════════════════════════════════════
        SectionTitle(stringResource(R.string.build_features))
        Card(modifier = Modifier.fillMaxWidth()) {
            val noRootScheme = config.kernelsuVariant == KSU_VARIANT_NONE
            val kpmSupported = KernelSupport.isKpmSupported(
                config.buildTarget,
                config.kernelsuVariant,
                config.kernelsuBranch
            )
            if (isOnePlusBuild) {
                val proxyAllowed = !config.onePlusCpu.startsWith("mt")
                val onePlusSusfsSupported = KernelSupport.onePlusSusfsSupported(config.androidVersion, config.kernelVersion)
                SwitchPreference(
                    title = stringResource(R.string.build_enable_susfs),
                    checked = !config.cancelSusfs && onePlusSusfsSupported,
                    onCheckedChange = { vm.updateBuildConfig(KernelSupport.normalize(config.copy(cancelSusfs = !it))) },
                    enabled = !noRootScheme && onePlusSusfsSupported
                )
                SwitchPreference(
                    title = stringResource(R.string.build_enable_kpm),
                    checked = config.useKpm,
                    onCheckedChange = {
                        if (kpmSupported && !noRootScheme) {
                            vm.updateBuildConfig(KernelSupport.normalize(config.copy(useKpm = it)))
                        }
                    },
                    enabled = kpmSupported && !noRootScheme
                )
                SwitchPreference(
                    title = stringResource(R.string.build_oneplus_lz4kd),
                    checked = config.onePlusUseLz4kd,
                    onCheckedChange = { vm.updateBuildConfig(config.copy(onePlusUseLz4kd = it)) }
                )
                SwitchPreference(
                    title = stringResource(R.string.build_enable_bbg),
                    checked = config.useBbg,
                    onCheckedChange = { vm.updateBuildConfig(config.copy(useBbg = it)) }
                )
                SwitchPreference(
                    title = stringResource(R.string.build_oneplus_bbr),
                    checked = config.onePlusUseBbr,
                    onCheckedChange = { vm.updateBuildConfig(config.copy(onePlusUseBbr = it)) }
                )
                SwitchPreference(
                    title = stringResource(R.string.build_oneplus_proxy_optimization),
                    checked = config.onePlusUseProxyOptimization,
                    onCheckedChange = {
                        if (proxyAllowed) {
                            vm.updateBuildConfig(KernelSupport.normalize(config.copy(onePlusUseProxyOptimization = it)))
                        }
                    }
                )
                SwitchPreference(
                    title = stringResource(R.string.build_oneplus_unicode_bypass),
                    checked = config.onePlusUseUnicodeBypass,
                    onCheckedChange = { vm.updateBuildConfig(config.copy(onePlusUseUnicodeBypass = it)) }
                )
            } else {
                SwitchPreference(
                    title = stringResource(R.string.build_enable_susfs),
                    checked = !config.cancelSusfs,
                    onCheckedChange = {
                        if (!noRootScheme) {
                            vm.updateBuildConfig(KernelSupport.normalize(config.copy(cancelSusfs = !it)))
                        }
                    },
                    enabled = !noRootScheme
                )
                SwitchPreference(
                    title = stringResource(R.string.build_enable_zram),
                    checked = config.useZram,
                    onCheckedChange = { vm.updateBuildConfig(config.copy(useZram = it)) }
                )
                SwitchPreference(
                    title = stringResource(R.string.build_enable_bbg),
                    checked = config.useBbg,
                    onCheckedChange = { vm.updateBuildConfig(config.copy(useBbg = it)) }
                )
                SwitchPreference(
                    title = stringResource(R.string.build_enable_ddk),
                    checked = config.useDdk,
                    onCheckedChange = { vm.updateBuildConfig(config.copy(useDdk = it)) }
                )
                SwitchPreference(
                    title = stringResource(R.string.build_enable_ntsync),
                    checked = config.useNtsync,
                    onCheckedChange = { vm.updateBuildConfig(config.copy(useNtsync = it)) }
                )
                SwitchPreference(
                    title = stringResource(R.string.build_enable_networking),
                    checked = config.useNetworking,
                    onCheckedChange = { vm.updateBuildConfig(config.copy(useNetworking = it)) }
                )
                SwitchPreference(
                    title = stringResource(R.string.build_enable_kpm),
                    checked = config.useKpm,
                    onCheckedChange = {
                        if (kpmSupported && !noRootScheme) {
                            vm.updateBuildConfig(KernelSupport.normalize(config.copy(useKpm = it)))
                        }
                    },
                    enabled = kpmSupported && !noRootScheme
                )
                SwitchPreference(
                    title = stringResource(R.string.build_enable_rekernel),
                    checked = config.useRekernel,
                    onCheckedChange = { vm.updateBuildConfig(config.copy(useRekernel = it)) }
                )
                val virtIndex = virtualizationSupportOptions.indexOf(config.virtualizationSupport).coerceAtLeast(0)
                OverlayDropdownPreference(
                    title = stringResource(R.string.build_virtualization_support),
                    items = virtualizationSupportOptions.map { virtualizationSupportLabel(it) },
                    selectedIndex = virtIndex,
                    renderInRootScaffold = true,
                    onSelectedIndexChange = { index ->
                        vm.updateBuildConfig(config.copy(virtualizationSupport = virtualizationSupportOptions[index]))
                    }
                )
                SwitchPreference(
                    title = stringResource(R.string.build_enable_oneplus_8e),
                    checked = config.suppOp,
                    onCheckedChange = { vm.updateBuildConfig(config.copy(suppOp = it)) }
                )
            }
        }

        // ═══ 8. ZRAM Options (conditional) ══════════════════════════
        AnimatedVisibility(
            visible = !isOnePlusBuild && config.useZram,
            enter = fadeIn() + expandIn(expandFrom = Alignment.TopStart),
            exit = fadeOut() + shrinkVertically() + shrinkOut(shrinkTowards = Alignment.TopStart)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle(stringResource(R.string.build_zram_options))
                Card(modifier = Modifier.fillMaxWidth()) {
                    SwitchPreference(
                        title = stringResource(R.string.build_zram_full_algo),
                        checked = config.zramFullAlgo,
                        onCheckedChange = { vm.updateBuildConfig(config.copy(zramFullAlgo = it)) }
                    )
                    AnimatedVisibility(
                        visible = config.zramFullAlgo,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        BuildTextFieldItem(
                            value = config.zramExtraAlgos,
                            onValueChange = { vm.updateBuildConfig(config.copy(zramExtraAlgos = it)) },
                            label = stringResource(R.string.build_zram_custom_algo),
                            placeholder = stringResource(R.string.build_zram_algo_placeholder)
                        )
                    }
                }
            }
        }

        // ═══ 9. KPM Options (conditional) ═══════════════════════════
        AnimatedVisibility(
            visible = !isOnePlusBuild && config.useKpm,
            enter = fadeIn() + expandIn(expandFrom = Alignment.TopStart),
            exit = fadeOut() + shrinkVertically() + shrinkOut(shrinkTowards = Alignment.TopStart)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle(stringResource(R.string.build_kpm_options))
                Card(modifier = Modifier.fillMaxWidth()) {
                    BuildTextFieldItem(
                        value = config.kpmPassword,
                        onValueChange = { vm.updateBuildConfig(config.copy(kpmPassword = it)) },
                        label = stringResource(R.string.build_kpm_password),
                        placeholder = stringResource(R.string.build_kpm_password_placeholder)
                    )
                }
            }
        }

        // ═══ 10. Custom Modules Section (GKI only) ══════════════════
        if (!isOnePlusBuild) {
            SectionTitle(stringResource(R.string.build_custom_modules))
            Card(modifier = Modifier.fillMaxWidth()) {
                SwitchPreference(
                    title = stringResource(R.string.build_enable_custom_modules),
                    checked = config.useCustomExternalModules,
                    onCheckedChange = { vm.updateBuildConfig(config.copy(useCustomExternalModules = it)) }
                )
                AnimatedVisibility(
                    visible = config.useCustomExternalModules,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    val catalogGroups = customModuleGroups.filter { it.catalogModule != null }
                    val manualGroups = customModuleGroups.filter { it.catalogModule == null }
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 4000.dp),
                        userScrollEnabled = false,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (catalogGroups.isNotEmpty()) {
                            item(key = "catalog-header") {
                                top.yukonga.miuix.kmp.basic.Text(
                                    text = stringResource(R.string.build_add_from_module_repo),
                                    style = MiuixTheme.textStyles.subtitle,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                )
                            }
                            items(catalogGroups, key = { it.key }) { group ->
                                AnimatedVisibility(
                                    visible = group.key !in removingCustomModuleKeys,
                                    enter = fadeIn() + expandVertically(),
                                    exit = fadeOut() + shrinkVertically()
                                ) {
                                    ArrowPreference(
                                        modifier = Modifier.animateItem(),
                                        title = group.displayName(stringResource(R.string.build_external_module_default)),
                                        summary = group.subtitle(
                                            noStageLabel = stringResource(R.string.build_stage_none),
                                            sourcePrefix = stringResource(R.string.build_source_list, "%s")
                                        ),
                                        endActions = {
                                            IconButton(
                                                onClick = {
                                                    if (group.entryKind == CustomExternalModuleEntryKind.MODULE_SET_CHILD) {
                                                        onOpenModuleSetEditor(group)
                                                    } else {
                                                        onEditCustomModuleStages(group)
                                                    }
                                                }
                                            ) {
                                                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.build_edit_injection_stage), tint = MiuixTheme.colorScheme.onSurface)
                                            }
                                            IconButton(
                                                onClick = {
                                                    if (group.key in removingCustomModuleKeys) return@IconButton
                                                    removingCustomModuleKeys =
                                                        (removingCustomModuleKeys + group.key).distinct()
                                                    coroutineScope.launch {
                                                        delay(CATALOG_MODULE_REMOVE_DELAY_MS)
                                                        if (group.entryKind == CustomExternalModuleEntryKind.MODULE_SET_CHILD) {
                                                            vm.removeModuleSetSelection(group.groupRepoUrl.ifBlank { group.url })
                                                        } else {
                                                            vm.setCustomExternalModuleStages(group.url, emptyList())
                                                        }
                                                        removingCustomModuleKeys =
                                                            removingCustomModuleKeys - group.key
                                                    }
                                                },
                                                enabled = group.key !in removingCustomModuleKeys
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.build_remove_module), tint = MiuixTheme.colorScheme.onSurface)
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        if (manualGroups.isNotEmpty()) {
                            item(key = "manual-header") {
                                top.yukonga.miuix.kmp.basic.Text(
                                    text = stringResource(R.string.build_manual_add),
                                    style = MiuixTheme.textStyles.subtitle,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                )
                            }
                            items(manualGroups, key = { it.key }) { group ->
                                AnimatedVisibility(
                                    visible = group.key !in removingCustomModuleKeys,
                                    enter = fadeIn() + expandVertically(),
                                    exit = fadeOut() + shrinkVertically()
                                ) {
                                    ArrowPreference(
                                        modifier = Modifier.animateItem(),
                                        title = group.displayName(stringResource(R.string.build_external_module_default)),
                                        summary = group.subtitle(
                                            noStageLabel = stringResource(R.string.build_stage_none),
                                            sourcePrefix = stringResource(R.string.build_source_list, "%s")
                                        ),
                                        endActions = {
                                            IconButton(
                                                onClick = {
                                                    if (group.entryKind == CustomExternalModuleEntryKind.MODULE_SET_CHILD) {
                                                        onOpenModuleSetEditor(group)
                                                    } else {
                                                        onEditCustomModuleStages(group)
                                                    }
                                                }
                                            ) {
                                                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.build_edit_injection_stage), tint = MiuixTheme.colorScheme.onSurface)
                                            }
                                            IconButton(
                                                onClick = {
                                                    if (group.key in removingCustomModuleKeys) return@IconButton
                                                    removingCustomModuleKeys =
                                                        (removingCustomModuleKeys + group.key).distinct()
                                                    coroutineScope.launch {
                                                        delay(CATALOG_MODULE_REMOVE_DELAY_MS)
                                                        if (group.entryKind == CustomExternalModuleEntryKind.MODULE_SET_CHILD) {
                                                            vm.removeModuleSetSelection(group.groupRepoUrl.ifBlank { group.url })
                                                        } else {
                                                            vm.setCustomExternalModuleStages(group.url, emptyList())
                                                        }
                                                        removingCustomModuleKeys =
                                                            removingCustomModuleKeys - group.key
                                                    }
                                                },
                                                enabled = group.key !in removingCustomModuleKeys
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.build_remove_module), tint = MiuixTheme.colorScheme.onSurface)
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        // Add custom module text field + button
                        item(key = "add-field") {
                            BuildTextFieldItem(
                                value = customModuleUrl,
                                onValueChange = onCustomModuleUrlChange,
                                label = stringResource(R.string.build_repo_url),
                                placeholder = "https://github.com/user/module"
                            )
                        }
                        item(key = "add-button") {
                            top.yukonga.miuix.kmp.basic.Button(
                                onClick = {
                                    val cleanUrl = customModuleUrl.trim()
                                    if (cleanUrl.isNotEmpty()) {
                                        onCheckCustomModuleMetadata(cleanUrl)
                                    }
                                },
                                enabled = customModuleUrl.isNotBlank() && !state.validatingCustomExternalModule,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .height(48.dp)
                            ) {
                                top.yukonga.miuix.kmp.basic.Text(
                                    text = if (state.validatingCustomExternalModule) {
                                        stringResource(R.string.build_checking)
                                    } else {
                                        stringResource(R.string.build_check_module)
                                    }
                                )
                            }
                        }

                        state.customExternalModuleError?.let { err ->
                            item(key = "error") {
                                Card(
                                    colors = CardDefaults.defaultColors(
                                        color = MiuixTheme.colorScheme.error.copy(alpha = 0.12f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp)
                                ) {
                                    Row(
                                        Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Error, null, tint = MiuixTheme.colorScheme.error)
                                        Spacer(Modifier.width(8.dp))
                                        top.yukonga.miuix.kmp.basic.Text(
                                            text = err,
                                            style = MiuixTheme.textStyles.body2,
                                            color = MiuixTheme.colorScheme.error,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(onClick = { vm.clearCustomExternalModuleError() }) {
                                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close_error), tint = MiuixTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// ═════════════════════════════════════════════════════════════════════════════
// Public screens for Navigation3 push destinations
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun BuildPlanLibraryScreenMiuix(vm: MainViewModel) {
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val scrollBehavior = MiuixScrollBehavior()
    val surfaceColor = MiuixTheme.colorScheme.surface
    val backdrop = rememberBlurBackdrop(state.miuixBlurEnabled, surfaceColor)
    val barColor = if (backdrop != null) Color.Transparent else surfaceColor

    var sharePlanTarget by remember { mutableStateOf<BuildPlan?>(null) }
    var renamePlanTarget by remember { mutableStateOf<BuildPlan?>(null) }
    var renamePlanName by remember { mutableStateOf("") }
    var deletePlanTarget by remember { mutableStateOf<BuildPlan?>(null) }

    sharePlanTarget?.let { plan ->
        ShareBuildPlanScopeDialog(
            plan = plan,
            onDismiss = { sharePlanTarget = null },
            onShare = { scope ->
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(
                    ClipData.newPlainText(
                        context.getString(R.string.build_plan_clipboard_label),
                        vm.shareBuildPlanCode(plan.config, plan.name, scope)
                    )
                )
                sharePlanTarget = null
                vm.showSnackbar(context.getString(R.string.build_plan_code_copied))
            }
        )
    }

    renamePlanTarget?.let { plan ->
        WindowDialog(
            show = true,
            title = stringResource(R.string.build_rename_plan),
            onDismissRequest = { renamePlanTarget = null }
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        BuildTextFieldItem(
                            value = renamePlanName,
                            onValueChange = { renamePlanName = it },
                            label = stringResource(R.string.build_plan_name),
                            placeholder = "",
                            editInDialog = false,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    top.yukonga.miuix.kmp.basic.TextButton(
                        modifier = Modifier.weight(1f),
                        onClick = { renamePlanTarget = null },
                        text = stringResource(R.string.cancel)
                    )
                    top.yukonga.miuix.kmp.basic.TextButton(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            vm.renameBuildPlan(plan.id, renamePlanName)
                            renamePlanTarget = null
                            vm.showSnackbar(context.getString(R.string.build_plan_renamed))
                        },
                        text = stringResource(R.string.build_save),
                        colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.textButtonColorsPrimary()
                    )
                }
            }
        }
    }

    deletePlanTarget?.let { plan ->
        WindowDialog(
            show = true,
            title = stringResource(R.string.build_delete_plan),
            onDismissRequest = { deletePlanTarget = null }
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        top.yukonga.miuix.kmp.basic.Text(
                            text = stringResource(R.string.build_delete_plan_confirm, plan.name),
                            style = MiuixTheme.textStyles.body1
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    top.yukonga.miuix.kmp.basic.TextButton(
                        modifier = Modifier.weight(1f),
                        onClick = { deletePlanTarget = null },
                        text = stringResource(R.string.cancel)
                    )
                    top.yukonga.miuix.kmp.basic.TextButton(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            vm.deleteBuildPlan(plan.id)
                            deletePlanTarget = null
                            vm.showSnackbar(context.getString(R.string.build_plan_deleted))
                        },
                        text = stringResource(R.string.delete),
                        colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.textButtonColors(
                            color = MiuixTheme.colorScheme.error
                        )
                    )
                }
            }
        }
    }

    Scaffold(


        topBar = {
            BlurredBar(backdrop, surfaceColor) {
                TopAppBar(
                    color = barColor,
                    title = stringResource(R.string.build_plan_library),
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        top.yukonga.miuix.kmp.basic.IconButton(onClick = { navigator.pop() }) {
                            top.yukonga.miuix.kmp.basic.Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.build_back_to_config)
                            )
                        }
                    }
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier.then(
                if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier
            )
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
                .overScrollVertical()
                .scrollEndHaptic()
                .padding(horizontal = BuildPageHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(padding.calculateTopPadding() + BuildPageTopSpacing))
            BuildPlanLibraryPageMiuix(
                plans = state.buildPlans,
                onApply = {
                    vm.applyBuildPlan(it)
                    navigator.pop()
                    vm.showSnackbar(context.getString(R.string.build_plan_applied_edit))
                },
                onShare = { sharePlanTarget = it },
                onRename = {
                    renamePlanTarget = it
                    renamePlanName = it.name
                },
                onDelete = { deletePlanTarget = it }
            )
            Spacer(Modifier.height(BuildPageBottomSpacing))
        }
        }
    }
}

@Composable
fun BuildQueueScreenMiuix(vm: MainViewModel) {
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val scrollBehavior = MiuixScrollBehavior()
    val surfaceColor = MiuixTheme.colorScheme.surface
    val backdrop = rememberBlurBackdrop(state.miuixBlurEnabled, surfaceColor)
    val barColor = if (backdrop != null) Color.Transparent else surfaceColor

    Scaffold(


        topBar = {
            BlurredBar(backdrop, surfaceColor) {
                TopAppBar(
                    color = barColor,
                    title = stringResource(R.string.build_queue_title),
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        top.yukonga.miuix.kmp.basic.IconButton(onClick = { navigator.pop() }) {
                            top.yukonga.miuix.kmp.basic.Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.build_back_to_config)
                            )
                        }
                    }
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier.then(
                if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier
            )
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
                .overScrollVertical()
                .scrollEndHaptic()
                .padding(horizontal = BuildPageHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(padding.calculateTopPadding() + BuildPageTopSpacing))
            BuildQueuePageMiuix(
                queue = state.buildQueue,
                cancellingRunIds = state.cancellingWorkflowRunIds,
                onApply = {
                    vm.updateBuildConfig(it.config)
                    navigator.pop()
                    vm.showSnackbar(context.getString(R.string.build_queue_applied))
                },
                onRemove = { vm.removeBuildQueueItem(it.id) },
                onRetry = { vm.retryBuildQueueItem(it.id) },
                onCancelRun = { runId -> vm.cancelWorkflowRun(runId) },
                onClearCompleted = vm::clearCompletedBuildQueueItems
            )
            Spacer(Modifier.height(BuildPageBottomSpacing))
        }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// Private composable helpers
// ═════════════════════════════════════════════════════════════════════════════
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun SectionTitle(title: String) {
    top.yukonga.miuix.kmp.basic.Text(
        text = title,
        style = MiuixTheme.textStyles.subtitle,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp)
    )
}

@Composable
private fun BuildHeroCardMiuix(
    title: String,
    subtitle: String,
    isActivated: Boolean,
    themeMode: String,
) {
    val isDark = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val containerColor = if (isActivated) {
        if (isDark) Color(0xFF193822) else Color(0xFFDDF5E6)
    } else {
        if (isDark) Color(0xFF381A18) else Color(0xFFF9EEEC)
    }
    val contentColor = if (isDark) Color.White else Color(0xFF1A1A1A)
    val descColor = if (isDark) Color.White.copy(alpha = 0.8f) else Color(0xFF1A1A1A).copy(alpha = 0.8f)
    val bgIconTint = if (isActivated) Color(0xFF35D267) else Color(0xFFD03636)

    Card(
        colors = CardDefaults.defaultColors(color = containerColor),
        modifier = Modifier.fillMaxWidth(),
        pressFeedbackType = PressFeedbackType.Tilt
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .offset(50.dp, 38.dp),
                contentAlignment = Alignment.BottomEnd
            ) {
                top.yukonga.miuix.kmp.basic.Icon(
                    modifier = Modifier.size(170.dp),
                    imageVector = if (isActivated) Icons.Rounded.CheckCircleOutline else Icons.Rounded.ErrorOutline,
                    tint = bgIconTint,
                    contentDescription = null
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 51.dp)
            ) {
                top.yukonga.miuix.kmp.basic.Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor
                )
                Spacer(Modifier.height(4.dp))
                top.yukonga.miuix.kmp.basic.Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = subtitle,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = descColor
                )
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun BuildPlanHeroMiuix(
    config: KernelBuildConfig,
    recommended: KernelBuildConfig?,
    status: BuildStatus,
    themeMode: String,
) {
    val isDark = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val containerColor = if (isDark) Color(0xFF1A2A40) else Color(0xFFD8E8F8)
    val contentColor = if (isDark) Color.White else Color(0xFF1A1A1A)
    val descColor = if (isDark) Color.White.copy(alpha = 0.8f) else Color(0xFF1A1A1A).copy(alpha = 0.8f)

    if (config.buildTarget == BUILD_TARGET_ONEPLUS) {
        Card(
            colors = CardDefaults.defaultColors(color = containerColor),
            modifier = Modifier.fillMaxWidth(),
            pressFeedbackType = PressFeedbackType.Tilt
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier.matchParentSize().offset(50.dp, 38.dp),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    top.yukonga.miuix.kmp.basic.Icon(
                        modifier = Modifier.size(170.dp),
                        imageVector = Icons.Default.PhoneAndroid,
                        tint = Color(0xFF4A90D9),
                        contentDescription = null
                    )
                }
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    top.yukonga.miuix.kmp.basic.Text(
                        text = KernelSupport.onePlusDeviceLabel(config.onePlusDeviceManifest),
                        fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = contentColor
                    )
                    Spacer(Modifier.height(2.dp))
                    top.yukonga.miuix.kmp.basic.Text(
                        text = stringResource(R.string.build_oneplus_hero_desc),
                        fontSize = 14.sp, color = descColor, minLines = 2
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        BuildStatusChipMiuix(ksuVariantDisplayName(config.kernelsuVariant))
                        BuildStatusChipMiuix("${config.kernelVersion} · ${config.androidVersion}")
                        BuildStatusChipMiuix(
                            if (!config.cancelSusfs) stringResource(R.string.build_susfs_on) else stringResource(R.string.build_susfs_off)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    BuildHeroDetailsRowMiuix(
                        details = listOf(
                            stringResource(R.string.build_oneplus_cpu) to config.onePlusCpu,
                            stringResource(R.string.build_android_version) to config.androidVersion,
                            stringResource(R.string.build_kernel_version) to config.kernelVersion,
                        ),
                        contentColor = contentColor,
                        descColor = descColor,
                        isDark = isDark,
                    )
                    Spacer(Modifier.height(2.dp))
                }
            }
        }
        return
    }

    val isRecommended = recommended != null &&
        config.androidVersion == recommended.androidVersion &&
        config.kernelVersion == recommended.kernelVersion &&
        config.subLevel == recommended.subLevel &&
        config.osPatchLevel == recommended.osPatchLevel

    Card(
        colors = CardDefaults.defaultColors(color = containerColor),
        modifier = Modifier.fillMaxWidth(),
        pressFeedbackType = PressFeedbackType.Tilt
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier.matchParentSize().offset(50.dp, 38.dp),
                contentAlignment = Alignment.BottomEnd
            ) {
                top.yukonga.miuix.kmp.basic.Icon(
                    modifier = Modifier.size(170.dp),
                    imageVector = Icons.Default.RocketLaunch,
                    tint = Color(0xFF4A90D9),
                    contentDescription = null
                )
            }
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                top.yukonga.miuix.kmp.basic.Text(
                    text = "${config.kernelVersion}.${config.subLevel} · ${config.androidVersion.removePrefix("android").let { "Android $it" }}",
                    fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = contentColor
                )
                Spacer(Modifier.height(2.dp))
                top.yukonga.miuix.kmp.basic.Text(
                    text = stringResource(R.string.build_hero_desc),
                    fontSize = 14.sp, color = descColor, minLines = 2
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BuildStatusChipMiuix(ksuVariantDisplayName(config.kernelsuVariant))
                    BuildStatusChipMiuix(
                        if (!config.cancelSusfs) stringResource(R.string.build_susfs_on) else stringResource(R.string.build_susfs_off)
                    )
                    BuildStatusChipMiuix(
                        if (isRecommended) stringResource(R.string.build_device_recommended) else buildStatusLabel(status)
                    )
                }
                Spacer(Modifier.height(10.dp))
                BuildHeroDetailsRowMiuix(
                    details = listOf(
                        stringResource(R.string.build_android_version) to config.androidVersion,
                        stringResource(R.string.build_kernel_version) to "${config.kernelVersion}.${config.subLevel}",
                        stringResource(R.string.build_kernelsu_variant) to ksuVariantDisplayName(config.kernelsuVariant),
                    ),
                    contentColor = contentColor,
                    descColor = descColor,
                    isDark = isDark,
                )
                Spacer(Modifier.height(2.dp))
            }
        }
    }
}

@Composable
private fun BuildHeroDetailsRowMiuix(
    details: List<Pair<String, String>>,
    contentColor: Color,
    descColor: Color,
    isDark: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        details.forEach { (title, value) ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 58.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.42f))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.Center
            ) {
                top.yukonga.miuix.kmp.basic.Text(
                    text = title,
                    style = MiuixTheme.textStyles.body2,
                    color = descColor,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                top.yukonga.miuix.kmp.basic.Text(
                    text = value,
                    style = MiuixTheme.textStyles.body2,
                    color = contentColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun BuildStatusChipMiuix(label: String) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        top.yukonga.miuix.kmp.basic.Text(
            text = label,
            style = MiuixTheme.textStyles.body2,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun BuildPlanToolsCardMiuix(
    plansCount: Int,
    pendingQueueCount: Int,
    activeQueueCount: Int,
    expanded: Boolean,
    currentSummary: String,
    onExpandedChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onLibrary: () -> Unit,
    onQueue: () -> Unit,
    onShare: () -> Unit,
    onImport: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        BasicComponent(
            title = stringResource(R.string.build_plan_tools_title),
            summary = currentSummary,
            startAction = {
                top.yukonga.miuix.kmp.basic.Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp),
                )
            },
            endActions = {
                top.yukonga.miuix.kmp.basic.Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) {
                        stringResource(R.string.build_collapse_plan_tools)
                    } else {
                        stringResource(R.string.build_expand_plan_tools)
                    },
                    tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                    modifier = Modifier.size(20.dp),
                )
            },
            onClick = { onExpandedChange(!expanded) },
        )

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
        ) {
            Column {
                HorizontalDivider()
                ArrowPreference(
                    title = stringResource(R.string.build_library),
                    summary = if (plansCount > 0) {
                        stringResource(R.string.build_saved_plans_count, plansCount)
                    } else {
                        stringResource(R.string.build_no_saved_plans)
                    },
                    startAction = {
                        top.yukonga.miuix.kmp.basic.Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onSurfaceSecondary,
                        )
                    },
                    onClick = onLibrary,
                )
                ArrowPreference(
                    title = stringResource(R.string.build_queue_short),
                    summary = if (activeQueueCount > 0) {
                        stringResource(R.string.build_queue_summary, activeQueueCount, pendingQueueCount)
                    } else {
                        stringResource(R.string.build_queue_empty)
                    },
                    startAction = {
                        top.yukonga.miuix.kmp.basic.Icon(
                            imageVector = Icons.Default.Queue,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onSurfaceSecondary,
                        )
                    },
                    onClick = onQueue,
                )
                BasicComponent(
                    title = stringResource(R.string.build_save),
                    startAction = {
                        top.yukonga.miuix.kmp.basic.Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onSurfaceSecondary,
                        )
                    },
                    onClick = onSave,
                )
                BasicComponent(
                    title = stringResource(R.string.build_share),
                    startAction = {
                        top.yukonga.miuix.kmp.basic.Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onSurfaceSecondary,
                        )
                    },
                    onClick = onShare,
                )
                BasicComponent(
                    title = stringResource(R.string.build_import),
                    startAction = {
                        top.yukonga.miuix.kmp.basic.Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onSurfaceSecondary,
                        )
                    },
                    onClick = onImport,
                )
            }
        }
    }
}

@Composable
private fun BuildTargetSelectorMiuix(
    selected: String,
    onSelect: (String) -> Unit,
) {
    val targets = listOf(BUILD_TARGET_GKI, BUILD_TARGET_ONEPLUS)
    val targetLabels = listOf(
        buildTargetLabel(BUILD_TARGET_GKI),
        buildTargetLabel(BUILD_TARGET_ONEPLUS),
    )
    val selectedIndex = targets.indexOf(selected).coerceAtLeast(0)
    TabRow(
        tabs = targetLabels,
        selectedTabIndex = selectedIndex,
        onTabSelected = { index -> onSelect(targets[index]) },
        modifier = Modifier.fillMaxWidth(),
        height = 48.dp,
    )
}

@Composable
private fun BuildKindProgressBlockMiuix(
    title: String,
    status: BuildStatus,
    progress: BuildProgress,
    currentRun: WorkflowRun?,
    activeRunCount: Int,
    cancellingRunIds: Set<Long>,
    runningChips: List<BuildRunChip>,
    queuedChips: List<BuildRunChip>,
    onCancel: (Long) -> Unit,
) {
    if (status == BuildStatus.IDLE && currentRun == null && runningChips.isEmpty() && queuedChips.isEmpty()) return

    val statusColor = when (status) {
        BuildStatus.QUEUED -> MiuixTheme.colorScheme.outline
        BuildStatus.IN_PROGRESS -> MiuixTheme.colorScheme.primary
        BuildStatus.SUCCESS -> MiuixTheme.colorScheme.primary
        BuildStatus.FAILURE -> MiuixTheme.colorScheme.error
        BuildStatus.CANCELLED -> MiuixTheme.colorScheme.onSurfaceSecondary
        else -> MiuixTheme.colorScheme.onSurfaceSecondary
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                top.yukonga.miuix.kmp.basic.Icon(
                    imageVector = Icons.Default.RunCircle,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(8.dp))
                top.yukonga.miuix.kmp.basic.Text(
                    text = title,
                    style = MiuixTheme.textStyles.subtitle,
                    fontWeight = FontWeight.SemiBold
                )
            }

            val bannerText = when (status) {
                BuildStatus.QUEUED -> if (activeRunCount > 1) {
                    stringResource(R.string.build_multiple_queued, activeRunCount)
                } else {
                    stringResource(R.string.build_queued_waiting)
                }
                BuildStatus.IN_PROGRESS -> if (activeRunCount > 1) {
                    stringResource(R.string.build_multiple_running, activeRunCount)
                } else {
                    stringResource(R.string.build_running_ellipsis)
                }
                BuildStatus.SUCCESS -> stringResource(R.string.build_success_bang)
                BuildStatus.FAILURE -> stringResource(R.string.build_failed)
                BuildStatus.CANCELLED -> stringResource(R.string.build_cancelled)
                else -> ""
            }
            if (bannerText.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    top.yukonga.miuix.kmp.basic.Text(
                        text = bannerText,
                        style = MiuixTheme.textStyles.body1,
                        color = statusColor,
                        fontWeight = FontWeight.Medium
                    )
                    if (progress.totalSteps > 0) {
                        top.yukonga.miuix.kmp.basic.Text(
                            text = "${progress.percent}% ${progress.currentStep}",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary,
                            maxLines = 1
                        )
                    }
                    if (status in listOf(BuildStatus.QUEUED, BuildStatus.IN_PROGRESS) &&
                        (currentRun?.id ?: 0L) > 0L && activeRunCount <= 1
                    ) {
                        val cancelling = (currentRun?.id ?: 0L) in cancellingRunIds
                        Spacer(Modifier.weight(1f))
                        top.yukonga.miuix.kmp.basic.TextButton(
                            modifier = Modifier.widthIn(min = 96.dp).height(40.dp),
                            text = if (cancelling) {
                                stringResource(R.string.status_cancelling)
                            } else {
                                stringResource(R.string.status_cancel)
                            },
                            enabled = (currentRun?.id ?: 0L) > 0L && !cancelling,
                            onClick = { onCancel(currentRun?.id ?: 0L) }
                        )
                    }
                }
            }

            if (progress.totalSteps > 0) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    progress = (progress.percent / 100f).coerceIn(0f, 1f),
                    colors = ProgressIndicatorDefaults.progressIndicatorColors(
                        foregroundColor = statusColor,
                        backgroundColor = MiuixTheme.colorScheme.surface
                    )
                )
            }

            if (runningChips.isNotEmpty() || queuedChips.isNotEmpty()) {
                if (runningChips.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        runningChips.forEach { chip -> BuildRunChipViewMiuix(chip) }
                    }
                }
                if (queuedChips.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        queuedChips.forEach { chip -> BuildRunChipViewMiuix(chip) }
                    }
                }
            } else if (progress.currentStep.isNotBlank()) {
                top.yukonga.miuix.kmp.basic.Text(
                    text = progress.currentStep,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    maxLines = 2
                )
            }
        }
    }
}

@Composable
private fun BuildRunChipViewMiuix(chip: BuildRunChip) {
    val containerColor = if (chip.running) {
        MiuixTheme.colorScheme.surfaceVariant
    } else {
        MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(containerColor)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        top.yukonga.miuix.kmp.basic.Text(
            text = chip.text,
            style = MiuixTheme.textStyles.body2,
            maxLines = 1
        )
    }
}

@Composable
private fun BuildTextFieldItem(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    editInDialog: Boolean = true,
) {
    if (!editInDialog) {
        InlineBuildTextFieldItem(
            value = value,
            onValueChange = onValueChange,
            label = label,
            placeholder = placeholder,
        )
        return
    }

    var showEditor by remember { mutableStateOf(false) }

    val hasValue = value.isNotBlank()
    val summary = when {
        hasValue -> value
        placeholder.isNotBlank() -> placeholder
        else -> null
    }

    BasicComponent(
        title = label,
        summary = summary,
        summaryColor = BasicComponentDefaults.summaryColor(
            color = if (hasValue) {
                MiuixTheme.colorScheme.onSurfaceSecondary
            } else {
                MiuixTheme.colorScheme.onSurfaceVariantSummary
            }
        ),
        endActions = {
            top.yukonga.miuix.kmp.basic.Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                modifier = Modifier.size(20.dp)
            )
        },
        onClick = { showEditor = true }
    )

    if (showEditor) {
        MiuixTextInputDialog(
            show = true,
            title = label,
            message = placeholder,
            value = value,
            cancelText = stringResource(R.string.cancel),
            confirmText = stringResource(R.string.confirm),
            onDismiss = { showEditor = false },
            onConfirm = { editedValue ->
                onValueChange(editedValue)
                showEditor = false
            },
        )
    }
}

@Composable
private fun InlineBuildTextFieldItem(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        top.yukonga.miuix.kmp.basic.Text(
            text = label,
            style = MiuixTheme.textStyles.body1,
            fontWeight = FontWeight.Medium
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MiuixTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(17.dp)
                )
                .border(
                    width = 1.dp,
                    color = MiuixTheme.colorScheme.primary.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(17.dp)
                )
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions.Default,
                textStyle = MiuixTheme.textStyles.body1.copy(
                    color = MiuixTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (value.isEmpty() && placeholder.isNotEmpty()) {
                            top.yukonga.miuix.kmp.basic.Text(
                                text = placeholder,
                                style = MiuixTheme.textStyles.body1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                            )
                        }
                        innerTextField()
                    }
                }
            )
        }
    }
}

@Composable
private fun ConfigPreviewItemMiuix(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    preview: String,
) {
    BasicComponent(
        title = title,
        summary = preview,
        startAction = {
            top.yukonga.miuix.kmp.basic.Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurfaceSecondary
            )
        }
    )
}

// ═════════════════════════════════════════════════════════════════════════════
// Child pages
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun BuildPlanLibraryPageMiuix(
    plans: List<BuildPlan>,
    onApply: (BuildPlan) -> Unit,
    onShare: (BuildPlan) -> Unit,
    onRename: (BuildPlan) -> Unit,
    onDelete: (BuildPlan) -> Unit,
) {
    if (plans.isEmpty()) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                top.yukonga.miuix.kmp.basic.Text(
                    text = stringResource(R.string.build_no_plans),
                    style = MiuixTheme.textStyles.subtitle,
                    fontWeight = FontWeight.SemiBold
                )
                top.yukonga.miuix.kmp.basic.Text(
                    text = stringResource(R.string.build_no_plans_desc),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary
                )
                top.yukonga.miuix.kmp.basic.Text(
                    text = stringResource(R.string.build_no_plans_hint),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
        }
    } else {
        plans.forEach { plan ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    top.yukonga.miuix.kmp.basic.Text(
                        text = plan.name,
                        style = MiuixTheme.textStyles.subtitle,
                        fontWeight = FontWeight.SemiBold
                    )
                    top.yukonga.miuix.kmp.basic.Text(
                        text = buildPlanSummary(plan.config),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        top.yukonga.miuix.kmp.basic.Button(
                            onClick = { onApply(plan) },
                            modifier = Modifier.weight(1f),
                            insideMargin = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            minWidth = 0.dp,
                            minHeight = 0.dp
                        ) {
                            top.yukonga.miuix.kmp.basic.Text(
                                text = stringResource(R.string.build_apply_edit),
                                maxLines = 1,
                                fontSize = 14.sp
                            )
                        }
                        top.yukonga.miuix.kmp.basic.TextButton(
                            text = stringResource(R.string.build_share),
                            modifier = Modifier.weight(1f),
                            onClick = { onShare(plan) }
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        top.yukonga.miuix.kmp.basic.TextButton(
                            text = stringResource(R.string.build_rename),
                            modifier = Modifier.weight(1f),
                            onClick = { onRename(plan) }
                        )
                        top.yukonga.miuix.kmp.basic.TextButton(
                            text = stringResource(R.string.delete),
                            modifier = Modifier.weight(1f),
                            onClick = { onDelete(plan) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BuildQueuePageMiuix(
    queue: List<BuildQueueItem>,
    cancellingRunIds: Set<Long>,
    onApply: (BuildQueueItem) -> Unit,
    onRemove: (BuildQueueItem) -> Unit,
    onRetry: (BuildQueueItem) -> Unit,
    onCancelRun: (Long) -> Unit,
    onClearCompleted: () -> Unit,
) {
    val terminalItems = queue.filter { it.status.isTerminalQueueStatus() }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                top.yukonga.miuix.kmp.basic.Icon(
                    imageVector = Icons.Default.Queue,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    top.yukonga.miuix.kmp.basic.Text(
                        text = stringResource(R.string.build_queue_status),
                        style = MiuixTheme.textStyles.subtitle,
                        fontWeight = FontWeight.SemiBold
                    )
                    top.yukonga.miuix.kmp.basic.Text(
                        text = if (queue.isEmpty()) {
                            stringResource(R.string.build_queue_status_desc)
                        } else {
                            stringResource(R.string.build_queue_status_count, queue.size, queue.count { it.status == BuildQueueItemStatus.PENDING })
                        },
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary
                    )
                }
            }
            if (terminalItems.isNotEmpty()) {
                top.yukonga.miuix.kmp.basic.TextButton(
                    text = stringResource(R.string.build_clear_finished),
                    onClick = onClearCompleted
                )
            }
        }
    }

    if (queue.isEmpty()) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                top.yukonga.miuix.kmp.basic.Text(
                    text = stringResource(R.string.build_no_queue_items),
                    style = MiuixTheme.textStyles.subtitle,
                    fontWeight = FontWeight.SemiBold
                )
                top.yukonga.miuix.kmp.basic.Text(
                    text = stringResource(R.string.build_no_queue_items_desc),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary
                )
                top.yukonga.miuix.kmp.basic.Text(
                    text = stringResource(R.string.build_queue_hint),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
        }
    } else {
        queue.forEachIndexed { index, item ->
            BuildQueueItemCardMiuix(
                index = index,
                item = item,
                cancelling = item.runId > 0L && item.runId in cancellingRunIds,
                onApply = { onApply(item) },
                onRemove = { onRemove(item) },
                onRetry = { onRetry(item) },
                onCancelRun = { if (item.runId > 0L) onCancelRun(item.runId) }
            )
        }
    }
}

@Composable
private fun BuildQueueItemCardMiuix(
    index: Int,
    item: BuildQueueItem,
    cancelling: Boolean,
    onApply: () -> Unit,
    onRemove: () -> Unit,
    onRetry: () -> Unit,
    onCancelRun: () -> Unit,
) {
    val statusIcon = when (item.status) {
        BuildQueueItemStatus.PENDING -> Icons.Default.Schedule
        BuildQueueItemStatus.DISPATCHING -> Icons.Default.RunCircle
        BuildQueueItemStatus.RUNNING -> Icons.Default.RunCircle
        BuildQueueItemStatus.DONE -> Icons.Default.CheckCircle
        BuildQueueItemStatus.FAILED -> Icons.Default.Error
        BuildQueueItemStatus.CANCELLED -> Icons.Default.Cancel
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                top.yukonga.miuix.kmp.basic.Icon(
                    imageVector = statusIcon,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    top.yukonga.miuix.kmp.basic.Text(
                        text = "${index + 1}. ${item.name.ifBlank { stringResource(R.string.build_queue_item) }}",
                        style = MiuixTheme.textStyles.subtitle,
                        fontWeight = FontWeight.SemiBold
                    )
                    top.yukonga.miuix.kmp.basic.Text(
                        text = buildPlanSummary(item.config),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                BuildStatusChipMiuix(item.status.queueStatusLabelMiuix())
                if (item.runNumber > 0) BuildStatusChipMiuix("#${item.runNumber}")
                if (item.runId > 0L) BuildStatusChipMiuix(stringResource(R.string.build_status_run_id, item.runId))
            }

            item.error?.let {
                top.yukonga.miuix.kmp.basic.Text(
                    text = it,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.error
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                top.yukonga.miuix.kmp.basic.Button(
                    onClick = onApply,
                    modifier = Modifier.weight(1f).height(42.dp)
                ) {
                    top.yukonga.miuix.kmp.basic.Text(stringResource(R.string.build_apply))
                }
                when (item.status) {
                    BuildQueueItemStatus.PENDING -> top.yukonga.miuix.kmp.basic.TextButton(
                        text = stringResource(R.string.build_remove),
                        modifier = Modifier.weight(1f).height(42.dp),
                        onClick = onRemove
                    )
                    BuildQueueItemStatus.DISPATCHING,
                    BuildQueueItemStatus.RUNNING -> top.yukonga.miuix.kmp.basic.TextButton(
                        text = if (cancelling) stringResource(R.string.status_cancelling) else stringResource(R.string.status_cancel),
                        modifier = Modifier.weight(1f).height(42.dp),
                        enabled = item.runId > 0L && !cancelling,
                        onClick = onCancelRun
                    )
                    BuildQueueItemStatus.FAILED,
                    BuildQueueItemStatus.CANCELLED -> top.yukonga.miuix.kmp.basic.Button(
                        onClick = onRetry,
                        modifier = Modifier.weight(1f).height(42.dp)
                    ) {
                        top.yukonga.miuix.kmp.basic.Text(stringResource(R.string.retry))
                    }
                    BuildQueueItemStatus.DONE -> top.yukonga.miuix.kmp.basic.TextButton(
                        text = stringResource(R.string.clear),
                        modifier = Modifier.weight(1f).height(42.dp),
                        onClick = onRemove
                    )
                }
            }
        }
    }
}

@Composable
private fun BuildQueueItemStatus.queueStatusLabelMiuix(): String = when (this) {
    BuildQueueItemStatus.PENDING -> stringResource(R.string.build_queue_pending)
    BuildQueueItemStatus.DISPATCHING -> stringResource(R.string.build_queue_dispatching)
    BuildQueueItemStatus.RUNNING -> stringResource(R.string.status_in_progress)
    BuildQueueItemStatus.DONE -> stringResource(R.string.build_queue_done)
    BuildQueueItemStatus.FAILED -> stringResource(R.string.status_failure)
    BuildQueueItemStatus.CANCELLED -> stringResource(R.string.status_cancelled_label)
}

private fun BuildQueueItemStatus.isTerminalQueueStatus(): Boolean =
    this in setOf(BuildQueueItemStatus.DONE, BuildQueueItemStatus.FAILED, BuildQueueItemStatus.CANCELLED)

// ═════════════════════════════════════════════════════════════════════════════
// Dialog composables (MD3 components — dialogs are not themed by MIUIX)
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun SaveBuildPlanDialog(
    name: String,
    onNameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    WindowDialog(
        show = true,
        title = stringResource(R.string.build_save_plan),
        onDismissRequest = onDismiss
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            BuildTextFieldItem(
                value = name,
                onValueChange = onNameChange,
                label = stringResource(R.string.build_plan_name),
                placeholder = "",
                editInDialog = false,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                top.yukonga.miuix.kmp.basic.TextButton(
                    modifier = Modifier.weight(1f),
                    onClick = onDismiss,
                    text = stringResource(R.string.cancel)
                )
                top.yukonga.miuix.kmp.basic.TextButton(
                    modifier = Modifier.weight(1f),
                    onClick = onConfirm,
                    text = stringResource(R.string.build_save),
                    colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.textButtonColorsPrimary()
                )
            }
        }
    }
}

@Composable
private fun ImportBuildPlanDialog(
    code: String,
    preview: BuildPlanImportPreview?,
    error: String?,
    onCodeChange: (String) -> Unit,
    onParse: () -> Unit,
    onApply: (BuildPlanImportPreview) -> Unit,
    onSave: (BuildPlanImportPreview) -> Unit,
    onDismiss: () -> Unit,
) {
    WindowDialog(
        show = true,
        title = stringResource(R.string.build_import_plan),
        onDismissRequest = onDismiss
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    BuildTextFieldItem(
                        value = code,
                        onValueChange = onCodeChange,
                        label = stringResource(R.string.build_abkp2_code),
                        placeholder = stringResource(R.string.build_abkp2_placeholder),
                        editInDialog = false,
                    )
                    error?.let {
                        Spacer(Modifier.height(8.dp))
                        top.yukonga.miuix.kmp.basic.Text(
                            text = it,
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.error
                        )
                    }
                    preview?.let {
                        Spacer(Modifier.height(8.dp))
                        top.yukonga.miuix.kmp.basic.Text(
                            text = it.plan.name,
                            style = MiuixTheme.textStyles.subtitle,
                            fontWeight = FontWeight.SemiBold
                        )
                        top.yukonga.miuix.kmp.basic.Text(
                            text = buildPlanSummary(it.plan.config),
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                top.yukonga.miuix.kmp.basic.TextButton(
                    modifier = Modifier.weight(1f),
                    onClick = onDismiss,
                    text = stringResource(R.string.cancel)
                )
                if (preview == null) {
                    top.yukonga.miuix.kmp.basic.TextButton(
                        modifier = Modifier.weight(1f),
                        onClick = onParse,
                        text = stringResource(R.string.build_parse),
                        colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.textButtonColorsPrimary()
                    )
                } else {
                    top.yukonga.miuix.kmp.basic.TextButton(
                        modifier = Modifier.weight(1f),
                        onClick = { onSave(preview) },
                        text = stringResource(R.string.build_save)
                    )
                    top.yukonga.miuix.kmp.basic.TextButton(
                        modifier = Modifier.weight(1f),
                        onClick = { onApply(preview) },
                        text = stringResource(R.string.build_apply),
                        colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.textButtonColorsPrimary()
                    )
                }
            }
        }
    }
}

@Composable
private fun ShareBuildPlanScopeDialog(
    plan: BuildPlan,
    onDismiss: () -> Unit,
    onShare: (BuildPlanShareScope) -> Unit,
) {
    WindowDialog(
        show = true,
        title = stringResource(R.string.build_share_plan),
        onDismissRequest = onDismiss
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    top.yukonga.miuix.kmp.basic.Text(
                        text = plan.name.ifBlank { stringResource(R.string.build_current_plan) },
                        style = MiuixTheme.textStyles.subtitle,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    top.yukonga.miuix.kmp.basic.Text(
                        text = buildPlanSummary(plan.config),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                    Spacer(Modifier.height(8.dp))
                    top.yukonga.miuix.kmp.basic.Text(
                        text = stringResource(R.string.build_share_plan_desc),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                top.yukonga.miuix.kmp.basic.TextButton(
                    modifier = Modifier.weight(1f),
                    onClick = { onShare(BuildPlanShareScope.FEATURES_ONLY) },
                    text = stringResource(R.string.build_features_only)
                )
                top.yukonga.miuix.kmp.basic.TextButton(
                    modifier = Modifier.weight(1f),
                    onClick = { onShare(BuildPlanShareScope.FULL) },
                    text = stringResource(R.string.build_full_plan),
                    colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.textButtonColorsPrimary()
                )
            }
        }
    }
}

@Composable
private fun RenameBuildPlanDialog(
    name: String,
    onNameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Edit, null) },
        title = { Text(stringResource(R.string.build_rename_plan)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                label = { Text(stringResource(R.string.build_plan_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = onConfirm) { Text(stringResource(R.string.build_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun DeleteBuildPlanDialog(
    plan: BuildPlan,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Delete, null) },
        title = { Text(stringResource(R.string.build_delete_plan)) },
        text = { Text(stringResource(R.string.build_delete_plan_confirm, plan.name)) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = androidx.compose.material3.MaterialTheme.colorScheme.error,
                    contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onError
                )
            ) {
                Text(stringResource(R.string.delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

// ═════════════════════════════════════════════════════════════════════════════
// Private helper functions (duplicated from MD3 BuildScreen.kt)
// ═════════════════════════════════════════════════════════════════════════════

private fun copyTextToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}

@Composable
private fun buildPlanSummary(config: KernelBuildConfig): String {
    if (config.buildTarget == BUILD_TARGET_ONEPLUS) {
        val enabled = mutableListOf<String>()
        if (!config.cancelSusfs) enabled += "SUSFS"
        if (config.onePlusUseLz4kd) enabled += "lz4kd"
        if (config.useBbg) enabled += "BBG"
        if (config.useKpm) enabled += "KPM"
        if (config.onePlusUseBbr) enabled += "BBR"
        if (config.onePlusUseProxyOptimization) enabled += stringResource(R.string.build_oneplus_proxy_short)
        if (config.onePlusUseUnicodeBypass) enabled += stringResource(R.string.build_oneplus_unicode_short)
        val featureSummary = enabled.ifEmpty { listOf(stringResource(R.string.build_base_config)) }.joinToString("、")
        return "${buildTargetLabel(config.buildTarget)} · ${KernelSupport.onePlusDeviceLabel(config.onePlusDeviceManifest)}\n" +
            "${config.kernelVersion} · ${config.androidVersion} · ${ksuVariantDisplayName(config.kernelsuVariant)} · $featureSummary"
    }
    val android = config.androidVersion.removePrefix("android").ifBlank { config.androidVersion }
    val enabled = mutableListOf<String>()
    if (!config.cancelSusfs) enabled += "SUSFS"
    if (config.useZram) enabled += "ZRAM"
    if (config.useBbg) enabled += "BBG"
    if (config.useDdk) enabled += "DDK"
    if (config.useNtsync) enabled += "NTsync"
    if (config.useNetworking) enabled += stringResource(R.string.build_feature_networking)
    if (config.useKpm) enabled += "KPM"
    if (config.useRekernel) enabled += "Re-Kernel"
    if (config.virtualizationSupport != "off") {
        enabled += stringResource(R.string.build_feature_virtualization, virtualizationSupportLabel(config.virtualizationSupport))
    }
    val featureSummary = enabled.ifEmpty { listOf(stringResource(R.string.build_base_config)) }.joinToString("、")
    val externalModuleCount = if (config.useCustomExternalModules) config.customExternalModules.size else 0
    val ksuSummary = when {
        config.kernelsuVariant == KSU_VARIANT_NONE -> ksuVariantDisplayName(config.kernelsuVariant)
        config.kernelsuBranch == KSU_BRANCH_CUSTOM && config.customRef.isNotBlank() ->
            "${config.kernelsuVariant} / ${config.kernelsuBranch} / ${config.customRef}"
        else -> "${config.kernelsuVariant} / ${config.kernelsuBranch}"
    }
    return "${config.kernelVersion}.${config.subLevel} · Android $android · ${config.osPatchLevel}\n" +
        "$ksuSummary · $featureSummary · ${stringResource(R.string.build_summary_external_modules, externalModuleCount)}"
}

@Composable
private fun buildTargetLabel(target: String): String = when (target) {
    BUILD_TARGET_ONEPLUS -> stringResource(R.string.build_target_oneplus)
    else -> stringResource(R.string.build_target_gki)
}

@Composable
private fun virtualizationSupportLabel(value: String): String = when (value) {
    "off" -> stringResource(R.string.build_virtualization_off)
    "on" -> stringResource(R.string.build_virtualization_on)
    "678" -> stringResource(R.string.build_virtualization_slot_678)
    "123" -> stringResource(R.string.build_virtualization_slot_123)
    "345" -> stringResource(R.string.build_virtualization_slot_345)
    else -> value
}

@Composable
private fun ksuVariantDisplayName(variant: String): String =
    if (variant == KSU_VARIANT_NONE) stringResource(R.string.build_ksu_none) else variant

@Composable
private fun buildStatusLabel(status: BuildStatus): String = when (status) {
    BuildStatus.IDLE -> stringResource(R.string.build_status_ready)
    BuildStatus.QUEUED -> stringResource(R.string.build_queued)
    BuildStatus.IN_PROGRESS -> stringResource(R.string.build_running)
    BuildStatus.SUCCESS -> stringResource(R.string.build_success)
    BuildStatus.FAILURE -> stringResource(R.string.build_failed)
    BuildStatus.CANCELLED -> stringResource(R.string.build_cancelled)
}

@Composable
private fun buildStatusColor(status: BuildStatus): Color = when (status) {
    BuildStatus.IDLE -> MiuixTheme.colorScheme.onSurfaceSecondary
    BuildStatus.QUEUED -> MiuixTheme.colorScheme.outline
    BuildStatus.IN_PROGRESS -> MiuixTheme.colorScheme.primary
    BuildStatus.SUCCESS -> MiuixTheme.colorScheme.primary
    BuildStatus.FAILURE -> MiuixTheme.colorScheme.error
    BuildStatus.CANCELLED -> MiuixTheme.colorScheme.onSurfaceSecondary
}

private fun buildVersionPreview(context: Context, config: KernelBuildConfig): String {
    val compact = config.version.filterNot { it.isWhitespace() }
    if (compact.isBlank()) return context.getString(R.string.build_preview_default_version)
    val cleanVersion = compact.replace(Regex("""^[0-9]+\.[0-9]+\.[0-9]+"""), "")
    val preview = "${config.kernelVersion}.${config.subLevel}$cleanVersion"
    return context.getString(R.string.build_preview_value, preview)
}

private fun buildTimePreview(context: Context, buildTime: String): String {
    val input = buildTime.trim()
    if (input.isBlank() || input.equals("N", ignoreCase = true)) {
        val sample = ZonedDateTime.now(ZoneOffset.UTC).format(BUILD_TIME_FORMATTER)
        return context.getString(R.string.build_preview_default_time, sample)
    }
    return context.getString(R.string.build_preview_kbuild_time, input)
}

private val BUILD_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss z yyyy", Locale.US)

// ═════════════════════════════════════════════════════════════════════════════
// Private data classes (duplicated from MD3 BuildScreen.kt)
// ═════════════════════════════════════════════════════════════════════════════

private data class BuildRunChip(
    val runId: Long,
    val text: String,
    val running: Boolean,
)

private data class BuildCatalogModule(
    val module: ModuleCatalogItem,
    val sources: List<String>,
)

private data class BuildCustomModuleGroup(
    val url: String,
    val stages: List<String>,
    val catalogModule: BuildCatalogModule?,
    val entryKind: String = CustomExternalModuleEntryKind.MODULE,
    val groupRepoUrl: String = "",
    val childNames: List<String> = emptyList(),
    val groupName: String = "",
) {
    val key: String = if (entryKind == CustomExternalModuleEntryKind.MODULE_SET_CHILD) {
        "set:${groupRepoUrl.trim().lowercase()}"
    } else {
        url.trim().lowercase()
    }
}

private fun mergeBuildCatalogModules(repositories: List<ModuleCatalogRepository>): List<BuildCatalogModule> =
    repositories
        .flatMap { repository ->
            repository.modules.map { module -> repository.name.ifBlank { repository.url } to module }
        }
        .groupBy { (_, module) -> module.repoUrl.trim().lowercase() }
        .values
        .map { entries ->
            BuildCatalogModule(
                module = entries.first().second,
                sources = entries.map { it.first }.distinct()
            )
        }
        .sortedBy { it.module.catalogModuleTitle().lowercase(Locale.ROOT) }

private fun ModuleCatalogItem.catalogModuleTitle(): String =
    name.ifBlank { repoUrl.trim().trimEnd('/').substringAfterLast('/').removeSuffix(".git") }

private fun groupBuildCustomExternalModules(
    modules: List<CustomExternalModule>,
    catalogModuleByUrl: Map<String, BuildCatalogModule>,
): List<BuildCustomModuleGroup> =
    modules
        .mapNotNull { module ->
            val url = module.url.trim()
            if (url.isBlank()) {
                null
            } else {
                module.copy(
                    url = url,
                    stage = CustomExternalModuleStage.normalize(module.stage),
                    entryKind = CustomExternalModuleEntryKind.normalize(module.entryKind),
                    groupRepoUrl = module.groupRepoUrl.trim(),
                    childName = module.childName.trim(),
                    groupName = module.groupName.trim()
                )
            }
        }
        .groupBy { module ->
            if (module.entryKind == CustomExternalModuleEntryKind.MODULE_SET_CHILD) {
                "set:${module.groupRepoUrl.lowercase()}"
            } else {
                module.url.lowercase()
            }
        }
        .values
        .map { entries ->
            val first = entries.first()
            val url = first.url
            val stages = CustomExternalModuleStage.options.filter { stage ->
                entries.any { entry -> entry.stage == stage }
            }
            BuildCustomModuleGroup(
                url = url,
                stages = stages,
                catalogModule = catalogModuleByUrl[
                    if (first.entryKind == CustomExternalModuleEntryKind.MODULE_SET_CHILD) {
                        first.groupRepoUrl.lowercase()
                    } else {
                        url.lowercase()
                    }
                ],
                entryKind = first.entryKind,
                groupRepoUrl = first.groupRepoUrl,
                childNames = entries.mapNotNull { it.childName.takeIf { name -> name.isNotBlank() } }.distinct(),
                groupName = first.groupName
            )
        }
        .sortedWith(
            compareBy<BuildCustomModuleGroup> { it.catalogModule == null }
                .thenBy { it.displayName("External module").lowercase(Locale.ROOT) }
        )

private fun BuildCustomModuleGroup.displayName(defaultName: String): String =
    if (entryKind == CustomExternalModuleEntryKind.MODULE_SET_CHILD && groupName.isNotBlank()) {
        groupName
    } else {
        catalogModule?.module?.catalogModuleTitle()
            ?: url.trim().trimEnd('/').removeSuffix(".git").substringAfterLast('/').ifBlank { defaultName }
    }

private fun BuildCustomModuleGroup.subtitle(noStageLabel: String, sourcePrefix: String): String {
    val stageLabel = stages.joinToString(" + ").ifBlank { noStageLabel }
    val catalog = catalogModule
    return if (catalog != null) {
        buildString {
            append(stageLabel)
            if (childNames.isNotEmpty()) {
                append(" · ")
                append(childNames.joinToString(", "))
            }
            append(" · ")
            append(sourcePrefix.replace("%s", catalog.sources.joinToString(", ")))
            if (catalog.module.version.isNotBlank()) append(" · v${catalog.module.version}")
            appendLine()
            append(catalog.module.description.ifBlank { catalog.module.repoUrl })
        }
    } else {
        buildString {
            append(stageLabel)
            if (childNames.isNotEmpty()) {
                append(" · ")
                append(childNames.joinToString(", "))
            }
            appendLine()
            append(url)
        }
    }
}

private fun buildRunChipsForStatus(
    activeRuns: List<WorkflowRun>,
    queue: List<BuildQueueItem>,
    running: Boolean,
): List<BuildRunChip> {
    val queueByRunId = queue.filter { it.runId > 0L }.associateBy { it.runId }
    return activeRuns
        .asSequence()
        .filter { run ->
            val isRunning = run.status == "in_progress"
            isRunning == running
        }
        .map { run ->
            val label = buildRunChipLabel(run, queueByRunId[run.id])
            BuildRunChip(runId = run.id, text = label, running = running)
        }
        .toList()
}

private fun buildRunChipLabel(run: WorkflowRun, item: BuildQueueItem?): String {
    val runLabel = if (run.runNumber > 0) "#${run.runNumber}" else "#${run.id}"
    if (run.isManagerBuild()) {
        return buildString {
            append(runLabel)
            append(' ')
            append(if (run.isManagerDevBuild()) "Manager Dev" else "Manager")
        }
    }
    val cfg = item?.config
    val variant = cfg?.kernelsuVariant?.takeIf { it != KSU_VARIANT_NONE }.orEmpty()
    val susfs = cfg != null && !cfg.cancelSusfs && cfg.kernelsuVariant != KSU_VARIANT_NONE
    val kernelLabel = if (cfg != null) {
        "${cfg.kernelVersion}.${cfg.subLevel}-${cfg.androidVersion}-${cfg.osPatchLevel}"
    } else ""
    return buildString {
        append(runLabel)
        if (variant.isNotBlank()) append(' ').append(variant)
        if (susfs) append(" SUSFS")
        if (kernelLabel.isNotBlank()) append(' ').append(kernelLabel)
        if (variant.isBlank() && !susfs && kernelLabel.isBlank()) {
            runChipTitleFallback(run, runLabel)?.let { append(' ').append(it) }
        }
    }
}

private fun runChipTitleFallback(run: WorkflowRun, runLabel: String): String? {
    val disallowed = setOf(runLabel, "#${run.id}")
    return listOf(run.displayTitle, run.name)
        .asSequence()
        .mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }
        .map { title ->
            title.removePrefix(runLabel)
                .removePrefix("#${run.id}")
                .trimStart(' ', '-', ':', '·', ',', '#')
                .trim()
        }
        .firstOrNull { title -> title.isNotBlank() && title !in disallowed }
}
