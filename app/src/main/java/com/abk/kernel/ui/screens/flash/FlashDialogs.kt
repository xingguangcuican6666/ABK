@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.abk.kernel.ui.screens.flash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.abk.kernel.R
import com.abk.kernel.data.model.BuildParameterSummary
import com.abk.kernel.data.model.PrebuiltGkiRelease
import io.github.xingguangcuican6666.abk.ui.components.AbkButton
import io.github.xingguangcuican6666.abk.ui.components.AbkCheckbox
import io.github.xingguangcuican6666.abk.ui.components.AbkDialog
import io.github.xingguangcuican6666.abk.ui.components.AbkPanel
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcon
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcons
import io.github.xingguangcuican6666.abk.ui.theme.AbkTheme

@Composable
internal fun BuildParameterSummaryDialog(
    group: WorkflowArtifactGroup,
    summary: BuildParameterSummary?,
    loading: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
) {
    AbkDialog(
        title = stringResource(R.string.flash_parameter_details),
        onDismissRequest = onDismiss,
        dismissDescription = stringResource(R.string.close),
        wide = true,
        actions = {
            if (error != null && !loading) {
                FlashAction(stringResource(R.string.retry), AbkIcons.Refresh, onRetry)
            }
            AbkButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
    ) {
        ParameterSection(stringResource(R.string.flash_workflow)) {
            ParameterRow(
                stringResource(R.string.flash_number),
                if (group.runNumber > 0) "#${group.runNumber}" else "#${group.runId}",
            )
            ParameterRow(stringResource(R.string.flash_title_label), group.runTitle)
            ParameterRow(
                stringResource(R.string.flash_artifacts),
                stringResource(R.string.flash_artifact_counts, group.remote.size, group.local.size),
            )
        }
        when {
            summary != null -> ParameterSummarySections(summary)
            loading -> LoadingRow(stringResource(R.string.flash_reading_build_summary))
            error != null ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AbkIcon(AbkIcons.AlertMark, null, tint = AbkTheme.colors.danger.color)
                    Text(error, Modifier.weight(1f), color = AbkTheme.colors.danger.color)
                }
            else ->
                Text(
                    stringResource(R.string.flash_no_parameter_details),
                    color = AbkTheme.colors.inkMuted,
                )
        }
    }
}

