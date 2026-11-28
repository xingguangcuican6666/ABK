package com.abk.kernel.ui.build

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.abk.kernel.R
import com.abk.kernel.data.model.BUILD_TARGET_CUSTOM_SOURCE
import com.abk.kernel.data.model.BUILD_TARGET_GKI
import com.abk.kernel.data.model.BUILD_TARGET_ONEPLUS
import com.abk.kernel.data.model.BuildQueueItemStatus
import com.abk.kernel.data.model.BuildStatus
import com.abk.kernel.data.model.KSU_VARIANT_NONE
import com.abk.kernel.data.model.KSU_VARIANT_NYASU
import com.abk.kernel.data.model.KernelBuildConfig
import com.abk.kernel.data.model.KernelSupport
import com.abk.kernel.data.model.SOURCE_ACCESS_GITHUB_PRIVATE
import com.abk.kernel.data.model.SOURCE_ACCESS_PUBLIC
import com.abk.kernel.data.model.WorkflowRun
import com.abk.kernel.ui.common.AbkPage
import com.abk.kernel.ui.common.MutedText
import com.abk.kernel.ui.common.PageSection
import com.abk.kernel.ui.common.StatusBadge
import com.abk.kernel.ui.common.formatRunTimestamp
import com.abk.kernel.ui.common.statusLabel
import com.abk.kernel.ui.common.tone
import com.abk.kernel.viewmodel.MainUiState
import com.abk.kernel.viewmodel.MainViewModel
import io.github.xingguangcuican6666.abk.ui.components.AbkAlert
import io.github.xingguangcuican6666.abk.ui.components.AbkButton
import io.github.xingguangcuican6666.abk.ui.components.AbkButtonStyle
import io.github.xingguangcuican6666.abk.ui.components.AbkCard
import io.github.xingguangcuican6666.abk.ui.components.AbkDivider
import io.github.xingguangcuican6666.abk.ui.components.AbkHeading
import io.github.xingguangcuican6666.abk.ui.components.AbkList
import io.github.xingguangcuican6666.abk.ui.components.AbkListItem
import io.github.xingguangcuican6666.abk.ui.components.AbkOption
import io.github.xingguangcuican6666.abk.ui.components.AbkSegmented
import io.github.xingguangcuican6666.abk.ui.components.AbkSelect
import io.github.xingguangcuican6666.abk.ui.components.AbkSettingSwitch
import io.github.xingguangcuican6666.abk.ui.components.AbkSize
import io.github.xingguangcuican6666.abk.ui.components.AbkStep
import io.github.xingguangcuican6666.abk.ui.components.AbkSteps
import io.github.xingguangcuican6666.abk.ui.components.AbkText
import io.github.xingguangcuican6666.abk.ui.components.AbkTextField
import io.github.xingguangcuican6666.abk.ui.theme.AbkSpacing
import io.github.xingguangcuican6666.abk.ui.theme.AbkTone

/**
 * 构建：五步引导（平台 → 内核 → 功能 → Root → 确认）。
 *
 * Root 实现只剩 NyaSU 与 None 两条路径；选择「无 Root」时补丁模块输入会隐藏并清空，
 * 这与工作流侧 `patch_modules` 可选的语义一致（留空 = 只集成 NyaSU 驱动）。
 */
