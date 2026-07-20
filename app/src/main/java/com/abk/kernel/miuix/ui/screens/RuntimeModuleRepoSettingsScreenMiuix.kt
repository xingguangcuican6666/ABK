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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.abk.kernel.R
import com.abk.kernel.data.model.RuntimeModuleRepository
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
// Entry point
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun RuntimeModuleRepoSettingsScreenMiuix(vm: MainViewModel) {
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val scrollBehavior = MiuixScrollBehavior()
    var repositoryUrl by rememberSaveable { mutableStateOf("") }
    var showAddRepositoryDialog by rememberSaveable { mutableStateOf(false) }
    var refreshingAll by remember { mutableStateOf(false) }
    LaunchedEffect(state.refreshingRuntimeModuleRepositoryIds) {
        if (refreshingAll && state.refreshingRuntimeModuleRepositoryIds.isEmpty()) {
            refreshingAll = false
        }
    }

    if (showAddRepositoryDialog) {
        MiuixTextInputDialog(
            show = true,
            title = runtimeRepoCentralLabelMiuix(context),
            message = runtimeRepoCentralDescLabelMiuix(context),
            value = repositoryUrl,
            cancelText = stringResource(android.R.string.cancel),
            confirmText = stringResource(R.string.add),
            confirmEnabled = { it.isNotBlank() },
            onDismiss = { showAddRepositoryDialog = false },
            onConfirm = { url ->
                vm.addRuntimeModuleRepository(url.trim())
                repositoryUrl = ""
                showAddRepositoryDialog = false
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = runtimeRepoCentralLabelMiuix(context),
                navigationIcon = {
                    IconButton(onClick = { navigator.pop() }) {
                        Icon(
                            MiuixIcons.Back,
                            contentDescription = stringResource(R.string.settings_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
                .overScrollVertical()
                .scrollEndHaptic()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // ── Add repository card ─────────────────────────────────────

            Card {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Title row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Dns,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = runtimeRepoCentralLabelMiuix(context),
                                style = MiuixTheme.textStyles.title4,
                                fontWeight = FontWeight.SemiBold,
                                color = MiuixTheme.colorScheme.onSurface
                            )
                            Text(
                                text = runtimeRepoCentralDescLabelMiuix(context),
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                            )
                        }
                    }

                    ArrowPreference(
                        title = runtimeRepoUrlLabelMiuix(context),
                        summary = runtimeRepoCentralDescLabelMiuix(context),
                        startAction = {
                            Icon(
                                imageVector = Icons.Default.Link,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        },
                        onClick = {
                            repositoryUrl = ""
                            showAddRepositoryDialog = true
                        },
                    )

                    // Action buttons
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                refreshingAll = true
                                vm.refreshAllRuntimeModuleRepositories()
                            },
                            enabled = state.runtimeModuleRepositories.isNotEmpty() && !refreshingAll,
                            colors = ButtonDefaults.buttonColors(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (refreshingAll) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(17.dp),
                                    progress = null,
                                    size = 17.dp,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.refresh_all))
                        }
                    }
                }
            }

            // ── Repository list / empty state ───────────────────────────

            if (state.runtimeModuleRepositories.isEmpty()) {
                Card {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 28.dp, horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Extension,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.size(38.dp)
                        )
                        Text(
                            text = runtimeRepoEmptyTitleLabelMiuix(context),
                            style = MiuixTheme.textStyles.subtitle,
                            color = MiuixTheme.colorScheme.onSurface
                        )
                        Text(
                            text = runtimeRepoCentralDescLabelMiuix(context),
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                    }
                }
            } else {
                state.runtimeModuleRepositories.forEach { repository ->
                    key(repository.id) {
                        RuntimeModuleRepositoryCardMiuix(
                            repository = repository,
                            refreshing = repository.id in state.refreshingRuntimeModuleRepositoryIds && !refreshingAll,
                            onRefresh = { vm.refreshRuntimeModuleRepository(repository.id) },
                            onDelete = { vm.deleteRuntimeModuleRepository(repository.id) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(80.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Per-repository card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RuntimeModuleRepositoryCardMiuix(
    repository: RuntimeModuleRepository,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onDelete: () -> Unit
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Title row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Dns,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = repository.name.ifBlank { repository.url },
                        style = MiuixTheme.textStyles.title4,
                        fontWeight = FontWeight.SemiBold,
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = repository.url,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Chips row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Extension,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = stringResource(
                            R.string.module_repo_module_count,
                            repository.modules.size
                        ),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.primary
                    )
                }
                if (repository.skippedCount > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Link,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.error,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = stringResource(
                                R.string.module_repo_skipped_count,
                                repository.skippedCount
                            ),
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.error
                        )
                    }
                }
            }

            // Error
            repository.error?.let { errorText ->
                Text(
                    text = errorText,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.error
                )
            }

            // Index URL
            val indexUrl = repository.indexJsonUrl.ifBlank { repository.url }
            Text(
                text = indexUrl,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Action buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onRefresh,
                    enabled = !refreshing,
                    colors = ButtonDefaults.buttonColors(),
                    modifier = Modifier.weight(1f)
                ) {
                    if (refreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(17.dp),
                            progress = null,
                            size = 17.dp,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.refresh))
                }
                Button(
                    onClick = onDelete,
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.delete))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Locale label functions – Runtime Repo Settings (private copies)
// ─────────────────────────────────────────────────────────────────────────────

private fun runtimeRepoCentralLabelMiuix(context: Context): String =
    when (LocaleHelper.getLanguage(context)) {
        LocaleHelper.LANG_ZH -> "普通模块中央仓库"
        LocaleHelper.LANG_RU -> "Центральный репозиторий обычных модулей"
        else -> "Standard module central repository"
    }

private fun runtimeRepoCentralDescLabelMiuix(context: Context): String =
    when (LocaleHelper.getLanguage(context)) {
        LocaleHelper.LANG_ZH -> "添加符合 Magisk 标准 JSON 格式的普通模块仓库。"
        LocaleHelper.LANG_RU -> "Добавьте репозиторий обычных модулей в стандартном формате JSON Magisk."
        else -> "Add standard module repositories that expose the Magisk JSON format."
    }

private fun runtimeRepoEmptyTitleLabelMiuix(context: Context): String =
    when (LocaleHelper.getLanguage(context)) {
        LocaleHelper.LANG_ZH -> "暂无普通模块仓库"
        LocaleHelper.LANG_RU -> "Нет репозиториев обычных модулей"
        else -> "No standard module repositories"
    }

private fun runtimeRepoUrlLabelMiuix(context: Context): String =
    when (LocaleHelper.getLanguage(context)) {
        LocaleHelper.LANG_ZH -> "普通模块仓库链接"
        LocaleHelper.LANG_RU -> "Ссылка на репозиторий обычных модулей"
        else -> "Standard module repository URL"
    }
