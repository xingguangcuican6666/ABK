package com.abk.kernel.miuix.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.abk.kernel.R
import com.abk.kernel.miuix.component.MiuixTextInputDialog
import com.abk.kernel.ui.navigation3.LocalNavigator
import com.abk.kernel.utils.LocaleHelper
import com.abk.kernel.viewmodel.MainViewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

// ─────────────────────────────────────────────────────────────────────────────
// MIUIX-styled build module repository settings sub-page.
// Navigated to via Navigation3 (Route.BuildModuleRepoSettings) from the
// build module repository screen.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun BuildModuleRepoSettingsScreenMiuix(vm: MainViewModel) {
    val navigator = LocalNavigator.current
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current
    val scrollBehavior = MiuixScrollBehavior()
    var repositoryUrl by rememberSaveable { mutableStateOf("") }
    var showAddRepositoryDialog by rememberSaveable { mutableStateOf(false) }

    if (showAddRepositoryDialog) {
        MiuixTextInputDialog(
            show = true,
            title = buildRepoCentralLabelMiuix(context),
            message = buildRepoCentralDescLabelMiuix(context),
            value = repositoryUrl,
            cancelText = stringResource(android.R.string.cancel),
            confirmText = stringResource(R.string.add),
            confirmEnabled = { it.isNotBlank() },
            onDismiss = { showAddRepositoryDialog = false },
            onConfirm = { url ->
                vm.addBuildModuleRepository(url.trim())
                repositoryUrl = ""
                showAddRepositoryDialog = false
                vm.showSnackbar(context.getString(R.string.add))
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = buildRepoCentralLabelMiuix(context),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = { navigator.pop() }) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = stringResource(R.string.settings_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .overScrollVertical()
                .scrollEndHaptic(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // ── Add Repository Section ───────────────────────────────────────
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Section header: icon + title + description
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Dns,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = buildRepoCentralLabelMiuix(context),
                                style = MiuixTheme.textStyles.title4,
                                color = MiuixTheme.colorScheme.onSurface
                            )
                            Text(
                                text = buildRepoCentralDescLabelMiuix(context),
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                            )
                        }
                    }

                    ArrowPreference(
                        title = buildRepoUrlLabelMiuix(context),
                        summary = buildRepoCentralDescLabelMiuix(context),
                        startAction = {
                            Icon(
                                imageVector = Icons.Default.Dns,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        },
                        onClick = {
                            repositoryUrl = ""
                            showAddRepositoryDialog = true
                        },
                    )

                    // Action buttons row
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { vm.refreshAllBuildModuleRepositories() },
                            enabled = state.buildModuleRepositories.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.refresh_all))
                        }
                    }
                }
            }

            // ── Repository List ──────────────────────────────────────────────
            if (state.buildModuleRepositories.isEmpty()) {
                // Empty state
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 28.dp, horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Extension,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.size(38.dp)
                        )
                        Text(
                            text = buildRepoEmptyTitleLabelMiuix(context),
                            style = MiuixTheme.textStyles.subtitle,
                            color = MiuixTheme.colorScheme.onSurface
                        )
                        Text(
                            text = buildRepoCentralDescLabelMiuix(context),
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                    }
                }
            } else {
                state.buildModuleRepositories.forEach { repo ->
                    val isRefreshing = repo.id in state.refreshingBuildModuleRepositoryIds
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Title row: icon + name + URL subtitle
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Dns,
                                    contentDescription = null,
                                    tint = MiuixTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = repo.name.ifBlank { repo.url },
                                        style = MiuixTheme.textStyles.title4,
                                        color = MiuixTheme.colorScheme.onSurface,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = repo.url,
                                        style = MiuixTheme.textStyles.body2,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // Status: module count & skipped count
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(
                                        R.string.module_repo_module_count,
                                        repo.modules.size
                                    ),
                                    style = MiuixTheme.textStyles.body2,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MiuixTheme.colorScheme.primary
                                )
                                if (repo.skippedCount > 0) {
                                    Text(
                                        text = stringResource(
                                            R.string.module_repo_skipped_count,
                                            repo.skippedCount
                                        ),
                                        style = MiuixTheme.textStyles.body2,
                                        color = MiuixTheme.colorScheme.error
                                    )
                                }
                            }

                            // Error text if present
                            repo.error?.let { error ->
                                Text(
                                    text = error,
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.error
                                )
                            }

                            // Index URL
                            if (repo.indexJsonUrl.isNotBlank()) {
                                Text(
                                    text = repo.indexJsonUrl,
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Action buttons: Refresh + Delete
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { vm.refreshBuildModuleRepository(repo.id) },
                                    enabled = !isRefreshing,
                                    colors = ButtonDefaults.buttonColors()
                                ) {
                                    if (isRefreshing) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            progress = null,
                                            size = 16.dp,
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.refresh))
                                }
                                Spacer(Modifier.width(8.dp))
                                Button(
                                    onClick = { vm.deleteBuildModuleRepository(repo.id) },
                                    colors = ButtonDefaults.buttonColors(
                                        color = MiuixTheme.colorScheme.error,
                                        contentColor = Color.White
                                    )
                                ) {
                                    Text(stringResource(R.string.delete))
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(80.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Private locale label functions (local copies of private helpers from
// ModuleRepositoryScreenMiuix.kt, suffixed with Miuix to avoid conflicts)
// ─────────────────────────────────────────────────────────────────────────────

private fun buildRepoCentralLabelMiuix(context: Context): String =
    when (LocaleHelper.getLanguage(context)) {
        LocaleHelper.LANG_ZH -> "ABK 模块中央仓库"
        LocaleHelper.LANG_RU -> "Центральный репозиторий модулей ABK"
        else -> "ABK module central repository"
    }

private fun buildRepoCentralDescLabelMiuix(context: Context): String =
    when (LocaleHelper.getLanguage(context)) {
        LocaleHelper.LANG_ZH -> "添加包含 abk-modules.json 的 ABK 模块仓库。"
        LocaleHelper.LANG_RU -> "Добавьте репозитории модулей ABK, содержащие abk-modules.json."
        else -> "Add ABK module repositories that contain abk-modules.json."
    }

private fun buildRepoEmptyTitleLabelMiuix(context: Context): String =
    when (LocaleHelper.getLanguage(context)) {
        LocaleHelper.LANG_ZH -> "暂无 ABK 模块仓库"
        LocaleHelper.LANG_RU -> "Нет репозиториев модулей ABK"
        else -> "No ABK module repositories"
    }

private fun buildRepoUrlLabelMiuix(context: Context): String =
    when (LocaleHelper.getLanguage(context)) {
        LocaleHelper.LANG_ZH -> "ABK 模块仓库链接"
        LocaleHelper.LANG_RU -> "Ссылка на репозиторий модулей ABK"
        else -> "ABK module repository URL"
    }
