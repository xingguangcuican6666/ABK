package com.abk.kernel.ui.flash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.abk.kernel.R
import com.abk.kernel.data.model.PrebuiltGkiRelease
import com.abk.kernel.ui.common.AbkPage
import com.abk.kernel.ui.common.MutedText
import com.abk.kernel.ui.common.StatusBadge
import com.abk.kernel.utils.DownloadUtils
import com.abk.kernel.viewmodel.MainUiState
import com.abk.kernel.viewmodel.MainViewModel
import io.github.xingguangcuican6666.abk.ui.components.AbkButton
import io.github.xingguangcuican6666.abk.ui.components.AbkButtonStyle
import io.github.xingguangcuican6666.abk.ui.components.AbkCard
import io.github.xingguangcuican6666.abk.ui.components.AbkDivider
import io.github.xingguangcuican6666.abk.ui.components.AbkEmptyState
import io.github.xingguangcuican6666.abk.ui.components.AbkHeading
import io.github.xingguangcuican6666.abk.ui.components.AbkIconButton
import io.github.xingguangcuican6666.abk.ui.components.AbkLinearProgress
import io.github.xingguangcuican6666.abk.ui.components.AbkList
import io.github.xingguangcuican6666.abk.ui.components.AbkListItem
import io.github.xingguangcuican6666.abk.ui.components.AbkSize
import io.github.xingguangcuican6666.abk.ui.components.AbkTabs
import io.github.xingguangcuican6666.abk.ui.components.AbkTabItem
import io.github.xingguangcuican6666.abk.ui.components.AbkText
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcons
import io.github.xingguangcuican6666.abk.ui.theme.AbkSpacing
import io.github.xingguangcuican6666.abk.ui.theme.AbkTone

private const val TAB_PREBUILT = "prebuilt"
private const val TAB_DOWNLOADED = "downloaded"

/**
 * 刷写：预编译 GKI Release 下载与本地已下载文件。
 * Workflow artifact grouping and flash execution return in the next phase.
 */
