package com.abk.kernel.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.abk.kernel.R
import com.abk.kernel.ui.screens.flash.FlashAction
import com.abk.kernel.utils.LocaleHelper
import io.github.xingguangcuican6666.abk.ui.components.AbkButton
import io.github.xingguangcuican6666.abk.ui.components.AbkButtonStyle
import io.github.xingguangcuican6666.abk.ui.components.AbkDialog
import io.github.xingguangcuican6666.abk.ui.components.AbkOption
import io.github.xingguangcuican6666.abk.ui.components.AbkRadioGroup
import io.github.xingguangcuican6666.abk.ui.components.AbkSlider
import io.github.xingguangcuican6666.abk.ui.components.AbkTextField
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcons
import io.github.xingguangcuican6666.abk.ui.theme.AbkTheme

@Composable
internal fun SettingsPreferenceChoices(
    title: String,
    options: List<AbkOption>,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        AbkRadioGroup(options, selectedId, onSelect, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
internal fun LanguageSettingsItem(current: String, onSelect: (String) -> Unit) {
    SettingsPreferenceChoices(
        title = stringResource(R.string.settings_language_desc),
        options =
            listOf(
                AbkOption(LocaleHelper.LANG_ZH, stringResource(R.string.settings_language_zh)),
                AbkOption(LocaleHelper.LANG_EN, stringResource(R.string.settings_language_en)),
                AbkOption(LocaleHelper.LANG_RU, stringResource(R.string.settings_language_ru)),
            ),
        selectedId = current,
        onSelect = onSelect,
    )
}

@Composable
internal fun SettingsDownloadThreadControl(count: Int, onCountChange: (Int) -> Unit) {
    AbkSlider(
        value = count.toFloat(),
        onValueChange = { onCountChange(it.toInt()) },
        label = stringResource(R.string.settings_download_threads),
        valueRange = 1f..64f,
        steps = 62,
        valueLabel = count.toString(),
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        stringResource(R.string.settings_download_threads_desc, count),
        style = MaterialTheme.typography.bodySmall,
        color = AbkTheme.colors.inkMuted,
    )
}

@Composable
internal fun SettingsClearDownloadsDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AbkDialog(
        title = stringResource(R.string.settings_clear_artifacts_title),
        dismissDescription = stringResource(R.string.close),
        onDismissRequest = onDismiss,
        actions = {
            AbkButton(onDismiss, style = AbkButtonStyle.Ghost) {
                Text(stringResource(android.R.string.cancel))
            }
            FlashAction(
                stringResource(R.string.settings_clear_artifacts_confirm),
                AbkIcons.Trash,
                onConfirm,
                style = AbkButtonStyle.Danger,
            )
        },
    ) {
        Text(stringResource(R.string.settings_clear_artifacts_message))
    }
}

@Composable
internal fun SettingsDownloadDirectoryActions(
    needsAllFilesAccess: Boolean,
    onChoose: () -> Unit,
    onReset: () -> Unit,
    onOpenAllFilesAccess: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlashAction(
            stringResource(R.string.settings_download_directory_choose),
            AbkIcons.Search,
            onChoose,
            modifier = Modifier.fillMaxWidth(),
        )
        FlashAction(
            stringResource(R.string.settings_download_directory_reset),
            AbkIcons.Refresh,
            onReset,
            modifier = Modifier.fillMaxWidth(),
            style = AbkButtonStyle.Ghost,
        )
        AnimatedVisibility(visible = needsAllFilesAccess) {
            FlashAction(
                stringResource(R.string.settings_download_directory_storage_permission),
                AbkIcons.Settings,
                onOpenAllFilesAccess,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
internal fun SettingsTextDraftField(
    value: String,
    onCommit: (String) -> Unit,
    label: String,
    description: String,
    placeholder: String,
    actions: (@Composable (commitDraft: (String) -> Unit) -> Unit)? = null,
) {
    val focusManager = LocalFocusManager.current
    var draft by rememberSaveable { mutableStateOf(value) }
    var isEditing by remember { mutableStateOf(false) }

    LaunchedEffect(value, isEditing) {
        if (
            shouldSyncSettingsTextDraft(
                isEditing = isEditing,
                persistedValue = value,
                draftValue = draft,
            )
        ) {
            draft = value
        }
    }

    fun commitDraft(newValue: String) {
        draft = newValue
        pendingSettingsTextCommit(persistedValue = value, draftValue = newValue)?.let(onCommit)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AbkTextField(
            value = draft,
            onValueChange = { draft = it },
            label = label,
            hint = description,
            placeholder = placeholder,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = AbkTheme.monoFont),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            inputModifier =
                Modifier.onFocusChanged { focusState ->
                    val wasEditing = isEditing
                    isEditing = focusState.isFocused
                    if (wasEditing && !focusState.isFocused) {
                        commitDraft(draft)
                    }
                },
        )
        actions?.invoke(::commitDraft)
    }
}

internal fun shouldSyncSettingsTextDraft(
    isEditing: Boolean,
    persistedValue: String,
    draftValue: String,
): Boolean = !isEditing && persistedValue != draftValue

internal fun pendingSettingsTextCommit(persistedValue: String, draftValue: String): String? =
    draftValue.takeIf { it != persistedValue }
