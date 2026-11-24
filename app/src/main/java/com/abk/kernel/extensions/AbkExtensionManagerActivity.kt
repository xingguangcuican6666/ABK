package com.abk.kernel.extensions

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.abk.kernel.R
import com.abk.kernel.data.repository.PreferencesRepository
import com.abk.kernel.ui.theme.AppTheme
import com.abk.kernel.utils.DownloadUtils
import com.abk.kernel.utils.LocaleHelper
import com.abk.kernel.utils.RootUtils
import com.abk.kernel.utils.findActivity
import io.github.xingguangcuican6666.abk.ui.components.AbkAlert
import io.github.xingguangcuican6666.abk.ui.components.AbkBadge
import io.github.xingguangcuican6666.abk.ui.components.AbkButton
import io.github.xingguangcuican6666.abk.ui.components.AbkButtonStyle
import io.github.xingguangcuican6666.abk.ui.components.AbkCard
import io.github.xingguangcuican6666.abk.ui.components.AbkDialog
import io.github.xingguangcuican6666.abk.ui.components.AbkEmptyState
import io.github.xingguangcuican6666.abk.ui.components.AbkHeading
import io.github.xingguangcuican6666.abk.ui.components.AbkIconButton
import io.github.xingguangcuican6666.abk.ui.components.AbkScaffold
import io.github.xingguangcuican6666.abk.ui.components.AbkSize
import io.github.xingguangcuican6666.abk.ui.components.AbkSpinner
import io.github.xingguangcuican6666.abk.ui.components.AbkText
import io.github.xingguangcuican6666.abk.ui.components.AbkTopBar
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcons
import io.github.xingguangcuican6666.abk.ui.theme.AbkSpacing
import io.github.xingguangcuican6666.abk.ui.theme.AbkTheme
import io.github.xingguangcuican6666.abk.ui.theme.AbkTone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val ContentGutter = 20.dp

class AbkExtensionManagerActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val focusExtensionId = intent.getStringExtra(ABK_EXTENSION_EXTRA_ID)
        val bootstrapMode = intent.getBooleanExtra("bootstrap_mode", false)

        setContent {
            AppTheme(prefs = remember { PreferencesRepository(applicationContext) }) {
                AbkExtensionManagerScreen(
                    focusExtensionId = focusExtensionId,
                    bootstrapMode = bootstrapMode,
                    onBack = ::finish,
                    onExternalFlowLaunched = ::finish,
                )
            }
        }
    }
}

