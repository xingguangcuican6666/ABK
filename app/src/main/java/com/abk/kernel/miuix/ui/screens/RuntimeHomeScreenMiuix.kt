package com.abk.kernel.miuix.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abk.kernel.BuildConfig
import com.abk.kernel.R
import com.abk.kernel.data.model.AbkRuntimeBuildInfo
import com.abk.kernel.data.model.AbkRuntimeManagerInfo
import com.abk.kernel.data.model.AbkRuntimeStatus
import com.abk.kernel.miuix.util.BlurredBar
import com.abk.kernel.miuix.util.rememberBlurBackdrop
import com.abk.kernel.ui.navigation3.Navigator
import com.abk.kernel.ui.navigation3.Route
import com.abk.kernel.viewmodel.MainViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
@Composable
fun RuntimeHomeScreenMiuix(
    vm: MainViewModel,
    outerPadding: PaddingValues = PaddingValues(0.dp),
    onSwitchToClassic: () -> Unit,
    navigator: Navigator,
) {
    val state by vm.uiState.collectAsState()
    val scrollBehavior = MiuixScrollBehavior()

    val surfaceColor = MiuixTheme.colorScheme.surface
    val backdrop = rememberBlurBackdrop(state.miuixBlurEnabled, surfaceColor)
    val barColor = if (backdrop != null) Color.Transparent else surfaceColor

    LaunchedEffect(Unit) {
        if (state.rootGranted && state.runtimeNavigationEnabled) vm.refreshAbkRuntimeStatus()
    }

    Scaffold(
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
        topBar = {
            BlurredBar(backdrop, surfaceColor) {
                TopAppBar(
                    color = barColor,
                    title = "AnyBase Kernel",
                    scrollBehavior = scrollBehavior,
                    actions = {
                        IconButton(onClick = onSwitchToClassic) {
                            Icon(
                                imageVector = Icons.Default.SwapHoriz,
                                contentDescription = stringResource(R.string.nav_status)
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
                    .padding(horizontal = 12.dp)
                    .overScrollVertical()
                    .scrollEndHaptic(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Spacer(Modifier.height(padding.calculateTopPadding() + 16.dp))

                RuntimeStatusHeroCardMiuix(
                    runtimeStatus = state.abkRuntimeStatus,
                    hasNativeManagerPermission = state.hasNativeManagerPermission,
                    loading = state.abkRuntimeLoading,
                    error = state.abkRuntimeError,
                    abkVersion = BuildConfig.VERSION_NAME,
                    themeMode = state.themeMode,
                    onRefresh = vm::refreshAbkRuntimeStatus,
                    onClick = { navigator.push(Route.ManagerPatch) }
                )

                state.abkRuntimeStatus?.let { runtimeStatus ->
                    RuntimeManagerCardMiuix(runtimeStatus)
                    RuntimeBuildParametersCardMiuix(runtimeStatus)
                }

                Spacer(Modifier.height(160.dp + outerPadding.calculateBottomPadding()))
            }
        }
    }
}

@Composable
private fun RuntimeStatusHeroCardMiuix(
    runtimeStatus: AbkRuntimeStatus?,
    hasNativeManagerPermission: Boolean,
    loading: Boolean,
    error: String?,
    abkVersion: String,
    themeMode: String,
    onRefresh: () -> Unit,
    onClick: () -> Unit,
) {
    val isDark = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val active = runtimeStatus != null && hasNativeManagerPermission
    val containerColor = if (active) {
        if (isDark) Color(0xFF193822) else Color(0xFFDDF5E6)
    } else {
        if (isDark) Color(0xFF381A18) else Color(0xFFF9EEEC)
    }
    val contentColor = if (isDark) Color.White else Color(0xFF1A1A1A)
    val descColor = if (isDark) Color.White.copy(alpha = 0.8f) else Color(0xFF1A1A1A).copy(alpha = 0.8f)
    val bgIconTint = if (active) Color(0xFF35D267) else Color(0xFFD03636)

    Card(
        colors = CardDefaults.defaultColors(color = containerColor),
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        showIndication = true,
        pressFeedbackType = PressFeedbackType.Tilt
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .offset(50.dp, 38.dp),
                contentAlignment = Alignment.BottomEnd
            ) {
                Icon(
                    modifier = Modifier.size(170.dp),
                    imageVector = if (active) Icons.Rounded.CheckCircleOutline else Icons.Rounded.ErrorOutline,
                    tint = bgIconTint,
                    contentDescription = null
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = if (active) {
                        stringResource(R.string.runtime_manager_active)
                    } else {
                        stringResource(R.string.runtime_manager_inactive_title)
                    },
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor
                )
                Spacer(Modifier.height(2.dp))

                val subtitleText = if (runtimeStatus != null) {
                    val managerName = runtimeStatus.manager?.displayName?.takeIf { it.isNotBlank() } ?: "Root"
                    "$managerName · ABK $abkVersion · ${stringResource(R.string.runtime_module_count, runtimeStatus.modules.size)}"
                } else {
                    error ?: stringResource(R.string.runtime_inactive_desc)
                }
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = subtitleText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = descColor
                )

                if (runtimeStatus != null && !hasNativeManagerPermission && !error.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = error,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = descColor
                    )
                }

                if (runtimeStatus == null) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                progress = null,
                                colors = ProgressIndicatorDefaults.progressIndicatorColors(
                                    foregroundColor = contentColor,
                                    backgroundColor = contentColor.copy(alpha = 0.2f)
                                ),
                                strokeWidth = 2.dp,
                                size = 14.dp
                            )
                        }
                        Text(
                            modifier = Modifier.weight(1f),
                            text = stringResource(R.string.runtime_recheck),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = descColor
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                } else {
                    Spacer(Modifier.height(if (active) 63.dp else 38.dp))
                }
            }

            if (runtimeStatus != null) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .border(
                                border = BorderStroke(1.dp, MiuixTheme.colorScheme.primary.copy(alpha = 0.35f)),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MiuixTheme.colorScheme.primary
                            )
                            Text(
                                text = "schema ${runtimeStatus.schema}",
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.primary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    if (runtimeStatus.abkCommit.isNotBlank()) {
                        RuntimeChipMiuix(label = runtimeStatus.abkCommit, secondary = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun RuntimeManagerCardMiuix(runtimeStatus: AbkRuntimeStatus) {
    val manager = runtimeStatus.manager ?: return
    val backend = runtimeStatus.runtimeBackend

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Memory,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.runtime_manager_title),
                        style = MiuixTheme.textStyles.subtitle,
                        color = MiuixTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.runtime_manager_desc),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RuntimeInfoRowMiuix(
                    label = stringResource(R.string.runtime_type),
                    value = manager.displayName.ifBlank { manager.variant }
                )
                RuntimeInfoRowMiuix(
                    label = stringResource(R.string.runtime_version),
                    value = manager.version
                )
                RuntimeInfoRowMiuix(
                    label = stringResource(R.string.runtime_source),
                    value = runtimeBackendLabel(manager.backend)
                )
                runtimeStatus.workMode.takeIf { it.isNotBlank() }?.let { workMode ->
                    RuntimeInfoRowMiuix(
                        label = stringResource(R.string.runtime_work_mode),
                        value = runtimeWorkModeLabel(workMode)
                    )
                }
                if (backend != null && backend != manager) {
                    Spacer(Modifier.height(2.dp))
                    RuntimeInfoRowMiuix(
                        label = stringResource(R.string.runtime_backend),
                        value = backend.displayName.ifBlank { backend.variant }
                    )
                    RuntimeInfoRowMiuix(
                        label = stringResource(R.string.runtime_backend_version),
                        value = backend.version
                    )
                    RuntimeInfoRowMiuix(
                        label = stringResource(R.string.runtime_compat_layer),
                        value = runtimeBackendLabel(backend.backend)
                    )
                }
            }

            val diagnostics = manager.diagnostics
                .plus(backend?.diagnostics.orEmpty())
                .distinct()
            diagnostics.forEach { message ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = message,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.error
                )
            }

            val chips = manager.capabilities
                .plus(backend?.capabilities.orEmpty())
                .map { runtimeCapabilityLabel(it) }
                .ifEmpty { listOf("Root Shell") }
                .distinct()

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                chips.forEach { label ->
                    RuntimeChipMiuix(label = label, secondary = true)
                }
            }
        }
    }
}

