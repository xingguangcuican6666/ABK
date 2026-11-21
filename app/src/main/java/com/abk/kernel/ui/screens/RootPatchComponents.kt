package com.abk.kernel.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.abk.kernel.R
import com.abk.kernel.ui.blur.blurredCardBackground
import com.abk.kernel.ui.blur.blurredCardSurfaceColor
import com.abk.kernel.ui.screens.flash.FlashAction
import com.abk.kernel.utils.SignedBundleManifest
import io.github.xingguangcuican6666.abk.ui.components.AbkBadge
import io.github.xingguangcuican6666.abk.ui.components.AbkButton
import io.github.xingguangcuican6666.abk.ui.components.AbkButtonStyle
import io.github.xingguangcuican6666.abk.ui.components.AbkCard
import io.github.xingguangcuican6666.abk.ui.components.AbkCheckbox
import io.github.xingguangcuican6666.abk.ui.components.AbkCluster
import io.github.xingguangcuican6666.abk.ui.components.AbkDialog
import io.github.xingguangcuican6666.abk.ui.components.AbkDivider
import io.github.xingguangcuican6666.abk.ui.components.AbkHeading
import io.github.xingguangcuican6666.abk.ui.components.AbkRadioOption
import io.github.xingguangcuican6666.abk.ui.components.AbkSpinner
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcon
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcons
import io.github.xingguangcuican6666.abk.ui.theme.AbkShapes
import io.github.xingguangcuican6666.abk.ui.theme.AbkTheme
import io.github.xingguangcuican6666.abk.ui.theme.AbkTone

@Composable
internal fun RootPatchGroupCard(content: @Composable ColumnScope.() -> Unit) {
    AbkCard(
        modifier = Modifier.fillMaxWidth().blurredCardBackground(AbkShapes.Large),
        containerColor = blurredCardSurfaceColor(AbkTheme.colors.raised),
        content = content,
    )
}

@Composable
internal fun RootPatchModeOptions(
    selected: LkmPatchInstallMode?,
    rootGranted: Boolean,
    running: Boolean,
    selectedBootName: String,
    selectedAnyKernelName: String,
    onSelect: (LkmPatchInstallMode) -> Unit,
) {
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        AbkRadioOption(
            label = stringResource(R.string.root_patch_select_file),
            description =
                selectedBootName.ifBlank { stringResource(R.string.root_patch_select_file_desc) },
            selected = selected == LkmPatchInstallMode.SelectFile,
            enabled = !running,
            onClick = { onSelect(LkmPatchInstallMode.SelectFile) },
        )
        if (rootGranted) {
            AbkDivider()
            AbkRadioOption(
                label = stringResource(R.string.root_patch_direct_install),
                description = stringResource(R.string.root_patch_direct_install_desc),
                selected = selected == LkmPatchInstallMode.DirectInstall,
                enabled = !running,
                onClick = { onSelect(LkmPatchInstallMode.DirectInstall) },
            )
            AbkDivider()
            AbkRadioOption(
                label = stringResource(R.string.root_patch_ota_install),
                description = stringResource(R.string.root_patch_ota_install_desc),
                selected = selected == LkmPatchInstallMode.OtaInstall,
                enabled = !running,
                onClick = { onSelect(LkmPatchInstallMode.OtaInstall) },
            )
            AbkDivider()
            AbkRadioOption(
                label = stringResource(R.string.root_patch_anykernel),
                description =
                    selectedAnyKernelName.ifBlank {
                        stringResource(R.string.root_patch_anykernel_desc)
                    },
                selected = selected == LkmPatchInstallMode.AnyKernel3,
                enabled = !running,
                onClick = { onSelect(LkmPatchInstallMode.AnyKernel3) },
            )
        }
    }
}

