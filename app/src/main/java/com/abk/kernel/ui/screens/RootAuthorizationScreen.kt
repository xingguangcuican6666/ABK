@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package com.abk.kernel.ui.screens

import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.abk.kernel.R
import com.abk.kernel.data.model.RootGrantApp
import com.abk.kernel.data.model.RootGrantProfile
import com.abk.kernel.ui.components.AbkScreenHorizontalPadding
import com.abk.kernel.ui.components.ObserveChildPageVisibility
import com.abk.kernel.ui.components.childPageOverlayEnterTransition
import com.abk.kernel.ui.components.childPageOverlayExitTransition
import com.abk.kernel.ui.components.childPageScrimExitTransition
import com.abk.kernel.ui.components.rememberChildPageBackController
import com.abk.kernel.ui.components.rememberChildPageOverlayTransition
import com.abk.kernel.ui.components.ExpressiveSectionCard
import com.abk.kernel.ui.components.ExpressiveStatusChip
import com.abk.kernel.ui.components.ExpressiveSwitch
import com.abk.kernel.ui.components.ExpressiveTopBar
import com.abk.kernel.ui.theme.uiSurfaceColor
import com.abk.kernel.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun RootAuthorizationScreen(
    vm: MainViewModel,
    outerPadding: PaddingValues = PaddingValues(0.dp),
    onDetailPageVisibleChange: (Boolean) -> Unit = {}
) {
    val state by vm.uiState.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var showSystemApps by rememberSaveable { mutableStateOf(false) }
    var selectedPackage by rememberSaveable { mutableStateOf<String?>(null) }
    val motionScheme = MaterialTheme.motionScheme
    val apps = remember(state.rootGrantApps, query, showSystemApps) {
        state.rootGrantApps
            .filter { showSystemApps || !it.isSystemApp }
            .filter { app ->
                val needle = query.trim()
                needle.isBlank() ||
                    app.label.contains(needle, ignoreCase = true) ||
                    app.packageName.contains(needle, ignoreCase = true) ||
                    app.uid.toString().contains(needle)
            }
    }
    val selectedApp = remember(state.rootGrantApps, selectedPackage) {
        selectedPackage?.let { packageName ->
            state.rootGrantApps.firstOrNull { it.packageName == packageName }
        }
    }
    val detailPageVisible = selectedApp != null
    val detailPageTransition = rememberChildPageOverlayTransition(
        visible = detailPageVisible,
        label = "root-auth-detail"
    )
    val canLeaveDetail = state.rootGrantSavingPackage == null
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    LaunchedEffect(state.runtimeNavigationEnabled, state.abkRuntimeStatus?.runtimeBackend?.backend) {
        if (state.runtimeNavigationEnabled) vm.refreshRootGrantApps()
    }

    fun closeDetailPage() {
        if (canLeaveDetail) selectedPackage = null
    }

    val childPageBack = rememberChildPageBackController(
        enabled = selectedApp != null && canLeaveDetail,
        predictiveBackEnabled = state.predictiveBackEnabled,
        onBack = ::closeDetailPage,
    )

    ObserveChildPageVisibility(
        transition = detailPageTransition,
        onVisibleChange = onDetailPageVisibleChange,
        onAfterExitAnimation = { childPageBack.resetProgress() }
    )

    DisposableEffect(Unit) {
        onDispose { onDetailPageVisibleChange(false) }
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
                    title = stringResource(R.string.root_auth_title),
                    scrollBehavior = scrollBehavior,
                    actions = {
                        IconButton(
                            onClick = { vm.refreshRootGrantApps(force = true) },
                            enabled = !state.rootGrantLoading
                        ) {
                            if (state.rootGrantLoading) {
                                LoadingIndicator(Modifier.size(22.dp))
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.root_auth_refresh_list))
                            }
                        }
                    }
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(
                    start = AbkScreenHorizontalPadding,
                    end = AbkScreenHorizontalPadding,
                    bottom = 80.dp + outerPadding.calculateBottomPadding()
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(key = "search") {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        placeholder = { Text(stringResource(R.string.root_auth_search_apps)) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp)
                    )
                }

                item(key = "controls") {
                    ExpressiveSectionCard(
                        title = stringResource(R.string.root_auth_section_title),
                        subtitle = stringResource(R.string.root_auth_section_desc),
                        icon = Icons.Default.AdminPanelSettings
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.root_auth_show_system_apps),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Switch(checked = showSystemApps, onCheckedChange = { showSystemApps = it })
                        }
                    }
                }

                if (state.rootGrantLoading && state.rootGrantApps.isEmpty()) {
                    item(key = "initial-loading") {
                        RootGrantInitialLoading()
                    }
                }

                if (state.rootGrantLoading && state.rootGrantApps.isNotEmpty()) {
                    item(key = "refreshing") {
                        RootGrantRefreshingRow()
                    }
                }

                state.rootGrantError?.let {
                    item(key = "error") {
                        RootGrantMessageCard(it) { vm.refreshRootGrantApps(force = true) }
                    }
                }

                if (!state.rootGrantLoading && apps.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            text = if (query.isBlank()) {
                                stringResource(R.string.root_auth_no_apps)
                            } else {
                                stringResource(R.string.root_auth_no_matching_apps)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 24.dp)
                        )
                    }
                }

                items(
                    items = apps,
                    key = { app -> "${app.uid}:${app.packageName}" }
                ) { app ->
                    RootGrantAppCard(
                        app = app,
                        saving = state.rootGrantSavingPackage == app.packageName,
                        anySaving = state.rootGrantSavingPackage != null,
                        onToggle = { allowed -> vm.setRootGrantAllowed(app.packageName, allowed) },
                        onOpen = {
                            childPageBack.resetProgress()
                            selectedPackage = app.packageName
                        }
                    )
                }
            }
        }

        detailPageTransition.AnimatedVisibility(
            visible = { it },
            enter = fadeIn(animationSpec = motionScheme.defaultEffectsSpec()),
            exit = childPageScrimExitTransition(state.predictiveBackEnabled, motionScheme),
            modifier = childPageModifier
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = childPageBack.scrimAlpha))
            )
        }

        detailPageTransition.AnimatedVisibility(
            visible = { it },
            enter = childPageOverlayEnterTransition(state.predictiveBackEnabled, motionScheme),
            exit = childPageOverlayExitTransition(state.predictiveBackEnabled, motionScheme),
            modifier = childPageModifier
        ) {
            selectedApp?.let { app ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(childPageBack.backTransformModifier())
                ) {
                    RootGrantDetailPageBackground(
                        backgroundUri = state.customBackgroundUri,
                        backgroundImageEnabled = state.backgroundImageEnabled
                    )
                    Scaffold(
                        containerColor = Color.Transparent,
                        topBar = {
                            ExpressiveTopBar(
                                title = app.label.ifBlank { app.packageName },
                                navigationIcon = {
                                    IconButton(
                                        enabled = canLeaveDetail,
                                        onClick = childPageBack::requestDismiss
                                    ) {
                                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.root_auth_back_to_list))
                                    }
                                }
                            )
                        }
                    ) { padding ->
                        RootGrantProfilePage(
                            app = app,
                            padding = padding,
                            saving = state.rootGrantSavingPackage == app.packageName,
                            onSave = { profile ->
                                vm.saveRootGrantProfile(profile)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RootGrantInitialLoading() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LoadingIndicator(Modifier.size(42.dp))
            Text(
                text = stringResource(R.string.root_auth_building_list),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RootGrantRefreshingRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LoadingIndicator(Modifier.size(24.dp))
        Text(
            text = stringResource(R.string.root_auth_refreshing_list),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RootGrantDetailPageBackground(
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
private fun RootGrantAppCard(
    app: RootGrantApp,
    saving: Boolean,
    anySaving: Boolean,
    onToggle: (Boolean) -> Unit,
    onOpen: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = uiSurfaceColor(MaterialTheme.colorScheme.surfaceContainer)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        onClick = onOpen
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                AppIcon(
                    packageName = app.packageName,
                    label = app.label,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = app.label.ifBlank { app.packageName },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = app.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = listOf("UID ${app.uid}", app.userName).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (saving) {
                    CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 2.dp)
                } else {
                    ExpressiveSwitch(
                        checked = app.profile.allowSu,
                        enabled = !anySaving,
                        onCheckedChange = onToggle
                    )
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 10.dp).size(24.dp)
                )
            }

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                RootGrantChip(if (app.profile.allowSu) stringResource(R.string.root_auth_allow) else stringResource(R.string.root_auth_deny))
                RootGrantChip(
                    if (app.profile.rootUseDefault) {
                        stringResource(R.string.root_auth_default_profile)
                    } else {
                        stringResource(R.string.root_auth_custom_profile)
                    }
                )
                if (app.isSystemApp) RootGrantChip(stringResource(R.string.root_auth_system_app))
                if (app.profile.umountModules) RootGrantChip(stringResource(R.string.root_auth_umount_modules))
            }
        }
    }
}

@Composable
private fun AppIcon(
    packageName: String,
    label: String,
    modifier: Modifier = Modifier.size(56.dp),
    cornerSize: Dp = 14.dp
) {
    val context = LocalContext.current
    var drawable by remember(packageName) { mutableStateOf<Drawable?>(null) }
    LaunchedEffect(packageName) {
        drawable = withContext(Dispatchers.IO) {
            runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull()
        }
    }
    val iconModifier = modifier.clip(RoundedCornerShape(cornerSize))
    if (drawable != null) {
        AsyncImage(
            model = drawable,
            contentDescription = label.ifBlank { packageName },
            contentScale = ContentScale.Crop,
            modifier = iconModifier
        )
    } else {
        Box(
            modifier = iconModifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Apps,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
private fun RootGrantProfilePage(
    app: RootGrantApp,
    padding: androidx.compose.foundation.layout.PaddingValues,
    saving: Boolean,
    onSave: (RootGrantProfile) -> Unit
) {
    val profile = app.profile
    var allowSu by rememberSaveable(app.packageName) { mutableStateOf(profile.allowSu) }
    var rootUseDefault by rememberSaveable(app.packageName) { mutableStateOf(profile.rootUseDefault) }
    var rootTemplate by rememberSaveable(app.packageName) { mutableStateOf(profile.rootTemplate) }
    var uidText by rememberSaveable(app.packageName) { mutableStateOf(profile.uid.toString()) }
    var gidText by rememberSaveable(app.packageName) { mutableStateOf(profile.gid.toString()) }
    var groupsText by rememberSaveable(app.packageName) { mutableStateOf(profile.groups.joinToString(",")) }
    var capabilitiesText by rememberSaveable(app.packageName) { mutableStateOf(profile.capabilities.joinToString(",")) }
    var contextText by rememberSaveable(app.packageName) { mutableStateOf(profile.context) }
    var namespaceText by rememberSaveable(app.packageName) { mutableStateOf(profile.namespace.toString()) }
    var nonRootUseDefault by rememberSaveable(app.packageName) { mutableStateOf(profile.nonRootUseDefault) }
    var umountModules by rememberSaveable(app.packageName) { mutableStateOf(profile.umountModules) }
    var rulesText by rememberSaveable(app.packageName) { mutableStateOf(profile.rules) }

    fun saveProfile() {
        onSave(
            profile.copy(
                name = app.packageName,
                currentUid = app.uid,
                allowSu = allowSu,
                rootUseDefault = rootUseDefault,
                rootTemplate = rootTemplate.trim(),
                uid = uidText.toIntOrNull() ?: 0,
                gid = gidText.toIntOrNull() ?: 0,
                groups = parseIntList(groupsText),
                capabilities = parseIntList(capabilitiesText),
                context = contextText.trim().ifBlank { "u:r:ksu:s0" },
                namespace = namespaceText.toIntOrNull() ?: 0,
                nonRootUseDefault = nonRootUseDefault,
                umountModules = umountModules,
                rules = rulesText
            )
        )
    }

    Column(
        modifier = Modifier
            .padding(padding)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AbkScreenHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(
                packageName = app.packageName,
                label = app.label,
                modifier = Modifier.size(64.dp),
                cornerSize = 16.dp
            )
            Spacer(Modifier.width(14.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = app.label.ifBlank { app.packageName },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        ExpressiveSectionCard(
            title = stringResource(R.string.root_auth_title),
            subtitle = if (allowSu) stringResource(R.string.root_auth_allow_request) else stringResource(R.string.root_auth_deny_request),
            icon = Icons.Default.Security
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (allowSu) stringResource(R.string.root_auth_allowed) else stringResource(R.string.root_auth_not_allowed),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                ExpressiveSwitch(
                    checked = allowSu,
                    enabled = !saving,
                    onCheckedChange = { allowSu = it }
                )
            }
        }

        ExpressiveSectionCard(
            title = "App Profile",
            subtitle = if (allowSu) {
                if (rootUseDefault) stringResource(R.string.root_auth_default) else stringResource(R.string.root_auth_custom)
            } else {
                if (nonRootUseDefault) {
                    stringResource(R.string.root_auth_default_non_root)
                } else {
                    stringResource(R.string.root_auth_custom_non_root)
                }
            },
            icon = Icons.Default.AccountCircle
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (allowSu) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = rootUseDefault,
                            onClick = { rootUseDefault = true },
                            label = { Text(stringResource(R.string.root_auth_default)) }
                        )
                        FilterChip(
                            selected = !rootUseDefault && rootTemplate.isNotBlank(),
                            onClick = {
                                rootUseDefault = false
                                if (rootTemplate.isBlank()) rootTemplate = "default"
                            },
                            label = { Text(stringResource(R.string.root_auth_template)) }
                        )
                        FilterChip(
                            selected = !rootUseDefault && rootTemplate.isBlank(),
                            onClick = {
                                rootUseDefault = false
                                rootTemplate = ""
                            },
                            label = { Text(stringResource(R.string.root_auth_custom)) }
                        )
                    }
                    if (!rootUseDefault && rootTemplate.isNotBlank()) {
                        RootGrantTextField(
                            stringResource(R.string.root_auth_template),
                            rootTemplate,
                            { rootTemplate = it },
                            stringResource(R.string.root_auth_template_name)
                        )
                    }
                    if (!rootUseDefault) {
                        RootGrantTextField("UID", uidText, { uidText = it })
                        RootGrantTextField("GID", gidText, { gidText = it })
                        RootGrantTextField("Groups", groupsText, { groupsText = it }, stringResource(R.string.root_auth_comma_separated))
                        RootGrantTextField("Capabilities", capabilitiesText, { capabilitiesText = it }, stringResource(R.string.root_auth_comma_separated))
                        RootGrantTextField("SELinux Context", contextText, { contextText = it })
                        RootGrantTextField("Namespace", namespaceText, { namespaceText = it }, stringResource(R.string.root_auth_namespace_hint))
                        RootGrantTextField("SEPolicy Rules", rulesText, { rulesText = it }, stringResource(R.string.root_auth_optional_empty), singleLine = false)
                    }
                } else {
                    RootGrantSwitchRow(stringResource(R.string.root_auth_use_default_non_root), nonRootUseDefault) { nonRootUseDefault = it }
                    RootGrantSwitchRow(stringResource(R.string.root_auth_umount_modules), umountModules) { umountModules = it }
                }
            }
        }

        Button(
            onClick = ::saveProfile,
            enabled = !saving,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (saving) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.save))
            }
        }

        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun RootGrantSwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun RootGrantTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    singleLine: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = if (placeholder.isNotBlank()) {
            { Text(placeholder) }
        } else {
            null
        },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 4
    )
}

@Composable
private fun RootGrantChip(label: String) {
    ExpressiveStatusChip(
        label = label,
        icon = Icons.Default.Tune,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun RootGrantMessageCard(message: String, onRefresh: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
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
            TextButton(onClick = onRefresh) {
                Text(stringResource(R.string.runtime_recheck))
            }
        }
    }
}

private fun parseIntList(value: String): List<Int> =
    value.split(',', ' ', '\n', '\t')
        .mapNotNull { it.trim().toIntOrNull() }
        .distinct()
