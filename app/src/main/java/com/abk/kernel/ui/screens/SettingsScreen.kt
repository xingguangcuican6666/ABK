@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.abk.kernel.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import coil.compose.AsyncImage
import com.abk.kernel.BuildConfig
import com.abk.kernel.R
import com.abk.kernel.ui.components.AbkScreenHorizontalPadding
import com.abk.kernel.ui.components.ExpressiveHeroCard
import com.abk.kernel.ui.components.ExpressiveListItem
import com.abk.kernel.ui.components.ExpressiveSectionCard
import com.abk.kernel.ui.components.ExpressiveStatusChip
import com.abk.kernel.ui.components.ExpressiveSwitchItem
import com.abk.kernel.ui.components.ExpressiveTopBar
import com.abk.kernel.ui.theme.uiSurfaceColor
import com.abk.kernel.viewmodel.MainUiState
import com.abk.kernel.viewmodel.MainViewModel
import kotlin.math.pow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect

private const val THEME_BACK_VISUAL_EXPONENT = 1.8f
private const val THEME_BACK_SCALE_DELTA = 0.09f
private const val THEME_BACK_SCRIM_ALPHA = 0.32f
private const val THEME_PAGE_EXIT_DELAY_MS = 280L
private val THEME_BACK_MAX_OFFSET = 56.dp
private val THEME_BACK_MAX_CORNER = 32.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    vm: MainViewModel,
    outerPadding: PaddingValues = PaddingValues(0.dp),
    onThemePageVisibleChange: (Boolean) -> Unit = {}
) {
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showThemeSettings by rememberSaveable { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    var themeBackProgress by remember { mutableFloatStateOf(0f) }
    val motionScheme = MaterialTheme.motionScheme
    val animatedThemeBackProgress by animateFloatAsState(
        targetValue = themeBackProgress.coerceIn(0f, 1f),
        animationSpec = motionScheme.fastSpatialSpec(),
        label = "settings-theme-back-progress"
    )
    val visualThemeBackProgress = animatedThemeBackProgress
        .coerceIn(0f, 1f)
        .pow(THEME_BACK_VISUAL_EXPONENT)
    val density = LocalDensity.current
    val themeBackOffsetPx = with(density) { THEME_BACK_MAX_OFFSET.toPx() }
    val themeBackCorner = with(density) { (THEME_BACK_MAX_CORNER.toPx() * visualThemeBackProgress).toDp() }

    LaunchedEffect(showThemeSettings) {
        if (showThemeSettings) {
            onThemePageVisibleChange(true)
        } else {
            delay(THEME_PAGE_EXIT_DELAY_MS)
            themeBackProgress = 0f
            onThemePageVisibleChange(false)
        }
    }

    DisposableEffect(Unit) {
        onDispose { onThemePageVisibleChange(false) }
    }

    fun openThemeSettings() {
        themeBackProgress = 0f
        onThemePageVisibleChange(true)
        showThemeSettings = true
    }

    fun closeThemeSettings() {
        showThemeSettings = false
    }

    PredictiveBackHandler(enabled = showThemeSettings && state.predictiveBackEnabled) { progress ->
        try {
            progress.collect { backEvent ->
                themeBackProgress = backEvent.progress.coerceIn(0f, 1f)
            }
            closeThemeSettings()
        } catch (_: CancellationException) {
            themeBackProgress = 0f
        }
    }

    BackHandler(enabled = showThemeSettings && !state.predictiveBackEnabled) {
        closeThemeSettings()
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            icon = { Icon(Icons.Default.Logout, null) },
            title = { Text("Sign out") },
            text = { Text("Sign out of GitHub? You will need to re-authorize.") },
            confirmButton = {
                Button(
                    onClick = { showLogoutDialog = false; vm.logout() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (showAboutDialog) {
        AboutDialog(
            onDismiss = { showAboutDialog = false },
            onOpenUrl = { openUrl(context, it) }
        )
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val childPageTopInset = outerPadding.calculateTopPadding()
        val childPageBottomInset = outerPadding.calculateBottomPadding()
        val childPageModifier = Modifier
            .fillMaxWidth()
            .height(maxHeight + childPageTopInset + childPageBottomInset)
            .offset(y = -childPageTopInset)
        Scaffold(
            containerColor = uiSurfaceColor(MaterialTheme.colorScheme.surface),
            topBar = {
                ExpressiveTopBar(
                    title = stringResource(R.string.settings_title),
                    scrollBehavior = scrollBehavior
                )
            }
        ) {
            SettingsMainContent(
                padding = it,
                state = state,
                vm = vm,
                scrollBehavior = scrollBehavior,
                onLogout = { showLogoutDialog = true },
                onOpenThemeSettings = ::openThemeSettings,
                onAbout = { showAboutDialog = true }
            )
        }

        AnimatedVisibility(
            visible = showThemeSettings,
            enter = fadeIn(animationSpec = motionScheme.defaultEffectsSpec()),
            exit = fadeOut(animationSpec = motionScheme.fastEffectsSpec()),
            modifier = childPageModifier
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = THEME_BACK_SCRIM_ALPHA * visualThemeBackProgress))
            )
        }

        AnimatedVisibility(
            visible = showThemeSettings,
            enter = fadeIn(animationSpec = motionScheme.defaultEffectsSpec()) +
                slideInHorizontally(animationSpec = motionScheme.defaultSpatialSpec()) { width -> width / 4 },
            exit = fadeOut(animationSpec = motionScheme.fastEffectsSpec()) +
                slideOutHorizontally(animationSpec = motionScheme.fastSpatialSpec()) { width -> width },
            modifier = childPageModifier
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = themeBackOffsetPx * visualThemeBackProgress
                        scaleX = 1f - THEME_BACK_SCALE_DELTA * visualThemeBackProgress
                        scaleY = 1f - THEME_BACK_SCALE_DELTA * visualThemeBackProgress
                        alpha = 1f - 0.06f * visualThemeBackProgress
                        shape = RoundedCornerShape(themeBackCorner)
                        clip = visualThemeBackProgress > 0.01f
                    }
            ) {
                SettingsPageBackground(
                    backgroundUri = state.customBackgroundUri,
                    backgroundImageEnabled = state.backgroundImageEnabled
                )
                Scaffold(
                    containerColor = Color.Transparent,
                    topBar = {
                        ExpressiveTopBar(
                            title = stringResource(R.string.settings_theme),
                            navigationIcon = {
                                IconButton(onClick = ::closeThemeSettings) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                                }
                            }
                        )
                    }
                ) {
                    ThemeSettingsScreen(
                        padding = it,
                        themeMode = state.themeMode,
                        dynamicColorEnabled = state.dynamicColorEnabled,
                        customThemeColorArgb = state.customThemeColorArgb,
                        customAccentColorArgb = state.customAccentColorArgb,
                        backgroundUri = state.customBackgroundUri,
                        backgroundImageEnabled = state.backgroundImageEnabled,
                        uiSurfaceAlpha = state.uiSurfaceAlpha,
                        onThemeModeChange = { value -> vm.setThemeMode(value) },
                        onDynamicColorEnabledChange = { enabled, themeColor, accentColor ->
                            vm.setDynamicColorEnabled(enabled, themeColor, accentColor)
                        },
                        onCustomThemeColorsChange = { themeColor, accentColor ->
                            vm.setCustomThemeColors(themeColor, accentColor)
                        },
                        onBackgroundImageChange = { uri -> vm.setBackgroundImageUri(uri) },
                        onBackgroundImageEnabledChange = { enabled -> vm.setBackgroundImageEnabled(enabled) },
                        onUiSurfaceAlphaChange = { alpha -> vm.setUiSurfaceAlpha(alpha) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsPageBackground(
    backgroundUri: String?,
    backgroundImageEnabled: Boolean
) {
    val colorScheme = MaterialTheme.colorScheme
    val hasBackground = backgroundImageEnabled && !backgroundUri.isNullOrBlank()
    val scrimColor = if (colorScheme.surface.luminance() > 0.5f) {
        colorScheme.surface.copy(alpha = 0.28f)
    } else {
        Color.Black.copy(alpha = 0.38f)
    }
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(scrimColor)
            )
        }
    }
}

@Composable
private fun SettingsMainContent(
    padding: PaddingValues,
    state: MainUiState,
    vm: MainViewModel,
    scrollBehavior: TopAppBarScrollBehavior,
    onLogout: () -> Unit,
    onOpenThemeSettings: () -> Unit,
    onAbout: () -> Unit
) {
    Column(
        modifier = Modifier
            .padding(padding)
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AbkScreenHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SettingsGroup(title = stringResource(R.string.settings_account)) {
            state.user?.let { user ->
                ExpressiveListItem(
                    title = user.login,
                    subtitle = user.name ?: user.htmlUrl,
                    leadingContent = {
                        AsyncImage(
                            model = user.avatarUrl,
                            contentDescription = null,
                            modifier = Modifier.size(42.dp)
                                .clip(CircleShape)
                        )
                    },
                    trailingContent = {
                        IconButton(onClick = onLogout) {
                            Icon(
                                Icons.Default.Logout,
                                contentDescription = "Sign out",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                )
                ExpressiveListItem(
                    title = "Forked repository",
                    subtitle = state.forkRepo?.fullName ?: "Not forked",
                    leadingIcon = Icons.Default.ForkRight
                )
            } ?: ExpressiveListItem(
                title = "Not signed in",
                leadingIcon = Icons.Default.AccountCircle
            )
        }

        SettingsGroup(title = stringResource(R.string.settings_build)) {
            SwitchSettingsItem(
                icon = Icons.Default.Download,
                title = stringResource(R.string.settings_auto_download),
                subtitle = "Applies to the next new build submission. Disabling stops auto-download.",
                checked = state.autoDownload,
                onCheckedChange = { vm.setAutoDownload(it) }
            )
            SwitchSettingsItem(
                icon = Icons.Default.CloudDownload,
                title = "Prebuilt GKI fetch & download",
                subtitle = "Fetch prebuilt GKI from this repository's releases. Downloads are manually triggered.",
                checked = state.prebuiltGkiEnabled,
                onCheckedChange = { vm.setPrebuiltGkiEnabled(it) }
            )
            Spacer(Modifier.height(10.dp))
            MirrorSettingsItem(
                value = state.downloadMirrorBaseUrl,
                onValueChange = { vm.setDownloadMirrorBaseUrl(it) }
            )
        }

        SettingsGroup(title = stringResource(R.string.settings_notification)) {
            SwitchSettingsItem(
                icon = Icons.Default.Notifications,
                title = stringResource(R.string.settings_notify_build),
                subtitle = "Show build status in notification bar",
                checked = state.notifyBuild,
                onCheckedChange = { vm.setNotifyBuild(it) }
            )
        }

        SettingsGroup(title = "Navigation") {
            SwitchSettingsItem(
                icon = Icons.Default.ArrowBack,
                title = "In-app M3E back animation",
                subtitle = "Use Material 3 Expressive page-back animation without relying on system predictive back.",
                checked = state.predictiveBackEnabled,
                onCheckedChange = { vm.setPredictiveBackEnabled(it) }
            )
        }

        SettingsGroup(title = stringResource(R.string.settings_theme)) {
            ExpressiveListItem(
                title = "Color & Appearance",
                subtitle = "${themeModeLabel(state.themeMode)} · ${dynamicColorLabel(state.dynamicColorEnabled)}",
                leadingIcon = Icons.Default.Palette,
                trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = "Open Color & Appearance") },
                onClick = onOpenThemeSettings
            )
        }

        SettingsGroup(title = stringResource(R.string.settings_about)) {
            ExpressiveListItem(
                title = stringResource(R.string.app_full_name),
                subtitle = "AnyBase Kernel v${BuildConfig.VERSION_NAME}",
                leadingIcon = Icons.Default.Info
            )
            ExpressiveListItem(
                title = "About",
                subtitle = "Project page, source repository, upstream projects and credits.",
                leadingIcon = Icons.Default.AutoAwesome,
                trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = "Open About") },
                onClick = onAbout
            )
        }

        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun ThemeSettingsScreen(
    padding: PaddingValues,
    themeMode: String,
    dynamicColorEnabled: Boolean,
    customThemeColorArgb: Int?,
    customAccentColorArgb: Int?,
    backgroundUri: String?,
    backgroundImageEnabled: Boolean,
    uiSurfaceAlpha: Float,
    onThemeModeChange: (String) -> Unit,
    onDynamicColorEnabledChange: (Boolean, Int?, Int?) -> Unit,
    onCustomThemeColorsChange: (Int, Int) -> Unit,
    onBackgroundImageChange: (String?) -> Unit,
    onBackgroundImageEnabledChange: (Boolean) -> Unit,
    onUiSurfaceAlphaChange: (Float) -> Unit
) {
    val context = LocalContext.current
    val dynamicColorAvailable = isDynamicColorAvailable()
    val effectiveDynamicColorEnabled = dynamicColorAvailable && dynamicColorEnabled
    val colorScheme = MaterialTheme.colorScheme
    val selectedThemeColorArgb = customThemeColorArgb ?: colorScheme.primary.toArgb()
    val selectedAccentColorArgb = customAccentColorArgb ?: colorScheme.secondary.toArgb()
    val backgroundPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            onBackgroundImageChange(uri.toString())
        }
    }
    val themes = listOf(
        Triple("system", stringResource(R.string.settings_theme_system), Icons.Default.BrightnessMedium),
        Triple("light", stringResource(R.string.settings_theme_light), Icons.Default.LightMode),
        Triple("dark", stringResource(R.string.settings_theme_dark), Icons.Default.DarkMode)
    )

    Column(
        modifier = Modifier
            .padding(padding)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AbkScreenHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SettingsGroup(title = "Appearance mode") {
            themes.forEach { (key, label, icon) ->
                val selected = themeMode == key
                ExpressiveListItem(
                    title = label,
                    leadingIcon = icon,
                    selected = selected,
                    trailingContent = {
                        if (selected) {
                            Icon(Icons.Default.Check, null)
                        }
                    },
                    onClick = { onThemeModeChange(key) }
                )
            }
        }

        SettingsGroup(title = "Color source") {
            SwitchSettingsItem(
                icon = Icons.Default.AutoAwesome,
                title = "Material You",
                subtitle = if (dynamicColorAvailable) {
                    "Dynamic colors generated from the system wallpaper (Material You)."
                } else {
                    "Available on Android 12+. Currently using custom palette."
                },
                checked = effectiveDynamicColorEnabled,
                enabled = dynamicColorAvailable,
                onCheckedChange = { enabled ->
                    onDynamicColorEnabledChange(
                        enabled,
                        if (!enabled) colorScheme.primary.toArgb() else null,
                        if (!enabled) colorScheme.secondary.toArgb() else null
                    )
                }
            )
        }

        if (!effectiveDynamicColorEnabled) {
            SettingsGroup(title = "Custom colors") {
                ThemeColorPicker(
                    title = "Primary color",
                    subtitle = "Primary actions, selected states and main highlight areas.",
                    selectedColorArgb = selectedThemeColorArgb,
                    presets = themeColorPresets(),
                    onColorSelected = { color ->
                        onCustomThemeColorsChange(color, selectedAccentColorArgb)
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ThemeColorPicker(
                    title = "Accent color",
                    subtitle = "Secondary states, labels and secondary highlight areas.",
                    selectedColorArgb = selectedAccentColorArgb,
                    presets = themeColorPresets(),
                    onColorSelected = { color ->
                        onCustomThemeColorsChange(selectedThemeColorArgb, color)
                    }
                )
            }
        }

        SettingsGroup(title = "Background") {
            SwitchSettingsItem(
                icon = Icons.Default.Image,
                title = "Custom background",
                subtitle = if (backgroundUri.isNullOrBlank()) {
                    "Select an image to enable the global background."
                } else {
                    "Background image selected"
                },
                checked = backgroundImageEnabled && !backgroundUri.isNullOrBlank(),
                enabled = !backgroundUri.isNullOrBlank(),
                onCheckedChange = onBackgroundImageEnabledChange
            )
            ExpressiveListItem(
                title = if (backgroundUri.isNullOrBlank()) "Choose background image" else "Change background image",
                subtitle = "Select an image from your device as the app background.",
                leadingIcon = Icons.Default.Image,
                onClick = { backgroundPicker.launch(arrayOf("image/*")) }
            )
            if (!backgroundUri.isNullOrBlank()) {
                ExpressiveListItem(
                    title = "Remove background image",
                    subtitle = "Restore solid Material theme background.",
                    leadingIcon = Icons.Default.Delete,
                    onClick = { onBackgroundImageChange(null) }
                )
            }
            BackgroundAlphaControl(
                alpha = uiSurfaceAlpha,
                enabled = backgroundImageEnabled && !backgroundUri.isNullOrBlank(),
                onAlphaChange = onUiSurfaceAlphaChange
            )
        }

        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun BackgroundAlphaControl(
    alpha: Float,
    enabled: Boolean,
    onAlphaChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "UI opacity",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${(alpha.coerceIn(0f, 1f) * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(
            value = alpha.coerceIn(0f, 1f),
            onValueChange = onAlphaChange,
            valueRange = 0f..1f,
            enabled = enabled
        )
        Text(
            text = "Decreasing opacity lets the background show through cards, top bar and bottom bar.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ThemeColorPicker(
    title: String,
    subtitle: String,
    selectedColorArgb: Int,
    presets: List<ThemeColorPreset>,
    onColorSelected: (Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (presets.none { colorsMatch(selectedColorArgb, it.argb) }) {
                ThemeColorSwatch(
                    preset = ThemeColorPreset("Current", selectedColorArgb),
                    selected = true,
                    enabled = false,
                    onClick = {}
                )
            }
            presets.forEach { preset ->
                ThemeColorSwatch(
                    preset = preset,
                    selected = colorsMatch(selectedColorArgb, preset.argb),
                    onClick = { onColorSelected(preset.argb) }
                )
            }
        }
    }
}

@Composable
private fun ThemeColorSwatch(
    preset: ThemeColorPreset,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color(preset.argb))
            .border(
                BorderStroke(if (selected) 3.dp else 1.dp, borderColor),
                CircleShape
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = preset.label,
                tint = readableSwatchContentColor(preset.argb),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

private data class ThemeColorPreset(
    val label: String,
    val argb: Int
)

private fun themeColorPresets(): List<ThemeColorPreset> = listOf(
    ThemeColorPreset("Green", 0xFF8BC34A.toInt()),
    ThemeColorPreset("Blue", 0xFF42A5F5.toInt()),
    ThemeColorPreset("Purple", 0xFF9575CD.toInt()),
    ThemeColorPreset("Pink", 0xFFEC6A9A.toInt()),
    ThemeColorPreset("Orange", 0xFFFFA726.toInt()),
    ThemeColorPreset("Cyan", 0xFF26C6DA.toInt())
)

private fun colorsMatch(left: Int, right: Int): Boolean {
    return (left or 0xFF000000.toInt()) == (right or 0xFF000000.toInt())
}

private fun readableSwatchContentColor(argb: Int): Color {
    return if (ColorUtils.calculateLuminance(argb) > 0.5) {
        Color(0xFF11140F.toInt())
    } else {
        Color(0xFFFFFFFF.toInt())
    }
}

@Composable
private fun themeModeLabel(themeMode: String): String = when (themeMode) {
    "light" -> stringResource(R.string.settings_theme_light)
    "dark" -> stringResource(R.string.settings_theme_dark)
    else -> stringResource(R.string.settings_theme_system)
}

@Composable
private fun dynamicColorLabel(enabled: Boolean): String = when {
    !isDynamicColorAvailable() -> "Material You unavailable"
    enabled -> "Material You"
    else -> "Custom palette"
}

private fun isDynamicColorAvailable(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Composable
private fun AboutDialog(
    onDismiss: () -> Unit,
    onOpenUrl: (String) -> Unit
) {
    val links = remember { aboutLinks() }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Info, null) },
        title = { Text("About AnyBase Kernel") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "AnyBase Kernel is used to build, distribute and manage GKI KernelSU / SUSFS kernels.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AboutLinkRow(AboutLink("Source repository", sourceRepoUrl()), onOpenUrl)
                AboutSectionTitle("Credits")
                Text(
                    "ABK is built on the following projects, repositories and community work.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                links.forEach {
                    AboutLinkRow(it, onOpenUrl)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}

@Composable
private fun AboutSectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun AboutLinkRow(
    link: AboutLink,
    onOpenUrl: (String) -> Unit
) {
    ExpressiveListItem(
        title = link.title,
        subtitle = link.url,
        leadingIcon = Icons.Default.Code,
        trailingContent = { Icon(Icons.Default.OpenInBrowser, null) },
        onClick = { onOpenUrl(link.url) }
    )
}

private data class AboutLink(
    val title: String,
    val url: String
)

private fun aboutLinks(): List<AboutLink> {
    return listOf(
        AboutLink("Upstream repository", BuildConfig.UPSTREAM_REPO_URL),
        AboutLink("Top-level repository", BuildConfig.TOP_LEVEL_REPO_URL),
        AboutLink("KernelSU", "https://github.com/tiann/KernelSU"),
        AboutLink("KernelSU Next", "https://github.com/KernelSU-Next/KernelSU-Next"),
        AboutLink("SukiSU Ultra", "https://github.com/SukiSU-Ultra/SukiSU-Ultra"),
        AboutLink("ReSukiSU", "https://github.com/ReSukiSU/ReSukiSU"),
        AboutLink("SUSFS", "https://gitlab.com/simonpunk/susfs4ksu"),
        AboutLink("SUSFS GitHub mirror / patch source", "https://github.com/ShirkNeko/susfs4ksu"),
        AboutLink("SukiSU patch", "https://github.com/ShirkNeko/SukiSU_patch"),
        AboutLink("AnyKernel3", "https://github.com/WildKernels/AnyKernel3"),
        AboutLink("Kernel patches", "https://github.com/WildKernels/kernel_patches"),
        AboutLink("NTsync / IPSet / BBR source", "https://github.com/WildKernels/kernel_patches"),
        AboutLink("NTsync / IPSet / BBR PR by huime180", "https://github.com/huime180"),
        AboutLink("Action-Build", "https://github.com/Numbersf/Action-Build"),
        AboutLink("SUSFS module build source", "https://github.com/sidex15/susfs4ksu-module"),
        AboutLink(
            "GCC prebuilts",
            "https://github.com/LineageOS/android_prebuilts_gcc_linux-x86_aarch64_aarch64-linux-gnu-6.4.1"
        ),
        AboutLink("Baseband Guard", "https://github.com/vc-teahouse/Baseband-guard"),
        AboutLink("Re-Kernel", "https://github.com/Sakion-Team/Re-Kernel"),
        AboutLink("Droidspaces / Virtualization patch source", "https://github.com/ravindu644/Droidspaces-OSS"),
        AboutLink("KernelSU official site", "https://kernelsu.org/")
    )
}

private fun sourceRepoUrl(): String =
    "https://github.com/${BuildConfig.SOURCE_REPO_OWNER}/${BuildConfig.SOURCE_REPO_NAME}"

private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

@Composable
private fun SettingsHero(
    login: String?,
    forkName: String?,
    themeMode: String
) {
    ExpressiveHeroCard(
        title = login?.let { "GitHub connected: $it" } ?: "AnyBase Kernel Settings",
        subtitle = forkName ?: "Manage build automation, notifications, theme and repository source.",
        icon = Icons.Default.Tune,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        badge = {
            ExpressiveStatusChip(
                label = when (themeMode) {
                    "dark" -> "Dark theme"
                    "light" -> "Light theme"
                    else -> "Follow system"
                },
                icon = Icons.Default.Palette,
                color = MaterialTheme.colorScheme.primary
            )
            ExpressiveStatusChip(
                label = if (forkName != null) "Fork connected" else "Awaiting fork",
                icon = Icons.Default.ForkRight,
                color = if (forkName != null) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
            )
        }
    )
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    ExpressiveSectionCard(
        title = title,
        subtitle = when (title) {
            stringResource(R.string.settings_account) -> "GitHub account, forked repository and sign out."
            stringResource(R.string.settings_build) -> "Control automated actions after a successful build."
            stringResource(R.string.settings_notification) -> "Sync workflow status to system notifications."
            "Navigation" -> "Control back gesture and page transition experience."
            stringResource(R.string.settings_theme) -> "Material 3 Expressive theme display mode."
            "Appearance mode" -> "Control light/dark display mode."
            "Color source" -> "Choose system dynamic colors or a custom palette."
            "Custom colors" -> "Primary and accent colors used when Material You is disabled."
            "Background" -> "Choose a background image and adjust the overlay UI transparency."
            else -> "App version and source code info."
        },
        icon = when (title) {
            stringResource(R.string.settings_account) -> Icons.Default.AccountCircle
            stringResource(R.string.settings_build) -> Icons.Default.Build
            stringResource(R.string.settings_notification) -> Icons.Default.Notifications
            "Navigation" -> Icons.Default.ArrowBack
            stringResource(R.string.settings_theme) -> Icons.Default.Palette
            "Appearance mode" -> Icons.Default.BrightnessMedium
            "Color source" -> Icons.Default.AutoAwesome
            "Custom colors" -> Icons.Default.Palette
            "Background" -> Icons.Default.Image
            else -> Icons.Default.Info
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
private fun SwitchSettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    ExpressiveSwitchItem(
        title = title,
        subtitle = subtitle,
        icon = icon,
        checked = checked,
        enabled = enabled,
        onCheckedChange = onCheckedChange
    )
}

@Composable
private fun MirrorSettingsItem(
    value: String,
    onValueChange: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ExpressiveListItem(
            title = "Download mirror",
            subtitle = "Leave blank for direct GitHub connection. If set, downloads will be mirrored via Release first.",
            leadingIcon = Icons.Default.Public
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            placeholder = { Text("https://hk.gh-proxy.org/") },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
