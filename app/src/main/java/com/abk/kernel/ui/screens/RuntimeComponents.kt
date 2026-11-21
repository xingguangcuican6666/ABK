package com.abk.kernel.ui.screens

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abk.kernel.R
import com.abk.kernel.data.model.AbkRuntimeModule
import com.abk.kernel.data.model.AbkRuntimeStatus
import com.abk.kernel.ui.blur.blurredCardBackground
import com.abk.kernel.ui.blur.blurredCardSurfaceColor
import com.abk.kernel.ui.screens.flash.FlashAction
import com.abk.kernel.viewmodel.RuntimeModuleUpdateInfo
import com.abk.kernel.viewmodel.RuntimeModuleUpdateTarget
import io.github.xingguangcuican6666.abk.ui.components.*
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcon
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcons
import io.github.xingguangcuican6666.abk.ui.theme.AbkShapes
import io.github.xingguangcuican6666.abk.ui.theme.AbkTheme
import io.github.xingguangcuican6666.abk.ui.theme.AbkTone

private const val RUNTIME_MARKDOWN_URL_TAG = "runtime_markdown_url"
private val RUNTIME_MARKDOWN_ORDERED_LIST_REGEX = Regex("""^\d+\.\s+""")
private val RUNTIME_MARKDOWN_BARE_URL_REGEX = Regex("""https?://[^\s)]+""")

@Composable
internal fun RuntimeGroupCard(content: @Composable ColumnScope.() -> Unit) {
    AbkCard(
        modifier = Modifier.fillMaxWidth().blurredCardBackground(AbkShapes.Large),
        containerColor = blurredCardSurfaceColor(AbkTheme.colors.raised),
        content = content,
    )
}

@Composable
internal fun RuntimeStatusHeader(
    runtimeStatus: AbkRuntimeStatus?,
    hasNativeManagerPermission: Boolean,
    loading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onOpenManagerPatch: (() -> Unit)? = null,
) {
    val active = runtimeStatus != null && hasNativeManagerPermission
    val title =
        stringResource(
            if (active) R.string.runtime_manager_active else R.string.runtime_manager_inactive_title
        )
    RuntimeGroupCard {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            AbkIcon(
                if (active) AbkIcons.Check else AbkIcons.Warning,
                null,
                tint = if (active) AbkTheme.colors.success.color else AbkTheme.colors.warning.color,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AbkHeading(title)
                Text(
                    runtimeStatus?.let {
                        if (!hasNativeManagerPermission && !error.isNullOrBlank()) error
                        else {
                            val manager =
                                it.manager?.displayName?.takeIf(String::isNotBlank) ?: "Root"
                            "$manager · ABK ${it.abkVersion.ifBlank { "unknown" }} · ${stringResource(R.string.runtime_module_count, it.modules.size)}"
                        }
                    } ?: (error ?: stringResource(R.string.runtime_inactive_desc)),
                    color = AbkTheme.colors.inkMuted,
                )
            }
        }
        runtimeStatus?.let {
            AbkCluster {
                AbkBadge("schema ${it.schema}", dot = false)
                if (it.abkCommit.isNotBlank()) AbkBadge(it.abkCommit, dot = false)
            }
        }
        AbkCluster {
            if (runtimeStatus == null) {
                FlashAction(
                    stringResource(R.string.runtime_recheck),
                    AbkIcons.Refresh,
                    onRefresh,
                    enabled = !loading,
                    loading = loading,
                )
            }
            if (onOpenManagerPatch != null) {
                FlashAction(
                    stringResource(R.string.root_patch_title),
                    AbkIcons.Build,
                    onOpenManagerPatch,
                )
            }
        }
    }
}

@Composable
private fun RuntimeSectionHeading(title: String, description: String, icon: ImageVector) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        AbkIcon(icon, null)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            AbkHeading(title)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = AbkTheme.colors.inkMuted,
            )
        }
    }
}

