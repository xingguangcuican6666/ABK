@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.abk.kernel.ui.screens.flash

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.abk.kernel.R
import com.abk.kernel.data.model.ArtifactCategory
import com.abk.kernel.data.model.ArtifactType
import com.abk.kernel.data.model.BuildArtifact
import com.abk.kernel.data.model.DownloadedArtifact
import com.abk.kernel.ui.blur.blurredCardBackground
import com.abk.kernel.ui.blur.blurredCardSurfaceColor
import com.abk.kernel.utils.DownloadUtils
import io.github.xingguangcuican6666.abk.ui.components.AbkBadge
import io.github.xingguangcuican6666.abk.ui.components.AbkButton
import io.github.xingguangcuican6666.abk.ui.components.AbkButtonStyle
import io.github.xingguangcuican6666.abk.ui.components.AbkCard
import io.github.xingguangcuican6666.abk.ui.components.AbkCluster
import io.github.xingguangcuican6666.abk.ui.components.AbkDialog
import io.github.xingguangcuican6666.abk.ui.components.AbkDivider
import io.github.xingguangcuican6666.abk.ui.components.AbkSpinner
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcon
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcons
import io.github.xingguangcuican6666.abk.ui.theme.AbkShapes
import io.github.xingguangcuican6666.abk.ui.theme.AbkTheme
import io.github.xingguangcuican6666.abk.ui.theme.AbkTone

internal fun WorkflowArtifactGroup.hasArtifactsInCategory(category: ArtifactCategory): Boolean {
    val remoteInCategory =
        remote.filter {
            DownloadUtils.classifyCategory(DownloadUtils.classifyArtifact(it.name)) == category
        }
    if (remoteInCategory.isNotEmpty()) return true
    val matchedLocalPaths =
        remoteInCategory
            .flatMap { source ->
                local.filter { DownloadUtils.matchesDownloadedArtifact(it, source) }
            }
            .map { it.filePath }
            .toSet()
    return local.any { it.category == category && it.filePath !in matchedLocalPaths }
}

@Composable
internal fun ArtifactSourceCard(
    artifact: BuildArtifact,
    downloadedFiles: List<DownloadedArtifact>,
    progress: Int?,
    autoDownloadEligible: Boolean,
    pendingAutoDownload: Boolean,
    showDownloadCancelActions: Boolean = false,
    onDownload: () -> Unit,
    onCancelDownload: (() -> Unit)? = null,
    onCancelAutoDownload: (() -> Unit)? = null,
    onCopyPath: (DownloadedArtifact) -> Unit,
    onInstall: (DownloadedArtifact) -> Unit,
    onFlash: (DownloadedArtifact) -> Unit,
    onDelete: (DownloadedArtifact) -> Unit,
    allowRootActions: Boolean,
) {
    val type = DownloadUtils.classifyArtifact(artifact.name)
    val typeLabel = stringResource(artifactTypeLabelRes(type))
    val animatedProgress by
        animateFloatAsState(
            targetValue = ((progress ?: 0) / 100f).coerceIn(0f, 1f),
            animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
            label = "artifact-download",
        )
    AbkCard(
        modifier = Modifier.fillMaxWidth().blurredCardBackground(AbkShapes.Large),
        containerColor = blurredCardSurfaceColor(AbkTheme.colors.raised),
    ) {
        ArtifactHeader(
            icon = artifactIcon(type),
            title = artifact.name,
            subtitle = "$typeLabel · ${DownloadUtils.formatSize(artifact.sizeInBytes)}",
            chip = if (autoDownloadEligible) stringResource(R.string.flash_auto_next) else null,
        )
        when {
            progress != null -> {
                FlashDownloadProgress(progress, animatedProgress)
                if (showDownloadCancelActions && onCancelDownload != null) {
                    FlashAction(
                        stringResource(R.string.flash_cancel_download),
                        AbkIcons.Close,
                        onCancelDownload,
                    )
                }
            }
            downloadedFiles.isEmpty() -> {
                if (pendingAutoDownload && autoDownloadEligible) {
                    Text(
                        stringResource(R.string.flash_download_waiting_auto),
                        style = MaterialTheme.typography.bodySmall,
                        color = AbkTheme.colors.inkMuted,
                    )
                    if (showDownloadCancelActions && onCancelAutoDownload != null) {
                        FlashAction(
                            stringResource(R.string.flash_stop_auto_download),
                            AbkIcons.Close,
                            onCancelAutoDownload,
                        )
                    }
                } else {
                    FlashAction(
                        stringResource(R.string.flash_download),
                        AbkIcons.Download,
                        onDownload,
                        style = AbkButtonStyle.Primary,
                    )
                }
            }
            else ->
                downloadedFiles.forEach { file ->
                    AbkDivider()
                    DownloadedOutputRow(
                        artifact = file,
                        onCopyPath = { onCopyPath(file) },
                        onInstall = { onInstall(file) },
                        onFlash = { onFlash(file) },
                        onDelete = { onDelete(file) },
                        allowRootActions = allowRootActions,
                    )
                }
        }
    }
}

