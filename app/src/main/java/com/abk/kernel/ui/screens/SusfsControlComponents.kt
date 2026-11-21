package com.abk.kernel.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.abk.kernel.R
import com.abk.kernel.data.model.SusfsConfig
import com.abk.kernel.data.model.SusfsPresetOptions
import com.abk.kernel.data.model.SusfsRuntimeStatus
import com.abk.kernel.ui.blur.blurredCardBackground
import com.abk.kernel.ui.blur.blurredCardSurfaceColor
import com.abk.kernel.ui.components.AbkScreenHorizontalPadding
import com.abk.kernel.ui.screens.flash.FlashAction
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
import io.github.xingguangcuican6666.abk.ui.components.AbkBadge
import io.github.xingguangcuican6666.abk.ui.components.AbkButtonStyle
import io.github.xingguangcuican6666.abk.ui.components.AbkCard
import io.github.xingguangcuican6666.abk.ui.components.AbkHeading
import io.github.xingguangcuican6666.abk.ui.components.AbkOption
import io.github.xingguangcuican6666.abk.ui.components.AbkRadioGroup
import io.github.xingguangcuican6666.abk.ui.components.AbkSettingSwitch
import io.github.xingguangcuican6666.abk.ui.components.AbkSpinner
import io.github.xingguangcuican6666.abk.ui.components.AbkTextField
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcon
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcons
import io.github.xingguangcuican6666.abk.ui.theme.AbkShapes
import io.github.xingguangcuican6666.abk.ui.theme.AbkTheme