@Composable
internal fun RuntimeManagerCard(runtimeStatus: AbkRuntimeStatus) {
    val manager = runtimeStatus.manager ?: return
    val backend = runtimeStatus.runtimeBackend
    RuntimeGroupCard {
        RuntimeSectionHeading(
            stringResource(R.string.runtime_manager_title),
            stringResource(R.string.runtime_manager_desc),
            AbkIcons.Cpu,
        )
        AbkDivider()
        RuntimeInfoRow(
            stringResource(R.string.runtime_type),
            manager.displayName.ifBlank { manager.variant },
        )
        RuntimeInfoRow(stringResource(R.string.runtime_version), manager.version)
        RuntimeInfoRow(
            stringResource(R.string.runtime_source),
            runtimeBackendLabel(manager.backend),
        )
        runtimeStatus.workMode.takeIf(String::isNotBlank)?.let {
            RuntimeInfoRow(stringResource(R.string.runtime_work_mode), runtimeWorkModeLabel(it))
        }
        if (backend != null && backend != manager) {
            AbkDivider()
            RuntimeInfoRow(
                stringResource(R.string.runtime_backend),
                backend.displayName.ifBlank { backend.variant },
            )
            RuntimeInfoRow(stringResource(R.string.runtime_backend_version), backend.version)
            RuntimeInfoRow(
                stringResource(R.string.runtime_compat_layer),
                runtimeBackendLabel(backend.backend),
            )
        }
        manager.diagnostics.plus(backend?.diagnostics.orEmpty()).distinct().forEach { message ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AbkIcon(AbkIcons.Warning, null, tint = AbkTheme.colors.danger.color, size = 18.dp)
                Text(message, Modifier.weight(1f), color = AbkTheme.colors.danger.color)
            }
        }
        val capabilities =
            manager.capabilities
                .plus(backend?.capabilities.orEmpty())
                .map { runtimeCapabilityLabel(it) }
                .ifEmpty { listOf("Root Shell") }
                .distinct()
        AbkCluster { capabilities.forEach { AbkBadge(it, dot = false) } }
    }
}

@Composable
internal fun RuntimeBuildParametersCard(
    runtimeStatus: AbkRuntimeStatus,
    systemKernelVersion: String,
) {
    val build = runtimeStatus.build
    RuntimeGroupCard {
        RuntimeSectionHeading(
            stringResource(R.string.runtime_build_params_title),
            stringResource(R.string.runtime_build_params_desc),
            AbkIcons.Controls,
        )
        AbkDivider()
        if (build == null) {
            Text(stringResource(R.string.runtime_old_schema), color = AbkTheme.colors.inkMuted)
        } else {
            RuntimeInfoRow("Android", build.androidVersion)
            RuntimeInfoRow(
                stringResource(R.string.runtime_target_kernel),
                listOf(build.kernelVersion, build.subLevel)
                    .filter(String::isNotBlank)
                    .joinToString("."),
            )
            RuntimeInfoRow(stringResource(R.string.build_kernel_version), systemKernelVersion)
            RuntimeInfoRow(stringResource(R.string.runtime_patch_level), build.osPatchLevel)
            RuntimeInfoRow(stringResource(R.string.runtime_revision), build.revision)
            RuntimeInfoRow(
                "KSU",
                listOf(build.kernelsuVariant, build.kernelsuBranch)
                    .filter(String::isNotBlank)
                    .joinToString(" / "),
            )
            RuntimeInfoRow(stringResource(R.string.runtime_build_time), build.buildTime)
            RuntimeInfoRow(
                stringResource(R.string.runtime_virtualization),
                build.virtualizationSupport,
            )
            RuntimeInfoRow(stringResource(R.string.runtime_zram_extra_algos), build.zramExtraAlgos)
            RuntimeInfoRow(
                "ABK",
                listOf(runtimeStatus.abkVersion, runtimeStatus.abkCommit)
                    .filter(String::isNotBlank)
                    .joinToString(" · "),
            )
            val features =
                build.features
                    .filterValues { it }
                    .keys
                    .map { runtimeFeatureLabel(it) }
                    .ifEmpty { listOf(stringResource(R.string.runtime_basic_config)) }
            AbkCluster { features.forEach { AbkBadge(it, dot = false) } }
        }
    }
}

