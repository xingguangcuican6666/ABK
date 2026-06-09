package com.abk.kernel.ui.screens.miuix

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abk.kernel.R
import com.abk.kernel.data.model.*
import com.abk.kernel.viewmodel.BuildPlanImportPreview
import com.abk.kernel.viewmodel.BuildPlanShareScope
import com.abk.kernel.viewmodel.MainViewModel
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.window.WindowDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import androidx.core.net.toUri

private val KSU_VAR = listOf(KSU_VARIANT_NONE, KSU_VARIANT_OFFICIAL, KSU_VARIANT_SUKISU, KSU_VARIANT_RESUKISU)
private val KSU_BRA = listOf(KSU_BRANCH_STABLE, KSU_BRANCH_DEV, KSU_BRANCH_LATEST, KSU_BRANCH_CUSTOM)
private val BUILD_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss z yyyy", Locale.US)
private val ZVIRT = listOf("off", "678", "123", "345")

@Composable
fun MiuixBuildScreen(vm: MainViewModel, outerPadding: PaddingValues, onOpenPlanLib: () -> Unit, onOpenQueue: () -> Unit) {
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current
    val config = state.buildConfig
    val sb = LocalMiuixScrollBehavior.current
    val isOp = config.buildTarget == BUILD_TARGET_ONEPLUS
    val gate = !state.isLoggedIn || (state.forkRepo == null)
    val needLogin = !state.isLoggedIn
    val vi = KSU_VAR.indexOf(config.kernelsuVariant).coerceAtLeast(0)
    val bi = KSU_BRA.indexOf(config.kernelsuBranch).coerceAtLeast(0)
    val zi = ZVIRT.indexOf(config.virtualizationSupport).coerceAtLeast(0)
    val sn = remember(config) { vm.suggestedBuildPlanName(config) }
    var showConfirm by remember { mutableStateOf(false) }
    var showImportPlanDialog by remember { mutableStateOf(false) }
    var importPlanCode by remember { mutableStateOf("") }
    var importPlanPreview by remember { mutableStateOf<BuildPlanImportPreview?>(null) }
    var importPlanError by remember { mutableStateOf<String?>(null) }
    var showSharePlanDialog by remember { mutableStateOf(false) }
    var customModuleUrl by remember { mutableStateOf("") }
    var pendingCustomModuleUrl by remember { mutableStateOf("") }
    var pendingCustomModuleMetadata by remember { mutableStateOf<ExternalModuleMetadata?>(null) }
    var selectedCustomModuleStages by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var editingCustomModuleUrl by remember { mutableStateOf<String?>(null) }
    var editingCustomModuleStages by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val coroutineScope = rememberCoroutineScope()

    Column(
        Modifier.fillMaxSize().padding(outerPadding)
            .then(if (sb != null) Modifier.nestedScroll(sb.nestedScrollConnection) else Modifier)
            .padding(horizontal = 12.dp).verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.height(4.dp))
        if (gate) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(if (needLogin) stringResource(R.string.build_login_required_title) else stringResource(R.string.build_fork_required_title), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = colorScheme.onSurface)
                    Spacer(Modifier.height(4.dp))
                    Text(if (needLogin) stringResource(R.string.build_login_required_desc) else stringResource(R.string.build_fork_required_desc), fontSize = 14.sp, color = colorScheme.onSurfaceVariantSummary)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = vm::openBuildOobe, colors = ButtonDefaults.buttonColorsPrimary(), modifier = Modifier.fillMaxWidth()) { Text(if (needLogin) stringResource(R.string.login_github) else stringResource(R.string.fork_action)) }
                }
            }
        } else {
            // Hero card
            val heroTitle = if (isOp) KernelSupport.onePlusDeviceLabel(config.onePlusDeviceManifest) else "${config.kernelVersion}.${config.subLevel} · ${config.androidVersion.removePrefix("android").let { "Android $it" }}"
            val heroSubtitle = if (isOp) stringResource(R.string.build_oneplus_hero_desc) else stringResource(R.string.build_hero_desc)
            val heroIcon = if (isOp) Icons.Filled.PhoneAndroid else Icons.Filled.RocketLaunch
            val chipPrimary = colorScheme.primary
            val chipSecondary = colorScheme.primary.copy(alpha = 0.72f)

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(heroIcon, null, tint = colorScheme.primary, modifier = Modifier.size(22.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(heroTitle, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = colorScheme.onSurface, maxLines = 2)
                            Text(heroSubtitle, fontSize = 12.sp, color = colorScheme.onSurfaceVariantSummary)
                        }
                    }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HeroChip(if (config.kernelsuVariant == KSU_VARIANT_NONE) stringResource(R.string.build_ksu_none) else config.kernelsuVariant, Icons.Filled.Shield, chipPrimary)
                        if (isOp) HeroChip("${config.kernelVersion} · ${config.androidVersion}", Icons.Filled.Memory, chipSecondary)
                        HeroChip(if (!config.cancelSusfs) stringResource(R.string.build_susfs_on) else stringResource(R.string.build_susfs_off), Icons.Filled.Extension, if (!config.cancelSusfs) chipSecondary else colorScheme.onSurfaceVariantActions)
                        if (!isOp) {
                            if (config.virtualizationSupport != "off") HeroChip(virtChipLabel(config.virtualizationSupport), Icons.Filled.Extension, chipSecondary)
                            if (config.useNtsync) HeroChip("NTsync", Icons.Filled.Sync, chipSecondary)
                            if (config.useNetworking) HeroChip(stringResource(R.string.build_feature_networking), Icons.Filled.Language, chipSecondary)
                        }
                        if (isOp && config.onePlusUseLz4kd) HeroChip("lz4kd", Icons.Filled.Compress, chipSecondary)
                        HeroChip(heroStatusLabel(state.kernelBuildStatus), Icons.Filled.RunCircle, heroStatusColor(state.kernelBuildStatus))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // Plan tools
            val ps = state.buildPlans.size; val qsTotal = state.buildQueue.size
            val qsPending = state.buildQueue.count { it.status == BuildQueueItemStatus.PENDING }
            var planEx by remember { mutableStateOf(false) }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Filled.FolderOpen, null, tint = colorScheme.onSurface, modifier = Modifier.size(20.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.build_plan_tools_title), fontSize = 16.sp, fontWeight = FontWeight.Medium, color = colorScheme.onSurface)
                            Text(stringResource(R.string.build_plan_tools_desc), fontSize = 13.sp, color = colorScheme.onSurfaceVariantSummary)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(sn, Modifier.weight(1f), fontSize = 13.sp, color = colorScheme.onSurfaceVariantSummary, maxLines = if (planEx) 3 else 1, overflow = TextOverflow.Ellipsis)
                        IconButton(onClick = { planEx = !planEx }) { Icon(if (planEx) MiuixIcons.ExpandLess else MiuixIcons.ExpandMore, null) }
                    }
                    AnimatedVisibility(visible = planEx, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { vm.saveCurrentBuildPlan(sn) }, colors = ButtonDefaults.buttonColorsPrimary(), modifier = Modifier.weight(1f)) { Icon(Icons.Filled.Add, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.build_save)) }
                                Button(onClick = onOpenPlanLib, colors = ButtonDefaults.buttonColors(), modifier = Modifier.weight(1f)) { Icon(Icons.Filled.FolderOpen, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.build_library)) }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = onOpenQueue, colors = ButtonDefaults.buttonColors(), modifier = Modifier.weight(1f)) { Icon(Icons.Filled.Queue, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.build_queue_short)) }
                                Button(onClick = { showSharePlanDialog = true }, colors = ButtonDefaults.buttonColors(), modifier = Modifier.weight(1f)) { Icon(Icons.Filled.Share, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.build_share)) }
                            }
                            Row(Modifier.fillMaxWidth()) { Button(onClick = { showImportPlanDialog = true }, colors = ButtonDefaults.buttonColors(), modifier = Modifier.fillMaxWidth()) { Icon(Icons.Filled.Download, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.build_import)) } }
                            Text(buildString { append(if (ps > 0) stringResource(R.string.build_saved_plans_count, ps) else stringResource(R.string.build_no_saved_plans)); append(" · "); append(if (qsTotal > 0) stringResource(R.string.build_queue_summary, qsTotal, qsPending) else stringResource(R.string.build_queue_empty)) }, fontSize = 11.sp, color = colorScheme.onSurfaceVariantActions)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // Target
            Sec(stringResource(R.string.build_target_title))
            Text(stringResource(R.string.build_target_desc), fontSize = 12.sp, color = colorScheme.onSurfaceVariantActions, modifier = Modifier.padding(start = 14.dp, bottom = 6.dp))
            TabRowWithContour(tabs = listOf(stringResource(R.string.build_target_gki), stringResource(R.string.build_target_oneplus)), selectedTabIndex = if (isOp) 1 else 0, onTabSelected = { i -> vm.updateBuildConfig(KernelSupport.normalize(config.copy(buildTarget = if (i == 0) BUILD_TARGET_GKI else BUILD_TARGET_ONEPLUS))) }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))

            // Active builds
            if (state.kernelCurrentRun != null || state.kernelActiveBuildRuns.isNotEmpty()) {
                Sec(stringResource(R.string.status_build)); val cr = state.kernelCurrentRun; val pr = state.kernelBuildProgress; val st = state.kernelBuildStatus
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        if (st.name == "IN_PROGRESS") {
                            Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(size = 20.dp, strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)); Text("${pr.percent}% · ${pr.currentStep}", fontSize = 14.sp, color = colorScheme.onSurfaceVariantSummary) }
                            if (pr.totalSteps > 0) { Spacer(Modifier.height(4.dp)); LinearProgressIndicator(progress = pr.percent / 100f); Text(stringResource(R.string.status_steps_complete, pr.completedSteps, pr.totalSteps), fontSize = 11.sp, color = colorScheme.onSurfaceVariantSummary) }
                        } else if (cr != null) Text(when (st.name) { "SUCCESS" -> stringResource(R.string.status_recent_build_success); "FAILURE" -> stringResource(R.string.status_recent_build_failed); "CANCELLED" -> stringResource(R.string.status_build_cancelled); "QUEUED" -> stringResource(R.string.status_build_waiting_runner); else -> st.name }, fontSize = 14.sp, color = colorScheme.onSurfaceVariantSummary)
                        else Text(stringResource(R.string.status_no_running_build), fontSize = 14.sp, color = colorScheme.onSurfaceVariantSummary)
                        if (cr != null) { Spacer(Modifier.height(6.dp)); if (cr.status in setOf("queued", "waiting", "in_progress")) TextButton(text = stringResource(R.string.status_cancel), onClick = { vm.cancelWorkflowRun(cr.id) }, modifier = Modifier.fillMaxWidth()) }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // Kernel config
            val rec = state.recommendedBuildConfig
            Sec(stringResource(R.string.build_kernel_version_config))
            Card(Modifier.fillMaxWidth()) {
                if (isOp) {
                    val devOpts = KernelSupport.onePlusDeviceManifestOptions; val devIdx = devOpts.indexOf(config.onePlusDeviceManifest).coerceAtLeast(0)
                    val devLabels = devOpts.map { KernelSupport.onePlusDeviceLabel(it) }
                    WindowDropdownPreference(title = stringResource(R.string.build_oneplus_device_manifest), items = devLabels, selectedIndex = devIdx, onSelectedIndexChange = { i -> val m = devOpts[i]; val p = KernelSupport.onePlusDeviceProfile(m); vm.updateBuildConfig(KernelSupport.normalize(config.copy(onePlusDeviceManifest = m, onePlusCpu = p?.cpu ?: config.onePlusCpu, androidVersion = p?.androidVersion ?: config.androidVersion, kernelVersion = p?.kernelVersion ?: config.kernelVersion))) })
                    Text(stringResource(R.string.build_oneplus_profile_desc), fontSize = 12.sp, color = colorScheme.onSurfaceVariantActions, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
                    HorizontalDivider(); BasicComponent(title = stringResource(R.string.build_oneplus_cpu), summary = config.onePlusCpu)
                    HorizontalDivider(); BasicComponent(title = stringResource(R.string.build_android_version), summary = config.androidVersion)
                    HorizontalDivider(); BasicComponent(title = stringResource(R.string.build_kernel_version), summary = config.kernelVersion)
                } else {
                    val avOpts = KernelSupport.androidVersions(); val avIdx = avOpts.indexOf(config.androidVersion).coerceAtLeast(0)
                    WindowDropdownPreference(title = stringResource(R.string.build_android_version), items = avOpts.map { if (it == rec?.androidVersion) "$it ★" else it }, selectedIndex = avIdx, onSelectedIndexChange = { i -> vm.updateBuildConfig(KernelSupport.normalize(config.copy(androidVersion = avOpts[i], kernelVersion = KernelSupport.kernelForAndroid(avOpts[i])))) })
                    HorizontalDivider()
                    val kvOpts = KernelSupport.kernelVersions(); val kvIdx = kvOpts.indexOf(config.kernelVersion).coerceAtLeast(0)
                    WindowDropdownPreference(title = stringResource(R.string.build_kernel_version), items = kvOpts.map { if (it == rec?.kernelVersion) "$it ★" else it }, selectedIndex = kvIdx, onSelectedIndexChange = { i -> vm.updateBuildConfig(KernelSupport.normalize(config.copy(androidVersion = KernelSupport.androidForKernel(kvOpts[i]), kernelVersion = kvOpts[i]))) })
                    HorizontalDivider()
                    val slOpts = remember(config.androidVersion, config.kernelVersion) { KernelSupport.subLevelOptions(config.androidVersion, config.kernelVersion) }; val slIdx = slOpts.indexOf(config.subLevel).coerceAtLeast(0)
                    WindowDropdownPreference(title = stringResource(R.string.build_sub_level), items = slOpts.map { if (it == rec?.subLevel) "$it ★" else it }, selectedIndex = slIdx, onSelectedIndexChange = { i -> vm.updateBuildConfig(KernelSupport.normalize(config.copy(subLevel = slOpts[i]))) })
                    HorizontalDivider()
                    val plOpts = remember(config.androidVersion, config.kernelVersion, config.subLevel) { KernelSupport.patchLevelOptions(config.androidVersion, config.kernelVersion, config.subLevel) }; val plIdx = plOpts.indexOf(config.osPatchLevel).coerceAtLeast(0)
                    WindowDropdownPreference(title = stringResource(R.string.build_security_patch_level), items = plOpts.map { if (it == rec?.osPatchLevel) "$it ★" else it }, selectedIndex = plIdx, onSelectedIndexChange = { i -> vm.updateBuildConfig(KernelSupport.normalize(config.copy(osPatchLevel = plOpts[i]))) })
                    if (config.kernelVersion == "5.10") { HorizontalDivider(); var rv by remember(config) { mutableStateOf(config.revision) }; TextField(value = rv, onValueChange = { rv = it; vm.updateBuildConfig(config.copy(revision = it)) }, label = stringResource(R.string.build_revision_510), modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) }
                }
            }
            Spacer(Modifier.height(12.dp))

            // KSU
            Sec("KernelSU")
            Card(Modifier.fillMaxWidth()) {
                WindowDropdownPreference(title = "Variant", items = KSU_VAR, selectedIndex = vi, onSelectedIndexChange = { i -> vm.updateBuildConfig(KernelSupport.normalize(config.copy(kernelsuVariant = KSU_VAR[i]))) })
                val noRoot = config.kernelsuVariant == KSU_VARIANT_NONE
                if (noRoot) {
                    HorizontalDivider()
                    Text(if (isOp) stringResource(R.string.build_oneplus_no_root_scheme_desc) else stringResource(R.string.build_no_root_scheme_desc), fontSize = 12.sp, color = colorScheme.onSurfaceVariantActions, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
                } else if (!isOp) {
                    HorizontalDivider()
                    WindowDropdownPreference(title = "Branch", items = KSU_BRA, selectedIndex = bi, onSelectedIndexChange = { i -> vm.updateBuildConfig(KernelSupport.normalize(config.copy(kernelsuBranch = KSU_BRA[i]))) })
                    if (config.kernelsuBranch == KSU_BRANCH_LATEST) Text(stringResource(R.string.build_ksu_branch_latest_hint), fontSize = 12.sp, color = colorScheme.onSurfaceVariantActions, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
                    if (config.kernelsuBranch == KSU_BRANCH_CUSTOM) { HorizontalDivider(); TextField(value = config.customRef, onValueChange = { vm.updateBuildConfig(config.copy(customRef = it)) }, label = stringResource(R.string.build_custom_ksu_ref), modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) }
                } else {
                    HorizontalDivider()
                    Text(stringResource(R.string.build_oneplus_ksu_branch_desc), fontSize = 12.sp, color = colorScheme.onSurfaceVariantActions, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
                }
            }
            if (!isOp) { Spacer(Modifier.height(12.dp)); Sec(stringResource(R.string.build_virtualization_support)); Card(Modifier.fillMaxWidth()) { WindowDropdownPreference(title = "Slot", items = ZVIRT, selectedIndex = zi, onSelectedIndexChange = { i -> vm.updateBuildConfig(config.copy(virtualizationSupport = ZVIRT[i])) }) } }
            Spacer(Modifier.height(12.dp))

            // Features
            Sec(stringResource(R.string.build_features))
            Card(Modifier.fillMaxWidth()) {
                val nr = config.kernelsuVariant == KSU_VARIANT_NONE
                Column {
                    if (isOp) {
                        val so = KernelSupport.onePlusSusfsSupported(config.androidVersion, config.kernelVersion); val ko = config.kernelsuVariant in setOf(KSU_VARIANT_SUKISU, KSU_VARIANT_RESUKISU); val po = !config.onePlusCpu.startsWith("mt")
                        SwitchPreference(title = stringResource(R.string.build_enable_susfs), checked = !config.cancelSusfs && so, enabled = !nr && so, onCheckedChange = { vm.updateBuildConfig(KernelSupport.normalize(config.copy(cancelSusfs = !it))) }, summary = if (!so) stringResource(R.string.build_oneplus_susfs_unsupported) else null)
                        HorizontalDivider()
                        SwitchPreference(title = stringResource(R.string.build_enable_kpm), checked = config.useKpm, enabled = ko && !nr, onCheckedChange = { vm.updateBuildConfig(KernelSupport.normalize(config.copy(useKpm = it))) })
                        HorizontalDivider()
                        SwitchPreference(title = stringResource(R.string.build_oneplus_lz4kd), checked = config.onePlusUseLz4kd, onCheckedChange = { vm.updateBuildConfig(config.copy(onePlusUseLz4kd = it)) })
                        HorizontalDivider()
                        SwitchPreference(title = stringResource(R.string.build_enable_bbg), checked = config.useBbg, onCheckedChange = { vm.updateBuildConfig(config.copy(useBbg = it)) })
                        HorizontalDivider()
                        SwitchPreference(title = stringResource(R.string.build_oneplus_bbr), checked = config.onePlusUseBbr, onCheckedChange = { vm.updateBuildConfig(config.copy(onePlusUseBbr = it)) })
                        HorizontalDivider()
                        SwitchPreference(title = stringResource(R.string.build_oneplus_proxy_optimization), checked = config.onePlusUseProxyOptimization, enabled = po, onCheckedChange = { vm.updateBuildConfig(KernelSupport.normalize(config.copy(onePlusUseProxyOptimization = it))) }, summary = if (!po) stringResource(R.string.build_oneplus_proxy_mtk_disabled) else null)
                        HorizontalDivider()
                        SwitchPreference(title = stringResource(R.string.build_oneplus_unicode_bypass), checked = config.onePlusUseUnicodeBypass, onCheckedChange = { vm.updateBuildConfig(config.copy(onePlusUseUnicodeBypass = it)) })
                    } else {
                        SwitchPreference(title = stringResource(R.string.build_enable_susfs), checked = !config.cancelSusfs, enabled = !nr, onCheckedChange = { vm.updateBuildConfig(KernelSupport.normalize(config.copy(cancelSusfs = !it))) })
                        HorizontalDivider()
                        SwitchPreference(title = stringResource(R.string.build_enable_zram), checked = config.useZram, onCheckedChange = { vm.updateBuildConfig(config.copy(useZram = it)) })
                        HorizontalDivider()
                        SwitchPreference(title = stringResource(R.string.build_enable_bbg), checked = config.useBbg, onCheckedChange = { vm.updateBuildConfig(config.copy(useBbg = it)) })
                        HorizontalDivider()
                        SwitchPreference(title = stringResource(R.string.build_enable_ddk), checked = config.useDdk, onCheckedChange = { vm.updateBuildConfig(config.copy(useDdk = it)) })
                        HorizontalDivider()
                        SwitchPreference(title = stringResource(R.string.build_enable_ntsync), checked = config.useNtsync, onCheckedChange = { vm.updateBuildConfig(config.copy(useNtsync = it)) })
                        HorizontalDivider()
                        SwitchPreference(title = stringResource(R.string.build_enable_networking), checked = config.useNetworking, onCheckedChange = { vm.updateBuildConfig(config.copy(useNetworking = it)) })
                        HorizontalDivider()
                        SwitchPreference(title = stringResource(R.string.build_enable_kpm), checked = config.useKpm, onCheckedChange = { vm.updateBuildConfig(KernelSupport.normalize(config.copy(useKpm = it))) })
                        HorizontalDivider()
                        SwitchPreference(title = stringResource(R.string.build_enable_rekernel), checked = config.useRekernel, onCheckedChange = { vm.updateBuildConfig(config.copy(useRekernel = it)) })
                        HorizontalDivider()
                        SwitchPreference(title = stringResource(R.string.build_enable_oneplus_8e), checked = config.suppOp, onCheckedChange = { vm.updateBuildConfig(config.copy(suppOp = it)) })
                    }
                }
            }

            // ZRAM / KPM options
            if (config.useZram && !isOp) { Spacer(Modifier.height(12.dp)); Sec(stringResource(R.string.build_zram_options)); Card(Modifier.fillMaxWidth()) { Column { SwitchPreference(title = stringResource(R.string.build_zram_full_algo), checked = config.zramFullAlgo, onCheckedChange = { vm.updateBuildConfig(config.copy(zramFullAlgo = it)) }); if (!config.zramFullAlgo) { HorizontalDivider(); var a by remember(config) { mutableStateOf(config.zramExtraAlgos) }; TextField(value = a, onValueChange = { a = it; vm.updateBuildConfig(config.copy(zramExtraAlgos = it)) }, label = "Extra Algos", modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) } } } }
            if (config.useKpm && !isOp) { Spacer(Modifier.height(12.dp)); Sec(stringResource(R.string.build_kpm_options)); Card(Modifier.fillMaxWidth()) { var p by remember(config) { mutableStateOf(config.kpmPassword) }; TextField(value = p, onValueChange = { p = it; vm.updateBuildConfig(config.copy(kpmPassword = it)) }, label = stringResource(R.string.build_kpm_password), modifier = Modifier.fillMaxWidth().padding(14.dp)) } }

            // Custom external modules
            Spacer(Modifier.height(12.dp))
            Sec(stringResource(R.string.build_custom_modules))
            Text(stringResource(R.string.build_section_custom_modules_desc), fontSize = 12.sp, color = colorScheme.onSurfaceVariantActions, modifier = Modifier.padding(start = 14.dp, bottom = 6.dp))
            Card(Modifier.fillMaxWidth()) {
                Column {
                    SwitchPreference(title = stringResource(R.string.build_enable_custom_modules), checked = config.useCustomExternalModules, onCheckedChange = { vm.updateBuildConfig(config.copy(useCustomExternalModules = it)) })
                    AnimatedVisibility(visible = config.useCustomExternalModules, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            val cm = remember(state.buildModuleRepositories) { mergeBuildCatalogModules(state.buildModuleRepositories) }
                            val cmByUrl = remember(cm) { cm.associateBy { it.module.repoUrl.trim().lowercase() } }
                            val groups = remember(config.customExternalModules, cmByUrl) { groupBuildCustomExternalModules(config.customExternalModules, cmByUrl) }
                            groups.forEach { group ->
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(group.displayName.let { if (it == group.url) stringResource(R.string.build_external_module_default) else it }, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colorScheme.onSurface)
                                        Text(group.subtitle(stringResource(R.string.build_stage_none), stringResource(R.string.build_source_list, "%s")), fontSize = 12.sp, color = colorScheme.onSurfaceVariantSummary)
                                    }
                                    IconButton(onClick = { editingCustomModuleUrl = group.url; editingCustomModuleStages = group.stages }) { Icon(Icons.Filled.Settings, null) }
                                    IconButton(onClick = { vm.setCustomExternalModuleStages(group.url, emptyList()) }) { Icon(Icons.Filled.Delete, null, tint = colorScheme.error) }
                                }
                            }
                            if (groups.isNotEmpty()) HorizontalDivider()
                            TextField(value = customModuleUrl, onValueChange = { customModuleUrl = it }, label = stringResource(R.string.build_repo_url), modifier = Modifier.fillMaxWidth())
                            Button(onClick = {
                                val u = customModuleUrl.trim()
                                if (u.isNotEmpty()) coroutineScope.launch { vm.checkCustomExternalModuleMetadata(u)?.let { m -> pendingCustomModuleUrl = u; pendingCustomModuleMetadata = m; selectedCustomModuleStages = m.recommendedStages.filter { s -> s in m.supportedStages }.ifEmpty { listOf(m.defaultStage) } } }
                            }, enabled = customModuleUrl.isNotBlank() && !state.validatingCustomExternalModule, colors = ButtonDefaults.buttonColorsPrimary(), modifier = Modifier.fillMaxWidth()) {
                                Text(if (state.validatingCustomExternalModule) stringResource(R.string.build_checking) else stringResource(R.string.build_check_module))
                            }
                            state.customExternalModuleError?.let { Text(it, fontSize = 12.sp, color = colorScheme.error) }
                        }
                    }
                }
            }

            // Optional config
            if (!isOp) {
                Spacer(Modifier.height(12.dp)); Sec(stringResource(R.string.build_optional_config))
                Text(stringResource(R.string.build_section_default_desc), fontSize = 12.sp, color = colorScheme.onSurfaceVariantActions, modifier = Modifier.padding(start = 14.dp, bottom = 6.dp))
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        var ver by remember(config) { mutableStateOf(config.version) }; TextField(value = ver, onValueChange = { ver = it; vm.updateBuildConfig(config.copy(version = it)) }, label = stringResource(R.string.build_custom_version_optional), modifier = Modifier.fillMaxWidth())
                        val defaultVersionPreview = stringResource(R.string.build_preview_default_version)
                        val versionPreview = remember(ver, config.kernelVersion, config.subLevel, defaultVersionPreview) {
                            val compact = ver.filterNot { it.isWhitespace() }
                            if (compact.isBlank()) defaultVersionPreview
                            else "${config.kernelVersion}.${config.subLevel}${compact.replace(Regex("^[0-9]+\\.[0-9]+\\.[0-9]+"), "")}"
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.Visibility, null, tint = colorScheme.onSurfaceVariantActions, modifier = Modifier.size(18.dp))
                            Column { Text(stringResource(R.string.build_config_preview), fontSize = 14.sp, color = colorScheme.onSurface); Text(versionPreview, fontSize = 12.sp, color = colorScheme.onSurfaceVariantSummary) }
                        }
                        var bt by remember(config) { mutableStateOf(config.buildTime) }; TextField(value = bt, onValueChange = { bt = it; vm.updateBuildConfig(config.copy(buildTime = it)) }, label = stringResource(R.string.build_custom_time_optional), modifier = Modifier.fillMaxWidth())
                        val timePreview = remember(bt) {
                            val t = bt.trim()
                            if (t.isBlank() || t == "N") {
                                val sample = ZonedDateTime.now(ZoneOffset.UTC).format(BUILD_TIME_FORMATTER)
                                "$sample"
                            } else t
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.Visibility, null, tint = colorScheme.onSurfaceVariantActions, modifier = Modifier.size(18.dp))
                            Column { Text(stringResource(R.string.build_config_preview), fontSize = 14.sp, color = colorScheme.onSurface); Text(timePreview, fontSize = 12.sp, color = colorScheme.onSurfaceVariantSummary) }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp)); Button(onClick = { showConfirm = true }, colors = ButtonDefaults.buttonColorsPrimary(), modifier = Modifier.fillMaxWidth().height(48.dp)) { Text(stringResource(R.string.build_submit)) }
        }

        if (showConfirm) {
            val nr = config.kernelsuVariant == KSU_VARIANT_NONE; val on = "✓"; val off = "✗"
            WindowDialog(title = stringResource(R.string.build_confirm_submit), show = true, onDismissRequest = { showConfirm = false }) {
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    item {
                        Text(stringResource(R.string.build_config_overview), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = colorScheme.onSurface)
                        Spacer(Modifier.height(6.dp))
                        if (isOp) {
                            Text(stringResource(R.string.build_target_line, config.buildTarget), fontSize = 13.sp, color = colorScheme.onSurfaceVariantSummary)
                            Text(stringResource(R.string.build_oneplus_device_line, KernelSupport.onePlusDeviceLabel(config.onePlusDeviceManifest)), fontSize = 13.sp, color = colorScheme.onSurfaceVariantSummary)
                            Text(stringResource(R.string.build_oneplus_kernel_line, config.androidVersion, config.kernelVersion), fontSize = 13.sp, color = colorScheme.onSurfaceVariantSummary)
                            Text("KSU: ${config.kernelsuVariant}", fontSize = 13.sp, color = colorScheme.onSurfaceVariantSummary)
                            Text(stringResource(R.string.build_feature_line, if (!config.cancelSusfs) on else off, if (config.onePlusUseLz4kd) on else off, if (config.useKpm) on else off), fontSize = 13.sp, color = colorScheme.onSurfaceVariantSummary)
                            Text(stringResource(R.string.build_oneplus_network_line, if (config.onePlusUseBbr) on else off, if (config.onePlusUseProxyOptimization) on else off, if (config.onePlusUseUnicodeBypass) on else off), fontSize = 13.sp, color = colorScheme.onSurfaceVariantSummary)
                            Text(stringResource(R.string.build_protection_line, if (config.useBbg) on else off, off), fontSize = 13.sp, color = colorScheme.onSurfaceVariantSummary)
                        } else {
                            Text(stringResource(R.string.build_kernel_line, config.androidVersion, config.kernelVersion, config.subLevel), fontSize = 13.sp, color = colorScheme.onSurfaceVariantSummary)
                            Text(if (nr) "KSU: ${config.kernelsuVariant}" else "KSU: ${config.kernelsuVariant} (${config.kernelsuBranch})", fontSize = 13.sp, color = colorScheme.onSurfaceVariantSummary)
                            Text(stringResource(R.string.build_patch_level_line, config.osPatchLevel), fontSize = 13.sp, color = colorScheme.onSurfaceVariantSummary)
                            Text(stringResource(R.string.build_feature_line, if (!config.cancelSusfs) on else off, if (config.useZram) on else off, if (config.useKpm) on else off), fontSize = 13.sp, color = colorScheme.onSurfaceVariantSummary)
                            Text(stringResource(R.string.build_protection_line, if (config.useBbg) on else off, if (config.useDdk) on else off), fontSize = 13.sp, color = colorScheme.onSurfaceVariantSummary)
                            Text(stringResource(R.string.build_sync_network_line, if (config.useNtsync) on else off, if (config.useNetworking) on else off), fontSize = 13.sp, color = colorScheme.onSurfaceVariantSummary)
                            Text(stringResource(R.string.build_virtualization_line, config.virtualizationSupport), fontSize = 13.sp, color = colorScheme.onSurfaceVariantSummary)
                            Text(stringResource(R.string.build_external_modules_line, if (config.useCustomExternalModules) "${config.customExternalModules.size} modules" else off), fontSize = 13.sp, color = colorScheme.onSurfaceVariantSummary)
                        }
                        if (state.buildQueue.isNotEmpty() || state.activeBuildRuns.isNotEmpty()) { Spacer(Modifier.height(4.dp)); Text(stringResource(R.string.build_active_queue_notice), fontSize = 12.sp, color = colorScheme.onSurfaceVariantActions) }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { TextButton(text = stringResource(R.string.cancel), onClick = { showConfirm = false }, modifier = Modifier.weight(1f)); Button(onClick = { showConfirm = false; vm.dispatchBuild(KernelSupport.normalize(config)) }, colors = ButtonDefaults.buttonColorsPrimary(), modifier = Modifier.weight(1f)) { Text(stringResource(R.string.build_submit)) } }
            }
        }
        Spacer(Modifier.height(140.dp))
    }

    // Workflow enablement prompt
    val ctx = LocalContext.current
    state.workflowEnablementPrompt?.let { p ->
        WindowDialog(title = stringResource(R.string.build_workflow_required), show = true, onDismissRequest = { vm.dismissWorkflowEnablementPrompt() }) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.build_workflow_required_desc_1), fontSize = 14.sp, color = colorScheme.onSurfaceVariantSummary)
                Text(stringResource(R.string.build_workflow_required_desc_2), fontSize = 14.sp, color = colorScheme.onSurfaceVariantSummary)
                Text(stringResource(R.string.build_workflow_required_desc_3), fontSize = 14.sp, color = colorScheme.onSurfaceVariantSummary)
                Text(p.message, fontSize = 12.sp, color = colorScheme.onSurfaceVariantActions)
            }
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(text = stringResource(R.string.build_handle_later), onClick = { vm.dismissWorkflowEnablementPrompt() }, modifier = Modifier.weight(1f))
                Button(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, p.actionUrl.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); vm.dismissWorkflowEnablementPrompt() }, colors = ButtonDefaults.buttonColorsPrimary(), modifier = Modifier.weight(1f)) { Text(stringResource(R.string.build_open_actions_page)) }
            }
        }
    }

    // Stage selection dialog for new custom module
    pendingCustomModuleMetadata?.let { metadata ->
        WindowDialog(title = stringResource(R.string.build_select_injection_stage), show = true, onDismissRequest = { pendingCustomModuleMetadata = null; pendingCustomModuleUrl = "" }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(metadata.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colorScheme.onSurface)
                if (metadata.version.isNotBlank()) Text(stringResource(R.string.module_repo_version, metadata.version), fontSize = 12.sp, color = colorScheme.onSurfaceVariantSummary)
                metadata.supportedStages.forEach { stage ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (stage in metadata.recommendedStages) "$stage ★" else stage, Modifier.weight(1f), fontSize = 14.sp, color = colorScheme.onSurface)
                        Switch(checked = stage in selectedCustomModuleStages, onCheckedChange = { c -> selectedCustomModuleStages = if (c) (selectedCustomModuleStages + stage).distinct() else selectedCustomModuleStages - stage })
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(text = stringResource(R.string.module_repo_all_stages), onClick = { vm.addCustomExternalModulesFromUrl(pendingCustomModuleUrl, metadata.supportedStages); customModuleUrl = ""; pendingCustomModuleMetadata = null; pendingCustomModuleUrl = "" }, modifier = Modifier.weight(1f))
                Button(onClick = { val s = selectedCustomModuleStages.filter { it in metadata.supportedStages }; vm.addCustomExternalModulesFromUrl(pendingCustomModuleUrl, s.ifEmpty { listOf(metadata.defaultStage) }); customModuleUrl = ""; pendingCustomModuleMetadata = null; pendingCustomModuleUrl = "" }, colors = ButtonDefaults.buttonColorsPrimary(), enabled = selectedCustomModuleStages.isNotEmpty(), modifier = Modifier.weight(1f)) { Text(stringResource(R.string.module_repo_add_selected)) }
            }
        }
    }

    // Edit stage dialog
    editingCustomModuleUrl?.let { url ->
        WindowDialog(title = stringResource(R.string.build_edit_injection_stage), show = true, onDismissRequest = { editingCustomModuleUrl = null }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(url, fontSize = 12.sp, color = colorScheme.onSurfaceVariantSummary, maxLines = 2)
                CustomExternalModuleStage.options.forEach { stage ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stage, Modifier.weight(1f), fontSize = 14.sp, color = colorScheme.onSurface)
                        Switch(checked = stage in editingCustomModuleStages, onCheckedChange = { c -> editingCustomModuleStages = if (c) (editingCustomModuleStages + stage).distinct() else editingCustomModuleStages - stage })
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(text = stringResource(R.string.cancel), onClick = { editingCustomModuleUrl = null }, modifier = Modifier.weight(1f))
                Button(onClick = { vm.setCustomExternalModuleStages(url, editingCustomModuleStages); editingCustomModuleUrl = null }, colors = ButtonDefaults.buttonColorsPrimary(), modifier = Modifier.weight(1f)) { Text(stringResource(R.string.build_save)) }
            }
        }
    }

    // Share plan dialog
    if (showSharePlanDialog) {
        val clipboardLabel = stringResource(R.string.build_plan_clipboard_label)
        val codeCopiedMsg = stringResource(R.string.build_plan_code_copied)
        WindowDialog(title = stringResource(R.string.build_share_plan), show = true, onDismissRequest = { showSharePlanDialog = false }) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(sn.ifBlank { stringResource(R.string.build_current_plan) }, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colorScheme.onSurface)
                Text(buildPlanSummaryText(config), fontSize = 13.sp, color = colorScheme.onSurfaceVariantSummary)
                Text(stringResource(R.string.build_share_plan_desc), fontSize = 12.sp, color = colorScheme.onSurfaceVariantActions)
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(text = stringResource(R.string.build_features_only), onClick = { val code = vm.shareBuildPlanCode(config, sn, BuildPlanShareScope.FEATURES_ONLY); val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager; cm.setPrimaryClip(ClipData.newPlainText(clipboardLabel, code)); Toast.makeText(context, codeCopiedMsg, Toast.LENGTH_SHORT).show(); showSharePlanDialog = false }, modifier = Modifier.weight(1f))
                Button(onClick = { val code = vm.shareBuildPlanCode(config, sn, BuildPlanShareScope.FULL); val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager; cm.setPrimaryClip(ClipData.newPlainText(clipboardLabel, code)); Toast.makeText(context, codeCopiedMsg, Toast.LENGTH_SHORT).show(); showSharePlanDialog = false }, colors = ButtonDefaults.buttonColorsPrimary(), modifier = Modifier.weight(1f)) { Text(stringResource(R.string.build_full_plan)) }
            }
        }
    }

    // Import plan dialog
    if (showImportPlanDialog) {
        val parseFailedMsg = stringResource(R.string.build_plan_parse_failed)
        WindowDialog(title = stringResource(R.string.build_import_plan), show = true, onDismissRequest = { showImportPlanDialog = false }) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TextField(value = importPlanCode, onValueChange = { importPlanCode = it; importPlanPreview = null; importPlanError = null }, label = stringResource(R.string.build_abkp2_code), modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp))
                importPlanError?.let { Text(it, fontSize = 12.sp, color = colorScheme.error) }
                importPlanPreview?.let { pv -> Column(Modifier.padding(horizontal = 4.dp)) { Text(pv.plan.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colorScheme.onSurface); Text(buildPlanSummaryText(pv.plan.config), fontSize = 13.sp, color = colorScheme.onSurfaceVariantSummary) } }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (importPlanPreview == null) Button(onClick = { runCatching { vm.parseBuildPlanCode(importPlanCode, config) }.onSuccess { importPlanPreview = it; importPlanError = null }.onFailure { importPlanPreview = null; importPlanError = it.message ?: parseFailedMsg } }, colors = ButtonDefaults.buttonColorsPrimary(), enabled = importPlanCode.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.build_parse)) }
                else { TextButton(text = stringResource(R.string.build_save), onClick = { vm.importBuildPlanToLibrary(importPlanPreview!!); showImportPlanDialog = false }, modifier = Modifier.weight(1f)); Button(onClick = { vm.importBuildPlanToCurrentConfig(importPlanPreview!!); showImportPlanDialog = false }, colors = ButtonDefaults.buttonColorsPrimary(), modifier = Modifier.weight(1f)) { Text(stringResource(R.string.build_apply)) } }
            }
        }
    }

}