@Composable
internal fun SusfsControlContent(
    topBarHeight: Dp,
    runtime: SusfsRuntimeStatus?,
    savedConfig: SusfsConfig,
    saving: Boolean,
    error: String?,
    lastApplyOutput: List<String>,
    showRefreshLoading: Boolean,
    onApply: (SusfsConfig) -> Unit,
    onReset: () -> Unit,
    onRefresh: () -> Unit,
) {
    val support = runtime?.support
    val config = normalizeSusfsConfig(savedConfig)
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
    var hideCustomRomLevel by rememberSaveable {
        mutableIntStateOf(config.presets.hideCustomRomLevel)
    }
    var emulateVoldAppDataMode by rememberSaveable {
        mutableIntStateOf(config.presets.emulateVoldAppDataMode)
    }
    var hideVendorSepolicy by rememberSaveable { mutableStateOf(config.presets.hideVendorSepolicy) }
    var hideCompatMatrix by rememberSaveable { mutableStateOf(config.presets.hideCompatMatrix) }
    var hideGapps by rememberSaveable { mutableStateOf(config.presets.hideGapps) }
    var hideRevanced by rememberSaveable { mutableStateOf(config.presets.hideRevanced) }
    var spoofCmdline by rememberSaveable { mutableStateOf(config.presets.spoofCmdline) }
    var hideLoops by rememberSaveable { mutableStateOf(config.presets.hideLoops) }
    var forceHideLsposed by rememberSaveable { mutableStateOf(config.presets.forceHideLsposed) }
    var autoTryUmount by rememberSaveable { mutableStateOf(config.presets.autoTryUmount) }
    var skipLegitMounts by rememberSaveable { mutableStateOf(config.presets.skipLegitMounts) }
    var umountForZygoteIsoService by rememberSaveable {
        mutableStateOf(config.presets.umountForZygoteIsoService)
    }
    var pathRulesText by rememberSaveable { mutableStateOf(renderSusfsPathRules(config.pathRules)) }
    var loopPathRulesText by rememberSaveable {
        mutableStateOf(renderSusfsPathRules(config.loopPathRules))
    }
    var mapsText by rememberSaveable { mutableStateOf(renderSusfsStringList(config.maps)) }
    var mountsText by rememberSaveable { mutableStateOf(renderSusfsStringList(config.mounts)) }
    var tryUmountText by rememberSaveable {
        mutableStateOf(renderSusfsStringList(config.tryUmounts))
    }
    var legitMountsText by rememberSaveable {
        mutableStateOf(renderSusfsStringList(config.legitMounts))
    }
    var openRedirectText by rememberSaveable {
        mutableStateOf(renderSusfsOpenRedirects(config.openRedirects))
    }
    var kstatJsonText by rememberSaveable {
        mutableStateOf(renderSusfsKstatJson(config.kstatEntries))
    }
    var formError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(savedConfig) {
        val synced = normalizeSusfsConfig(savedConfig)
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
                        presets =
                            SusfsPresetOptions(
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
            }
            .onSuccess(onApply)
            .onFailure { formError = it.message ?: applyFailedText }
    }

    Column(
        modifier =
            Modifier.verticalScroll(rememberScrollState())
                .padding(horizontal = AbkScreenHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(topBarHeight + 16.dp))
        if (showRefreshLoading) {
            val label = stringResource(R.string.settings_manager_loading_title)
            Row(
                modifier =
                    Modifier.clearAndSetSemantics {
                        contentDescription = label
                        progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
                    },
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AbkSpinner(label = label)
                Text(label, style = MaterialTheme.typography.bodyMedium)
            }
        }
        error
            ?.takeIf { it.isNotBlank() }
            ?.let { message ->
                SusfsErrorCard(
                    stringResource(R.string.susfs_status_error_title),
                    stringResource(R.string.susfs_status_error_desc),
                    message,
                )
            }
        formError
            ?.takeIf { it.isNotBlank() }
            ?.let { message ->
                SusfsErrorCard(
                    stringResource(R.string.susfs_form_error_title),
                    stringResource(R.string.susfs_form_error_desc),
                    message,
                )
            }
        runtime?.let {
            SusfsSection(
                stringResource(R.string.susfs_section_overview),
                stringResource(R.string.susfs_section_overview_desc),
                AbkIcons.Modules,
            ) {
                val kernelVersionText = it.kernelVersion.ifBlank { unavailableText }
                val installedBinaryText = it.installedBinaryPath.ifBlank { unavailableText }
                AbkBadge(
                    stringResource(R.string.susfs_feature_flags_count, it.featureFlags.size),
                    dot = false,
                )
                SusfsTechnicalText(
                    stringResource(R.string.susfs_label_kernel_version, kernelVersionText)
                )
                SusfsTechnicalText(
                    stringResource(
                        R.string.susfs_label_bundled_binary,
                        "${it.bundledBinaryVersion} (${it.bundledBinaryRef})",
                    )
                )
                SusfsTechnicalText(
                    stringResource(R.string.susfs_label_installed_binary, installedBinaryText)
                )
                SusfsTechnicalText(stringResource(R.string.susfs_label_config_path, it.configPath))
                if (it.rawFeatureText.isNotBlank()) SusfsTechnicalText(it.rawFeatureText)
            }
        }
        SusfsSection(
            stringResource(R.string.susfs_section_actions),
            stringResource(R.string.susfs_section_actions_desc),
            AbkIcons.Controls,
        ) {
            FlashAction(
                stringResource(
                    if (saving) R.string.susfs_action_applying else R.string.susfs_action_apply
                ),
                AbkIcons.Check,
                ::submit,
                modifier = Modifier.fillMaxWidth(),
                style = AbkButtonStyle.Primary,
                enabled = !saving,
                loading = saving,
            )
            FlashAction(
                stringResource(R.string.susfs_action_reset),
                AbkIcons.Settings,
                onReset,
                modifier = Modifier.fillMaxWidth(),
                enabled = !saving,
            )
            FlashAction(
                stringResource(R.string.susfs_action_refresh),
                AbkIcons.Refresh,
                onRefresh,
                modifier = Modifier.fillMaxWidth(),
                style = AbkButtonStyle.Ghost,
                enabled = !saving,
            )
        }
        SusfsSection(
            stringResource(R.string.susfs_section_basic),
            stringResource(R.string.susfs_section_basic_desc),
            AbkIcons.Settings,
        ) {
            SusfsSwitch(
                stringResource(R.string.susfs_toggle_auto_replay),
                autoReplayEnabled,
                { autoReplayEnabled = it },
                description = stringResource(R.string.susfs_toggle_auto_replay_desc),
                enabled = !saving,
            )
            SusfsSwitch(
                stringResource(R.string.susfs_toggle_log),
                logEnabled,
                { logEnabled = it },
                description = stringResource(R.string.susfs_toggle_log_desc),
                enabled = !saving,
            )
            if (support?.avcLogSpoofing == true) {
                SusfsSwitch(
                    stringResource(R.string.susfs_toggle_avc_log_spoofing),
                    avcLogSpoofing,
                    { avcLogSpoofing = it },
                    description = stringResource(R.string.susfs_toggle_avc_log_spoofing_desc),
                    enabled = !saving,
                )
            }
            if (support?.hideSusMountsForAll == true || support?.hideSusMountsForNonSu == true) {
                SusfsChoiceSetting(
                    stringResource(R.string.susfs_hide_mount_mode),
                    listOf(
                        SUSFS_HIDE_MOUNTS_OFF to stringResource(R.string.susfs_option_off),
                        SUSFS_HIDE_MOUNTS_ALL to
                            stringResource(R.string.susfs_option_all_processes),
                        SUSFS_HIDE_MOUNTS_NON_SU to
                            stringResource(R.string.susfs_option_non_su_processes),
                    ),
                    hideSusMountsMode,
                    { hideSusMountsMode = it },
                    enabled = !saving,
                )
            }
            if (support?.setUname == true) {
                SusfsChoiceSetting(
                    stringResource(R.string.susfs_uname_stage),
                    listOf(
                        SUSFS_SPOOF_UNAME_OFF to stringResource(R.string.susfs_option_off),
                        SUSFS_SPOOF_UNAME_POST_FS_DATA to
                            stringResource(R.string.susfs_option_post_fs_data),
                        SUSFS_SPOOF_UNAME_BOOT_COMPLETED to
                            stringResource(R.string.susfs_option_boot_completed),
                    ),
                    spoofUnameStage,
                    { spoofUnameStage = it },
                    enabled = !saving,
                )
                SusfsTextArea(
                    stringResource(R.string.susfs_field_uname_value),
                    unameValue,
                    { unameValue = it },
                    minLines = 1,
                    enabled = !saving,
                )
                SusfsTextArea(
                    stringResource(R.string.susfs_field_build_time_value),
                    buildTimeValue,
                    { buildTimeValue = it },
                    minLines = 1,
                    enabled = !saving,
                )
            }
            if (support?.sdcardRootPath == true) {
                SusfsTextArea(
                    stringResource(R.string.susfs_field_sdcard_root_path),
                    sdcardRootPath,
                    { sdcardRootPath = it },
                    minLines = 1,
                    enabled = !saving,
                )
                SusfsTextArea(
                    stringResource(R.string.susfs_field_android_data_root_path),
                    androidDataRootPath,
                    { androidDataRootPath = it },
                    minLines = 1,
                    enabled = !saving,
                )
            }
        }
        SusfsSection(
            stringResource(R.string.susfs_section_presets),
            stringResource(R.string.susfs_section_presets_desc),
            AbkIcons.Controls,
        ) {
            SusfsChoiceSetting(
                stringResource(R.string.susfs_preset_hide_custom_rom_level),
                (0..5).map { level -> level to level.toString() },
                hideCustomRomLevel,
                { hideCustomRomLevel = it },
                enabled = !saving,
            )
            SusfsChoiceSetting(
                stringResource(R.string.susfs_preset_emulate_vold_app_data),
                listOf(
                    0 to stringResource(R.string.susfs_option_off),
                    1 to "sus_path",
                    2 to "sus_path_loop",
                ),
                emulateVoldAppDataMode,
                { emulateVoldAppDataMode = it },
                enabled = !saving,
            )
            SusfsSwitch(
                stringResource(R.string.susfs_toggle_hide_vendor_sepolicy),
                hideVendorSepolicy,
                { hideVendorSepolicy = it },
                enabled = !saving,
            )
            SusfsSwitch(
                stringResource(R.string.susfs_toggle_hide_compat_matrix),
                hideCompatMatrix,
                { hideCompatMatrix = it },
                enabled = !saving,
            )
            SusfsSwitch(
                stringResource(R.string.susfs_toggle_hide_gapps),
                hideGapps,
                { hideGapps = it },
                enabled = !saving,
            )
            SusfsSwitch(
                stringResource(R.string.susfs_toggle_hide_revanced),
                hideRevanced,
                { hideRevanced = it },
                enabled = !saving,
            )
            SusfsSwitch(
                stringResource(R.string.susfs_toggle_spoof_cmdline),
                spoofCmdline,
                { spoofCmdline = it },
                enabled = !saving,
            )
            SusfsSwitch(
                stringResource(R.string.susfs_toggle_hide_loops),
                hideLoops,
                { hideLoops = it },
                enabled = !saving,
            )
            SusfsSwitch(
                stringResource(R.string.susfs_toggle_force_hide_lsposed),
                forceHideLsposed,
                { forceHideLsposed = it },
                enabled = !saving,
            )
            if (support?.autoTryUmountPreset == true || support?.ksudKernelUmountFallback == true) {
                SusfsSwitch(
                    stringResource(R.string.susfs_toggle_auto_try_umount),
                    autoTryUmount,
                    { autoTryUmount = it },
                    enabled = !saving,
                )
                SusfsSwitch(
                    stringResource(R.string.susfs_toggle_skip_legit_mounts),
                    skipLegitMounts,
                    { skipLegitMounts = it },
                    enabled = !saving,
                )
            }
            if (support?.umountForZygoteIsoService == true) {
                SusfsSwitch(
                    stringResource(R.string.susfs_toggle_umount_for_zygote_iso_service),
                    umountForZygoteIsoService,
                    { umountForZygoteIsoService = it },
                    enabled = !saving,
                )
            }
        }
        SusfsSection(
            stringResource(R.string.susfs_section_rules),
            stringResource(R.string.susfs_section_rules_desc),
            AbkIcons.Modules,
        ) {
            if (support?.susPath == true) {
                SusfsTextArea(
                    stringResource(R.string.susfs_field_sus_path_rules),
                    pathRulesText,
                    { pathRulesText = it },
                    hint = stringResource(R.string.susfs_hint_path_rules),
                    enabled = !saving,
                )
            }
            if (support?.susPathLoop == true) {
                SusfsTextArea(
                    stringResource(R.string.susfs_field_sus_path_loop_rules),
                    loopPathRulesText,
                    { loopPathRulesText = it },
                    hint = stringResource(R.string.susfs_hint_path_rules),
                    enabled = !saving,
                )
            }
            if (support?.susMap == true) {
                SusfsTextArea(
                    stringResource(R.string.susfs_field_sus_maps),
                    mapsText,
                    { mapsText = it },
                    enabled = !saving,
                )
            }
            if (support?.susMount == true) {
                SusfsTextArea(
                    stringResource(R.string.susfs_field_sus_mounts),
                    mountsText,
                    { mountsText = it },
                    enabled = !saving,
                )
            }
            if (support?.tryUmount == true || support?.ksudKernelUmountFallback == true) {
                SusfsTextArea(
                    stringResource(R.string.susfs_field_try_umount),
                    tryUmountText,
                    { tryUmountText = it },
                    enabled = !saving,
                )
            }
            SusfsTextArea(
                stringResource(R.string.susfs_field_legit_mounts),
                legitMountsText,
                { legitMountsText = it },
                enabled = !saving,
            )
        }
        if (support?.openRedirect == true || support?.staticKstat == true) {
            SusfsSection(
                stringResource(R.string.susfs_section_advanced),
                stringResource(R.string.susfs_section_advanced_desc),
                AbkIcons.Settings,
            ) {
                if (support?.openRedirect == true) {
                    SusfsTextArea(
                        stringResource(R.string.susfs_field_open_redirect),
                        openRedirectText,
                        { openRedirectText = it },
                        hint = stringResource(R.string.susfs_hint_open_redirect),
                        enabled = !saving,
                    )
                }
                if (support?.staticKstat == true) {
                    SusfsTextArea(
                        stringResource(R.string.susfs_field_kstat_json),
                        kstatJsonText,
                        { kstatJsonText = it },
                        minLines = 8,
                        enabled = !saving,
                    )
                }
            }
        }
        SusfsSection(
            stringResource(R.string.susfs_section_output),
            stringResource(R.string.susfs_section_output_desc),
            AbkIcons.Terminal,
        ) {
            SusfsTechnicalText(lastApplyOutput.joinToString("\n").ifBlank { noOutputText })
        }
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun SusfsSection(
    title: String,
    description: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit,
) {
    AbkCard(
        modifier = Modifier.fillMaxWidth().blurredCardBackground(AbkShapes.Large),
        containerColor = blurredCardSurfaceColor(AbkTheme.colors.raised),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            AbkIcon(icon, null)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AbkHeading(title)
                Text(description, color = AbkTheme.colors.inkMuted)
            }
        }
        content()
    }
}

