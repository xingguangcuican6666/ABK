@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package com.abk.kernel.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Web
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abk.kernel.R
import com.abk.kernel.data.model.AbkRuntimeBuildInfo
import com.abk.kernel.data.model.AbkRuntimeModule
import com.abk.kernel.data.model.AbkRuntimeStatus
import com.abk.kernel.data.model.downloadFileName
import com.abk.kernel.ui.blur.BlurScreenScaffold
import com.abk.kernel.ui.blur.blurredCardBackground
import com.abk.kernel.ui.blur.blurredCardSurfaceColor
import com.abk.kernel.ui.components.AbkScreenHorizontalPadding
import com.abk.kernel.ui.components.AbkInlineLoadingPill
import com.abk.kernel.ui.components.ObserveChildPageVisibility
import com.abk.kernel.ui.components.childPageOverlayEnterTransition
import com.abk.kernel.ui.components.childPageOverlayExitTransition
import com.abk.kernel.ui.components.childPageScrimExitTransition
import com.abk.kernel.ui.components.rememberChildPageBackController
import com.abk.kernel.ui.components.rememberChildPageOverlayTransition
import com.abk.kernel.ui.components.ExpressiveSwitch
import com.abk.kernel.ui.components.ExpressiveHeroCard
import com.abk.kernel.ui.components.ExpressiveSectionCard
import com.abk.kernel.ui.components.ExpressiveStatusChip
import com.abk.kernel.ui.components.ExpressiveTopBar
import com.abk.kernel.ui.components.ShimmerLinearProgress
import com.abk.kernel.ui.components.rememberAbkInteractiveRefreshPresentation
import com.abk.kernel.ui.theme.appPageBackgroundColor
import com.abk.kernel.ui.theme.uiSurfaceColor
import com.abk.kernel.ui.webui.ModuleWebUiActivity
import com.abk.kernel.utils.DownloadUtils
import com.abk.kernel.utils.RootUtils
import com.abk.kernel.viewmodel.MainViewModel
import com.abk.kernel.viewmodel.RuntimeModuleUpdateInfo
import com.abk.kernel.viewmodel.RuntimeModuleUpdateTarget
import com.abk.kernel.viewmodel.findRuntimeModuleUpdateTarget
import com.abk.kernel.viewmodel.resolveRuntimeModuleChangelog
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val RUNTIME_MODULE_DOWNLOAD_RUN_ID = -2_000_000_001L
private const val RUNTIME_MARKDOWN_URL_TAG = "runtime_markdown_url"
private val RUNTIME_MARKDOWN_ORDERED_LIST_REGEX = Regex("""^\d+\.\s+""")
private val RUNTIME_MARKDOWN_BARE_URL_REGEX = Regex("""https?://[^\s)]+""")

@Composable
fun RuntimeHomeScreen(
    vm: MainViewModel,
    outerPadding: PaddingValues = PaddingValues(0.dp),
    onSwitchToClassic: () -> Unit,
    onManagerPatchPageVisibleChange: (Boolean) -> Unit = {}
) {
    val state by vm.uiState.collectAsState()
    val refreshPresentation = rememberAbkInteractiveRefreshPresentation(loading = state.abkRuntimeLoading)
    val showRefreshLoading = refreshPresentation.showLoading
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    var showManagerPatchPage by rememberSaveable { mutableStateOf(false) }
    val managerPatchPageTransition = rememberChildPageOverlayTransition(
        visible = showManagerPatchPage,
        label = "runtime-manager-patch"
    )
    var managerPatchBackEnabled by remember { mutableStateOf(true) }
    val motionScheme = MaterialTheme.motionScheme

    LaunchedEffect(state.runtimeNavigationEnabled, state.rootGranted) {
        if (state.runtimeNavigationEnabled) vm.refreshAbkRuntimeStatus()
    }

    fun closeManagerPatchPage() {
        if (!managerPatchBackEnabled) return
        showManagerPatchPage = false
    }

    val childPageBack = rememberChildPageBackController(
        enabled = showManagerPatchPage && managerPatchBackEnabled,
        predictiveBackEnabled = state.predictiveBackEnabled,
        onBack = ::closeManagerPatchPage,
    )

    ObserveChildPageVisibility(
        transition = managerPatchPageTransition,
        onVisibleChange = onManagerPatchPageVisibleChange,
        onAfterExitAnimation = {
            childPageBack.resetProgress()
            managerPatchBackEnabled = true
        }
    )

    DisposableEffect(Unit) {
        onDispose {
            onManagerPatchPageVisibleChange(false)
            managerPatchBackEnabled = true
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val childPageBottomInset = outerPadding.calculateBottomPadding()
        val childPageModifier = Modifier
            .fillMaxWidth()
            .height(maxHeight + childPageBottomInset)

        BlurScreenScaffold(
            blurConfig = state.blurConfig,
            containerColor = appPageBackgroundColor(uiSurfaceColor(MaterialTheme.colorScheme.surface)),
            topBar = {
                ExpressiveTopBar(
                    title = "AnyBase Kernel",
                    compactTitle = true,
                    scrollBehavior = scrollBehavior,
                    enableBlur = state.blurEnabled,
                    actions = {
                        IconButton(onClick = {
                            refreshPresentation.beginRefresh()
                            vm.refreshAbkRuntimeStatus()
                        }) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.runtime_refresh))
                        }
                        IconButton(onClick = onSwitchToClassic) {
                            Icon(Icons.Default.SwapHoriz, contentDescription = stringResource(R.string.nav_status))
                        }
                    }
                )
            }
        ) { topBarHeight ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = AbkScreenHorizontalPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Spacer(Modifier.height(topBarHeight + 16.dp))
                RuntimeStatusHeader(
                    runtimeStatus = state.abkRuntimeStatus,
                    hasNativeManagerPermission = state.hasNativeManagerPermission,
                    loading = state.abkRuntimeLoading,
                    error = state.abkRuntimeError,
                    onRefresh = {
                        refreshPresentation.beginRefresh()
                        vm.refreshAbkRuntimeStatus()
                    },
                    onOpenManagerPatch = {
                        childPageBack.resetProgress()
                        managerPatchBackEnabled = true
                        showManagerPatchPage = true
                    }
                )

                Crossfade(targetState = showRefreshLoading, label = "runtime-home-refresh") { refreshing ->
                    if (refreshing) {
                        AbkInlineLoadingPill(
                            text = stringResource(R.string.runtime_refreshing_status),
                            modifier = Modifier.fillMaxWidth(),
                            compact = false
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            state.abkRuntimeStatus?.let { runtimeStatus ->
                                RuntimeManagerCard(runtimeStatus)
                                RuntimeBuildParametersCard(runtimeStatus)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(80.dp + outerPadding.calculateBottomPadding()))
            }
        }

        managerPatchPageTransition.AnimatedVisibility(
            visible = { it },
            enter = fadeIn(animationSpec = motionScheme.defaultEffectsSpec()),
            exit = childPageScrimExitTransition(state.predictiveBackEnabled, motionScheme),
            modifier = childPageModifier
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = childPageBack.scrimAlpha))
            )
        }

        managerPatchPageTransition.AnimatedVisibility(
            visible = { it },
            enter = childPageOverlayEnterTransition(state.predictiveBackEnabled, motionScheme),
            exit = childPageOverlayExitTransition(state.predictiveBackEnabled, motionScheme),
            modifier = childPageModifier
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(childPageBack.backTransformModifier())
            ) {
                AbkRootPatchScreen(
                    rootGranted = state.rootGranted,
                    hasNativeManagerPermission = state.hasNativeManagerPermission,
                    runtimeVariant = state.abkRuntimeStatus?.manager?.variant.orEmpty(),
                    backgroundUri = state.customBackgroundUri,
                    backgroundImageEnabled = state.backgroundImageEnabled,
                    downloadDirectory = state.downloadDirectory,
                    blurEnabled = state.blurEnabled,
                    blurBackgroundExpEnabled = state.blurBackgroundExpEnabled,
                    onBack = childPageBack::requestDismiss,
                    onBackEnabledChange = { managerPatchBackEnabled = it }
                )
            }
        }
    }
}

