package com.abk.kernel.ui.modules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
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
import com.abk.kernel.ui.common.AbkPage
import com.abk.kernel.ui.common.MutedText
import com.abk.kernel.viewmodel.MainUiState
import com.abk.kernel.viewmodel.MainViewModel
import io.github.xingguangcuican6666.abk.ui.components.AbkAlert
import io.github.xingguangcuican6666.abk.ui.components.AbkBadge
import io.github.xingguangcuican6666.abk.ui.components.AbkCard
import io.github.xingguangcuican6666.abk.ui.components.AbkChip
import io.github.xingguangcuican6666.abk.ui.components.AbkDivider
import io.github.xingguangcuican6666.abk.ui.components.AbkEmptyState
import io.github.xingguangcuican6666.abk.ui.components.AbkHeading
import io.github.xingguangcuican6666.abk.ui.components.AbkIconButton
import io.github.xingguangcuican6666.abk.ui.components.AbkList
import io.github.xingguangcuican6666.abk.ui.components.AbkListItem
import io.github.xingguangcuican6666.abk.ui.components.AbkSize
import io.github.xingguangcuican6666.abk.ui.components.AbkText
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcons
import io.github.xingguangcuican6666.abk.ui.theme.AbkSpacing
import io.github.xingguangcuican6666.abk.ui.theme.AbkTheme
import io.github.xingguangcuican6666.abk.ui.theme.AbkTone

/**
 * 模块：中央仓库汇总视图。选一个仓库可缩小列表；新增/删除中央仓库的
 * 管理页在下一阶段接入。
 */
@Composable
fun ModulesDestination(
    vm: MainViewModel,
    state: MainUiState,
    pendingModuleInstallUri: String? = null,
    onPendingModuleInstallUriConsumed: () -> Unit = {},
) {
    val repositories = state.buildModuleRepositories
    var selectedRepoId by rememberSaveable { mutableStateOf<String?>(null) }

    // A shared .zip intent switches to this page; the install flow arrives next phase.
    LaunchedEffect(pendingModuleInstallUri) {
        if (!pendingModuleInstallUri.isNullOrBlank()) {
            onPendingModuleInstallUriConsumed()
        }
    }
    if (!pendingModuleInstallUri.isNullOrBlank()) {
        AbkAlert(
            title = stringResource(R.string.modules_pending_zip),
            message = pendingModuleInstallUri,
            tone = AbkTone.Info,
        )
    }

    val visible = if (selectedRepoId == null) repositories else repositories.filter { it.id == selectedRepoId }
    val moduleCount = visible.sumOf { it.modules.size }

    AbkPage(
        title = stringResource(R.string.module_repo_title),
        eyebrow = stringResource(R.string.nav_modules),
        description = stringResource(R.string.module_repo_central_desc),
        topBarActions = {
            AbkIconButton(
                icon = AbkIcons.Refresh,
                contentDescription = stringResource(R.string.refresh_all),
                onClick = { repositories.forEach { vm.refreshBuildModuleRepository(it.id) } },
                enabled = state.refreshingBuildModuleRepositoryIds.isEmpty(),
                size = AbkSize.Small,
            )
        },
    ) {
        if (repositories.isEmpty()) {
            AbkEmptyState(
                title = stringResource(R.string.module_repo_empty_title),
                description = stringResource(R.string.module_repo_empty_desc),
                icon = AbkIcons.Modules,
            )
            return@AbkPage
        }

        FlowRow(
            Modifier,
            horizontalArrangement = Arrangement.spacedBy(AbkSpacing.Sm),
            verticalArrangement = Arrangement.spacedBy(AbkSpacing.Sm),
        ) {
            AbkChip(
                label = stringResource(R.string.module_repo_all_stages),
                selected = selectedRepoId == null,
                onSelectedChange = { if (it) selectedRepoId = null },
            )
            repositories.forEach { repo ->
                AbkChip(
                    label = repo.name.ifBlank { repo.id },
                    selected = selectedRepoId == repo.id,
                    onSelectedChange = { selected -> selectedRepoId = if (selected) repo.id else null },
                )
            }
        }

        MutedText(stringResource(R.string.module_repo_module_count, moduleCount))

        visible.forEach { repo ->
            AbkCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AbkHeading(repo.name.ifBlank { repo.id }, Modifier.weight(1f))
                    when {
                        repo.error != null ->
                            AbkBadge(stringResource(R.string.settings_unknown), tone = AbkTone.Warning)
                        repo.id in state.refreshingBuildModuleRepositoryIds ->
                            AbkBadge(stringResource(R.string.module_repo_refreshing_repository), tone = AbkTone.Accent)
                    }
                }
                MutedText(repo.url)
                repo.error?.let {
                    AbkText(it, style = MaterialTheme.typography.bodySmall, color = AbkTheme.colors.status(AbkTone.Danger).color)
                }
                if (repo.modules.isEmpty()) {
                    MutedText(stringResource(R.string.module_repo_no_display))
                } else {
                    AbkList(plain = true) {
                        repo.modules.forEachIndexed { index, module ->
                            if (index > 0) AbkDivider()
                            AbkListItem(
                                title = module.name,
                                description = module.author.takeIf { it.isNotBlank() }
                                    ?.let { stringResource(R.string.runtime_module_author, it) },
                                trailing = {
                                    AbkBadge(
                                        module.version.takeIf { it.isNotBlank() } ?: "-",
                                        tone = AbkTone.Neutral,
                                        dot = false,
                                    )
                                },
                            )
                        }
                        if (repo.skippedCount > 0) {
                            MutedText(stringResource(R.string.module_repo_skipped_count, repo.skippedCount))
                        }
                    }
                }
            }
        }
    }
}
