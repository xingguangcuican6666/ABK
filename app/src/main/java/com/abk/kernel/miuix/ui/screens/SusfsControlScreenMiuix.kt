@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.abk.kernel.miuix.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.abk.kernel.R
import com.abk.kernel.data.model.SusfsConfig
import com.abk.kernel.data.model.SusfsPresetOptions
import com.abk.kernel.miuix.util.BlurredBar
import com.abk.kernel.miuix.util.rememberBlurBackdrop
import com.abk.kernel.utils.SUSFS_HIDE_MOUNTS_ALL
import com.abk.kernel.utils.SUSFS_HIDE_MOUNTS_NON_SU
import com.abk.kernel.utils.SUSFS_HIDE_MOUNTS_OFF
import com.abk.kernel.utils.SUSFS_SPOOF_UNAME_BOOT_COMPLETED
import com.abk.kernel.utils.SUSFS_SPOOF_UNAME_OFF
import com.abk.kernel.utils.SUSFS_SPOOF_UNAME_POST_FS_DATA
import com.abk.kernel.utils.normalizeSusfsConfig
import com.abk.kernel.utils.parseSusfsKstatJson
import com.abk.kernel.utils.parseSusfsOpenRedirects
import com.abk.kernel.utils.parseSusfsPathRules
import com.abk.kernel.utils.parseSusfsStringList
import com.abk.kernel.utils.renderSusfsKstatJson
import com.abk.kernel.utils.renderSusfsOpenRedirects
import com.abk.kernel.utils.renderSusfsPathRules
import com.abk.kernel.utils.renderSusfsStringList
import com.abk.kernel.viewmodel.MainUiState
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun SusfsControlScreenMiuix(
    state: MainUiState,
    showRefreshLoading: Boolean,
    onApply: (SusfsConfig) -> Unit,
    onReset: () -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val runtime = state.susfsRuntimeStatus
    val support = runtime?.support
    val config = normalizeSusfsConfig(state.susfsConfig)
    val applyFailedText = stringResource(R.string.susfs_apply_failed)
    val unavailableText = stringResource(R.string.susfs_value_unavailable)
    val noOutputText = stringResource(R.string.susfs_no_output)
    val surfaceColor = MiuixTheme.colorScheme.surface
    val backdrop = rememberBlurBackdrop(state.miuixBlurEnabled, surfaceColor)
    val barColor = if (backdrop != null) Color.Transparent else surfaceColor

    var autoReplayEnabled by rememberSaveable { mutableStateOf(config.autoReplayEnabled) }
    var logEnabled by rememberSaveable { mutableStateOf(config.logEnabled) }
    var avcLogSpoofing by rememberSaveable { mutableStateOf(config.avcLogSpoofing) }
    var hideSusMountsMode by rememberSaveable { mutableStateOf(config.hideSusMountsMode) }
    var spoofUnameStage by rememberSaveable { mutableStateOf(config.spoofUnameStage) }
    var unameValue by rememberSaveable { mutableStateOf(config.unameValue) }
    var buildTimeValue by rememberSaveable { mutableStateOf(config.buildTimeValue) }
    var sdcardRootPath by rememberSaveable { mutableStateOf(config.sdcardRootPath) }
    var androidDataRootPath by rememberSaveable { mutableStateOf(config.androidDataRootPath) }
    var hideCustomRomLevel by rememberSaveable { mutableIntStateOf(config.presets.hideCustomRomLevel) }
    var emulateVoldAppDataMode by rememberSaveable { mutableIntStateOf(config.presets.emulateVoldAppDataMode) }
    var hideVendorSepolicy by rememberSaveable { mutableStateOf(config.presets.hideVendorSepolicy) }
    var hideCompatMatrix by rememberSaveable { mutableStateOf(config.presets.hideCompatMatrix) }
    var hideGapps by rememberSaveable { mutableStateOf(config.presets.hideGapps) }
    var hideRevanced by rememberSaveable { mutableStateOf(config.presets.hideRevanced) }
    var spoofCmdline by rememberSaveable { mutableStateOf(config.presets.spoofCmdline) }
    var hideLoops by rememberSaveable { mutableStateOf(config.presets.hideLoops) }
    var forceHideLsposed by rememberSaveable { mutableStateOf(config.presets.forceHideLsposed) }
    var autoTryUmount by rememberSaveable { mutableStateOf(config.presets.autoTryUmount) }
    var skipLegitMounts by rememberSaveable { mutableStateOf(config.presets.skipLegitMounts) }
    var umountForZygoteIsoService by rememberSaveable { mutableStateOf(config.presets.umountForZygoteIsoService) }
    var pathRulesText by rememberSaveable { mutableStateOf(renderSusfsPathRules(config.pathRules)) }
    var loopPathRulesText by rememberSaveable { mutableStateOf(renderSusfsPathRules(config.loopPathRules)) }
    var mapsText by rememberSaveable { mutableStateOf(renderSusfsStringList(config.maps)) }
    var mountsText by rememberSaveable { mutableStateOf(renderSusfsStringList(config.mounts)) }
    var tryUmountText by rememberSaveable { mutableStateOf(renderSusfsStringList(config.tryUmounts)) }
    var legitMountsText by rememberSaveable { mutableStateOf(renderSusfsStringList(config.legitMounts)) }
    var openRedirectText by rememberSaveable { mutableStateOf(renderSusfsOpenRedirects(config.openRedirects)) }
    var kstatJsonText by rememberSaveable { mutableStateOf(renderSusfsKstatJson(config.kstatEntries)) }
    var formError by remember { mutableStateOf<String?>(null) }
    var refreshRequested by rememberSaveable { mutableStateOf(false) }
    var refreshStarted by rememberSaveable { mutableStateOf(false) }
    val refreshText = stringResource(R.string.settings_manager_loading_title)
    val refreshTexts = remember(refreshText) {
        listOf(refreshText, refreshText, refreshText, refreshText)
    }
    val isRefreshing = showRefreshLoading || refreshRequested

    LaunchedEffect(refreshRequested) {
        if (refreshRequested && !refreshStarted && !showRefreshLoading) {
            onRefresh()
        }
    }

    LaunchedEffect(showRefreshLoading, refreshRequested, refreshStarted) {
        if (refreshRequested && showRefreshLoading) {
            refreshStarted = true
        }
        if (refreshRequested && refreshStarted && !showRefreshLoading) {
            refreshRequested = false
            refreshStarted = false
        }
    }

    LaunchedEffect(state.susfsConfig) {
        val synced = normalizeSusfsConfig(state.susfsConfig)
        autoReplayEnabled = synced.autoReplayEnabled
        logEnabled = synced.logEnabled
        avcLogSpoofing = synced.avcLogSpoofing
        hideSusMountsMode = synced.hideSusMountsMode
        spoofUnameStage = synced.spoofUnameStage
        unameValue = synced.unameValue
        buildTimeValue = synced.buildTimeValue
        sdcardRootPath = synced.sdcardRootPath
        androidDataRootPath = synced.androidDataRootPath
        hideCustomRomLevel = synced.presets.hideCustomRomLevel
        emulateVoldAppDataMode = synced.presets.emulateVoldAppDataMode
        hideVendorSepolicy = synced.presets.hideVendorSepolicy
        hideCompatMatrix = synced.presets.hideCompatMatrix
        hideGapps = synced.presets.hideGapps
        hideRevanced = synced.presets.hideRevanced
        spoofCmdline = synced.presets.spoofCmdline
        hideLoops = synced.presets.hideLoops
        forceHideLsposed = synced.presets.forceHideLsposed
        autoTryUmount = synced.presets.autoTryUmount
        skipLegitMounts = synced.presets.skipLegitMounts
        umountForZygoteIsoService = synced.presets.umountForZygoteIsoService
        pathRulesText = renderSusfsPathRules(synced.pathRules)
        loopPathRulesText = renderSusfsPathRules(synced.loopPathRules)
        mapsText = renderSusfsStringList(synced.maps)
        mountsText = renderSusfsStringList(synced.mounts)
        tryUmountText = renderSusfsStringList(synced.tryUmounts)
        legitMountsText = renderSusfsStringList(synced.legitMounts)
        openRedirectText = renderSusfsOpenRedirects(synced.openRedirects)
        kstatJsonText = renderSusfsKstatJson(synced.kstatEntries)
        formError = null
    }

    fun submit() {
        formError = null
        runCatching {
            normalizeSusfsConfig(
                SusfsConfig(
                    autoReplayEnabled = autoReplayEnabled,
                    logEnabled = logEnabled,
                    avcLogSpoofing = avcLogSpoofing,
                    hideSusMountsMode = hideSusMountsMode,
                    spoofUnameStage = spoofUnameStage,
                    unameValue = unameValue,
                    buildTimeValue = buildTimeValue,
                    sdcardRootPath = sdcardRootPath,
                    androidDataRootPath = androidDataRootPath,
                    pathRules = parseSusfsPathRules(pathRulesText),
                    loopPathRules = parseSusfsPathRules(loopPathRulesText),
                    maps = parseSusfsStringList(mapsText),
                    mounts = parseSusfsStringList(mountsText),
                    tryUmounts = parseSusfsStringList(tryUmountText),
                    legitMounts = parseSusfsStringList(legitMountsText),
                    openRedirects = parseSusfsOpenRedirects(openRedirectText),
                    kstatEntries = parseSusfsKstatJson(kstatJsonText),
                    presets = SusfsPresetOptions(
                        hideCustomRomLevel = hideCustomRomLevel,
                        hideVendorSepolicy = hideVendorSepolicy,
                        hideCompatMatrix = hideCompatMatrix,
                        hideGapps = hideGapps,
                        hideRevanced = hideRevanced,
                        spoofCmdline = spoofCmdline,
                        hideLoops = hideLoops,
                        forceHideLsposed = forceHideLsposed,
                        autoTryUmount = autoTryUmount,
                        skipLegitMounts = skipLegitMounts,
                        emulateVoldAppDataMode = emulateVoldAppDataMode,
                        umountForZygoteIsoService = umountForZygoteIsoService,
                    ),
                )
            )
        }.onSuccess(onApply).onFailure { formError = it.message ?: applyFailedText }
    }

    Scaffold(
        topBar = {
            BlurredBar(backdrop, surfaceColor) {
                TopAppBar(
                    title = stringResource(R.string.susfs_title),
                    scrollBehavior = scrollBehavior,
                    color = barColor,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = MiuixIcons.Back,
                                contentDescription = stringResource(R.string.settings_back)
                            )
                        }
                    }
                )
            }
        }
    ) { padding ->
        val listState = rememberScrollState()
        Box(
            modifier = Modifier.then(
                if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier
            )
        ) {
            PullToRefresh(
                isRefreshing = isRefreshing,
                onRefresh = {
                    if (!isRefreshing) {
                        refreshRequested = true
                    }
                },
                refreshTexts = refreshTexts,
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding() + 6.dp,
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection)
                        .verticalScroll(listState)
                        .overScrollVertical()
                        .scrollEndHaptic()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Spacer(Modifier.height(padding.calculateTopPadding() + 16.dp))

                    // Error status section
                    state.susfsError?.takeIf { it.isNotBlank() }?.let { error ->
                        MiuixSectionCard(
                            title = "状态",
                            subtitle = "SUSFS 探测或应用过程返回了错误",
                            icon = Icons.Default.Info,
                            iconColor = MiuixTheme.colorScheme.error
                        ) {
                            Text(
                                text = error,
                                color = MiuixTheme.colorScheme.error,
                                style = MiuixTheme.textStyles.body1
                            )
                        }
                    }

                    // Form error section
                    formError?.takeIf { it.isNotBlank() }?.let { error ->
                MiuixSectionCard(
                    title = "表单错误",
                    subtitle = "请先修正配置格式，再重新应用",
                    icon = Icons.Default.Info,
                    iconColor = MiuixTheme.colorScheme.error
                ) {
                    Text(
                        text = error,
                        color = MiuixTheme.colorScheme.error,
                        style = MiuixTheme.textStyles.body1
                    )
                }
            }

            // Overview section
            runtime?.let {
                MiuixSectionCard(
                    title = stringResource(R.string.susfs_section_overview),
                    subtitle = stringResource(R.string.susfs_section_overview_desc),
                    icon = Icons.Default.Extension
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MiuixStatusChip(it.kernelVersion.ifBlank { unavailableText })
                        MiuixStatusChip(it.bundledBinaryVersion)
                        MiuixStatusChip(
                            stringResource(R.string.susfs_feature_flags_count, it.featureFlags.size)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.susfs_label_kernel_version, it.kernelVersion.ifBlank { unavailableText }),
                        style = MiuixTheme.textStyles.body2
                    )
                    Text(
                        text = stringResource(R.string.susfs_label_bundled_binary, "${it.bundledBinaryVersion} (${it.bundledBinaryRef})"),
                        style = MiuixTheme.textStyles.body2
                    )
                    Text(
                        text = stringResource(R.string.susfs_label_installed_binary, it.installedBinaryPath.ifBlank { unavailableText }),
                        style = MiuixTheme.textStyles.body2
                    )
                    Text(
                        text = stringResource(R.string.susfs_label_config_path, it.configPath),
                        style = MiuixTheme.textStyles.body2
                    )
                    if (it.rawFeatureText.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = it.rawFeatureText,
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary
                        )
                    }
                }
            }

            // Actions section
            MiuixSectionCard(
                title = stringResource(R.string.susfs_section_actions),
                subtitle = stringResource(R.string.susfs_section_actions_desc),
                icon = Icons.Default.Settings
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = ::submit,
                        enabled = !state.susfsSaving,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColorsPrimary()
                    ) {
                        Text(
                            if (state.susfsSaving) stringResource(R.string.susfs_action_applying)
                            else stringResource(R.string.susfs_action_apply)
                        )
                    }
                    MiuixTextButton(
                        text = stringResource(R.string.susfs_action_reset),
                        onClick = onReset,
                        enabled = !state.susfsSaving,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                MiuixTextButton(
                    text = stringResource(R.string.susfs_action_refresh),
                    onClick = {
                        if (!isRefreshing) {
                            refreshRequested = true
                        }
                    },
                    enabled = !state.susfsSaving
                )
            }

            // Basic settings section
            MiuixSectionCard(
                title = stringResource(R.string.susfs_section_basic),
                subtitle = stringResource(R.string.susfs_section_basic_desc),
                icon = Icons.Default.Settings
            ) {
                SwitchPreference(
                    title = stringResource(R.string.susfs_toggle_auto_replay),
                    summary = stringResource(R.string.susfs_toggle_auto_replay_desc),
                    checked = autoReplayEnabled,
                    onCheckedChange = { autoReplayEnabled = it }
                )
                SwitchPreference(
                    title = stringResource(R.string.susfs_toggle_log),
                    summary = stringResource(R.string.susfs_toggle_log_desc),
                    checked = logEnabled,
                    onCheckedChange = { logEnabled = it }
                )
                if (support?.avcLogSpoofing == true) {
                    SwitchPreference(
                        title = stringResource(R.string.susfs_toggle_avc_log_spoofing),
                        summary = stringResource(R.string.susfs_toggle_avc_log_spoofing_desc),
                        checked = avcLogSpoofing,
                        onCheckedChange = { avcLogSpoofing = it }
                    )
                }
                if (support?.hideSusMountsForAll == true || support?.hideSusMountsForNonSu == true) {
                    val hideMountOptions = listOf(
                        SUSFS_HIDE_MOUNTS_OFF to stringResource(R.string.susfs_option_off),
                        SUSFS_HIDE_MOUNTS_ALL to stringResource(R.string.susfs_option_all_processes),
                        SUSFS_HIDE_MOUNTS_NON_SU to stringResource(R.string.susfs_option_non_su_processes),
                    )
                    val hideMountIndex = hideMountOptions.indexOfFirst { it.first == hideSusMountsMode }
                        .takeIf { it >= 0 } ?: 0
                    OverlayDropdownPreference(
                        title = stringResource(R.string.susfs_hide_mount_mode),
                        items = hideMountOptions.map { it.second },
                        selectedIndex = hideMountIndex,
                        renderInRootScaffold = true,
                        onSelectedIndexChange = { index ->
                            hideSusMountsMode = hideMountOptions[index].first
                        }
                    )
                }
                if (support?.setUname == true) {
                    val unameStageOptions = listOf(
                        SUSFS_SPOOF_UNAME_OFF to stringResource(R.string.susfs_option_off),
                        SUSFS_SPOOF_UNAME_POST_FS_DATA to stringResource(R.string.susfs_option_post_fs_data),
                        SUSFS_SPOOF_UNAME_BOOT_COMPLETED to stringResource(R.string.susfs_option_boot_completed),
                    )
                    val unameStageIndex = unameStageOptions.indexOfFirst { it.first == spoofUnameStage }
                        .takeIf { it >= 0 } ?: 0
                    OverlayDropdownPreference(
                        title = stringResource(R.string.susfs_uname_stage),
                        items = unameStageOptions.map { it.second },
                        selectedIndex = unameStageIndex,
                        renderInRootScaffold = true,
                        onSelectedIndexChange = { index ->
                            spoofUnameStage = unameStageOptions[index].first
                        }
                    )
                    MiuixTextAreaInput(
                        label = stringResource(R.string.susfs_field_uname_value),
                        value = unameValue,
                        onValueChange = { unameValue = it },
                        singleLine = true
                    )
                    MiuixTextAreaInput(
                        label = stringResource(R.string.susfs_field_build_time_value),
                        value = buildTimeValue,
                        onValueChange = { buildTimeValue = it },
                        singleLine = true
                    )
                }
                if (support?.sdcardRootPath == true) {
                    MiuixTextAreaInput(
                        label = stringResource(R.string.susfs_field_sdcard_root_path),
                        value = sdcardRootPath,
                        onValueChange = { sdcardRootPath = it },
                        singleLine = true
                    )
                    MiuixTextAreaInput(
                        label = stringResource(R.string.susfs_field_android_data_root_path),
                        value = androidDataRootPath,
                        onValueChange = { androidDataRootPath = it },
                        singleLine = true
                    )
                }
            }

            // Presets section
            MiuixSectionCard(
                title = stringResource(R.string.susfs_section_presets),
                subtitle = stringResource(R.string.susfs_section_presets_desc),
                icon = Icons.Default.Route
            ) {
                val hideCustomRomOptions = (0..5).map { level ->
                    level to level.toString()
                }
                val hideCustomRomIndex = hideCustomRomOptions.indexOfFirst { it.first == hideCustomRomLevel }
                    .takeIf { it >= 0 } ?: 0
                OverlayDropdownPreference(
                    title = stringResource(R.string.susfs_preset_hide_custom_rom_level),
                    items = hideCustomRomOptions.map { it.second },
                    selectedIndex = hideCustomRomIndex,
                    renderInRootScaffold = true,
                    onSelectedIndexChange = { index ->
                        hideCustomRomLevel = hideCustomRomOptions[index].first
                    }
                )
                val emulateVoldOptions = listOf(
                    0 to stringResource(R.string.susfs_option_off),
                    1 to "sus_path",
                    2 to "sus_path_loop",
                )
                val emulateVoldIndex = emulateVoldOptions.indexOfFirst { it.first == emulateVoldAppDataMode }
                    .takeIf { it >= 0 } ?: 0
                OverlayDropdownPreference(
                    title = stringResource(R.string.susfs_preset_emulate_vold_app_data),
                    items = emulateVoldOptions.map { it.second },
                    selectedIndex = emulateVoldIndex,
                    renderInRootScaffold = true,
                    onSelectedIndexChange = { index ->
                        emulateVoldAppDataMode = emulateVoldOptions[index].first
                    }
                )
                SwitchPreference(
                    title = stringResource(R.string.susfs_toggle_hide_vendor_sepolicy),
                    summary = "",
                    checked = hideVendorSepolicy,
                    onCheckedChange = { hideVendorSepolicy = it }
                )
                SwitchPreference(
                    title = stringResource(R.string.susfs_toggle_hide_compat_matrix),
                    summary = "",
                    checked = hideCompatMatrix,
                    onCheckedChange = { hideCompatMatrix = it }
                )
                SwitchPreference(
                    title = stringResource(R.string.susfs_toggle_hide_gapps),
                    summary = "",
                    checked = hideGapps,
                    onCheckedChange = { hideGapps = it }
                )
                SwitchPreference(
                    title = stringResource(R.string.susfs_toggle_hide_revanced),
                    summary = "",
                    checked = hideRevanced,
                    onCheckedChange = { hideRevanced = it }
                )
                SwitchPreference(
                    title = stringResource(R.string.susfs_toggle_spoof_cmdline),
                    summary = "",
                    checked = spoofCmdline,
                    onCheckedChange = { spoofCmdline = it }
                )
                SwitchPreference(
                    title = stringResource(R.string.susfs_toggle_hide_loops),
                    summary = "",
                    checked = hideLoops,
                    onCheckedChange = { hideLoops = it }
                )
                SwitchPreference(
                    title = stringResource(R.string.susfs_toggle_force_hide_lsposed),
                    summary = "",
                    checked = forceHideLsposed,
                    onCheckedChange = { forceHideLsposed = it }
                )
                if (support?.autoTryUmountPreset == true || support?.ksudKernelUmountFallback == true) {
                    SwitchPreference(
                        title = stringResource(R.string.susfs_toggle_auto_try_umount),
                        summary = "",
                        checked = autoTryUmount,
                        onCheckedChange = { autoTryUmount = it }
                    )
                    SwitchPreference(
                        title = stringResource(R.string.susfs_toggle_skip_legit_mounts),
                        summary = "",
                        checked = skipLegitMounts,
                        onCheckedChange = { skipLegitMounts = it }
                    )
                }
                if (support?.umountForZygoteIsoService == true) {
                    SwitchPreference(
                        title = stringResource(R.string.susfs_toggle_umount_for_zygote_iso_service),
                        summary = "",
                        checked = umountForZygoteIsoService,
                        onCheckedChange = { umountForZygoteIsoService = it }
                    )
                }
            }

            // Rules section
            MiuixSectionCard(
                title = stringResource(R.string.susfs_section_rules),
                subtitle = stringResource(R.string.susfs_section_rules_desc),
                icon = Icons.Default.Storage
            ) {
                if (support?.susPath == true) {
                    MiuixTextAreaInput(
                        label = stringResource(R.string.susfs_field_sus_path_rules),
                        value = pathRulesText,
                        onValueChange = { pathRulesText = it },
                        hint = stringResource(R.string.susfs_hint_path_rules)
                    )
                }
                if (support?.susPathLoop == true) {
                    MiuixTextAreaInput(
                        label = stringResource(R.string.susfs_field_sus_path_loop_rules),
                        value = loopPathRulesText,
                        onValueChange = { loopPathRulesText = it },
                        hint = stringResource(R.string.susfs_hint_path_rules)
                    )
                }
                if (support?.susMap == true) {
                    MiuixTextAreaInput(
                        label = stringResource(R.string.susfs_field_sus_maps),
                        value = mapsText,
                        onValueChange = { mapsText = it }
                    )
                }
                if (support?.susMount == true) {
                    MiuixTextAreaInput(
                        label = stringResource(R.string.susfs_field_sus_mounts),
                        value = mountsText,
                        onValueChange = { mountsText = it }
                    )
                }
                if (support?.tryUmount == true || support?.ksudKernelUmountFallback == true) {
                    MiuixTextAreaInput(
                        label = stringResource(R.string.susfs_field_try_umount),
                        value = tryUmountText,
                        onValueChange = { tryUmountText = it }
                    )
                }
                MiuixTextAreaInput(
                    label = stringResource(R.string.susfs_field_legit_mounts),
                    value = legitMountsText,
                    onValueChange = { legitMountsText = it }
                )
            }

            // Advanced section
            if (support?.openRedirect == true || support?.staticKstat == true) {
                MiuixSectionCard(
                    title = stringResource(R.string.susfs_section_advanced),
                    subtitle = stringResource(R.string.susfs_section_advanced_desc),
                    icon = Icons.Default.DataObject
                ) {
                    if (support.openRedirect == true) {
                        MiuixTextAreaInput(
                            label = stringResource(R.string.susfs_field_open_redirect),
                            value = openRedirectText,
                            onValueChange = { openRedirectText = it },
                            hint = stringResource(R.string.susfs_hint_open_redirect)
                        )
                    }
                    if (support.staticKstat == true) {
                        MiuixTextAreaInput(
                            label = stringResource(R.string.susfs_field_kstat_json),
                            value = kstatJsonText,
                            onValueChange = { kstatJsonText = it },
                            minLines = 8
                        )
                    }
                }
            }

            // Output section
            MiuixSectionCard(
                title = stringResource(R.string.susfs_section_output),
                subtitle = stringResource(R.string.susfs_section_output_desc),
                icon = Icons.Default.Description
            ) {
                Text(
                    text = state.susfsLastApplyOutput.joinToString("\n").ifBlank { noOutputText },
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary
                )
            }

            Spacer(Modifier.height(80.dp))
                }
            }
        }
    }
}