@Composable
fun BuildDestination(
    vm: MainViewModel,
    state: MainUiState,
    onChildPageVisibleChange: (Boolean) -> Unit,
) {
    val config = state.buildConfig
    var stepIndex by rememberSaveable { mutableIntStateOf(0) }
    var submitting by rememberSaveable { mutableStateOf(false) }
    // The dispatch has no completion callback: stop the spinner once a run starts or an error arrives.
    LaunchedEffect(state.kernelBuildStatus, state.managerBuildStatus, state.error) {
        if (submitting && (
                state.kernelBuildStatus == BuildStatus.IN_PROGRESS ||
                    state.managerBuildStatus == BuildStatus.IN_PROGRESS ||
                    state.error != null
                )
        ) {
            submitting = false
        }
    }
    val canBuild = state.isLoggedIn && state.forkRepo != null
    val step = BuildWizardSteps[stepIndex.coerceIn(0, BuildWizardSteps.lastIndex)]
    val lastStep = step == BuildWizardStep.Review

    // A system back press walks the wizard backwards; only the first step hands the
    // gesture back to the shell, which is when the bottom bar may reappear.
    BackHandler(enabled = stepIndex > 0) { stepIndex -= 1 }
    LaunchedEffect(stepIndex) { onChildPageVisibleChange(stepIndex > 0) }

    fun goTo(index: Int) {
        stepIndex = index.coerceIn(0, BuildWizardSteps.lastIndex)
    }

    AbkPage(
        title = stringResource(R.string.build_title),
        description = stringResource(
            if (config.buildTarget == BUILD_TARGET_ONEPLUS) R.string.build_oneplus_hero_desc
            else R.string.build_hero_desc
        ),
    ) {
        if (!state.isLoggedIn) {
            AbkAlert(
                title = stringResource(R.string.build_login_required_title),
                message = stringResource(R.string.build_login_required_desc),
                tone = AbkTone.Info,
                actions = {
                    AbkButton(
                        onClick = { vm.openLoginOobe() },
                        style = AbkButtonStyle.Primary,
                    ) { AbkText(stringResource(R.string.login_github)) }
                },
            )
        } else if (state.forkRepo == null) {
            AbkAlert(
                title = stringResource(R.string.build_fork_required_title),
                message = stringResource(R.string.build_fork_required_desc),
                tone = AbkTone.Warning,
                actions = {
                    AbkButton(
                        onClick = { vm.openBuildOobe() },
                        style = AbkButtonStyle.Primary,
                    ) { AbkText(stringResource(R.string.fork_action)) }
                },
            )
        }

        val states = stepStates(stepIndex)
        AbkSteps(
            steps = BuildWizardSteps.mapIndexed { index, wizardStep ->
                AbkStep(title = stringResource(wizardStep.titleRes()), state = states[index])
            },
            modifier = Modifier.fillMaxWidth(),
            completeDescription = stringResource(R.string.build_wizard_step_complete),
            currentDescription = stringResource(R.string.build_wizard_step_current),
            pendingDescription = stringResource(R.string.build_wizard_step_pending),
        )

        when (step) {
            BuildWizardStep.Target -> TargetStep(
                config = config,
                onChange = vm::updateBuildConfig,
            )
            BuildWizardStep.Kernel -> KernelStep(
                config = config,
                state = state,
                onChange = vm::updateBuildConfig,
            )
            BuildWizardStep.Features -> FeaturesStep(
                config = config,
                onChange = vm::updateBuildConfig,
            )
            BuildWizardStep.Root -> RootStep(
                config = config,
                onChange = vm::updateBuildConfig,
            )
            BuildWizardStep.Review -> ReviewStep(
                config = config,
                state = state,
            )
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AbkSpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (stepIndex > 0) {
                AbkButton(
                    onClick = { goTo(stepIndex - 1) },
                    modifier = Modifier.weight(1f),
                    style = AbkButtonStyle.Secondary,
                    size = AbkSize.Large,
                ) { AbkText(stringResource(R.string.build_wizard_back)) }
            }
            if (!lastStep) {
                AbkButton(
                    onClick = { goTo(stepIndex + 1) },
                    modifier = Modifier.weight(1f),
                    style = AbkButtonStyle.Primary,
                    size = AbkSize.Large,
                    enabled = canAdvanceFrom(config, step),
                ) { AbkText(stringResource(R.string.build_wizard_next)) }
            } else {
                AbkButton(
                    onClick = {
                        submitting = true
                        vm.dispatchBuild(config)
                    },
                    modifier = Modifier.weight(1f),
                    style = AbkButtonStyle.Primary,
                    size = AbkSize.Large,
                    enabled = canBuild && !submitting,
                    loading = submitting,
                    loadingDescription = stringResource(R.string.build_running),
                ) { AbkText(stringResource(R.string.build_submit)) }
            }
        }

        if (state.buildQueue.isNotEmpty()) {
            MutedText(
                stringResource(R.string.build_queue_summary, state.buildQueue.size, state.buildQueue.count { it.status == BuildQueueItemStatus.PENDING })
            )
        }

        PageSection(title = stringResource(R.string.status_workflows_title)) {
            val runs = state.recentRuns
            if (runs.isEmpty()) {
                MutedText(stringResource(R.string.status_workflow_empty))
            } else {
                AbkList {
                    runs.forEachIndexed { index, run ->
                        if (index > 0) AbkDivider()
                        WorkflowRow(run)
                    }
                }
            }
        }
    }
}

