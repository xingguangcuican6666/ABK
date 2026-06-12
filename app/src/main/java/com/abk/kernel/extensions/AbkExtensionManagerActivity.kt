package com.abk.kernel.extensions

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.abk.kernel.R
import com.abk.kernel.data.repository.PreferencesRepository
import com.abk.kernel.ui.components.AbkScreenHorizontalPadding
import com.abk.kernel.ui.components.AppBackgroundHost
import com.abk.kernel.ui.components.ExpressiveSectionCard
import com.abk.kernel.ui.components.ExpressiveStatusChip
import com.abk.kernel.ui.components.ExpressiveTopBar
import com.abk.kernel.ui.theme.AbkTheme
import com.abk.kernel.utils.DownloadUtils
import com.abk.kernel.utils.LocaleHelper
import com.abk.kernel.utils.RootUtils
import com.abk.kernel.utils.findActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AbkExtensionManagerActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val focusExtensionId = intent.getStringExtra(ABK_EXTENSION_EXTRA_ID)
        val bootstrapMode = intent.getBooleanExtra("bootstrap_mode", false)

        setContent {
            val prefs = remember { PreferencesRepository(applicationContext) }
            val themeMode by prefs.themeMode.collectAsState(initial = "dark")
            val dynamicColorEnabled by prefs.dynamicColorEnabled.collectAsState(initial = true)
            val customThemeColorArgb by prefs.customThemeColorArgb.collectAsState(initial = null)
            val customAccentColorArgb by prefs.customAccentColorArgb.collectAsState(initial = null)
            val customBackgroundUri by prefs.customBackgroundUri.collectAsState(initial = null)
            val backgroundImageEnabled by prefs.backgroundImageEnabled.collectAsState(initial = false)
            val uiSurfaceAlpha by prefs.uiSurfaceAlpha.collectAsState(initial = 1f)

            AbkTheme(
                themeMode = themeMode,
                dynamicColorEnabled = dynamicColorEnabled,
                customThemeColorArgb = customThemeColorArgb,
                customAccentColorArgb = customAccentColorArgb
            ) {
                AppBackgroundHost(
                    backgroundUri = customBackgroundUri,
                    backgroundEnabled = backgroundImageEnabled,
                    uiSurfaceAlpha = uiSurfaceAlpha
                ) {
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
}

@Composable
fun AbkExtensionManagerScreen(
    focusExtensionId: String?,
    bootstrapMode: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = Color.Transparent,
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
        if (!abkLaunchExtensionOobe(hostActivity, extension)) {
            Toast.makeText(hostActivity, hostActivity.getString(R.string.extension_oobe_missing), Toast.LENGTH_SHORT).show()
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
        if (target.needsOobe && target.canLaunchOobe) {
            autoLaunchConsumed = true
            launchOobe(target, finishAfterLaunch = false)
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = containerColor,
        topBar = {
            ExpressiveTopBar(
                title = stringResource(
                    if (bootstrapMode) {
                        R.string.extension_bootstrap_title
                    } else {
                        R.string.extension_manager_title
                    }
                ),
                navigationIcon = if (dismissLocked) {
                    null
                } else {
                    {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = stringResource(R.string.settings_back)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = ::requestRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                }
            )
        }
    ) { padding ->
        if (loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        if (extensions.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = AbkScreenHorizontalPadding, vertical = 16.dp),
                verticalArrangement = Arrangement.Top
            ) {
                ExpressiveSectionCard(
                    title = stringResource(R.string.extension_manager_empty_title),
                    subtitle = stringResource(R.string.extension_manager_empty_desc),
                    icon = Icons.Default.Extension,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {}
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                start = AbkScreenHorizontalPadding,
                top = 16.dp,
                end = AbkScreenHorizontalPadding,
                bottom = 80.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (bootstrapMode && focused != null) {
                item("focus-guide-${focused.extensionId}") {
                    ExtensionBootstrapCard(
                        extension = focused,
                        onInstall = { installExtension(focused) },
                        onOpenOobe = { launchOobe(focused) }
                    )
                }
            }

            focused?.let { extension ->
                item("focus-${extension.extensionId}") {
                    ExtensionCard(
                        extension = extension,
                        emphasize = true,
                        onInstall = { installExtension(it) },
                        onOpenOobe = { launchOobe(it) },
                        onReset = { resetExtension(it) },
                    )
                }
            }

            items(
                items = extensions.filter { it.extensionId != focused?.extensionId },
                key = { it.extensionId }
            ) { extension ->
                ExtensionCard(
                    extension = extension,
                    emphasize = false,
                    onInstall = { installExtension(it) },
                    onOpenOobe = { launchOobe(it) },
                    onReset = { resetExtension(it) },
                )
            }
        }
    }
}

@Composable
private fun ExtensionBootstrapCard(
    extension: AbkManagedExtension,
    onInstall: () -> Unit,
    onOpenOobe: () -> Unit,
) {
    val companionLabel = extension.companionLabel()
    val titleRes = when {
        !extension.isCompanionInstalled -> R.string.extension_bootstrap_install_title
        extension.canLaunchOobe -> R.string.extension_bootstrap_oobe_required_title
        else -> R.string.extension_bootstrap_oobe_missing_title
    }
    val subtitle = when {
        !extension.isCompanionInstalled -> stringResource(R.string.extension_bootstrap_install_desc, companionLabel)
        extension.canLaunchOobe -> stringResource(R.string.extension_bootstrap_oobe_required_desc)
        else -> stringResource(R.string.extension_bootstrap_oobe_missing_desc, companionLabel)
    }
    val containerColor = when {
        !extension.isCompanionInstalled -> MaterialTheme.colorScheme.secondaryContainer
        extension.canLaunchOobe -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.errorContainer
    }

    ExpressiveSectionCard(
        title = stringResource(titleRes),
        subtitle = subtitle,
        icon = if (!extension.isCompanionInstalled) Icons.Default.InstallMobile else Icons.Default.Build,
        containerColor = containerColor,
    ) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when {
                !extension.isCompanionInstalled -> {
                    Button(
                        onClick = onInstall,
                        enabled = extension.companionDownloadUrl.isNotBlank()
                    ) {
                        Icon(Icons.Default.InstallMobile, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.extension_install_action))
                    }
                }
                extension.canLaunchOobe -> {
                    Button(onClick = onOpenOobe) {
                        Icon(Icons.Default.Build, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.extension_run_oobe_action))
                    }
                }
            }
        }
    }
}

@Composable
private fun ExtensionCard(
    extension: AbkManagedExtension,
    emphasize: Boolean,
    onInstall: (AbkManagedExtension) -> Unit,
    onOpenOobe: (AbkManagedExtension) -> Unit,
    onReset: (AbkManagedExtension) -> Unit,
) {
    val chipColor = if (extension.needsOobe) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.tertiary
    }

    ExpressiveSectionCard(
        title = extension.name,
        subtitle = extension.description.takeIf { it.isNotBlank() },
        icon = Icons.Default.Extension,
        containerColor = if (emphasize) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ExpressiveStatusChip(
                label = stringResource(
                    if (extension.needsOobe) {
                        R.string.extension_status_pending_oobe
                    } else {
                        R.string.extension_status_oobe_complete
                    }
                ),
                color = chipColor
            )
            ExpressiveStatusChip(
                label = stringResource(
                    if (extension.isCompanionInstalled) {
                        R.string.extension_status_companion_ready
                    } else {
                        R.string.extension_status_companion_missing
                    }
                ),
                color = if (extension.isCompanionInstalled) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.error
                }
            )
        }

        if (extension.summary.isNotBlank()) {
            Text(
                text = extension.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (!extension.isCompanionInstalled) {
            Text(
                text = stringResource(R.string.extension_missing_companion_desc, extension.companionLabel()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!extension.isCompanionInstalled) {
                Button(
                    onClick = { onInstall(extension) },
                    enabled = extension.companionDownloadUrl.isNotBlank()
                ) {
                    Icon(Icons.Default.InstallMobile, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.extension_install_action))
                }
            } else {
                if (extension.canLaunchOobe) {
                    Button(onClick = { onOpenOobe(extension) }) {
                        Icon(Icons.Default.Build, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.extension_run_oobe_action))
                    }
                }
            }

            Button(onClick = { onReset(extension) }) {
                Text(stringResource(R.string.extension_reset_action))
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