// MIUIX-themed section card: Card + icon + title + subtitle header
@Composable
private fun MiuixSectionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconColor: Color = MiuixTheme.colorScheme.primary,
    content: @Composable () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor
                )
                Column {
                    Text(
                        text = title,
                        style = MiuixTheme.textStyles.title4,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = subtitle,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary
                    )
                }
            }
            content()
        }
    }
}

// MIUIX-themed status chip
@Composable
private fun MiuixStatusChip(
    label: String,
    maxWidth: Dp = 160.dp
) {
    Box(
        modifier = Modifier
            .widthIn(max = maxWidth)
            .background(
                color = MiuixTheme.colorScheme.primary.copy(alpha = 0.12f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.primary,
            maxLines = 1
        )
    }
}

// MIUIX-themed multi-line text input
@Composable
private fun MiuixTextAreaInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    hint: String? = null,
    singleLine: Boolean = false,
    minLines: Int = 4,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
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
                .padding(horizontal = 16.dp, vertical = if (singleLine) 14.dp else 12.dp)
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = singleLine,
                minLines = if (singleLine) 1 else minLines,
                textStyle = MiuixTheme.textStyles.body1.copy(
                    color = MiuixTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MiuixTheme.colorScheme.primary)
            )
        }
        if (hint != null) {
            Text(
                text = hint,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary
            )
        }
    }
}
