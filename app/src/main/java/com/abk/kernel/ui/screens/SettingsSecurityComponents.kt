package com.abk.kernel.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.abk.kernel.R
import com.abk.kernel.ui.screens.flash.FlashAction
import io.github.xingguangcuican6666.abk.ui.components.AbkButtonStyle
import io.github.xingguangcuican6666.abk.ui.components.AbkDialog
import io.github.xingguangcuican6666.abk.ui.components.AbkLinearProgress
import io.github.xingguangcuican6666.abk.ui.components.AbkSettingSwitch
import io.github.xingguangcuican6666.abk.ui.components.AbkTextField
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcon
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcons
import io.github.xingguangcuican6666.abk.ui.theme.AbkTheme
import kotlinx.coroutines.delay

@Composable
internal fun SettingsSecurityCard(
    canManageKeys: Boolean,
    signingEnabled: Boolean,
    signingConfigured: Boolean,
    signingBusy: Boolean,
    secretConfigured: Boolean,
    secretBusy: Boolean,
    onSigningChange: (Boolean) -> Unit,
    onImportKeys: () -> Unit,
    onResetKeys: () -> Unit,
    onEditSecret: () -> Unit,
    onDeleteSecret: () -> Unit,
) {
    SettingsMainCard(stringResource(R.string.settings_security)) {
        AbkSettingSwitch(
            title = stringResource(R.string.settings_security_signing_title),
            description =
                stringResource(
                    when {
                        !canManageKeys -> R.string.settings_security_requires_fork
                        signingEnabled && signingConfigured ->
                            R.string.settings_security_status_enabled_configured
                        signingEnabled -> R.string.settings_security_status_enabled_pending
                        else -> R.string.settings_security_status_disabled
                    }
                ),
            checked = signingEnabled,
            onCheckedChange = onSigningChange,
            enabled = canManageKeys && !signingBusy,
        )
        SettingsMainDestination(
            title = stringResource(R.string.settings_security_import_keys),
            description =
                stringResource(
                    when {
                        !canManageKeys -> R.string.settings_security_requires_fork
                        !signingEnabled -> R.string.settings_security_import_requires_enabled
                        else -> R.string.settings_security_import_keys_desc
                    }
                ),
            icon = AbkIcons.Upload,
            enabled = canManageKeys && signingEnabled && !signingBusy,
            onClick = onImportKeys,
        )
        SettingsMainDestination(
            title = stringResource(R.string.settings_security_reset_keys),
            description = stringResource(R.string.settings_security_reset_keys_desc),
            icon = AbkIcons.Refresh,
            enabled = canManageKeys && signingEnabled && !signingBusy,
            onClick = onResetKeys,
        )
        SettingsMainDestination(
            title = stringResource(R.string.settings_custom_source_secret_title),
            description =
                stringResource(
                    when {
                        !canManageKeys -> R.string.settings_security_requires_fork
                        secretConfigured -> R.string.settings_custom_source_secret_configured
                        else -> R.string.settings_custom_source_secret_missing
                    }
                ),
            icon = AbkIcons.Settings,
            enabled = canManageKeys && !secretBusy,
            onClick = onEditSecret,
        )
        if (secretConfigured) {
            SettingsMainDestination(
                title = stringResource(R.string.settings_custom_source_secret_delete),
                description = stringResource(R.string.settings_custom_source_secret_delete_desc),
                icon = AbkIcons.Trash,
                enabled = canManageKeys && !secretBusy,
                onClick = onDeleteSecret,
            )
        }
        if (signingBusy)
            SettingsSecurityProgress(stringResource(R.string.settings_security_operation_running))
        if (secretBusy)
            SettingsSecurityProgress(
                stringResource(R.string.settings_custom_source_secret_operation)
            )
    }
}

@Composable
internal fun TimedConfirmationDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    delaySeconds: Int = 5,
) {
    var remainingSeconds by remember { mutableStateOf(delaySeconds) }
    LaunchedEffect(Unit) {
        while (remainingSeconds > 0) {
            delay(1_000)
            remainingSeconds -= 1
        }
    }
    AbkDialog(
        title = title,
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
                if (remainingSeconds > 0) {
                    stringResource(
                        R.string.settings_security_confirm_countdown,
                        confirmLabel,
                        remainingSeconds,
                    )
                } else confirmLabel,
                AbkIcons.Warning,
                onConfirm,
                enabled = remainingSeconds <= 0,
                style = AbkButtonStyle.Danger,
            )
        },
    ) {
        AbkIcon(AbkIcons.Warning, null, tint = AbkTheme.colors.warning.color)
        Text(message)
    }
}

