package com.abk.kernel.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.abk.kernel.R
import com.abk.kernel.data.model.KernelBuildConfig
import com.abk.kernel.data.model.KernelPatchModules
import com.abk.kernel.ui.components.AbkScreenHorizontalPadding
import com.abk.kernel.ui.components.ExpressiveSectionCard
import io.github.xingguangcuican6666.abk.ui.components.*
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcons
import io.github.xingguangcuican6666.abk.ui.theme.AbkTheme

@Composable
internal fun BuildPatchModulesPage(
    config: KernelBuildConfig,
    onConfigChange: (KernelBuildConfig) -> Unit,
    topBarHeight: Dp,
    bottomPadding: Dp,
) {
    var editingIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var repository by rememberSaveable { mutableStateOf("") }
    var showError by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())
        .padding(horizontal = AbkScreenHorizontalPadding), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(topBarHeight + 16.dp))
        ExpressiveSectionCard(title = stringResource(R.string.build_patch_title),
            subtitle = stringResource(R.string.build_patch_description), icon = AbkIcons.Modules) {
            KernelPatchModules.validationError(config)?.let { Text(stringResource(it), color = AbkTheme.colors.danger.color) }
            AbkTextField(config.ksuhookSdkRef, { onConfigChange(config.copy(ksuhookSdkRef = it)) },
                label = stringResource(R.string.build_patch_sdk), hint = stringResource(R.string.build_patch_sdk_hint),
                error = if (config.ksuhookSdkRef.isNotBlank() && !Regex("[0-9a-fA-F]{40}").matches(config.ksuhookSdkRef.trim()))
                    stringResource(R.string.build_patch_invalid_sdk) else null)
        }
        ExpressiveSectionCard(title = stringResource(R.string.build_patch_count, config.ksuhookModules.size)) {
            if (config.ksuhookModules.isEmpty()) Text(stringResource(R.string.build_patch_empty), color = AbkTheme.colors.inkMuted)
            config.ksuhookModules.forEachIndexed { index, spec ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    AbkListItem(title = spec, modifier = Modifier.weight(1f), trailing = {
                        AbkIconButton(AbkIcons.Edit, stringResource(R.string.build_patch_edit) + ": " + spec, {
                            editingIndex = index; repository = spec; showError = false; editorOpen = true
                        })
                    })
                    AbkIconButton(AbkIcons.Trash, stringResource(R.string.build_patch_remove, spec), {
                        onConfigChange(config.copy(ksuhookModules = config.ksuhookModules.filterIndexed { i, _ -> i != index }))
                    })
                }
                AbkDivider()
            }
            AbkButton(onClick = { editingIndex = null; repository = ""; showError = false; editorOpen = true },
                modifier = Modifier.fillMaxWidth(), enabled = config.ksuhookModules.size < KernelPatchModules.MAX_MODULES) {
                Text(stringResource(R.string.build_patch_add))
            }
        }
        Spacer(Modifier.height(bottomPadding + 24.dp))
    }
    if (editorOpen) AbkDialog(
        title = stringResource(if (editingIndex == null) R.string.build_patch_add else R.string.build_patch_edit),
        onDismissRequest = { editorOpen = false }, dismissDescription = stringResource(R.string.close),
        actions = {
            AbkButton(onClick = { editorOpen = false }, style = AbkButtonStyle.Ghost) { Text(stringResource(R.string.cancel)) }
            AbkButton(onClick = {
                val spec = repository.trim()
                if (!KernelPatchModules.isValidSpec(spec)) showError = true
                else {
                    val modules = config.ksuhookModules.toMutableList()
                    val index = editingIndex
                    if (index != null && index in modules.indices) modules[index] = spec else modules.add(spec)
                    onConfigChange(config.copy(ksuhookModules = modules.distinct()))
                    editorOpen = false
                }
            }, style = AbkButtonStyle.Primary) { Text(stringResource(R.string.build_save)) }
        }
    ) {
        AbkTextField(repository, { repository = it; showError = false }, label = stringResource(R.string.build_patch_repo),
            hint = stringResource(R.string.build_patch_repo_hint),
            error = if (showError) stringResource(R.string.build_patch_invalid_repo) else null)
    }
}