@Composable
internal fun RootPatchAdvancedOptions(
    expanded: Boolean,
    running: Boolean,
    allowShell: Boolean,
    enableAdb: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onAllowShellChange: (Boolean) -> Unit,
    onEnableAdbChange: (Boolean) -> Unit,
) {
    val state =
        stringResource(
            if (expanded) R.string.root_patch_advanced_expanded
            else R.string.root_patch_advanced_collapsed
        )
    AbkButton(
        onClick = { onExpandedChange(!expanded) },
        modifier = Modifier.fillMaxWidth().semantics { stateDescription = state },
        style = AbkButtonStyle.Outline,
    ) {
        AbkIcon(AbkIcons.Controls, null, size = 18.dp)
        Text(
            stringResource(R.string.root_patch_advanced_options),
            Modifier.weight(1f).padding(horizontal = 8.dp),
        )
        AbkIcon(AbkIcons.Forward, null, Modifier.rotate(if (expanded) -90f else 90f), size = 18.dp)
    }
    AnimatedVisibility(
        visible = expanded,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.root_patch_advanced_desc),
                style = MaterialTheme.typography.bodySmall,
                color = AbkTheme.colors.inkMuted,
            )
            AbkDivider()
            AbkCheckbox(
                state = ToggleableState(allowShell),
                onClick = { onAllowShellChange(!allowShell) },
                label = stringResource(R.string.root_patch_allow_shell_root),
                description = stringResource(R.string.root_patch_allow_shell_root_desc),
                enabled = !running,
            )
            AbkDivider()
            AbkCheckbox(
                state = ToggleableState(enableAdb),
                onClick = { onEnableAdbChange(!enableAdb) },
                label = stringResource(R.string.root_patch_enable_adb_debug),
                description = stringResource(R.string.root_patch_enable_adb_debug_desc),
                enabled = !running,
            )
        }
    }
}

