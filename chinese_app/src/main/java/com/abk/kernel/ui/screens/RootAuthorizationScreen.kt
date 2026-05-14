@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.abk.kernel.ui.screens

import android.graphics.drawable.Drawable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.abk.kernel.data.model.RootGrantApp
import com.abk.kernel.data.model.RootGrantProfile
import com.abk.kernel.ui.components.AbkScreenHorizontalPadding
import com.abk.kernel.ui.components.ExpressiveSectionCard
import com.abk.kernel.ui.components.ExpressiveStatusChip
import com.abk.kernel.ui.components.ExpressiveSwitch
import com.abk.kernel.ui.components.ExpressiveTopBar
import com.abk.kernel.ui.theme.uiSurfaceColor
import com.abk.kernel.viewmodel.MainViewModel

@Composable
fun RootAuthorizationScreen(vm: MainViewModel) {
    val state by vm.uiState.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var showSystemApps by rememberSaveable { mutableStateOf(false) }
    var selectedPackage by rememberSaveable { mutableStateOf<String?>(null) }
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
    val canLeaveDetail = state.rootGrantSavingPackage == null
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    LaunchedEffect(state.runtimeNavigationEnabled, state.abkRuntimeStatus?.runtimeBackend?.backend) {
        if (state.runtimeNavigationEnabled) vm.refreshRootGrantApps()
    }

    BackHandler(enabled = selectedApp != null && canLeaveDetail) {
        selectedPackage = null
    }

    Scaffold(
        containerColor = uiSurfaceColor(MaterialTheme.colorScheme.surface),
        topBar = {
            if (selectedApp == null) {
                ExpressiveTopBar(
                    title = "超级用户",
                    scrollBehavior = scrollBehavior,
                    actions = {
                        IconButton(onClick = vm::refreshRootGrantApps) {
                            Icon(Icons.Default.Refresh, contentDescription = "刷新授权列表")
                        }
                    }
                )
            } else {
                ExpressiveTopBar(
                    title = selectedApp.label.ifBlank { selectedApp.packageName },
                    navigationIcon = {
                        IconButton(
                            enabled = canLeaveDetail,
                            onClick = { selectedPackage = null }
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回授权列表")
                        }
                    }
                )
            }
        }
    ) { padding ->
        if (selectedApp != null) {
            RootGrantProfilePage(
                app = selectedApp,
                padding = padding,
                saving = state.rootGrantSavingPackage == selectedApp.packageName,
                onSave = { profile ->
                    vm.saveRootGrantProfile(profile)
                }
            )
        } else {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = AbkScreenHorizontalPadding),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    placeholder = { Text("搜索应用") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )

                ExpressiveSectionCard(
                    title = "Root 授权",
                    subtitle = "管理其他应用的内核权限配置",
                    icon = Icons.Default.AdminPanelSettings
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "显示系统应用",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Switch(checked = showSystemApps, onCheckedChange = { showSystemApps = it })
                    }
                }

                if (state.rootGrantLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                state.rootGrantError?.let {
                    RootGrantMessageCard(it, vm::refreshRootGrantApps)
                }

                if (!state.rootGrantLoading && apps.isEmpty()) {
                    Text(
                        text = if (query.isBlank()) "没有可显示的应用" else "没有匹配的应用",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }

                apps.forEach { app ->
                    RootGrantAppCard(
                        app = app,
                        saving = state.rootGrantSavingPackage == app.packageName,
                        anySaving = state.rootGrantSavingPackage != null,
                        onToggle = { allowed -> vm.setRootGrantAllowed(app.packageName, allowed) },
                        onOpen = { selectedPackage = app.packageName }
                    )
                }

                Spacer(Modifier.height(80.dp))
            }
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
                RootGrantChip(if (app.profile.allowSu) "允许 Root" else "拒绝 Root")
                RootGrantChip(if (app.profile.rootUseDefault) "默认 Root 配置" else "自定义 Root 配置")
                if (app.isSystemApp) RootGrantChip("系统应用")
                if (app.profile.umountModules) RootGrantChip("卸载模块")
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
    val drawable: Drawable? = remember(packageName) {
        runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull()
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
            title = "超级用户",
            subtitle = if (allowSu) "允许请求 Root 权限" else "拒绝 Root 权限",
            icon = Icons.Default.Security
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (allowSu) "已允许" else "未允许",
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
                if (rootUseDefault) "默认" else "自定义"
            } else {
                if (nonRootUseDefault) "默认非 Root 配置" else "自定义非 Root 配置"
            },
            icon = Icons.Default.AccountCircle
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (allowSu) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = rootUseDefault,
                            onClick = { rootUseDefault = true },
                            label = { Text("默认") }
                        )
                        FilterChip(
                            selected = !rootUseDefault && rootTemplate.isNotBlank(),
                            onClick = {
                                rootUseDefault = false
                                if (rootTemplate.isBlank()) rootTemplate = "default"
                            },
                            label = { Text("模板") }
                        )
                        FilterChip(
                            selected = !rootUseDefault && rootTemplate.isBlank(),
                            onClick = {
                                rootUseDefault = false
                                rootTemplate = ""
                            },
                            label = { Text("自定义") }
                        )
                    }
                    if (!rootUseDefault && rootTemplate.isNotBlank()) {
                        RootGrantTextField("模板", rootTemplate, { rootTemplate = it }, "模板名称")
                    }
                    if (!rootUseDefault) {
                        RootGrantTextField("UID", uidText, { uidText = it })
                        RootGrantTextField("GID", gidText, { gidText = it })
                        RootGrantTextField("Groups", groupsText, { groupsText = it }, "逗号分隔")
                        RootGrantTextField("Capabilities", capabilitiesText, { capabilitiesText = it }, "逗号分隔")
                        RootGrantTextField("SELinux Context", contextText, { contextText = it })
                        RootGrantTextField("Namespace", namespaceText, { namespaceText = it }, "0 继承 / 1 全局 / 2 独立")
                        RootGrantTextField("SEPolicy Rules", rulesText, { rulesText = it }, "可留空", singleLine = false)
                    }
                } else {
                    RootGrantSwitchRow("使用默认非 Root 配置", nonRootUseDefault) { nonRootUseDefault = it }
                    RootGrantSwitchRow("卸载模块", umountModules) { umountModules = it }
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
                Text("保存")
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
                Text("重新检测")
            }
        }
    }
}

private fun parseIntList(value: String): List<Int> =
    value.split(',', ' ', '\n', '\t')
        .mapNotNull { it.trim().toIntOrNull() }
        .distinct()
