@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.abk.kernel.ui.screens.flash

import android.content.Intent
import android.net.Uri
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.RunCircle
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil.compose.AsyncImage
import com.abk.kernel.R
import com.abk.kernel.data.model.ActiveDownloadTask
import com.abk.kernel.data.model.ArtifactCategory
import com.abk.kernel.data.model.ArtifactType
import com.abk.kernel.data.model.BuildArtifact
import com.abk.kernel.data.model.BuildParameterSummary
import com.abk.kernel.data.model.BuildProgress
import com.abk.kernel.utils.BuildProgressUtils
import com.abk.kernel.data.model.BuildQueueItemStatus
import com.abk.kernel.data.model.BuildStatus
import com.abk.kernel.data.model.DownloadedArtifact
import com.abk.kernel.data.model.KernelBuildConfig
import com.abk.kernel.data.model.KernelSupport
import com.abk.kernel.data.model.PREBUILT_GKI_RUN_ID
import com.abk.kernel.data.model.PrebuiltGkiAsset
import com.abk.kernel.data.model.PrebuiltGkiRelease
import com.abk.kernel.data.model.WorkflowJob
import com.abk.kernel.data.model.WorkflowRun
import com.abk.kernel.data.model.WorkflowStep
import com.abk.kernel.data.model.isFailedFlashRun
import com.abk.kernel.utils.FlashFilter
import com.abk.kernel.utils.FlashFilterKernelKind
import com.abk.kernel.utils.FlashFilterManagerKind
import com.abk.kernel.utils.FlashFilterWorkflowState
import com.abk.kernel.utils.FlashWorkflowFilter
import com.abk.kernel.utils.WorkflowPrimary
import com.abk.kernel.ui.components.AbkScreenHorizontalPadding
import com.abk.kernel.ui.components.ObserveChildPageVisibility
import com.abk.kernel.ui.components.childPageOverlayEnterTransition
import com.abk.kernel.ui.components.childPageOverlayExitTransition
import com.abk.kernel.ui.components.childPageScrimExitTransition
import com.abk.kernel.ui.components.rememberChildPageBackController
import com.abk.kernel.ui.components.rememberChildPageOverlayTransition
import com.abk.kernel.utils.FailureLogExtractor
import com.abk.kernel.ui.components.LIVE_DURATION_MINUTE_HAND_PERIOD_MS
import com.abk.kernel.ui.components.LiveDurationScheduleIcon
import com.abk.kernel.ui.components.ShimmerLinearProgress
import com.abk.kernel.ui.components.liveWorkflowShimmerBrush
import com.abk.kernel.ui.components.rememberLiveWorkflowShimmerPhase
import com.abk.kernel.ui.components.MinuteHandController
import com.abk.kernel.ui.components.MinuteHandControllerHost
import com.abk.kernel.ui.components.MinuteHandPhase
import com.abk.kernel.ui.components.ExpressiveEmptyState
import com.abk.kernel.ui.components.ExpressiveHeroCard
import com.abk.kernel.ui.components.ExpressiveSectionCard
import com.abk.kernel.ui.components.ExpressiveStatusChip
import com.abk.kernel.ui.components.ExpressiveTopBar
import com.abk.kernel.ui.theme.uiSurfaceColor
import com.abk.kernel.utils.DownloadUtils
import com.abk.kernel.utils.RootUtils
import com.abk.kernel.viewmodel.MainViewModel
import com.abk.kernel.viewmodel.mergeWorkflowActiveDownloads
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


