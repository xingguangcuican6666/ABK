package com.abk.kernel

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import com.abk.kernel.utils.LocaleHelper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.abk.kernel.ui.screens.BuildScreen
import com.abk.kernel.ui.screens.FlashScreen
import com.abk.kernel.ui.screens.InstalledModulesScreen
import com.abk.kernel.ui.screens.ModuleRepositoryScreen
import com.abk.kernel.ui.screens.OobeScreen
import com.abk.kernel.ui.screens.RootAuthorizationScreen
import com.abk.kernel.ui.screens.RuntimeHomeScreen
import com.abk.kernel.ui.screens.SettingsScreen
import com.abk.kernel.ui.screens.StatusScreen
import com.abk.kernel.ui.theme.AbkTheme
import com.abk.kernel.ui.theme.LocalUiSurfaceAlpha
import com.abk.kernel.ui.theme.uiSurfaceColor
import com.abk.kernel.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    private val requestNotifications = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    private var pendingModuleInstallUri by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingModuleInstallUri = extractModuleInstallUri(intent)?.toString()

        setContent {
            val vm: MainViewModel = viewModel()
            val state by vm.uiState.collectAsState()

            LaunchedEffect(Unit) {
                vm.checkRoot()
            }

            LaunchedEffect(state.termsAccepted) {
                if (state.termsAccepted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            LaunchedEffect(state.termsAccepted, state.oobeCompleted) {
                if (state.termsAccepted && !state.oobeCompleted) {
                    vm.maybeShowInitialOobe()
                }
            }

            AbkTheme(
                themeMode = state.themeMode,
                dynamicColorEnabled = state.dynamicColorEnabled,
                customThemeColorArgb = state.customThemeColorArgb,
                customAccentColorArgb = state.customAccentColorArgb
            ) {
                AppBackgroundHost(
                    backgroundUri = state.customBackgroundUri,
                    backgroundEnabled = state.backgroundImageEnabled,
                    uiSurfaceAlpha = state.uiSurfaceAlpha
                ) {
                    when {
                        !state.termsLoaded -> Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.surface
                        ) {}
                        !state.termsAccepted -> TermsAgreementDialog(
                            onAccept = vm::acceptTerms,
                            onDecline = { finishAffinity() }
                        )
                        else -> Box(modifier = Modifier.fillMaxSize()) {
                            AbkMainScaffold(
                                vm = vm,
                                pendingModuleInstallUri = pendingModuleInstallUri,
                                onModuleInstallUriConsumed = { pendingModuleInstallUri = null }
                            )
                            if (state.showSyncPrompt && !state.showOobe) {
                                SyncPromptDialog(
                                    behindBy = state.behindBy,
                                    onSync = vm::syncFork,
                                    onDismiss = vm::dismissSyncPrompt
                                )
                            }
                            if (state.showOobe) {
                                CompositionLocalProvider(LocalUiSurfaceAlpha provides 1f) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(MaterialTheme.colorScheme.surface)
                                            .zIndex(4f)
                                    ) {
                                        OobeScreen(vm)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingModuleInstallUri = extractModuleInstallUri(intent)?.toString()
    }
}

@Composable
private fun SyncPromptDialog(
    behindBy: Int,
    onSync: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sync_title)) },
        text = {
            Text(
                "${stringResource(R.string.sync_desc)}\n\n" +
                    stringResource(R.string.sync_behind_commits, behindBy)
            )
        },
        confirmButton = {
            Button(onClick = onSync) {
                Text(stringResource(R.string.sync_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.skip))
            }
        }
    )
}

@Composable
private fun AppBackgroundHost(
    backgroundUri: String?,
    backgroundEnabled: Boolean,
    uiSurfaceAlpha: Float,
    content: @Composable () -> Unit
) {
    val hasBackground = backgroundEnabled && !backgroundUri.isNullOrBlank()
    val colorScheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.surface)
    ) {
        if (hasBackground) {
            AsyncImage(
                model = backgroundUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        CompositionLocalProvider(
            LocalUiSurfaceAlpha provides if (hasBackground) uiSurfaceAlpha.coerceIn(0f, 1f) else 1f
        ) {
            content()
        }
    }
}

@Composable
private fun TermsAgreementDialog(
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    val scrollState = rememberScrollState()
    val canAccept by remember {
        derivedStateOf { scrollState.maxValue > 0 && scrollState.value >= scrollState.maxValue }
    }

    AlertDialog(
        onDismissRequest = {},
        title = {
            Text(
                text = stringResource(R.string.terms_title),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TermsText(stringResource(R.string.terms_version))
                TermsText(stringResource(R.string.terms_effective_date))
                TermsText(stringResource(R.string.terms_intro))

                TermsSection(
                    stringResource(R.string.terms_section_usage),
                    stringResource(R.string.terms_usage_1),
                    stringResource(R.string.terms_usage_2)
                )
                TermsSection(
                    stringResource(R.string.terms_section_risk),
                    stringResource(R.string.terms_risk_1),
                    stringResource(R.string.terms_risk_2),
                    stringResource(R.string.terms_risk_3)
                )
                TermsSection(
                    stringResource(R.string.terms_section_legal),
                    stringResource(R.string.terms_legal_1),
                    stringResource(R.string.terms_legal_2),
                    stringResource(R.string.terms_legal_3)
                )
                TermsSection(
                    stringResource(R.string.terms_section_third_party),
                    stringResource(R.string.terms_third_party_1),
                    stringResource(R.string.terms_third_party_2),
                    stringResource(R.string.terms_third_party_3)
                )
                TermsSection(
                    stringResource(R.string.terms_section_privacy),
                    stringResource(R.string.terms_privacy_1),
                    stringResource(R.string.terms_privacy_2),
                    stringResource(R.string.terms_privacy_3)
                )
                TermsSection(
                    stringResource(R.string.terms_section_disclaimer),
                    stringResource(R.string.terms_disclaimer_1),
                    stringResource(R.string.terms_disclaimer_2),
                    stringResource(R.string.terms_disclaimer_3)
                )
                TermsText(stringResource(R.string.terms_accept_hint))
            }
        },
        dismissButton = {
            TextButton(onClick = onDecline) {
                Text(stringResource(R.string.terms_decline))
            }
        },
        confirmButton = {
            Button(
                onClick = onAccept,
                enabled = canAccept
            ) {
                Text(
                    if (canAccept) {
                        stringResource(R.string.terms_accept)
                    } else {
                        stringResource(R.string.terms_scroll_bottom)
                    }
                )
            }
        }
    )
}

@Composable
private fun TermsSection(title: String, vararg paragraphs: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
    )
    paragraphs.forEach { paragraph ->
        TermsText(paragraph)
    }
}