private fun BuildWizardStep.titleRes(): Int = when (this) {
    BuildWizardStep.Target -> R.string.build_wizard_step_target
    BuildWizardStep.Kernel -> R.string.build_wizard_step_kernel
    BuildWizardStep.Features -> R.string.build_wizard_step_features
    BuildWizardStep.Root -> R.string.build_wizard_step_root
    BuildWizardStep.Review -> R.string.build_wizard_step_review
}

@Composable
private fun TargetStep(config: KernelBuildConfig, onChange: (KernelBuildConfig) -> Unit) {
    AbkCard {
        AbkHeading(stringResource(R.string.build_target_title))
        MutedText(stringResource(R.string.build_target_desc))
        AbkSegmented(
            options = listOf(
                AbkOption(BUILD_TARGET_GKI, stringResource(R.string.build_target_gki)),
                AbkOption(BUILD_TARGET_ONEPLUS, stringResource(R.string.build_target_oneplus)),
                AbkOption(BUILD_TARGET_CUSTOM_SOURCE, stringResource(R.string.build_target_custom_source)),
            ),
            selectedId = config.buildTarget,
            onSelect = { onChange(config.copy(buildTarget = it)) },
        )
        MutedText(buildTargetSummary(config))
    }
}

@Composable
private fun KernelStep(config: KernelBuildConfig, state: MainUiState, onChange: (KernelBuildConfig) -> Unit) {
    val isOnePlus = config.buildTarget == BUILD_TARGET_ONEPLUS
    val isCustomSource = config.buildTarget == BUILD_TARGET_CUSTOM_SOURCE
    if (isCustomSource) {
        CustomSourceFields(config = config, secretConfigured = state.customSourceSecretConfigured, onChange = onChange)
    } else if (isOnePlus) {
        val profiles = KernelSupport.onePlusDeviceProfiles
        AbkCard {
            AbkHeading(stringResource(R.string.build_oneplus_device_manifest))
            MutedText(stringResource(R.string.build_oneplus_profile_desc))
            AbkSelect(
                options = profiles.map { AbkOption(it.manifest, "${it.displayName} · ${it.systemVersion}") },
                selectedId = config.onePlusDeviceManifest,
                onSelect = { onChange(config.copy(onePlusDeviceManifest = it)) },
                label = stringResource(R.string.build_oneplus_device_manifest),
            )
            MutedText(buildTargetSummary(config))
        }
    } else {
        AbkCard {
            AbkHeading(stringResource(R.string.build_kernel_version_config))
            val androidOptions = KernelSupport.androidVersions()
            AbkSelect(
                options = androidOptions.map { AbkOption(it, it) },
                selectedId = config.androidVersion,
                onSelect = {
                    val kernel = KernelSupport.kernelForAndroid(it)
                    onChange(config.copy(androidVersion = it, kernelVersion = kernel, subLevel = "", osPatchLevel = ""))
                },
                label = stringResource(R.string.build_android_version),
            )
            val kernelOptions = KernelSupport.kernelVersions()
            AbkSelect(
                options = kernelOptions.map { AbkOption(it, it) },
                selectedId = config.kernelVersion,
                onSelect = {
                    val android = KernelSupport.androidForKernel(it)
                    onChange(config.copy(androidVersion = android, kernelVersion = it, subLevel = "", osPatchLevel = ""))
                },
                label = stringResource(R.string.build_kernel_version),
            )
            val subOptions = KernelSupport.subLevelOptions(config.androidVersion, config.kernelVersion)
            AbkSelect(
                options = subOptions.map { AbkOption(it, it) },
                selectedId = config.subLevel,
                onSelect = { onChange(config.copy(subLevel = it, osPatchLevel = "")) },
                label = stringResource(R.string.build_sub_level),
            )
            val patchOptions = KernelSupport.patchLevelOptions(config.androidVersion, config.kernelVersion, config.subLevel)
            AbkSelect(
                options = patchOptions.map { AbkOption(it, it) },
                selectedId = config.osPatchLevel,
                onSelect = { onChange(config.copy(osPatchLevel = it)) },
                label = stringResource(R.string.build_security_patch_level),
            )
            MutedText(buildTargetSummary(config))
            if (state.recommendedBuildConfig != null && state.recommendedBuildConfig != config) {
                AbkButton(
                    onClick = { state.recommendedBuildConfig?.let(onChange) },
                    style = AbkButtonStyle.Soft,
                ) { AbkText(stringResource(R.string.build_device_recommended)) }
            }
        }
    }
}