@Composable
fun AbkExtensionManagerScreen(
    focusExtensionId: String?,
    bootstrapMode: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onExternalFlowLaunched: () -> Unit = {},
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    var loading by remember { mutableStateOf(true) }
    var extensions by remember { mutableStateOf<List<AbkManagedExtension>>(emptyList()) }
    var refreshToken by remember { mutableIntStateOf(0) }
    var autoLaunchConsumed by rememberSaveable(focusExtensionId, bootstrapMode) { mutableStateOf(false) }
    var missingCompanionDialog by remember { mutableStateOf<AbkManagedExtension?>(null) }

    fun requestRefresh() {
        refreshToken += 1
    }

    LaunchedEffect(refreshToken) {
        loading = true
        extensions = withContext(Dispatchers.IO) { abkLoadManagedExtensions(context) }
        loading = false
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && !loading) {
                requestRefresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val focused = remember(extensions, focusExtensionId) {
        extensions.firstOrNull { it.extensionId == focusExtensionId }
    }
    val dismissLocked = bootstrapMode && focused?.canLaunchOobe == true && focused.needsOobe

    if (dismissLocked) {
        BackHandler {
            Toast.makeText(
                context,
                context.getString(R.string.extension_bootstrap_locked),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun launchOobe(extension: AbkManagedExtension, finishAfterLaunch: Boolean = bootstrapMode && !dismissLocked) {
        val hostActivity = activity ?: return
        if (!extension.isCompanionInstalled) {
            missingCompanionDialog = extension
            return
        }
        if (!abkLaunchExtensionOobe(hostActivity, extension)) {
            Toast.makeText(hostActivity, hostActivity.getString(R.string.extension_oobe_missing), Toast.LENGTH_SHORT).show()
            requestRefresh()
            return
        }
        if (finishAfterLaunch) {
            onExternalFlowLaunched()
        }
    }

    fun launchServiceActivity(
        extension: AbkManagedExtension,
        finishAfterLaunch: Boolean = bootstrapMode && !dismissLocked
    ) {
        val hostActivity = activity ?: return
        if (!extension.isCompanionInstalled) {
            missingCompanionDialog = extension
            return
        }
        if (!abkLaunchExtensionServiceActivity(hostActivity, extension)) {
            Toast.makeText(hostActivity, hostActivity.getString(R.string.extension_launch_failed), Toast.LENGTH_SHORT).show()
            requestRefresh()
            return
        }
        if (finishAfterLaunch) {
            onExternalFlowLaunched()
        }
    }

    fun installExtension(extension: AbkManagedExtension) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                installExtensionCompanion(context, extension)
            }
            Toast.makeText(
                context,
                if (result.success) {
                    context.getString(
                        R.string.extension_install_success,
                        extension.companionDisplayName.ifBlank { extension.name }
                    )
                } else {
                    result.output.lastOrNull() ?: context.getString(R.string.extension_install_failed)
                },
                Toast.LENGTH_LONG
            ).show()
            if (!result.success) return@launch

            val refreshed = withContext(Dispatchers.IO) {
                abkLoadManagedExtensions(context).firstOrNull { it.extensionId == extension.extensionId }
            }
            val hostActivity = activity
            if (hostActivity != null && refreshed?.canLaunchOobe == true && refreshed.needsOobe) {
                if (abkLaunchExtensionOobe(hostActivity, refreshed)) {
                    return@launch
                }
            }
            requestRefresh()
        }
    }

    fun resetExtension(extension: AbkManagedExtension) {
        scope.launch {
            withContext(Dispatchers.IO) {
                RootUtils.clearAbkExtensionState(extension.extensionId)
            }
            requestRefresh()
        }
    }

    LaunchedEffect(bootstrapMode, focused?.extensionId, focused?.needsOobe, focused?.canLaunchOobe, loading) {
        val target = focused ?: return@LaunchedEffect
        if (!bootstrapMode || loading || autoLaunchConsumed) return@LaunchedEffect
        if (!target.isCompanionInstalled) {
            autoLaunchConsumed = true
            missingCompanionDialog = target
            return@LaunchedEffect
        }
        if (target.canLaunchServiceActivity) {
            autoLaunchConsumed = true
            launchServiceActivity(target, finishAfterLaunch = false)
        } else if (target.needsOobe && target.canLaunchOobe) {
            autoLaunchConsumed = true
            launchOobe(target, finishAfterLaunch = false)
        }
    }

    AbkScaffold(
        modifier = modifier,
        topBar = {
            AbkTopBar(
                title = stringResource(
                    if (bootstrapMode) R.string.extension_bootstrap_title
                    else R.string.extension_manager_title
                ),
                navigationIcon = if (dismissLocked) {
                    null
                } else {
                    {
                        AbkIconButton(
                            icon = AbkIcons.Back,
                            contentDescription = stringResource(R.string.settings_back),
                            onClick = onBack,
                        )
                    }
                },
                actions = {
                    AbkIconButton(
                        icon = AbkIcons.Refresh,
                        contentDescription = stringResource(R.string.refresh),
                        onClick = ::requestRefresh,
                        size = AbkSize.Small,
                    )
                },
            )
        },
    ) { padding ->
        when {
            loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                AbkSpinner(label = stringResource(R.string.loading))
            }

            extensions.isEmpty() -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = ContentGutter, vertical = AbkSpacing.Lg),
            ) {
                AbkEmptyState(
                    title = stringResource(R.string.extension_manager_empty_title),
                    description = stringResource(R.string.extension_manager_empty_desc),
                    icon = AbkIcons.Modules,
                )
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(
                    start = ContentGutter,
                    top = AbkSpacing.Lg,
                    end = ContentGutter,
                    bottom = AbkSpacing.Hero,
                ),
                verticalArrangement = Arrangement.spacedBy(AbkSpacing.Md),
            ) {
                if (bootstrapMode && focused != null) {
                    item("focus-guide-${focused.extensionId}") {
                        ExtensionBootstrapCard(
                            extension = focused,
                            onInstall = { installExtension(focused) },
                            onOpenOobe = { launchOobe(focused) },
                            onOpenService = { launchServiceActivity(focused) },
                        )
                    }
                }

                focused?.let { extension ->
                    item("focus-${extension.extensionId}") {
                        ExtensionCard(
                            extension = extension,
                            onInstall = { installExtension(it) },
                            onOpenOobe = { launchOobe(it) },
                            onOpenService = { launchServiceActivity(it) },
                            onReset = { resetExtension(it) },
                        )
                    }
                }

                items(
                    items = extensions.filter { it.extensionId != focused?.extensionId },
                    key = { it.extensionId },
                ) { extension ->
                    ExtensionCard(
                        extension = extension,
                        onInstall = { installExtension(it) },
                        onOpenOobe = { launchOobe(it) },
                        onOpenService = { launchServiceActivity(it) },
                        onReset = { resetExtension(it) },
                    )
                }
            }
        }
    }

    missingCompanionDialog?.let { extension ->
        AbkDialog(
            title = stringResource(R.string.extension_bootstrap_install_dialog_title),
            onDismissRequest = { missingCompanionDialog = null },
            actions = {
                AbkButton(
                    onClick = { missingCompanionDialog = null },
                    style = AbkButtonStyle.Ghost,
                ) {
                    AbkText(stringResource(android.R.string.cancel))
                }
                AbkButton(
                    onClick = {
                        missingCompanionDialog = null
                        installExtension(extension)
                    },
                    style = AbkButtonStyle.Primary,
                    enabled = extension.companionDownloadUrl.isNotBlank(),
                ) {
                    AbkText(stringResource(R.string.extension_install_action))
                }
            },
        ) {
            AbkText(
                stringResource(
                    R.string.extension_bootstrap_install_dialog_desc,
                    extension.name,
                    extension.companionLabel(),
                ),
                color = AbkTheme.colors.inkSoft,
            )
        }
    }
}