// ── Helper composables ──

@Composable private fun HeroChip(label: String, icon: ImageVector, color: Color) {
    Row(Modifier.background(color.copy(alpha = 0.14f), RoundedCornerShape(percent = 50)).padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
        Text(label, fontSize = 12.sp, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable private fun virtChipLabel(value: String): String = when (value) {
    "off" -> stringResource(R.string.build_virtualization_off); "on" -> stringResource(R.string.build_virtualization_on)
    "678" -> stringResource(R.string.build_virtualization_slot_678); "123" -> stringResource(R.string.build_virtualization_slot_123)
    "345" -> stringResource(R.string.build_virtualization_slot_345); else -> value
}

@Composable private fun heroStatusLabel(status: BuildStatus): String = when (status) {
    BuildStatus.IDLE -> stringResource(R.string.build_status_ready); BuildStatus.QUEUED -> stringResource(R.string.build_queued)
    BuildStatus.IN_PROGRESS -> stringResource(R.string.build_running); BuildStatus.SUCCESS -> stringResource(R.string.build_success)
    BuildStatus.FAILURE -> stringResource(R.string.build_failed); BuildStatus.CANCELLED -> stringResource(R.string.build_cancelled)
}

@Composable private fun heroStatusColor(status: BuildStatus): Color = when (status) {
    BuildStatus.IDLE -> colorScheme.onSurfaceVariantActions; BuildStatus.QUEUED -> colorScheme.primary.copy(alpha = 0.7f)
    BuildStatus.IN_PROGRESS -> colorScheme.primary; BuildStatus.SUCCESS -> colorScheme.primary
    BuildStatus.FAILURE -> colorScheme.error; BuildStatus.CANCELLED -> colorScheme.onSurfaceVariantActions
}

@Composable private fun Sec(t: String) { Text(t, fontSize = MiuixTheme.textStyles.subtitle.fontSize, fontWeight = FontWeight.Medium, color = colorScheme.onSurfaceVariantActions, modifier = Modifier.padding(start = 14.dp, bottom = 6.dp)) }

private fun buildPlanSummaryText(config: KernelBuildConfig): String {
    val parts = mutableListOf("${config.kernelVersion}.${config.subLevel}", config.kernelsuVariant)
    if (!config.cancelSusfs) parts += "SUSFS"; if (config.useZram) parts += "ZRAM"; if (config.useKpm) parts += "KPM"; if (config.useBbg) parts += "BBG"
    return parts.joinToString(" · ")
}

// ── Custom external module helpers ──

private data class CatModule(val repo: ModuleCatalogRepository, val module: ModuleCatalogItem)
private data class ExtModuleGroup(val url: String, val stages: List<String>, val catalogModule: ModuleCatalogItem?, val displayName: String, val key: String) {
    fun subtitle(noStage: String, prefix: String): String {
        val s = stages.joinToString(", ").ifBlank { noStage }
        val src = catalogModule?.let { catRepoMap[it] }?.name ?: ""
        return if (src.isNotBlank()) prefix.format(src) + " · $s" else s
    }
}
private var catRepoMap: Map<ModuleCatalogItem, ModuleCatalogRepository> = emptyMap()

private fun mergeBuildCatalogModules(repositories: List<ModuleCatalogRepository>): List<CatModule> {
    val map = mutableMapOf<ModuleCatalogItem, ModuleCatalogRepository>()
    val list = mutableListOf<CatModule>()
    for (r in repositories) for (m in r.modules) { map[m] = r; list.add(CatModule(r, m)) }
    catRepoMap = map
    return list
}

private fun groupBuildCustomExternalModules(modules: List<CustomExternalModule>, catalogByUrl: Map<String, CatModule>): List<ExtModuleGroup> {
    val m = linkedMapOf<String, MutableList<String>>()
    for (mod in modules) m.getOrPut(mod.url.trim().lowercase()) { mutableListOf() }.add(CustomExternalModuleStage.normalize(mod.stage))
    return m.map { (url, stages) -> val cat = catalogByUrl[url]; ExtModuleGroup(url = url, stages = stages.distinct(), catalogModule = cat?.module, displayName = cat?.module?.name ?: url, key = url) }
}
