@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.abk.kernel.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
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
import com.abk.kernel.data.model.BuildProgress
import com.abk.kernel.data.model.BuildStepProgress
import com.abk.kernel.data.model.BuildStatus
import com.abk.kernel.data.model.CustomExternalModuleStage
import com.abk.kernel.data.model.ExternalModuleMetadata
import com.abk.kernel.data.model.KernelSupport
import com.abk.kernel.data.model.KernelBuildConfig
import com.abk.kernel.data.model.ModuleCatalogItem
import com.abk.kernel.data.model.ModuleCatalogRepository
import com.abk.kernel.ui.components.AbkScreenHorizontalPadding
import com.abk.kernel.ui.components.ExpressiveHeroCard
import com.abk.kernel.ui.components.ExpressiveListItem
import com.abk.kernel.ui.components.ExpressiveSectionCard
import com.abk.kernel.ui.components.ExpressiveStatusChip
import com.abk.kernel.ui.components.ExpressiveSwitchItem
import com.abk.kernel.ui.components.ExpressiveTopBar
import com.abk.kernel.ui.theme.uiSurfaceColor
import com.abk.kernel.viewmodel.BuildPlanImportPreview
import com.abk.kernel.viewmodel.BuildPlanShareScope
import com.abk.kernel.viewmodel.MainViewModel
import coil.compose.AsyncImage
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.pow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

