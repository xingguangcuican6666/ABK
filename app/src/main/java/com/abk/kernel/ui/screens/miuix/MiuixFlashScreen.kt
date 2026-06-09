package com.abk.kernel.ui.screens.miuix

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abk.kernel.R
import com.abk.kernel.data.model.*
import com.abk.kernel.ui.screens.flash.flashButtonLabelRes
import com.abk.kernel.utils.DownloadUtils
import com.abk.kernel.utils.FlashFilter
import com.abk.kernel.utils.RootUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.abk.kernel.utils.FlashFilterKernelKind
import com.abk.kernel.utils.FlashFilterWorkflowState
import com.abk.kernel.viewmodel.MainViewModel
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.icon.extended.Pause
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun MiuixFlashScreen(vm: MainViewModel, outerPadding: PaddingValues, onOpenFlashDetail: (Long) -> Unit = {}, onOpenPrebuiltDetail: (Long) -> Unit = {}) {
    val state by vm.uiState.collectAsState()
    val rootGranted = state.rootGranted
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var contentTab by rememberSaveable { mutableIntStateOf(0) }
    var terminalState by remember { mutableStateOf<MiuixTerminalState?>(null) }
    LaunchedEffect(Unit) { if (state.prebuiltGkiEnabled || !state.isLoggedIn) vm.loadPrebuiltGkiReleases() }
    var parameterTarget by remember { mutableStateOf<Long?>(null) }
    var deleteTarget by remember { mutableStateOf<WfGroup?>(null) }
    var deleteRemoteRun by remember { mutableStateOf(false) }
    var deleteFileTarget by remember { mutableStateOf<DownloadedArtifact?>(null) }
    var showFilter by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf(FlashFilter()) }

    val artifacts = remember(state.artifacts, state.isLoggedIn) {
        if (!state.isLoggedIn) emptyList()
        else state.artifacts.filter { !it.expired && DownloadUtils.classifyCategory(DownloadUtils.classifyArtifact(it.name)) != null }
    }
    val workflowGroups = remember(artifacts, state.downloadedArtifacts) {
        groupArtifactsByWorkflow(artifacts, state.downloadedArtifacts)
    }

    // Apply filter to workflow groups
    val filteredWorkflowGroups: List<WfGroup> = remember(workflowGroups, filter) {
        workflowGroups.filter { group ->
            val hasKernel = group.categories.contains(ArtifactCategory.KERNEL)
            if (hasKernel && !filter.kernelEnabled) return@filter false
            val names = group.artifacts.joinToString(" ") { it.name.lowercase() }
            if (filter.kernelKinds.isNotEmpty()) {
                val matchKind = filter.kernelKinds.any { kind ->
                    when (kind) {
                        FlashFilterKernelKind.ResuKisu -> "resukisu" in names
                        FlashFilterKernelKind.SukiSu -> "sukisu" in names && "resukisu" !in names
                        FlashFilterKernelKind.Official -> "kernelsu" in names && "sukisu" !in names && "resukisu" !in names
                        FlashFilterKernelKind.None -> "kernelsu" !in names && "sukisu" !in names && "resukisu" !in names
                    }
                }
                if (!matchKind) return@filter false
            }
            true
        }
    }

    val scrollBehavior = LocalMiuixScrollBehavior.current
    Column(Modifier.fillMaxSize().padding(outerPadding).padding(horizontal = 12.dp).then(
        if (scrollBehavior != null) Modifier.nestedScroll(scrollBehavior.nestedScrollConnection) else Modifier
    )) {
        Spacer(Modifier.height(8.dp))

        // Hero card
        val buildSt = state.kernelBuildStatus
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(if (rootGranted) Icons.Filled.FlashOn else Icons.Filled.FolderOpen, null, tint = colorScheme.onSurface, modifier = Modifier.size(22.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(if (rootGranted) stringResource(R.string.flash_artifact_center) else stringResource(R.string.flash_file_center), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = colorScheme.onSurface)
                        Text(if (rootGranted) stringResource(R.string.flash_artifact_center_desc) else stringResource(R.string.flash_file_center_desc), fontSize = 12.sp, color = colorScheme.onSurfaceVariantSummary)
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    HeroChip(stringResource(R.string.flash_source_artifacts_count, artifacts.size), Icons.Filled.CloudDownload, colorScheme.primary)
                    HeroChip(stringResource(R.string.flash_downloaded_count, state.downloadedArtifacts.size), Icons.Filled.Inventory2, colorScheme.primary.copy(alpha = 0.7f))
                    if (buildSt !in setOf(BuildStatus.IN_PROGRESS, BuildStatus.QUEUED)) {
                        HeroChip(
                            when (buildSt) {
                                BuildStatus.SUCCESS -> stringResource(R.string.build_success_bang)
                                BuildStatus.FAILURE -> stringResource(R.string.build_failed)
                                BuildStatus.CANCELLED -> stringResource(R.string.build_cancelled)
                                else -> stringResource(R.string.flash_build_waiting)
                            },
                            Icons.Filled.RunCircle,
                            when (buildSt) {
                                BuildStatus.SUCCESS -> colorScheme.primary
                                BuildStatus.FAILURE -> colorScheme.error
                                else -> colorScheme.onSurfaceVariantActions
                            }
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        // Tabs
        if (state.prebuiltGkiEnabled && state.isLoggedIn) {
            TabRowWithContour(tabs = listOf(stringResource(R.string.flash_tab_workflows), stringResource(R.string.flash_prebuilt_gki)), selectedTabIndex = contentTab, onTabSelected = { contentTab = it }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
        }

        if (contentTab == 0) {
            // Refresh + Filter (workflows only)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.loadRecentRuns() }, colors = ButtonDefaults.buttonColors(), modifier = Modifier.weight(1f)) { Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.flash_refresh_artifacts)) }
                IconButton(onClick = { showFilter = true }) { Icon(Icons.Filled.FilterList, null) }
            }
            Spacer(Modifier.height(8.dp))

            // Active downloads card
            val activeDownloads = state.activeDownloadTasks
            val pendingAuto = state.pendingAutoDownloadRunId.takeIf { it > 0L }
            if (activeDownloads.isNotEmpty() || pendingAuto != null) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Filled.Download, null, tint = colorScheme.onSurface, modifier = Modifier.size(20.dp))
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.flash_download_tasks_title), fontSize = 16.sp, fontWeight = FontWeight.Medium, color = colorScheme.onSurface)
                                Text(stringResource(R.string.flash_download_tasks_desc), fontSize = 13.sp, color = colorScheme.onSurfaceVariantSummary)
                            }
                        }
                        if (pendingAuto != null) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("#$pendingAuto", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colorScheme.onSurface)
                                Text(stringResource(R.string.flash_download_waiting_auto), fontSize = 12.sp, color = colorScheme.onSurfaceVariantSummary)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { IconButton(onClick = { vm.cancelAutoDownloads(pendingAuto) }) { Icon(MiuixIcons.Pause, stringResource(R.string.flash_stop_auto_download), tint = colorScheme.error) } }
                            }
                        }
                        activeDownloads.forEachIndexed { i, task ->
                            if (i > 0 || pendingAuto != null) HorizontalDivider()
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(task.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(task.runTitle, fontSize = 12.sp, color = colorScheme.onSurfaceVariantSummary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    TagChip(if (task.automatic) stringResource(R.string.flash_auto_download_badge) else stringResource(R.string.flash_manual_download_badge), if (task.automatic) colorScheme.primary.copy(alpha = 0.7f) else colorScheme.primary)
                                }
                                LinearProgressIndicator(progress = (task.progress / 100f).coerceIn(0f, 1f), modifier = Modifier.fillMaxWidth())
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(stringResource(R.string.flash_download_progress, task.progress), fontSize = 12.sp, color = colorScheme.onSurfaceVariantSummary)
                                    IconButton(onClick = { vm.cancelDownload(task.key) }) {
                                        Icon(MiuixIcons.Pause, stringResource(R.string.flash_cancel_download), tint = colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            if (filteredWorkflowGroups.isEmpty() && workflowGroups.isNotEmpty()) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Filled.FilterList, null, tint = colorScheme.onSurface, modifier = Modifier.size(20.dp))
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.flash_filter_empty), fontSize = 16.sp, fontWeight = FontWeight.Medium, color = colorScheme.onSurface)
                            }
                        }
                        Text(stringResource(R.string.empty_state_build_artifacts_hint), fontSize = 12.sp, color = colorScheme.onSurfaceVariantSummary)
                    }
                }
            } else if (workflowGroups.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.FolderOpen, null, modifier = Modifier.size(48.dp), tint = colorScheme.onSurfaceVariantActions)
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.flash_empty_flash_title), fontSize = 16.sp, fontWeight = FontWeight.Medium, color = colorScheme.onSurface)
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.flash_empty_flash_desc), fontSize = 14.sp, color = colorScheme.onSurfaceVariantSummary)
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filteredWorkflowGroups, key = { it.runId }) { group -> WorkflowCard(group, vm, onOpenFlashDetail, onParameters = { parameterTarget = group.runId }, onDelete = { deleteTarget = group; deleteRemoteRun = false }) }
                }
            }
        } else {
            // Prebuilt GKI
            val releases = state.prebuiltGkiReleases
            val loading = state.isLoadingPrebuiltGkiReleases
            val localFiles = state.downloadedArtifacts.filter { it.runId == PREBUILT_GKI_RUN_ID }

            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    PrebuiltHeader(releases.size, loading) { vm.loadPrebuiltGkiReleases(force = true) }
                }
                when {
                    loading -> item { Box(Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator(); Spacer(Modifier.height(16.dp)); Text(stringResource(R.string.flash_loading_release), fontSize = 14.sp, color = colorScheme.onSurfaceVariantSummary) } } }
                    releases.isEmpty() -> item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Filled.CloudDownload, null, modifier = Modifier.size(48.dp), tint = colorScheme.onSurfaceVariantActions); Spacer(Modifier.height(12.dp)); Text(stringResource(R.string.flash_empty_prebuilt_title), fontSize = 16.sp, fontWeight = FontWeight.Medium, color = colorScheme.onSurface); Spacer(Modifier.height(4.dp)); Text(stringResource(R.string.flash_empty_prebuilt_desc), fontSize = 14.sp, color = colorScheme.onSurfaceVariantSummary) } } }
                    else -> items(releases, key = { it.id }) { release ->
                        Card(Modifier.fillMaxWidth().clickable { onOpenPrebuiltDetail(release.id) }) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Filled.CloudDownload, null, tint = colorScheme.primary, modifier = Modifier.size(20.dp))
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(release.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Text("${release.tagName} · ${release.publishedAt.take(10)}", fontSize = 13.sp, color = colorScheme.onSurfaceVariantSummary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    TagChip(if (release.assetCount > 0) stringResource(R.string.flash_asset_count, release.assetCount) else stringResource(R.string.flash_asset_load_later), colorScheme.primary)
                                    TagChip(stringResource(R.string.flash_manual_download), colorScheme.primary.copy(alpha = 0.7f))
                                    TagChip(stringResource(R.string.flash_filter_by_release), colorScheme.primary.copy(alpha = 0.6f))
                                }
                            }
                        }
                    }
                }
                if (localFiles.isNotEmpty()) {
                    item { Text(stringResource(R.string.flash_local_files), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colorScheme.onSurfaceVariantActions, modifier = Modifier.padding(start = 4.dp, top = 4.dp)) }
                    items(localFiles, key = { it.id }) { a ->
                        val installable = a.isInstallableApk()
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Filled.CheckCircle, null, tint = colorScheme.primary, modifier = Modifier.size(18.dp))
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(a.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(formatSize(a.sizeBytes), fontSize = 12.sp, color = colorScheme.onSurfaceVariantSummary)
                                    }
                                    IconButton(onClick = { deleteFileTarget = a }, modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Filled.Delete, null, tint = colorScheme.error, modifier = Modifier.size(18.dp))
                                    }
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (installable) {
                                        Button(
                                            onClick = { installManagerApk(context, scope, a, rootGranted) { terminalState = it } },
                                            colors = ButtonDefaults.buttonColorsPrimary(),
                                            modifier = Modifier.weight(1f).height(38.dp)
                                        ) {
                                            Icon(Icons.Filled.InstallMobile, null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(stringResource(R.string.flash_install), fontSize = 13.sp)
                                        }
                                    } else {
                                        Button(
                                            onClick = { copyPath(context, a) },
                                            colors = ButtonDefaults.buttonColors(),
                                            modifier = Modifier.weight(1f).height(38.dp)
                                        ) {
                                            Icon(Icons.Filled.ContentCopy, null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(stringResource(R.string.flash_copy_path), fontSize = 13.sp)
                                        }
                                    }
                                    if (rootGranted) {
                                        when (a.type) {
                                            ArtifactType.KERNEL_IMG,
                                            ArtifactType.ANYKERNEL3,
                                            ArtifactType.SUSFS_MODULE -> Button(
                                                onClick = { flashItem(context, scope, a, true, RootUtils.Ak3SlotTarget.CURRENT) { terminalState = it } },
                                                colors = ButtonDefaults.buttonColors(
                                                    if (a.type == ArtifactType.KERNEL_IMG) colorScheme.error
                                                    else colorScheme.primary
                                                ),
                                                modifier = Modifier.weight(1f).height(38.dp)
                                            ) {
                                                Icon(
                                                    if (a.type == ArtifactType.SUSFS_MODULE) Icons.Filled.Extension else Icons.Filled.FlashOn,
                                                    null, modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Text(stringResource(flashButtonLabelRes(a.type)), fontSize = 13.sp)
                                            }
                                            else -> {}
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

    // Delete workflow dialog
    deleteTarget?.let { group ->
        WindowDialog(title = if (group.runId == PREBUILT_GKI_RUN_ID) stringResource(R.string.flash_delete_prebuilt_files) else stringResource(R.string.flash_delete_workflow_record), show = true, onDismissRequest = { deleteTarget = null; deleteRemoteRun = false }) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (group.runId == PREBUILT_GKI_RUN_ID) stringResource(R.string.flash_delete_prebuilt_msg) else stringResource(R.string.flash_delete_workflow_msg, if (group.runNumber > 0) "#${group.runNumber}" else "#${group.runId}"), fontSize = 14.sp, color = colorScheme.onSurfaceVariantSummary)
                if (group.runId > 0) {
                    Row(Modifier.fillMaxWidth().clickable { deleteRemoteRun = !deleteRemoteRun }, verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = deleteRemoteRun, onCheckedChange = { deleteRemoteRun = it })
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.flash_delete_remote_workflow), fontSize = 14.sp, color = colorScheme.onSurface)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(text = stringResource(R.string.cancel), onClick = { deleteTarget = null; deleteRemoteRun = false }, modifier = Modifier.weight(1f))
                Button(onClick = { vm.deleteWorkflowArtifacts(group.runId, deleteRemoteRun); deleteTarget = null; deleteRemoteRun = false }, colors = ButtonDefaults.buttonColors(
                    colorScheme.error), modifier = Modifier.weight(1f)) { Text(stringResource(R.string.delete)) }
            }
        }
    }

    // Filter dialog
    if (showFilter) {
        WindowDialog(title = stringResource(R.string.flash_filters), show = true, onDismissRequest = { showFilter = false }) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.flash_filters_desc), fontSize = 12.sp, color = colorScheme.onSurfaceVariantSummary)
                Spacer(Modifier.height(4.dp))
                SwitchPreference(title = stringResource(R.string.flash_filter_kernel), checked = filter.kernelEnabled, onCheckedChange = { filter = filter.copy(kernelEnabled = it) })
                AnimatedVisibility(visible = filter.kernelEnabled, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                    Column { FlashFilterKernelKind.entries.forEach { kind ->
                        SwitchPreference(title = stringResource(kind.labelRes()), checked = kind in filter.kernelKinds, onCheckedChange = { add -> filter = filter.copy(kernelKinds = if (add) filter.kernelKinds + kind else filter.kernelKinds - kind) })
                    } }
                }
                HorizontalDivider()
                FlashFilterWorkflowState.entries.forEach { st ->
                    SwitchPreference(title = stringResource(st.labelRes()), checked = st in filter.workflowStates, onCheckedChange = { add -> filter = filter.copy(workflowStates = if (add) filter.workflowStates + st else filter.workflowStates - st) })
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(text = stringResource(R.string.reset), onClick = { filter = FlashFilter(); showFilter = false }, modifier = Modifier.weight(1f))
                TextButton(text = stringResource(R.string.close), onClick = { showFilter = false }, modifier = Modifier.weight(1f))
            }
        }
    }

    // Parameter summary dialog
    parameterTarget?.let { runId ->
        LaunchedEffect(runId) { vm.loadBuildParameterSummary(runId) }
        val summary = state.buildParameterSummaries[runId]
        val loading = runId in state.loadingBuildParameterRunIds
        val error = state.buildParameterErrors[runId]
        val group = workflowGroups.find { it.runId == runId }
        if (group != null) {
            WindowDialog(title = stringResource(R.string.flash_parameter_details), show = true, onDismissRequest = { parameterTarget = null }) {
                Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Workflow info
                    ParamSection(stringResource(R.string.flash_workflow)) {
                        ParamRow(stringResource(R.string.flash_number), if (group.runNumber > 0) "#${group.runNumber}" else "#${group.runId}")
                        ParamRow(stringResource(R.string.flash_title_label), group.runTitle)
                        ParamRow(stringResource(R.string.flash_artifacts), stringResource(R.string.flash_artifact_counts, group.artifacts.size, group.downloadedIds.count { it in group.downloadedIds }))
                    }
                    when {
                        loading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) { CircularProgressIndicator(Modifier.size(24.dp)); Text(stringResource(R.string.flash_reading_build_summary), fontSize = 14.sp, color = colorScheme.onSurfaceVariantSummary) }
                        error != null -> Text(error, fontSize = 14.sp, color = colorScheme.error)
                        summary != null -> {
                            ParamSection(stringResource(R.string.flash_version_params)) {
                                ParamRow(stringResource(R.string.build_android_version), summary.androidVersion)
                                ParamRow(stringResource(R.string.build_kernel_version), summary.kernelVersion)
                                ParamRow(stringResource(R.string.build_sub_level), summary.subLevel)
                                ParamRow(stringResource(R.string.runtime_patch_level), summary.osPatchLevel)
                                ParamRow(stringResource(R.string.flash_build_time), summary.buildTime)
                            }
                            ParamSection("KernelSU") {
                                ParamRow(stringResource(R.string.flash_ksu_variant), summary.ksuVariant)
                                ParamRow(stringResource(R.string.flash_ksu_branch), summary.ksuBranch)
                                ParamRow(stringResource(R.string.flash_susfs_status), summary.susfsEnabled)
                            }
                            ParamSection(stringResource(R.string.flash_patches_features)) {
                                ParamRow(stringResource(R.string.flash_zram), summary.zramEnabled)
                                ParamRow(stringResource(R.string.flash_zram_full_algo), summary.zramFullAlgo)
                                ParamRow(stringResource(R.string.flash_zram_extra_algos), summary.zramExtraAlgos)
                                ParamRow(stringResource(R.string.flash_bbg_patch), summary.bbgEnabled)
                                ParamRow("DDK LSM", summary.ddkLsm)
                                ParamRow(stringResource(R.string.flash_ntsync_patch), summary.ntsyncEnabled)
                                ParamRow(stringResource(R.string.runtime_feature_networking), summary.networkingEnabled)
                                ParamRow(stringResource(R.string.flash_kpm_feature), summary.kpmEnabled)
                                ParamRow(stringResource(R.string.flash_kpm_password), summary.kpmPassword)
                                ParamRow("Re-Kernel", summary.reKernelEnabled)
                                ParamRow(stringResource(R.string.runtime_virtualization), summary.virtualizationSupport)
                                ParamRow(stringResource(R.string.flash_custom_injection), summary.customInjection)
                                ParamRow("Stock Config", summary.stockConfig)
                            }
                            val extra = summary.extraRows.orEmpty()
                            if (extra.isNotEmpty()) ParamSection(stringResource(R.string.flash_extra_info)) { extra.forEach { (k, v) -> ParamRow(k, v) } }
                        }
                        else -> Text(stringResource(R.string.flash_no_parameter_details), fontSize = 14.sp, color = colorScheme.onSurfaceVariantSummary)
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (error != null && !loading) TextButton(text = stringResource(R.string.retry), onClick = { vm.loadBuildParameterSummary(runId, true) }, modifier = Modifier.weight(1f))
                    TextButton(text = stringResource(R.string.close), onClick = { parameterTarget = null }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }

    // ── Delete file confirmation ──
    deleteFileTarget?.let { item ->
        WindowDialog(
            title = stringResource(R.string.flash_delete_file),
            show = true,
            onDismissRequest = { deleteFileTarget = null }
        ) {
            Text(stringResource(R.string.flash_delete_file_msg, item.name), fontSize = 14.sp, color = colorScheme.onSurfaceVariantSummary)
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(text = stringResource(R.string.cancel), onClick = { deleteFileTarget = null }, modifier = Modifier.weight(1f))
                Button(
                    onClick = { vm.deleteDownloadedArtifact(item.filePath); deleteFileTarget = null },
                    colors = ButtonDefaults.buttonColors(colorScheme.error),
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.delete)) }
            }
        }
    }

    // ── Terminal dialog ──
    terminalState?.let { ts ->
        if (!ts.closed) {
            MiuixTerminalDialog(
                state = ts,
                onClose = { ts.closed = true; terminalState = null },
                onReboot = { ts.closed = true; terminalState = null; scope.launch(Dispatchers.IO) { RootUtils.reboot() } }
            )
        }
    }
}