@Composable
private fun SusfsErrorCard(title: String, description: String, message: String) {
    SusfsSection(title, description, AbkIcons.Warning) {
        Text(message, Modifier.semantics { error(message) }, color = AbkTheme.colors.danger.color)
    }
}

@Composable
private fun SusfsTechnicalText(value: String) {
    SelectionContainer {
        Text(
            value,
            fontFamily = AbkTheme.monoFont,
            style = MaterialTheme.typography.bodySmall,
            color = AbkTheme.colors.inkMuted,
        )
    }
}

@Composable
private fun SusfsSwitch(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    description: String? = null,
    enabled: Boolean = true,
) {
    AbkSettingSwitch(title, description, checked, onCheckedChange, enabled = enabled)
}

@Composable
private fun <T> SusfsChoiceSetting(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    enabled: Boolean = true,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        AbkRadioGroup(
            options =
                options.map { (value, label) ->
                    AbkOption(value.toString(), label, enabled = enabled)
                },
            selectedId = selected.toString(),
            onSelect = { id -> onSelect(options.first { it.first.toString() == id }.first) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SusfsTextArea(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    hint: String? = null,
    minLines: Int = 4,
    enabled: Boolean = true,
) {
    AbkTextField(
        value,
        onValueChange,
        title,
        modifier = Modifier.fillMaxWidth(),
        hint = hint,
        singleLine = false,
        minLines = minLines,
        enabled = enabled,
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = AbkTheme.monoFont),
    )
}