@Composable
private fun CustomSourceFields(
    config: KernelBuildConfig,
    secretConfigured: Boolean,
    onChange: (KernelBuildConfig) -> Unit,
) {
    val isPrivate = config.sourceAccessMode == SOURCE_ACCESS_GITHUB_PRIVATE
    AbkCard {
        AbkHeading(stringResource(R.string.build_target_custom_source))
        AbkTextField(
            value = config.sourceUrl,
            onValueChange = { onChange(config.copy(sourceUrl = it)) },
            label = stringResource(R.string.build_source_repo_url),
            placeholder = "https://github.com/owner/repo",
        )
        AbkTextField(
            value = config.sourceRef,
            onValueChange = { onChange(config.copy(sourceRef = it)) },
            label = stringResource(R.string.build_source_ref),
            placeholder = "branch / tag / 40 位 SHA",
        )
        AbkSegmented(
            options = listOf(
                AbkOption(SOURCE_ACCESS_PUBLIC, stringResource(R.string.build_source_public)),
                AbkOption(SOURCE_ACCESS_GITHUB_PRIVATE, stringResource(R.string.build_source_private)),
            ),
            selectedId = config.sourceAccessMode,
            onSelect = { onChange(config.copy(sourceAccessMode = it)) },
        )
        if (isPrivate) {
            MutedText(
                stringResource(
                    if (secretConfigured) R.string.build_source_secret_exists
                    else R.string.build_source_secret_missing
                )
            )
        }
        AbkTextField(
            value = config.sourceDefconfigs.joinToString("\n"),
            onValueChange = { raw ->
                onChange(config.copy(sourceDefconfigs = raw.split('\n').map(String::trim).filter(String::isNotBlank)))
            },
            label = stringResource(R.string.build_source_defconfigs),
            hint = stringResource(R.string.build_source_defconfig_base_hint),
            singleLine = false,
            minLines = 2,
        )
        AbkTextField(
            value = config.osPatchLevel,
            onValueChange = { onChange(config.copy(osPatchLevel = it)) },
            label = stringResource(R.string.build_source_patch_month),
            placeholder = stringResource(R.string.build_source_patch_month_placeholder),
        )
        AbkTextField(
            value = config.sourceKernelVersionOverride,
            onValueChange = { onChange(config.copy(sourceKernelVersionOverride = it)) },
            label = stringResource(R.string.build_source_kernel_version),
            hint = stringResource(R.string.build_source_kernel_version_hint),
        )
        AbkTextField(
            value = config.sourceDeviceLabel,
            onValueChange = { onChange(config.copy(sourceDeviceLabel = it)) },
            label = stringResource(R.string.build_source_device_label),
        )
    }
}