@Composable
internal fun PrebuiltParameterSummaryDialog(
    release: PrebuiltGkiRelease,
    summary: BuildParameterSummary?,
    onDismiss: () -> Unit,
) {
    AbkDialog(
        title = stringResource(R.string.flash_parameter_details),
        onDismissRequest = onDismiss,
        dismissDescription = stringResource(R.string.close),
        wide = true,
        actions = { AbkButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    ) {
        ParameterSection("Release") {
            ParameterRow(stringResource(R.string.flash_name), release.name)
            ParameterRow("Tag", release.tagName)
            ParameterRow(
                stringResource(R.string.flash_published_at),
                releaseDateLabel(release.publishedAt, stringResource(R.string.flash_unknown_date)),
            )
            ParameterRow(
                stringResource(R.string.flash_assets),
                if (release.assetCount > 0) {
                    stringResource(R.string.flash_asset_count, release.assetCount)
                } else {
                    stringResource(R.string.flash_unknown)
                },
            )
        }
        if (summary != null) {
            ParameterSummarySections(summary)
        } else {
            Text(stringResource(R.string.flash_release_no_matrix), color = AbkTheme.colors.inkMuted)
        }
    }
}

@Composable
internal fun ParameterSummarySections(summary: BuildParameterSummary) {
    ParameterSection(stringResource(R.string.flash_version_params)) {
        ParameterRow(stringResource(R.string.build_android_version), summary.androidVersion)
        ParameterRow(stringResource(R.string.build_kernel_version), summary.kernelVersion)
        ParameterRow(stringResource(R.string.build_sub_level), summary.subLevel)
        ParameterRow(stringResource(R.string.runtime_patch_level), summary.osPatchLevel)
        ParameterRow(stringResource(R.string.flash_build_time), summary.buildTime)
    }
    ParameterSection("KernelSU") {
        ParameterRow(stringResource(R.string.flash_ksu_variant), summary.ksuVariant)
        ParameterRow(stringResource(R.string.flash_ksu_branch), summary.ksuBranch)
        ParameterRow(stringResource(R.string.flash_susfs_status), summary.susfsEnabled)
    }
    ParameterSection(stringResource(R.string.flash_patches_features)) {
        ParameterRow(stringResource(R.string.flash_zram), summary.zramEnabled)
        ParameterRow(stringResource(R.string.flash_zram_full_algo), summary.zramFullAlgo)
        ParameterRow(stringResource(R.string.flash_zram_extra_algos), summary.zramExtraAlgos)
        ParameterRow(stringResource(R.string.flash_bbg_patch), summary.bbgEnabled)
        ParameterRow("DDK LSM", summary.ddkLsm)
        ParameterRow(stringResource(R.string.flash_ntsync_patch), summary.ntsyncEnabled)
        ParameterRow(stringResource(R.string.runtime_feature_networking), summary.networkingEnabled)
        ParameterRow(stringResource(R.string.flash_kpm_feature), summary.kpmEnabled)
        ParameterRow(stringResource(R.string.flash_kpm_password), summary.kpmPassword)
        ParameterRow("Re-Kernel", summary.reKernelEnabled)
        ParameterRow(stringResource(R.string.runtime_virtualization), summary.virtualizationSupport)
        ParameterRow(stringResource(R.string.flash_custom_injection), summary.customInjection)
        ParameterRow("Stock Config", summary.stockConfig)
    }
    val extraRows = summary.extraRows.orEmpty()
    if (extraRows.isNotEmpty()) {
        ParameterSection(stringResource(R.string.flash_extra_info)) {
            extraRows.forEach { (label, value) -> ParameterRow(label, value) }
        }
    }
}

@Composable
internal fun ParameterSection(title: String, content: @Composable () -> Unit) {
    AbkPanel(Modifier.fillMaxWidth()) {
        Text(
            title,
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleSmall,
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
internal fun ParameterRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = AbkTheme.colors.inkMuted)
        Text(
            text =
                parameterDisplayValue(
                    value = value,
                    unknown = stringResource(R.string.flash_unknown),
                    enabled = stringResource(R.string.build_feature_enabled),
                    disabled = stringResource(R.string.build_virtualization_off),
                    none = stringResource(R.string.flash_value_none),
                    defaultValue = stringResource(R.string.flash_value_default),
                    set = stringResource(R.string.flash_value_set),
                ),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = AbkTheme.monoFont,
        )
    }
}

internal fun parameterDisplayValue(
    value: String,
    unknown: String,
    enabled: String,
    disabled: String,
    none: String,
    defaultValue: String,
    set: String,
): String {
    val trimmed = value.trim()
    return when (trimmed.lowercase()) {
        "" -> unknown
        "true" -> enabled
        "false" -> disabled
        "none" -> none
        "default" -> defaultValue
        "set" -> set
        else -> trimmed
    }
}

internal fun parsePrebuiltGkiParameterSummary(release: PrebuiltGkiRelease): BuildParameterSummary? {
    val values = linkedMapOf<String, String>()
    val extraRows = linkedMapOf<String, String>()
    parseReleaseBodyParameterRows(release.body).forEach { (label, rawValue) ->
        val value = rawValue.trim()
        val key = normalizeReleaseParameterLabel(label)
        if (key != null) {
            values[key] = sanitizeReleaseParameterValue(key, value)
        } else if (isReleaseExtraParameterLabel(label)) {
            extraRows[label.trim()] = value.ifBlank { "none" }
        }
    }
    if (values.isEmpty() && extraRows.isEmpty()) return null

    val inferredVersion = inferPrebuiltVersionFields(release)
    return BuildParameterSummary(
        runId = -release.id,
        runNumber = 0,
        runTitle = release.name,
        runCreatedAt = release.publishedAt,
        runHtmlUrl = release.htmlUrl,
        androidVersion =
            values["androidVersion"].orEmpty().ifBlank { inferredVersion.androidVersion },
        kernelVersion = values["kernelVersion"].orEmpty().ifBlank { inferredVersion.kernelVersion },
        subLevel = values["subLevel"].orEmpty().ifBlank { inferredVersion.subLevel },
        osPatchLevel = values["osPatchLevel"].orEmpty(),
        ksuVariant = values["ksuVariant"].orEmpty(),
        ksuBranch = values["ksuBranch"].orEmpty(),
        buildTime = values["buildTime"].orEmpty(),
        susfsEnabled = values["susfsEnabled"].orEmpty(),
        zramEnabled = values["zramEnabled"].orEmpty(),
        zramFullAlgo = values["zramFullAlgo"].orEmpty(),
        zramExtraAlgos = values["zramExtraAlgos"].orEmpty(),
        bbgEnabled = values["bbgEnabled"].orEmpty(),
        ddkLsm = values["ddkLsm"].orEmpty(),
        ntsyncEnabled = values["ntsyncEnabled"].orEmpty(),
        networkingEnabled = values["networkingEnabled"].orEmpty(),
        kpmEnabled = values["kpmEnabled"].orEmpty(),
        kpmPassword = values["kpmPassword"].orEmpty(),
        reKernelEnabled = values["reKernelEnabled"].orEmpty(),
        virtualizationSupport = values["virtualizationSupport"].orEmpty(),
        customInjection = values["customInjection"].orEmpty(),
        stockConfig = values["stockConfig"].orEmpty(),
        source = "release_body",
        extraRows = extraRows,
    )
}

internal fun parseReleaseBodyParameterRows(body: String): List<Pair<String, String>> {
    if (body.isBlank()) return emptyList()
    return body
        .lineSequence()
        .mapNotNull(::parseReleaseBodyParameterRow)
        .filterNot { (label, value) ->
            val normalized = label.replace(Regex("\\s+"), "")
            normalized == "项目" && value.replace(Regex("\\s+"), "") == "内容"
        }
        .toList()
}

internal fun parseReleaseBodyParameterRow(line: String): Pair<String, String>? {
    val trimmed = line.trim()
    if (trimmed.isBlank()) return null
    if (trimmed.startsWith("|")) {
        val cells = trimmed.trim('|').split('|').map { it.trim() }.filter { it.isNotBlank() }
        if (cells.size >= 2 && !cells[0].all { it == '-' || it == ':' }) {
            return cells[0] to cells.drop(1).joinToString(" | ")
        }
    }
    val separated = trimmed.split(Regex("\\t+| {2,}"), limit = 2)
    if (separated.size == 2) return separated[0].trim() to separated[1].trim()
    val colonIndex =
        listOf(trimmed.indexOf(':'), trimmed.indexOf('：')).filter { it >= 0 }.minOrNull()
    if (colonIndex != null) {
        return trimmed.substring(0, colonIndex).trim() to trimmed.substring(colonIndex + 1).trim()
    }
    return RELEASE_PARAMETER_LABELS.firstOrNull { trimmed.startsWith(it) }
        ?.let { label -> label to trimmed.removePrefix(label).trim().trimStart(':', '：').trim() }
}

internal fun normalizeReleaseParameterLabel(label: String): String? {
    val compact = label.replace(Regex("\\s+"), "").lowercase()
    return when {
        compact.contains("android版本") -> "androidVersion"
        compact.contains("内核版本") -> "kernelVersion"
        compact.contains("子版本号") -> "subLevel"
        compact.contains("补丁级别") -> "osPatchLevel"
        compact.contains("ksu变体") -> "ksuVariant"
        compact.contains("ksu分支") -> "ksuBranch"
        compact.contains("构建时间") -> "buildTime"
        compact.contains("susfs状态") -> "susfsEnabled"
        compact.contains("zram增强") -> "zramEnabled"
        compact.contains("zram完整算法") -> "zramFullAlgo"
        compact.contains("zram额外算法") -> "zramExtraAlgos"
        compact.contains("bbg补丁") -> "bbgEnabled"
        compact.contains("ddklsm") -> "ddkLsm"
        compact.contains("ntsync补丁") -> "ntsyncEnabled"
        compact.contains("网络增强") ||
            compact.contains("networking增强") ||
            compact.contains("networing增强") -> "networkingEnabled"
        compact.contains("kpm功能") -> "kpmEnabled"
        compact.contains("kpm密码") -> "kpmPassword"
        compact.contains("re-kernel") || compact.contains("rekernel") -> "reKernelEnabled"
        compact.contains("虚拟化支持") -> "virtualizationSupport"
        compact == "自定义注入" -> "customInjection"
        compact.contains("stockconfig") -> "stockConfig"
        else -> null
    }
}

internal fun sanitizeReleaseParameterValue(key: String, value: String): String {
    if (key != "kpmPassword") return value.ifBlank { "none" }
    val normalized = value.trim().lowercase()
    return when {
        normalized.isBlank() -> "default"
        normalized in setOf("默认", "default", "无", "none", "not set") -> "default"
        else -> "set"
    }
}

internal fun isReleaseExtraParameterLabel(label: String): Boolean {
    val compact = label.replace(Regex("\\s+"), "").lowercase()
    return RELEASE_EXTRA_PARAMETER_LABELS.any { compact == it }
}

internal fun inferPrebuiltVersionFields(release: PrebuiltGkiRelease): PrebuiltVersionFields {
    val source = "${release.name}\n${release.tagName}\n${release.body}"
    val androidKernel =
        Regex("android\\s*(\\d+)\\s*/\\s*(\\d+\\.\\d+)(?:\\.(\\d+))?", RegexOption.IGNORE_CASE)
            .find(source)
    if (androidKernel != null) {
        return PrebuiltVersionFields(
            androidVersion = "android${androidKernel.groupValues[1]}",
            kernelVersion = androidKernel.groupValues[2],
            subLevel = androidKernel.groupValues.getOrNull(3).orEmpty(),
        )
    }
    val android =
        Regex("android\\s*(\\d+)", RegexOption.IGNORE_CASE)
            .find(source)
            ?.groupValues
            ?.getOrNull(1)
            ?.let { "android$it" }
            .orEmpty()
    return PrebuiltVersionFields(androidVersion = android)
}

internal data class PrebuiltVersionFields(
    val androidVersion: String = "",
    val kernelVersion: String = "",
    val subLevel: String = "",
)

internal val RELEASE_PARAMETER_LABELS =
    listOf(
            "自定义注入参数列表",
            "网络增强 (IPSet + BBR)",
            "Release asset 数",
            "5.10 修订版本",
            "自定义版本名",
            "一加 8E 支持",
            "Android 版本",
            "Stock Config",
            "ZRAM 完整算法",
            "ZRAM 额外算法",
            "NTsync 补丁",
            "虚拟化支持",
            "自定义注入",
            "内核版本",
            "子版本号",
            "补丁级别",
            "KSU 变体",
            "KSU 分支",
            "构建时间",
            "SUSFS 状态",
            "ZRAM 增强",
            "BBG 补丁",
            "DDK LSM",
            "网络增强",
            "KPM 功能",
            "KPM 密码",
            "Re-Kernel",
            "Artifact 数",
            "源 commit",
            "源 run",
        )
        .sortedByDescending { it.length }

internal val RELEASE_EXTRA_PARAMETER_LABELS =
    setOf(
        "源run",
        "源commit",
        "artifact数",
        "releaseasset数",
        "自定义版本名",
        "5.10修订版本",
        "一加8e支持",
        "自定义注入参数列表",
    )

@Composable
internal fun DismissFailedRunDialog(
    hasDownloadedFiles: Boolean,
    onConfirm: (deleteFiles: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var deleteFiles by remember(hasDownloadedFiles) { mutableStateOf(hasDownloadedFiles) }
    FlashConfirmDialog(
        title = stringResource(R.string.flash_dismiss_failed_title),
        message = stringResource(R.string.flash_dismiss_failed_message),
        confirmLabel = stringResource(R.string.flash_dismiss_confirm),
        onConfirm = { onConfirm(deleteFiles) },
        onDismiss = onDismiss,
        icon = AbkIcons.Close,
    ) {
        AbkCheckbox(
            state = ToggleableState(deleteFiles),
            onClick = { deleteFiles = !deleteFiles },
            label = stringResource(R.string.flash_dismiss_delete_files),
            modifier = Modifier.fillMaxWidth(),
            enabled = hasDownloadedFiles,
        )
    }
}