@Composable
internal fun ImportArtifactSigningKeysDialog(
    publicKeyText: String,
    privateKeyText: String,
    error: String?,
    importing: Boolean,
    onPublicKeyTextChange: (String) -> Unit,
    onPrivateKeyTextChange: (String) -> Unit,
    onPickPublicKey: () -> Unit,
    onPickPrivateKey: () -> Unit,
    onImport: () -> Unit,
    onDismiss: () -> Unit,
) {
    AbkDialog(
        title = stringResource(R.string.settings_security_import_dialog_title),
        dismissDescription = stringResource(R.string.close),
        onDismissRequest = onDismiss,
        dismissible = !importing,
        actions = {
            FlashAction(
                stringResource(R.string.cancel),
                AbkIcons.Close,
                onDismiss,
                enabled = !importing,
                style = AbkButtonStyle.Ghost,
            )
            FlashAction(
                stringResource(R.string.settings_import),
                AbkIcons.Upload,
                onImport,
                enabled = !importing && publicKeyText.isNotBlank() && privateKeyText.isNotBlank(),
                loading = importing,
                style = AbkButtonStyle.Primary,
            )
        },
    ) {
        Text(
            stringResource(R.string.settings_security_import_keys_desc),
            color = AbkTheme.colors.inkMuted,
        )
        AbkTextField(
            value = publicKeyText,
            onValueChange = onPublicKeyTextChange,
            label = stringResource(R.string.settings_security_import_public_key),
            modifier = Modifier.fillMaxWidth(),
            singleLine = false,
            minLines = 4,
            maxLines = 8,
            enabled = !importing,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = AbkTheme.monoFont),
        )
        FlashAction(
            stringResource(R.string.settings_security_import_pick_public_key),
            AbkIcons.Search,
            onPickPublicKey,
            modifier = Modifier.fillMaxWidth(),
            enabled = !importing,
        )
        AbkTextField(
            value = privateKeyText,
            onValueChange = onPrivateKeyTextChange,
            label = stringResource(R.string.settings_security_import_private_key),
            modifier = Modifier.fillMaxWidth(),
            singleLine = false,
            minLines = 4,
            maxLines = 8,
            enabled = !importing,
            visualTransformation = PasswordVisualTransformation(),
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = AbkTheme.monoFont),
        )
        FlashAction(
            stringResource(R.string.settings_security_import_pick_private_key),
            AbkIcons.Search,
            onPickPrivateKey,
            modifier = Modifier.fillMaxWidth(),
            enabled = !importing,
        )
        error?.let { SettingsSecurityError(it) }
        if (importing)
            SettingsSecurityProgress(stringResource(R.string.settings_security_operation_running))
    }
}

@Composable
internal fun SettingsSourceSecretDialog(
    value: String,
    busy: Boolean,
    onValueChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    AbkDialog(
        title = stringResource(R.string.settings_custom_source_secret_dialog_title),
        dismissDescription = stringResource(R.string.close),
        onDismissRequest = onDismiss,
        dismissible = !busy,
        actions = {
            FlashAction(
                stringResource(android.R.string.cancel),
                AbkIcons.Close,
                onDismiss,
                enabled = !busy,
                style = AbkButtonStyle.Ghost,
            )
            FlashAction(
                stringResource(R.string.save),
                AbkIcons.Check,
                onSave,
                enabled = value.isNotBlank() && !busy,
                loading = busy,
                style = AbkButtonStyle.Primary,
            )
        },
    ) {
        Text(
            stringResource(R.string.settings_custom_source_secret_dialog_desc),
            color = AbkTheme.colors.inkMuted,
        )
        AbkTextField(
            value = value,
            onValueChange = onValueChange,
            label = stringResource(R.string.build_source_pat),
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
            visualTransformation = PasswordVisualTransformation(),
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = AbkTheme.monoFont),
        )
        if (busy)
            SettingsSecurityProgress(
                stringResource(R.string.settings_custom_source_secret_operation)
            )
    }
}

@Composable
internal fun SettingsDeleteSourceSecretDialog(
    busy: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AbkDialog(
        title = stringResource(R.string.settings_custom_source_secret_delete),
        dismissDescription = stringResource(R.string.close),
        onDismissRequest = onDismiss,
        dismissible = !busy,
        actions = {
            FlashAction(
                stringResource(android.R.string.cancel),
                AbkIcons.Close,
                onDismiss,
                enabled = !busy,
                style = AbkButtonStyle.Ghost,
            )
            FlashAction(
                stringResource(R.string.delete),
                AbkIcons.Trash,
                onConfirm,
                enabled = !busy,
                loading = busy,
                style = AbkButtonStyle.Danger,
            )
        },
    ) {
        Text(stringResource(R.string.settings_custom_source_secret_delete_confirm))
        if (busy)
            SettingsSecurityProgress(
                stringResource(R.string.settings_custom_source_secret_operation)
            )
    }
}

@Composable
private fun SettingsSecurityProgress(label: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, Modifier.clearAndSetSemantics {}, color = AbkTheme.colors.inkMuted)
        AbkLinearProgress(null, label = label)
    }
}

@Composable
private fun SettingsSecurityError(message: String) {
    Column(
        Modifier.semantics { error(message) },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AbkIcon(AbkIcons.Warning, null, tint = AbkTheme.colors.danger.color)
        Text(message, color = AbkTheme.colors.danger.color)
    }
}
