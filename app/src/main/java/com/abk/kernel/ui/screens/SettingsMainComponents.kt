package com.abk.kernel.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.abk.kernel.R
import com.abk.kernel.data.model.AppUpdateCheckResult
import com.abk.kernel.ui.blur.blurredCardBackground
import com.abk.kernel.ui.blur.blurredCardSurfaceColor
import com.abk.kernel.ui.screens.flash.FlashAction
import io.github.xingguangcuican6666.abk.ui.components.AbkButtonStyle
import io.github.xingguangcuican6666.abk.ui.components.AbkCard
import io.github.xingguangcuican6666.abk.ui.components.AbkDialog
import io.github.xingguangcuican6666.abk.ui.components.AbkDivider
import io.github.xingguangcuican6666.abk.ui.components.AbkHeading
import io.github.xingguangcuican6666.abk.ui.components.AbkLinearProgress
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcon
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcons
import io.github.xingguangcuican6666.abk.ui.theme.AbkShapes
import io.github.xingguangcuican6666.abk.ui.theme.AbkTheme

@Composable
internal fun SettingsMainCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    AbkCard(
        modifier = Modifier.fillMaxWidth().blurredCardBackground(AbkShapes.Large),
        containerColor = blurredCardSurfaceColor(AbkTheme.colors.raised),
    ) {
        AbkHeading(title)
        content()
    }
}

@Composable
internal fun SettingsMainDestination(
    title: String,
    description: String,
    icon: ImageVector,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(description, color = AbkTheme.colors.inkMuted)
        FlashAction(title, icon, onClick, modifier = Modifier.fillMaxWidth(), enabled = enabled)
    }
}

@Composable
internal fun SettingsAccountCard(
    login: String?,
    description: String?,
    forkName: String?,
    canOpenFork: Boolean,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onOpenFork: () -> Unit,
    onOpenRunner: () -> Unit,
    avatar: (@Composable () -> Unit)? = null,
) {
    SettingsMainCard(stringResource(R.string.settings_account)) {
        if (login != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                avatar?.invoke()
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(login, style = MaterialTheme.typography.titleMedium)
                    description?.let { Text(it, color = AbkTheme.colors.inkMuted) }
                }
            }
            FlashAction(
                stringResource(R.string.settings_logout_desc),
                AbkIcons.Back,
                onLogout,
                modifier = Modifier.fillMaxWidth(),
                style = AbkButtonStyle.Ghost,
            )
            AbkDivider()
            Text(
                stringResource(R.string.settings_fork_repo),
                style = MaterialTheme.typography.titleSmall,
            )
            SelectionContainer {
                Text(
                    forkName ?: stringResource(R.string.settings_waiting_fork),
                    style =
                        MaterialTheme.typography.bodyMedium.copy(fontFamily = AbkTheme.monoFont),
                    color = AbkTheme.colors.inkMuted,
                )
            }
            FlashAction(
                stringResource(R.string.settings_fork_repo),
                AbkIcons.Build,
                onOpenFork,
                modifier = Modifier.fillMaxWidth(),
                enabled = canOpenFork,
            )
        } else {
            Text(
                stringResource(R.string.settings_not_logged_in),
                style = MaterialTheme.typography.titleSmall,
            )
            FlashAction(
                stringResource(R.string.settings_login_hint),
                AbkIcons.Forward,
                onLogin,
                modifier = Modifier.fillMaxWidth(),
                style = AbkButtonStyle.Primary,
            )
        }
        AbkDivider()
        Text(stringResource(R.string.settings_kernel_runner_desc), color = AbkTheme.colors.inkMuted)
        FlashAction(
            stringResource(R.string.settings_kernel_runner_title),
            AbkIcons.Cpu,
            onOpenRunner,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun SettingsLogoutDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AbkDialog(
        title = stringResource(R.string.settings_logout_title),
        dismissDescription = stringResource(R.string.close),
        onDismissRequest = onDismiss,
        actions = {
            FlashAction(
                stringResource(R.string.cancel),
                AbkIcons.Close,
                onDismiss,
                style = AbkButtonStyle.Ghost,
            )
            FlashAction(
                stringResource(R.string.confirm),
                AbkIcons.Back,
                onConfirm,
                style = AbkButtonStyle.Danger,
            )
        },
    ) {
        Text(stringResource(R.string.settings_logout_message))
    }
}

@Composable
internal fun SettingsAppUpdateFeedback(
    checking: Boolean,
    downloading: Boolean,
    downloadProgress: Int,
    info: AppUpdateCheckResult?,
    checkSubtitle: String,
    resultSubtitle: String?,
    error: String?,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
) {
    Text(checkSubtitle, color = AbkTheme.colors.inkMuted)
    FlashAction(
        stringResource(R.string.settings_check_app_update),
        AbkIcons.Refresh,
        onCheck,
        modifier = Modifier.fillMaxWidth(),
        loading = checking,
    )
    if (checking) {
        SettingsUpdateProgress(stringResource(R.string.settings_app_update_checking), null)
    }
    if (info != null) {
        AbkDivider()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            AbkIcon(if (info.hasUpdate) AbkIcons.Download else AbkIcons.Check, null)
            Text(
                stringResource(
                    if (info.hasUpdate) R.string.settings_app_update_available
                    else R.string.settings_app_update_latest
                ),
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
            )
        }
        resultSubtitle?.let { Text(it, color = AbkTheme.colors.inkMuted) }
        if (info.hasUpdate) {
            val downloadUrl = info.remote.downloadUrl
            if (downloading) {
                SettingsUpdateProgress(
                    stringResource(
                        R.string.settings_app_update_downloading_progress,
                        downloadProgress,
                    ),
                    (downloadProgress / 100f).coerceIn(0f, 1f),
                )
            } else if (downloadUrl.isBlank()) {
                Text(
                    stringResource(R.string.settings_app_update_link_missing),
                    color = AbkTheme.colors.inkMuted,
                )
            } else {
                SelectionContainer {
                    Text(
                        downloadUrl,
                        style =
                            MaterialTheme.typography.bodySmall.copy(fontFamily = AbkTheme.monoFont),
                        color = AbkTheme.colors.inkMuted,
                    )
                }
            }
            FlashAction(
                stringResource(R.string.settings_download_install_update),
                AbkIcons.Download,
                onDownload,
                modifier = Modifier.fillMaxWidth(),
                enabled = downloadUrl.isNotBlank(),
                loading = downloading,
                style = AbkButtonStyle.Primary,
            )
        }
    }
    error?.takeIf(String::isNotBlank)?.let { message ->
        Row(
            Modifier.semantics { this.error(message) },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            AbkIcon(AbkIcons.Warning, null, tint = AbkTheme.colors.danger.color)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.settings_app_update_error),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(message, color = AbkTheme.colors.danger.color)
            }
        }
    }
}

@Composable
private fun SettingsUpdateProgress(label: String, progress: Float?) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, Modifier.clearAndSetSemantics {}, style = MaterialTheme.typography.bodySmall)
        AbkLinearProgress(progress, label = label)
    }
}

@Composable
internal fun SettingsDiagnosticsAction(exporting: Boolean, onExport: () -> Unit) {
    Text(
        stringResource(R.string.settings_export_diagnostics_desc),
        color = AbkTheme.colors.inkMuted,
    )
    FlashAction(
        stringResource(R.string.settings_export_diagnostics),
        AbkIcons.Upload,
        onExport,
        modifier = Modifier.fillMaxWidth(),
        enabled = !exporting,
        loading = exporting,
    )
}