// ── Workflow group card ──

private data class WfGroup(val runId: Long, val runNumber: Int, val runTitle: String, val runCreatedAt: String, val artifacts: List<BuildArtifact>, val categories: List<ArtifactCategory>, val downloadedIds: Set<Long>)

private fun groupArtifactsByWorkflow(artifacts: List<BuildArtifact>, downloaded: List<DownloadedArtifact>): List<WfGroup> {
    val dlIds = downloaded.map { it.id }.toSet()
    val groups = linkedMapOf<Long, MutableList<BuildArtifact>>()
    for (a in artifacts) groups.getOrPut(a.runId) { mutableListOf() }.add(a)
    return groups.map { (runId, list) ->
        val first = list.first()
        val cats =
            list.mapNotNull { DownloadUtils.classifyCategory(DownloadUtils.classifyArtifact(it.name)) }
                .distinct()
        WfGroup(runId = runId, runNumber = first.runNumber, runTitle = first.runTitle, runCreatedAt = first.runCreatedAt, artifacts = list, categories = cats, downloadedIds = dlIds)
    }
}

@Composable
private fun WorkflowCard(group: WfGroup, vm: MainViewModel, onClick: (Long) -> Unit, onParameters: () -> Unit, onDelete: () -> Unit) {
    val state by vm.uiState.collectAsState()
    val isActive = group.runId in (state.recentRuns.filter { it.isActive() }.map { it.id }.toSet())
    val downloadedCount = group.artifacts.count { it.id in group.downloadedIds }

    val names = group.artifacts.joinToString(" ") { it.name.lowercase() }
    val hasResuKisu = "resukisu" in names
    val hasSukiSu = !hasResuKisu && "sukisu" in names
    val hasKsu = "kernelsu" in names || "kernel" in names
    val susfsOn = "susfs" in names

    Card(Modifier.fillMaxWidth().clickable { onClick(group.runId) }) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isActive) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                else Icon(Icons.Filled.FolderSpecial, null, tint = colorScheme.primary, modifier = Modifier.size(22.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.flash_workflow_label, if (group.runNumber > 0) "#${group.runNumber}" else "#${group.runId}"), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colorScheme.onSurface)
                    Text(group.runTitle.ifBlank { "#${group.runId}" }, fontSize = 13.sp, color = colorScheme.onSurfaceVariantSummary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (!isActive) { IconButton(onClick = onParameters) { Icon(Icons.Filled.Tune, null, tint = colorScheme.onSurfaceVariantActions) } }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, null, tint = colorScheme.onSurfaceVariantActions) }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                if (hasResuKisu) TagChip(stringResource(R.string.flash_kernel_resukisu), colorScheme.primary.copy(alpha = 0.7f))
                else if (hasSukiSu) TagChip(stringResource(R.string.flash_kernel_sukisu), colorScheme.primary.copy(alpha = 0.7f))
                else if (hasKsu) TagChip(stringResource(R.string.flash_kernel_official), colorScheme.primary.copy(alpha = 0.7f))
                if (susfsOn) TagChip(stringResource(R.string.flash_chip_susfs), colorScheme.primary)
                TagChip(group.runCreatedAt.take(10), colorScheme.primary)
                TagChip(stringResource(R.string.flash_source_artifacts_count, group.artifacts.size), colorScheme.primary)
                if (downloadedCount > 0) TagChip(stringResource(R.string.flash_downloaded_count, downloadedCount), colorScheme.primary.copy(alpha = 0.7f))
                group.categories.forEach { TagChip(stringResource(categoryLabel(it)), colorScheme.primary.copy(alpha = 0.6f)) }
            }
        }
    }
}