@Composable
private fun RuntimeInfoRow(label: String, value: String) {
    if (value.isBlank()) return
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = AbkTheme.colors.inkMuted)
        SelectionContainer {
            Text(value, style = MaterialTheme.typography.bodyMedium, fontFamily = AbkTheme.monoFont)
        }
    }
}

@Composable
internal fun RuntimeErrorCard(message: String, onRefresh: () -> Unit) {
    RuntimeGroupCard {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AbkIcon(AbkIcons.Warning, null, tint = AbkTheme.colors.danger.color)
            Text(message, Modifier.weight(1f), color = AbkTheme.colors.danger.color)
        }
        FlashAction(stringResource(R.string.runtime_recheck), AbkIcons.Refresh, onRefresh)
    }
}

@Composable
internal fun RuntimeModuleSearchField(value: String, onValueChange: (String) -> Unit) {
    AbkTextField(
        value,
        onValueChange,
        label = stringResource(R.string.runtime_search_installed_modules),
        modifier = Modifier.fillMaxWidth(),
        prefix = { AbkIcon(AbkIcons.Search, null, size = 18.dp) },
    )
}

@Composable
internal fun InstalledRuntimeModuleCard(
    module: AbkRuntimeModule,
    updateCandidate: RuntimeModuleUpdateTarget?,
    actionInFlight: Boolean,
    onSetEnabled: (Boolean) -> Unit,
    onRequestUpdate: (RuntimeModuleUpdateTarget) -> Unit,
    onRequestUninstall: () -> Unit,
    onRunAction: () -> Unit,
    onOpenWebUi: () -> Unit,
) {
    val canUninstall = module.canUninstallRuntimeModule()
    RuntimeGroupCard {
        AbkHeading(module.displayName())
        if (module.version.isNotBlank()) {
            Text(
                buildString {
                    append(stringResource(R.string.runtime_module_version, module.version))
                    if (module.versionCode > 0) append(" (${module.versionCode})")
                },
                style = MaterialTheme.typography.bodySmall,
                fontFamily = AbkTheme.monoFont,
            )
        }
        if (module.author.isNotBlank())
            Text(
                stringResource(R.string.runtime_module_author, module.author),
                style = MaterialTheme.typography.bodySmall,
                color = AbkTheme.colors.inkMuted,
            )
        if (module.description.isNotBlank())
            Text(module.description, style = MaterialTheme.typography.bodyMedium)
        AbkCluster {
            AbkBadge(module.id.ifBlank { module.repoName() }, dot = false)
            if (module.metamodule) AbkBadge("META", dot = false)
            AbkBadge(runtimeModuleTypeLabel(module), dot = false)
            if (module.stage.isNotBlank()) AbkBadge(module.stage, dot = false)
            if (module.source.isNotBlank())
                AbkBadge(runtimeModuleSourceLabel(module.source), dot = false)
            AbkBadge(
                stringResource(
                    if (module.enabled) R.string.runtime_enabled else R.string.runtime_disabled
                ),
                tone = if (module.enabled) AbkTone.Success else AbkTone.Neutral,
            )
            if (module.update)
                AbkBadge(stringResource(R.string.runtime_pending_update), tone = AbkTone.Warning)
            if (updateCandidate != null)
                AbkBadge(stringResource(R.string.runtime_update_available), tone = AbkTone.Info)
            if (module.remove)
                AbkBadge(stringResource(R.string.runtime_pending_remove), tone = AbkTone.Warning)
            if (module.hasWebUi) AbkBadge("WebUI", dot = false)
            if (module.actionSupported || module.hasActionScript) AbkBadge("Action", dot = false)
            AbkBadge(
                stringResource(
                    when {
                        module.readonly -> R.string.runtime_readonly
                        module.controllable -> R.string.runtime_controllable
                        else -> R.string.runtime_metadata_only
                    }
                ),
                dot = false,
            )
        }
        if (module.repoUrl.isNotBlank())
            SelectionContainer {
                Text(
                    module.repoUrl,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = AbkTheme.monoFont,
                    color = AbkTheme.colors.inkMuted,
                )
            }
        if (module.controllable && !module.readonly) {
            AbkDivider()
            AbkSettingSwitch(
                title = module.displayName(),
                description =
                    stringResource(
                        if (module.enabled) R.string.runtime_enabled else R.string.runtime_disabled
                    ),
                checked = module.enabled,
                onCheckedChange = onSetEnabled,
                enabled = !actionInFlight && !module.update,
            )
        }
        AbkCluster {
            if (updateCandidate != null)
                FlashAction(
                    stringResource(R.string.runtime_update_module),
                    AbkIcons.Download,
                    { onRequestUpdate(updateCandidate) },
                    enabled = !actionInFlight && !module.remove,
                )
            if (module.hasWebUi)
                FlashAction(
                    stringResource(R.string.runtime_open_webui),
                    AbkIcons.Modules,
                    onOpenWebUi,
                    enabled = module.enabled && !module.remove && !module.update,
                )
            if (module.actionSupported)
                FlashAction(
                    stringResource(R.string.runtime_run_action),
                    AbkIcons.Terminal,
                    onRunAction,
                    enabled = module.enabled && !actionInFlight,
                )
            if (canUninstall)
                FlashAction(
                    stringResource(
                        if (module.remove) R.string.runtime_revoke else R.string.runtime_uninstall
                    ),
                    if (module.remove) AbkIcons.Refresh else AbkIcons.Trash,
                    onRequestUninstall,
                    enabled = !actionInFlight,
                    style = if (module.remove) AbkButtonStyle.Outline else AbkButtonStyle.Danger,
                )
        }
        if (actionInFlight) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AbkSpinner(label = stringResource(R.string.runtime_running))
                Text(stringResource(R.string.runtime_running))
            }
        }
    }
}

