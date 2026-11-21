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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.xingguangcuican6666.abk.ui.components.AbkIconButton
import io.github.xingguangcuican6666.abk.ui.components.AbkButtonStyle
import io.github.xingguangcuican6666.abk.ui.components.AbkEmptyState
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcons
import com.abk.kernel.ui.screens.flash.FlashAction
import com.abk.kernel.R
import com.abk.kernel.data.model.AbkRuntimeModule
import com.abk.kernel.data.model.downloadFileName
import com.abk.kernel.ui.blur.BlurScreenScaffold
import com.abk.kernel.ui.components.AbkScreenHorizontalPadding
import com.abk.kernel.ui.components.AbkInlineLoadingPill
import com.abk.kernel.ui.components.ObserveChildPageVisibility
import com.abk.kernel.ui.components.childPageOverlayEnterTransition
import com.abk.kernel.ui.components.childPageOverlayExitTransition
import com.abk.kernel.ui.components.childPageScrimExitTransition
import com.abk.kernel.ui.components.rememberChildPageBackController
import com.abk.kernel.ui.components.rememberChildPageOverlayTransition
import com.abk.kernel.ui.components.ExpressiveTopBar
import com.abk.kernel.ui.components.rememberAbkInteractiveRefreshPresentation
import com.abk.kernel.ui.theme.appPageBackgroundColor
import com.abk.kernel.ui.theme.uiSurfaceColor
import com.abk.kernel.ui.webui.ModuleWebUiActivity
import com.abk.kernel.utils.DownloadUtils
import com.abk.kernel.utils.RootUtils
import com.abk.kernel.viewmodel.MainViewModel
import com.abk.kernel.viewmodel.RuntimeModuleUpdateTarget
import com.abk.kernel.viewmodel.findRuntimeModuleUpdateTarget
import com.abk.kernel.viewmodel.resolveRuntimeModuleChangelog
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val RUNTIME_MODULE_DOWNLOAD_RUN_ID = -2_000_000_001L

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
                        AbkIconButton(
                            icon = AbkIcons.Refresh,
                            contentDescription = stringResource(R.string.runtime_refresh),
                            onClick = {
                                refreshPresentation.beginRefresh()
                                vm.refreshAbkRuntimeStatus()
                            },
                        )
                        AbkIconButton(
                            icon = AbkIcons.Home,
                            contentDescription = stringResource(R.string.nav_status),
                            onClick = onSwitchToClassic,
                        )
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
                                RuntimeBuildParametersCard(runtimeStatus, remember { RootUtils.getKernelVersion() })
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
                        AbkIconButton(
                            icon = AbkIcons.Refresh,
                            contentDescription = stringResource(R.string.runtime_refresh_installed_modules),
                            onClick = {
                                refreshPresentation.beginRefresh()
                                vm.refreshAbkRuntimeStatus()
                            },
                        )
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
                FlashAction(
                    label = stringResource(R.string.runtime_install_module),
                    icon = AbkIcons.Upload,
                    onClick = ::launchModulePickerWithPermissionCheck,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !installRunning,
                    style = AbkButtonStyle.Primary,
                )

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
                                        AbkEmptyState(
                                            title = stringResource(R.string.runtime_installed_modules_title),
                                            description = stringResource(
                                                if (query.isBlank()) R.string.runtime_no_reported_modules
                                                else R.string.runtime_no_matching_modules
                                            ),
                                            icon = AbkIcons.Modules,
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
    }

    if (state.abkRuntimeModuleActionTitle != null) {
        RuntimeModuleActionDialog(
            title = state.abkRuntimeModuleActionTitle.orEmpty(),
            running = state.abkRuntimeModuleActionId != null,
            logLines = state.abkRuntimeModuleActionOutput,
            onClose = vm::dismissRuntimeModuleActionOutput,
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
private fun RuntimeModuleUpdateConfirmDialog(
    target: RuntimeModuleUpdateTarget,
    downloadDirectoryPath: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val module = target.module
    val updateInfo = target.updateInfo
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

    RuntimeModuleUpdateContentDialog(
        module = module,
        updateInfo = updateInfo,
        changelog = changelogText,
        downloadDirectoryPath = downloadDirectoryPath,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    )
}

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