@Composable
private fun TermsText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private enum class AbkTab(@StringRes val labelRes: Int) {
    Status(R.string.nav_status),
    Build(R.string.nav_build),
    Modules(R.string.nav_modules),
    Flash(R.string.nav_flash),
    RuntimeHome(R.string.nav_home),
    InstalledModules(R.string.nav_installed_modules),
    RootAuth(R.string.nav_root_auth),
    Settings(R.string.nav_settings)
}

@Composable
private fun AbkMainScaffold(
    vm: MainViewModel,
    pendingModuleInstallUri: String? = null,
    onModuleInstallUriConsumed: () -> Unit = {}
) {
    val state by vm.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    var selectedTab by rememberSaveable { mutableStateOf(AbkTab.Status) }
    var flashDetailPageVisible by rememberSaveable { mutableStateOf(false) }
    var settingsThemePageVisible by rememberSaveable { mutableStateOf(false) }
    var buildPlanPageVisible by rememberSaveable { mutableStateOf(false) }
    var moduleRepositoryPageVisible by rememberSaveable { mutableStateOf(false) }
    var rootAuthDetailPageVisible by rememberSaveable { mutableStateOf(false) }
    var managerPatchPageVisible by rememberSaveable { mutableStateOf(false) }
    var lastBackAt by remember { mutableStateOf(0L) }
    val runtimeNativeManagerActive = state.hasNativeManagerPermission
    val visibleTabs = remember(state.runtimeNavigationEnabled, state.rootGranted, runtimeNativeManagerActive) {
        if (state.runtimeNavigationEnabled) {
            buildList {
                add(AbkTab.RuntimeHome)
                if (state.rootGranted) add(AbkTab.InstalledModules)
                add(AbkTab.Modules)
                if (runtimeNativeManagerActive) add(AbkTab.RootAuth)
                add(AbkTab.Settings)
            }
        } else {
            listOf(AbkTab.Status, AbkTab.Build, AbkTab.Modules, AbkTab.Flash, AbkTab.Settings)
        }
    }
    val activeTab = if (selectedTab in visibleTabs) selectedTab else visibleTabs.first()
    val motionScheme = MaterialTheme.motionScheme
    val density = LocalDensity.current
    var bottomBarHeightPx by remember { mutableIntStateOf(0) }
    val contentPadding = PaddingValues(
        bottom = with(density) { bottomBarHeightPx.toDp() }
    )
    val childPageVisible = when (activeTab) {
        AbkTab.Build -> buildPlanPageVisible
        AbkTab.Modules -> moduleRepositoryPageVisible
        AbkTab.Flash -> flashDetailPageVisible
        AbkTab.Settings -> settingsThemePageVisible
        AbkTab.RootAuth -> rootAuthDetailPageVisible
        AbkTab.RuntimeHome -> managerPatchPageVisible
        else -> false
    }

    LaunchedEffect(pendingModuleInstallUri) {
        if (!pendingModuleInstallUri.isNullOrBlank()) {
            if (!state.runtimeNavigationEnabled) vm.setRuntimeNavigationEnabled(true)
            selectedTab = AbkTab.InstalledModules
        }
    }

    LaunchedEffect(activeTab) {
        when (activeTab) {
            AbkTab.Build -> {
                moduleRepositoryPageVisible = false
                flashDetailPageVisible = false
                settingsThemePageVisible = false
                rootAuthDetailPageVisible = false
                managerPatchPageVisible = false
            }
            AbkTab.Flash -> {
                buildPlanPageVisible = false
                moduleRepositoryPageVisible = false
                settingsThemePageVisible = false
                rootAuthDetailPageVisible = false
                managerPatchPageVisible = false
            }
            AbkTab.Modules -> {
                buildPlanPageVisible = false
                flashDetailPageVisible = false
                settingsThemePageVisible = false
                rootAuthDetailPageVisible = false
                managerPatchPageVisible = false
            }
            AbkTab.Settings -> {
                buildPlanPageVisible = false
                moduleRepositoryPageVisible = false
                flashDetailPageVisible = false
                rootAuthDetailPageVisible = false
                managerPatchPageVisible = false
            }
            AbkTab.RootAuth -> {
                buildPlanPageVisible = false
                moduleRepositoryPageVisible = false
                flashDetailPageVisible = false
                settingsThemePageVisible = false
                managerPatchPageVisible = false
            }
            AbkTab.RuntimeHome -> {
                buildPlanPageVisible = false
                moduleRepositoryPageVisible = false
                flashDetailPageVisible = false
                settingsThemePageVisible = false
                rootAuthDetailPageVisible = false
            }
            else -> {
                buildPlanPageVisible = false
                moduleRepositoryPageVisible = false
                flashDetailPageVisible = false
                settingsThemePageVisible = false
                rootAuthDetailPageVisible = false
                managerPatchPageVisible = false
            }
        }
    }

    LaunchedEffect(visibleTabs, selectedTab, state.runtimeNavigationEnabled) {
        if (selectedTab !in visibleTabs) {
            selectedTab = if (state.runtimeNavigationEnabled) AbkTab.RuntimeHome else AbkTab.Status
        }
    }

    fun handleTopLevelBack() {
        val now = System.currentTimeMillis()
        if (now - lastBackAt <= EXIT_BACK_INTERVAL_MS) {
            context.findActivity()?.finish()
        } else {
            lastBackAt = now
            Toast.makeText(context, context.getString(R.string.press_again_exit), Toast.LENGTH_SHORT).show()
        }
    }

    if (!childPageVisible) {
        BackHandler(onBack = ::handleTopLevelBack)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(uiSurfaceColor(MaterialTheme.colorScheme.surface))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .onSizeChanged { bottomBarHeightPx = it.height }
                .zIndex(if (childPageVisible) 0f else 2f)
        ) {
            NavigationBar(
                containerColor = uiSurfaceColor(MaterialTheme.colorScheme.surfaceContainer),
                tonalElevation = 0.dp
            ) {
                visibleTabs.forEach { tab ->
                    NavigationBarItem(
                        selected = activeTab == tab,
                        onClick = { selectedTab = tab },
                        enabled = !childPageVisible,
                        alwaysShowLabel = false,
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        icon = {
                            Icon(
                                imageVector = when (tab) {
                                    AbkTab.Status -> Icons.Default.Home
                                    AbkTab.Build -> Icons.Default.RocketLaunch
                                    AbkTab.Modules -> Icons.Default.LibraryBooks
                                    AbkTab.Flash -> if (state.rootGranted) Icons.Default.FlashOn else Icons.Default.FolderOpen
                                    AbkTab.RuntimeHome -> Icons.Default.Memory
                                    AbkTab.InstalledModules -> Icons.Default.Extension
                                    AbkTab.RootAuth -> Icons.Default.AdminPanelSettings
                                    AbkTab.Settings -> Icons.Default.Settings
                                },
                                contentDescription = tab.displayLabel(state.rootGranted)
                            )
                        },
                        label = {
                            Text(
                                text = tab.displayLabel(state.rootGranted),
                                maxLines = 2,
                                softWrap = true,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(1f)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AnimatedContent(
                    targetState = activeTab,
                    transitionSpec = {
                        val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                        (
                            fadeIn(animationSpec = motionScheme.defaultEffectsSpec()) +
                                slideInHorizontally(
                                    animationSpec = motionScheme.defaultSpatialSpec()
                                ) { width -> direction * width / 4 }
                            ) togetherWith (
                            fadeOut(animationSpec = motionScheme.fastEffectsSpec()) +
                                slideOutHorizontally(
                                    animationSpec = motionScheme.fastSpatialSpec()
                                ) { width -> -direction * width / 6 }
                            )
                    },
                    label = "abk-tab"
                ) { tab ->
                    when (tab) {
                        AbkTab.Status -> StatusScreen(
                            vm = vm,
                            outerPadding = contentPadding,
                            runtimeNavigationEnabled = state.runtimeNavigationEnabled,
                            onToggleRuntimeNavigation = { vm.setRuntimeNavigationEnabled(true) }
                        )
                        AbkTab.Build -> BuildScreen(
                            vm = vm,
                            outerPadding = contentPadding,
                            onPlanPageVisibleChange = { buildPlanPageVisible = it }
                        )
                        AbkTab.Modules -> ModuleRepositoryScreen(
                            vm = vm,
                            mode = if (state.runtimeNavigationEnabled) {
                                com.abk.kernel.ui.screens.ModuleRepositoryMode.RUNTIME_STANDARD
                            } else {
                                com.abk.kernel.ui.screens.ModuleRepositoryMode.BUILD_ABK
                            },
                            outerPadding = contentPadding,
                            onRepositoryPageVisibleChange = { moduleRepositoryPageVisible = it }
                        )
                        AbkTab.Flash -> FlashScreen(
                            vm = vm,
                            outerPadding = contentPadding,
                            onDetailPageVisibleChange = { flashDetailPageVisible = it }
                        )
                        AbkTab.RuntimeHome -> RuntimeHomeScreen(
                            vm = vm,
                            outerPadding = contentPadding,
                            onSwitchToClassic = { vm.setRuntimeNavigationEnabled(false) },
                            onManagerPatchPageVisibleChange = { managerPatchPageVisible = it }
                        )
                        AbkTab.InstalledModules -> InstalledModulesScreen(
                            vm = vm,
                            outerPadding = contentPadding,
                            pendingModuleInstallUri = pendingModuleInstallUri,
                            onPendingModuleInstallUriConsumed = onModuleInstallUriConsumed
                        )
                        AbkTab.RootAuth -> RootAuthorizationScreen(
                            vm = vm,
                            outerPadding = contentPadding,
                            onDetailPageVisibleChange = { rootAuthDetailPageVisible = it }
                        )
                        AbkTab.Settings -> SettingsScreen(
                            vm = vm,
                            outerPadding = contentPadding,
                            onThemePageVisibleChange = { settingsThemePageVisible = it },
                            onOpenInstalledModules = {
                                if (!state.runtimeNavigationEnabled) vm.setRuntimeNavigationEnabled(true)
                                selectedTab = if (state.rootGranted) {
                                    AbkTab.InstalledModules
                                } else {
                                    AbkTab.RuntimeHome
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AbkTab.displayLabel(rootGranted: Boolean): String = when (this) {
    AbkTab.Flash -> stringResource(if (rootGranted) labelRes else R.string.nav_files)
    else -> stringResource(labelRes)
}

private fun extractModuleInstallUri(intent: Intent?): Uri? {
    if (intent == null) return null
    val uri = when (intent.action) {
        Intent.ACTION_VIEW -> intent.data
        Intent.ACTION_SEND -> intent.streamUri() ?: intent.firstClipUri()
        Intent.ACTION_SEND_MULTIPLE -> intent.streamUris().firstOrNull() ?: intent.firstClipUri()
        else -> null
    } ?: return null
    return uri.takeIf { isLikelyModuleZipIntent(intent.type, it) }
}

private fun Intent.streamUri(): Uri? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
    }

private fun Intent.streamUris(): List<Uri> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
    } else {
        @Suppress("DEPRECATION")
        getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
    }

private fun Intent.firstClipUri(): Uri? =
    clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri

private fun isLikelyModuleZipIntent(mimeType: String?, uri: Uri): Boolean {
    val cleanMime = mimeType?.lowercase().orEmpty()
    val path = uri.toString().lowercase()
    return cleanMime in MODULE_ZIP_MIME_TYPES || path.endsWith(".zip")
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private const val EXIT_BACK_INTERVAL_MS = 2_000L
private val MODULE_ZIP_MIME_TYPES = setOf(
    "application/zip",
    "application/x-zip",
    "application/x-zip-compressed",
    "application/octet-stream"
)