@Composable
private fun FeaturesStep(config: KernelBuildConfig, onChange: (KernelBuildConfig) -> Unit) {
    val isOnePlus = config.buildTarget == BUILD_TARGET_ONEPLUS
    AbkCard {
        AbkHeading(stringResource(R.string.build_features))
        MutedText(stringResource(R.string.build_section_features_desc))
        if (isOnePlus) {
            AbkSettingSwitch(
                title = stringResource(R.string.build_oneplus_lz4kd),
                description = if (KernelSupport.onePlusLz4kdSupported(config.kernelVersion)) null
                else stringResource(R.string.build_oneplus_lz4kd_unsupported),
                checked = config.onePlusUseLz4kd,
                enabled = KernelSupport.onePlusLz4kdSupported(config.kernelVersion),
                onCheckedChange = { onChange(config.copy(onePlusUseLz4kd = it)) },
            )
            AbkDivider()
            AbkSettingSwitch(
                title = stringResource(R.string.build_enable_bbg),
                description = null,
                checked = config.useBbg,
                onCheckedChange = { onChange(config.copy(useBbg = it)) },
            )
            AbkDivider()
            AbkSettingSwitch(
                title = stringResource(R.string.build_oneplus_bbr),
                description = null,
                checked = config.onePlusUseBbr,
                onCheckedChange = { onChange(config.copy(onePlusUseBbr = it)) },
            )
            AbkDivider()
            AbkSettingSwitch(
                title = stringResource(R.string.build_oneplus_proxy_optimization),
                description = if (config.onePlusCpu.startsWith("mt")) stringResource(R.string.build_oneplus_proxy_mtk_disabled) else null,
                checked = config.onePlusUseProxyOptimization,
                enabled = !config.onePlusCpu.startsWith("mt"),
                onCheckedChange = { onChange(config.copy(onePlusUseProxyOptimization = it)) },
            )
            AbkDivider()
            AbkSettingSwitch(
                title = stringResource(R.string.build_oneplus_unicode_bypass),
                description = null,
                checked = config.onePlusUseUnicodeBypass,
                onCheckedChange = { onChange(config.copy(onePlusUseUnicodeBypass = it)) },
            )
        } else {
            AbkSettingSwitch(
                title = stringResource(R.string.build_enable_zram),
                description = null,
                checked = config.useZram,
                onCheckedChange = { onChange(config.copy(useZram = it)) },
            )
            if (config.useZram) {
                AbkDivider()
                AbkSettingSwitch(
                    title = stringResource(R.string.build_zram_full_algo),
                    description = null,
                    checked = config.zramFullAlgo,
                    onCheckedChange = { onChange(config.copy(zramFullAlgo = it)) },
                )
                AbkTextField(
                    value = config.zramExtraAlgos,
                    onValueChange = { onChange(config.copy(zramExtraAlgos = it)) },
                    label = stringResource(R.string.build_zram_custom_algo),
                    placeholder = stringResource(R.string.build_zram_algo_placeholder),
                )
            }
            AbkDivider()
            AbkSettingSwitch(
                title = stringResource(R.string.build_enable_bbg),
                description = if (KernelSupport.bbgSupported(config.androidVersion, config.kernelVersion)) null
                else stringResource(R.string.build_bbg_unsupported),
                checked = config.useBbg,
                enabled = KernelSupport.bbgSupported(config.androidVersion, config.kernelVersion),
                onCheckedChange = { onChange(config.copy(useBbg = it)) },
            )
            AbkDivider()
            AbkSettingSwitch(
                title = stringResource(R.string.build_enable_ddk),
                description = null,
                checked = config.useDdk,
                onCheckedChange = { onChange(config.copy(useDdk = it)) },
            )
            AbkDivider()
            AbkSettingSwitch(
                title = stringResource(R.string.build_enable_ntsync),
                description = null,
                checked = config.useNtsync,
                onCheckedChange = { onChange(config.copy(useNtsync = it)) },
            )
            AbkDivider()
            AbkSettingSwitch(
                title = stringResource(R.string.build_enable_networking),
                description = null,
                checked = config.useNetworking,
                onCheckedChange = { onChange(config.copy(useNetworking = it)) },
            )
            AbkDivider()
            AbkSettingSwitch(
                title = stringResource(R.string.build_enable_rekernel),
                description = null,
                checked = config.useRekernel,
                onCheckedChange = { onChange(config.copy(useRekernel = it)) },
            )
            AbkDivider()
            AbkHeading(stringResource(R.string.build_virtualization_support))
            // 6.12 / 6.18 只有开关两档；6.1 及更早才有 678/123/345 补丁栈。
            val virtualizationOptions = KernelSupport.virtualizationSupportOptions(config.kernelVersion)
            if (virtualizationOptions.size <= 2) {
                AbkSettingSwitch(
                    title = stringResource(R.string.build_virtualization_support),
                    description = null,
                    checked = config.virtualizationSupport == "on",
                    onCheckedChange = {
                        onChange(config.copy(virtualizationSupport = if (it) "on" else "off"))
                    },
                )
            } else {
                AbkSegmented(
                    options = listOf(
                        AbkOption("off", stringResource(R.string.build_virtualization_off)),
                        AbkOption("678", stringResource(R.string.build_virtualization_slot_678)),
                        AbkOption("123", stringResource(R.string.build_virtualization_slot_123)),
                        AbkOption("345", stringResource(R.string.build_virtualization_slot_345)),
                    ),
                    selectedId = config.virtualizationSupport,
                    onSelect = { onChange(config.copy(virtualizationSupport = it)) },
                )
            }
        }
    }
}

