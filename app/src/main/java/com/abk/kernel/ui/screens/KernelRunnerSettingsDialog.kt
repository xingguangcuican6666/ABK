package com.abk.kernel.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.abk.kernel.R
import com.abk.kernel.data.model.KernelRunnerConfig
import com.abk.kernel.data.model.KernelRunnerTarget
import com.abk.kernel.ui.screens.flash.FlashAction
import com.abk.kernel.viewmodel.KernelRunnerSettingsContext
import com.abk.kernel.viewmodel.KernelRunnerSettingsUiState
import io.github.xingguangcuican6666.abk.ui.components.AbkButtonStyle
import io.github.xingguangcuican6666.abk.ui.components.AbkDialog
import io.github.xingguangcuican6666.abk.ui.components.AbkDivider
import io.github.xingguangcuican6666.abk.ui.components.AbkLinearProgress
import io.github.xingguangcuican6666.abk.ui.components.AbkSettingSwitch
import io.github.xingguangcuican6666.abk.ui.components.AbkTextField
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcon
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcons
import io.github.xingguangcuican6666.abk.ui.theme.AbkTheme

@Composable
internal fun KernelRunnerSettingsDialog(
    state: KernelRunnerSettingsUiState,
    onRefresh: () -> Unit,
    onSave: (KernelRunnerTarget, String, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    LaunchedEffect(state.context) { onRefresh() }
    AbkDialog(
        title = stringResource(R.string.settings_kernel_runner_title),
        dismissDescription = stringResource(R.string.close),
        onDismissRequest = onDismiss,
        actions = {
            if (state.context != null) {
                FlashAction(
                    stringResource(R.string.refresh),
                    AbkIcons.Refresh,
                    onRefresh,
                    enabled = !state.busy,
                    style = AbkButtonStyle.Ghost,
                )
            }
            FlashAction(
                stringResource(R.string.close),
                AbkIcons.Close,
                onDismiss,
                style = AbkButtonStyle.Ghost,
            )
        },
    ) {
        val context = state.context
        if (context == null) {
            Text(stringResource(R.string.settings_kernel_runner_requires_fork))
        } else {
            SelectionContainer {
                Text(
                    stringResource(R.string.settings_kernel_runner_scope, context.fullName),
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = AbkTheme.monoFont),
                )
            }
            Text(
                stringResource(R.string.settings_kernel_runner_applies_next),
                color = AbkTheme.colors.inkMuted,
            )
            if (state.loading) {
                KernelRunnerProgress(stringResource(R.string.loading))
            }
            state.error?.let { KernelRunnerError(it) }
            state.settings?.let { settings ->
                KernelRunnerEditor(context, KernelRunnerTarget.GKI, settings.gki, state, onSave)
                AbkDivider()
                KernelRunnerEditor(
                    context,
                    KernelRunnerTarget.ONEPLUS,
                    settings.oneplus,
                    state,
                    onSave,
                )
            }
            Text(
                stringResource(R.string.settings_kernel_runner_offline),
                color = AbkTheme.colors.inkMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
internal fun KernelRunnerEditor(
    context: KernelRunnerSettingsContext,
    target: KernelRunnerTarget,
    config: KernelRunnerConfig,
    state: KernelRunnerSettingsUiState,
    onSave: (KernelRunnerTarget, String, Boolean) -> Unit,
) {
    // Draft values are local to this fork and snapshot. They are never a saved status.
    val saveRevision = state.saveRevisions[target]
    var labels by remember(context, target, config, saveRevision) { mutableStateOf(config.labels) }
    var enabled by
        remember(context, target, config, saveRevision) { mutableStateOf(config.selfHostedEnabled) }
    val editable = config.supported && !state.busy
    val dirty = (enabled && labels.trim() != config.labels) || enabled != config.selfHostedEnabled
    val missingLabels = enabled && labels.isBlank()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(
                when (target) {
                    KernelRunnerTarget.GKI -> R.string.settings_kernel_runner_gki
                    KernelRunnerTarget.ONEPLUS -> R.string.settings_kernel_runner_oneplus
                }
            ),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
        )
        if (!config.supported) {
            KernelRunnerError(stringResource(R.string.settings_kernel_runner_unsupported))
        }
        AbkSettingSwitch(
            title = stringResource(R.string.settings_kernel_runner_self_hosted),
            description =
                stringResource(
                    if (enabled) R.string.settings_kernel_runner_self_hosted_desc
                    else R.string.settings_kernel_runner_github_hosted_desc
                ),
            checked = enabled,
            onCheckedChange = { enabled = it },
            enabled = editable,
        )
        AbkTextField(
            value = labels,
            onValueChange = { labels = it },
            label = stringResource(R.string.settings_kernel_runner_labels),
            modifier = Modifier.fillMaxWidth(),
            placeholder = stringResource(R.string.settings_kernel_runner_labels_example),
            hint = stringResource(R.string.settings_kernel_runner_labels_hint),
            error =
                if (missingLabels) stringResource(R.string.settings_kernel_runner_labels_required)
                else null,
            enabled = editable && enabled,
            singleLine = false,
            maxLines = 3,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = AbkTheme.monoFont),
        )
        if (dirty) {
            AbkIcon(AbkIcons.Info, null, tint = AbkTheme.colors.inkMuted)
            Text(
                stringResource(R.string.settings_kernel_runner_unsaved),
                color = AbkTheme.colors.inkMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        } else if (state.savedTarget == target) {
            AbkIcon(AbkIcons.Check, null, tint = AbkTheme.colors.success.color)
            Text(
                stringResource(R.string.settings_kernel_runner_saved),
                color = AbkTheme.colors.success.color,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (state.savingTarget == target) {
            KernelRunnerProgress(stringResource(R.string.settings_kernel_runner_saving))
        }
        FlashAction(
            stringResource(
                if (state.savingTarget == target) R.string.settings_kernel_runner_saving
                else R.string.save
            ),
            AbkIcons.Check,
            onClick = { onSave(target, labels.trim(), enabled) },
            enabled = editable && (dirty || !enabled) && !missingLabels,
            modifier = Modifier.fillMaxWidth(),
            style = AbkButtonStyle.Primary,
        )
    }
}

@Composable
private fun KernelRunnerProgress(label: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, Modifier.clearAndSetSemantics {}, color = AbkTheme.colors.inkMuted)
        AbkLinearProgress(null, label = label)
    }
}

@Composable
private fun KernelRunnerError(message: String) {
    Column(
        Modifier.semantics { error(message) },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AbkIcon(AbkIcons.Warning, null, tint = AbkTheme.colors.danger.color)
        Text(message, color = AbkTheme.colors.danger.color)
    }
}