@Composable
internal fun RuntimeModuleFileAccessDialog(
    onDismiss: () -> Unit,
    onGrantAccess: () -> Unit,
    onUseSystemPicker: () -> Unit,
) {
    AbkDialog(
        title = stringResource(R.string.runtime_file_access_required),
        onDismissRequest = onDismiss,
        dismissDescription = stringResource(R.string.close),
        actions = {
            AbkButton(onUseSystemPicker, style = AbkButtonStyle.Outline) {
                Text(stringResource(R.string.runtime_system_picker))
            }
            AbkButton(onGrantAccess, style = AbkButtonStyle.Primary) {
                Text(stringResource(R.string.runtime_grant_permission))
            }
        },
    ) {
        AbkIcon(AbkIcons.Warning, null, tint = AbkTheme.colors.warning.color)
        Text(stringResource(R.string.runtime_file_access_vendor_picker_warning))
        Text(stringResource(R.string.runtime_file_access_desc), color = AbkTheme.colors.inkMuted)
    }
}

@Composable
internal fun RuntimeModuleInstallConfirmDialog(
    uri: Uri,
    displayName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    RuntimeConfirmationDialog(
        title = stringResource(R.string.runtime_confirm_flash_module),
        confirmLabel = stringResource(R.string.runtime_confirm_flash),
        icon = AbkIcons.Upload,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    ) {
        AbkHeading(displayName)
        SelectionContainer {
            Text(
                uri.toString(),
                fontFamily = AbkTheme.monoFont,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            stringResource(R.string.runtime_confirm_flash_module_desc),
            color = AbkTheme.colors.inkMuted,
        )
    }
}

@Composable
internal fun RuntimeModuleUpdateContentDialog(
    module: AbkRuntimeModule,
    updateInfo: RuntimeModuleUpdateInfo,
    changelog: String,
    downloadDirectoryPath: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    RuntimeConfirmationDialog(
        title = stringResource(R.string.runtime_update_module),
        confirmLabel = stringResource(R.string.runtime_update_module),
        icon = AbkIcons.Download,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
        wide = true,
    ) {
        AbkHeading(module.displayName())
        Text(
            stringResource(
                R.string.runtime_update_version_change,
                module.version.ifBlank { "unknown" },
                updateInfo.version.ifBlank { "unknown" },
            )
        )
        RuntimeModuleUpdateChangelog(changelog, rememberScrollState())
        Text(
            stringResource(R.string.runtime_confirm_update_module_desc, downloadDirectoryPath),
            color = AbkTheme.colors.inkMuted,
        )
    }
}

@Composable
private fun RuntimeModuleUpdateChangelog(changelog: String, scrollState: ScrollState) {
    val colors = AbkTheme.colors
    val uriHandler = LocalUriHandler.current
    val annotated =
        remember(changelog, colors.accent.color, colors.sunken) {
            buildRuntimeMarkdownAnnotatedString(
                changelog.ifBlank { "-" },
                colors.accent.color,
                colors.sunken,
            )
        }
    Surface(
        Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 280.dp),
        shape = AbkShapes.Medium,
        color = colors.sunken,
        border = BorderStroke(1.dp, colors.line),
    ) {
        Column(
            Modifier.verticalScroll(scrollState).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.runtime_update_changelog),
                style = MaterialTheme.typography.labelLarge,
            )
            ClickableText(
                text = annotated,
                style = MaterialTheme.typography.bodySmall.copy(color = colors.ink),
                onClick = { offset ->
                    annotated
                        .getStringAnnotations(RUNTIME_MARKDOWN_URL_TAG, offset, offset)
                        .firstOrNull()
                        ?.let { uriHandler.openUri(it.item) }
                },
            )
        }
    }
}

