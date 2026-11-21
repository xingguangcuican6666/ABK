package com.abk.kernel.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.abk.kernel.R
import com.abk.kernel.data.model.KernelBuildConfig
import com.abk.kernel.data.model.SOURCE_ACCESS_GITHUB_PRIVATE
import com.abk.kernel.data.model.SOURCE_ACCESS_PUBLIC
import com.abk.kernel.ui.components.ExpressiveListItem
import io.github.xingguangcuican6666.abk.ui.components.AbkOption
import io.github.xingguangcuican6666.abk.ui.components.AbkSelect
import io.github.xingguangcuican6666.abk.ui.components.AbkSpinner
import io.github.xingguangcuican6666.abk.ui.components.AbkTextField
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcon
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcons
import io.github.xingguangcuican6666.abk.ui.theme.AbkTheme

@Composable
internal fun BuildSourceConfiguration(
    config: KernelBuildConfig,
    detecting: Boolean,
    detectionError: String?,
    secretConfigured: Boolean,
    onChange: (KernelBuildConfig) -> Unit,
    onRefFocusLost: () -> Unit,
    onOpenDefconfigs: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val codeStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = AbkTheme.monoFont)
    var refFocused by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        AbkTextField(
            value = config.sourceUrl,
            onValueChange = { onChange(config.copy(sourceUrl = it)) },
            label = stringResource(R.string.build_source_repo_url),
            placeholder = "https://github.com/LineageOS/android_kernel_xxx.git",
            textStyle = codeStyle,
            modifier = Modifier.fillMaxWidth(),
        )
        AbkTextField(
            value = config.sourceRef,
            onValueChange = { onChange(config.copy(sourceRef = it)) },
            label = stringResource(R.string.build_source_ref),
            placeholder = stringResource(R.string.build_source_ref_placeholder),
            textStyle = codeStyle,
            inputModifier =
                Modifier.onFocusChanged { focus ->
                    val wasFocused = refFocused
                    refFocused = focus.isFocused
                    if (wasFocused && !focus.isFocused) onRefFocusLost()
                },
            modifier = Modifier.fillMaxWidth(),
        )
        AbkSelect(
            options =
                listOf(
                    AbkOption(SOURCE_ACCESS_PUBLIC, stringResource(R.string.build_source_public)),
                    AbkOption(
                        SOURCE_ACCESS_GITHUB_PRIVATE,
                        stringResource(R.string.build_source_private),
                    ),
                ),
            selectedId = config.sourceAccessMode,
            onSelect = { onChange(config.copy(sourceAccessMode = it)) },
            label = stringResource(R.string.build_source_access),
            modifier = Modifier.fillMaxWidth(),
        )
        AbkTextField(
            value = config.sourceKernelVersionOverride,
            onValueChange = { onChange(config.copy(sourceKernelVersionOverride = it)) },
            label = stringResource(R.string.build_source_kernel_version),
            placeholder = "5.10.177",
            hint =
                stringResource(
                    if (detecting) R.string.build_source_detecting
                    else R.string.build_source_kernel_version_hint
                ),
            error = if (detecting) null else detectionError,
            textStyle = codeStyle,
            suffix =
                if (detecting) {
                    { AbkSpinner(label = stringResource(R.string.build_source_detecting)) }
                } else null,
            modifier = Modifier.fillMaxWidth(),
        )
        AbkTextField(
            value = config.osPatchLevel,
            onValueChange = { onChange(config.copy(osPatchLevel = it)) },
            label = stringResource(R.string.build_source_patch_month),
            placeholder = stringResource(R.string.build_source_patch_month_placeholder),
            textStyle = codeStyle,
            modifier = Modifier.fillMaxWidth(),
        )
        AbkTextField(
            value = config.sourceDeviceLabel,
            onValueChange = { onChange(config.copy(sourceDeviceLabel = it)) },
            label = stringResource(R.string.build_source_device_label),
            modifier = Modifier.fillMaxWidth(),
        )
        ExpressiveListItem(
            title = stringResource(R.string.build_source_defconfigs),
            subtitle =
                config.sourceDefconfigs.joinToString(" → ").ifBlank {
                    stringResource(R.string.build_source_defconfig_base_hint)
                },
            leadingIcon = AbkIcons.Controls,
            trailingContent = { AbkIcon(AbkIcons.Forward, null) },
            onClick = onOpenDefconfigs,
        )
        if (config.sourceAccessMode == SOURCE_ACCESS_GITHUB_PRIVATE) {
            Text(
                text =
                    stringResource(
                        if (secretConfigured) R.string.build_source_secret_exists
                        else R.string.build_source_secret_missing
                    ),
                color = AbkTheme.colors.inkMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
