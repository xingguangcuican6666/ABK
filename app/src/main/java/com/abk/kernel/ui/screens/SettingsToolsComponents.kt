package com.abk.kernel.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.abk.kernel.R
import com.abk.kernel.data.model.KernelTcpCongestionControlState
import com.abk.kernel.ui.components.AbkScreenHorizontalPadding
import com.abk.kernel.ui.screens.flash.FlashAction
import io.github.xingguangcuican6666.abk.ui.components.AbkDivider
import io.github.xingguangcuican6666.abk.ui.components.AbkLinearProgress
import io.github.xingguangcuican6666.abk.ui.components.AbkSettingSwitch
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcon
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcons
import io.github.xingguangcuican6666.abk.ui.theme.AbkTheme

@Composable
internal fun SettingsManagerToolsContent(
    topBarHeight: Dp,
    loading: Boolean,
    showRefreshLoading: Boolean,
    selinuxMode: String,
    selinuxEnforcing: Boolean,
    umountPathCount: Int,
    error: String?,
    actionId: String?,
    onSelinuxChange: (Boolean) -> Unit,
    onBackupAllowlist: () -> Unit,
    onRestoreAllowlist: () -> Unit,
) {
    val selinuxBusy = actionId == "selinux_mode"
    val backupBusy = actionId == "backup_allowlist"
    val restoreBusy = actionId == "restore_allowlist"
    val showInitialLoading = loading && umountPathCount == 0 && error == null
    SettingsToolsPage(
        topBarHeight = topBarHeight,
        refreshing = showRefreshLoading || showInitialLoading,
        loadingLabel =
            stringResource(
                if (showRefreshLoading) R.string.settings_tools_refreshing else R.string.loading
            ),
    ) {
        SettingsMainCard(stringResource(R.string.settings_system_tools)) {
            AbkSettingSwitch(
                title = stringResource(R.string.settings_selinux_mode),
                description = stringResource(R.string.settings_current_value, selinuxMode),
                checked = selinuxEnforcing,
                enabled = !loading && !selinuxBusy,
                onCheckedChange = onSelinuxChange,
            )
            if (selinuxBusy) SettingsToolsProgress(stringResource(R.string.settings_selinux_mode))
            AbkDivider()
            Text(
                stringResource(R.string.settings_umount_paths),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                if (umountPathCount == 0) {
                    stringResource(R.string.settings_umount_no_paths)
                } else {
                    stringResource(R.string.settings_umount_path_count, umountPathCount)
                },
                color = AbkTheme.colors.inkMuted,
            )
        }
        SettingsMainCard(stringResource(R.string.settings_allowlist)) {
            Text(
                stringResource(R.string.settings_backup_allowlist_desc),
                color = AbkTheme.colors.inkMuted,
            )
            FlashAction(
                stringResource(R.string.settings_backup_allowlist),
                AbkIcons.Upload,
                onBackupAllowlist,
                modifier = Modifier.fillMaxWidth(),
                enabled = !backupBusy,
                loading = backupBusy,
            )
            AbkDivider()
            Text(
                stringResource(R.string.settings_restore_allowlist_desc),
                color = AbkTheme.colors.inkMuted,
            )
            FlashAction(
                stringResource(R.string.settings_restore_allowlist),
                AbkIcons.Download,
                onRestoreAllowlist,
                modifier = Modifier.fillMaxWidth(),
                enabled = !restoreBusy,
                loading = restoreBusy,
            )
        }
        if (error != null) {
            SettingsMainCard(stringResource(R.string.settings_tool_status)) {
                SettingsToolsError(stringResource(R.string.settings_operation_incomplete), error)
            }
        }
    }
}