@Composable
fun InstalledModulesScreen(
    vm: MainViewModel,
    outerPadding: PaddingValues = PaddingValues(0.dp),
    pendingModuleInstallUri: String? = null,
    onPendingModuleInstallUriConsumed: () -> Unit = {}
) {
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val refreshPresentation = rememberAbkInteractiveRefreshPresentation(loading = state.abkRuntimeLoading)
    val showRefreshLoading = refreshPresentation.showLoading
    val showInitialLoading = state.abkRuntimeLoading && state.abkRuntimeStatus == null && state.abkRuntimeError == null
    var query by rememberSaveable { mutableStateOf("") }
    var pendingInstallUri by remember { mutableStateOf<Uri?>(null) }
    var installDialogVisible by remember { mutableStateOf(false) }
    var installRunning by remember { mutableStateOf(false) }
    var installSuccess by remember { mutableStateOf<Boolean?>(null) }
    var installLog by remember { mutableStateOf<List<String>>(emptyList()) }
    var showAllFilesAccessPrompt by remember { mutableStateOf(false) }
    var resumeModulePickerAfterPermission by remember { mutableStateOf(false) }
    var uninstallTarget by remember { mutableStateOf<AbkRuntimeModule?>(null) }
    var updateTarget by remember { mutableStateOf<RuntimeModuleUpdateTarget?>(null) }
    var runtimeUpdateCandidates by remember { mutableStateOf<Map<String, RuntimeModuleUpdateTarget>>(emptyMap()) }
    val runtimeModulesForUpdates = remember(state.abkRuntimeStatus?.modules) { state.abkRuntimeStatus?.modules.orEmpty() }
    LaunchedEffect(runtimeModulesForUpdates) {
        val targets = withContext(Dispatchers.IO) {
            val targetsMap = mutableMapOf<String, RuntimeModuleUpdateTarget>()
            for (module in runtimeModulesForUpdates) {
                findRuntimeModuleUpdateTarget(module)?.let { target ->
                    targetsMap[module.id] = target
                }
            }
            targetsMap
        }
        runtimeUpdateCandidates = targets
        if (updateTarget?.let { it.module.id !in targets } == true) {
            updateTarget = null
        }
    }
    val runtimeUpdates = runtimeUpdateCandidates
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val modules = remember(state.abkRuntimeStatus?.modules, query) {
        state.abkRuntimeStatus?.modules.orEmpty()
            .filter { it.matchesRuntimeModuleQuery(query) }
    }
    val groupedModules = remember(modules) { groupRuntimeModulesForDisplay(modules) }

    fun appendInstallLog(line: String) {
        scope.launch(Dispatchers.Main.immediate) {
            installLog = installLog + line
        }
    }

    fun installModuleFromUri(uri: Uri) {
        if (installRunning) return
        installDialogVisible = true
        installRunning = true
        installSuccess = null
        installLog = listOf(
            "${'$'} module install",
            "source: $uri",
            "",
            context.getString(R.string.runtime_copying_module)
        )
        scope.launch {
            var stagedName = "module.zip"
            var stagedPath = ""
            val result = withContext(Dispatchers.IO) {
                var stagedFile: File? = null
                runCatching {
                    stagedFile = copyRuntimeModuleUriToCache(context, uri).also {
                        stagedName = it.name
                        stagedPath = it.absolutePath
                    }
                    appendInstallLog("file: $stagedPath")
                    appendInstallLog(context.getString(R.string.runtime_wait_root_shell))
                    if (!RootUtils.refreshRootState()) {
                        RootUtils.ShellResult(false, listOf(context.getString(R.string.runtime_manager_inactive)))
                    } else {
                        RootUtils.installModule(stagedPath, ::appendInstallLog)
                    }
                }.getOrElse {
                    RootUtils.ShellResult(false, listOf(context.getString(R.string.runtime_module_file_read_failed)))
                }.also {
                    stagedFile?.delete()
                }
            }
            installRunning = false
            installSuccess = result.success
            installLog = listOf(
                "${'$'} module install $stagedName",
                "file: ${stagedPath.ifBlank { context.getString(R.string.runtime_temp_file_missing) }}",
                ""
            ) + result.output.ifEmpty {
                listOf(
                    if (result.success) {
                        context.getString(R.string.runtime_module_install_done_no_output)
                    } else {
                        context.getString(R.string.runtime_module_install_failed_no_log)
                    }
                )
            }
            if (result.success) vm.refreshAbkRuntimeStatus()
        }
    }

    fun installModuleUpdate(target: RuntimeModuleUpdateTarget) {
        if (installRunning) return
        installDialogVisible = true
        installRunning = true
        installSuccess = null
        installLog = listOf(
            "module update",
            "name: ${target.module.displayName()}",
            "version: ${target.updateInfo.version.ifBlank { "unknown" }}",
            "source: ${target.updateInfo.zipUrl}",
            ""
        )
        scope.launch {
            val downloadDirectoryPath = state.downloadDirectory
            val downloadResult = withContext(Dispatchers.IO) {
                DownloadUtils.downloadRuntimeModuleAsset(
                    context = context,
                    token = null,
                    url = target.updateInfo.zipUrl,
                    name = target.module.downloadFileName(),
                    sizeBytes = 0L,
                    runId = RUNTIME_MODULE_DOWNLOAD_RUN_ID,
                    runTitle = target.module.displayName(),
                    downloadDirectoryPath = downloadDirectoryPath,
                    downloadThreadCount = state.downloadThreadCount
                )
            }
            val downloadedFile = downloadResult.artifacts.firstOrNull()?.filePath?.let(::File)
            if (downloadedFile == null || !downloadedFile.exists()) {
                installRunning = false
                installSuccess = false
                installLog = installLog + listOf(
                    "",
                    downloadResult.errorMessage ?: context.getString(R.string.runtime_module_download_failed),
                    context.getString(R.string.runtime_wait_root_shell)
                )
                return@launch
            }
            installLog = installLog + "file: ${downloadedFile.absolutePath}"
            val expectedSha256 = target.updateInfo.sha256
            if (!expectedSha256.isNullOrBlank()) {
                val actual = withContext(Dispatchers.IO) { DownloadUtils.fileSha256Hex(downloadedFile) }
                if (!actual.equals(expectedSha256.trim(), ignoreCase = true)) {
                    installRunning = false
                    installSuccess = false
                    installLog = installLog + listOf(
                        "",
                        context.getString(R.string.runtime_module_checksum_mismatch)
                    )
                    return@launch
                }
            }
            val result = withContext(Dispatchers.IO) {
                if (!RootUtils.refreshRootState()) {
                    RootUtils.ShellResult(false, listOf(context.getString(R.string.runtime_manager_inactive)))
                } else {
                    RootUtils.installModule(downloadedFile.absolutePath) { line ->
                        scope.launch(Dispatchers.Main.immediate) {
                            installLog = installLog + line
                        }
                    }
                }
            }
            installRunning = false
            installSuccess = result.success
            if (result.success) {
                runtimeUpdateCandidates = runtimeUpdateCandidates - target.module.id
                vm.refreshAbkRuntimeStatus()
            }
        }
    }

    val modulePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) pendingInstallUri = uri
    }
    val allFilesAccessLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (resumeModulePickerAfterPermission) {
            if (hasRuntimeModuleFileAccess()) {
                resumeModulePickerAfterPermission = false
                modulePicker.launch(MODULE_INSTALL_MIME_TYPES)
            } else {
                showAllFilesAccessPrompt = true
            }
        }
    }

    fun launchModulePickerWithPermissionCheck() {
        if (installRunning) return
        if (hasRuntimeModuleFileAccess()) {
            modulePicker.launch(MODULE_INSTALL_MIME_TYPES)
        } else {
            resumeModulePickerAfterPermission = true
            showAllFilesAccessPrompt = true
        }
    }

    fun openAllFilesAccessSettings() {
        showAllFilesAccessPrompt = false
        resumeModulePickerAfterPermission = true
        val packageUri = Uri.parse("package:${context.packageName}")
        val appSettings = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, packageUri)
        val allFilesSettings = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
        runCatching {
            allFilesAccessLauncher.launch(appSettings)
        }.getOrElse {
            runCatching { allFilesAccessLauncher.launch(allFilesSettings) }
                .onFailure { showAllFilesAccessPrompt = true }
        }
    }

    fun launchModulePickerFallback() {
        showAllFilesAccessPrompt = false
        resumeModulePickerAfterPermission = false
        if (!installRunning) modulePicker.launch(MODULE_INSTALL_MIME_TYPES)
    }

    LaunchedEffect(pendingModuleInstallUri) {
        if (!pendingModuleInstallUri.isNullOrBlank()) {
            runCatching { Uri.parse(pendingModuleInstallUri) }.getOrNull()?.let { uri ->
                pendingInstallUri = uri
            }
            onPendingModuleInstallUriConsumed()
        }
    }

    LaunchedEffect(state.runtimeNavigationEnabled, state.rootGranted) {
        if (state.runtimeNavigationEnabled) vm.refreshAbkRuntimeStatus()
    }

    Box(Modifier.fillMaxSize()) {
        BlurScreenScaffold(
            blurConfig = state.blurConfig,
            containerColor = appPageBackgroundColor(uiSurfaceColor(MaterialTheme.colorScheme.surface)),
            topBar = {
                ExpressiveTopBar(
                    title = stringResource(R.string.runtime_installed_modules_title),
                    scrollBehavior = scrollBehavior,
                    enableBlur = state.blurEnabled,
                    actions = {
                        IconButton(onClick = {
                            refreshPresentation.beginRefresh()
                            vm.refreshAbkRuntimeStatus()
                        }) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.runtime_refresh_installed_modules))
                        }
                    }
                )
            }
        ) { topBarHeight ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = AbkScreenHorizontalPadding),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Spacer(Modifier.height(topBarHeight + 16.dp))
                RuntimeModuleSearchField(query, onValueChange = { query = it })

            when {
                showInitialLoading -> {
                    AbkInlineLoadingPill(
                        text = stringResource(R.string.loading),
                        modifier = Modifier.fillMaxWidth(),
                        compact = false
                    )
                }
                else -> {
                    Crossfade(targetState = showRefreshLoading, label = "installed-modules-refresh") { refreshing ->
                        if (refreshing) {
                            AbkInlineLoadingPill(
                                text = stringResource(R.string.runtime_refreshing_modules),
                                modifier = Modifier.fillMaxWidth(),
                                compact = false
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                state.abkRuntimeError?.let {
                                    RuntimeErrorCard(
                                        message = if (state.abkRuntimeStatus == null || !state.hasNativeManagerPermission) {
                                            it
                                        } else {
                                            stringResource(R.string.runtime_operation_incomplete_retry)
                                        },
                                        onRefresh = {
                                            refreshPresentation.beginRefresh()
                                            vm.refreshAbkRuntimeStatus()
                                        }
                                    )
                                }

                                if (state.abkRuntimeStatus != null && modules.isEmpty()) {
                                    Text(
                                        text = if (query.isBlank()) {
                                            stringResource(R.string.runtime_no_reported_modules)
                                        } else {
                                            stringResource(R.string.runtime_no_matching_modules)
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 24.dp)
                                    )
                                } else {
                                    groupedModules.forEach { grouped ->
                                        grouped.groupName?.let { groupName ->
                                            Text(
                                                text = groupName,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.padding(top = 4.dp)
                                            )
                                            grouped.groupDescription?.takeIf { it.isNotBlank() }?.let { description ->
                                                Text(
                                                    text = description,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        grouped.modules.forEach { module ->
                                            InstalledRuntimeModuleCard(
                                                module = module,
                                                updateCandidate = runtimeUpdates[module.id],
                                                actionInFlight = state.abkRuntimeModuleActionId == module.id,
                                                onSetEnabled = { enabled -> vm.setAbkRuntimeModuleEnabled(module.id, enabled) },
                                                onRequestUpdate = { candidate -> updateTarget = candidate },
                                                onRequestUninstall = { uninstallTarget = module },
                                                onRunAction = { vm.runRuntimeModuleAction(module.id) },
                                                onOpenWebUi = {
                                                    context.startActivity(
                                                        Intent(context, ModuleWebUiActivity::class.java)
                                                            .putExtra(ModuleWebUiActivity.EXTRA_MODULE_ID, module.id)
                                                            .putExtra(ModuleWebUiActivity.EXTRA_MODULE_NAME, module.displayName())
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(80.dp + outerPadding.calculateBottomPadding()))
        }
        }
        SmallFloatingActionButton(
            onClick = {
                launchModulePickerWithPermissionCheck()
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 16.dp, bottom = 16.dp + outerPadding.calculateBottomPadding()),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            Icon(Icons.Default.UploadFile, contentDescription = stringResource(R.string.runtime_install_module))
        }
    }

    if (state.abkRuntimeModuleActionTitle != null) {
        AlertDialog(
            onDismissRequest = vm::dismissRuntimeModuleActionOutput,
            confirmButton = {
            TextButton(onClick = vm::dismissRuntimeModuleActionOutput) {
                    Text(stringResource(R.string.close))
                }
            },
            title = { Text(state.abkRuntimeModuleActionTitle.orEmpty()) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.abkRuntimeModuleActionId != null) {
                        ShimmerLinearProgress(
                            progress = { null },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Text(
                        text = state.abkRuntimeModuleActionOutput.ifEmpty {
                            listOf(stringResource(R.string.runtime_waiting_output))
                        }.joinToString("\n"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        )
    }

    if (showAllFilesAccessPrompt) {
        RuntimeModuleFileAccessDialog(
            onDismiss = {
                showAllFilesAccessPrompt = false
                resumeModulePickerAfterPermission = false
            },
            onGrantAccess = ::openAllFilesAccessSettings,
            onUseSystemPicker = ::launchModulePickerFallback
        )
    }

    pendingInstallUri?.let { uri ->
        RuntimeModuleInstallConfirmDialog(
            uri = uri,
            displayName = remember(context, uri) { runtimeModuleUriDisplayName(context, uri) },
            onDismiss = { if (!installRunning) pendingInstallUri = null },
            onConfirm = {
                if (!installRunning) {
                    pendingInstallUri = null
                    installModuleFromUri(uri)
                }
            }
        )
    }

    updateTarget?.let { target ->
        val currentDownloadDirectory = state.downloadDirectory
        RuntimeModuleUpdateConfirmDialog(
            target = target,
            downloadDirectoryPath = currentDownloadDirectory,
            onDismiss = { updateTarget = null },
            onConfirm = {
                updateTarget = null
                installModuleUpdate(target)
            }
        )
    }

    uninstallTarget?.let { module ->
        RuntimeModuleUninstallConfirmDialog(
            module = module,
            pending = !module.remove,
            onDismiss = { uninstallTarget = null },
            onConfirm = {
                vm.setAbkRuntimeModulePendingUninstall(module.id, !module.remove)
                uninstallTarget = null
            }
        )
    }

    if (installDialogVisible) {
        RuntimeModuleInstallDialog(
            running = installRunning,
            success = installSuccess,
            logLines = installLog,
            onClose = { if (!installRunning) installDialogVisible = false },
            onReboot = {
                if (!installRunning) {
                    scope.launch(Dispatchers.IO) { RootUtils.reboot() }
                }
            }
        )
    }
}

@Composable
private fun RuntimeStatusHeader(
    runtimeStatus: AbkRuntimeStatus?,
    hasNativeManagerPermission: Boolean,
    loading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onOpenManagerPatch: (() -> Unit)? = null
) {
    val clickableModifier = if (onOpenManagerPatch != null) {
        Modifier.clickable(onClick = onOpenManagerPatch)
    } else {
        Modifier
    }
    ExpressiveHeroCard(
        title = when {
            runtimeStatus != null && hasNativeManagerPermission -> stringResource(R.string.runtime_manager_active)
            runtimeStatus != null -> stringResource(R.string.runtime_manager_inactive_title)
            else -> stringResource(R.string.runtime_manager_inactive_title)
        },
        subtitle = runtimeStatus?.let {
            if (!hasNativeManagerPermission && !error.isNullOrBlank()) {
                error
            } else {
                val managerName = it.manager?.displayName?.takeIf { name -> name.isNotBlank() } ?: "Root"
                "$managerName · ABK ${it.abkVersion.ifBlank { "unknown" }} · ${stringResource(R.string.runtime_module_count, it.modules.size)}"
            }
        } ?: (error ?: stringResource(R.string.runtime_inactive_desc)),
        icon = if (runtimeStatus != null && hasNativeManagerPermission) Icons.Default.CheckCircle else Icons.Default.Error,
        containerColor = if (runtimeStatus != null && hasNativeManagerPermission) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        contentColor = if (runtimeStatus != null && hasNativeManagerPermission) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = clickableModifier,
        badge = {
            runtimeStatus?.let {
                ExpressiveStatusChip(
                    label = "schema ${it.schema}",
                    icon = Icons.Default.Tune,
                    color = MaterialTheme.colorScheme.primary
                )
                if (it.abkCommit.isNotBlank()) {
                    ExpressiveStatusChip(
                        label = it.abkCommit,
                        icon = Icons.Default.Memory,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    ) {
        if (runtimeStatus == null) {
            Button(
                onClick = onRefresh,
                enabled = !loading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.runtime_recheck))
                }
            }
        }
    }
}

@Composable
private fun RuntimeManagerCard(runtimeStatus: AbkRuntimeStatus) {
    val manager = runtimeStatus.manager ?: return
    val backend = runtimeStatus.runtimeBackend
    ExpressiveSectionCard(
        title = stringResource(R.string.runtime_manager_title),
        subtitle = stringResource(R.string.runtime_manager_desc),
        icon = Icons.Default.Memory
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            RuntimeInfoRow(stringResource(R.string.runtime_type), manager.displayName.ifBlank { manager.variant })
            RuntimeInfoRow(stringResource(R.string.runtime_version), manager.version)
            RuntimeInfoRow(stringResource(R.string.runtime_source), runtimeBackendLabel(manager.backend))
            runtimeStatus.workMode.takeIf { it.isNotBlank() }?.let { workMode ->
                RuntimeInfoRow(stringResource(R.string.runtime_work_mode), runtimeWorkModeLabel(workMode))
            }
            if (backend != null && backend != manager) {
                Spacer(Modifier.height(2.dp))
                RuntimeInfoRow(stringResource(R.string.runtime_backend), backend.displayName.ifBlank { backend.variant })
                RuntimeInfoRow(stringResource(R.string.runtime_backend_version), backend.version)
                RuntimeInfoRow(stringResource(R.string.runtime_compat_layer), runtimeBackendLabel(backend.backend))
            }
            val diagnostics = manager.diagnostics
                .plus(backend?.diagnostics.orEmpty())
                .distinct()
            diagnostics.forEach { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            val chips = manager.capabilities
                .plus(backend?.capabilities.orEmpty())
                .map { runtimeCapabilityLabel(it) }
                .ifEmpty { listOf("Root Shell") }
                .distinct()
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                chips.forEach { label ->
                    RuntimeModuleChip(label, secondary = true)
                }
            }
        }
    }
}

@Composable
private fun RuntimeBuildParametersCard(runtimeStatus: AbkRuntimeStatus) {
    val build = runtimeStatus.build
    val systemKernelVersion = remember { RootUtils.getKernelVersion() }
    ExpressiveSectionCard(
        title = stringResource(R.string.runtime_build_params_title),
        subtitle = stringResource(R.string.runtime_build_params_desc),
        icon = Icons.Default.Tune
    ) {
        if (build == null) {
            Text(
                text = stringResource(R.string.runtime_old_schema),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@ExpressiveSectionCard
        }

        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            RuntimeInfoRow("Android", build.androidVersion)
            RuntimeInfoRow(stringResource(R.string.runtime_target_kernel), listOf(build.kernelVersion, build.subLevel).filter { it.isNotBlank() }.joinToString("."))
            RuntimeInfoRow(stringResource(R.string.build_kernel_version), systemKernelVersion)
            RuntimeInfoRow(stringResource(R.string.runtime_patch_level), build.osPatchLevel)
            RuntimeInfoRow(stringResource(R.string.runtime_revision), build.revision)
            RuntimeInfoRow("KSU", listOf(build.kernelsuVariant, build.kernelsuBranch).filter { it.isNotBlank() }.joinToString(" / "))
            RuntimeInfoRow(stringResource(R.string.runtime_build_time), build.buildTime)
            RuntimeInfoRow(stringResource(R.string.runtime_virtualization), build.virtualizationSupport)
            RuntimeInfoRow(stringResource(R.string.runtime_zram_extra_algos), build.zramExtraAlgos)
            RuntimeInfoRow("ABK", listOf(runtimeStatus.abkVersion, runtimeStatus.abkCommit).filter { it.isNotBlank() }.joinToString(" · "))
            RuntimeFeatureChips(build)
        }
    }
}

@Composable
private fun RuntimeFeatureChips(build: AbkRuntimeBuildInfo) {
    val features = build.features
        .filterValues { it }
        .keys
        .map { runtimeFeatureLabel(it) }
        .ifEmpty { listOf(stringResource(R.string.runtime_basic_config)) }
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        features.forEach { feature ->
            RuntimeModuleChip(feature, secondary = true)
        }
    }
}

@Composable
private fun RuntimeInfoRow(label: String, value: String) {
    if (value.isBlank()) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(82.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun RuntimeErrorCard(
    message: String,
    onRefresh: () -> Unit
) {
    val shape = RoundedCornerShape(8.dp)
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .blurredCardBackground(shape),
        shape = shape,
        colors = CardDefaults.elevatedCardColors(
            containerColor = blurredCardSurfaceColor(MaterialTheme.colorScheme.errorContainer)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.runtime_recheck))
            }
        }
    }
}

@Composable
private fun RuntimeModuleSearchField(value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        leadingIcon = { Icon(Icons.Default.Search, null) },
        placeholder = { Text(stringResource(R.string.runtime_search_installed_modules)) },
        singleLine = true,
        shape = RoundedCornerShape(14.dp)
    )
}

@Composable
private fun InstalledRuntimeModuleCard(
    module: AbkRuntimeModule,
    updateCandidate: RuntimeModuleUpdateTarget?,
    actionInFlight: Boolean,
    onSetEnabled: (Boolean) -> Unit,
    onRequestUpdate: (RuntimeModuleUpdateTarget) -> Unit,
    onRequestUninstall: () -> Unit,
    onRunAction: () -> Unit,
    onOpenWebUi: () -> Unit
) {
    val canUninstall = module.canUninstallRuntimeModule()
    val shape = RoundedCornerShape(8.dp)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .blurredCardBackground(shape),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = blurredCardSurfaceColor(MaterialTheme.colorScheme.surfaceContainer)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = module.displayName(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (module.version.isNotBlank()) {
                        Text(
                            text = buildString {
                                append(stringResource(R.string.runtime_module_version, module.version))
                                if (module.versionCode > 0) append(" (${module.versionCode})")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (module.author.isNotBlank()) {
                        Text(
                            text = stringResource(R.string.runtime_module_author, module.author),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (module.controllable && !module.readonly) {
                    ExpressiveSwitch(
                        checked = module.enabled,
                        enabled = !actionInFlight && !module.update,
                        onCheckedChange = onSetEnabled
                    )
                }
            }

            if (module.description.isNotBlank()) {
                Text(
                    text = module.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                RuntimeModuleChip(module.id.ifBlank { module.repoName() })
                if (module.metamodule) RuntimeModuleChip("META", secondary = false)
                RuntimeModuleChip(runtimeModuleTypeLabel(module), secondary = true)
                if (module.stage.isNotBlank()) RuntimeModuleChip(module.stage, secondary = true)
                if (module.source.isNotBlank()) RuntimeModuleChip(runtimeModuleSourceLabel(module.source), secondary = true)
                RuntimeModuleChip(if (module.enabled) stringResource(R.string.runtime_enabled) else stringResource(R.string.runtime_disabled), secondary = !module.enabled)
                if (module.update) RuntimeModuleChip(stringResource(R.string.runtime_pending_update), secondary = true)
                if (updateCandidate != null) RuntimeModuleChip(stringResource(R.string.runtime_update_available), secondary = false)
                if (module.remove) RuntimeModuleChip(stringResource(R.string.runtime_pending_remove), secondary = true)
                if (module.hasWebUi) RuntimeModuleChip("WebUI", secondary = true)
                if (module.actionSupported || module.hasActionScript) RuntimeModuleChip("Action", secondary = true)
                RuntimeModuleChip(
                    when {
                        module.readonly -> stringResource(R.string.runtime_readonly)
                        module.controllable -> stringResource(R.string.runtime_controllable)
                        else -> stringResource(R.string.runtime_metadata_only)
                    },
                    secondary = !module.controllable || module.readonly
                )
            }

            if (module.repoUrl.isNotBlank()) {
                Text(
                    text = module.repoUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (module.hasWebUi || module.actionSupported || updateCandidate != null || canUninstall || actionInFlight) {
                Row(
                    modifier = Modifier.align(Alignment.End),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (updateCandidate != null) {
                        IconButton(
                            onClick = { onRequestUpdate(updateCandidate) },
                            enabled = !actionInFlight && !module.remove
                        ) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = stringResource(R.string.runtime_update_module)
                            )
                        }
                    }
                    if (actionInFlight) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                    if (module.hasWebUi) {
                        IconButton(
                            onClick = onOpenWebUi,
                            enabled = module.enabled && !module.remove && !module.update
                        ) {
                            Icon(Icons.Default.Web, contentDescription = stringResource(R.string.runtime_open_webui))
                        }
                    }
                    if (module.actionSupported) {
                        IconButton(
                            onClick = onRunAction,
                            enabled = module.enabled && !actionInFlight
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.runtime_run_action))
                        }
                    }
                    if (canUninstall) {
                        FilledTonalButton(
                            onClick = onRequestUninstall,
                            enabled = !actionInFlight,
                            modifier = Modifier.defaultMinSize(minWidth = 52.dp, minHeight = 32.dp),
                            contentPadding = ButtonDefaults.TextButtonContentPadding
                        ) {
                            Icon(
                                if (module.remove) Icons.Default.RestartAlt else Icons.Default.Delete,
                                contentDescription = stringResource(
                                    if (module.remove) R.string.runtime_reboot
                                    else R.string.runtime_uninstall
                                ),
                                modifier = Modifier.size(20.dp),
                                tint = if (module.remove) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RuntimeModuleFileAccessDialog(
    onDismiss: () -> Unit,
    onGrantAccess: () -> Unit,
    onUseSystemPicker: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.FolderOpen, null) },
        title = { Text(stringResource(R.string.runtime_file_access_required)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.runtime_file_access_vendor_picker_warning))
                Text(
                    text = stringResource(R.string.runtime_file_access_desc),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            Button(onClick = onGrantAccess) {
                Text(stringResource(R.string.runtime_grant_permission))
            }
        },
        dismissButton = {
            TextButton(onClick = onUseSystemPicker) {
                Text(stringResource(R.string.runtime_system_picker))
            }
        }
    )
}

@Composable
private fun RuntimeModuleInstallConfirmDialog(
    uri: Uri,
    displayName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.UploadFile, null) },
        title = { Text(stringResource(R.string.runtime_confirm_flash_module)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = uri.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.runtime_confirm_flash_module_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Icon(Icons.Default.UploadFile, null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.runtime_confirm_flash))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun RuntimeModuleUpdateConfirmDialog(
    target: RuntimeModuleUpdateTarget,
    downloadDirectoryPath: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val module = target.module
    val updateInfo = target.updateInfo
    val changelogScroll = rememberScrollState()
    var changelogText by remember(updateInfo.changelog) { mutableStateOf(updateInfo.changelog) }

    val context = LocalContext.current

    LaunchedEffect(updateInfo.changelog) {
        changelogText = try {
            if (updateInfo.changelog.isBlank()) {
                ""
            } else {
                resolveRuntimeModuleChangelog(updateInfo.changelog)
            }
        } catch (_: Exception) {
            context.getString(R.string.runtime_update_changelog_unavailable)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Download, contentDescription = stringResource(R.string.runtime_update_module)) },
        title = { Text(stringResource(R.string.runtime_update_module)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = module.displayName(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(
                        R.string.runtime_update_version_change,
                        module.version.ifBlank { "unknown" },
                        updateInfo.version.ifBlank { "unknown" }
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
                RuntimeModuleUpdateChangelog(
                    changelog = changelogText,
                    scrollState = changelogScroll
                )
                Text(
                    text = stringResource(R.string.runtime_confirm_update_module_desc, downloadDirectoryPath),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.runtime_update_module))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun RuntimeModuleUpdateChangelog(
    changelog: String,
    scrollState: ScrollState
) {
    val colorScheme = MaterialTheme.colorScheme
    val uriHandler = LocalUriHandler.current
    val annotatedChangelog = remember(changelog, colorScheme.primary, colorScheme.surfaceVariant) {
        buildRuntimeMarkdownAnnotatedString(
            markdown = changelog.ifBlank { "-" },
            linkColor = colorScheme.primary,
            codeBackground = colorScheme.surfaceVariant,
        )
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 180.dp, max = 360.dp),
        shape = RoundedCornerShape(12.dp),
        color = uiSurfaceColor(MaterialTheme.colorScheme.surfaceContainerHighest),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.runtime_update_changelog),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ClickableText(
                text = annotatedChangelog,
                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface),
                onClick = { offset ->
                    annotatedChangelog
                        .getStringAnnotations(RUNTIME_MARKDOWN_URL_TAG, offset, offset)
                        .firstOrNull()
                        ?.let { annotation -> uriHandler.openUri(annotation.item) }
                }
            )
        }
    }
}

private fun buildRuntimeMarkdownAnnotatedString(
    markdown: String,
    linkColor: Color,
    codeBackground: Color,
): AnnotatedString {
    val builder = AnnotatedString.Builder()
    val lines = markdown.replace("\r\n", "\n").replace('\r', '\n').lines()
    var inCodeBlock = false

    lines.forEachIndexed { index, rawLine ->
        val trimmed = rawLine.trimStart()
        if (trimmed.startsWith("```")) {
            inCodeBlock = !inCodeBlock
            if (index != lines.lastIndex) builder.append('\n')
            return@forEachIndexed
        }

        val lineStart = builder.length
        if (inCodeBlock) {
            builder.append(rawLine.ifBlank { " " })
            if (builder.length > lineStart) {
                builder.addStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = codeBackground,
                    ),
                    lineStart,
                    builder.length,
                )
            }
        } else {
            val headingLevel = trimmed.takeWhile { it == '#' }.length
            when {
                headingLevel in 1..6 && trimmed.getOrNull(headingLevel) == ' ' -> {
                    appendRuntimeMarkdownInline(builder, trimmed.drop(headingLevel + 1), linkColor, codeBackground)
                    if (builder.length > lineStart) {
                        builder.addStyle(
                            SpanStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = when (headingLevel) {
                                    1 -> 20.sp
                                    2 -> 18.sp
                                    3 -> 16.sp
                                    else -> 14.sp
                                },
                            ),
                            lineStart,
                            builder.length,
                        )
                    }
                }
                trimmed.startsWith(">") -> {
                    appendRuntimeMarkdownInline(builder, trimmed.removePrefix("> ").removePrefix(">"), linkColor, codeBackground)
                    if (builder.length > lineStart) {
                        builder.addStyle(
                            SpanStyle(
                                fontStyle = FontStyle.Italic,
                                color = Color.Gray,
                            ),
                            lineStart,
                            builder.length,
                        )
                    }
                }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ") -> {
                    builder.append("• ")
                    appendRuntimeMarkdownInline(builder, trimmed.drop(2), linkColor, codeBackground)
                }
                RUNTIME_MARKDOWN_ORDERED_LIST_REGEX.containsMatchIn(trimmed) -> {
                    appendRuntimeMarkdownInline(builder, trimmed, linkColor, codeBackground)
                }
                else -> {
                    appendRuntimeMarkdownInline(builder, rawLine, linkColor, codeBackground)
                }
            }
        }

        if (index != lines.lastIndex) builder.append('\n')
    }

    return builder.toAnnotatedString()
}

private fun appendRuntimeMarkdownInline(
    builder: AnnotatedString.Builder,
    text: String,
    linkColor: Color,
    codeBackground: Color,
) {
    var index = 0
    while (index < text.length) {
        val markdownLink = runtimeMarkdownLinkAt(text, index)
        if (markdownLink != null) {
            builder.pushStringAnnotation(RUNTIME_MARKDOWN_URL_TAG, markdownLink.second)
            builder.pushStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
            builder.append(markdownLink.first)
            builder.pop()
            builder.pop()
            index = markdownLink.third
            continue
        }

        val bareUrl = RUNTIME_MARKDOWN_BARE_URL_REGEX.find(text, index)
        if (bareUrl != null && bareUrl.range.first == index) {
            val url = bareUrl.value
            builder.pushStringAnnotation(RUNTIME_MARKDOWN_URL_TAG, url)
            builder.pushStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
            builder.append(url)
            builder.pop()
            builder.pop()
            index = bareUrl.range.last + 1
            continue
        }

        val bold = runtimeMarkdownDelimitedSegment(text, index, "**")
        if (bold != null) {
            builder.pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
            builder.append(bold.first)
            builder.pop()
            index = bold.second
            continue
        }

        val code = runtimeMarkdownDelimitedSegment(text, index, "`")
        if (code != null) {
            builder.pushStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = codeBackground,
                )
            )
            builder.append(code.first)
            builder.pop()
            index = code.second
            continue
        }

        val italicStar = runtimeMarkdownDelimitedSegment(text, index, "*")
        if (italicStar != null) {
            builder.pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
            builder.append(italicStar.first)
            builder.pop()
            index = italicStar.second
            continue
        }

        val italicUnderline = runtimeMarkdownDelimitedSegment(text, index, "_")
        if (italicUnderline != null) {
            builder.pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
            builder.append(italicUnderline.first)
            builder.pop()
            index = italicUnderline.second
            continue
        }

        builder.append(text[index])
        index += 1
    }
}

private fun runtimeMarkdownLinkAt(text: String, index: Int): Triple<String, String, Int>? {
    if (text.getOrNull(index) != '[') return null
    val labelEnd = text.indexOf(']', startIndex = index + 1)
    if (labelEnd <= index + 1 || text.getOrNull(labelEnd + 1) != '(') return null
    val urlEnd = text.indexOf(')', startIndex = labelEnd + 2)
    if (urlEnd <= labelEnd + 2) return null
    val label = text.substring(index + 1, labelEnd)
    val url = text.substring(labelEnd + 2, urlEnd)
    if (!url.startsWith("https://") && !url.startsWith("http://")) return null
    return Triple(label, url, urlEnd + 1)
}

private fun runtimeMarkdownDelimitedSegment(
    text: String,
    index: Int,
    delimiter: String,
): Pair<String, Int>? {
    if (!text.startsWith(delimiter, startIndex = index)) return null
    val end = text.indexOf(delimiter, startIndex = index + delimiter.length)
    if (end <= index + delimiter.length) return null
    return text.substring(index + delimiter.length, end) to (end + delimiter.length)
}
@Composable
private fun RuntimeModuleUninstallConfirmDialog(
    module: AbkRuntimeModule,
    pending: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val title = if (pending) stringResource(R.string.runtime_confirm_uninstall_module) else stringResource(R.string.runtime_revoke_uninstall_module)
    val message = if (pending) {
        stringResource(R.string.runtime_confirm_uninstall_module_desc)
    } else {
        stringResource(R.string.runtime_revoke_uninstall_module_desc)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                if (pending) Icons.Default.Delete else Icons.Default.RestartAlt,
                null,
                tint = if (pending) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = module.displayName(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = module.id,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = if (pending) {
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                } else {
                    ButtonDefaults.buttonColors()
                }
            ) {
                Icon(
                    if (pending) Icons.Default.Delete else Icons.Default.RestartAlt,
                    null,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(if (pending) stringResource(R.string.runtime_uninstall) else stringResource(R.string.runtime_revoke))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun RuntimeModuleInstallDialog(
    running: Boolean,
    success: Boolean?,
    logLines: List<String>,
    onClose: () -> Unit,
    onReboot: () -> Unit
) {
    val terminalScroll = rememberScrollState()
    val colorScheme = MaterialTheme.colorScheme
    val isLightTheme = colorScheme.surface.luminance() > 0.5f
    val terminalContainer = if (isLightTheme) {
        colorScheme.surfaceContainerHighest
    } else {
        colorScheme.surfaceContainerLowest
    }

    LaunchedEffect(logLines.size) {
        terminalScroll.animateScrollTo(terminalScroll.maxValue)
    }

    AlertDialog(
        onDismissRequest = { if (!running) onClose() },
        icon = {
            when {
                running -> LoadingIndicator(modifier = Modifier.size(24.dp))
                success == true -> Icon(Icons.Default.CheckCircle, null, tint = colorScheme.primary)
                success == false -> Icon(Icons.Default.Error, null, tint = colorScheme.error)
                else -> Icon(Icons.Default.UploadFile, null)
            }
        },
        title = {
            Text(if (running) stringResource(R.string.runtime_installing_module) else stringResource(R.string.runtime_install_module))
        },
        text = {
            Surface(
                modifier = Modifier.fillMaxWidth().heightIn(min = 190.dp, max = 360.dp),
                shape = RoundedCornerShape(12.dp),
                color = terminalContainer,
                contentColor = colorScheme.onSurface,
                border = BorderStroke(1.dp, colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(terminalScroll)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    logLines.ifEmpty { listOf(stringResource(R.string.runtime_waiting_output)) }.forEach { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = if (line.startsWith("${'$'}")) colorScheme.primary else colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (running) {
                TextButton(onClick = {}, enabled = false) { Text(stringResource(R.string.runtime_running)) }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onClose) { Text(stringResource(R.string.close)) }
                    if (success == true) {
                        Button(
                            onClick = onReboot,
                            colors = ButtonDefaults.buttonColors(containerColor = colorScheme.error)
                        ) {
                            Icon(Icons.Default.RestartAlt, null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.runtime_reboot))
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun RuntimeModuleChip(label: String, secondary: Boolean = false) {
    val accentColor = if (secondary) {
        MaterialTheme.colorScheme.secondary
    } else {
        MaterialTheme.colorScheme.primary
    }
    Surface(
        shape = RoundedCornerShape(7.dp),
        color = accentColor.copy(alpha = 0.10f),
        contentColor = accentColor,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.62f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (!secondary) {
                Icon(Icons.Default.Extension, null, modifier = Modifier.size(14.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (secondary) FontWeight.Medium else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun AbkRuntimeModule.matchesRuntimeModuleQuery(query: String): Boolean {
    val cleanQuery = query.trim()
    if (cleanQuery.isBlank()) return true
    return listOf(id, name, version, description, repoUrl, stage, type, source, author)
        .any { it.contains(cleanQuery, ignoreCase = true) }
}

private fun AbkRuntimeModule.displayName(): String =
    name.ifBlank { id.ifBlank { repoName() } }

private fun AbkRuntimeModule.repoName(): String =
    repoUrl
        .trim()
        .trimEnd('/')
        .removeSuffix(".git")
        .substringAfterLast('/')
        .ifBlank { "unknown" }

@Composable
private fun runtimeFeatureLabel(key: String): String = when (key) {
    "use_zram" -> "ZRAM"
    "use_bbg" -> "BBG"
    "use_ddk" -> "DDK"
    "use_ntsync" -> "NTsync"
    "use_networking" -> stringResource(R.string.runtime_feature_networking)
    "use_kpm" -> "KPM"
    "use_rekernel" -> "Re-Kernel"
    "enable_susfs" -> "SUSFS"
    "supp_op" -> "SukiSU SUS_SU"
    "zram_full_algo" -> stringResource(R.string.runtime_feature_zram_full_algo)
    "cancel_susfs" -> stringResource(R.string.runtime_feature_cancel_susfs)
    else -> key
}

@Composable
private fun runtimeCapabilityLabel(key: String): String =
    if (key == internalRuntimeControlCapability()) {
        stringResource(R.string.runtime_cap_abk_control)
    } else {
        when (key) {
            "root_shell" -> "Root Shell"
            "native_manager" -> stringResource(R.string.runtime_cap_native_manager)
            "root_policy" -> stringResource(R.string.runtime_cap_root_policy)
            "superuser_profiles" -> stringResource(R.string.runtime_cap_superuser_profiles)
            "lkm" -> "LKM"
            "late_load" -> "Late Load"
            "safe_mode" -> stringResource(R.string.runtime_cap_safe_mode)
            "modules" -> stringResource(R.string.runtime_cap_modules)
            "module_control" -> stringResource(R.string.runtime_cap_module_control)
            "susfs" -> "SUSFS"
            "kpm" -> "KPM"
            "features" -> stringResource(R.string.runtime_cap_features)
            else -> key
        }
    }

@Composable
private fun runtimeBackendLabel(backend: String): String = when (backend) {
    "native" -> stringResource(R.string.runtime_backend_native)
    "ksud" -> stringResource(R.string.runtime_backend_ksud)
    "su" -> stringResource(R.string.runtime_backend_su)
    "kernel" -> stringResource(R.string.runtime_backend_kernel)
    else -> backend
}

private fun runtimeWorkModeLabel(workMode: String): String = when (workMode) {
    "lkm" -> "LKM"
    "built-in" -> "Built-in"
    else -> workMode
}

private fun runtimeModuleSourceLabel(source: String): String {
    val labels = source
        .split(',')
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .map {
            when (it) {
                "ksud" -> "KSU"
                "abk" -> "ABK"
                else -> it
            }
        }
    return labels.joinToString("+")
}

@Composable
private fun runtimeModuleTypeLabel(module: AbkRuntimeModule): String = when (module.normalizedType()) {
    "standard" -> stringResource(R.string.runtime_module_type_standard)
    "builtin" -> stringResource(R.string.runtime_module_type_builtin)
    "kpm" -> "KPM"
    else -> module.normalizedType()
}

private fun AbkRuntimeModule.normalizedType(): String =
    type.ifBlank {
        when {
            source.split(',').any { it.trim() == "kpm" } -> "kpm"
            source.split(',').any { it.trim() == "ksud" } -> "standard"
            else -> "builtin"
        }
    }

private fun AbkRuntimeModule.canUninstallRuntimeModule(): Boolean =
    normalizedType() == "standard" && controllable && !readonly

private data class RuntimeModuleDisplayGroup(
    val groupName: String? = null,
    val groupDescription: String? = null,
    val modules: List<AbkRuntimeModule> = emptyList()
)

private fun groupRuntimeModulesForDisplay(modules: List<AbkRuntimeModule>): List<RuntimeModuleDisplayGroup> =
    modules
        .groupBy { module ->
            module.groupRepoUrl.trim()
                .takeIf { it.isNotBlank() }
                ?.let { "repo:${it.lowercase()}" }
                ?: module.groupId.trim().ifBlank {
                    module.groupName.trim()
                        .takeIf { it.isNotBlank() }
                        ?.let { "name:${it.lowercase()}" }
                        ?: "single:${module.id}"
                }
        }
        .values
        .map { grouped ->
            val first = grouped.first()
            RuntimeModuleDisplayGroup(
                groupName = first.groupName.trim().takeIf { it.isNotBlank() },
                groupDescription = first.groupDescription.trim().takeIf { it.isNotBlank() },
                modules = grouped.sortedBy { it.displayName().lowercase() }
            )
        }
        .sortedBy { it.groupName.orEmpty().lowercase() }

private fun internalRuntimeControlCapability(): String =
    intArrayOf(97, 98, 107, 95, 99, 111, 110, 116, 114, 111, 108)
        .map { it.toChar() }
        .joinToString("")

private val MODULE_INSTALL_MIME_TYPES = arrayOf(
    "application/zip",
    "application/x-zip",
    "application/octet-stream",
    "application/x-zip-compressed",
    "*/*"
)

private fun hasRuntimeModuleFileAccess(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()

private fun runtimeModuleUriDisplayName(context: Context, uri: Uri): String {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) {
            cursor.getString(index)?.takeIf { it.isNotBlank() }?.let { return it }
        }
    }
    return uri.lastPathSegment
        ?.substringAfterLast('/')
        ?.takeIf { it.isNotBlank() }
        ?: "module.zip"
}

private fun copyRuntimeModuleUriToCache(context: Context, uri: Uri): File {
    val cacheDir = File(context.cacheDir, "runtime-module-install").apply {
        mkdirs()
    }
    cacheDir.listFiles()
        ?.filter { it.isFile && it.name.startsWith("module-") }
        ?.forEach { it.delete() }

    val target = File(cacheDir, "module-${System.currentTimeMillis()}.zip")
    try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: error("module input stream unavailable")
    } catch (error: Throwable) {
        target.delete()
        throw error
    }

    if (target.length() <= 0L) {
        target.delete()
        error("empty module file")
    }
    return target
}