@Composable
private fun RuntimeBuildParametersCardMiuix(runtimeStatus: AbkRuntimeStatus) {
    val build = runtimeStatus.build

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.runtime_build_params_title),
                        style = MiuixTheme.textStyles.subtitle,
                        color = MiuixTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.runtime_build_params_desc),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            if (build == null) {
                Text(
                    text = stringResource(R.string.runtime_old_schema),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary
                )
                return@Column
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RuntimeInfoRowMiuix(label = "Android", value = build.androidVersion)
                RuntimeInfoRowMiuix(
                    label = stringResource(R.string.runtime_target_kernel),
                    value = listOf(build.kernelVersion, build.subLevel).filter { it.isNotBlank() }.joinToString(".")
                )
                RuntimeInfoRowMiuix(
                    label = stringResource(R.string.runtime_patch_level),
                    value = build.osPatchLevel
                )
                RuntimeInfoRowMiuix(
                    label = stringResource(R.string.runtime_revision),
                    value = build.revision
                )
                RuntimeInfoRowMiuix(
                    label = "KSU",
                    value = listOf(build.kernelsuVariant, build.kernelsuBranch).filter { it.isNotBlank() }.joinToString(" / ")
                )
                RuntimeInfoRowMiuix(
                    label = stringResource(R.string.runtime_build_time),
                    value = build.buildTime
                )
                RuntimeInfoRowMiuix(
                    label = stringResource(R.string.runtime_virtualization),
                    value = build.virtualizationSupport
                )
                RuntimeInfoRowMiuix(
                    label = stringResource(R.string.runtime_zram_extra_algos),
                    value = build.zramExtraAlgos
                )
                RuntimeInfoRowMiuix(
                    label = "ABK",
                    value = listOf(runtimeStatus.abkVersion, runtimeStatus.abkCommit).filter { it.isNotBlank() }.joinToString(" · ")
                )
            }

            val features = build.features
                .filterValues { it }
                .keys
                .map { runtimeFeatureLabel(it) }
                .ifEmpty { listOf(stringResource(R.string.runtime_basic_config)) }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                features.forEach { feature ->
                    RuntimeChipMiuix(label = feature, secondary = true)
                }
            }
        }
    }
}