@Composable
internal fun RuntimeModuleUninstallConfirmDialog(
    module: AbkRuntimeModule,
    pending: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    RuntimeConfirmationDialog(
        title =
            stringResource(
                if (pending) R.string.runtime_confirm_uninstall_module
                else R.string.runtime_revoke_uninstall_module
            ),
        confirmLabel =
            stringResource(if (pending) R.string.runtime_uninstall else R.string.runtime_revoke),
        icon = if (pending) AbkIcons.Trash else AbkIcons.Refresh,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
        danger = pending,
    ) {
        AbkHeading(module.displayName())
        Text(module.id, fontFamily = AbkTheme.monoFont, style = MaterialTheme.typography.bodySmall)
        Text(
            stringResource(
                if (pending) R.string.runtime_confirm_uninstall_module_desc
                else R.string.runtime_revoke_uninstall_module_desc
            )
        )
    }
}

@Composable
private fun RuntimeConfirmationDialog(
    title: String,
    confirmLabel: String,
    icon: ImageVector,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    danger: Boolean = true,
    wide: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    AbkDialog(
        title,
        onDismiss,
        wide = wide,
        dismissDescription = stringResource(R.string.close),
        actions = {
            AbkButton(onDismiss, style = AbkButtonStyle.Outline) {
                Text(stringResource(R.string.cancel))
            }
            FlashAction(
                confirmLabel,
                icon,
                onConfirm,
                style = if (danger) AbkButtonStyle.Danger else AbkButtonStyle.Primary,
            )
        },
    ) {
        AbkIcon(
            icon,
            null,
            tint = if (danger) AbkTheme.colors.danger.color else AbkTheme.colors.accent.color,
        )
        content()
    }
}