@Composable
internal fun WorkflowRunCard(
    group: WorkflowArtifactGroup,
    summary: BuildParameterSummary?,
    showKernelBuildChips: Boolean,
    showParameterDetails: Boolean,
    dispatchedKernelVariant: String?,
    dispatchedSusfsEnabled: Boolean?,
    active: Boolean,
    failedGhost: Boolean,
    cancelling: Boolean,
    onClick: () -> Unit,
    onShowParameters: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit
) {
    val sourceCount = group.remote.size
    val downloadedCount = group.local.size
    val categories = artifactCategoryOrder.filter { it in group.categories }
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
            // Summary not loaded yet — fall back to the dispatched config so
            // the SUSFS chip shows during an active build, just like the
            // kernel-kind chip does.
            dispatchedSusfsEnabled == true
        }
    } else {
        false
    }
    val dateLabel = group.runCreatedAt.take(10)
    val colorScheme = MaterialTheme.colorScheme
    val cardContainer = when {
        failedGhost -> uiSurfaceColor(lerp(colorScheme.surfaceContainer, colorScheme.errorContainer, 0.48f))
        else -> uiSurfaceColor(colorScheme.surfaceContainer)
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = cardContainer),
        border = if (failedGhost) {
            BorderStroke(1.dp, colorScheme.error.copy(alpha = 0.28f))
        } else {
            null
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (active) {
                    LoadingIndicator(
                        modifier = Modifier.size(22.dp)
                    )
                } else if (failedGhost) {
                    Icon(
                        Icons.Default.Error,
                        null,
                        tint = colorScheme.error,
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    Icon(
                        Icons.Default.FolderSpecial,
                        null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = if (group.runId == PREBUILT_GKI_RUN_ID) {
                            stringResource(R.string.flash_prebuilt_gki)
                        } else {
                            stringResource(
                                R.string.flash_workflow_label,
                                if (group.runNumber > 0) "#${group.runNumber}" else "#${group.runId}"
                            )
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (failedGhost) {
                            colorScheme.onErrorContainer
                        } else {
                            colorScheme.onSurface
                        },
                    )
                    Text(
                        text = group.runTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // Parameter details come from the kernel build log — manager-only
                // workflows have nothing meaningful to show. While a build is
                // still running the log isn't ready either, so hide until finished.
                if (showParameterDetails && !active && !failedGhost) {
                    IconButton(onClick = onShowParameters) {
                        Icon(Icons.Default.Tune, contentDescription = stringResource(R.string.flash_parameter_details))
                    }
                }
                if (active) {
                    IconButton(onClick = onCancel, enabled = !cancelling) {
                        if (cancelling) {
                            // Red while waiting for GitHub to acknowledge the
                            // cancel — keeps the icon's destructive intent
                            // visible during the limbo period before the run
                            // status flips to "completed".
                            LoadingIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            Icon(
                                Icons.Default.Cancel,
                                contentDescription = stringResource(R.string.flash_cancel_workflow),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        if (failedGhost) Icons.Default.Close else Icons.Default.Delete,
                        contentDescription = if (failedGhost) {
                            stringResource(R.string.flash_dismiss_failed)
                        } else {
                            stringResource(R.string.flash_delete_workflow)
                        },
                        tint = if (failedGhost) Color.White else colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Kernel kind chip only renders once we know the variant —
                // otherwise it would always say "None" for in-progress builds.
                if (failedGhost) {
                    ExpressiveStatusChip(
                        label = stringResource(R.string.flash_build_failed_chip),
                        color = colorScheme.error
                    )
                }
                if (kernelKind != null) {
                    ExpressiveStatusChip(
                        label = stringResource(kernelKind.shortLabelRes()),
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                if (dateLabel.isNotBlank()) {
                    ExpressiveStatusChip(label = dateLabel, color = MaterialTheme.colorScheme.primary)
                }
                if (susfsOn) {
                    ExpressiveStatusChip(label = stringResource(R.string.flash_chip_susfs), color = MaterialTheme.colorScheme.primary)
                }
                if (!failedGhost) {
                    ExpressiveStatusChip(label = stringResource(R.string.flash_source_artifacts_count, sourceCount), color = MaterialTheme.colorScheme.primary)
                    ExpressiveStatusChip(label = stringResource(R.string.flash_downloaded_count, downloadedCount), color = MaterialTheme.colorScheme.secondary)
                    categories.forEach {
                        ExpressiveStatusChip(label = stringResource(it.labelRes()), color = MaterialTheme.colorScheme.surfaceTint)
                    }
                }
            }
        }
    }
}

internal fun openGithubRun(context: Context, url: String) {
    if (url.isBlank()) return
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}

internal fun flattenFailedWorkflowSteps(jobs: List<WorkflowJob>): List<WorkflowStep> =
    jobs.flatMap { job ->
        val jobSteps = job.steps.orEmpty()
        if (jobSteps.isEmpty()) {
            listOf(
                WorkflowStep(
                    name = job.name,
                    status = job.status,
                    conclusion = job.conclusion,
                    number = 0,
                )
            )
        } else {
            jobSteps
        }
    }

internal val BuildErrorLogMaxHeight = 525.dp

@Composable
internal fun rememberBuildErrorLogEdgeLock(scrollState: ScrollState): NestedScrollConnection =
    remember(scrollState) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                val atTop = scrollState.value == 0
                val atBottom = scrollState.value >= scrollState.maxValue
                return when {
                    atTop && available.y > 0f -> Offset(0f, available.y)
                    atBottom && available.y < 0f -> Offset(0f, available.y)
                    else -> Offset.Zero
                }
            }

            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity,
            ): Velocity {
                val atTop = scrollState.value == 0
                val atBottom = scrollState.value >= scrollState.maxValue
                return when {
                    atTop && available.y > 0f -> available
                    atBottom && available.y < 0f -> available
                    else -> Velocity.Zero
                }
            }
        }
    }

@Composable
internal fun BuildErrorLogPanel(text: String) {
    val colorScheme = MaterialTheme.colorScheme
    val displayText = remember(text) { FailureLogExtractor.sanitizeForDisplay(text) }
    val logScrollState = rememberScrollState()
    val edgeLock = rememberBuildErrorLogEdgeLock(logScrollState)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = uiSurfaceColor(colorScheme.surfaceContainerHighest),
    ) {
        SelectionContainer {
            Text(
                text = displayText,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFeatureSettings = "tnum,lnum",
                ),
                color = colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = BuildErrorLogMaxHeight)
                    .nestedScroll(edgeLock)
                    .verticalScroll(logScrollState)
                    .padding(12.dp),
            )
        }
    }
}