@Composable
private fun RuntimeInfoRowMiuix(label: String, value: String) {
    if (value.isBlank()) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.width(82.dp)
        )
        Text(
            text = value,
            style = MiuixTheme.textStyles.body1,
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RuntimeChipMiuix(label: String, secondary: Boolean = false, bold: Boolean = false) {
    val accentColor = if (secondary) {
        MiuixTheme.colorScheme.onSurfaceVariantSummary
    } else {
        MiuixTheme.colorScheme.primary
    }
    Row(
        modifier = Modifier
            .padding(horizontal = if (bold) 6.dp else 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.body2,
            color = accentColor,
            fontSize = if (bold) 14.sp else 12.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium
        )
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
    else -> key
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

@Composable
fun ManagerPatchScreenMiuix(
    rootGranted: Boolean,
    hasNativeManagerPermission: Boolean,
    runtimeVariant: String,
    backgroundUri: String?,
    backgroundImageEnabled: Boolean,
    onBack: () -> Unit,
    onFeedback: (String, Boolean) -> Unit = { _, _ -> },
) {
    AbkRootPatchScreenMiuix(
        rootGranted = rootGranted,
        hasNativeManagerPermission = hasNativeManagerPermission,
        runtimeVariant = runtimeVariant,
        backgroundUri = backgroundUri,
        backgroundImageEnabled = backgroundImageEnabled,
        onBack = onBack,
        onBackEnabledChange = {},
        onFeedback = onFeedback,
    )
}
