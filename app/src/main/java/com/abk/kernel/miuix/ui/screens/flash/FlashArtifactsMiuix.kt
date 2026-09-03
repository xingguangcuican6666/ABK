package com.abk.kernel.miuix.ui.screens.flash

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abk.kernel.R
import com.abk.kernel.data.model.ActiveDownloadTask
import com.abk.kernel.data.model.ArtifactCategory
import com.abk.kernel.data.model.ArtifactType
import com.abk.kernel.data.model.BuildArtifact
import com.abk.kernel.data.model.BuildParameterSummary
import com.abk.kernel.data.model.DownloadedArtifact
import com.abk.kernel.data.model.PREBUILT_GKI_RUN_ID
import com.abk.kernel.utils.DownloadUtils
import com.abk.kernel.utils.FlashFilterKernelKind
import com.abk.kernel.utils.FlashWorkflowFilter
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.abk.kernel.miuix.ui.screens.flash.common.*

// ─────────────────────────────────────────────────────────────────────────────
// Internal helpers (mirror FlashRouting/FlashFilter functions kept internal)
// ─────────────────────────────────────────────────────────────────────────────

private fun miuixArtifactIcon(type: ArtifactType): ImageVector = when (type) {
    ArtifactType.KERNEL_PACKAGE -> Icons.Default.Inventory2
    ArtifactType.KERNEL_IMG -> Icons.Default.Memory
    ArtifactType.ANYKERNEL3 -> Icons.Default.Archive
    ArtifactType.ABK_MANAGER -> Icons.Default.InstallMobile
    ArtifactType.KSU_MANAGER -> Icons.Default.Shield
    ArtifactType.SUSFS_MODULE -> Icons.Default.Extension
    ArtifactType.OTHER -> Icons.AutoMirrored.Filled.InsertDriveFile
}

@androidx.annotation.StringRes
private fun miuixArtifactTypeLabelRes(type: ArtifactType) = when (type) {
    ArtifactType.KERNEL_PACKAGE -> R.string.flash_artifact_kernel_package
    ArtifactType.KERNEL_IMG -> R.string.flash_artifact_kernel_img
    ArtifactType.ANYKERNEL3 -> R.string.flash_artifact_anykernel3
    ArtifactType.ABK_MANAGER -> R.string.flash_artifact_abk_manager
    ArtifactType.KSU_MANAGER -> R.string.flash_artifact_ksu_manager
    ArtifactType.SUSFS_MODULE -> R.string.flash_artifact_susfs_module
    ArtifactType.OTHER -> R.string.flash_artifact_other
}

@androidx.annotation.StringRes
private fun miuixFlashButtonLabelRes(type: ArtifactType) = when (type) {
    ArtifactType.KERNEL_IMG -> R.string.flash_button_flash
    ArtifactType.ANYKERNEL3 -> R.string.flash_button_flash_ak3
    ArtifactType.SUSFS_MODULE -> R.string.flash_button_install_module
    else -> R.string.flash_button_execute
}

private fun miuixIsInstallableApk(artifact: DownloadedArtifact): Boolean =
    artifact.type == ArtifactType.KSU_MANAGER ||
        (artifact.type != ArtifactType.ABK_MANAGER && artifact.name.endsWith(".apk", ignoreCase = true))

@androidx.annotation.StringRes
private fun miuixCategoryLabelRes(category: ArtifactCategory): Int = when (category) {
    ArtifactCategory.KERNEL -> R.string.flash_category_kernel
    ArtifactCategory.MANAGER -> R.string.flash_category_manager
    ArtifactCategory.MODULE -> R.string.flash_category_module
}

private fun miuixCategoryIcon(category: ArtifactCategory): ImageVector = when (category) {
    ArtifactCategory.KERNEL -> Icons.Default.Memory
    ArtifactCategory.MANAGER -> Icons.Default.Shield
    ArtifactCategory.MODULE -> Icons.Default.Extension
}

private val miuixArtifactCategoryOrder = listOf(
    ArtifactCategory.KERNEL,
    ArtifactCategory.MANAGER,
    ArtifactCategory.MODULE
)

private fun miuixKernelKindShortLabel(kind: FlashFilterKernelKind): String = when (kind) {
    FlashFilterKernelKind.ResuKisu -> "ReSukiSU"
    FlashFilterKernelKind.SukiSu -> "SukiSU"
    FlashFilterKernelKind.Official -> "Official"
    FlashFilterKernelKind.None -> "None"
}

