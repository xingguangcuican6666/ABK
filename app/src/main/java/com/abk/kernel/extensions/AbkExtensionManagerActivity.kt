package com.abk.kernel.extensions

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.abk.kernel.R
import com.abk.kernel.data.repository.PreferencesRepository
import com.abk.kernel.ui.blur.BlurConfig
import com.abk.kernel.ui.blur.BlurScreenScaffold
import io.github.xingguangcuican6666.abk.ui.components.AbkIconButton
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcons
import com.abk.kernel.ui.components.AbkCenteredLoadingTransition
import com.abk.kernel.ui.components.AppBackgroundHost
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
            val customFontId by prefs.customFontId.collectAsState(initial = null)
            val customBackgroundUri by prefs.customBackgroundUri.collectAsState(initial = null)
            val backgroundImageEnabled by prefs.backgroundImageEnabled.collectAsState(initial = false)
            val uiSurfaceAlpha by prefs.uiSurfaceAlpha.collectAsState(initial = 1f)
            val blurEnabled by prefs.blurEnabled.collectAsState(initial = true)
            val blurBackgroundExpEnabled by prefs.blurBackgroundExpEnabled.collectAsState(initial = false)
            val blurConfig = remember(
                customBackgroundUri,
                backgroundImageEnabled,
                blurEnabled,
                blurBackgroundExpEnabled
            ) {
                BlurConfig(
                    blurEnabled = blurEnabled,
                    backgroundExpEnabled = blurBackgroundExpEnabled,
                    backgroundUri = customBackgroundUri,
                    backgroundImageEnabled = backgroundImageEnabled,
                )
            }

            AbkTheme(
                themeMode = themeMode,
                dynamicColorEnabled = dynamicColorEnabled,
                customThemeColorArgb = customThemeColorArgb,
                customAccentColorArgb = customAccentColorArgb,
                customFontId = customFontId
            ) {
                AppBackgroundHost(
                    backgroundUri = customBackgroundUri,
                    backgroundEnabled = backgroundImageEnabled,
                    uiSurfaceAlpha = uiSurfaceAlpha,
                    blurBackgroundEnabled = blurConfig.wantsBackgroundPainter,
                ) {
                    AbkExtensionManagerScreen(
                        focusExtensionId = focusExtensionId,
                        bootstrapMode = bootstrapMode,
                    onBack = ::finish,
                    onExternalFlowLaunched = ::finish,
                    blurEnabled = blurEnabled,
                    blurBackgroundExpEnabled = blurBackgroundExpEnabled,
                    backgroundUri = customBackgroundUri,
                    backgroundImageEnabled = backgroundImageEnabled,
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
    blurEnabled: Boolean = false,
    blurBackgroundExpEnabled: Boolean = false,
    backgroundUri: String? = null,
    backgroundImageEnabled: Boolean = false,
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
    val presented = remember(extensions) { extensions.associate { it.toItemState() to it } }
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

    if (loading && bootstrapMode) {
        AbkCenteredLoadingTransition(text = stringResource(R.string.loading))
        return
    }

    BlurScreenScaffold(
        blurConfig = BlurConfig(
            blurEnabled = blurEnabled,
            backgroundExpEnabled = blurBackgroundExpEnabled,
            backgroundUri = backgroundUri,
            backgroundImageEnabled = backgroundImageEnabled,
        ),
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
                        AbkIconButton(
                            icon = AbkIcons.Back,
                            contentDescription = stringResource(R.string.settings_back),
                            onClick = onBack
                        )
                    }
                },
                enableBlur = blurEnabled,
                actions = {
                    AbkIconButton(
                        icon = AbkIcons.Refresh,
                        contentDescription = stringResource(R.string.refresh),
                        onClick = ::requestRefresh
                    )
                }
            )
        }
    ) { topBarHeight ->
        if (loading) {
            AbkCenteredLoadingTransition(
                text = stringResource(R.string.loading),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topBarHeight + 16.dp)
            )
            return@BlurScreenScaffold
        }

        ExtensionsContent(
            topBarHeight = topBarHeight,
            bootstrapMode = bootstrapMode,
            focused = focused?.toItemState(),
            extensions = extensions.map { it.toItemState() },
            onInstall = { installExtension(it.resolve(presented)) },
            onOpenOobe = { launchOobe(it.resolve(presented)) },
            onOpenService = { launchServiceActivity(it.resolve(presented)) },
            onReset = { resetExtension(it.resolve(presented)) },
        )
    }

    missingCompanionDialog?.let { extension ->
        ExtensionMissingCompanionDialog(
            extensionName = extension.name,
            companionLabel = extension.companionLabel(),
            installEnabled = extension.companionDownloadUrl.isNotBlank(),
            onDismiss = { missingCompanionDialog = null },
            onInstall = {
                missingCompanionDialog = null
                installExtension(extension)
            },
        )
    }
}

private fun AbkManagedExtension.toItemState() = ExtensionItemState(
    id = extensionId,
    name = name,
    description = description,
    summary = summary,
    companionLabel = companionLabel(),
    companionInstalled = isCompanionInstalled,
    needsOobe = needsOobe,
    canLaunchOobe = canLaunchOobe,
    canLaunchService = canLaunchServiceActivity,
    canInstall = companionDownloadUrl.isNotBlank(),
)

private fun ExtensionItemState.resolve(
    presented: Map<ExtensionItemState, AbkManagedExtension>,
): AbkManagedExtension = presented[this] ?: error("Unknown extension $id")

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