@Composable
private fun ExtensionBootstrapCard(
    extension: AbkManagedExtension,
    onInstall: () -> Unit,
    onOpenOobe: () -> Unit,
    onOpenService: () -> Unit,
) {
    val companionLabel = extension.companionLabel()
    val titleRes = when {
        !extension.isCompanionInstalled -> R.string.extension_bootstrap_install_title
        extension.canLaunchServiceActivity -> R.string.extension_bootstrap_service_title
        extension.canLaunchOobe -> R.string.extension_bootstrap_oobe_required_title
        else -> R.string.extension_bootstrap_oobe_missing_title
    }
    val description = when {
        !extension.isCompanionInstalled -> stringResource(R.string.extension_bootstrap_install_desc, companionLabel)
        extension.canLaunchServiceActivity -> stringResource(R.string.extension_bootstrap_service_desc)
        extension.canLaunchOobe -> stringResource(R.string.extension_bootstrap_oobe_required_desc)
        else -> stringResource(R.string.extension_bootstrap_oobe_missing_desc, companionLabel)
    }
    val tone = when {
        !extension.isCompanionInstalled -> AbkTone.Warning
        extension.canLaunchServiceActivity || extension.canLaunchOobe -> AbkTone.Accent
        else -> AbkTone.Neutral
    }

    AbkAlert(
        title = stringResource(titleRes),
        message = description,
        tone = tone,
        actions = {
            when {
                !extension.isCompanionInstalled -> AbkButton(
                    onClick = onInstall,
                    style = AbkButtonStyle.Primary,
                    enabled = extension.companionDownloadUrl.isNotBlank(),
                ) {
                    AbkText(stringResource(R.string.extension_install_action))
                }
                extension.canLaunchServiceActivity -> AbkButton(
                    onClick = onOpenService,
                    style = AbkButtonStyle.Primary,
                ) {
                    AbkText(stringResource(R.string.extension_run_service_action))
                }
                extension.canLaunchOobe -> AbkButton(
                    onClick = onOpenOobe,
                    style = AbkButtonStyle.Primary,
                ) {
                    AbkText(stringResource(R.string.extension_run_oobe_action))
                }
            }
        },
    )
}

