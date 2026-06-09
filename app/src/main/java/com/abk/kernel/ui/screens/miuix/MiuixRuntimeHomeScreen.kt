package com.abk.kernel.ui.screens.miuix

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abk.kernel.R
import com.abk.kernel.data.model.AbkRuntimeStatus
import com.abk.kernel.utils.RootUtils
import com.abk.kernel.viewmodel.MainViewModel
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

@Composable
fun MiuixRuntimeHomeScreen(
    vm: MainViewModel,
    outerPadding: PaddingValues
) {
    val state by vm.uiState.collectAsState()
    val scrollBehavior = LocalMiuixScrollBehavior.current
    var showManagerPatchPage by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.runtimeNavigationEnabled, state.rootGranted) {
        if (state.runtimeNavigationEnabled) vm.refreshAbkRuntimeStatus()
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(outerPadding)
                .then(
                    if (scrollBehavior != null) Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
                    else Modifier
                )
                .padding(horizontal = 12.dp).padding(bottom = 80.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // ── 管理器状态 Hero ──
            MiuixRuntimeStatusHeader(
                runtimeStatus = state.abkRuntimeStatus,
                hasNativeManagerPermission = state.hasNativeManagerPermission,
                loading = state.abkRuntimeLoading,
                error = state.abkRuntimeError,
                onRefresh = vm::refreshAbkRuntimeStatus,
                onOpenManagerPatch = { showManagerPatchPage = true }
            )

            state.abkRuntimeStatus?.let { runtimeStatus ->
                // ── 管理器信息 ──
                MiuixRuntimeManagerCard(runtimeStatus)

                // ── 构建参数 ──
                MiuixRuntimeBuildParamsCard(runtimeStatus)
            }

            Spacer(Modifier.height(80.dp))
        }
    }

    // ── 管理器补丁页面（子页面叠加）──
    AnimatedVisibility(
        visible = showManagerPatchPage,
        enter = slideInHorizontally(tween(350)) { it } + fadeIn(tween(350)),
        exit = slideOutHorizontally(tween(350)) { -it / 3 } + fadeOut(tween(350))
    ) {
        MiuixRuntimePatchScreen(
            rootGranted = state.rootGranted,
            runtimeVariant = state.abkRuntimeStatus?.manager?.variant.orEmpty(),
            onClose = { showManagerPatchPage = false }
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// 管理器状态 Hero 卡片
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun MiuixRuntimeStatusHeader(
    runtimeStatus: AbkRuntimeStatus?,
    hasNativeManagerPermission: Boolean,
    loading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onOpenManagerPatch: () -> Unit
) {
    val active = runtimeStatus != null && hasNativeManagerPermission

    if (active) {
        // ── 活跃状态：KSU 风格深色卡片 ──
        Card(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenManagerPatch),
            colors = CardDefaults.defaultColors(color = Color(0xFF1A3825))
        ) {
            Box(Modifier.fillMaxWidth()) {
                Box(
                    Modifier.fillMaxSize().offset(20.dp, 24.dp),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    Icon(
                        modifier = Modifier.size(100.dp),
                        imageVector = Icons.Filled.CheckCircle,
                        tint = Color(0xFF36D167).copy(alpha = 0.7f),
                        contentDescription = null
                    )
                }
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.runtime_manager_active),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = runtimeStatus.let {
                            val managerName = it.manager?.displayName?.takeIf { n -> n.isNotBlank() } ?: "Root"
                            "$managerName · ABK ${it.abkVersion.ifBlank { "unknown" }} · ${stringResource(R.string.runtime_module_count, it.modules.size)}"
                        },
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                    runtimeStatus.let {
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            MiuixChip("schema ${it.schema}", colorScheme.primary)
                            if (it.abkCommit.isNotBlank()) MiuixChip(it.abkCommit, colorScheme.primary.copy(alpha = 0.7f))
                        }
                    }
                }
            }
        }
    } else {
        // ── 未激活状态：普通卡片，照搬原版部分激活样式 ──
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(
                        Icons.Filled.Error,
                        null,
                        tint = colorScheme.onSurfaceVariantActions,
                        modifier = Modifier.size(22.dp)
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = when {
                                runtimeStatus != null -> stringResource(R.string.runtime_manager_inactive_title)
                                else -> stringResource(R.string.runtime_manager_inactive_title)
                            },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.onSurface
                        )
                        Text(
                            text = runtimeStatus?.let {
                                if (!hasNativeManagerPermission && !error.isNullOrBlank()) error
                                else {
                                    val managerName = it.manager?.displayName?.takeIf { n -> n.isNotBlank() } ?: "Root"
                                    "$managerName · ABK ${it.abkVersion.ifBlank { "unknown" }} · ${stringResource(R.string.runtime_module_count, it.modules.size)}"
                                }
                            } ?: (error ?: stringResource(R.string.runtime_inactive_desc)),
                            fontSize = 13.sp,
                            color = colorScheme.onSurfaceVariantSummary
                        )
                    }
                }

                if (runtimeStatus == null) {
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onRefresh,
                        enabled = !loading,
                        colors = ButtonDefaults.buttonColors(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (loading) {
                            CircularProgressIndicator(size = 20.dp, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.runtime_recheck))
                        }
                    }
                }

                runtimeStatus?.let {
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        MiuixChip("schema ${it.schema}", colorScheme.primary)
                        if (it.abkCommit.isNotBlank()) MiuixChip(it.abkCommit, colorScheme.primary.copy(alpha = 0.7f))
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// 管理器信息卡片
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun MiuixRuntimeManagerCard(runtimeStatus: AbkRuntimeStatus) {
    val manager = runtimeStatus.manager ?: return
    val backend = runtimeStatus.runtimeBackend

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.Memory, null, tint = colorScheme.primary, modifier = Modifier.size(20.dp))
                Text(
                    stringResource(R.string.runtime_manager_title),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.onSurface
                )
            }
            Spacer(Modifier.height(10.dp))

            RuntimeRow(stringResource(R.string.runtime_type), manager.displayName.ifBlank { manager.variant })
            RuntimeRow(stringResource(R.string.runtime_version), manager.version)
            RuntimeRow(stringResource(R.string.runtime_source), runtimeBackendLabel(manager.backend))
            runtimeStatus.workMode.takeIf { it.isNotBlank() }?.let { workMode ->
                RuntimeRow(stringResource(R.string.runtime_work_mode), runtimeWorkModeLabel(workMode))
            }

            if (backend != null && backend != manager) {
                Spacer(Modifier.height(4.dp))
                HorizontalDivider()
                Spacer(Modifier.height(4.dp))
                RuntimeRow(stringResource(R.string.runtime_backend), backend.displayName.ifBlank { backend.variant })
                RuntimeRow(stringResource(R.string.runtime_backend_version), backend.version)
                RuntimeRow(stringResource(R.string.runtime_compat_layer), runtimeBackendLabel(backend.backend))
            }

            // 诊断信息
            val diagnostics = manager.diagnostics.plus(backend?.diagnostics.orEmpty()).distinct()
            diagnostics.forEach { message ->
                Text(
                    text = message,
                    fontSize = 13.sp,
                    color = colorScheme.error
                )
            }

            // 能力标签
            val chips = manager.capabilities
                .plus(backend?.capabilities.orEmpty())
                .map { runtimeCapabilityLabel(it) }
                .ifEmpty { listOf("Root Shell") }
                .distinct()
            Spacer(Modifier.height(6.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                chips.forEach { label -> MiuixChip(label, colorScheme.primary) }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// 构建参数卡片
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun MiuixRuntimeBuildParamsCard(runtimeStatus: AbkRuntimeStatus) {
    val build = runtimeStatus.build
    val systemKernelVersion = remember { RootUtils.getKernelVersion() }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.Tune, null, tint = colorScheme.primary, modifier = Modifier.size(20.dp))
                Text(
                    stringResource(R.string.runtime_build_params_title),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.onSurface
                )
            }
            Spacer(Modifier.height(10.dp))

            if (build == null) {
                Text(
                    text = stringResource(R.string.runtime_old_schema),
                    fontSize = 14.sp,
                    color = colorScheme.onSurfaceVariantSummary
                )
                return@Column
            }

            RuntimeRow("Android", build.androidVersion)
            RuntimeRow(
                stringResource(R.string.runtime_target_kernel),
                listOf(build.kernelVersion, build.subLevel).filter { it.isNotBlank() }.joinToString(".")
            )
            RuntimeRow(stringResource(R.string.build_kernel_version), systemKernelVersion)
            RuntimeRow(stringResource(R.string.runtime_patch_level), build.osPatchLevel)
            RuntimeRow(stringResource(R.string.runtime_revision), build.revision)
            RuntimeRow("KernelSU", listOf(build.kernelsuVariant, build.kernelsuBranch).filter { it.isNotBlank() }.joinToString(" / "))
            RuntimeRow(stringResource(R.string.runtime_build_time), build.buildTime)
            RuntimeRow(stringResource(R.string.runtime_virtualization), build.virtualizationSupport)
            RuntimeRow(stringResource(R.string.runtime_zram_extra_algos), build.zramExtraAlgos)
            RuntimeRow("ABK", listOf(runtimeStatus.abkVersion, runtimeStatus.abkCommit).filter { it.isNotBlank() }.joinToString(" · "))

            // 特性标签
            val features = build.features
                .filterValues { it }
                .keys
                .map { runtimeFeatureLabel(it) }
                .ifEmpty { listOf(stringResource(R.string.runtime_basic_config)) }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                features.forEach { MiuixChip(it, colorScheme.primary.copy(alpha = 0.7f)) }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// 管理器补丁页面
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun MiuixRuntimePatchScreen(
    rootGranted: Boolean,
    runtimeVariant: String,
    onClose: () -> Unit
) {
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "管理器补丁配置",
                navigationIcon = { IconButton(onClick = onClose) { Icon(MiuixIcons.Back, null) } }
            )
        },
        containerColor = colorScheme.surface
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // 管理器补丁配置卡片
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Root 设备",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = colorScheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    if (rootGranted) {
                        Text(
                            "管理器补丁可用",
                            fontSize = 14.sp,
                            color = colorScheme.onSurfaceVariantSummary
                        )
                    } else {
                        Text(
                            "需要 Root 权限才能使用补丁功能",
                            fontSize = 14.sp,
                            color = colorScheme.error
                        )
                    }
                    if (runtimeVariant.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "管理器变体",
                                fontSize = 13.sp,
                                color = colorScheme.onSurfaceVariantSummary
                            )
                            Spacer(Modifier.width(8.dp))
                            MiuixChip(runtimeVariant, colorScheme.primary)
                        }
                    }
                }
            }

            Spacer(Modifier.height(80.dp))
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// 共享辅助组件
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun RuntimeRow(label: String, value: String) {
    if (value.isBlank()) return
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.width(84.dp)
        )
        Text(
            text = value,
            fontSize = 13.sp,
            color = colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun MiuixChip(label: String, color: Color) {
    Box(
        Modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(label, fontSize = 12.sp, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun runtimeFeatureLabel(key: String): String = when (key) {
    "use_zram" -> "ZRAM"
    "use_bbg" -> "BBG"
    "use_ddk" -> "DDK"
    "use_ntsync" -> "NTsync"
    "use_networking" -> stringResource(R.string.runtime_feature_networking)
    "use_kpm" -> "KPM"
    "use_rekernel" -> "Re-Kernel"
    "enable_susfs" -> "SUSFS"
    "supp_op" -> "SukiSU SUS_SU"
    "zram_full_algo" -> stringResource(R.string.runtime_feature_zram_full_algo)
    "cancel_susfs" -> stringResource(R.string.runtime_feature_cancel_susfs)
    else -> key
}

@Composable
private fun runtimeCapabilityLabel(key: String): String = when (key) {
    "root_shell" -> "Root Shell"
    "native_manager" -> stringResource(R.string.runtime_cap_native_manager)
    "root_policy" -> stringResource(R.string.runtime_cap_root_policy)
    "superuser_profiles" -> stringResource(R.string.runtime_cap_superuser_profiles)
    "lkm" -> "LKM"
    "late_load" -> "Late Load"
    "safe_mode" -> stringResource(R.string.runtime_cap_safe_mode)
    "modules" -> stringResource(R.string.runtime_cap_modules)
    "module_control" -> stringResource(R.string.runtime_cap_module_control)
    "susfs" -> "SUSFS"
    "kpm" -> "KPM"
    "features" -> stringResource(R.string.runtime_cap_features)
    else -> if (key == internalRuntimeControlCapability()) stringResource(R.string.runtime_cap_abk_control) else key
}

@Composable
private fun runtimeBackendLabel(backend: String): String = when (backend) {
    "native" -> stringResource(R.string.runtime_backend_native)
    "ksud" -> stringResource(R.string.runtime_backend_ksud)
    "su" -> stringResource(R.string.runtime_backend_su)
    "kernel" -> stringResource(R.string.runtime_backend_kernel)
    else -> backend
}

private fun runtimeWorkModeLabel(workMode: String): String = when (workMode) {
    "lkm" -> "LKM"
    "built-in" -> "Built-in"
    else -> workMode
}

private fun internalRuntimeControlCapability(): String =
    intArrayOf(97, 98, 107, 95, 99, 111, 110, 116, 114, 111, 108)
        .map { it.toChar() }
        .joinToString("")
