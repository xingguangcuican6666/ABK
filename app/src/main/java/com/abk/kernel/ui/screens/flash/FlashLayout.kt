@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.abk.kernel.ui.screens.flash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.abk.kernel.R
import com.abk.kernel.data.model.ActiveDownloadTask
import com.abk.kernel.data.model.BuildStatus
import com.abk.kernel.ui.components.AppPageBackground
import com.abk.kernel.ui.components.ExpressiveHeroCard
import com.abk.kernel.ui.components.ExpressiveSectionCard
import com.abk.kernel.ui.components.rememberChildPageBackController
import io.github.xingguangcuican6666.abk.ui.components.AbkBadge
import io.github.xingguangcuican6666.abk.ui.components.AbkDivider
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcons
import io.github.xingguangcuican6666.abk.ui.theme.AbkTheme
import io.github.xingguangcuican6666.abk.ui.theme.AbkTone

@Composable
internal fun FlashDetailBackSurface(
    predictiveBackEnabled: Boolean,
    outerPadding: PaddingValues,
    backgroundUri: String?,
    backgroundImageEnabled: Boolean,
    onBack: () -> Unit,
    backgroundContent: @Composable () -> Unit,
    content: @Composable (dismiss: () -> Unit) -> Unit,
) {
    val back =
        rememberChildPageBackController(
            enabled = true,
            predictiveBackEnabled = predictiveBackEnabled,
            onBack = onBack,
        )

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val childPageTopInset = outerPadding.calculateTopPadding()
        val childPageBottomInset = outerPadding.calculateBottomPadding()
        val childPageModifier =
            Modifier.fillMaxWidth()
                .height(maxHeight + childPageTopInset + childPageBottomInset)
                .offset(y = -childPageTopInset)
        backgroundContent()
        Box(childPageModifier.background(Color.Black.copy(alpha = back.scrimAlpha)))
        Box(modifier = childPageModifier.then(back.backTransformModifier())) {
            FlashDetailPageBackground(
                backgroundUri = backgroundUri,
                backgroundImageEnabled = backgroundImageEnabled,
            )
            content(back::requestDismiss)
        }
    }
}

@Composable
internal fun FlashDetailPageBackground(backgroundUri: String?, backgroundImageEnabled: Boolean) {
    AppPageBackground(
        backgroundUri = backgroundUri,
        backgroundImageEnabled = backgroundImageEnabled,
    )
}

@Composable
internal fun FlashHero(
    buildStatus: BuildStatus,
    availableCount: Int,
    downloadedCount: Int,
    rootGranted: Boolean,
) {
    ExpressiveHeroCard(
        title =
            stringResource(
                if (rootGranted) R.string.flash_artifact_center else R.string.flash_file_center
            ),
        subtitle =
            stringResource(
                if (rootGranted) R.string.flash_artifact_center_desc
                else R.string.flash_file_center_desc
            ),
        icon = if (rootGranted) AbkIcons.Cpu else AbkIcons.Modules,
        badge = {
            AbkBadge(
                stringResource(R.string.flash_source_artifacts_count, availableCount),
                dot = false,
            )
            AbkBadge(stringResource(R.string.flash_downloaded_count, downloadedCount), dot = false)
            when (buildStatus) {
                BuildStatus.SUCCESS ->
                    AbkBadge(stringResource(R.string.build_success_bang), tone = AbkTone.Success)
                BuildStatus.FAILURE ->
                    AbkBadge(stringResource(R.string.build_failed), tone = AbkTone.Danger)
                BuildStatus.CANCELLED -> AbkBadge(stringResource(R.string.build_cancelled))
                BuildStatus.IDLE -> AbkBadge(stringResource(R.string.flash_build_waiting))
                else -> Unit // Active builds are signalled on the corresponding workflow card.
            }
        },
    )
}

@Composable
internal fun WorkflowDownloadManagementCard(
    tasks: List<ActiveDownloadTask>,
    pendingRunId: Long?,
    pendingRunLabel: String?,
    onCancelTask: (Long) -> Unit,
    onCancelPending: (Long) -> Unit,
) {
    if (tasks.isEmpty() && pendingRunId == null) return
    ExpressiveSectionCard(
        title = stringResource(R.string.flash_download_tasks_title),
        subtitle = stringResource(R.string.flash_download_tasks_desc),
        icon = AbkIcons.Download,
    ) {
        if (pendingRunId != null) {
            Text(pendingRunLabel ?: "#$pendingRunId", style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.flash_download_waiting_auto),
                style = MaterialTheme.typography.bodySmall,
                color = AbkTheme.colors.inkMuted,
            )
            FlashAction(
                stringResource(R.string.flash_stop_auto_download),
                AbkIcons.Close,
                { onCancelPending(pendingRunId) },
            )
        }
        tasks.forEachIndexed { index, task ->
            if (index > 0 || pendingRunId != null) AbkDivider()
            ArtifactHeader(
                icon = AbkIcons.Download,
                title = task.name,
                subtitle = workflowTaskLabel(task),
                chip =
                    stringResource(
                        if (task.automatic) R.string.flash_auto_download_badge
                        else R.string.flash_manual_download_badge
                    ),
            )
            FlashDownloadProgress(task.progress)
            FlashAction(
                stringResource(R.string.flash_cancel_download),
                AbkIcons.Close,
                { onCancelTask(task.key) },
            )
        }
    }
}