@Composable
private fun ExtensionCard(
    extension: AbkManagedExtension,
    onInstall: (AbkManagedExtension) -> Unit,
    onOpenOobe: (AbkManagedExtension) -> Unit,
    onOpenService: (AbkManagedExtension) -> Unit,
    onReset: (AbkManagedExtension) -> Unit,
) {
    AbkCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AbkSpacing.Sm),
        ) {
            AbkHeading(extension.name, Modifier.weight(1f))
            AbkBadge(
                text = stringResource(
                    if (extension.needsOobe) R.string.extension_status_pending_oobe
                    else R.string.extension_status_oobe_complete
                ),
                tone = if (extension.needsOobe) AbkTone.Warning else AbkTone.Success,
            )
            AbkBadge(
                text = stringResource(
                    if (extension.isCompanionInstalled) R.string.extension_status_companion_ready
                    else R.string.extension_status_companion_missing
                ),
                tone = if (extension.isCompanionInstalled) AbkTone.Success else AbkTone.Danger,
                dot = false,
            )
        }

        if (extension.description.isNotBlank()) {
            AbkText(
                extension.description,
                style = MaterialTheme.typography.bodyMedium,
                color = AbkTheme.colors.inkSoft,
            )
        }

        if (extension.summary.isNotBlank()) {
            AbkText(
                extension.summary,
                style = MaterialTheme.typography.bodySmall,
                color = AbkTheme.colors.inkMuted,
            )
        }

        if (!extension.isCompanionInstalled) {
            AbkText(
                stringResource(R.string.extension_missing_companion_desc, extension.companionLabel()),
                style = MaterialTheme.typography.bodySmall,
                color = AbkTheme.colors.inkMuted,
            )
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(AbkSpacing.Sm),
            verticalArrangement = Arrangement.spacedBy(AbkSpacing.Sm),
        ) {
            if (!extension.isCompanionInstalled) {
                AbkButton(
                    onClick = { onInstall(extension) },
                    style = AbkButtonStyle.Primary,
                    enabled = extension.companionDownloadUrl.isNotBlank(),
                ) {
                    AbkText(stringResource(R.string.extension_install_action))
                }
            } else {
                if (extension.canLaunchServiceActivity) {
                    AbkButton(
                        onClick = { onOpenService(extension) },
                        style = AbkButtonStyle.Primary,
                    ) {
                        AbkText(stringResource(R.string.extension_run_service_action))
                    }
                } else if (extension.canLaunchOobe) {
                    AbkButton(
                        onClick = { onOpenOobe(extension) },
                        style = AbkButtonStyle.Primary,
                    ) {
                        AbkText(stringResource(R.string.extension_run_oobe_action))
                    }
                }
            }

            AbkButton(
                onClick = { onReset(extension) },
                style = AbkButtonStyle.Outline,
            ) {
                AbkText(stringResource(R.string.extension_reset_action))
            }
        }
    }
}

private suspend fun installExtensionCompanion(
    context: Context,
    extension: AbkManagedExtension,
): RootUtils.ShellResult {
    val url = extension.companionDownloadUrl.trim()
    if (url.isBlank()) {
        return RootUtils.ShellResult(false, listOf(context.getString(R.string.extension_download_missing)))
    }
    val download = DownloadUtils.downloadDirectAsset(
        context = context,
        token = null,
        url = url,
        name = extension.companionAssetName.ifBlank { "${extension.extensionId}.apk" },
        sizeBytes = 1L,
        runId = -1L,
        runTitle = extension.name,
    )
    val apkFile = download.artifacts.firstOrNull()?.filePath
    return if (apkFile.isNullOrBlank()) {
        RootUtils.ShellResult(
            false,
            listOf(download.errorMessage ?: context.getString(R.string.extension_install_failed))
        )
    } else {
        RootUtils.installApk(context, apkFile)
    }
}

private val AbkManagedExtension.canLaunchOobe: Boolean
    get() = isCompanionInstalled && discoveredApp?.oobeComponent != null

private fun AbkManagedExtension.companionLabel(): String =
    companionDisplayName.ifBlank { companionPackage.ifBlank { "Unknown" } }
