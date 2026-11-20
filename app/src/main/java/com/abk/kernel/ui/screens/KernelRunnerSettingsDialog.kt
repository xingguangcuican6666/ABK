package com.abk.kernel.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.abk.kernel.R
import com.abk.kernel.data.model.KernelRunnerConfig
import com.abk.kernel.data.model.KernelRunnerTarget
import com.abk.kernel.viewmodel.KernelRunnerSettingsContext
import com.abk.kernel.viewmodel.KernelRunnerSettingsUiState

@Composable
internal fun KernelRunnerSettingsDialog(
    state: KernelRunnerSettingsUiState,
    onRefresh: () -> Unit,
    onSave: (KernelRunnerTarget, String, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    LaunchedEffect(state.context) { onRefresh() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_kernel_runner_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val context = state.context
                if (context == null) {
                    Text(stringResource(R.string.settings_kernel_runner_requires_fork))
                } else {
                    Text(stringResource(R.string.settings_kernel_runner_scope, context.fullName))
                    Text(
                        stringResource(R.string.settings_kernel_runner_applies_next),
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (state.loading) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text(stringResource(R.string.loading))
                        }
                    }
                    state.error?.let { message ->
                        Text(message, color = MaterialTheme.colorScheme.error)
                    }
                    state.settings?.let { settings ->
                        KernelRunnerEditor(context, KernelRunnerTarget.GKI, settings.gki, state, onSave)
                        HorizontalDivider()
                        KernelRunnerEditor(context, KernelRunnerTarget.ONEPLUS, settings.oneplus, state, onSave)
                    }
                    Text(
                        stringResource(R.string.settings_kernel_runner_offline),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        dismissButton = {
            if (state.context != null) {
                TextButton(onClick = onRefresh, enabled = !state.busy) {
                    Text(stringResource(R.string.refresh))
                }
            }
        }
    )
}

@Composable
private fun KernelRunnerEditor(
    context: KernelRunnerSettingsContext,
    target: KernelRunnerTarget,
    config: KernelRunnerConfig,
    state: KernelRunnerSettingsUiState,
    onSave: (KernelRunnerTarget, String, Boolean) -> Unit
) {
    // Draft values are local to this fork and snapshot. They are never a saved status.
    val saveRevision = state.saveRevisions[target]
    var labels by remember(context, target, config, saveRevision) { mutableStateOf(config.labels) }
    var enabled by remember(context, target, config, saveRevision) { mutableStateOf(config.selfHostedEnabled) }
    val editable = config.supported && !state.busy
    val dirty = (enabled && labels.trim() != config.labels) || enabled != config.selfHostedEnabled
    val missingLabels = enabled && labels.isBlank()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(when (target) {
                KernelRunnerTarget.GKI -> R.string.settings_kernel_runner_gki
                KernelRunnerTarget.ONEPLUS -> R.string.settings_kernel_runner_oneplus
            }),
            style = MaterialTheme.typography.titleMedium
        )
        if (!config.supported) {
            Text(
                stringResource(R.string.settings_kernel_runner_unsupported),
                color = MaterialTheme.colorScheme.error
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text(stringResource(R.string.settings_kernel_runner_self_hosted))
                Text(
                    stringResource(if (enabled) R.string.settings_kernel_runner_self_hosted_desc
                    else R.string.settings_kernel_runner_github_hosted_desc),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Switch(checked = enabled, onCheckedChange = { enabled = it }, enabled = editable)
        }
        OutlinedTextField(
            value = labels,
            onValueChange = { labels = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.settings_kernel_runner_labels)) },
            placeholder = { Text(stringResource(R.string.settings_kernel_runner_labels_example)) },
            supportingText = {
                Text(stringResource(if (missingLabels) R.string.settings_kernel_runner_labels_required
                else R.string.settings_kernel_runner_labels_hint))
            },
            isError = missingLabels,
            enabled = editable && enabled,
            maxLines = 3
        )
        if (dirty) {
            Text(
                stringResource(R.string.settings_kernel_runner_unsaved),
                style = MaterialTheme.typography.bodySmall
            )
        } else if (state.savedTarget == target) {
            Text(
                stringResource(R.string.settings_kernel_runner_saved),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(Modifier.height(2.dp))
        Button(
            onClick = { onSave(target, labels.trim(), enabled) },
            enabled = editable && (dirty || !enabled) && !missingLabels,
            modifier = Modifier.align(Alignment.End)
        ) {
            Text(stringResource(if (state.savingTarget == target)
                R.string.settings_kernel_runner_saving else R.string.save))
        }
    }
}
