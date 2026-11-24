package com.abk.kernel.ui.build

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.abk.kernel.R
import com.abk.kernel.data.model.BUILD_TARGET_GKI
import com.abk.kernel.data.model.BUILD_TARGET_ONEPLUS
import com.abk.kernel.data.model.BuildQueueItemStatus
import com.abk.kernel.data.model.BuildStatus
import com.abk.kernel.data.model.KernelBuildConfig
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
import io.github.xingguangcuican6666.abk.ui.components.AbkSettingSwitch
import io.github.xingguangcuican6666.abk.ui.components.AbkText
import io.github.xingguangcuican6666.abk.ui.theme.AbkSpacing
import io.github.xingguangcuican6666.abk.ui.theme.AbkTheme
import io.github.xingguangcuican6666.abk.ui.theme.AbkTone

/**
 * 构建：目标与功能开关的精简表单、最近工作流和取消入口。
 * Advanced parameters and plans arrive in the next phase; this page submits
 * the persisted config as-is.
 */
@Composable
fun BuildDestination(
    vm: MainViewModel,
    state: MainUiState,
    onChildPageVisibleChange: (Boolean) -> Unit,
) {
    LaunchedEffect(Unit) { onChildPageVisibleChange(false) }
    val config = state.buildConfig
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

        AbkCard {
            AbkHeading(stringResource(R.string.build_target_title))
            MutedText(stringResource(R.string.build_target_desc))
            AbkSegmented(
                options = listOf(
                    AbkOption(BUILD_TARGET_GKI, stringResource(R.string.build_target_gki)),
                    AbkOption(BUILD_TARGET_ONEPLUS, stringResource(R.string.build_target_oneplus)),
                ),
                selectedId = config.buildTarget,
                onSelect = { vm.updateBuildConfig(config.copy(buildTarget = it)) },
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AbkSpacing.Xl)) {
                AbkText(
                    buildTargetSummary(config),
                    Modifier.weight(1f),
                    MaterialTheme.typography.bodyMedium,
                    AbkTheme.colors.inkSoft,
                )
            }
        }

        AbkCard {
            AbkHeading(stringResource(R.string.build_features))
            AbkSettingSwitch(
                title = stringResource(R.string.build_enable_susfs),
                description = null,
                checked = !config.cancelSusfs,
                onCheckedChange = { vm.updateBuildConfig(config.copy(cancelSusfs = !it)) },
            )
            AbkDivider()
            AbkSettingSwitch(
                title = stringResource(R.string.build_enable_zram),
                description = null,
                checked = config.useZram,
                onCheckedChange = { vm.updateBuildConfig(config.copy(useZram = it)) },
            )
            AbkDivider()
            AbkSettingSwitch(
                title = stringResource(R.string.build_enable_kpm),
                description = null,
                checked = config.useKpm,
                onCheckedChange = { vm.updateBuildConfig(config.copy(useKpm = it)) },
            )
            AbkDivider()
            AbkSettingSwitch(
                title = stringResource(R.string.build_enable_networking),
                description = null,
                checked = config.useNetworking,
                onCheckedChange = { vm.updateBuildConfig(config.copy(useNetworking = it)) },
            )
        }

        AbkButton(
            onClick = {
                submitting = true
                vm.dispatchBuild(config)
            },
            modifier = Modifier.fillMaxWidth(),
            style = AbkButtonStyle.Primary,
            size = io.github.xingguangcuican6666.abk.ui.components.AbkSize.Large,
            enabled = canBuild && !submitting,
            loading = submitting,
            loadingDescription = stringResource(R.string.build_running),
        ) {
            AbkText(stringResource(R.string.build_submit))
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
private fun buildTargetSummary(config: KernelBuildConfig): String = if (config.buildTarget == BUILD_TARGET_ONEPLUS) {
    stringResource(
        R.string.build_oneplus_kernel_line,
        "${config.androidVersion} / ${config.kernelVersion}",
        config.onePlusCpu,
        config.onePlusDeviceManifest,
    )
} else {
    stringResource(R.string.build_kernel_line, config.androidVersion, config.kernelVersion, config.subLevel)
}