@Composable
fun FlashDestination(
    vm: MainViewModel,
    state: MainUiState,
    onChildPageVisibleChange: (Boolean) -> Unit,
) {
    LaunchedEffect(Unit) {
        onChildPageVisibleChange(false)
        vm.loadPrebuiltGkiReleases()
    }
    var tab by rememberSaveable { mutableStateOf(TAB_PREBUILT) }
    var expandedReleaseId by rememberSaveable { mutableStateOf<Long?>(null) }

    AbkPage(
        title = stringResource(R.string.flash_title),
        eyebrow = stringResource(R.string.nav_flash),
        description = stringResource(
            if (state.rootGranted) R.string.flash_artifact_center_desc else R.string.flash_file_center_desc
        ),
        topBarActions = {
            AbkIconButton(
                icon = AbkIcons.Refresh,
                contentDescription = stringResource(R.string.refresh),
                onClick = { vm.loadPrebuiltGkiReleases(force = true) },
                enabled = !state.isLoadingPrebuiltGkiReleases,
                size = AbkSize.Small,
            )
        },
    ) {
        AbkTabs(
            items = listOf(
                AbkTabItem(TAB_PREBUILT, stringResource(R.string.flash_prebuilt_gki), state.prebuiltGkiReleases.size),
                AbkTabItem(TAB_DOWNLOADED, stringResource(R.string.flash_files_title), state.downloadedArtifacts.size),
            ),
            selectedId = tab,
            onSelect = { tab = it },
        )

        when (tab) {
            TAB_PREBUILT -> PrebuiltSection(
                state = state,
                expandedReleaseId = expandedReleaseId,
                onToggleRelease = { id, release ->
                    expandedReleaseId = if (expandedReleaseId == id) null else id
                    if (expandedReleaseId == id) vm.loadPrebuiltGkiAssets(release)
                },
                onDownload = { vm.downloadPrebuiltGki(it) },
            )
            else -> DownloadedSection(state)
        }

        if (state.activeDownloadTasks.isNotEmpty()) {
            AbkCard {
                AbkHeading(stringResource(R.string.flash_download_tasks_title))
                MutedText(stringResource(R.string.flash_download_tasks_desc))
                state.activeDownloadTasks.forEach { task ->
                    Column(verticalArrangement = Arrangement.spacedBy(AbkSpacing.Xs)) {
                        AbkText(task.name, style = MaterialTheme.typography.bodyMedium)
                        AbkLinearProgress(
                            progress = task.progress / 100f,
                            tone = AbkTone.Accent,
                            label = stringResource(R.string.flash_download_progress, task.progress),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PrebuiltSection(
    state: MainUiState,
    expandedReleaseId: Long?,
    onToggleRelease: (Long, PrebuiltGkiRelease) -> Unit,
    onDownload: (com.abk.kernel.data.model.PrebuiltGkiAsset) -> Unit,
) {
    if (!state.prebuiltGkiEnabled) {
        AbkEmptyState(
            title = stringResource(R.string.flash_prebuilt_disabled_title),
            description = stringResource(R.string.flash_prebuilt_disabled_desc),
            icon = AbkIcons.Download,
        )
        return
    }
    if (state.prebuiltGkiReleases.isEmpty()) {
        AbkEmptyState(
            title = stringResource(R.string.flash_empty_prebuilt_title),
            description = stringResource(R.string.flash_empty_prebuilt_desc),
            icon = AbkIcons.Download,
        )
        return
    }
    MutedText(stringResource(R.string.flash_prebuilt_list_desc, state.prebuiltGkiReleases.size))
    state.prebuiltGkiReleases.forEach { release ->
        val expanded = expandedReleaseId == release.id
        AbkCard(onClick = { onToggleRelease(release.id, release) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    AbkText(release.name.ifBlank { release.tagName }, style = MaterialTheme.typography.titleMedium)
                    MutedText(release.publishedAt)
                }
                StatusBadge(AbkTone.Neutral, stringResource(R.string.flash_asset_count, release.assetCount))
            }
            if (expanded) {
                val assets = state.prebuiltGkiAssetsByReleaseId[release.id]
                val loading = release.id in state.loadingPrebuiltGkiAssetReleaseIds
                when {
                    loading -> MutedText(stringResource(R.string.flash_loading_prebuilt, release.tagName))
                    assets.isNullOrEmpty() -> MutedText(stringResource(R.string.flash_no_matching_assets))
                    else -> AbkList(plain = true) {
                        assets.forEachIndexed { index, asset ->
                            if (index > 0) AbkDivider()
                            AbkListItem(
                                title = asset.name,
                                description = DownloadUtils.formatSize(asset.sizeBytes),
                                trailing = {
                                    val progress = state.downloadProgress[asset.id]
                                    if (progress != null && progress < 100) {
                                        AbkText(stringResource(R.string.flash_download_progress, progress))
                                    } else {
                                        AbkButton(
                                            onClick = { onDownload(asset) },
                                            style = AbkButtonStyle.Soft,
                                            size = AbkSize.Small,
                                        ) { AbkText(stringResource(R.string.flash_download)) }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadedSection(state: MainUiState) {
    val files = state.downloadedArtifacts
    if (files.isEmpty()) {
        AbkEmptyState(
            title = stringResource(R.string.settings_clear_artifacts_empty),
            description = stringResource(R.string.flash_empty_files_desc),
            icon = AbkIcons.Download,
        )
        return
    }
    MutedText(stringResource(R.string.flash_downloaded_count, files.size))
    AbkList {
        files.forEachIndexed { index, file ->
            if (index > 0) AbkDivider()
            AbkListItem(
                title = file.name,
                description = DownloadUtils.formatSize(file.sizeBytes),
                trailing = {
                    StatusBadge(
                        tone = if (file.verified) AbkTone.Success else AbkTone.Warning,
                        label = if (file.verified) stringResource(R.string.status_success)
                        else stringResource(R.string.status_pending_check),
                    )
                },
            )
        }
    }
}
