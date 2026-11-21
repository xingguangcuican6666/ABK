package com.abk.kernel.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.abk.kernel.R
import com.abk.kernel.data.model.ManagerSettingItem
import com.abk.kernel.data.model.ManagerSettingKind
import com.abk.kernel.ui.screens.flash.FlashAction
import io.github.xingguangcuican6666.abk.ui.components.AbkDivider
import io.github.xingguangcuican6666.abk.ui.components.AbkLinearProgress
import io.github.xingguangcuican6666.abk.ui.components.AbkOption
import io.github.xingguangcuican6666.abk.ui.components.AbkSelect
import io.github.xingguangcuican6666.abk.ui.components.AbkSettingSwitch
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcon
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcons
import io.github.xingguangcuican6666.abk.ui.theme.AbkTheme

@Composable
internal fun SettingsManagerCard(
    title: String,
    items: List<ManagerSettingItem>,
    loading: Boolean,
    showRefreshLoading: Boolean,
    error: String?,
    actionId: String?,
    onRetry: () -> Unit,
    onNavigate: (String) -> Unit,
    onModeChange: (String, Int) -> Unit,
    onCheckedChange: (String, Boolean) -> Unit,
) {
    SettingsMainCard(title) {
        when {
            loading && items.isEmpty() -> {
                SettingsManagerProgress(stringResource(R.string.settings_manager_loading_title))
            }
            error != null -> {
                Column(
                    Modifier.semantics { this.error(error) },
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    AbkIcon(AbkIcons.Warning, null, tint = AbkTheme.colors.danger.color)
                    Text(
                        stringResource(R.string.settings_manager_load_failed),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(error, color = AbkTheme.colors.danger.color)
                }
                FlashAction(
                    stringResource(R.string.retry),
                    AbkIcons.Refresh,
                    onRetry,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (showRefreshLoading) {
            SettingsManagerProgress(stringResource(R.string.settings_manager_loading_title))
        } else {
            items.forEachIndexed { index, item ->
                if (index > 0) AbkDivider()
                val actionInFlight = actionId == item.id
                when (item.kind) {
                    ManagerSettingKind.NAVIGATION ->
                        SettingsMainDestination(
                            title = item.title,
                            description = item.subtitle,
                            icon =
                                when (item.id) {
                                    "app_profile_templates" -> AbkIcons.Edit
                                    "manager_tools" -> AbkIcons.Build
                                    "kernel_capabilities" -> AbkIcons.Cpu
                                    "susfs_control",
                                    "kpm" -> AbkIcons.Modules
                                    else -> AbkIcons.Settings
                                },
                            enabled = item.enabled && !actionInFlight,
                            onClick = { onNavigate(item.id) },
                        )
                    ManagerSettingKind.MODE ->
                        SettingsManagerModeItem(
                            item = item,
                            actionInFlight = actionInFlight,
                            onSelected = { onModeChange(item.id, it) },
                        )
                    ManagerSettingKind.SWITCH ->
                        AbkSettingSwitch(
                            title = item.title,
                            description = item.subtitle,
                            checked = item.checked,
                            enabled = item.enabled && !actionInFlight,
                            onCheckedChange = { onCheckedChange(item.id, it) },
                        )
                }
                if (actionInFlight) SettingsManagerProgress(item.title)
            }
        }
    }
}

@Composable
internal fun SettingsManagerModeItem(
    item: ManagerSettingItem,
    actionInFlight: Boolean,
    onSelected: (Int) -> Unit,
) {
    val options =
        remember(item.options) { item.options.map { it.trim() }.filter { it.isNotBlank() } }
    val selectedIndex =
        if (options.isNotEmpty()) item.selectedIndex.coerceIn(0, options.lastIndex) else 0
    val enabled = item.enabled && !actionInFlight && options.isNotEmpty()
    AbkSelect(
        options = options.mapIndexed { index, label -> AbkOption(index.toString(), label) },
        selectedId = selectedIndex.toString(),
        onSelect = { id ->
            val index = id.toInt()
            if (index != selectedIndex) onSelected(index)
        },
        label = item.title,
        hint = item.subtitle.takeIf(String::isNotBlank),
        placeholder = stringResource(R.string.settings_unknown),
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SettingsManagerProgress(label: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, Modifier.clearAndSetSemantics {}, color = AbkTheme.colors.inkMuted)
        AbkLinearProgress(null, label = label)
    }
}