@Composable
internal fun RuntimeModuleInstallDialog(
    running: Boolean,
    success: Boolean?,
    logLines: List<String>,
    onClose: () -> Unit,
    onReboot: () -> Unit,
) {
    RuntimeExecutionDialog(
        title =
            stringResource(
                if (running) R.string.runtime_installing_module else R.string.runtime_install_module
            ),
        running = running,
        success = success,
        logLines = logLines,
        onClose = onClose,
        onReboot = if (success == true && !running) onReboot else null,
    )
}

@Composable
internal fun RuntimeModuleActionDialog(
    title: String,
    running: Boolean,
    logLines: List<String>,
    onClose: () -> Unit,
) {
    // Action output remains dismissible while its independently hosted job runs, as before.
    RuntimeExecutionDialog(title, running, null, logLines, onClose, dismissWhileRunning = true)
}

@Composable
private fun RuntimeExecutionDialog(
    title: String,
    running: Boolean,
    success: Boolean?,
    logLines: List<String>,
    onClose: () -> Unit,
    onReboot: (() -> Unit)? = null,
    dismissWhileRunning: Boolean = false,
) {
    val canClose = !running || dismissWhileRunning
    val terminalScroll = rememberScrollState()
    LaunchedEffect(logLines.size) { terminalScroll.animateScrollTo(terminalScroll.maxValue) }
    AbkDialog(
        title = title,
        onDismissRequest = { if (canClose) onClose() },
        dismissible = canClose,
        dismissDescription = stringResource(R.string.close),
        wide = true,
        actions = {
            if (canClose)
                AbkButton(onClose, style = AbkButtonStyle.Outline) {
                    Text(stringResource(R.string.close))
                }
            if (!running && onReboot != null)
                FlashAction(
                    stringResource(R.string.runtime_reboot),
                    AbkIcons.Refresh,
                    onReboot,
                    style = AbkButtonStyle.Danger,
                )
        },
    ) {
        when {
            running -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AbkSpinner(label = stringResource(R.string.runtime_running))
                    Text(stringResource(R.string.runtime_running))
                }
            }
            success != null ->
                AbkBadge(
                    text =
                        stringResource(
                            if (success) R.string.status_success else R.string.status_failure
                        ),
                    tone = if (success) AbkTone.Success else AbkTone.Danger,
                )
        }
        Surface(
            Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 320.dp),
            shape = AbkShapes.Medium,
            color = AbkTheme.colors.sunken,
            border = BorderStroke(1.dp, AbkTheme.colors.line),
        ) {
            SelectionContainer {
                Text(
                    logLines
                        .ifEmpty { listOf(stringResource(R.string.runtime_waiting_output)) }
                        .joinToString("\n"),
                    modifier = Modifier.verticalScroll(terminalScroll).padding(12.dp),
                    fontFamily = AbkTheme.monoFont,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private fun buildRuntimeMarkdownAnnotatedString(
    markdown: String,
    linkColor: Color,
    codeBackground: Color,
): AnnotatedString {
    val builder = AnnotatedString.Builder()
    val lines = markdown.replace("\r\n", "\n").replace('\r', '\n').lines()
    var inCodeBlock = false

    lines.forEachIndexed { index, rawLine ->
        val trimmed = rawLine.trimStart()
        if (trimmed.startsWith("```")) {
            inCodeBlock = !inCodeBlock
            if (index != lines.lastIndex) builder.append('\n')
            return@forEachIndexed
        }

        val lineStart = builder.length
        if (inCodeBlock) {
            builder.append(rawLine.ifBlank { " " })
            if (builder.length > lineStart) {
                builder.addStyle(
                    SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground),
                    lineStart,
                    builder.length,
                )
            }
        } else {
            val headingLevel = trimmed.takeWhile { it == '#' }.length
            when {
                headingLevel in 1..6 && trimmed.getOrNull(headingLevel) == ' ' -> {
                    appendRuntimeMarkdownInline(
                        builder,
                        trimmed.drop(headingLevel + 1),
                        linkColor,
                        codeBackground,
                    )
                    if (builder.length > lineStart) {
                        builder.addStyle(
                            SpanStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize =
                                    when (headingLevel) {
                                        1 -> 20.sp
                                        2 -> 18.sp
                                        3 -> 16.sp
                                        else -> 14.sp
                                    },
                            ),
                            lineStart,
                            builder.length,
                        )
                    }
                }
                trimmed.startsWith(">") -> {
                    appendRuntimeMarkdownInline(
                        builder,
                        trimmed.removePrefix("> ").removePrefix(">"),
                        linkColor,
                        codeBackground,
                    )
                    if (builder.length > lineStart) {
                        builder.addStyle(
                            SpanStyle(fontStyle = FontStyle.Italic, color = Color.Gray),
                            lineStart,
                            builder.length,
                        )
                    }
                }
                trimmed.startsWith("- ") ||
                    trimmed.startsWith("* ") ||
                    trimmed.startsWith("+ ") -> {
                    builder.append("• ")
                    appendRuntimeMarkdownInline(builder, trimmed.drop(2), linkColor, codeBackground)
                }
                RUNTIME_MARKDOWN_ORDERED_LIST_REGEX.containsMatchIn(trimmed) -> {
                    appendRuntimeMarkdownInline(builder, trimmed, linkColor, codeBackground)
                }
                else -> {
                    appendRuntimeMarkdownInline(builder, rawLine, linkColor, codeBackground)
                }
            }
        }

        if (index != lines.lastIndex) builder.append('\n')
    }

    return builder.toAnnotatedString()
}

private fun appendRuntimeMarkdownInline(
    builder: AnnotatedString.Builder,
    text: String,
    linkColor: Color,
    codeBackground: Color,
) {
    var index = 0
    while (index < text.length) {
        val markdownLink = runtimeMarkdownLinkAt(text, index)
        if (markdownLink != null) {
            builder.pushStringAnnotation(RUNTIME_MARKDOWN_URL_TAG, markdownLink.second)
            builder.pushStyle(
                SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
            )
            builder.append(markdownLink.first)
            builder.pop()
            builder.pop()
            index = markdownLink.third
            continue
        }

        val bareUrl = RUNTIME_MARKDOWN_BARE_URL_REGEX.find(text, index)
        if (bareUrl != null && bareUrl.range.first == index) {
            val url = bareUrl.value
            builder.pushStringAnnotation(RUNTIME_MARKDOWN_URL_TAG, url)
            builder.pushStyle(
                SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
            )
            builder.append(url)
            builder.pop()
            builder.pop()
            index = bareUrl.range.last + 1
            continue
        }

        val bold = runtimeMarkdownDelimitedSegment(text, index, "**")
        if (bold != null) {
            builder.pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
            builder.append(bold.first)
            builder.pop()
            index = bold.second
            continue
        }

        val code = runtimeMarkdownDelimitedSegment(text, index, "`")
        if (code != null) {
            builder.pushStyle(
                SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground)
            )
            builder.append(code.first)
            builder.pop()
            index = code.second
            continue
        }

        val italicStar = runtimeMarkdownDelimitedSegment(text, index, "*")
        if (italicStar != null) {
            builder.pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
            builder.append(italicStar.first)
            builder.pop()
            index = italicStar.second
            continue
        }

        val italicUnderline = runtimeMarkdownDelimitedSegment(text, index, "_")
        if (italicUnderline != null) {
            builder.pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
            builder.append(italicUnderline.first)
            builder.pop()
            index = italicUnderline.second
            continue
        }

        builder.append(text[index])
        index += 1
    }
}

