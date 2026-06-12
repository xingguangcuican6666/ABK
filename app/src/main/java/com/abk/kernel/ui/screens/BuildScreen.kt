@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.abk.kernel.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.abk.kernel.R
import com.abk.kernel.data.model.BuildPlan
import com.abk.kernel.data.model.BuildQueueItem
import com.abk.kernel.data.model.BuildQueueItemStatus
import com.abk.kernel.data.model.BuildProgress
import com.abk.kernel.data.model.BuildStepProgress
import com.abk.kernel.data.model.BuildStatus
import com.abk.kernel.data.model.BUILD_TARGET_GKI
import com.abk.kernel.data.model.BUILD_TARGET_ONEPLUS
import com.abk.kernel.data.model.CustomExternalModule
import com.abk.kernel.data.model.CustomExternalModuleEntryKind
import com.abk.kernel.data.model.CustomExternalModuleStage
import com.abk.kernel.data.model.ExternalModuleMetadata
import com.abk.kernel.data.model.KernelSupport
import com.abk.kernel.data.model.KernelBuildConfig
import com.abk.kernel.data.model.KSU_BRANCH_CUSTOM
import com.abk.kernel.data.model.KSU_BRANCH_LATEST
import com.abk.kernel.data.model.KSU_VARIANT_NONE
import com.abk.kernel.data.model.KSU_VARIANT_RESUKISU
import com.abk.kernel.data.model.KSU_VARIANT_SUKISU
import com.abk.kernel.data.model.ModuleCatalogItem
import com.abk.kernel.data.model.ModuleCatalogItemKind
import com.abk.kernel.data.model.ModuleCatalogRepository
import com.abk.kernel.data.model.WorkflowRun
import com.abk.kernel.data.model.isKernelBuild
import com.abk.kernel.data.model.isManagerBuild
import com.abk.kernel.data.model.isManagerDevBuild
import com.abk.kernel.ui.components.AbkScreenHorizontalPadding
import com.abk.kernel.ui.components.AppPageBackground
import com.abk.kernel.ui.components.ObserveChildPageVisibility
import com.abk.kernel.ui.components.childPageOverlayEnterTransition
import com.abk.kernel.ui.components.childPageOverlayExitTransition
import com.abk.kernel.ui.components.childPageScrimExitTransition
import com.abk.kernel.ui.components.rememberChildPageBackController
import com.abk.kernel.ui.components.rememberChildPageOverlayTransition
import com.abk.kernel.ui.components.ExpressiveHeroCard
import com.abk.kernel.ui.components.ShimmerLinearProgress
import com.abk.kernel.ui.components.ExpressiveListItem
import com.abk.kernel.ui.components.ExpressiveSectionCard
import com.abk.kernel.ui.components.ExpressiveStatusChip
import com.abk.kernel.ui.components.ExpressiveSwitchItem
import com.abk.kernel.ui.components.ExpressiveTopBar
import com.abk.kernel.ui.theme.appPageBackgroundColor
import com.abk.kernel.ui.theme.uiSurfaceColor
import com.abk.kernel.viewmodel.BuildPlanImportPreview
import com.abk.kernel.viewmodel.BuildPlanShareScope
import com.abk.kernel.viewmodel.MainViewModel
import coil.compose.AsyncImage
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val CATALOG_MODULE_REMOVE_DELAY_MS = 260L

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BuildScreen(
    vm: MainViewModel,
    outerPadding: PaddingValues = PaddingValues(0.dp),
    onPlanPageVisibleChange: (Boolean) -> Unit = {}
) {
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val rawConfig = state.buildConfig
    val config = remember(rawConfig) { KernelSupport.normalize(rawConfig) }
    val isOnePlusBuild = config.buildTarget == BUILD_TARGET_ONEPLUS
    val recommended = state.recommendedBuildConfig
    val motionScheme = MaterialTheme.motionScheme
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
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
    val buildTimePreview = remember(context, config.buildTime) {
        buildTimePreview(context, config.buildTime)
    }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showSavePlanDialog by remember { mutableStateOf(false) }
    var showImportPlanDialog by remember { mutableStateOf(false) }
    var showPlanLibraryPage by rememberSaveable { mutableStateOf(false) }
    var showBuildQueuePage by rememberSaveable { mutableStateOf(false) }
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
    var removingCustomModuleKeys by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val coroutineScope = rememberCoroutineScope()
    val catalogModules = remember(state.buildModuleRepositories) {
        mergeBuildCatalogModules(state.buildModuleRepositories)
    }
    val catalogModuleByUrl = remember(catalogModules) {
        catalogModules.associateBy { it.module.repoUrl.trim().lowercase() }
    }
    val customModuleGroups = remember(config.customExternalModules, catalogModuleByUrl) {
        groupBuildCustomExternalModules(config.customExternalModules, catalogModuleByUrl)
    }
    val childPageVisible = showPlanLibraryPage || showBuildQueuePage
    val childPageTransition = rememberChildPageOverlayTransition(
        visible = childPageVisible,
        label = "build-child-page"
    )
    val activeBuild = state.buildStatus in listOf(BuildStatus.QUEUED, BuildStatus.IN_PROGRESS)
    val pendingQueueCount = state.buildQueue.count { it.status == BuildQueueItemStatus.PENDING }
    val activeQueueCount = state.buildQueue.count {
        it.status in listOf(
            BuildQueueItemStatus.PENDING,
            BuildQueueItemStatus.DISPATCHING,
            BuildQueueItemStatus.RUNNING
        )
    }

    LaunchedEffect(config, rawConfig) {
        if (config != rawConfig) vm.updateBuildConfig(config)
    }

    fun closeChildPage() {
        showPlanLibraryPage = false
        showBuildQueuePage = false
    }

    val childPageBack = rememberChildPageBackController(
        enabled = childPageVisible,
        predictiveBackEnabled = state.predictiveBackEnabled,
        onBack = ::closeChildPage,
    )

    fun openPlanLibraryPage() {
        childPageBack.resetProgress()
        showBuildQueuePage = false
        showPlanLibraryPage = true
    }

    fun openBuildQueuePage() {
        childPageBack.resetProgress()
        showPlanLibraryPage = false
        showBuildQueuePage = true
    }

    ObserveChildPageVisibility(
        transition = childPageTransition,
        onVisibleChange = onPlanPageVisibleChange,
        onAfterExitAnimation = { childPageBack.resetProgress() }
    )

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

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            icon = { Icon(Icons.Default.Build, null) },
            title = { Text(stringResource(R.string.build_confirm_submit)) },
            text = {
                val noRootScheme = config.kernelsuVariant == KSU_VARIANT_NONE
                val enabledLabel = stringResource(R.string.build_feature_enabled)
                val disabledLabel = stringResource(R.string.build_feature_disabled)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.build_config_overview), fontWeight = FontWeight.SemiBold)
                    if (isOnePlusBuild) {
                        Text(stringResource(R.string.build_target_line, buildTargetLabel(config.buildTarget)))
                        Text(stringResource(R.string.build_oneplus_device_line, KernelSupport.onePlusDeviceLabel(config.onePlusDeviceManifest)))
                        Text(stringResource(R.string.build_oneplus_kernel_line, config.androidVersion, config.kernelVersion))
                        Text("KSU: ${ksuVariantDisplayName(config.kernelsuVariant)}")
                        Text(
                            stringResource(
                                R.string.build_oneplus_feature_line,
                                if (!config.cancelSusfs) enabledLabel else disabledLabel,
                                if (config.onePlusUseLz4kd) enabledLabel else disabledLabel,
                                if (config.useKpm) enabledLabel else disabledLabel
                            )
                        )
                        Text(
                            stringResource(
                                R.string.build_oneplus_network_line,
                                if (config.onePlusUseBbr) enabledLabel else disabledLabel,
                                if (config.onePlusUseProxyOptimization) enabledLabel else disabledLabel,
                                if (config.onePlusUseUnicodeBypass) enabledLabel else disabledLabel
                            )
                        )
                        Text(
                            stringResource(
                                R.string.build_protection_line,
                                if (config.useBbg) enabledLabel else disabledLabel,
                                disabledLabel
                            )
                        )
                    } else {
                        Text(stringResource(R.string.build_kernel_line, config.androidVersion, config.kernelVersion, config.subLevel))
                        Text(
                            if (noRootScheme) {
                                "KSU: ${ksuVariantDisplayName(config.kernelsuVariant)}"
                            } else {
                                "KSU: ${config.kernelsuVariant} (${config.kernelsuBranch})"
                            }
                        )
                        Text(stringResource(R.string.build_patch_level_line, config.osPatchLevel))
                        Text(
                            stringResource(
                                R.string.build_feature_line,
                                if (!config.cancelSusfs) enabledLabel else disabledLabel,
                                if (config.useZram) enabledLabel else disabledLabel,
                                if (config.useKpm) enabledLabel else disabledLabel
                            )
                        )
                        Text(
                            stringResource(
                                R.string.build_protection_line,
                                if (config.useBbg) enabledLabel else disabledLabel,
                                if (config.useDdk) enabledLabel else disabledLabel
                            )
                        )
                        Text(
                            stringResource(
                                R.string.build_sync_network_line,
                                if (config.useNtsync) enabledLabel else disabledLabel,
                                if (config.useNetworking) enabledLabel else disabledLabel
                            )
                        )
                        Text(stringResource(R.string.build_virtualization_line, virtualizationSupportLabel(config.virtualizationSupport)))
                        Text(
                            stringResource(
                                R.string.build_external_modules_line,
                                if (config.useCustomExternalModules) {
                                    stringResource(R.string.build_external_modules_count, config.customExternalModules.size)
                                } else {
                                    disabledLabel
                                }
                            )
                        )
                    }
                    if (activeBuild || activeQueueCount > 0) {
                        Text(
                            text = stringResource(R.string.build_active_queue_notice),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
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

    if (showSavePlanDialog) {
        SaveBuildPlanDialog(
            name = savePlanName,
            onNameChange = { savePlanName = it },
            onDismiss = { showSavePlanDialog = false },
            onConfirm = {
                vm.saveCurrentBuildPlan(savePlanName)
                showSavePlanDialog = false
                Toast.makeText(context, context.getString(R.string.build_plan_saved), Toast.LENGTH_SHORT).show()
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
                Toast.makeText(context, context.getString(R.string.build_plan_applied), Toast.LENGTH_SHORT).show()
            },
            onSave = { preview ->
                vm.importBuildPlanToLibrary(preview)
                showImportPlanDialog = false
                Toast.makeText(context, context.getString(R.string.build_plan_saved_library), Toast.LENGTH_SHORT).show()
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
                Toast.makeText(context, context.getString(R.string.build_plan_code_copied), Toast.LENGTH_SHORT).show()
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
                Toast.makeText(context, context.getString(R.string.build_plan_renamed), Toast.LENGTH_SHORT).show()
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
                Toast.makeText(context, context.getString(R.string.build_plan_deleted), Toast.LENGTH_SHORT).show()
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
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (metadata.version.isNotBlank() || metadata.description.isNotBlank()) {
                        Text(
                            text = buildString {
                                if (metadata.version.isNotBlank()) append(stringResource(R.string.module_repo_version, metadata.version))
                                if (metadata.version.isNotBlank() && metadata.description.isNotBlank()) appendLine()
                                if (metadata.description.isNotBlank()) append(metadata.description)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                style = MaterialTheme.typography.bodyMedium
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
        AlertDialog(
            onDismissRequest = {
                editingCustomModuleGroup = null
                editingCustomModuleStages = emptyList()
            },
            icon = { Icon(Icons.Default.Edit, null) },
            title = { Text(stringResource(R.string.build_edit_injection_stage)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = group.displayName(stringResource(R.string.build_external_module_default)),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = group.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    CustomExternalModuleStage.options.forEach { stage ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Checkbox(
                                checked = stage in editingCustomModuleStages,
                                onCheckedChange = { checked ->
                                    editingCustomModuleStages = if (checked) {
                                        (editingCustomModuleStages + stage).distinct()
                                    } else {
                                        editingCustomModuleStages - stage
                                    }
                                }
                            )
                            Text(stage, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.setCustomExternalModuleStages(group.url, editingCustomModuleStages)
                        editingCustomModuleGroup = null
                        editingCustomModuleStages = emptyList()
                    }
                ) {
                    Text(
                        if (editingCustomModuleStages.isEmpty()) {
                            stringResource(R.string.build_remove_module)
                        } else {
                            stringResource(R.string.build_save)
                        }
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        editingCustomModuleGroup = null
                        editingCustomModuleStages = emptyList()
                    }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    val moduleSetGroup = editingModuleSetGroup
    val moduleSetMetadata = editingModuleSetMetadata
    if (moduleSetGroup != null && moduleSetMetadata != null) {
        AlertDialog(
            onDismissRequest = ::clearModuleSetEditor,
            icon = { Icon(Icons.Default.Edit, null) },
            title = { Text(stringResource(R.string.build_edit_injection_stage)) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = moduleSetMetadata.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (moduleSetMetadata.version.isNotBlank() || moduleSetMetadata.description.isNotBlank()) {
                        Text(
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
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    moduleSetMetadata.children.forEach { child ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Checkbox(
                                checked = child.id in editingModuleSetChildIds,
                                onCheckedChange = { checked ->
                                    editingModuleSetChildIds = if (checked) {
                                        (editingModuleSetChildIds + child.id).distinct()
                                    } else {
                                        editingModuleSetChildIds - child.id
                                    }
                                }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = child.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (child.description.isNotBlank()) {
                                    Text(
                                        text = child.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (child.id in editingModuleSetChildIds) {
                                    val options = child.supportedStages
                                    val initialStages = child.recommendedStages
                                        .filter { it in options }
                                        .ifEmpty { listOf(child.defaultStage) }
                                    val selectedStages = editingModuleSetStageSelections[child.id] ?: initialStages
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        options.forEach { stage ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Checkbox(
                                                    checked = stage in selectedStages,
                                                    onCheckedChange = { checked ->
                                                        val updatedStages = if (checked) {
                                                            (selectedStages + stage).distinct()
                                                        } else {
                                                            selectedStages - stage
                                                        }
                                                        editingModuleSetStageSelections =
                                                            editingModuleSetStageSelections + (child.id to updatedStages)
                                                    }
                                                )
                                                Text(
                                                    text = buildString {
                                                        append(stage)
                                                        if (stage in child.recommendedStages) {
                                                            append(stringResource(R.string.module_repo_recommended))
                                                        }
                                                    },
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
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
                    },
                    enabled = editingModuleSetChildIds.isNotEmpty() && moduleSetMetadata.children
                        .filter { it.id in editingModuleSetChildIds }
                        .all { child ->
                            val selectedStages = editingModuleSetStageSelections[child.id]
                                ?: child.recommendedStages
                                    .filter { it in child.supportedStages }
                                    .ifEmpty { listOf(child.defaultStage) }
                            selectedStages.any { it in child.supportedStages }
                        }
                ) {
                    Text(stringResource(R.string.build_save))
                }
            },
            dismissButton = {
                TextButton(onClick = ::clearModuleSetEditor) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
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

    if (!state.isLoggedIn || state.forkRepo == null) {
        val needsLogin = !state.isLoggedIn
        Scaffold(
            containerColor = appPageBackgroundColor(uiSurfaceColor(MaterialTheme.colorScheme.surface)),
            topBar = {
                ExpressiveTopBar(
                    title = stringResource(R.string.build_title),
                    scrollBehavior = scrollBehavior
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(horizontal = AbkScreenHorizontalPadding)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ExpressiveHeroCard(
                    title = stringResource(
                        if (needsLogin) {
                            R.string.build_login_required_title
                        } else {
                            R.string.build_fork_required_title
                        }
                    ),
                    subtitle = stringResource(
                        if (needsLogin) {
                            R.string.build_login_required_desc
                        } else {
                            R.string.build_fork_required_desc
                        }
                    ),
                    icon = if (needsLogin) Icons.Default.Code else Icons.Default.ForkRight,
                    badge = {
                        ExpressiveStatusChip(
                            label = stringResource(
                                if (needsLogin) {
                                    R.string.github_auth_required
                                } else {
                                    R.string.fork_create_badge
                                }
                            ),
                            icon = if (needsLogin) Icons.Default.VerifiedUser else Icons.Default.CallSplit,
                            color = if (needsLogin) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                        )
                    }
                )
                Button(
                    onClick = vm::openBuildOobe,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Icon(
                        if (needsLogin) Icons.Default.Code else Icons.Default.ForkRight,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(
                            if (needsLogin) {
                                R.string.login_github
                            } else {
                                R.string.oobe_continue_setup
                            }
                        )
                    )
                }
            }
        }
        return
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val childPageTopInset = outerPadding.calculateTopPadding()
        val childPageBottomInset = outerPadding.calculateBottomPadding()
        val childPageModifier = Modifier
            .fillMaxWidth()
            .height(maxHeight + childPageTopInset + childPageBottomInset)
            .offset(y = -childPageTopInset)
        Scaffold(
            containerColor = appPageBackgroundColor(uiSurfaceColor(MaterialTheme.colorScheme.surface)),
            topBar = {
                ExpressiveTopBar(
                    title = stringResource(R.string.build_title),
                    scrollBehavior = scrollBehavior
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = AbkScreenHorizontalPadding),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            BuildPlanHero(
                config,
                recommended,
                state.buildStatus
            )

            BuildPlanToolsCard(
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

            BuildTargetSelector(
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

            AnimatedVisibility(
                visible = state.buildStatus != BuildStatus.IDLE,
                enter = fadeIn() + slideInVertically { -it / 3 } + expandVertically(),
                exit = fadeOut() + shrinkVertically()
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
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    BuildKindProgressBlock(
                        title = stringResource(R.string.status_build),
                        status = state.kernelBuildStatus,
                        progress = state.kernelBuildProgress,
                        currentRun = state.kernelCurrentRun,
                        activeRunCount = state.kernelActiveBuildRuns.size,
                        cancellingRunIds = state.cancellingWorkflowRunIds,
                        runningChips = kernelRunningChips,
                        queuedChips = kernelQueuedChips,
                        onCancel = vm::cancelWorkflowRun,
                    )
                    if (state.managerBuildStatus != BuildStatus.IDLE || state.managerCurrentRun != null) {
                        BuildKindProgressBlock(
                            title = stringResource(R.string.status_manager_build),
                            status = state.managerBuildStatus,
                            progress = state.managerBuildProgress,
                            currentRun = state.managerCurrentRun,
                            activeRunCount = state.managerActiveBuildRuns.size,
                            cancellingRunIds = state.cancellingWorkflowRunIds,
                            runningChips = managerRunningChips,
                            queuedChips = managerQueuedChips,
                            onCancel = vm::cancelWorkflowRun,
                        )
                    }
                }
            }

            SectionCard(section = BuildSection.KernelVersion) {
                if (isOnePlusBuild) {
                    DropdownField(
                        label = stringResource(R.string.build_oneplus_device_manifest),
                        value = config.onePlusDeviceManifest,
                        options = KernelSupport.onePlusDeviceManifestOptions,
                        optionLabel = KernelSupport::onePlusDeviceLabel,
                        onSelect = { manifest ->
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
                    Text(
                        text = stringResource(R.string.build_oneplus_profile_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ReadOnlyField(
                        label = stringResource(R.string.build_oneplus_cpu),
                        value = config.onePlusCpu
                    )
                    ReadOnlyField(
                        label = stringResource(R.string.build_android_version),
                        value = config.androidVersion
                    )
                    ReadOnlyField(
                        label = stringResource(R.string.build_kernel_version),
                        value = config.kernelVersion
                    )
                } else {
                    DropdownField(
                        label = stringResource(R.string.build_android_version),
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
                        label = stringResource(R.string.build_kernel_version),
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
                        label = stringResource(R.string.build_sub_level),
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
                        label = stringResource(R.string.build_security_patch_level),
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
                                Text(
                                    recommended?.revision?.let {
                                        stringResource(R.string.build_revision_recommended, it)
                                    } ?: stringResource(R.string.build_revision_510)
                                )
                            },
                            placeholder = { Text(stringResource(R.string.build_revision_placeholder)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }
            }

            SectionCard(section = BuildSection.KernelSu) {
                val noRootScheme = config.kernelsuVariant == KSU_VARIANT_NONE
                DropdownField(
                    label = stringResource(R.string.build_kernelsu_variant),
                    value = config.kernelsuVariant,
                    options = ksuVariantOptions,
                    onSelect = {
                        vm.updateBuildConfig(KernelSupport.normalize(config.copy(kernelsuVariant = it)))
                    }
                )
                if (noRootScheme) {
                    Text(
                        text = if (isOnePlusBuild) {
                            stringResource(R.string.build_oneplus_no_root_scheme_desc)
                        } else {
                            stringResource(R.string.build_no_root_scheme_desc)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (!isOnePlusBuild) {
                    DropdownField(
                        label = stringResource(R.string.build_ksu_branch),
                        value = KernelSupport.normalizeKsuBranch(config.kernelsuBranch),
                        options = ksuBranchOptions,
                        onSelect = {
                            vm.updateBuildConfig(
                                KernelSupport.normalize(config.copy(kernelsuBranch = it))
                            )
                        }
                    )
                    AnimatedVisibility(config.kernelsuBranch == KSU_BRANCH_LATEST) {
                        Text(
                            text = stringResource(R.string.build_ksu_branch_latest_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    AnimatedVisibility(config.kernelsuBranch == KSU_BRANCH_CUSTOM) {
                        OutlinedTextField(
                            value = config.customRef,
                            onValueChange = { vm.updateBuildConfig(config.copy(customRef = it)) },
                            label = { Text(stringResource(R.string.build_custom_ksu_ref)) },
                            placeholder = { Text(stringResource(R.string.build_custom_ksu_ref_placeholder)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                } else {
                    Text(
                        text = stringResource(R.string.build_oneplus_ksu_branch_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            SectionCard(section = BuildSection.Features) {
                val noRootScheme = config.kernelsuVariant == KSU_VARIANT_NONE
                val kpmSupported = KernelSupport.isKpmSupported(
                    config.buildTarget,
                    config.kernelsuVariant,
                    config.kernelsuBranch
                )
                if (isOnePlusBuild) {
                    val proxyAllowed = !config.onePlusCpu.startsWith("mt")
                    val onePlusSusfsSupported = KernelSupport.onePlusSusfsSupported(config.androidVersion, config.kernelVersion)
                    SwitchRow(
                        stringResource(R.string.build_enable_susfs),
                        !config.cancelSusfs && onePlusSusfsSupported,
                        enabled = !noRootScheme && onePlusSusfsSupported
                    ) {
                        vm.updateBuildConfig(KernelSupport.normalize(config.copy(cancelSusfs = !it)))
                    }
                    if (!onePlusSusfsSupported) {
                        Text(
                            text = stringResource(R.string.build_oneplus_susfs_unsupported),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    SwitchRow(stringResource(R.string.build_enable_kpm), config.useKpm, enabled = kpmSupported && !noRootScheme) {
                        vm.updateBuildConfig(KernelSupport.normalize(config.copy(useKpm = it)))
                    }
                    SwitchRow(stringResource(R.string.build_oneplus_lz4kd), config.onePlusUseLz4kd) {
                        vm.updateBuildConfig(config.copy(onePlusUseLz4kd = it))
                    }
                    SwitchRow(stringResource(R.string.build_enable_bbg), config.useBbg) {
                        vm.updateBuildConfig(config.copy(useBbg = it))
                    }
                    SwitchRow(stringResource(R.string.build_oneplus_bbr), config.onePlusUseBbr) {
                        vm.updateBuildConfig(config.copy(onePlusUseBbr = it))
                    }
                    SwitchRow(
                        stringResource(R.string.build_oneplus_proxy_optimization),
                        config.onePlusUseProxyOptimization,
                        enabled = proxyAllowed
                    ) {
                        vm.updateBuildConfig(KernelSupport.normalize(config.copy(onePlusUseProxyOptimization = it)))
                    }
                    if (!proxyAllowed) {
                        Text(
                            text = stringResource(R.string.build_oneplus_proxy_mtk_disabled),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    SwitchRow(stringResource(R.string.build_oneplus_unicode_bypass), config.onePlusUseUnicodeBypass) {
                        vm.updateBuildConfig(config.copy(onePlusUseUnicodeBypass = it))
                    }
                } else {
                    SwitchRow(stringResource(R.string.build_enable_susfs), !config.cancelSusfs, enabled = !noRootScheme) {
                        vm.updateBuildConfig(KernelSupport.normalize(config.copy(cancelSusfs = !it)))
                    }
                    SwitchRow(stringResource(R.string.build_enable_zram), config.useZram) {
                        vm.updateBuildConfig(config.copy(useZram = it))
                    }
                    SwitchRow(stringResource(R.string.build_enable_bbg), config.useBbg) {
                        vm.updateBuildConfig(config.copy(useBbg = it))
                    }
                    SwitchRow(stringResource(R.string.build_enable_ddk), config.useDdk) {
                        vm.updateBuildConfig(config.copy(useDdk = it))
                    }
                    SwitchRow(stringResource(R.string.build_enable_ntsync), config.useNtsync) {
                        vm.updateBuildConfig(config.copy(useNtsync = it))
                    }
                    SwitchRow(stringResource(R.string.build_enable_networking), config.useNetworking) {
                        vm.updateBuildConfig(config.copy(useNetworking = it))
                    }
                    SwitchRow(stringResource(R.string.build_enable_kpm), config.useKpm, enabled = kpmSupported && !noRootScheme) {
                        vm.updateBuildConfig(KernelSupport.normalize(config.copy(useKpm = it)))
                    }
                    SwitchRow(stringResource(R.string.build_enable_rekernel), config.useRekernel) {
                        vm.updateBuildConfig(config.copy(useRekernel = it))
                    }
                    DropdownField(
                        label = stringResource(R.string.build_virtualization_support),
                        value = config.virtualizationSupport,
                        options = virtualizationSupportOptions,
                        onSelect = { vm.updateBuildConfig(config.copy(virtualizationSupport = it)) }
                    )
                    SwitchRow(stringResource(R.string.build_enable_oneplus_8e), config.suppOp) {
                        vm.updateBuildConfig(config.copy(suppOp = it))
                    }
                }
            }

            AnimatedVisibility(!isOnePlusBuild && config.useZram) {
                SectionCard(section = BuildSection.ZramOptions) {
                    SwitchRow(stringResource(R.string.build_zram_full_algo), config.zramFullAlgo) {
                        vm.updateBuildConfig(config.copy(zramFullAlgo = it))
                    }
                    if (!config.zramFullAlgo) {
                        OutlinedTextField(
                            value = config.zramExtraAlgos,
                            onValueChange = { vm.updateBuildConfig(config.copy(zramExtraAlgos = it)) },
                            label = { Text(stringResource(R.string.build_zram_custom_algo)) },
                            placeholder = { Text(stringResource(R.string.build_zram_algo_placeholder)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }
            }

            AnimatedVisibility(!isOnePlusBuild && config.useKpm) {
                SectionCard(section = BuildSection.KpmOptions) {
                    OutlinedTextField(
                        value = config.kpmPassword,
                        onValueChange = { vm.updateBuildConfig(config.copy(kpmPassword = it)) },
                        label = { Text(stringResource(R.string.build_kpm_password)) },
                        placeholder = { Text(stringResource(R.string.build_kpm_password_placeholder)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }

            if (!isOnePlusBuild) {
            SectionCard(section = BuildSection.CustomModules) {
                SwitchRow(stringResource(R.string.build_enable_custom_modules), config.useCustomExternalModules) {
                    vm.updateBuildConfig(config.copy(useCustomExternalModules = it))
                }
                AnimatedVisibility(config.useCustomExternalModules) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        val catalogGroups = customModuleGroups.filter { it.catalogModule != null }
                        val manualGroups = customModuleGroups.filter { it.catalogModule == null }
                        if (catalogGroups.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.build_add_from_module_repo),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            catalogGroups.forEach { group ->
                                key(group.key) {
                                    AnimatedVisibility(
                                        visible = group.key !in removingCustomModuleKeys,
                                        enter = fadeIn() + expandVertically(),
                                        exit = fadeOut() + shrinkVertically()
                                    ) {
                                        ExpressiveListItem(
                                            title = group.displayName(stringResource(R.string.build_external_module_default)),
                                            subtitle = group.subtitle(
                                                noStageLabel = stringResource(R.string.build_stage_none),
                                                sourcePrefix = stringResource(R.string.build_source_list, "%s")
                                            ),
                                            leadingIcon = Icons.Default.CheckCircle,
                                            trailingContent = {
                                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                                    IconButton(
                                                        onClick = {
                                                            if (group.entryKind == CustomExternalModuleEntryKind.MODULE_SET_CHILD) {
                                                                openModuleSetEditor(group)
                                                            } else {
                                                                editingCustomModuleGroup = group
                                                                editingCustomModuleStages = group.stages
                                                            }
                                                        }
                                                    ) {
                                                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.build_edit_injection_stage))
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
                                                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.build_remove_module))
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        if (manualGroups.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.build_manual_add),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            manualGroups.forEach { group ->
                                key(group.key) {
                                    AnimatedVisibility(
                                        visible = group.key !in removingCustomModuleKeys,
                                        enter = fadeIn() + expandVertically(),
                                        exit = fadeOut() + shrinkVertically()
                                    ) {
                                        ExpressiveListItem(
                                            title = group.displayName(stringResource(R.string.build_external_module_default)),
                                            subtitle = group.subtitle(
                                                noStageLabel = stringResource(R.string.build_stage_none),
                                                sourcePrefix = stringResource(R.string.build_source_list, "%s")
                                            ),
                                            leadingIcon = Icons.Default.Extension,
                                            trailingContent = {
                                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                                    IconButton(
                                                        onClick = {
                                                            if (group.entryKind == CustomExternalModuleEntryKind.MODULE_SET_CHILD) {
                                                                openModuleSetEditor(group)
                                                            } else {
                                                                editingCustomModuleGroup = group
                                                                editingCustomModuleStages = group.stages
                                                            }
                                                        }
                                                    ) {
                                                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.build_edit_injection_stage))
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
                                                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.build_remove_module))
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        if (customModuleGroups.isNotEmpty()) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }

                        OutlinedTextField(
                            value = customModuleUrl,
                            onValueChange = { customModuleUrl = it },
                            label = { Text(stringResource(R.string.build_repo_url)) },
                            placeholder = { Text("https://github.com/user/module") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                val cleanUrl = customModuleUrl.trim()
                                if (cleanUrl.isNotEmpty()) {
                                    coroutineScope.launch {
                                        vm.checkCustomExternalModuleMetadata(cleanUrl)?.let { metadata ->
                                            pendingCustomModuleUrl = cleanUrl
                                            pendingCustomModuleMetadata = metadata
                                            selectedCustomModuleStages = metadata.recommendedStages
                                                .filter { it in metadata.supportedStages }
                                                .ifEmpty { listOf(metadata.defaultStage) }
                                        }
                                    }
                                }
                            },
                            enabled = customModuleUrl.isNotBlank() && !state.validatingCustomExternalModule,
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Icon(
                                imageVector = if (state.validatingCustomExternalModule) {
                                    Icons.Default.Refresh
                                } else {
                                    Icons.Default.Add
                                },
                                contentDescription = null
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (state.validatingCustomExternalModule) {
                                    stringResource(R.string.build_checking)
                                } else {
                                    stringResource(R.string.build_check_module)
                                }
                            )
                        }

                        state.customExternalModuleError?.let { err ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                )
                            ) {
                                Row(
                                    Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Error,
                                        null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        err,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = { vm.clearCustomExternalModuleError() }) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = stringResource(R.string.close_error),
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }

                    }
                }
            }

            SectionCard(section = BuildSection.OptionalConfig) {
                OutlinedTextField(
                    value = config.version,
                    onValueChange = { vm.updateBuildConfig(config.copy(version = it)) },
                    label = { Text(stringResource(R.string.build_custom_version_optional)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                ConfigPreviewText(versionPreview)
                OutlinedTextField(
                    value = config.buildTime,
                    onValueChange = { vm.updateBuildConfig(config.copy(buildTime = it)) },
                    label = { Text(stringResource(R.string.build_custom_time_optional)) },
                    placeholder = { Text(stringResource(R.string.build_time_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                ConfigPreviewText(buildTimePreview)
            }
            }

            // Submit button
            Button(
                onClick = { showConfirmDialog = true },
                enabled = true,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Icon(Icons.Default.RocketLaunch, null)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (activeBuild || activeQueueCount > 0 || state.buildQueueProcessing) {
                        stringResource(R.string.build_add_queue)
                    } else {
                        stringResource(R.string.build_submit)
                    }
                )
            }

            Spacer(Modifier.height(80.dp + outerPadding.calculateBottomPadding()))
            }
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
            visible = { it },
            enter = childPageOverlayEnterTransition(state.predictiveBackEnabled, motionScheme),
            exit = childPageOverlayExitTransition(state.predictiveBackEnabled, motionScheme),
            modifier = childPageModifier
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(childPageBack.backTransformModifier())
            ) {
                BuildPlanPageBackground(
                    backgroundUri = state.customBackgroundUri,
                    backgroundImageEnabled = state.backgroundImageEnabled
                )
                Scaffold(
                    containerColor = Color.Transparent,
                    topBar = {
                        ExpressiveTopBar(
                            title = if (showBuildQueuePage) {
                                stringResource(R.string.build_queue_title)
                            } else {
                                stringResource(R.string.build_plan_library)
                            },
                            navigationIcon = {
                                IconButton(onClick = childPageBack::requestDismiss) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.build_back_to_config))
                                }
                            }
                        )
                    }
                ) { padding ->
                    if (showBuildQueuePage) {
                        BuildQueuePage(
                            queue = state.buildQueue,
                            cancellingRunIds = state.cancellingWorkflowRunIds,
                            onApply = {
                                vm.updateBuildConfig(it.config)
                                childPageBack.requestDismiss()
                                Toast.makeText(context, context.getString(R.string.build_queue_applied), Toast.LENGTH_SHORT).show()
                            },
                            onRemove = { vm.removeBuildQueueItem(it.id) },
                            onRetry = { vm.retryBuildQueueItem(it.id) },
                            onCancelRun = { runId -> vm.cancelWorkflowRun(runId) },
                            onClearCompleted = vm::clearCompletedBuildQueueItems,
                            modifier = Modifier
                                .padding(padding)
                                .fillMaxSize()
                        )
                    } else {
                        BuildPlanLibraryPage(
                            plans = state.buildPlans,
                            onApply = {
                                vm.applyBuildPlan(it)
                                childPageBack.requestDismiss()
                                Toast.makeText(context, context.getString(R.string.build_plan_applied_edit), Toast.LENGTH_SHORT).show()
                            },
                            onShare = { sharePlanTarget = it },
                            onRename = {
                                renamePlanTarget = it
                                renamePlanName = it.name
                            },
                            onDelete = { deletePlanTarget = it },
                            modifier = Modifier
                                .padding(padding)
                                .fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BuildPlanPageBackground(
    backgroundUri: String?,
    backgroundImageEnabled: Boolean
) {
    AppPageBackground(
        backgroundUri = backgroundUri,
        backgroundImageEnabled = backgroundImageEnabled
    )
}

@Composable
private fun BuildPlanToolsCard(
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
    onImport: () -> Unit
) {
    ExpressiveSectionCard(
        title = stringResource(R.string.build_plan_tools_title),
        subtitle = stringResource(R.string.build_plan_tools_desc),
        icon = Icons.Default.FolderOpen
    ) {
        Column(
            modifier = Modifier.animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = currentSummary,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (expanded) 3 else 1,
                    overflow = TextOverflow.Ellipsis
                )
                IconButton(onClick = { onExpandedChange(!expanded) }) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded) {
                            stringResource(R.string.build_collapse_plan_tools)
                        } else {
                            stringResource(R.string.build_expand_plan_tools)
                        }
                    )
                }
            }
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onSave,
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.build_save))
                        }
                        OutlinedButton(
                            onClick = onLibrary,
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) {
                            Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.build_library))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onQueue,
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) {
                            Icon(Icons.Default.Queue, null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.build_queue_short))
                        }
                        Button(
                            onClick = onShare,
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.build_share))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onImport,
                            modifier = Modifier.fillMaxWidth().height(44.dp)
                        ) {
                            Icon(Icons.Default.Download, null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.build_import))
                        }
                    }
                    Text(
                        text = buildString {
                            append(
                                if (plansCount > 0) {
                                    stringResource(R.string.build_saved_plans_count, plansCount)
                                } else {
                                    stringResource(R.string.build_no_saved_plans)
                                }
                            )
                            append(" · ")
                            append(
                                if (activeQueueCount > 0) {
                                    stringResource(R.string.build_queue_summary, activeQueueCount, pendingQueueCount)
                                } else {
                                    stringResource(R.string.build_queue_empty)
                                }
                            )
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SaveBuildPlanDialog(
    name: String,
    onNameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Add, null) },
        title = { Text(stringResource(R.string.build_save_plan)) },
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
            Button(onClick = onConfirm) {
                Text(stringResource(R.string.build_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
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
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Download, null) },
        title = { Text(stringResource(R.string.build_import_plan)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = code,
                    onValueChange = onCodeChange,
                    label = { Text(stringResource(R.string.build_abkp2_code)) },
                    placeholder = { Text(stringResource(R.string.build_abkp2_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5
                )
                error?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                preview?.let {
                    ExpressiveListItem(
                        title = it.plan.name,
                        subtitle = "${buildPlanScopeLabel(it.scope)}\n${buildPlanSummary(it.plan.config)}",
                        leadingIcon = Icons.Default.CheckCircle,
                        selected = true
                    )
                }
            }
        },
        confirmButton = {
            if (preview == null) {
                Button(
                    onClick = onParse,
                    enabled = code.isNotBlank()
                ) {
                    Text(stringResource(R.string.build_parse))
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onSave(preview) }) {
                        Text(stringResource(R.string.build_save))
                    }
                    Button(onClick = { onApply(preview) }) {
                        Text(stringResource(R.string.build_apply))
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun ShareBuildPlanScopeDialog(
    plan: BuildPlan,
    onDismiss: () -> Unit,
    onShare: (BuildPlanShareScope) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Share, null) },
        title = { Text(stringResource(R.string.build_share_plan)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ExpressiveListItem(
                    title = plan.name.ifBlank { stringResource(R.string.build_current_plan) },
                    subtitle = buildPlanSummary(plan.config),
                    leadingIcon = Icons.Default.FolderOpen
                )
                Text(
                    text = stringResource(R.string.build_share_plan_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = { onShare(BuildPlanShareScope.FULL) }) {
                Text(stringResource(R.string.build_full_plan))
            }
        },
        dismissButton = {
            TextButton(onClick = { onShare(BuildPlanShareScope.FEATURES_ONLY) }) {
                Text(stringResource(R.string.build_features_only))
            }
        }
    )
}

@Composable
private fun BuildPlanLibraryPage(
    plans: List<BuildPlan>,
    onApply: (BuildPlan) -> Unit,
    onShare: (BuildPlan) -> Unit,
    onRename: (BuildPlan) -> Unit,
    onDelete: (BuildPlan) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AbkScreenHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (plans.isEmpty()) {
            ExpressiveSectionCard(
                title = stringResource(R.string.build_no_plans),
                subtitle = stringResource(R.string.build_no_plans_desc),
                icon = Icons.Default.FolderOpen
            ) {
                Text(
                    text = stringResource(R.string.build_no_plans_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            plans.forEach { plan ->
                BuildPlanLibraryItem(
                    plan = plan,
                    onApply = { onApply(plan) },
                    onShare = { onShare(plan) },
                    onRename = { onRename(plan) },
                    onDelete = { onDelete(plan) }
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun BuildPlanLibraryItem(
    plan: BuildPlan,
    onApply: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    ExpressiveSectionCard(
        title = plan.name,
        subtitle = buildPlanSummary(plan.config),
        icon = Icons.Default.FolderOpen
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onApply,
                modifier = Modifier.weight(1f).height(42.dp)
            ) {
                Icon(Icons.Default.Edit, null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.build_apply_edit))
            }
            OutlinedButton(
                onClick = onShare,
                modifier = Modifier.weight(1f).height(42.dp)
            ) {
                Icon(Icons.Default.Share, null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.build_share))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onRename,
                modifier = Modifier.weight(1f).height(42.dp)
            ) {
                Icon(Icons.Default.Edit, null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.build_rename))
            }
            OutlinedButton(
                onClick = onDelete,
                modifier = Modifier.weight(1f).height(42.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.Delete, null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.delete))
            }
        }
    }
}

@Composable
private fun BuildQueuePage(
    queue: List<BuildQueueItem>,
    cancellingRunIds: Set<Long>,
    onApply: (BuildQueueItem) -> Unit,
    onRemove: (BuildQueueItem) -> Unit,
    onRetry: (BuildQueueItem) -> Unit,
    onCancelRun: (Long) -> Unit,
    onClearCompleted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val terminalItems = queue.filter { it.status.isTerminalQueueStatus() }
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AbkScreenHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ExpressiveSectionCard(
            title = stringResource(R.string.build_queue_status),
            subtitle = if (queue.isEmpty()) {
                stringResource(R.string.build_queue_status_desc)
            } else {
                stringResource(R.string.build_queue_status_count, queue.size, queue.count { it.status == BuildQueueItemStatus.PENDING })
            },
            icon = Icons.Default.Queue
        ) {
            if (terminalItems.isNotEmpty()) {
                OutlinedButton(
                    onClick = onClearCompleted,
                    modifier = Modifier.fillMaxWidth().height(42.dp)
                ) {
                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.build_clear_finished))
                }
            } else {
                Text(
                    text = if (queue.isEmpty()) {
                        stringResource(R.string.build_queue_empty)
                    } else {
                        stringResource(R.string.build_dispatching_in_order)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (queue.isEmpty()) {
            ExpressiveSectionCard(
                title = stringResource(R.string.build_no_queue_items),
                subtitle = stringResource(R.string.build_no_queue_items_desc),
                icon = Icons.Default.Inbox
            ) {
                Text(
                    text = stringResource(R.string.build_queue_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            queue.forEachIndexed { index, item ->
                BuildQueueItemCard(
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
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun BuildQueueItemCard(
    index: Int,
    item: BuildQueueItem,
    cancelling: Boolean,
    onApply: () -> Unit,
    onRemove: () -> Unit,
    onRetry: () -> Unit,
    onCancelRun: () -> Unit
) {
    ExpressiveSectionCard(
        title = "${index + 1}. ${item.name.ifBlank { stringResource(R.string.build_queue_item) }}",
        subtitle = buildPlanSummary(item.config),
        icon = when (item.status) {
            BuildQueueItemStatus.PENDING -> Icons.Default.Schedule
            BuildQueueItemStatus.DISPATCHING -> Icons.Default.CloudUpload
            BuildQueueItemStatus.RUNNING -> Icons.Default.RunCircle
            BuildQueueItemStatus.DONE -> Icons.Default.CheckCircle
            BuildQueueItemStatus.FAILED -> Icons.Default.Error
            BuildQueueItemStatus.CANCELLED -> Icons.Default.Cancel
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ExpressiveStatusChip(label = item.status.queueStatusLabel(), color = item.status.queueStatusColor())
            if (item.runNumber > 0) {
                ExpressiveStatusChip(label = "#${item.runNumber}", color = MaterialTheme.colorScheme.secondary)
            }
            if (item.runId > 0L) {
                ExpressiveStatusChip(
                    label = stringResource(R.string.build_status_run_id, item.runId),
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
        item.error?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onApply,
                modifier = Modifier.weight(1f).height(42.dp)
            ) {
                Icon(Icons.Default.Edit, null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.build_apply))
            }
            when (item.status) {
                BuildQueueItemStatus.PENDING -> OutlinedButton(
                    onClick = onRemove,
                    modifier = Modifier.weight(1f).height(42.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.build_remove))
                }
                BuildQueueItemStatus.DISPATCHING,
                BuildQueueItemStatus.RUNNING -> Button(
                    onClick = onCancelRun,
                    enabled = item.runId > 0L && !cancelling,
                    modifier = Modifier.weight(1f).height(42.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    if (cancelling) {
                        LoadingIndicator(Modifier.size(17.dp))
                    } else {
                        Icon(Icons.Default.Cancel, null, modifier = Modifier.size(17.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (cancelling) {
                            stringResource(R.string.status_cancelling)
                        } else {
                            stringResource(R.string.status_cancel)
                        }
                    )
                }
                BuildQueueItemStatus.FAILED,
                BuildQueueItemStatus.CANCELLED -> Button(
                    onClick = onRetry,
                    modifier = Modifier.weight(1f).height(42.dp)
                ) {
                    Icon(Icons.Default.Replay, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.retry))
                }
                BuildQueueItemStatus.DONE -> OutlinedButton(
                    onClick = onRemove,
                    modifier = Modifier.weight(1f).height(42.dp)
                ) {
                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.clear))
                }
            }
        }
    }
}

@Composable
private fun BuildQueueItemStatus.queueStatusColor(): Color = when (this) {
    BuildQueueItemStatus.PENDING -> MaterialTheme.colorScheme.tertiary
    BuildQueueItemStatus.DISPATCHING,
    BuildQueueItemStatus.RUNNING -> MaterialTheme.colorScheme.secondary
    BuildQueueItemStatus.DONE -> MaterialTheme.colorScheme.primary
    BuildQueueItemStatus.FAILED -> MaterialTheme.colorScheme.error
    BuildQueueItemStatus.CANCELLED -> MaterialTheme.colorScheme.outline
}

@Composable
private fun BuildQueueItemStatus.queueStatusLabel(): String = when (this) {
    BuildQueueItemStatus.PENDING -> stringResource(R.string.build_queue_pending)
    BuildQueueItemStatus.DISPATCHING -> stringResource(R.string.build_queue_dispatching)
    BuildQueueItemStatus.RUNNING -> stringResource(R.string.status_in_progress)
    BuildQueueItemStatus.DONE -> stringResource(R.string.build_queue_done)
    BuildQueueItemStatus.FAILED -> stringResource(R.string.status_failure)
    BuildQueueItemStatus.CANCELLED -> stringResource(R.string.status_cancelled_label)
}

private fun BuildQueueItemStatus.isTerminalQueueStatus(): Boolean =
    this in setOf(BuildQueueItemStatus.DONE, BuildQueueItemStatus.FAILED, BuildQueueItemStatus.CANCELLED)

@Composable
private fun RenameBuildPlanDialog(
    name: String,
    onNameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
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
            Button(onClick = onConfirm) {
                Text(stringResource(R.string.build_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun DeleteBuildPlanDialog(
    plan: BuildPlan,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Delete, null) },
        title = { Text(stringResource(R.string.build_delete_plan)) },
        text = { Text(stringResource(R.string.build_delete_plan_confirm, plan.name)) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Text(stringResource(R.string.delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun ConfigPreviewText(preview: String) {
    ExpressiveListItem(
        title = stringResource(R.string.build_config_preview),
        subtitle = preview,
        leadingIcon = Icons.Default.Visibility,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun BuildTargetSelector(
    selected: String,
    onSelect: (String) -> Unit
) {
    ExpressiveSectionCard(
        title = stringResource(R.string.build_target_title),
        subtitle = stringResource(R.string.build_target_desc),
        icon = Icons.Default.AccountTree
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(BUILD_TARGET_GKI, BUILD_TARGET_ONEPLUS).forEach { target ->
                FilterChip(
                    selected = selected == target,
                    onClick = { onSelect(target) },
                    label = { Text(buildTargetLabel(target), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = {
                        Icon(
                            imageVector = if (target == BUILD_TARGET_ONEPLUS) Icons.Default.PhoneAndroid else Icons.Default.Memory,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun buildTargetLabel(target: String): String = when (target) {
    BUILD_TARGET_ONEPLUS -> stringResource(R.string.build_target_oneplus)
    else -> stringResource(R.string.build_target_gki)
}

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
private fun buildPlanScopeLabel(scope: BuildPlanShareScope): String = when (scope) {
    BuildPlanShareScope.FULL -> stringResource(R.string.build_full_plan)
    BuildPlanShareScope.FEATURES_ONLY -> stringResource(R.string.build_features_only)
}

@Composable
private fun BuildPlanHero(
    config: KernelBuildConfig,
    recommended: KernelBuildConfig?,
    status: BuildStatus
) {
    if (config.buildTarget == BUILD_TARGET_ONEPLUS) {
        ExpressiveHeroCard(
            title = KernelSupport.onePlusDeviceLabel(config.onePlusDeviceManifest),
            subtitle = stringResource(R.string.build_oneplus_hero_desc),
            icon = Icons.Default.PhoneAndroid,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            badge = {
                ExpressiveStatusChip(
                    label = ksuVariantDisplayName(config.kernelsuVariant),
                    icon = Icons.Default.Shield,
                    color = MaterialTheme.colorScheme.primary
                )
                ExpressiveStatusChip(
                    label = "${config.kernelVersion} · ${config.androidVersion}",
                    icon = Icons.Default.Memory,
                    color = MaterialTheme.colorScheme.secondary
                )
                ExpressiveStatusChip(
                    label = if (!config.cancelSusfs) stringResource(R.string.build_susfs_on) else stringResource(R.string.build_susfs_off),
                    icon = Icons.Default.Extension,
                    color = if (!config.cancelSusfs) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline
                )
                if (config.onePlusUseLz4kd) {
                    ExpressiveStatusChip(
                        label = "lz4kd",
                        icon = Icons.Default.Compress,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                ExpressiveStatusChip(
                    label = buildStatusLabel(status),
                    icon = Icons.Default.RunCircle,
                    color = buildStatusColor(status)
                )
            }
        )
        return
    }
    val isRecommended = recommended != null &&
        config.androidVersion == recommended.androidVersion &&
        config.kernelVersion == recommended.kernelVersion &&
        config.subLevel == recommended.subLevel &&
        config.osPatchLevel == recommended.osPatchLevel

    ExpressiveHeroCard(
        title = "${config.kernelVersion}.${config.subLevel} · ${config.androidVersion.removePrefix("android").let { "Android $it" }}",
        subtitle = stringResource(R.string.build_hero_desc),
        icon = Icons.Default.RocketLaunch,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        badge = {
            ExpressiveStatusChip(
                label = ksuVariantDisplayName(config.kernelsuVariant),
                icon = Icons.Default.Shield,
                color = MaterialTheme.colorScheme.primary
            )
            ExpressiveStatusChip(
                label = if (!config.cancelSusfs) stringResource(R.string.build_susfs_on) else stringResource(R.string.build_susfs_off),
                icon = Icons.Default.Extension,
                color = if (!config.cancelSusfs) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline
            )
            if (config.virtualizationSupport != "off") {
                ExpressiveStatusChip(
                    label = stringResource(R.string.build_virtualization_chip, virtualizationSupportLabel(config.virtualizationSupport)),
                    icon = Icons.Default.Extension,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            if (config.useNtsync) {
                ExpressiveStatusChip(
                    label = "NTsync",
                    icon = Icons.Default.Sync,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            if (config.useNetworking) {
                ExpressiveStatusChip(
                    label = stringResource(R.string.build_feature_networking),
                    icon = Icons.Default.Language,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            ExpressiveStatusChip(
                label = if (isRecommended) stringResource(R.string.build_device_recommended) else buildStatusLabel(status),
                icon = if (isRecommended) Icons.Default.AutoAwesome else Icons.Default.RunCircle,
                color = if (isRecommended) MaterialTheme.colorScheme.tertiary else buildStatusColor(status)
            )
        }
    )
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

private fun buildVersionPreview(context: Context, config: KernelBuildConfig): String {
    val compact = config.version.filterNot { it.isWhitespace() }
    if (compact.isBlank()) {
        return context.getString(R.string.build_preview_default_version)
    }
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

@Composable
private fun BuildKindProgressBlock(
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
    if (
        status == BuildStatus.IDLE &&
        currentRun == null &&
        runningChips.isEmpty() &&
        queuedChips.isEmpty()
    ) {
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        BuildStatusBanner(
            status = status,
            progress = progress,
            runId = currentRun?.id ?: 0L,
            activeRunCount = activeRunCount,
            cancelling = currentRun?.id in cancellingRunIds,
            onCancel = onCancel,
        )
        BuildProgressCard(
            progress = progress,
            runningChips = runningChips,
            queuedChips = queuedChips,
        )
    }
}

@Composable
private fun BuildStatusBanner(
    status: BuildStatus,
    progress: BuildProgress,
    runId: Long,
    activeRunCount: Int,
    cancelling: Boolean,
    onCancel: (Long) -> Unit
) {
    val (icon, text, color) = when (status) {
        BuildStatus.QUEUED -> Triple(
            Icons.Default.Queue,
            if (activeRunCount > 1) {
                stringResource(R.string.build_multiple_queued, activeRunCount)
            } else {
                stringResource(R.string.build_queued_waiting)
            },
            MaterialTheme.colorScheme.tertiary
        )
        BuildStatus.IN_PROGRESS -> Triple(
            Icons.Default.RunCircle,
            if (activeRunCount > 1) {
                stringResource(R.string.build_multiple_running, activeRunCount)
            } else {
                stringResource(R.string.build_running_ellipsis)
            },
            MaterialTheme.colorScheme.secondary
        )
        BuildStatus.SUCCESS -> Triple(Icons.Default.CheckCircle, stringResource(R.string.build_success_bang), MaterialTheme.colorScheme.primary)
        BuildStatus.FAILURE -> Triple(Icons.Default.Error, stringResource(R.string.build_failed), MaterialTheme.colorScheme.error)
        BuildStatus.CANCELLED -> Triple(Icons.Default.Cancel, stringResource(R.string.build_cancelled), MaterialTheme.colorScheme.outline)
        else -> return
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = uiSurfaceColor(MaterialTheme.colorScheme.surfaceContainer)
        )
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (status == BuildStatus.IN_PROGRESS) {
                LoadingIndicator(Modifier.size(24.dp))
            } else {
                Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(text, color = color, style = MaterialTheme.typography.bodyMedium)
                if (progress.totalSteps > 0) {
                    // Drop the "·" separator the user explicitly asked to remove —
                    // percent on the left, then the compact chip format text
                    // (already comma-joined for multi-run). maxLines=1 so the
                    // banner stays at a fixed height; full chip layout lives
                    // in BuildProgressCard below.
                    Text(
                        "${progress.percent}% ${progress.currentStep}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                }
            }
            if (status in listOf(BuildStatus.QUEUED, BuildStatus.IN_PROGRESS) && runId > 0L && activeRunCount <= 1) {
                TextButton(
                    onClick = { onCancel(runId) },
                    enabled = !cancelling,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    if (cancelling) {
                        LoadingIndicator(Modifier.size(18.dp))
                    } else {
                        Icon(Icons.Default.Cancel, null, modifier = Modifier.size(17.dp))
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (cancelling) {
                            stringResource(R.string.status_cancelling)
                        } else {
                            stringResource(R.string.status_cancel)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun BuildProgressCard(
    progress: BuildProgress,
    runningChips: List<BuildRunChip> = emptyList(),
    queuedChips: List<BuildRunChip> = emptyList()
) {
    val animatedProgress by animateFloatAsState(
        targetValue = (progress.percent / 100f).coerceIn(0f, 1f),
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "build-progress"
    )
    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = uiSurfaceColor(MaterialTheme.colorScheme.surfaceContainer)
        )
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.build_progress_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("${progress.percent}%", style = MaterialTheme.typography.labelLarge)
            }
            ShimmerLinearProgress(
                progress = { animatedProgress },
                modifier = Modifier.fillMaxWidth(),
            )
            // Top row = currently running runs, bottom row = queued runs.
            // Each chip is one workflow rendered in the compact
            // "#65 SukiSU SUSFS 6.6.89-android15-2025-06" format. Rows scroll
            // horizontally so an arbitrary number of parallel builds fit
            // without wrapping the page.
            if (runningChips.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    runningChips.forEach { chip -> BuildRunChipView(chip) }
                }
            }
            if (queuedChips.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    queuedChips.forEach { chip -> BuildRunChipView(chip) }
                }
            }
            if (runningChips.isEmpty() && queuedChips.isEmpty() && progress.currentStep.isNotBlank()) {
                Text(
                    progress.currentStep,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 2
                )
            }
        }
    }
}

private data class BuildRunChip(
    val runId: Long,
    val text: String,
    val running: Boolean
)

/**
 * Compact "#65 SukiSU SUSFS 6.6.89-android15-2025-06" chips for the Build
 * tab progress card. Mirrors the descriptor logic that the merged-progress
 * text uses, but renders separate UI tiles rather than concatenated text.
 * Manager-only runs become "#42 Manager" / "#42 Manager Dev" chips.
 */
private fun buildRunChipsForStatus(
    activeRuns: List<WorkflowRun>,
    queue: List<BuildQueueItem>,
    running: Boolean
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

@Composable
private fun BuildRunChipView(chip: BuildRunChip) {
    val containerColor = if (chip.running) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (chip.running) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = containerColor,
        contentColor = contentColor
    ) {
        Text(
            text = chip.text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            maxLines = 1
        )
    }
}

private data class BuildCatalogModule(
    val module: ModuleCatalogItem,
    val sources: List<String>
)

private data class BuildCustomModuleGroup(
    val url: String,
    val stages: List<String>,
    val catalogModule: BuildCatalogModule?,
    val entryKind: String = CustomExternalModuleEntryKind.MODULE,
    val groupRepoUrl: String = "",
    val childNames: List<String> = emptyList(),
    val groupName: String = ""
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
    catalogModuleByUrl: Map<String, BuildCatalogModule>
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

private enum class BuildSection {
    KernelVersion,
    KernelSu,
    Features,
    ZramOptions,
    KpmOptions,
    CustomModules,
    OptionalConfig
}

@Composable
private fun SectionCard(section: BuildSection, content: @Composable ColumnScope.() -> Unit) {
    ExpressiveSectionCard(
        title = when (section) {
            BuildSection.KernelVersion -> stringResource(R.string.build_kernel_version_config)
            BuildSection.KernelSu -> stringResource(R.string.build_kernelsu_config)
            BuildSection.Features -> stringResource(R.string.build_features)
            BuildSection.ZramOptions -> stringResource(R.string.build_zram_options)
            BuildSection.KpmOptions -> stringResource(R.string.build_kpm_options)
            BuildSection.CustomModules -> stringResource(R.string.build_custom_modules)
            BuildSection.OptionalConfig -> stringResource(R.string.build_optional_config)
        },
        subtitle = when (section) {
            BuildSection.KernelVersion -> stringResource(R.string.build_section_kernel_desc)
            BuildSection.KernelSu -> stringResource(R.string.build_section_ksu_desc)
            BuildSection.Features -> stringResource(R.string.build_section_features_desc)
            BuildSection.ZramOptions -> stringResource(R.string.build_section_zram_desc)
            BuildSection.KpmOptions -> stringResource(R.string.build_section_kpm_desc)
            BuildSection.CustomModules -> stringResource(R.string.build_section_custom_modules_desc)
            BuildSection.OptionalConfig -> stringResource(R.string.build_section_default_desc)
        },
        icon = when (section) {
            BuildSection.KernelVersion -> Icons.Default.Memory
            BuildSection.KernelSu -> Icons.Default.Shield
            BuildSection.Features -> Icons.Default.Tune
            BuildSection.ZramOptions -> Icons.Default.Compress
            BuildSection.KpmOptions -> Icons.Default.Key
            BuildSection.CustomModules -> Icons.Default.Extension
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
fun SwitchRow(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    ExpressiveSwitchItem(
        title = label,
        checked = checked,
        enabled = enabled,
        onCheckedChange = onCheckedChange
    )
}

@Composable
private fun ksuVariantDisplayName(variant: String): String =
    if (variant == KSU_VARIANT_NONE) {
        stringResource(R.string.build_ksu_none)
    } else {
        variant
    }

@Composable
fun ReadOnlyField(
    label: String,
    value: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth()
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropdownField(
    label: String,
    value: String,
    options: List<String>,
    recommendedValue: String? = null,
    optionLabel: (String) -> String = { it },
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = optionLabel(value),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                val labelText = optionLabel(opt)
                val text = if (opt == recommendedValue) {
                    "$labelText${stringResource(R.string.build_recommended_suffix)}"
                } else {
                    labelText
                }
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = { onSelect(opt); expanded = false },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}
