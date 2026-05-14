@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package com.abk.kernel.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Extension
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.abk.kernel.data.model.AbkRuntimeBuildInfo
import com.abk.kernel.data.model.AbkRuntimeModule
import com.abk.kernel.data.model.AbkRuntimeStatus
import com.abk.kernel.ui.components.AbkScreenHorizontalPadding
import com.abk.kernel.ui.components.ExpressiveSwitch
import com.abk.kernel.ui.components.ExpressiveHeroCard
import com.abk.kernel.ui.components.ExpressiveSectionCard
import com.abk.kernel.ui.components.ExpressiveStatusChip
import com.abk.kernel.ui.components.ExpressiveTopBar
import com.abk.kernel.ui.theme.uiSurfaceColor
import com.abk.kernel.ui.webui.ModuleWebUiActivity
import com.abk.kernel.utils.RootUtils
import com.abk.kernel.viewmodel.MainViewModel
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun RuntimeHomeScreen(
    vm: MainViewModel,
    onSwitchToClassic: () -> Unit
) {
    val state by vm.uiState.collectAsState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    LaunchedEffect(state.runtimeNavigationEnabled, state.rootGranted) {
        if (state.runtimeNavigationEnabled) vm.refreshAbkRuntimeStatus()
    }

    Scaffold(
        containerColor = uiSurfaceColor(MaterialTheme.colorScheme.surface),
        topBar = {
            ExpressiveTopBar(
                title = "AnyBase Kernel",
                compactTitle = true,
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = { vm.refreshAbkRuntimeStatus() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh runtime info")
                    }
                    IconButton(onClick = onSwitchToClassic) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = "Switch to full navigation")
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
                .padding(horizontal = AbkScreenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RuntimeStatusHeader(
                runtimeStatus = state.abkRuntimeStatus,
                loading = state.abkRuntimeLoading,
                error = state.abkRuntimeError,
                onRefresh = vm::refreshAbkRuntimeStatus
            )

            state.abkRuntimeStatus?.let { runtimeStatus ->
                RuntimeManagerCard(runtimeStatus)
                RuntimeBuildParametersCard(runtimeStatus)
            }

            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
fun InstalledModulesScreen(vm: MainViewModel) {
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var installDialogVisible by remember { mutableStateOf(false) }
    var installRunning by remember { mutableStateOf(false) }
    var installSuccess by remember { mutableStateOf<Boolean?>(null) }
    var installLog by remember { mutableStateOf<List<String>>(emptyList()) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val modules = remember(state.abkRuntimeStatus?.modules, query) {
        state.abkRuntimeStatus?.modules.orEmpty()
            .filter { it.matchesRuntimeModuleQuery(query) }
            .sortedWith(
                compareBy<AbkRuntimeModule> { it.typeOrder() }
                    .thenBy { !it.enabled }
                    .thenBy { it.displayName().lowercase() }
            )
    }

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
            "Copying module file…"
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
                    appendInstallLog("Waiting for root shell. Do not close the app…")
                    if (!RootUtils.refreshRootState()) {
                        RootUtils.ShellResult(false, listOf("Manager not activated"))
                    } else {
                        RootUtils.installModule(stagedPath, ::appendInstallLog)
                    }
                }.getOrElse {
                    RootUtils.ShellResult(false, listOf("Module file read failed"))
                }.also {
                    stagedFile?.delete()
                }
            }
            installRunning = false
            installSuccess = result.success
            installLog = listOf(
                "${'$'} module install $stagedName",
                "file: ${stagedPath.ifBlank { "temp file not created" }}",
                ""
            ) + result.output.ifEmpty {
                listOf(if (result.success) "Module installation complete, no output." else "Module installation failed, no log returned.")
            }
            if (result.success) vm.refreshAbkRuntimeStatus()
        }
    }

    val modulePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) installModuleFromUri(uri)
    }

    LaunchedEffect(state.runtimeNavigationEnabled, state.rootGranted) {
        if (state.runtimeNavigationEnabled) vm.refreshAbkRuntimeStatus()
    }

    Scaffold(
        containerColor = uiSurfaceColor(MaterialTheme.colorScheme.surface),
        topBar = {
            ExpressiveTopBar(
                title = "Installed modules",
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = { vm.refreshAbkRuntimeStatus() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh installed modules")
                    }
                }
            )
        },
        floatingActionButton = {
            SmallFloatingActionButton(
                onClick = {
                    if (!installRunning) modulePicker.launch(MODULE_INSTALL_MIME_TYPES)
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(Icons.Default.UploadFile, contentDescription = "Install module")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AbkScreenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            RuntimeModuleSearchField(query, onValueChange = { query = it })

            if (state.abkRuntimeLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            state.abkRuntimeError?.let {
                RuntimeErrorCard(
                    message = if (state.abkRuntimeStatus == null) it else "Operation incomplete. Refresh and try again.",
                    onRefresh = vm::refreshAbkRuntimeStatus
                )
            }

            if (state.abkRuntimeStatus != null && modules.isEmpty()) {
                Text(
                    text = if (query.isBlank()) "No ABK external modules reported by current kernel" else "No matching modules",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                modules.forEach { module ->
                    InstalledRuntimeModuleCard(
                        module = module,
                        actionInFlight = state.abkRuntimeModuleActionId == module.id,
                        onSetEnabled = { enabled -> vm.setAbkRuntimeModuleEnabled(module.id, enabled) },
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

            Spacer(Modifier.height(80.dp))
        }
    }

    if (state.abkRuntimeModuleActionTitle != null) {
        AlertDialog(
            onDismissRequest = vm::dismissRuntimeModuleActionOutput,
            confirmButton = {
                TextButton(onClick = vm::dismissRuntimeModuleActionOutput) {
                    Text("Off")
                }
            },
            title = { Text(state.abkRuntimeModuleActionTitle.orEmpty()) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.abkRuntimeModuleActionId != null) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Text(
                        text = state.abkRuntimeModuleActionOutput.ifEmpty { listOf("Waiting for output…") }.joinToString("\n"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
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
    loading: Boolean,
    error: String?,
    onRefresh: () -> Unit
) {
    ExpressiveHeroCard(
        title = if (runtimeStatus != null) "Manager activated" else "Manager not activated",
        subtitle = runtimeStatus?.let {
            val managerName = it.manager?.displayName?.takeIf { name -> name.isNotBlank() } ?: "Root"
            "$managerName · ABK ${it.abkVersion.ifBlank { "unknown" }} · ${it.modules.size} 个模块"
        } ?: (error ?: "Install and enable a manager-compatible kernel to view runtime info"),
        icon = if (runtimeStatus != null) Icons.Default.CheckCircle else Icons.Default.Error,
        containerColor = if (runtimeStatus != null) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        contentColor = if (runtimeStatus != null) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
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
                    Text("Re-check")
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
        title = "Kernel manager",
        subtitle = "Build identity and current runtime backend",
        icon = Icons.Default.Memory
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            RuntimeInfoRow("Type", manager.displayName.ifBlank { manager.variant })
            RuntimeInfoRow("Version", manager.version)
            RuntimeInfoRow("Source", runtimeBackendLabel(manager.backend))
            if (backend != null && backend != manager) {
                Spacer(Modifier.height(2.dp))
                RuntimeInfoRow("Runtime backend", backend.displayName.ifBlank { backend.variant })
                RuntimeInfoRow("Backend version", backend.version)
                RuntimeInfoRow("Compat layer", runtimeBackendLabel(backend.backend))
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
                .map(::runtimeCapabilityLabel)
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
        title = "Current kernel build parameters",
        subtitle = "From compiler-written build record",
        icon = Icons.Default.Tune
    ) {
        if (build == null) {
            Text(
                text = "Current device output uses an older schema without build parameters.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@ExpressiveSectionCard
        }

        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            RuntimeInfoRow("Android", build.androidVersion)
            RuntimeInfoRow("Target kernel", listOf(build.kernelVersion, build.subLevel).filter { it.isNotBlank() }.joinToString("."))
            RuntimeInfoRow("Kernel version", systemKernelVersion)
            RuntimeInfoRow("Patch level", build.osPatchLevel)
            RuntimeInfoRow("Revision", build.revision)
            RuntimeInfoRow("KSU", listOf(build.kernelsuVariant, build.kernelsuBranch).filter { it.isNotBlank() }.joinToString(" / "))
            RuntimeInfoRow("Build time", build.buildTime)
            RuntimeInfoRow("Virtualization", build.virtualizationSupport)
            RuntimeInfoRow("ZRAM extra algorithms", build.zramExtraAlgos)
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
        .map(::runtimeFeatureLabel)
        .ifEmpty { listOf("Base config") }
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
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = uiSurfaceColor(MaterialTheme.colorScheme.errorContainer)
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
                Text("Re-check")
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
        placeholder = { Text("Search installed modules") },
        singleLine = true,
        shape = RoundedCornerShape(14.dp)
    )
}

@Composable
private fun InstalledRuntimeModuleCard(
    module: AbkRuntimeModule,
    actionInFlight: Boolean,
    onSetEnabled: (Boolean) -> Unit,
    onRunAction: () -> Unit,
    onOpenWebUi: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = uiSurfaceColor(MaterialTheme.colorScheme.surfaceContainer)
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
                                append("Version: ")
                                append(module.version)
                                if (module.versionCode > 0) append(" (${module.versionCode})")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (module.author.isNotBlank()) {
                        Text(
                            text = "Author: ${module.author}",
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
                        enabled = !actionInFlight,
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
                RuntimeModuleChip(runtimeModuleTypeLabel(module), secondary = true)
                if (module.stage.isNotBlank()) RuntimeModuleChip(module.stage, secondary = true)
                if (module.source.isNotBlank()) RuntimeModuleChip(runtimeModuleSourceLabel(module.source), secondary = true)
                RuntimeModuleChip(if (module.enabled) "Enabled" else "Disabled", secondary = !module.enabled)
                if (module.update) RuntimeModuleChip("Update pending", secondary = true)
                if (module.remove) RuntimeModuleChip("Removal pending", secondary = true)
                if (module.hasWebUi) RuntimeModuleChip("WebUI", secondary = true)
                if (module.actionSupported || module.hasActionScript) RuntimeModuleChip("Action", secondary = true)
                RuntimeModuleChip(
                    when {
                        module.readonly -> "Read-only"
                        module.controllable -> "Controllable"
                        else -> "Metadata only"
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

            if (module.hasWebUi || module.actionSupported || actionInFlight) {
                Row(
                    modifier = Modifier.align(Alignment.End),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (actionInFlight) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                    if (module.hasWebUi) {
                        IconButton(
                            onClick = onOpenWebUi,
                            enabled = module.enabled && !module.remove && !module.update
                        ) {
                            Icon(Icons.Default.Web, contentDescription = "Open WebUI")
                        }
                    }
                    if (module.actionSupported) {
                        IconButton(
                            onClick = onRunAction,
                            enabled = module.enabled && !actionInFlight
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Run action")
                        }
                    }
                }
            }
        }
    }
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
        title = { Text(if (running) "Installing module…" else "Install module") },
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
                    logLines.ifEmpty { listOf("Waiting for output…") }.forEach { line ->
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
                TextButton(onClick = {}, enabled = false) { Text("Running") }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onClose) { Text("Off") }
                    if (success == true) {
                        Button(
                            onClick = onReboot,
                            colors = ButtonDefaults.buttonColors(containerColor = colorScheme.error)
                        ) {
                            Icon(Icons.Default.RestartAlt, null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Reboot")
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

private fun runtimeFeatureLabel(key: String): String = when (key) {
    "use_zram" -> "ZRAM"
    "use_bbg" -> "BBG"
    "use_ddk" -> "DDK"
    "use_ntsync" -> "NTsync"
    "use_networking" -> "Networking"
    "use_kpm" -> "KPM"
    "use_rekernel" -> "Re-Kernel"
    "enable_susfs" -> "SUSFS"
    "supp_op" -> "SukiSU SUS_SU"
    "zram_full_algo" -> "ZRAM full algorithms"
    "cancel_susfs" -> "SUSFS cancelled"
    else -> key
}

private fun runtimeCapabilityLabel(key: String): String =
    if (key == internalRuntimeControlCapability()) {
        "ABK control"
    } else {
        when (key) {
            "root_shell" -> "Root Shell"
            "native_manager" -> "Native manager"
            "root_policy" -> "Auth config"
            "superuser_profiles" -> "Auth list"
            "lkm" -> "LKM"
            "late_load" -> "Late Load"
            "safe_mode" -> "Safe mode"
            "modules" -> "Module list"
            "module_control" -> "Module control"
            "susfs" -> "SUSFS"
            "kpm" -> "KPM"
            "features" -> "Feature Toggles"
            else -> key
        }
    }

private fun runtimeBackendLabel(backend: String): String = when (backend) {
    "native" -> "Native control"
    "ksud" -> "KSU compat"
    "su" -> "Generic su"
    "kernel" -> "Kernel runtime"
    else -> backend
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

private fun runtimeModuleTypeLabel(module: AbkRuntimeModule): String = when (module.normalizedType()) {
    "standard" -> "Standard module"
    "builtin" -> "Built-in module"
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

private fun AbkRuntimeModule.typeOrder(): Int = when (normalizedType()) {
    "builtin" -> 0
    "standard" -> 1
    "kpm" -> 2
    else -> 3
}

private fun internalRuntimeControlCapability(): String =
    intArrayOf(97, 98, 107, 95, 99, 111, 110, 116, 114, 111, 108)
        .map { it.toChar() }
        .joinToString("")

private val MODULE_INSTALL_MIME_TYPES = arrayOf(
    "application/zip",
    "application/octet-stream",
    "application/x-zip-compressed",
    "*/*"
)

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
