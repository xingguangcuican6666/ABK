@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.abk.kernel.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.abk.kernel.R
import com.abk.kernel.data.model.SusfsConfig
import com.abk.kernel.data.model.SusfsPresetOptions
import com.abk.kernel.ui.components.AbkInlineLoadingPill
import com.abk.kernel.ui.components.AbkSegmentedButtonOption
import com.abk.kernel.ui.components.AbkSingleChoiceSegmentedButtonRow
import com.abk.kernel.ui.components.ExpressiveSectionCard
import com.abk.kernel.ui.components.ExpressiveStatusChip
import com.abk.kernel.ui.components.ExpressiveSwitchItem
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

@Composable
internal fun SusfsControlScreen(
    padding: PaddingValues,
    state: MainUiState,
    showRefreshLoading: Boolean,
    onApply: (SusfsConfig) -> Unit,
    onReset: () -> Unit,
    onRefresh: () -> Unit,
) {
    val runtime = state.susfsRuntimeStatus
    val support = runtime?.support
    val config = normalizeSusfsConfig(state.susfsConfig)
    val applyFailedText = stringResource(R.string.susfs_apply_failed)
    val unavailableText = stringResource(R.string.susfs_value_unavailable)
    val noOutputText = stringResource(R.string.susfs_no_output)

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

    Column(
        modifier = Modifier
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (showRefreshLoading) {
            AbkInlineLoadingPill(
                text = stringResource(R.string.settings_manager_loading_title),
                modifier = Modifier.fillMaxWidth(),
                compact = false
            )
        }
        state.susfsError?.takeIf { it.isNotBlank() }?.let { error ->
            ExpressiveSectionCard(
                title = "状态",
                subtitle = "SUSFS 探测或应用过程返回了错误",
                icon = Icons.Default.Info
            ) {
                Text(error, style = MaterialTheme.typography.bodyMedium)
            }
        }
        formError?.takeIf { it.isNotBlank() }?.let { error ->
            ExpressiveSectionCard(
                title = "表单错误",
                subtitle = "请先修正配置格式，再重新应用",
                icon = Icons.Default.Info
            ) {
                Text(error, style = MaterialTheme.typography.bodyMedium)
            }
        }
        runtime?.let {
            ExpressiveSectionCard(
                title = stringResource(R.string.susfs_section_overview),
                subtitle = stringResource(R.string.susfs_section_overview_desc),
                icon = Icons.Default.Extension
            ) {
                val kernelVersionText = it.kernelVersion.ifBlank { unavailableText }
                val installedBinaryText = it.installedBinaryPath.ifBlank { unavailableText }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExpressiveStatusChip(label = kernelVersionText)
                    ExpressiveStatusChip(label = it.bundledBinaryVersion)
                    ExpressiveStatusChip(label = stringResource(R.string.susfs_feature_flags_count, it.featureFlags.size))
                }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.susfs_label_kernel_version, kernelVersionText))
                Text(stringResource(R.string.susfs_label_bundled_binary, "${it.bundledBinaryVersion} (${it.bundledBinaryRef})"))
                Text(stringResource(R.string.susfs_label_installed_binary, installedBinaryText))
                Text(stringResource(R.string.susfs_label_config_path, it.configPath))
                if (it.rawFeatureText.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        it.rawFeatureText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        ExpressiveSectionCard(
            title = stringResource(R.string.susfs_section_actions),
            subtitle = stringResource(R.string.susfs_section_actions_desc),
            icon = Icons.Default.Settings
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = ::submit,
                    enabled = !state.susfsSaving,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (state.susfsSaving) stringResource(R.string.susfs_action_applying) else stringResource(R.string.susfs_action_apply))
                }
                TextButton(
                    onClick = onReset,
                    enabled = !state.susfsSaving,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.susfs_action_reset))
                }
            }
            TextButton(onClick = onRefresh, enabled = !state.susfsSaving) {
                Text(stringResource(R.string.susfs_action_refresh))
            }
        }

        ExpressiveSectionCard(
            title = stringResource(R.string.susfs_section_basic),
            subtitle = stringResource(R.string.susfs_section_basic_desc),
            icon = Icons.Default.Settings
        ) {
            ExpressiveSwitchItem(
                title = stringResource(R.string.susfs_toggle_auto_replay),
                subtitle = stringResource(R.string.susfs_toggle_auto_replay_desc),
                checked = autoReplayEnabled,
                onCheckedChange = { autoReplayEnabled = it }
            )
            ExpressiveSwitchItem(
                title = stringResource(R.string.susfs_toggle_log),
                subtitle = stringResource(R.string.susfs_toggle_log_desc),
                checked = logEnabled,
                onCheckedChange = { logEnabled = it }
            )
            if (support?.avcLogSpoofing == true) {
                ExpressiveSwitchItem(
                    title = stringResource(R.string.susfs_toggle_avc_log_spoofing),
                    subtitle = stringResource(R.string.susfs_toggle_avc_log_spoofing_desc),
                    checked = avcLogSpoofing,
                    onCheckedChange = { avcLogSpoofing = it }
                )
            }
            if (support?.hideSusMountsForAll == true || support?.hideSusMountsForNonSu == true) {
                SegmentedSetting(
                    title = stringResource(R.string.susfs_hide_mount_mode),
                    options = listOf(
                        AbkSegmentedButtonOption(SUSFS_HIDE_MOUNTS_OFF, stringResource(R.string.susfs_option_off)),
                        AbkSegmentedButtonOption(SUSFS_HIDE_MOUNTS_ALL, stringResource(R.string.susfs_option_all_processes)),
                        AbkSegmentedButtonOption(SUSFS_HIDE_MOUNTS_NON_SU, stringResource(R.string.susfs_option_non_su_processes)),
                    ),
                    selected = hideSusMountsMode,
                    onSelect = { hideSusMountsMode = it }
                )
            }
            if (support?.setUname == true) {
                SegmentedSetting(
                    title = stringResource(R.string.susfs_uname_stage),
                    options = listOf(
                        AbkSegmentedButtonOption(SUSFS_SPOOF_UNAME_OFF, stringResource(R.string.susfs_option_off)),
                        AbkSegmentedButtonOption(SUSFS_SPOOF_UNAME_POST_FS_DATA, stringResource(R.string.susfs_option_post_fs_data)),
                        AbkSegmentedButtonOption(SUSFS_SPOOF_UNAME_BOOT_COMPLETED, stringResource(R.string.susfs_option_boot_completed)),
                    ),
                    selected = spoofUnameStage,
                    onSelect = { spoofUnameStage = it }
                )
                TextAreaSetting(stringResource(R.string.susfs_field_uname_value), unameValue, { unameValue = it }, minLines = 1)
                TextAreaSetting(stringResource(R.string.susfs_field_build_time_value), buildTimeValue, { buildTimeValue = it }, minLines = 1)
            }
            if (support?.sdcardRootPath == true) {
                TextAreaSetting(stringResource(R.string.susfs_field_sdcard_root_path), sdcardRootPath, { sdcardRootPath = it }, minLines = 1)
                TextAreaSetting(stringResource(R.string.susfs_field_android_data_root_path), androidDataRootPath, { androidDataRootPath = it }, minLines = 1)
            }
        }

        ExpressiveSectionCard(
            title = stringResource(R.string.susfs_section_presets),
            subtitle = stringResource(R.string.susfs_section_presets_desc),
            icon = Icons.Default.Route
        ) {
            SegmentedSetting(
                title = stringResource(R.string.susfs_preset_hide_custom_rom_level),
                options = (0..5).map { level ->
                    AbkSegmentedButtonOption(level, level.toString())
                },
                selected = hideCustomRomLevel,
                onSelect = { hideCustomRomLevel = it }
            )
            SegmentedSetting(
                title = stringResource(R.string.susfs_preset_emulate_vold_app_data),
                options = listOf(
                    AbkSegmentedButtonOption(0, stringResource(R.string.susfs_option_off)),
                    AbkSegmentedButtonOption(1, "sus_path"),
                    AbkSegmentedButtonOption(2, "sus_path_loop"),
                ),
                selected = emulateVoldAppDataMode,
                onSelect = { emulateVoldAppDataMode = it }
            )
            ExpressiveSwitchItem(stringResource(R.string.susfs_toggle_hide_vendor_sepolicy), hideVendorSepolicy, { hideVendorSepolicy = it })
            ExpressiveSwitchItem(stringResource(R.string.susfs_toggle_hide_compat_matrix), hideCompatMatrix, { hideCompatMatrix = it })
            ExpressiveSwitchItem(stringResource(R.string.susfs_toggle_hide_gapps), hideGapps, { hideGapps = it })
            ExpressiveSwitchItem(stringResource(R.string.susfs_toggle_hide_revanced), hideRevanced, { hideRevanced = it })
            ExpressiveSwitchItem(stringResource(R.string.susfs_toggle_spoof_cmdline), spoofCmdline, { spoofCmdline = it })
            ExpressiveSwitchItem(stringResource(R.string.susfs_toggle_hide_loops), hideLoops, { hideLoops = it })
            ExpressiveSwitchItem(stringResource(R.string.susfs_toggle_force_hide_lsposed), forceHideLsposed, { forceHideLsposed = it })
            if (support?.autoTryUmountPreset == true || support?.ksudKernelUmountFallback == true) {
                ExpressiveSwitchItem(stringResource(R.string.susfs_toggle_auto_try_umount), autoTryUmount, { autoTryUmount = it })
                ExpressiveSwitchItem(stringResource(R.string.susfs_toggle_skip_legit_mounts), skipLegitMounts, { skipLegitMounts = it })
            }
            if (support?.umountForZygoteIsoService == true) {
                ExpressiveSwitchItem(stringResource(R.string.susfs_toggle_umount_for_zygote_iso_service), umountForZygoteIsoService, { umountForZygoteIsoService = it })
            }
        }

        ExpressiveSectionCard(
            title = stringResource(R.string.susfs_section_rules),
            subtitle = stringResource(R.string.susfs_section_rules_desc),
            icon = Icons.Default.Storage
        ) {
            if (support?.susPath == true) {
                TextAreaSetting(stringResource(R.string.susfs_field_sus_path_rules), pathRulesText, { pathRulesText = it }, hint = stringResource(R.string.susfs_hint_path_rules))
            }
            if (support?.susPathLoop == true) {
                TextAreaSetting(stringResource(R.string.susfs_field_sus_path_loop_rules), loopPathRulesText, { loopPathRulesText = it }, hint = stringResource(R.string.susfs_hint_path_rules))
            }
            if (support?.susMap == true) {
                TextAreaSetting(stringResource(R.string.susfs_field_sus_maps), mapsText, { mapsText = it })
            }
            if (support?.susMount == true) {
                TextAreaSetting(stringResource(R.string.susfs_field_sus_mounts), mountsText, { mountsText = it })
            }
            if (support?.tryUmount == true || support?.ksudKernelUmountFallback == true) {
                TextAreaSetting(stringResource(R.string.susfs_field_try_umount), tryUmountText, { tryUmountText = it })
            }
            TextAreaSetting(stringResource(R.string.susfs_field_legit_mounts), legitMountsText, { legitMountsText = it })
        }

        if (support?.openRedirect == true || support?.staticKstat == true) {
            ExpressiveSectionCard(
                title = stringResource(R.string.susfs_section_advanced),
                subtitle = stringResource(R.string.susfs_section_advanced_desc),
                icon = Icons.Default.DataObject
            ) {
                if (support?.openRedirect == true) {
                    TextAreaSetting(
                        stringResource(R.string.susfs_field_open_redirect),
                        openRedirectText,
                        { openRedirectText = it },
                        hint = stringResource(R.string.susfs_hint_open_redirect)
                    )
                }
                if (support?.staticKstat == true) {
                    TextAreaSetting(stringResource(R.string.susfs_field_kstat_json), kstatJsonText, { kstatJsonText = it }, minLines = 8)
                }
            }
        }

        ExpressiveSectionCard(
            title = stringResource(R.string.susfs_section_output),
            subtitle = stringResource(R.string.susfs_section_output_desc),
            icon = Icons.Default.Description
        ) {
            Text(
                text = state.susfsLastApplyOutput.joinToString("\n").ifBlank { noOutputText },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun <T> SegmentedSetting(
    title: String,
    options: List<AbkSegmentedButtonOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    equalWidth: Boolean = true
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        AbkSingleChoiceSegmentedButtonRow(
            options = options,
            selectedValue = selected,
            onSelect = onSelect,
            equalWidth = equalWidth,
            showSelectionIcon = false,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun TextAreaSetting(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    hint: String? = null,
    minLines: Int = 4
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            minLines = minLines,
            supportingText = hint?.let { { Text(it) } }
        )
    }
}
