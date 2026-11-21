package com.abk.kernel.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.abk.kernel.R
import com.abk.kernel.data.model.AppProfileTemplateItem
import com.abk.kernel.ui.components.AbkScreenHorizontalPadding
import com.abk.kernel.ui.screens.flash.FlashAction
import io.github.xingguangcuican6666.abk.ui.components.AbkButtonStyle
import io.github.xingguangcuican6666.abk.ui.components.AbkDivider
import io.github.xingguangcuican6666.abk.ui.components.AbkLinearProgress
import io.github.xingguangcuican6666.abk.ui.components.AbkListItem
import io.github.xingguangcuican6666.abk.ui.components.AbkTextField
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcon
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcons
import io.github.xingguangcuican6666.abk.ui.theme.AbkTheme

@Composable
internal fun SettingsProfileTemplatesContent(
    topBarHeight: Dp,
    templates: List<AppProfileTemplateItem>,
    loading: Boolean,
    showRefreshLoading: Boolean,
    selectedId: String?,
    error: String?,
    saving: Boolean,
    hasEditor: Boolean,
    editingId: String,
    editingContent: String,
    onSelect: (String?) -> Unit,
    onNew: () -> Unit,
    onIdChange: (String) -> Unit,
    onContentChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AbkScreenHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(topBarHeight + 16.dp))
        Crossfade(targetState = showRefreshLoading, label = "template-refresh") { refreshing ->
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (refreshing) {
                    SettingsProfileTemplatesProgress()
                } else {
                    SettingsMainCard(stringResource(R.string.settings_local_templates)) {
                        when {
                            loading -> SettingsProfileTemplatesProgress()
                            templates.isEmpty() -> {
                                AbkIcon(AbkIcons.Modules, null)
                                Text(
                                    stringResource(R.string.settings_templates_empty),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    stringResource(R.string.settings_templates_empty_desc),
                                    color = AbkTheme.colors.inkMuted,
                                )
                            }
                        }
                        templates.forEachIndexed { index, template ->
                            if (index > 0) AbkDivider()
                            val editing = selectedId == template.id
                            AbkListItem(
                                title = template.id,
                                description =
                                    stringResource(
                                        if (editing) R.string.settings_template_editing
                                        else R.string.settings_app_profile_templates
                                    ),
                                modifier = Modifier.semantics { selected = editing },
                                leading = { AbkIcon(AbkIcons.Edit, null) },
                                trailing = {
                                    AbkIcon(if (editing) AbkIcons.Check else AbkIcons.Forward, null)
                                },
                                onClick = { onSelect(template.id) },
                            )
                        }
                        AbkDivider()
                        Text(
                            stringResource(R.string.settings_new_template_desc),
                            color = AbkTheme.colors.inkMuted,
                        )
                        FlashAction(
                            stringResource(R.string.settings_new_template),
                            AbkIcons.Plus,
                            onNew,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (error != null) {
                        SettingsMainCard(stringResource(R.string.settings_status)) {
                            Column(
                                Modifier.semantics { this.error(error) },
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                AbkIcon(AbkIcons.Warning, null, tint = AbkTheme.colors.danger.color)
                                Text(
                                    stringResource(R.string.settings_operation_incomplete),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(error, color = AbkTheme.colors.danger.color)
                            }
                            FlashAction(
                                stringResource(R.string.refresh),
                                AbkIcons.Refresh,
                                onRefresh,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    if (hasEditor) {
                        SettingsMainCard(stringResource(R.string.settings_edit_template)) {
                            AbkTextField(
                                value = editingId,
                                onValueChange = onIdChange,
                                label = stringResource(R.string.settings_template_name),
                                enabled = selectedId.isNullOrBlank(),
                                textStyle =
                                    MaterialTheme.typography.bodyLarge.copy(
                                        fontFamily = AbkTheme.monoFont
                                    ),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            AbkTextField(
                                value = editingContent,
                                onValueChange = onContentChange,
                                label = stringResource(R.string.settings_template_json),
                                singleLine = false,
                                minLines = 10,
                                textStyle =
                                    MaterialTheme.typography.bodyLarge.copy(
                                        fontFamily = AbkTheme.monoFont
                                    ),
                                inputModifier = Modifier.heightIn(min = 220.dp),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            if (saving)
                                SettingsProfileTemplatesProgress(
                                    stringResource(R.string.root_patch_processing)
                                )
                            if (!selectedId.isNullOrBlank()) {
                                FlashAction(
                                    stringResource(R.string.delete),
                                    AbkIcons.Trash,
                                    onDelete,
                                    enabled = !saving,
                                    style = AbkButtonStyle.Danger,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            FlashAction(
                                stringResource(R.string.save),
                                AbkIcons.Check,
                                onSave,
                                enabled = !saving && editingId.isNotBlank(),
                                style = AbkButtonStyle.Primary,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun SettingsProfileTemplatesProgress(
    label: String = stringResource(R.string.settings_templates_loading)
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, Modifier.clearAndSetSemantics {}, color = AbkTheme.colors.inkMuted)
        AbkLinearProgress(null, label = label)
    }
}