private const val BUILD_PLAN_BACK_VISUAL_EXPONENT = 1.8f
private const val BUILD_PLAN_BACK_SCALE_DELTA = 0.09f
private const val BUILD_PLAN_BACK_SCRIM_ALPHA = 0.32f
private const val BUILD_PLAN_PAGE_EXIT_DELAY_MS = 280L
private const val CATALOG_MODULE_REMOVE_DELAY_MS = 260L
private val BUILD_PLAN_BACK_MAX_OFFSET = 56.dp
private val BUILD_PLAN_BACK_MAX_CORNER = 32.dp

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
    val recommended = state.recommendedBuildConfig
    val motionScheme = MaterialTheme.motionScheme
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val suggestedPlanName = remember(config) { vm.suggestedBuildPlanName(config) }
    val ksuBranchOptions = listOf("Stable", "Dev")
    val virtualizationSupportOptions = remember(config.kernelVersion) {
        KernelSupport.virtualizationSupportOptions(config.kernelVersion)
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
    var showSavePlanDialog by remember { mutableStateOf(false) }
    var showImportPlanDialog by remember { mutableStateOf(false) }
    var showPlanLibraryPage by rememberSaveable { mutableStateOf(false) }
    var planToolsExpanded by rememberSaveable { mutableStateOf(false) }
    var planBackProgress by remember { mutableFloatStateOf(0f) }
    val animatedPlanBackProgress by animateFloatAsState(
        targetValue = planBackProgress.coerceIn(0f, 1f),
        animationSpec = motionScheme.fastSpatialSpec(),
        label = "build-plan-back-progress"
    )
    val visualPlanBackProgress = animatedPlanBackProgress
        .coerceIn(0f, 1f)
        .pow(BUILD_PLAN_BACK_VISUAL_EXPONENT)
    val density = LocalDensity.current
    val planBackOffsetPx = with(density) { BUILD_PLAN_BACK_MAX_OFFSET.toPx() }
    val planBackCorner = with(density) { (BUILD_PLAN_BACK_MAX_CORNER.toPx() * visualPlanBackProgress).toDp() }
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
    var removingCatalogModuleKeys by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val coroutineScope = rememberCoroutineScope()
    val catalogModules = remember(state.moduleCatalogRepositories) {
        mergeBuildCatalogModules(state.moduleCatalogRepositories)
    }
    val catalogModuleByUrl = remember(catalogModules) {
        catalogModules.associateBy { it.module.repoUrl.trim().lowercase() }
    }
    val catalogSelections = remember(config.customExternalModules, catalogModuleByUrl) {
        config.customExternalModules.mapNotNull { customModule ->
            val catalogModule = catalogModuleByUrl[customModule.url.trim().lowercase()] ?: return@mapNotNull null
            BuildCatalogSelection(
                catalogModule = catalogModule,
                stage = CustomExternalModuleStage.normalize(customModule.stage)
            )
        }.distinctBy { it.key }
    }

    LaunchedEffect(config, rawConfig) {
        if (config != rawConfig) vm.updateBuildConfig(config)
    }

    fun openPlanLibraryPage() {
        planBackProgress = 0f
        onPlanPageVisibleChange(true)
        showPlanLibraryPage = true
    }

    fun closePlanLibraryPage() {
        showPlanLibraryPage = false
    }

    LaunchedEffect(showPlanLibraryPage) {
        if (showPlanLibraryPage) {
            onPlanPageVisibleChange(true)
        } else {
            delay(BUILD_PLAN_PAGE_EXIT_DELAY_MS)
            planBackProgress = 0f
            onPlanPageVisibleChange(false)
        }
    }

    DisposableEffect(Unit) {
        onDispose { onPlanPageVisibleChange(false) }
    }

    PredictiveBackHandler(enabled = showPlanLibraryPage && state.predictiveBackEnabled) { progress ->
        try {
            progress.collect { backEvent ->
                planBackProgress = backEvent.progress.coerceIn(0f, 1f)
            }
            closePlanLibraryPage()
        } catch (_: CancellationException) {
            planBackProgress = 0f
        }
    }

    BackHandler(enabled = showPlanLibraryPage && !state.predictiveBackEnabled) {
        closePlanLibraryPage()
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            icon = { Icon(Icons.Default.Build, null) },
            title = { Text("Confirm Build Submission") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Build config overview:", fontWeight = FontWeight.SemiBold)
                    Text("Android ${config.androidVersion} · Kernel ${config.kernelVersion}.${config.subLevel}")
                    Text("KSU: ${config.kernelsuVariant} (${config.kernelsuBranch})")
                    Text("Patch level: ${config.osPatchLevel}")
                    Text("SUSFS: ${if (!config.cancelSusfs) "Enabled" else "Disabled"} · ZRAM: ${if (config.useZram) "Enabled" else "Disabled"} · KPM: ${if (config.useKpm) "Enabled" else "Disabled"}")
                    Text("BBG: ${if (config.useBbg) "Enabled" else "Disabled"} · DDK: ${if (config.useDdk) "Enabled" else "Disabled"}")
                    Text("NTsync: ${if (config.useNtsync) "Enabled" else "Disabled"} · Networking: ${if (config.useNetworking) "Enabled" else "Disabled"}")
                    Text("Virtualization: ${virtualizationSupportLabel(config.virtualizationSupport)}")
                    Text(
                        "External modules: ${
                            if (config.useCustomExternalModules) "${config.customExternalModules.size}" else "Disabled"
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

    if (showSavePlanDialog) {
        SaveBuildPlanDialog(
            name = savePlanName,
            onNameChange = { savePlanName = it },
            onDismiss = { showSavePlanDialog = false },
            onConfirm = {
                vm.saveCurrentBuildPlan(savePlanName)
                showSavePlanDialog = false
                Toast.makeText(context, "Build plan saved", Toast.LENGTH_SHORT).show()
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
                        importPlanError = it.message ?: "Failed to parse plan code"
                    }
            },
            onApply = { preview ->
                vm.importBuildPlanToCurrentConfig(preview)
                showImportPlanDialog = false
                Toast.makeText(context, "Build plan applied", Toast.LENGTH_SHORT).show()
            },
            onSave = { preview ->
                vm.importBuildPlanToLibrary(preview)
                showImportPlanDialog = false
                Toast.makeText(context, "Build plan saved to library", Toast.LENGTH_SHORT).show()
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
                    label = "ABK Build Plan",
                    text = vm.shareBuildPlanCode(plan.config, plan.name, scope)
                )
                sharePlanTarget = null
                Toast.makeText(context, "Plan code copied", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(context, "Plan renamed", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(context, "Plan deleted", Toast.LENGTH_SHORT).show()
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
            title = { Text("Select injection stage") },
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
                                if (metadata.version.isNotBlank()) append("Version: ${metadata.version}")
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
                                text = if (stage in recommendedStages) "$stage (recommended)" else stage,
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
                    Text("Add selected")
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
                        Text("All stages")
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

    state.workflowEnablementPrompt?.let { prompt ->
        AlertDialog(
            onDismissRequest = { vm.dismissWorkflowEnablementPrompt() },
            icon = { Icon(Icons.Default.OpenInBrowser, null) },
            title = { Text("Workflow activation required") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("ABK could not confirm or enable the GitHub Actions build workflow before submission.")
                    Text("Sign in to GitHub in your browser and confirm that your account has Actions/Workflow permissions for this fork.")
                    Text("After opening the page, if you see \"Enable workflow\", enable it manually, then return to ABK and resubmit.")
                    Text(
                        text = "Check result: ${prompt.message}",
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
                    Text("Open Actions page")
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.dismissWorkflowEnablementPrompt() }) {
                    Text("Handle later")
                }
            }
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
                expanded = planToolsExpanded,
                currentSummary = buildPlanSummary(config),
                onExpandedChange = { planToolsExpanded = it },
                onSave = {
                    savePlanName = suggestedPlanName
                    showSavePlanDialog = true
                },
                onLibrary = ::openPlanLibraryPage,
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

            // ── Kernel Version Config ───────────────────────────────────────
            SectionCard(title = "Kernel Version Config") {
                DropdownField(
                    label = "Android version",
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
                    label = "Kernel version",
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
                    label = "Sub-level",
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
                    label = "Security patch level",
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
                            Text(recommended?.revision?.let { "Revision (recommended: $it)" } ?: "Revision (5.10 only)")
                        },
                        placeholder = { Text("e.g., r11") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }

            // ── KernelSU Config ─────────────────────────────────────────────
            SectionCard(title = "KernelSU Config") {
                DropdownField(
                    label = "KernelSU variant",
                    value = config.kernelsuVariant,
                    options = listOf("Official", "SukiSU", "ReSukiSU"),
                    onSelect = { vm.updateBuildConfig(config.copy(kernelsuVariant = it)) }
                )
                DropdownField(
                    label = "KSU branch",
                    value = config.kernelsuBranch.takeIf { it in ksuBranchOptions } ?: "Stable",
                    options = ksuBranchOptions,
                    onSelect = { vm.updateBuildConfig(config.copy(kernelsuBranch = it)) }
                )
            }

            // ── Feature Toggles ────────────────────────────────────────────
            SectionCard(title = "Feature Toggles") {
                SwitchRow("Enable SUSFS", !config.cancelSusfs) {
                    vm.updateBuildConfig(config.copy(cancelSusfs = !it))
                }
                SwitchRow("Enable ZRAM compression enhancement", config.useZram) {
                    vm.updateBuildConfig(config.copy(useZram = it))
                }
                SwitchRow("Enable BBG brick prevention", config.useBbg) {
                    vm.updateBuildConfig(config.copy(useBbg = it))
                }
                SwitchRow("Enable DDK brick prevention LSM", config.useDdk) {
                    vm.updateBuildConfig(config.copy(useDdk = it))
                }
                SwitchRow("Enable NTsync patch", config.useNtsync) {
                    vm.updateBuildConfig(config.copy(useNtsync = it))
                }
                SwitchRow("Enable networking enhancements (IPSet + BBR)", config.useNetworking) {
                    vm.updateBuildConfig(config.copy(useNetworking = it))
                }
                SwitchRow("Enable KPM", config.useKpm) {
                    vm.updateBuildConfig(config.copy(useKpm = it))
                }
                SwitchRow("Enable Re-Kernel driver (experimental)", config.useRekernel) {
                    vm.updateBuildConfig(config.copy(useRekernel = it))
                }
                DropdownField(
                    label = "Virtualization support",
                    value = config.virtualizationSupport,
                    options = virtualizationSupportOptions,
                    onSelect = { vm.updateBuildConfig(config.copy(virtualizationSupport = it)) }
                )
                SwitchRow("Enable OnePlus 8E support", config.suppOp) {
                    vm.updateBuildConfig(config.copy(suppOp = it))
                }
            }

            // ── ZRAM Extended Options ───────────────────────────────────────
            AnimatedVisibility(config.useZram) {
                SectionCard(title = "ZRAM Extended Options") {
                    SwitchRow("Enable full algorithm support (LZO/LZ4/ZSTD, etc.)", config.zramFullAlgo) {
                        vm.updateBuildConfig(config.copy(zramFullAlgo = it))
                    }
                    if (!config.zramFullAlgo) {
                        OutlinedTextField(
                            value = config.zramExtraAlgos,
                            onValueChange = { vm.updateBuildConfig(config.copy(zramExtraAlgos = it)) },
                            label = { Text("Custom ZRAM algorithms") },
                            placeholder = { Text("e.g., lzo,lz4,deflate,zstd") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }
            }

            // ── KPM Extended Options ────────────────────────────────────────
            AnimatedVisibility(config.useKpm) {
                SectionCard(title = "KPM Extended Options") {
                    OutlinedTextField(
                        value = config.kpmPassword,
                        onValueChange = { vm.updateBuildConfig(config.copy(kpmPassword = it)) },
                        label = { Text("KPM super password (optional)") },
                        placeholder = { Text("Leave blank to use default password") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }

            // ── Custom External Modules ─────────────────────────────────────
            SectionCard(title = "Custom External Modules") {
                SwitchRow("Enable custom external modules", config.useCustomExternalModules) {
                    vm.updateBuildConfig(config.copy(useCustomExternalModules = it))
                }
                AnimatedVisibility(config.useCustomExternalModules) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (catalogSelections.isNotEmpty()) {
                            Text(
                                text = "Add from module repository",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            catalogSelections.forEach { selection ->
                                val merged = selection.catalogModule
                                val module = merged.module
                                key(selection.key) {
                                    AnimatedVisibility(
                                        visible = selection.key !in removingCatalogModuleKeys,
                                        enter = fadeIn() + expandVertically(),
                                        exit = fadeOut() + shrinkVertically()
                                    ) {
                                        ExpressiveListItem(
                                            title = module.catalogModuleTitle(),
                                            subtitle = buildString {
                                                append("${selection.stage} · Source: ${merged.sources.joinToString(", ")}")
                                                if (module.version.isNotBlank()) append(" · v${module.version}")
                                                appendLine()
                                                append(module.description.ifBlank { module.repoUrl })
                                            },
                                            leadingIcon = Icons.Default.CheckCircle,
                                            trailingContent = {
                                                TextButton(
                                                    onClick = {
                                                        if (selection.key in removingCatalogModuleKeys) return@TextButton
                                                        removingCatalogModuleKeys =
                                                            (removingCatalogModuleKeys + selection.key).distinct()
                                                        coroutineScope.launch {
                                                            delay(CATALOG_MODULE_REMOVE_DELAY_MS)
                                                            vm.removeCustomExternalModule(module.repoUrl, selection.stage)
                                                            removingCatalogModuleKeys =
                                                                removingCatalogModuleKeys - selection.key
                                                        }
                                                    },
                                                    enabled = selection.key !in removingCatalogModuleKeys
                                                ) {
                                                    Text("Remove")
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = customModuleUrl,
                            onValueChange = { customModuleUrl = it },
                            label = { Text("Repository URL") },
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
                            Text(if (state.validatingCustomExternalModule) "Checking…" else "Validate module")
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
                                            contentDescription = "Dismiss module error",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }

                        val manualModules = config.customExternalModules.filter {
                            catalogModuleByUrl[it.url.trim().lowercase()] == null
                        }
                        manualModules.forEach { module ->
                            val catalogModule = catalogModuleByUrl[module.url.trim().lowercase()]
                            ExpressiveListItem(
                                title = catalogModule?.module?.catalogModuleTitle()
                                    ?: CustomExternalModuleStage.normalize(module.stage),
                                subtitle = buildString {
                                    append(CustomExternalModuleStage.normalize(module.stage))
                                    catalogModule?.sources?.takeIf { it.isNotEmpty() }?.let {
                                        append(" · ${it.joinToString(", ")}")
                                    }
                                    appendLine()
                                    append(module.url)
                                },
                                leadingIcon = Icons.Default.Extension,
                                trailingContent = {
                                    IconButton(
                                        onClick = {
                                            vm.removeCustomExternalModule(module.url, module.stage)
                                        }
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete module")
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // ── Optional Config ─────────────────────────────────────────────
            SectionCard(title = "Optional Config") {
                OutlinedTextField(
                    value = config.version,
                    onValueChange = { vm.updateBuildConfig(config.copy(version = it)) },
                    label = { Text("Custom version name (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                ConfigPreviewText(versionPreview)
                OutlinedTextField(
                    value = config.buildTime,
                    onValueChange = { vm.updateBuildConfig(config.copy(buildTime = it)) },
                    label = { Text("Custom build time (optional)") },
                    placeholder = { Text("Blank/N = current UTC time") },
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
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                if (state.isLoading) {
                    LoadingIndicator(Modifier.size(24.dp))
                } else {
                    Icon(Icons.Default.RocketLaunch, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.build_submit))
                }
            }

            // Error
            state.error?.let { err ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text(err, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f))
                        IconButton(onClick = { vm.clearError() }) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss error", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            Spacer(Modifier.height(80.dp))
            }
        }

        AnimatedVisibility(
            visible = showPlanLibraryPage,
            enter = fadeIn(animationSpec = motionScheme.defaultEffectsSpec()),
            exit = fadeOut(animationSpec = motionScheme.fastEffectsSpec()),
            modifier = childPageModifier
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = BUILD_PLAN_BACK_SCRIM_ALPHA * visualPlanBackProgress))
            )
        }

        AnimatedVisibility(
            visible = showPlanLibraryPage,
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
                        translationX = planBackOffsetPx * visualPlanBackProgress
                        scaleX = 1f - BUILD_PLAN_BACK_SCALE_DELTA * visualPlanBackProgress
                        scaleY = 1f - BUILD_PLAN_BACK_SCALE_DELTA * visualPlanBackProgress
                        alpha = 1f - 0.06f * visualPlanBackProgress
                        shape = RoundedCornerShape(planBackCorner)
                        clip = visualPlanBackProgress > 0.01f
                    }
            ) {
                BuildPlanPageBackground(
                    backgroundUri = state.customBackgroundUri,
                    backgroundImageEnabled = state.backgroundImageEnabled
                )
                Scaffold(
                    containerColor = Color.Transparent,
                    topBar = {
                        ExpressiveTopBar(
                            title = "Plan Library",
                            navigationIcon = {
                                IconButton(onClick = ::closePlanLibraryPage) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "Back to build config")
                                }
                            }
                        )
                    }
                ) { padding ->
                    BuildPlanLibraryPage(
                        plans = state.buildPlans,
                        onApply = {
                            vm.applyBuildPlan(it)
                            closePlanLibraryPage()
                            Toast.makeText(context, "Plan applied. You can still modify it.", Toast.LENGTH_SHORT).show()
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

@Composable
private fun BuildPlanPageBackground(
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
private fun BuildPlanToolsCard(
    plansCount: Int,
    expanded: Boolean,
    currentSummary: String,
    onExpandedChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onLibrary: () -> Unit,
    onShare: () -> Unit,
    onImport: () -> Unit
) {
    ExpressiveSectionCard(
        title = "Build Plans",
        subtitle = "Save, share or import the current build configuration.",
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
                        contentDescription = if (expanded) "Collapse build plans" else "Expand build plans"
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
                            Text("Save")
                        }
                        OutlinedButton(
                            onClick = onLibrary,
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) {
                            Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Library")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onShare,
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Share")
                        }
                        OutlinedButton(
                            onClick = onImport,
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) {
                            Icon(Icons.Default.Download, null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Import")
                        }
                    }
                    Text(
                        text = if (plansCount > 0) "$plansCount saved plan(s)" else "No saved plans",
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
        title = { Text("Save plan") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                label = { Text("Plan name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Save")
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
        title = { Text("Import plan") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = code,
                    onValueChange = onCodeChange,
                    label = { Text("ABKP2 plan code") },
                    placeholder = { Text("Paste a plan code starting with ABKP2:") },
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
                    Text("Parse")
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onSave(preview) }) {
                        Text("Save")
                    }
                    Button(onClick = { onApply(preview) }) {
                        Text("Apply")
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
        title = { Text("Share plan") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ExpressiveListItem(
                    title = plan.name.ifBlank { "Current plan" },
                    subtitle = buildPlanSummary(plan.config),
                    leadingIcon = Icons.Default.FolderOpen
                )
                Text(
                    text = "Full plan includes Android version, kernel version, patch level and feature settings. Features-only plan keeps the current kernel version when imported.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = { onShare(BuildPlanShareScope.FULL) }) {
                Text("Full plan")
            }
        },
        dismissButton = {
            TextButton(onClick = { onShare(BuildPlanShareScope.FEATURES_ONLY) }) {
                Text("Features only")
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
                title = "No saved plans",
                subtitle = "Save your current build config as a plan first.",
                icon = Icons.Default.FolderOpen
            ) {
                Text(
                    text = "Once saved, you can apply, share, rename or delete plans here.",
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
                Text("Apply/Edit")
            }
            OutlinedButton(
                onClick = onShare,
                modifier = Modifier.weight(1f).height(42.dp)
            ) {
                Icon(Icons.Default.Share, null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text("Share")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onRename,
                modifier = Modifier.weight(1f).height(42.dp)
            ) {
                Icon(Icons.Default.Edit, null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text("Rename")
            }
            OutlinedButton(
                onClick = onDelete,
                modifier = Modifier.weight(1f).height(42.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.Delete, null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text("Delete")
            }
        }
    }
}

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
        title = { Text("Rename plan") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                label = { Text("Plan name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Save")
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
        title = { Text("Delete plan") },
        text = { Text("Delete \"${plan.name}\"? This will not affect the current build config.") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Text("Delete")
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
        title = "Config preview",
        subtitle = preview,
        leadingIcon = Icons.Default.Visibility,
        modifier = Modifier.fillMaxWidth()
    )
}

private fun copyTextToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}

private fun buildPlanSummary(config: KernelBuildConfig): String {
    val android = config.androidVersion.removePrefix("android").ifBlank { config.androidVersion }
    val enabled = mutableListOf<String>()
    if (!config.cancelSusfs) enabled += "SUSFS"
    if (config.useZram) enabled += "ZRAM"
    if (config.useBbg) enabled += "BBG"
    if (config.useDdk) enabled += "DDK"
    if (config.useNtsync) enabled += "NTsync"
    if (config.useNetworking) enabled += "Networking"
    if (config.useKpm) enabled += "KPM"
    if (config.useRekernel) enabled += "Re-Kernel"
    if (config.virtualizationSupport != "off") {
        enabled += "Virt: ${virtualizationSupportLabel(config.virtualizationSupport)}"
    }
    val featureSummary = enabled.ifEmpty { listOf("Base config") }.joinToString("、")
    val externalModuleCount = if (config.useCustomExternalModules) config.customExternalModules.size else 0
    return "${config.kernelVersion}.${config.subLevel} · Android $android · ${config.osPatchLevel}\n" +
        "${config.kernelsuVariant} / ${config.kernelsuBranch} · $featureSummary · External modules: $externalModuleCount"
}

private fun buildPlanScopeLabel(scope: BuildPlanShareScope): String = when (scope) {
    BuildPlanShareScope.FULL -> "Full plan"
    BuildPlanShareScope.FEATURES_ONLY -> "Features only"
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
        subtitle = "Triggers GitHub Actions and automatically packages img, AK3, manager, and SUSFS module.",
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
                label = if (!config.cancelSusfs) "SUSFS ON" else "SUSFS OFF",
                icon = Icons.Default.Extension,
                color = if (!config.cancelSusfs) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline
            )
            if (config.virtualizationSupport != "off") {
                ExpressiveStatusChip(
                    label = "Virtualization ${virtualizationSupportLabel(config.virtualizationSupport)}",
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
                    label = "Networking",
                    icon = Icons.Default.Language,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            ExpressiveStatusChip(
                label = if (isRecommended) "Device recommended" else buildStatusLabel(status),
                icon = if (isRecommended) Icons.Default.AutoAwesome else Icons.Default.RunCircle,
                color = if (isRecommended) MaterialTheme.colorScheme.tertiary else buildStatusColor(status)
            )
        }
    )
}

private fun virtualizationSupportLabel(value: String): String = when (value) {
    "off" -> "Off"
    "on" -> "On"
    "678" -> "Slot 6/7/8"
    "123" -> "Slot 1/2/3"
    "345" -> "Slot 3/4/5"
    else -> value
}

private fun buildVersionPreview(config: KernelBuildConfig): String {
    val compact = config.version.filterNot { it.isWhitespace() }
    if (compact.isBlank()) {
        return "Preview: blank = workflow default local version"
    }
    val cleanVersion = compact.replace(Regex("""^[0-9]+\.[0-9]+\.[0-9]+"""), "")
    val preview = "${config.kernelVersion}.${config.subLevel}$cleanVersion"
    return "Preview: $preview"
}

private fun buildTimePreview(buildTime: String): String {
    val input = buildTime.trim()
    if (input.isBlank() || input.equals("N", ignoreCase = true)) {
        val sample = ZonedDateTime.now(ZoneOffset.UTC).format(BUILD_TIME_FORMATTER)
        return "Preview: workflow run UTC time (e.g., $sample)"
    }
    return "Preview: KBUILD_BUILD_TIMESTAMP=$input"
}

private val BUILD_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss z yyyy", Locale.US)

@Composable
private fun BuildStatusBanner(status: BuildStatus, progress: BuildProgress) {
    val (icon, text, color) = when (status) {
        BuildStatus.QUEUED -> Triple(Icons.Default.Queue, "Build queued, waiting for runner…", MaterialTheme.colorScheme.tertiary)
        BuildStatus.IN_PROGRESS -> Triple(Icons.Default.RunCircle, "Build in progress…", MaterialTheme.colorScheme.secondary)
        BuildStatus.SUCCESS -> Triple(Icons.Default.CheckCircle, "Build successful!", MaterialTheme.colorScheme.primary)
        BuildStatus.FAILURE -> Triple(Icons.Default.Error, "Build failed", MaterialTheme.colorScheme.error)
        BuildStatus.CANCELLED -> Triple(Icons.Default.Cancel, "Build cancelled", MaterialTheme.colorScheme.outline)
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
                Text("Workflow progress", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
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
                            "${progress.steps.size - 8} more steps tracked in background",
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
            Triple(Icons.Default.Error, MaterialTheme.colorScheme.error, "Failed")
        step.status == "completed" ->
            Triple(Icons.Default.CheckCircle, MaterialTheme.colorScheme.primary, "Done")
        step.status == "in_progress" ->
            Triple(Icons.Default.Sync, MaterialTheme.colorScheme.tertiary, "In progress")
        else -> Triple(Icons.Default.RadioButtonUnchecked, MaterialTheme.colorScheme.outline, "Waiting")
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        Text(step.name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1)
        Text(label, color = color, style = MaterialTheme.typography.labelSmall)
    }
}

private data class BuildCatalogModule(
    val module: ModuleCatalogItem,
    val sources: List<String>
)

private data class BuildCatalogSelection(
    val catalogModule: BuildCatalogModule,
    val stage: String
) {
    val key: String = "${catalogModule.module.repoUrl.trim().lowercase()}|${CustomExternalModuleStage.normalize(stage)}"
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

@Composable
fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    ExpressiveSectionCard(
        title = title,
        subtitle = when (title) {
            "Kernel Version Config" -> "Use device-detected recommended parameters to avoid version mismatch."
            "KernelSU Config" -> "Choose the kernel privilege solution and corresponding branch."
            "Feature Toggles" -> "Enable module features as needed. Fewer changes make troubleshooting easier."
            "ZRAM Extended Options" -> "Add extra kernel support for memory compression algorithms."
            "KPM Extended Options" -> "Optional security parameters for KPM functionality."
            "Custom External Modules" -> "Executes setup.sh from external repository root at specified stages."
            else -> "These fields are persisted and will not reset on next launch."
        },
        icon = when (title) {
            "Kernel Version Config" -> Icons.Default.Memory
            "KernelSU Config" -> Icons.Default.Shield
            "Feature Toggles" -> Icons.Default.Tune
            "ZRAM Extended Options" -> Icons.Default.Compress
            "KPM Extended Options" -> Icons.Default.Key
            "Custom External Modules" -> Icons.Default.Extension
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
    BuildStatus.IDLE -> "Ready"
    BuildStatus.QUEUED -> "Queued"
    BuildStatus.IN_PROGRESS -> "Building"
    BuildStatus.SUCCESS -> "Success"
    BuildStatus.FAILURE -> "Failed"
    BuildStatus.CANCELLED -> "Cancelled"
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    ExpressiveSwitchItem(
        title = label,
        checked = checked,
        onCheckedChange = onCheckedChange
    )
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
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                val text = if (opt == recommendedValue) "$opt (recommended)" else opt
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = { onSelect(opt); expanded = false },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}