@Composable
private fun RootStep(config: KernelBuildConfig, onChange: (KernelBuildConfig) -> Unit) {
    val isNone = config.kernelsuVariant == KSU_VARIANT_NONE
    AbkCard {
        AbkHeading(stringResource(R.string.build_section_root))
        MutedText(stringResource(R.string.build_section_root_desc))
        AbkSegmented(
            options = KernelSupport.ksuVariantOptions().map { AbkOption(it, ksuVariantLabel(it)) },
            selectedId = config.kernelsuVariant,
            onSelect = { variant ->
                onChange(
                    if (variant == KSU_VARIANT_NONE) config.copy(kernelsuVariant = variant, patchModules = "")
                    else config.copy(kernelsuVariant = variant)
                )
            },
        )
        if (isNone) {
            MutedText(
                if (config.buildTarget == BUILD_TARGET_ONEPLUS) stringResource(R.string.build_oneplus_no_root_scheme_desc)
                else stringResource(R.string.build_no_root_scheme_desc)
            )
        } else {
            val branchOptions = KernelSupport.ksuBranchOptions()
            AbkSelect(
                options = branchOptions.map { AbkOption(it, it) },
                selectedId = config.kernelsuBranch,
                onSelect = { onChange(config.copy(kernelsuBranch = it)) },
                label = stringResource(R.string.build_ksu_branch),
            )
            if (config.kernelsuBranch == com.abk.kernel.data.model.KSU_BRANCH_CUSTOM) {
                AbkTextField(
                    value = config.customRef,
                    onValueChange = { onChange(config.copy(customRef = it)) },
                    label = stringResource(R.string.build_custom_ksu_ref),
                    placeholder = stringResource(R.string.build_custom_ksu_ref_placeholder),
                )
            }
            AbkTextField(
                value = config.patchModules,
                onValueChange = { onChange(config.copy(patchModules = it)) },
                label = stringResource(R.string.build_patch_modules),
                hint = stringResource(R.string.build_patch_modules_hint),
                placeholder = "https://github.com/owner/repo[@ref]",
                singleLine = false,
                minLines = 2,
            )
        }
    }
}