@Composable
internal fun SettingsKernelCapabilitiesContent(
    topBarHeight: Dp,
    loading: Boolean,
    showRefreshLoading: Boolean,
    tcp: KernelTcpCongestionControlState?,
    error: String?,
    actionId: String?,
    onRefresh: () -> Unit,
    onTcpAlgorithmSelected: (String) -> Unit,
) {
    val showInitialLoading = loading && tcp == null && error == null
    SettingsToolsPage(
        topBarHeight = topBarHeight,
        refreshing = showRefreshLoading || showInitialLoading,
        loadingLabel =
            stringResource(
                if (showRefreshLoading) R.string.settings_kernel_capabilities_refreshing
                else R.string.loading
            ),
    ) {
        if (tcp != null && tcp.available) {
            SettingsMainCard(stringResource(R.string.settings_tcp_congestion_control)) {
                Column(
                    Modifier.fillMaxWidth().selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    tcp.availableAlgorithms.forEachIndexed { index, algorithm ->
                        if (index > 0) AbkDivider()
                        val selected = algorithm == tcp.currentAlgorithm
                        Column(
                            Modifier.fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .selectable(
                                    selected = selected,
                                    enabled = actionId == null && !selected,
                                    role = Role.RadioButton,
                                    onClick = { if (!selected) onTcpAlgorithmSelected(algorithm) },
                                )
                                .padding(vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                AbkIcon(if (selected) AbkIcons.Check else AbkIcons.Cpu, null)
                                Text(
                                    algorithm,
                                    Modifier.weight(1f),
                                    style =
                                        MaterialTheme.typography.bodyLarge.copy(
                                            fontFamily = AbkTheme.monoFont
                                        ),
                                )
                            }
                            Text(
                                stringResource(
                                    if (selected) R.string.settings_tcp_congestion_selected
                                    else R.string.settings_tcp_congestion_available
                                ),
                                color = AbkTheme.colors.inkMuted,
                            )
                            if (actionId == "tcp_congestion:$algorithm")
                                SettingsToolsProgress(algorithm)
                        }
                    }
                }
            }
        } else {
            SettingsMainCard(stringResource(R.string.settings_kernel_capabilities)) {
                if (error != null) {
                    SettingsToolsError(
                        stringResource(R.string.settings_kernel_capabilities_unavailable),
                        error,
                    )
                } else {
                    AbkIcon(AbkIcons.Info, null)
                    Text(
                        stringResource(R.string.settings_kernel_capabilities_unavailable),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        stringResource(R.string.settings_kernel_capabilities_unavailable_desc),
                        color = AbkTheme.colors.inkMuted,
                    )
                }
                FlashAction(
                    stringResource(R.string.refresh),
                    AbkIcons.Refresh,
                    onRefresh,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        error
            ?.takeIf { it.isNotBlank() && tcp != null }
            ?.let { message ->
                SettingsMainCard(stringResource(R.string.settings_status)) {
                    SettingsToolsError(
                        stringResource(R.string.settings_operation_incomplete),
                        message,
                    )
                    FlashAction(
                        stringResource(R.string.refresh),
                        AbkIcons.Refresh,
                        onRefresh,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
    }
}

@Composable
private fun SettingsToolsPage(
    topBarHeight: Dp,
    refreshing: Boolean,
    loadingLabel: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AbkScreenHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(topBarHeight + 16.dp))
        Crossfade(targetState = refreshing, label = "settings-tools-refresh") { showingProgress ->
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (showingProgress) SettingsToolsProgress(loadingLabel) else content()
            }
        }
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun SettingsToolsProgress(label: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, Modifier.clearAndSetSemantics {}, color = AbkTheme.colors.inkMuted)
        AbkLinearProgress(null, label = label)
    }
}

@Composable
private fun SettingsToolsError(title: String, message: String) {
    Column(
        Modifier.semantics { error(message) },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AbkIcon(AbkIcons.Warning, null, tint = AbkTheme.colors.danger.color)
        Text(title, style = MaterialTheme.typography.titleSmall)
        SelectionContainer { Text(message, color = AbkTheme.colors.danger.color) }
    }
}