@Composable
internal fun LocalOnlyArtifactCard(
    artifact: DownloadedArtifact,
    onCopyPath: (DownloadedArtifact) -> Unit,
    onInstall: (DownloadedArtifact) -> Unit,
    onFlash: (DownloadedArtifact) -> Unit,
    onDelete: (DownloadedArtifact) -> Unit,
    allowRootActions: Boolean,
) {
    AbkCard(
        modifier = Modifier.fillMaxWidth().blurredCardBackground(AbkShapes.Large),
        containerColor = blurredCardSurfaceColor(AbkTheme.colors.raised),
    ) {
        DownloadedOutputRow(
            artifact = artifact,
            onCopyPath = { onCopyPath(artifact) },
            onInstall = { onInstall(artifact) },
            onFlash = { onFlash(artifact) },
            onDelete = { onDelete(artifact) },
            allowRootActions = allowRootActions,
        )
    }
}

@Composable
internal fun ArtifactHeader(
    icon: ImageVector,
    title: String,
    subtitle: String,
    chip: String? = null,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth(),
    ) {
        AbkIcon(icon, null, tint = AbkTheme.colors.inkMuted)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                title,
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleSmall,
                fontFamily = AbkTheme.monoFont,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = AbkTheme.colors.inkMuted,
            )
        }
    }
    if (chip != null) AbkBadge(chip, dot = false)
}

@Composable
internal fun DownloadedOutputRow(
    artifact: DownloadedArtifact,
    onCopyPath: () -> Unit,
    onInstall: () -> Unit,
    onFlash: () -> Unit,
    onDelete: () -> Unit,
    allowRootActions: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ArtifactHeader(
            icon = artifactIcon(artifact.type),
            title = artifact.name,
            subtitle =
                "${stringResource(artifactTypeLabelRes(artifact.type))} · ${DownloadUtils.formatSize(artifact.sizeBytes)}",
            chip = stringResource(R.string.flash_local_file),
        )
        AbkCluster(Modifier.fillMaxWidth()) {
            if (artifact.isInstallableApk()) {
                FlashAction(
                    stringResource(R.string.flash_install),
                    AbkIcons.Download,
                    onInstall,
                    style = AbkButtonStyle.Soft,
                )
            } else {
                FlashAction(stringResource(R.string.flash_copy_path), AbkIcons.Copy, onCopyPath)
            }
            if (allowRootActions) {
                when (artifact.type) {
                    ArtifactType.KERNEL_IMG,
                    ArtifactType.ANYKERNEL3,
                    ArtifactType.SUSFS_MODULE -> {
                        FlashAction(
                            label = stringResource(flashButtonLabelRes(artifact.type)),
                            icon =
                                if (artifact.type == ArtifactType.SUSFS_MODULE) AbkIcons.Modules
                                else AbkIcons.Cpu,
                            onClick = onFlash,
                            style =
                                if (artifact.type == ArtifactType.KERNEL_IMG) AbkButtonStyle.Danger
                                else AbkButtonStyle.Soft,
                        )
                    }
                    else -> Unit
                }
            }
            FlashAction(
                stringResource(R.string.flash_delete_file),
                AbkIcons.Trash,
                onDelete,
                style = AbkButtonStyle.Ghost,
            )
        }
    }
}

@Composable
internal fun FlashTerminalDialog(
    title: String,
    running: Boolean,
    success: Boolean?,
    logLines: List<String>,
    canReboot: Boolean,
    onClose: () -> Unit,
    onReboot: () -> Unit,
) {
    val terminalScroll = rememberScrollState()
    val colors = AbkTheme.colors
    LaunchedEffect(logLines.size) { terminalScroll.animateScrollTo(terminalScroll.maxValue) }
    AbkDialog(
        title = if (running) stringResource(R.string.flash_executing_title, title) else title,
        onDismissRequest = { if (!running) onClose() },
        dismissDescription = stringResource(R.string.close),
        dismissible = !running,
        wide = true,
        actions = {
            if (!running) {
                AbkButton(onClick = onClose, style = AbkButtonStyle.Outline) {
                    Text(stringResource(R.string.close))
                }
                if (canReboot) {
                    FlashAction(
                        stringResource(R.string.flash_reboot),
                        AbkIcons.Refresh,
                        onReboot,
                        style = AbkButtonStyle.Danger,
                    )
                }
            }
        },
    ) {
        if (running) {
            val status = stringResource(R.string.flash_executing)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AbkSpinner(label = status)
                Text(status)
            }
        } else if (success != null) {
            AbkBadge(
                text =
                    stringResource(
                        if (success) R.string.status_success else R.string.flash_operation_failed
                    ),
                tone = if (success) AbkTone.Success else AbkTone.Danger,
            )
        }
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(min = 190.dp, max = 360.dp),
            shape = AbkShapes.Medium,
            color = colors.sunken,
            contentColor = colors.ink,
            border = BorderStroke(1.dp, colors.line),
        ) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(terminalScroll).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                logLines.forEach { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = AbkTheme.monoFont,
                        color = if (line.startsWith('$')) colors.accent.color else colors.ink,
                    )
                }
            }
        }
    }
}