@Composable
private fun ksuVariantLabel(variant: String): String = when (variant) {
    KSU_VARIANT_NYASU -> stringResource(R.string.build_ksu_nyasu)
    KSU_VARIANT_NONE -> stringResource(R.string.build_ksu_none)
    else -> variant
}

@Composable
private fun ReviewStep(config: KernelBuildConfig, state: MainUiState) {
    AbkCard {
        AbkHeading(stringResource(R.string.build_confirm_submit))
        MutedText(stringResource(R.string.build_config_overview))
        MutedText(buildTargetSummary(config))
        MutedText(
            stringResource(
                R.string.build_section_root_line,
                ksuVariantLabel(config.kernelsuVariant),
                if (config.kernelsuVariant == KSU_VARIANT_NONE) "-" else config.kernelsuBranch,
            )
        )
        if (config.kernelsuVariant != KSU_VARIANT_NONE) {
            MutedText(
                if (config.patchModules.isBlank()) stringResource(R.string.build_patch_modules_empty)
                else stringResource(R.string.build_patch_modules_line, config.patchModules)
            )
        }
        MutedText(
            stringResource(
                R.string.build_features_line,
                featureLabel(config.useZram),
                featureLabel(config.useBbg),
                featureLabel(config.useDdk),
            )
        )
        MutedText(
            stringResource(
                R.string.build_sync_network_line,
                featureLabel(config.useNtsync),
                featureLabel(config.useNetworking),
            )
        )
        MutedText(stringResource(R.string.build_rekernel_line, featureLabel(config.useRekernel)))
        MutedText(stringResource(R.string.build_virtualization_line, virtualizationLabel(config.virtualizationSupport)))
        MutedText(stringResource(R.string.build_build_workflow_line, buildWorkflowName(config)))
    }
    if (state.buildQueue.isNotEmpty()) {
        MutedText(stringResource(R.string.build_active_queue_notice))
    }
}

@Composable
private fun featureLabel(enabled: Boolean): String =
    stringResource(if (enabled) R.string.build_feature_enabled else R.string.build_feature_disabled)

@Composable
private fun virtualizationLabel(value: String): String = when (value) {
    "678" -> stringResource(R.string.build_virtualization_slot_678)
    "123" -> stringResource(R.string.build_virtualization_slot_123)
    "345" -> stringResource(R.string.build_virtualization_slot_345)
    "on" -> stringResource(R.string.build_virtualization_on)
    else -> stringResource(R.string.build_virtualization_off)
}

/** Workflow file the current target dispatches, spelled the way the repo names it. */
private fun buildWorkflowName(config: KernelBuildConfig): String = when (config.buildTarget) {
    BUILD_TARGET_ONEPLUS -> "oneplus-custom.yml"
    BUILD_TARGET_CUSTOM_SOURCE -> "kernel-source.yml"
    else -> "kernel-custom.yml"
}

@Composable
private fun WorkflowRow(run: WorkflowRun) {
    AbkListItem(
        title = "#${run.runNumber} · ${run.displayTitle ?: run.name ?: "-"}",
        description = formatRunTimestamp(run.createdAt),
        trailing = { StatusBadge(run.tone(), run.statusLabel()) },
    )
}

/** One-line description of the kernel the current config will build. */
@Composable
private fun buildTargetSummary(config: KernelBuildConfig): String = when (config.buildTarget) {
    BUILD_TARGET_ONEPLUS -> stringResource(
        R.string.build_oneplus_kernel_line,
        "${config.androidVersion} / ${config.kernelVersion}",
        config.onePlusCpu,
        config.onePlusDeviceManifest,
    )
    BUILD_TARGET_CUSTOM_SOURCE -> stringResource(
        R.string.build_source_repo_line,
        config.sourceUrl.ifBlank { "-" },
    )
    else -> stringResource(R.string.build_kernel_line, config.androidVersion, config.kernelVersion, config.subLevel)
}