private fun miuixWorkflowTaskLabel(task: ActiveDownloadTask): String =
    if (task.runNumber > 0) "#${task.runNumber} · ${task.runTitle}" else "#${task.runId} · ${task.runTitle}"

// ─────────────────────────────────────────────────────────────────────────────
// 1. MiuixTagChip
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Colored tag chip. Follows the ModuleRepositoryScreenMiuix pattern of a
 * small colored box with tinted text.
 */
@Composable
fun MiuixTagChip(
    label: String,
    primary: Boolean = true,
    maxWidth: androidx.compose.ui.unit.Dp = 160.dp,
    large: Boolean = false
) {
    val bgColor = if (primary) {
        MiuixTheme.colorScheme.primary.copy(alpha = FlashChipBgAlpha)
    } else {
        MiuixTheme.colorScheme.secondary.copy(alpha = FlashChipBgAlpha)
    }
    // Label text uses the adaptive surface content color so it stays white in
    // dark mode and black in light mode, instead of mirroring the accent color.
    val contentColor = MiuixTheme.colorScheme.onSurface
    Box(
        modifier = Modifier
            .widthIn(max = maxWidth)
            .background(
                color = bgColor,
                shape = FlashTagChipShape
            )
            .padding(
                horizontal = if (large) 10.dp else 6.dp,
                vertical = if (large) 5.dp else 2.dp
            )
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.body2,
            color = contentColor,
            fontSize = if (large) 14.sp else FlashChipFontSize,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Pill-shaped chip used for status/badge labels on workflow run cards.
 * Mirrors MD3 `ExpressiveStatusChip` with MIUIX theming.
 */
@Composable
private fun MiuixPillChip(
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(
                color = color.copy(alpha = FlashPillChipBgAlpha),
                shape = FlashPillChipShape
            )
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.body2,
            fontSize = FlashChipFontSize,
            fontWeight = FontWeight.Medium,
            // Adaptive surface content color: white in dark mode, black in light mode.
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 2. MiuixDownloadedOutputRow
// ─────────────────────────────────────────────────────────────────────────────

/**
 * MIUIX Card row for a downloaded file. Shows file name, type label, size, and
 * action buttons.
 *
 * Mirrors MD3 `DownloadedOutputRow` from `FlashArtifacts.kt`.
 */
@Composable
fun MiuixDownloadedOutputRow(
    artifact: DownloadedArtifact,
    onCopyPath: () -> Unit,
    onInstall: () -> Unit,
    onFlash: () -> Unit,
    onDelete: () -> Unit,
    allowRootActions: Boolean
) {
    val installableApk = miuixIsInstallableApk(artifact)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(FlashStatusIconSize)
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = artifact.name,
                    style = MiuixTheme.textStyles.main,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    color = MiuixTheme.colorScheme.onSurface,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${stringResource(miuixArtifactTypeLabelRes(artifact.type))} · ${DownloadUtils.formatSize(artifact.sizeBytes)}",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.flash_delete_file),
                    tint = MiuixTheme.colorScheme.error
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (installableApk) {
                Button(
                    onClick = onInstall,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    insideMargin = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    minWidth = 0.dp,
                    minHeight = 0.dp
                ) {
                    Icon(
                        imageVector = Icons.Default.InstallMobile,
                        contentDescription = null,
                        modifier = Modifier.size(FlashButtonIconSize)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.flash_install),
                        fontSize = FlashCompactButtonFontSize
                    )
                }
            } else {
                Button(
                    onClick = onCopyPath,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(),
                    insideMargin = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    minWidth = 0.dp,
                    minHeight = 0.dp
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(FlashButtonIconSize)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.flash_copy_path),
                        fontSize = FlashCompactButtonFontSize
                    )
                }
            }
            if (allowRootActions) {
                when (artifact.type) {
                    ArtifactType.KERNEL_IMG,
                    ArtifactType.ANYKERNEL3,
                    ArtifactType.SUSFS_MODULE -> {
                        val isDangerous = artifact.type == ArtifactType.KERNEL_IMG
                        Button(
                            onClick = onFlash,
                            modifier = Modifier.weight(1f),
                            colors = if (isDangerous) {
                                ButtonDefaults.buttonColors(
                                    color = MiuixTheme.colorScheme.error,
                                    contentColor = Color.White
                                )
                            } else {
                                ButtonDefaults.buttonColors(
                                    color = MiuixTheme.colorScheme.primary,
                                    contentColor = Color.White
                                )
                            },
                            insideMargin = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            minWidth = 0.dp,
                            minHeight = 0.dp
                        ) {
                            Icon(
                                imageVector = if (artifact.type == ArtifactType.SUSFS_MODULE) {
                                    Icons.Default.Extension
                                } else {
                                    Icons.Default.FlashOn
                                },
                                contentDescription = null,
                                modifier = Modifier.size(FlashButtonIconSize)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = stringResource(miuixFlashButtonLabelRes(artifact.type)),
                                fontSize = FlashCompactButtonFontSize
                            )
                        }
                    }
                    else -> {}
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 3. MiuixArtifactSourceCard
// ─────────────────────────────────────────────────────────────────────────────

/**
 * MIUIX Card for a remote artifact with its matched local downloaded files.
 *
 * Mirrors MD3 `ArtifactSourceCard` from `FlashArtifacts.kt`.
 */
@Composable
fun MiuixArtifactSourceCard(
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
    allowRootActions: Boolean
) {
    val type = DownloadUtils.classifyArtifact(artifact.name)
    val chipLabel = if (autoDownloadEligible) {
        stringResource(R.string.flash_auto_next)
    } else {
        stringResource(miuixArtifactTypeLabelRes(type))
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ── Header row ──────────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = miuixArtifactIcon(type),
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(FlashCardHeaderIconSize)
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = artifact.name,
                        style = MiuixTheme.textStyles.main,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        color = MiuixTheme.colorScheme.onSurface,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${stringResource(miuixArtifactTypeLabelRes(type))} · ${DownloadUtils.formatSize(artifact.sizeInBytes)}",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                MiuixTagChip(label = chipLabel, primary = true, maxWidth = 140.dp)
            }

            // ── State-dependent content ─────────────────────────────────
            when {
                progress != null -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            progress = (progress / 100f).coerceIn(0f, 1f),
                    colors = flashProgressColors()
                        )
                        Text(
                            text = stringResource(R.string.flash_download_progress, progress),
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                        if (showDownloadCancelActions && onCancelDownload != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Button(
                                    onClick = onCancelDownload,
                                    colors = ButtonDefaults.buttonColors(),
                                    insideMargin = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    minWidth = 0.dp,
                                    minHeight = 0.dp
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Cancel,
                                        contentDescription = null,
                                        modifier = Modifier.size(FlashButtonIconSize)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.flash_cancel_download),
                                        fontSize = FlashCompactButtonFontSize
                                    )
                                }
                            }
                        }
                    }
                }
                downloadedFiles.isEmpty() -> {
                    if (pendingAutoDownload && autoDownloadEligible) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = stringResource(R.string.flash_download_waiting_auto),
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                            )
                            if (showDownloadCancelActions && onCancelAutoDownload != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Button(
                                        onClick = onCancelAutoDownload,
                                        colors = ButtonDefaults.buttonColors(),
                                        insideMargin = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        minWidth = 0.dp,
                                        minHeight = 0.dp
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Cancel,
                                            contentDescription = null,
                                            modifier = Modifier.size(FlashButtonIconSize)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = stringResource(R.string.flash_stop_auto_download),
                                            fontSize = FlashCompactButtonFontSize
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Button(
                            onClick = onDownload,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                color = MiuixTheme.colorScheme.primary,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(FlashStatusIconSize)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(text = stringResource(R.string.flash_download))
                        }
                    }
                }
                else -> {
                    downloadedFiles.forEachIndexed { index, file ->
                        if (index > 0) {
                            HorizontalDivider()
                        }
                        MiuixDownloadedOutputRow(
                            artifact = file,
                            onCopyPath = { onCopyPath(file) },
                            onInstall = { onInstall(file) },
                            onFlash = { onFlash(file) },
                            onDelete = { onDelete(file) },
                            allowRootActions = allowRootActions
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 4. MiuixLocalOnlyArtifactCard
// ─────────────────────────────────────────────────────────────────────────────

/**
 * MIUIX Card for a downloaded file not linked to a remote source.
 *
 * Mirrors MD3 `LocalOnlyArtifactCard` from `FlashArtifacts.kt`.
 */
@Composable
fun MiuixLocalOnlyArtifactCard(
    artifact: DownloadedArtifact,
    onCopyPath: (DownloadedArtifact) -> Unit,
    onInstall: (DownloadedArtifact) -> Unit,
    onFlash: (DownloadedArtifact) -> Unit,
    onDelete: (DownloadedArtifact) -> Unit,
    allowRootActions: Boolean
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = miuixArtifactIcon(artifact.type),
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(FlashCardHeaderIconSize)
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = artifact.name,
                        style = MiuixTheme.textStyles.main,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        color = MiuixTheme.colorScheme.onSurface,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${stringResource(miuixArtifactTypeLabelRes(artifact.type))} · ${DownloadUtils.formatSize(artifact.sizeBytes)}",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                MiuixTagChip(
                    label = stringResource(R.string.flash_local_file),
                    primary = true,
                    maxWidth = 140.dp
                )
            }

            MiuixDownloadedOutputRow(
                artifact = artifact,
                onCopyPath = { onCopyPath(artifact) },
                onInstall = { onInstall(artifact) },
                onFlash = { onFlash(artifact) },
                onDelete = { onDelete(artifact) },
                allowRootActions = allowRootActions
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 5. MiuixWorkflowCategorySection
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Wraps a category header plus a list of artifact cards inside a MIUIX Card.
 *
 * Mirrors the category rendering portion of MD3 `WorkflowCategorySection` from
 * `FlashWorkflow.kt`, but exposes a simpler, pre-split API that the caller
 * (the MIUIX workflow detail screen) is expected to populate.
 */
@Composable
fun MiuixWorkflowCategorySection(
    category: ArtifactCategory,
    sourceArtifacts: List<BuildArtifact>,
    localOnlyArtifacts: List<DownloadedArtifact>,
    matchedLocalBySource: (BuildArtifact) -> List<DownloadedArtifact>,
    downloadProgress: Map<Long, Int>,
    autoDownload: Boolean,
    pendingAutoDownloadRunId: Long?,
    showDownloadCancelActions: Boolean = false,
    onDownload: (BuildArtifact) -> Unit,
    onCancelDownload: (Long) -> Unit = {},
    onCancelAutoDownload: (Long) -> Unit = {},
    onCopyPath: (DownloadedArtifact) -> Unit,
    onInstall: (DownloadedArtifact) -> Unit,
    onFlash: (DownloadedArtifact) -> Unit,
    onDelete: (DownloadedArtifact) -> Unit,
    allowRootActions: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Category header
        Row(
            modifier = Modifier.padding(start = 4.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = miuixCategoryIcon(category),
                contentDescription = null,
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(FlashMediumIconSize)
            )
            Text(
                text = stringResource(miuixCategoryLabelRes(category)),
                style = MiuixTheme.textStyles.subtitle,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
        }

        if (sourceArtifacts.isNotEmpty() || localOnlyArtifacts.isNotEmpty()) {
            sourceArtifacts.forEach { artifact ->
                MiuixArtifactSourceCard(
                    artifact = artifact,
                    downloadedFiles = matchedLocalBySource(artifact),
                    progress = downloadProgress[artifact.id],
                    autoDownloadEligible = autoDownload &&
                        pendingAutoDownloadRunId == artifact.runId &&
                        DownloadUtils.shouldAutoDownload(artifact),
                    pendingAutoDownload = pendingAutoDownloadRunId == artifact.runId,
                    showDownloadCancelActions = showDownloadCancelActions,
                    onDownload = { onDownload(artifact) },
                    onCancelDownload = if (showDownloadCancelActions) {
                        { onCancelDownload(artifact.id) }
                    } else {
                        null
                    },
                    onCancelAutoDownload = if (showDownloadCancelActions) {
                        { onCancelAutoDownload(artifact.runId) }
                    } else {
                        null
                    },
                    onCopyPath = onCopyPath,
                    onInstall = onInstall,
                    onFlash = onFlash,
                    onDelete = onDelete,
                    allowRootActions = allowRootActions
                )
            }
            localOnlyArtifacts.forEach { artifact ->
                MiuixLocalOnlyArtifactCard(
                    artifact = artifact,
                    onCopyPath = onCopyPath,
                    onInstall = onInstall,
                    onFlash = onFlash,
                    onDelete = onDelete,
                    allowRootActions = allowRootActions
                )
            }
        } else {
            // No artifacts yet — show indeterminate build progress hint
            MiuixCategoryProgressPlaceholder()
        }
    }
}

/**
 * Placeholder shown when a category has no artifacts yet (build still running).
 */
@Composable
private fun MiuixCategoryProgressPlaceholder() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(FlashCardHeaderIconSize),
                    progress = null,
                    size = 20.dp,
                    strokeWidth = 2.dp
                )
                Text(
                    text = stringResource(R.string.flash_building_subtitle),
                    style = MiuixTheme.textStyles.main,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                progress = null,
                colors = flashProgressColors()
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 6. MiuixWorkflowDownloadManagementCard
// ─────────────────────────────────────────────────────────────────────────────

/**
 * MIUIX Card showing active download tasks with progress and cancel buttons.
 *
 * Mirrors MD3 `WorkflowDownloadManagementCard` from `FlashLayout.kt`.
 */
@Composable
fun MiuixWorkflowDownloadManagementCard(
    tasks: List<ActiveDownloadTask>,
    pendingRunId: Long?,
    pendingRunLabel: String?,
    onCancelTask: (Long) -> Unit,
    onCancelPending: (Long) -> Unit
) {
    if (tasks.isEmpty() && pendingRunId == null) return

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(FlashCardHeaderIconSize)
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.flash_download_tasks_title),
                        style = MiuixTheme.textStyles.title4,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.flash_download_tasks_desc),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            }

            // Pending auto-download run
            if (pendingRunId != null) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = pendingRunLabel ?: "#$pendingRunId",
                        style = MiuixTheme.textStyles.main,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        color = MiuixTheme.colorScheme.onSurface,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(R.string.flash_download_waiting_auto),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = { onCancelPending(pendingRunId) },
                            colors = ButtonDefaults.buttonColors(),
                            insideMargin = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            minWidth = 0.dp,
                            minHeight = 0.dp
                        ) {
                            Text(
                                text = stringResource(R.string.flash_stop_auto_download),
                                fontSize = FlashCompactButtonFontSize
                            )
                        }
                    }
                }
            }

            // Active tasks
            tasks.forEachIndexed { index, task ->
                if (index > 0) {
                    HorizontalDivider()
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = task.name,
                                style = MiuixTheme.textStyles.main,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                color = MiuixTheme.colorScheme.onSurface,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = miuixWorkflowTaskLabel(task),
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        MiuixTagChip(
                            label = if (task.automatic) {
                                stringResource(R.string.flash_auto_download_badge)
                            } else {
                                stringResource(R.string.flash_manual_download_badge)
                            },
                            primary = !task.automatic
                        )
                    }
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        progress = (task.progress / 100f).coerceIn(0f, 1f),
                        colors = flashProgressColors()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.flash_download_progress, task.progress),
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                        Button(
                            onClick = { onCancelTask(task.key) },
                            colors = ButtonDefaults.buttonColors(),
                            insideMargin = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            minWidth = 0.dp,
                            minHeight = 0.dp
                        ) {
                            Text(
                                text = stringResource(R.string.flash_cancel_download),
                                fontSize = FlashCompactButtonFontSize
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 7. MiuixWorkflowRunCard
// ─────────────────────────────────────────────────────────────────────────────

/**
 * MIUIX Card for a workflow run in the list. Shows run title, meta info,
 * status chip, kernel variant + SUSFS tag chips, and category progress
 * summary.
 *
 * Mirrors MD3 `WorkflowRunCard` from `FlashWorkflow.kt`.
 *
 * @param group            Pre-computed artifact group for this run.
 * @param summary          Optional parsed build parameters (from log).
 * @param showKernelBuildChips Whether to render kernel-variant / SUSFS chips.
 * @param dispatchedKernelVariant Kernel variant that was requested at dispatch time.
 * @param dispatchedSusfsEnabled  Whether SUSFS was enabled at dispatch time.
 * @param active           Whether the build is still running.
 * @param failedGhost      Whether this is a failed build shown as a dismissible ghost card.
 * @param cancelling       Whether a cancel request is in flight.
 * @param onClick          Navigate to the detail page.
 * @param onDelete         Delete / dismiss the workflow.
 * @param onCancel         Cancel the running workflow.
 */
@Composable
internal fun MiuixWorkflowRunCard(
    runId: Long,
    runTitle: String,
    runNumber: Int,
    runCreatedAt: String = "",
    sourceCount: Int = 0,
    downloadedCount: Int = 0,
    categories: Set<ArtifactCategory> = emptySet(),
    summary: BuildParameterSummary? = null,
    showKernelBuildChips: Boolean = true,
    dispatchedKernelVariant: String? = null,
    dispatchedSusfsEnabled: Boolean? = null,
    active: Boolean = false,
    failedGhost: Boolean = false,
    cancelling: Boolean = false,
    onClick: () -> Unit,
    onDelete: () -> Unit = {},
    onCancel: () -> Unit = {}
) {
    val categoryList = miuixArtifactCategoryOrder.filter { it in categories }
    val kernelKind = if (showKernelBuildChips) {
        FlashWorkflowFilter.kernelKind(summary, dispatchedKernelVariant)
    } else {
        null
    }
    val susfsOn = if (showKernelBuildChips) {
        val v = summary?.susfsEnabled.orEmpty().lowercase().trim()
        if (v.isNotBlank()) {
            v !in setOf("false", "0", "no", "disabled", "off", "未启用", "未開啟", "未开启")
        } else {
            dispatchedSusfsEnabled == true
        }
    } else {
        false
    }
    val dateLabel = runCreatedAt.take(10)

    val accentError = MiuixTheme.colorScheme.error

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ── Title row ────────────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (active) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(FlashRunStatusIconSize),
                        progress = null,
                        size = 22.dp,
                        strokeWidth = 2.dp
                    )
                } else if (failedGhost) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.error,
                        modifier = Modifier.size(FlashRunStatusIconSize)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.FolderSpecial,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.size(FlashRunStatusIconSize)
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = if (runId == PREBUILT_GKI_RUN_ID) {
                            stringResource(R.string.flash_prebuilt_gki)
                        } else {
                            stringResource(
                                R.string.flash_workflow_label,
                                if (runNumber > 0) "#$runNumber" else "#$runId"
                            )
                        },
                        style = MiuixTheme.textStyles.title4,
                        fontWeight = FontWeight.SemiBold,
                        color = if (failedGhost) {
                            MiuixTheme.colorScheme.error
                        } else {
                            MiuixTheme.colorScheme.onSurface
                        }
                    )
                    Text(
                        text = runTitle,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (active) {
                    IconButton(onClick = onCancel, enabled = !cancelling) {
                        if (cancelling) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(FlashCardHeaderIconSize),
                                progress = null,
                                size = 20.dp,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Cancel,
                                contentDescription = stringResource(R.string.flash_cancel_workflow),
                                tint = MiuixTheme.colorScheme.error
                            )
                        }
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.flash_delete_workflow),
                        tint = if (failedGhost) accentError else MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            }

            // ── Chip row ─────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (failedGhost) {
                    MiuixPillChip(
                        label = stringResource(R.string.flash_build_failed_chip),
                        color = MiuixTheme.colorScheme.error
                    )
                }
                if (kernelKind != null) {
                    MiuixPillChip(
                        label = miuixKernelKindShortLabel(kernelKind),
                        color = MiuixTheme.colorScheme.primary
                    )
                }
                if (dateLabel.isNotBlank()) {
                    MiuixPillChip(label = dateLabel, color = MiuixTheme.colorScheme.primary)
                }
                if (susfsOn) {
                    MiuixPillChip(
                        label = stringResource(R.string.flash_chip_susfs),
                        color = MiuixTheme.colorScheme.primary
                    )
                }
                if (!failedGhost) {
                    MiuixPillChip(
                        label = stringResource(R.string.flash_source_artifacts_count, sourceCount),
                        color = MiuixTheme.colorScheme.primary
                    )
                    val secondary = MiuixTheme.colorScheme.secondary
                    MiuixPillChip(
                        label = stringResource(R.string.flash_downloaded_count, downloadedCount),
                        color = secondary
                    )
                    categoryList.forEach {
                        MiuixPillChip(
                            label = stringResource(miuixCategoryLabelRes(it)),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                    }
                }
            }
        }
    }
}
