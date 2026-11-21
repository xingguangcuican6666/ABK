package com.abk.kernel.ui.screens.flash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.abk.kernel.R
import com.abk.kernel.utils.RootUtils
import io.github.xingguangcuican6666.abk.ui.components.AbkButton
import io.github.xingguangcuican6666.abk.ui.components.AbkButtonStyle
import io.github.xingguangcuican6666.abk.ui.components.AbkDialog
import io.github.xingguangcuican6666.abk.ui.components.AbkLinearProgress
import io.github.xingguangcuican6666.abk.ui.components.AbkOption
import io.github.xingguangcuican6666.abk.ui.components.AbkRadioGroup
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcon
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcons
import io.github.xingguangcuican6666.abk.ui.theme.AbkTheme

@Composable
internal fun FlashAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: AbkButtonStyle = AbkButtonStyle.Outline,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    AbkButton(
        onClick = onClick,
        modifier = modifier,
        style = style,
        enabled = enabled,
        loading = loading,
        loadingDescription = label,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!loading) AbkIcon(icon, null, size = 18.dp)
            Text(label)
        }
    }
}

@Composable
internal fun FlashDownloadProgress(
    percent: Int,
    fraction: Float = (percent / 100f).coerceIn(0f, 1f),
) {
    val label = stringResource(R.string.flash_download_progress, percent)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = AbkTheme.colors.inkMuted)
        AbkLinearProgress(fraction, modifier = Modifier.fillMaxWidth(), label = label)
    }
}

@Composable
internal fun FlashSlotOptions(
    currentLabel: String,
    inactiveLabel: String,
    selected: RootUtils.Ak3SlotTarget,
    onSelect: (RootUtils.Ak3SlotTarget) -> Unit,
) {
    Text(
        stringResource(R.string.root_patch_ak3_slot_title),
        style = MaterialTheme.typography.titleSmall,
    )
    AbkRadioGroup(
        options =
            listOf(
                AbkOption(RootUtils.Ak3SlotTarget.CURRENT.name, currentLabel),
                AbkOption(RootUtils.Ak3SlotTarget.INACTIVE.name, inactiveLabel),
            ),
        selectedId = selected.name,
        onSelect = { onSelect(RootUtils.Ak3SlotTarget.valueOf(it)) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
internal fun FlashConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    icon: ImageVector = AbkIcons.Warning,
    details: @Composable ColumnScope.() -> Unit = {},
) {
    AbkDialog(
        title = title,
        onDismissRequest = onDismiss,
        dismissDescription = stringResource(R.string.close),
        actions = {
            AbkButton(onClick = onDismiss, style = AbkButtonStyle.Outline) {
                Text(stringResource(R.string.cancel))
            }
            AbkButton(onClick = onConfirm, style = AbkButtonStyle.Danger) { Text(confirmLabel) }
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            AbkIcon(icon, null, tint = AbkTheme.colors.danger.color)
            Text(message, modifier = Modifier.weight(1f))
        }
        details()
    }
}