@Composable
internal fun RootPatchManifestDialog(
    manifest: SignedBundleManifest,
    confirmFlash: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val source = manifest.kernelSource
    val feature = manifest.featureStatus
    AbkDialog(
        title =
            stringResource(
                if (confirmFlash) R.string.flash_custom_source_review_before_flash
                else R.string.flash_custom_source_notice_title
            ),
        onDismissRequest = onDismiss,
        dismissDescription = stringResource(R.string.close),
        wide = true,
        actions = {
            if (confirmFlash) {
                AbkButton(onClick = onDismiss, style = AbkButtonStyle.Outline) {
                    Text(stringResource(R.string.cancel))
                }
            }
            AbkButton(
                onClick = onConfirm,
                style = if (confirmFlash) AbkButtonStyle.Danger else AbkButtonStyle.Primary,
            ) {
                Text(stringResource(if (confirmFlash) R.string.flash_confirm else R.string.confirm))
            }
        },
    ) {
        AbkIcon(AbkIcons.Warning, null, tint = AbkTheme.colors.warning.color)
        SelectionContainer {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                source?.url?.let {
                    Text(
                        stringResource(R.string.flash_custom_source_url, it),
                        fontFamily = AbkTheme.monoFont,
                    )
                }
                if (source?.access == "github_private") {
                    Text(
                        stringResource(R.string.flash_custom_source_private_warning),
                        color = AbkTheme.colors.danger.color,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                source?.access?.let {
                    Text(stringResource(R.string.flash_custom_source_access, it))
                }
                source?.requestedRef?.let {
                    Text(
                        stringResource(R.string.flash_custom_source_ref, it),
                        fontFamily = AbkTheme.monoFont,
                    )
                }
                source?.resolvedCommit?.let {
                    Text(
                        stringResource(R.string.flash_custom_source_commit, it),
                        fontFamily = AbkTheme.monoFont,
                    )
                }
                source?.kernelVersion?.let {
                    Text(
                        stringResource(
                            R.string.flash_custom_source_kernel,
                            source.androidVersion.orEmpty(),
                            it,
                        )
                    )
                }
                source?.toolchainPatchLevel?.let {
                    Text(stringResource(R.string.flash_custom_source_toolchain, it))
                }
                source
                    ?.deviceLabel
                    ?.takeIf { it.isNotBlank() }
                    ?.let { Text(stringResource(R.string.flash_custom_source_device, it)) }
                source
                    ?.defconfigs
                    ?.takeIf { it.isNotEmpty() }
                    ?.let {
                        Text(
                            stringResource(
                                R.string.flash_custom_source_defconfigs,
                                it.joinToString(" -> "),
                            ),
                            fontFamily = AbkTheme.monoFont,
                        )
                    }
                feature
                    ?.requested
                    ?.takeIf { it.isNotEmpty() }
                    ?.let {
                        Text(
                            stringResource(
                                R.string.flash_custom_source_requested,
                                formatRootPatchFeatures(it),
                            )
                        )
                    }
                feature
                    ?.effective
                    ?.takeIf { it.isNotEmpty() }
                    ?.let {
                        Text(
                            stringResource(
                                R.string.flash_custom_source_effective,
                                formatRootPatchFeatures(it),
                            )
                        )
                    }
                feature
                    ?.skipped
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { skipped ->
                        Text(
                            stringResource(R.string.flash_custom_source_skipped_title),
                            fontWeight = FontWeight.SemiBold,
                            color = AbkTheme.colors.danger.color,
                        )
                        skipped.forEach {
                            Text("• ${it.id}: ${it.message}", color = AbkTheme.colors.danger.color)
                        }
                    }
                Text(
                    stringResource(R.string.flash_custom_source_old_client_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = AbkTheme.colors.inkMuted,
                )
            }
        }
    }
}

@Composable
internal fun RootPatchedImageCard(
    path: String,
    canFlash: Boolean,
    onCopy: () -> Unit,
    onFlash: () -> Unit,
) {
    RootPatchGroupCard {
        AbkHeading(stringResource(R.string.root_patch_result))
        SelectionContainer {
            Text(path, style = MaterialTheme.typography.bodySmall, fontFamily = AbkTheme.monoFont)
        }
        AbkCluster(Modifier.fillMaxWidth()) {
            FlashAction(stringResource(R.string.root_patch_copy_path), AbkIcons.Copy, onCopy)
            if (canFlash)
                FlashAction(
                    stringResource(R.string.root_patch_flash),
                    AbkIcons.Cpu,
                    onFlash,
                    style = AbkButtonStyle.Danger,
                )
        }
    }
}

@Composable
internal fun RootPatchLogCard(
    running: Boolean,
    success: Boolean?,
    action: String,
    lines: List<String>,
    canReboot: Boolean,
    onReboot: () -> Unit,
) {
    val scroll = rememberScrollState()
    LaunchedEffect(lines.size) { scroll.animateScrollTo(scroll.maxValue) }
    RootPatchGroupCard {
        AbkHeading(action.ifBlank { stringResource(R.string.root_patch_logs) })
        when {
            running -> {
                val label = stringResource(R.string.root_patch_processing)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AbkSpinner(label = label)
                    Text(label)
                }
            }
            success == true ->
                AbkBadge(stringResource(R.string.status_success), tone = AbkTone.Success)
            success == false ->
                AbkBadge(stringResource(R.string.status_failure), tone = AbkTone.Danger)
        }
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(min = 84.dp, max = 280.dp),
            shape = AbkShapes.Medium,
            color = AbkTheme.colors.sunken,
            contentColor = AbkTheme.colors.ink,
        ) {
            Column(
                Modifier.verticalScroll(scroll).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                lines
                    .ifEmpty { listOf(stringResource(R.string.root_patch_waiting_operation)) }
                    .forEach { line ->
                        Text(
                            line,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = AbkTheme.monoFont,
                            color =
                                if (line.startsWith('$')) AbkTheme.colors.accent.color
                                else AbkTheme.colors.ink,
                        )
                    }
            }
        }
        if (canReboot && !running)
            FlashAction(
                stringResource(R.string.root_patch_reboot),
                AbkIcons.Refresh,
                onReboot,
                style = AbkButtonStyle.Danger,
            )
    }
}

private fun formatRootPatchFeatures(values: Map<String, Any?>): String =
    values.entries
        .sortedBy { it.key }
        .joinToString(", ") { (key, value) -> "$key=${value ?: "null"}" }