@Composable
private fun TagChip(label: String, color: Color = colorScheme.primary) {
    Box(Modifier.background(color.copy(alpha = 0.12f), RoundedCornerShape(5.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
        Text(label, fontSize = 11.sp, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun categoryLabel(cat: ArtifactCategory): Int = when (cat) {
    ArtifactCategory.KERNEL -> R.string.flash_category_kernel
    ArtifactCategory.MANAGER -> R.string.flash_category_manager
    ArtifactCategory.MODULE -> R.string.flash_category_module
}

private fun DownloadedArtifact.isInstallableApk(): Boolean =
    type == ArtifactType.KSU_MANAGER || (type != ArtifactType.ABK_MANAGER && name.endsWith(".apk", ignoreCase = true))

@Composable
private fun HeroChip(label: String, icon: ImageVector, color: Color) {
    Row(Modifier.background(color.copy(alpha = 0.14f), RoundedCornerShape(percent = 50)).padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
        Text(label, fontSize = 12.sp, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ── Flash detail screen: see MiuixFlashDetailScreen.kt ──

// ── Prebuilt GKI detail screen: see MiuixFlashDetailScreen.kt ──

@Composable
private fun FlashFilterKernelKind.labelRes(): Int = when (this) {
    FlashFilterKernelKind.ResuKisu -> R.string.flash_filter_kernel_resukisu
    FlashFilterKernelKind.SukiSu -> R.string.flash_filter_kernel_sukisu
    FlashFilterKernelKind.Official -> R.string.flash_filter_kernel_official
    FlashFilterKernelKind.None -> R.string.flash_filter_kernel_none
}

@Composable
private fun FlashFilterWorkflowState.labelRes(): Int = when (this) {
    FlashFilterWorkflowState.Running -> R.string.flash_filter_workflow_running
    FlashFilterWorkflowState.Finished -> R.string.flash_filter_workflow_finished
}

@Composable
private fun PrebuiltHeader(count: Int, loading: Boolean, onRefresh: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Filled.CloudDownload, null, tint = colorScheme.primary, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.flash_prebuilt_gki), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = colorScheme.onSurface)
            Text(stringResource(R.string.flash_prebuilt_list_desc, count), fontSize = 12.sp, color = colorScheme.onSurfaceVariantSummary)
        }
        Button(onClick = onRefresh, colors = ButtonDefaults.buttonColors(), enabled = !loading) { Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.refresh)) }
    }
}

@Composable
private fun ParamSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colorScheme.primary)
        content()
        HorizontalDivider()
    }
}

@Composable
private fun ParamRow(label: String, value: String) {
    val display = paramDisplayValue(value)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Text(label, fontSize = 13.sp, color = colorScheme.onSurfaceVariantSummary, modifier = Modifier.width(96.dp))
        Text(display, fontSize = 13.sp, color = colorScheme.onSurface)
    }
}

@Composable
private fun paramDisplayValue(value: String): String {
    val t = value.trim()
    return when (t.lowercase()) {
        "" -> stringResource(R.string.flash_unknown)
        "true" -> stringResource(R.string.build_feature_enabled)
        "false" -> stringResource(R.string.build_virtualization_off)
        "none" -> stringResource(R.string.flash_value_none)
        "default" -> stringResource(R.string.flash_value_default)
        "set" -> stringResource(R.string.flash_value_set)
        else -> t
    }
}

private fun formatSize(bytes: Long): String { val mb = bytes / 1024.0 / 1024.0; return if (mb >= 1.0) "%.1f MB".format(mb) else "%.1f KB".format(bytes / 1024.0) }