private fun runtimeMarkdownLinkAt(text: String, index: Int): Triple<String, String, Int>? {
    if (text.getOrNull(index) != '[') return null
    val labelEnd = text.indexOf(']', startIndex = index + 1)
    if (labelEnd <= index + 1 || text.getOrNull(labelEnd + 1) != '(') return null
    val urlEnd = text.indexOf(')', startIndex = labelEnd + 2)
    if (urlEnd <= labelEnd + 2) return null
    val label = text.substring(index + 1, labelEnd)
    val url = text.substring(labelEnd + 2, urlEnd)
    if (!url.startsWith("https://") && !url.startsWith("http://")) return null
    return Triple(label, url, urlEnd + 1)
}

private fun runtimeMarkdownDelimitedSegment(
    text: String,
    index: Int,
    delimiter: String,
): Pair<String, Int>? {
    if (!text.startsWith(delimiter, startIndex = index)) return null
    val end = text.indexOf(delimiter, startIndex = index + delimiter.length)
    if (end <= index + delimiter.length) return null
    return text.substring(index + delimiter.length, end) to (end + delimiter.length)
}

@Composable
private fun runtimeFeatureLabel(key: String): String =
    when (key) {
        "use_zram" -> "ZRAM"
        "use_bbg" -> "BBG"
        "use_ddk" -> "DDK"
        "use_ntsync" -> "NTsync"
        "use_networking" -> stringResource(R.string.runtime_feature_networking)
        "use_kpm" -> "KPM"
        "use_rekernel" -> "Re-Kernel"
        "enable_susfs" -> "SUSFS"
        "supp_op" -> "SukiSU SUS_SU"
        "zram_full_algo" -> stringResource(R.string.runtime_feature_zram_full_algo)
        "cancel_susfs" -> stringResource(R.string.runtime_feature_cancel_susfs)
        else -> key
    }