@Composable
internal fun FailedWorkflowDetail(
    run: WorkflowRun,
    jobs: List<WorkflowJob>?,
    jobsLoading: Boolean,
    jobsError: String?,
    logExcerpt: String?,
    logLoading: Boolean,
    onBack: () -> Unit,
    onOpenGitHub: () -> Unit,
    onRetryJobs: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val steps = remember(jobs) { jobs?.let(::flattenFailedWorkflowSteps).orEmpty() }
    val failureIndex = remember(steps) { steps.indexOfFirst { it.conclusion == "failure" } }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = AbkScreenHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            ExpressiveSectionCard(
                title = stringResource(
                    R.string.flash_workflow_label,
                    if (run.runNumber > 0) "#${run.runNumber}" else "#${run.id}"
                ),
                subtitle = run.displayTitle ?: run.name.orEmpty(),
                icon = Icons.Default.Error
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.flash_back)
                        )
                    }
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text(stringResource(R.string.flash_conclusion_failure)) },
                        colors = AssistChipDefaults.assistChipColors(
                            disabledContainerColor = colorScheme.errorContainer,
                            disabledLabelColor = colorScheme.onErrorContainer,
                        ),
                        border = AssistChipDefaults.assistChipBorder(
                            enabled = false,
                            borderColor = colorScheme.error.copy(alpha = 0.35f),
                        ),
                    )
                    Text(
                        text = stringResource(R.string.flash_failed_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        item {
            ExpressiveSectionCard(
                title = stringResource(R.string.flash_failed_step_list_title),
                icon = Icons.Default.RunCircle
            ) {
                when {
                    jobsLoading -> LoadingRow(stringResource(R.string.flash_loading_steps))
                    jobsError != null -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = stringResource(R.string.flash_steps_load_error),
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.error,
                            )
                            TextButton(onClick = onRetryJobs) {
                                Text(stringResource(R.string.retry))
                            }
                        }
                    }
                    steps.isEmpty() -> {
                        Text(
                            text = stringResource(R.string.flash_failed_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                        )
                    }
                    else -> {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            steps.forEachIndexed { index, step ->
                                FailedWorkflowStepRow(
                                    step = step,
                                    failed = index == failureIndex,
                                    muted = failureIndex >= 0 && index > failureIndex,
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            ExpressiveSectionCard(
                title = stringResource(R.string.flash_build_error),
                icon = Icons.Default.Terminal
            ) {
                when {
                    logLoading -> LoadingRow(stringResource(R.string.flash_loading_steps))
                    else -> {
                        val excerpt = logExcerpt?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.flash_build_error_unavailable)
                        BuildErrorLogPanel(text = excerpt)
                    }
                }
            }
        }

        item {
            FilledTonalButton(
                onClick = onOpenGitHub,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
            ) {
                Icon(Icons.Default.RunCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.flash_open_github_actions))
            }
        }
    }
}

@Composable
internal fun FailedWorkflowStepRow(
    step: WorkflowStep,
    failed: Boolean,
    muted: Boolean,
) {
    val colorScheme = MaterialTheme.colorScheme
    val alpha = if (muted) 0.45f else 1f
    val rowBackground = when {
        failed -> uiSurfaceColor(lerp(colorScheme.surfaceContainer, colorScheme.errorContainer, 0.55f))
        else -> uiSurfaceColor(colorScheme.surfaceContainerHighest)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { this.alpha = alpha }
            .background(
                color = rowBackground,
                shape = RoundedCornerShape(10.dp),
            )
            .then(
                if (failed) {
                    Modifier.padding(start = 0.dp)
                } else {
                    Modifier
                }
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (failed) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(36.dp)
                    .background(colorScheme.error, RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp))
            )
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = when {
                    failed -> Icons.Default.Cancel
                    step.conclusion == "success" || step.status == "completed" -> Icons.Default.CheckCircle
                    else -> Icons.Default.Schedule
                },
                contentDescription = null,
                tint = when {
                    failed -> colorScheme.error
                    step.conclusion == "success" || step.status == "completed" -> colorScheme.onSurfaceVariant
                    else -> colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                },
                modifier = Modifier.size(16.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = step.name,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (failed) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (failed) colorScheme.onErrorContainer else colorScheme.onSurface,
                )
                if (failed && !step.conclusion.isNullOrBlank()) {
                    Text(
                        text = step.conclusion.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
internal fun WorkflowCategorySection(
    group: WorkflowArtifactGroup,
    category: ArtifactCategory,
    showDuration: Boolean,
    createdAt: String,
    finishedAt: String? = null,
    liveDuration: Boolean,
    minuteHandController: MinuteHandController? = null,
    progress: BuildProgress?,
    downloadProgress: Map<Long, Int>,
    autoDownload: Boolean,
    pendingAutoDownloadRunId: Long?,
    onDownload: (BuildArtifact) -> Unit,
    onCancelDownload: (Long) -> Unit = {},
    onCancelAutoDownload: (Long) -> Unit = {},
    showDownloadCancelActions: Boolean = false,
    onCopyPath: (DownloadedArtifact) -> Unit,
    onInstall: (DownloadedArtifact) -> Unit,
    onFlash: (DownloadedArtifact) -> Unit,
    onDelete: (DownloadedArtifact) -> Unit,
    allowRootActions: Boolean,
) {
    val remoteInCategory = group.remote.filter {
        DownloadUtils.classifyCategory(DownloadUtils.classifyArtifact(it.name)) == category
    }
    val matchedLocalPaths = remoteInCategory
        .flatMap { source -> group.local.filter { DownloadUtils.matchesDownloadedArtifact(it, source) } }
        .map { it.filePath }
        .toSet()
    val localOnly = group.local.filter { it.category == category && it.filePath !in matchedLocalPaths }
    val hasArtifacts = remoteInCategory.isNotEmpty() || localOnly.isNotEmpty()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (hasArtifacts || liveDuration) {
            CategoryHeaderWithDuration(
                category = category,
                showDuration = showDuration,
                createdAt = createdAt,
                finishedAt = finishedAt,
                live = liveDuration,
                minuteHandController = minuteHandController,
            )
        }
        if (hasArtifacts) {
            remoteInCategory.forEach { artifact ->
                ArtifactSourceCard(
                    artifact = artifact,
                    downloadedFiles = group.local.filter {
                        DownloadUtils.matchesDownloadedArtifact(it, artifact)
                    },
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
                    allowRootActions = allowRootActions,
                )
            }
            localOnly.forEach { artifact ->
                LocalOnlyArtifactCard(
                    artifact = artifact,
                    onCopyPath = onCopyPath,
                    onInstall = onInstall,
                    onFlash = onFlash,
                    onDelete = onDelete,
                    allowRootActions = allowRootActions,
                )
            }
        } else {
            CategoryProgressCard(progress = progress)
        }
    }
}

@Composable
internal fun BuildingWorkflowDetail(
    run: WorkflowRun,
    group: WorkflowArtifactGroup?,
    progress: BuildProgress?,
    cancelling: Boolean,
    downloadProgress: Map<Long, Int>,
    autoDownload: Boolean,
    pendingAutoDownloadRunId: Long?,
    onDownload: (BuildArtifact) -> Unit,
    onCopyPath: (DownloadedArtifact) -> Unit,
    onInstall: (DownloadedArtifact) -> Unit,
    onFlash: (DownloadedArtifact) -> Unit,
    onDelete: (DownloadedArtifact) -> Unit,
    allowRootActions: Boolean,
    unlinkedWorkflowTitle: String,
    onBack: () -> Unit,
    onCancel: () -> Unit,
    onCancelDownload: (Long) -> Unit,
    onCancelAutoDownload: (Long) -> Unit,
    minuteHandController: MinuteHandController? = null,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = AbkScreenHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            // Header mirrors WorkflowDetailHeader visually (same ExpressiveSectionCard +
            // back-button row), but without artifact-counts / parameters / delete actions.
            ExpressiveSectionCard(
                title = stringResource(
                    R.string.flash_workflow_label,
                    if (run.runNumber > 0) "#${run.runNumber}" else "#${run.id}"
                ),
                subtitle = run.displayTitle ?: run.name.orEmpty(),
                icon = Icons.Default.FolderSpecial
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.flash_back)
                        )
                    }
                    Text(
                        text = if (cancelling) {
                            stringResource(R.string.flash_cancelling_subtitle)
                        } else {
                            stringResource(R.string.flash_building_subtitle)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Hide irrelevant categories: a manager-only build doesn't produce kernel artifacts
        // or modules, so only show "Manager artifacts" for it. Kernel / hybrid builds keep
        // all three sections.
        val isPureManager = FlashWorkflowFilter.isPureManagerBuild(run)
        val visibleCategories = if (isPureManager) {
            listOf(ArtifactCategory.MANAGER)
        } else {
            artifactCategoryOrder
        }
        // The elapsed chip rides above the first visible category tile, right-
        // aligned to mirror the tile's right edge.
        val elapsedAnchorCategory = visibleCategories.first()
        val workflowGroup = group ?: emptyWorkflowGroupFor(run, unlinkedWorkflowTitle)
        visibleCategories.forEach { category ->
            item("category-build-${category.name}") {
                WorkflowCategorySection(
                    group = workflowGroup,
                    category = category,
                    showDuration = category == elapsedAnchorCategory,
                    createdAt = run.createdAt,
                    liveDuration = true,
                    minuteHandController = minuteHandController,
                    progress = progress,
                    downloadProgress = downloadProgress,
                    autoDownload = autoDownload,
                    pendingAutoDownloadRunId = pendingAutoDownloadRunId,
                    onDownload = onDownload,
                    onCancelDownload = onCancelDownload,
                    onCancelAutoDownload = onCancelAutoDownload,
                    showDownloadCancelActions = true,
                    onCopyPath = onCopyPath,
                    onInstall = onInstall,
                    onFlash = onFlash,
                    onDelete = onDelete,
                    allowRootActions = allowRootActions,
                )
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onCancel,
                enabled = !cancelling,
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                if (cancelling) {
                    LoadingIndicator(Modifier.size(20.dp))
                } else {
                    Icon(Icons.Default.Cancel, null, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.flash_cancel_build),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

/**
 * Workflow duration chip.
 * Running workflows tick in real time from `created_at`.
 * Completed workflows freeze to `updated_at - created_at` so the same timer
 * can be reused on the finished detail screen.
 */
@Composable
internal fun BuildDurationChip(
    createdAt: String,
    finishedAt: String? = null,
    live: Boolean = finishedAt.isNullOrBlank(),
    minuteHandController: MinuteHandController? = null,
) {
    val startMillis = remember(createdAt) { parseIsoMillis(createdAt) }
    if (startMillis <= 0L) return
    val endMillis = remember(finishedAt) { parseIsoMillis(finishedAt.orEmpty()) }
    val isFinished = !live && endMillis > startMillis
    if (!live && !isFinished) return
    var currentMillis by remember(startMillis, endMillis) {
        mutableStateOf(if (isFinished) endMillis else System.currentTimeMillis())
    }
    LaunchedEffect(startMillis, endMillis) {
        if (!isFinished) {
            while (true) {
                currentMillis = System.currentTimeMillis()
                delay(100L)
            }
        }
    }
    val elapsedSec = ((currentMillis - startMillis) / 1000L).coerceAtLeast(0L)
    val h = elapsedSec / 3600
    val m = (elapsedSec % 3600) / 60
    val s = elapsedSec % 60
    val formatted = if (h > 0) {
        "%d:%02d:%02d".format(h, m, s)
    } else {
        "%02d:%02d".format(m, s)
    }
    val chipAccent = MaterialTheme.colorScheme.primary
    val chipShape = RoundedCornerShape(50)
    var localMinuteHandRotationDegrees by remember { mutableFloatStateOf(0f) }
    val controllerPhase = minuteHandController?.phase
    val useLiveIcon = when {
        minuteHandController != null ->
            controllerPhase == MinuteHandPhase.Spinning || controllerPhase == MinuteHandPhase.Settling
        else -> live
    }
    val minuteHandRotationDegrees = minuteHandController?.rotationDegrees
        ?: localMinuteHandRotationDegrees
    if (live && minuteHandController == null) {
        LaunchedEffect(Unit) {
            val startTime = withFrameMillis { it }
            while (true) {
                withFrameMillis { frameTime ->
                    val elapsed = (frameTime - startTime) % LIVE_DURATION_MINUTE_HAND_PERIOD_MS
                    localMinuteHandRotationDegrees =
                        elapsed / LIVE_DURATION_MINUTE_HAND_PERIOD_MS.toFloat() * 360f
                    frameTime
                }
            }
        }
    }
    val shimmerPhase = rememberLiveWorkflowShimmerPhase(live)
    val chipContent: @Composable () -> Unit = {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (useLiveIcon) {
                LiveDurationScheduleIcon(
                    minuteHandRotationDegrees = minuteHandRotationDegrees,
                    tint = chipAccent,
                )
            } else {
                Icon(
                    Icons.Default.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(
                text = formatted,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
    if (live) {
        Surface(
            shape = chipShape,
            color = Color.Transparent,
            contentColor = chipAccent,
        ) {
            Box(
                Modifier.drawWithCache {
                    val animatedPhase = shimmerPhase
                    val brush = liveWorkflowShimmerBrush(size, animatedPhase, chipAccent)
                    onDrawBehind { drawRect(brush) }
                },
            ) {
                chipContent()
            }
        }
    } else {
        Surface(
            shape = chipShape,
            color = uiSurfaceColor(chipAccent.copy(alpha = 0.14f)),
            contentColor = chipAccent,
        ) {
            chipContent()
        }
    }
}

internal fun parseIsoMillis(value: String): Long = runCatching {
    if (value.isBlank()) 0L else java.time.Instant.parse(value).toEpochMilli()
}.getOrDefault(0L)

@Composable
internal fun CategoryProgressCard(progress: BuildProgress?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = uiSurfaceColor(MaterialTheme.colorScheme.surfaceContainer)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LoadingIndicator(Modifier.size(20.dp))
                Text(
                    text = if (progress != null && progress.totalSteps > 0) {
                        "${progress.percent}% · ${progress.currentStep}"
                    } else {
                        stringResource(R.string.flash_building_subtitle)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            if (progress != null && progress.totalSteps > 0) {
                val animatedProgress by animateFloatAsState(
                    targetValue = (progress.percent / 100f).coerceIn(0f, 1f),
                    label = "category-progress"
                )
                ShimmerLinearProgress(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxWidth(),
                    height = 8.dp,
                )
            } else {
                ShimmerLinearProgress(
                    progress = { null },
                    modifier = Modifier.fillMaxWidth(),
                    height = 8.dp,
                )
            }
        }
    }
}

@Composable
internal fun WorkflowDetailHeader(
    group: WorkflowArtifactGroup,
    showParameterDetails: Boolean = true,
    onBack: () -> Unit,
    onShowParameters: () -> Unit,
    onDelete: () -> Unit
) {
    ExpressiveSectionCard(
        title = if (group.runId == PREBUILT_GKI_RUN_ID) {
            stringResource(R.string.flash_prebuilt_gki)
        } else {
            stringResource(
                R.string.flash_workflow_label,
                if (group.runNumber > 0) "#${group.runNumber}" else "#${group.runId}"
            )
        },
        subtitle = group.runTitle,
        icon = Icons.Default.FolderSpecial
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.flash_back))
            }
            Text(
                text = stringResource(R.string.flash_artifact_counts, group.remote.size, group.local.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            if (showParameterDetails) {
                IconButton(onClick = onShowParameters) {
                    Icon(Icons.Default.Tune, contentDescription = stringResource(R.string.flash_parameter_details))
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.flash_delete_workflow))
            }
        }
    }
}

@Composable
internal fun CategoryHeader(category: ArtifactCategory) {
    Row(
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(category.icon(), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Text(
            stringResource(category.labelRes()),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun CategoryHeaderWithDuration(
    category: ArtifactCategory,
    showDuration: Boolean,
    createdAt: String,
    finishedAt: String? = null,
    live: Boolean = finishedAt.isNullOrBlank(),
    minuteHandController: MinuteHandController? = null,
) {
    if (showDuration) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CategoryHeader(category)
            Spacer(Modifier.weight(1f))
            BuildDurationChip(
                createdAt = createdAt,
                finishedAt = finishedAt,
                live = live,
                minuteHandController = minuteHandController,
            )
        }
    } else {
        CategoryHeader(category)
    }
}

