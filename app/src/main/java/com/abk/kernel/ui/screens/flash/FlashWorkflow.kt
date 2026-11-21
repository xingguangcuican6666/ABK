@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.abk.kernel.ui.screens.flash

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.abk.kernel.R
import com.abk.kernel.data.model.ArtifactCategory
import com.abk.kernel.data.model.BuildArtifact
import com.abk.kernel.data.model.BuildParameterSummary
import com.abk.kernel.data.model.BuildProgress
import com.abk.kernel.data.model.DownloadedArtifact
import com.abk.kernel.data.model.PREBUILT_GKI_RUN_ID
import com.abk.kernel.data.model.WorkflowJob
import com.abk.kernel.data.model.WorkflowRun
import com.abk.kernel.data.model.WorkflowStep
import com.abk.kernel.ui.blur.blurredCardBackground
import com.abk.kernel.ui.blur.blurredCardSurfaceColor
import com.abk.kernel.ui.components.AbkScreenHorizontalPadding
import com.abk.kernel.ui.components.ExpressiveSectionCard
import com.abk.kernel.ui.components.LIVE_DURATION_MINUTE_HAND_PERIOD_MS
import com.abk.kernel.ui.components.LiveDurationScheduleIcon
import com.abk.kernel.ui.components.MinuteHandController
import com.abk.kernel.ui.components.MinuteHandPhase
import com.abk.kernel.ui.components.liveWorkflowShimmerBrush
import com.abk.kernel.ui.components.rememberLiveWorkflowShimmerPhase
import com.abk.kernel.ui.theme.uiSurfaceColor
import com.abk.kernel.utils.DownloadUtils
import com.abk.kernel.utils.FailureLogExtractor
import com.abk.kernel.utils.FlashWorkflowFilter
import io.github.xingguangcuican6666.abk.ui.components.AbkBadge
import io.github.xingguangcuican6666.abk.ui.components.AbkButtonStyle
import io.github.xingguangcuican6666.abk.ui.components.AbkCard
import io.github.xingguangcuican6666.abk.ui.components.AbkCluster
import io.github.xingguangcuican6666.abk.ui.components.AbkIconButton
import io.github.xingguangcuican6666.abk.ui.components.AbkLinearProgress
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcon
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcons
import io.github.xingguangcuican6666.abk.ui.theme.AbkShapes
import io.github.xingguangcuican6666.abk.ui.theme.AbkTheme
import io.github.xingguangcuican6666.abk.ui.theme.AbkTone
import kotlinx.coroutines.delay

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
    onCancel: () -> Unit,
) {
    val sourceCount = group.remote.size
    val downloadedCount = group.local.size
    val categories = artifactCategoryOrder.filter { it in group.categories }
    val kernelKind =
        if (showKernelBuildChips) {
            FlashWorkflowFilter.kernelKind(summary, dispatchedKernelVariant)
        } else {
            null
        }
    val susfsOn =
        if (showKernelBuildChips) {
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
    val title =
        if (group.runId == PREBUILT_GKI_RUN_ID) {
            stringResource(R.string.flash_prebuilt_gki)
        } else {
            stringResource(
                R.string.flash_workflow_label,
                if (group.runNumber > 0) "#${group.runNumber}" else "#${group.runId}",
            )
        }
    AbkCard(
        modifier = Modifier.fillMaxWidth().blurredCardBackground(AbkShapes.Large),
        containerColor = blurredCardSurfaceColor(AbkTheme.colors.raised),
        onClick = onClick,
    ) {
        ArtifactHeader(
            icon = if (failedGhost) AbkIcons.AlertMark else AbkIcons.Modules,
            title = title,
            subtitle = group.runTitle,
        )
        AbkCluster(Modifier.fillMaxWidth()) {
            if (active) {
                AbkBadge(
                    stringResource(
                        if (cancelling) R.string.status_cancelling
                        else R.string.flash_filter_workflow_running
                    ),
                    tone = if (cancelling) AbkTone.Warning else AbkTone.Info,
                )
            }
            if (failedGhost)
                AbkBadge(stringResource(R.string.flash_build_failed_chip), tone = AbkTone.Danger)
            if (kernelKind != null)
                AbkBadge(stringResource(kernelKind.shortLabelRes()), dot = false)
            if (dateLabel.isNotBlank()) AbkBadge(dateLabel, dot = false)
            if (susfsOn) AbkBadge(stringResource(R.string.flash_chip_susfs), dot = false)
            if (!failedGhost) {
                AbkBadge(
                    stringResource(R.string.flash_source_artifacts_count, sourceCount),
                    dot = false,
                )
                AbkBadge(
                    stringResource(R.string.flash_downloaded_count, downloadedCount),
                    dot = false,
                )
                categories.forEach { AbkBadge(stringResource(it.labelRes()), dot = false) }
            }
        }
        AbkCluster(Modifier.fillMaxWidth()) {
            // Only finished kernel workflows have a parameter summary.
            if (showParameterDetails && !active && !failedGhost) {
                AbkIconButton(
                    AbkIcons.Controls,
                    stringResource(R.string.flash_parameter_details),
                    onShowParameters,
                )
            }
            if (active) {
                FlashAction(
                    label =
                        stringResource(
                            if (cancelling) R.string.status_cancelling
                            else R.string.flash_cancel_workflow
                        ),
                    icon = AbkIcons.Close,
                    onClick = onCancel,
                    loading = cancelling,
                    style = AbkButtonStyle.Danger,
                )
            }
            AbkIconButton(
                if (failedGhost) AbkIcons.Close else AbkIcons.Trash,
                stringResource(
                    if (failedGhost) R.string.flash_dismiss_failed
                    else R.string.flash_delete_workflow
                ),
                onDelete,
            )
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

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
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
        modifier = Modifier.fillMaxWidth().blurredCardBackground(RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        color = blurredCardSurfaceColor(colorScheme.surfaceContainerHighest),
    ) {
        SelectionContainer {
            Text(
                text = displayText,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = AbkTheme.monoFont,
                color = colorScheme.onSurfaceVariant,
                modifier =
                    Modifier.fillMaxWidth()
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
        modifier =
            Modifier.fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = AbkScreenHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        item {
            ExpressiveSectionCard(
                title =
                    stringResource(
                        R.string.flash_workflow_label,
                        if (run.runNumber > 0) "#${run.runNumber}" else "#${run.id}",
                    ),
                subtitle = run.displayTitle ?: run.name.orEmpty(),
                icon = AbkIcons.AlertMark,
            ) {
                AbkCluster(Modifier.fillMaxWidth()) {
                    AbkIconButton(AbkIcons.Back, stringResource(R.string.flash_back), onBack)
                    AbkBadge(
                        stringResource(R.string.flash_build_failed_chip),
                        tone = AbkTone.Danger,
                    )
                }
                Text(
                    stringResource(R.string.flash_failed_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = AbkTheme.colors.inkMuted,
                )
            }
        }

        item {
            ExpressiveSectionCard(
                title = stringResource(R.string.flash_failed_step_list_title),
                icon = AbkIcons.Build,
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
                            FlashAction(stringResource(R.string.retry), AbkIcons.Refresh, onRetryJobs)
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
                icon = AbkIcons.Terminal,
            ) {
                when {
                    logLoading -> LoadingRow(stringResource(R.string.flash_loading_steps))
                    else -> {
                        val excerpt =
                            logExcerpt?.takeIf { it.isNotBlank() }
                                ?: stringResource(R.string.flash_build_error_unavailable)
                        BuildErrorLogPanel(text = excerpt)
                    }
                }
            }
        }

        item {
            FlashAction(
                stringResource(R.string.flash_open_github_actions),
                AbkIcons.ArrowRight,
                onOpenGitHub,
            )
        }
    }
}

@Composable
internal fun FailedWorkflowStepRow(step: WorkflowStep, failed: Boolean, muted: Boolean) {
    val alpha = if (muted) 0.45f else 1f
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .graphicsLayer { this.alpha = alpha }
                .background(AbkTheme.colors.sunken, AbkShapes.Medium)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val completed = step.conclusion == "success" || step.status == "completed"
        AbkIcon(
            when {
                failed -> AbkIcons.AlertMark
                completed -> AbkIcons.Check
                else -> AbkIcons.Pending
            },
            contentDescription = if (failed) null else step.conclusion ?: step.status,
            tint = if (failed) AbkTheme.colors.danger.color else AbkTheme.colors.inkMuted,
            size = 18.dp,
        )
        Column(Modifier.weight(1f)) {
            Text(step.name, style = MaterialTheme.typography.bodySmall)
            if (failed && !step.conclusion.isNullOrBlank()) {
                Text(
                    stringResource(R.string.flash_build_failed_chip),
                    style = MaterialTheme.typography.labelSmall,
                    color = AbkTheme.colors.danger.color,
                )
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
    val remoteInCategory =
        group.remote.filter {
            DownloadUtils.classifyCategory(DownloadUtils.classifyArtifact(it.name)) == category
        }
    val matchedLocalPaths =
        remoteInCategory
            .flatMap { source ->
                group.local.filter { DownloadUtils.matchesDownloadedArtifact(it, source) }
            }
            .map { it.filePath }
            .toSet()
    val localOnly =
        group.local.filter { it.category == category && it.filePath !in matchedLocalPaths }
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
                    downloadedFiles =
                        group.local.filter {
                            DownloadUtils.matchesDownloadedArtifact(it, artifact)
                        },
                    progress = downloadProgress[artifact.id],
                    autoDownloadEligible =
                        autoDownload &&
                            pendingAutoDownloadRunId == artifact.runId &&
                            DownloadUtils.shouldAutoDownload(artifact),
                    pendingAutoDownload = pendingAutoDownloadRunId == artifact.runId,
                    showDownloadCancelActions = showDownloadCancelActions,
                    onDownload = { onDownload(artifact) },
                    onCancelDownload =
                        if (showDownloadCancelActions) {
                            { onCancelDownload(artifact.id) }
                        } else {
                            null
                        },
                    onCancelAutoDownload =
                        if (showDownloadCancelActions) {
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
        modifier =
            Modifier.fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = AbkScreenHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        item {
            // Header mirrors WorkflowDetailHeader visually (same ExpressiveSectionCard +
            // back-button row), but without artifact-counts / parameters / delete actions.
            ExpressiveSectionCard(
                title =
                    stringResource(
                        R.string.flash_workflow_label,
                        if (run.runNumber > 0) "#${run.runNumber}" else "#${run.id}",
                    ),
                subtitle = run.displayTitle ?: run.name.orEmpty(),
                icon = AbkIcons.Modules,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AbkIconButton(AbkIcons.Back, stringResource(R.string.flash_back), onBack)
                    Text(
                        text =
                            if (cancelling) {
                                stringResource(R.string.flash_cancelling_subtitle)
                            } else {
                                stringResource(R.string.flash_building_subtitle)
                            },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // Hide irrelevant categories: a manager-only build doesn't produce kernel artifacts
        // or modules, so only show "Manager artifacts" for it. Kernel / hybrid builds keep
        // all three sections.
        val isPureManager = FlashWorkflowFilter.isPureManagerBuild(run)
        val visibleCategories =
            if (isPureManager) {
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
            FlashAction(
                label =
                    stringResource(
                        if (cancelling) R.string.status_cancelling else R.string.flash_cancel_build
                    ),
                icon = AbkIcons.Close,
                onClick = onCancel,
                loading = cancelling,
                style = AbkButtonStyle.Danger,
            )
        }
    }
}

/**
 * Workflow duration chip. Running workflows tick in real time from `created_at`. Completed
 * workflows freeze to `updated_at - created_at` so the same timer can be reused on the finished
 * detail screen.
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
    var currentMillis by
        remember(startMillis, endMillis) {
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
    val formatted =
        if (h > 0) {
            "%d:%02d:%02d".format(h, m, s)
        } else {
            "%02d:%02d".format(m, s)
        }
    val chipAccent = MaterialTheme.colorScheme.primary
    val chipShape = RoundedCornerShape(50)
    var localMinuteHandRotationDegrees by remember { mutableFloatStateOf(0f) }
    val controllerPhase = minuteHandController?.phase
    val useLiveIcon =
        when {
            minuteHandController != null ->
                controllerPhase == MinuteHandPhase.Spinning ||
                    controllerPhase == MinuteHandPhase.Settling
            else -> live
        }
    val minuteHandRotationDegrees =
        minuteHandController?.rotationDegrees ?: localMinuteHandRotationDegrees
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
                Icon(AbkIcons.Pending, contentDescription = null, modifier = Modifier.size(14.dp))
            }
            Text(
                text = formatted,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
    if (live) {
        Surface(shape = chipShape, color = Color.Transparent, contentColor = chipAccent) {
            Box(
                Modifier.drawWithCache {
                    val animatedPhase = shimmerPhase
                    val brush = liveWorkflowShimmerBrush(size, animatedPhase, chipAccent)
                    onDrawBehind { drawRect(brush) }
                }
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

internal fun parseIsoMillis(value: String): Long =
    runCatching { if (value.isBlank()) 0L else java.time.Instant.parse(value).toEpochMilli() }
        .getOrDefault(0L)

@Composable
internal fun CategoryProgressCard(progress: BuildProgress?) {
    val hasProgress = progress != null && progress.totalSteps > 0
    val label =
        if (hasProgress && progress != null) {
            "${progress.percent}% · ${progress.currentStep}"
        } else {
            stringResource(R.string.flash_building_subtitle)
        }
    AbkCard(
        modifier = Modifier.fillMaxWidth().blurredCardBackground(AbkShapes.Large),
        containerColor = blurredCardSurfaceColor(AbkTheme.colors.raised),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        AbkLinearProgress(
            progress =
                progress
                    ?.takeIf { it.totalSteps > 0 }
                    ?.let { (it.percent / 100f).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
            label = label,
        )
    }
}

@Composable
internal fun WorkflowDetailHeader(
    group: WorkflowArtifactGroup,
    showParameterDetails: Boolean = true,
    onBack: () -> Unit,
    onShowParameters: () -> Unit,
    onDelete: () -> Unit,
) {
    ExpressiveSectionCard(
        title =
            if (group.runId == PREBUILT_GKI_RUN_ID) {
                stringResource(R.string.flash_prebuilt_gki)
            } else {
                stringResource(
                    R.string.flash_workflow_label,
                    if (group.runNumber > 0) "#${group.runNumber}" else "#${group.runId}",
                )
            },
        subtitle = group.runTitle,
        icon = AbkIcons.Modules,
    ) {
        Text(
            stringResource(R.string.flash_artifact_counts, group.remote.size, group.local.size),
            style = MaterialTheme.typography.bodySmall,
            color = AbkTheme.colors.inkMuted,
        )
        AbkCluster(Modifier.fillMaxWidth()) {
            AbkIconButton(AbkIcons.Back, stringResource(R.string.flash_back), onBack)
            if (showParameterDetails) {
                AbkIconButton(
                    AbkIcons.Controls,
                    stringResource(R.string.flash_parameter_details),
                    onShowParameters,
                )
            }
            AbkIconButton(AbkIcons.Trash, stringResource(R.string.flash_delete_workflow), onDelete)
        }
    }
}

@Composable
internal fun CategoryHeader(category: ArtifactCategory) {
    Row(
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            category.icon(),
            null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Text(
            stringResource(category.labelRes()),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        AbkCluster(Modifier.fillMaxWidth()) {
            CategoryHeader(category)
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