@Composable
private fun runtimeCapabilityLabel(key: String): String =
    if (key == internalRuntimeControlCapability()) {
        stringResource(R.string.runtime_cap_abk_control)
    } else {
        when (key) {
            "root_shell" -> "Root Shell"
            "native_manager" -> stringResource(R.string.runtime_cap_native_manager)
            "root_policy" -> stringResource(R.string.runtime_cap_root_policy)
            "superuser_profiles" -> stringResource(R.string.runtime_cap_superuser_profiles)
            "lkm" -> "LKM"
            "late_load" -> "Late Load"
            "safe_mode" -> stringResource(R.string.runtime_cap_safe_mode)
            "modules" -> stringResource(R.string.runtime_cap_modules)
            "module_control" -> stringResource(R.string.runtime_cap_module_control)
            "susfs" -> "SUSFS"
            "kpm" -> "KPM"
            "features" -> stringResource(R.string.runtime_cap_features)
            else -> key
        }
    }

@Composable
private fun runtimeBackendLabel(backend: String): String =
    when (backend) {
        "native" -> stringResource(R.string.runtime_backend_native)
        "ksud" -> stringResource(R.string.runtime_backend_ksud)
        "su" -> stringResource(R.string.runtime_backend_su)
        "kernel" -> stringResource(R.string.runtime_backend_kernel)
        else -> backend
    }

private fun runtimeWorkModeLabel(workMode: String): String =
    when (workMode) {
        "lkm" -> "LKM"
        "built-in" -> "Built-in"
        else -> workMode
    }

private fun runtimeModuleSourceLabel(source: String): String {
    val labels =
        source
            .split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map {
                when (it) {
                    "ksud" -> "KSU"
                    "abk" -> "ABK"
                    else -> it
                }
            }
    return labels.joinToString("+")
}

@Composable
private fun runtimeModuleTypeLabel(module: AbkRuntimeModule): String =
    when (module.normalizedType()) {
        "standard" -> stringResource(R.string.runtime_module_type_standard)
        "builtin" -> stringResource(R.string.runtime_module_type_builtin)
        "kpm" -> "KPM"
        else -> module.normalizedType()
    }

private fun internalRuntimeControlCapability(): String =
    intArrayOf(97, 98, 107, 95, 99, 111, 110, 116, 114, 111, 108)
        .map { it.toChar() }
        .joinToString("")
