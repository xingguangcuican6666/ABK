package com.abk.kernel.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.abk.kernel.BuildConfig
import com.abk.kernel.R
import com.abk.kernel.ui.components.ExpressiveHeroCard
import com.abk.kernel.ui.components.ExpressiveSectionCard
import com.abk.kernel.ui.components.ExpressiveStatusChip
import com.abk.kernel.ui.components.ExpressiveTopBar
import com.abk.kernel.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel) {
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            icon = { Icon(Icons.Default.Logout, null) },
            title = { Text("退出登录") },
            text = { Text("确认退出 GitHub 账户吗？退出后需重新授权。") },
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            ExpressiveTopBar(
                title = stringResource(R.string.settings_title)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── 账户 ──────────────────────────────────────────────────────
            SettingsGroup(title = stringResource(R.string.settings_account)) {
                state.user?.let { user ->
                    ListItem(
                        headlineContent = { Text(user.login, fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text(user.name ?: user.htmlUrl) },
                        leadingContent = {
                            AsyncImage(
                                model = user.avatarUrl,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp)
                            )
                        },
                        trailingContent = {
                            IconButton(onClick = { showLogoutDialog = true }) {
                                Icon(Icons.Default.Logout, null, tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("Fork 仓库") },
                        supportingContent = { Text(state.forkRepo?.fullName ?: "未 Fork") },
                        leadingContent = { Icon(Icons.Default.ForkRight, null) }
                    )
                } ?: ListItem(
                    headlineContent = { Text("未登录") },
                    leadingContent = { Icon(Icons.Default.AccountCircle, null) }
                )
            }

            // ── 构建 ──────────────────────────────────────────────────────
            SettingsGroup(title = stringResource(R.string.settings_build)) {
                SwitchSettingsItem(
                    icon = Icons.Default.Download,
                    title = stringResource(R.string.settings_auto_download),
                    subtitle = "仅对下一次新提交的构建生效，关闭后不会自动下载",
                    checked = state.autoDownload,
                    onCheckedChange = { vm.setAutoDownload(it) }
                )
                Spacer(Modifier.height(10.dp))
                MirrorSettingsItem(
                    value = state.downloadMirrorBaseUrl,
                    onValueChange = { vm.setDownloadMirrorBaseUrl(it) }
                )
            }

            // ── 通知 ──────────────────────────────────────────────────────
            SettingsGroup(title = stringResource(R.string.settings_notification)) {
                SwitchSettingsItem(
                    icon = Icons.Default.Notifications,
                    title = stringResource(R.string.settings_notify_build),
                    subtitle = "在通知栏显示构建状态",
                    checked = state.notifyBuild,
                    onCheckedChange = { vm.setNotifyBuild(it) }
                )
            }

            // ── 主题 ──────────────────────────────────────────────────────
            SettingsGroup(title = stringResource(R.string.settings_theme)) {
                val themes = listOf(
                    "system" to stringResource(R.string.settings_theme_system),
                    "light" to stringResource(R.string.settings_theme_light),
                    "dark" to stringResource(R.string.settings_theme_dark)
                )
                themes.forEach { (key, label) ->
                    ListItem(
                        headlineContent = { Text(label) },
                        leadingContent = {
                            Icon(
                                when (key) {
                                    "light" -> Icons.Default.LightMode
                                    "dark" -> Icons.Default.DarkMode
                                    else -> Icons.Default.BrightnessMedium
                                }, null
                            )
                        },
                        trailingContent = {
                            RadioButton(
                                selected = state.themeMode == key,
                                onClick = { vm.setThemeMode(key) }
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                        modifier = Modifier.clickable { vm.setThemeMode(key) }
                    )
                    if (key != themes.last().first) HorizontalDivider()
                }
            }

            // ── 关于 ──────────────────────────────────────────────────────
            SettingsGroup(title = stringResource(R.string.settings_about)) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.app_full_name)) },
                    supportingContent = { Text("AnyBase Kernel v${BuildConfig.VERSION_NAME}") },
                    leadingContent = { Icon(Icons.Default.Info, null) }
                )
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text("关于") },
                    supportingContent = { Text("项目入口、源码仓库、上游项目与致谢") },
                    leadingContent = { Icon(Icons.Default.AutoAwesome, null) },
                    trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                    modifier = Modifier.clickable { showAboutDialog = true }
                )
            }

            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun AboutDialog(
    onDismiss: () -> Unit,
    onOpenUrl: (String) -> Unit
) {
    val links = remember { aboutLinks() }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Info, null) },
        title = { Text("关于 AnyBase Kernel") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "AnyBase Kernel 用于构建、分发和管理 GKI KernelSU / SUSFS 内核。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AboutLinkRow(AboutLink("源仓库", sourceRepoUrl()), onOpenUrl)
                AboutSectionTitle("致谢")
                Text(
                    "ABK 基于以下项目、仓库和社区工作继续开发。",
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
    ListItem(
        headlineContent = { Text(link.title, fontWeight = FontWeight.SemiBold) },
        supportingContent = { Text(link.url) },
        leadingContent = { Icon(Icons.Default.Code, null) },
        trailingContent = { Icon(Icons.Default.OpenInBrowser, null) },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        modifier = Modifier.clickable { onOpenUrl(link.url) }
    )
}

private data class AboutLink(
    val title: String,
    val url: String
)

private fun aboutLinks(): List<AboutLink> {
    return listOf(
        AboutLink("上游仓库", BuildConfig.UPSTREAM_REPO_URL),
        AboutLink("顶层仓库", BuildConfig.TOP_LEVEL_REPO_URL),
        AboutLink("KernelSU", "https://github.com/tiann/KernelSU"),
        AboutLink("KernelSU Next", "https://github.com/KernelSU-Next/KernelSU-Next"),
        AboutLink("SukiSU Ultra", "https://github.com/SukiSU-Ultra/SukiSU-Ultra"),
        AboutLink("ReSukiSU", "https://github.com/ReSukiSU/ReSukiSU"),
        AboutLink("SUSFS", "https://gitlab.com/simonpunk/susfs4ksu"),
        AboutLink("SUSFS GitHub 镜像/补丁来源", "https://github.com/ShirkNeko/susfs4ksu"),
        AboutLink("SukiSU patch", "https://github.com/ShirkNeko/SukiSU_patch"),
        AboutLink("AnyKernel3", "https://github.com/WildKernels/AnyKernel3"),
        AboutLink("Kernel patches", "https://github.com/WildKernels/kernel_patches"),
        AboutLink("Action-Build", "https://github.com/Numbersf/Action-Build"),
        AboutLink("SUSFS 模块构建来源", "https://github.com/sidex15/susfs4ksu-module"),
        AboutLink(
            "GCC prebuilts",
            "https://github.com/LineageOS/android_prebuilts_gcc_linux-x86_aarch64_aarch64-linux-gnu-6.4.1"
        ),
        AboutLink("Baseband Guard", "https://github.com/vc-teahouse/Baseband-guard"),
        AboutLink("Re-Kernel", "https://github.com/Sakion-Team/Re-Kernel"),
        AboutLink("KernelSU 官方站点", "https://kernelsu.org/")
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
        title = login?.let { "已连接 GitHub：$it" } ?: "AnyBase Kernel 设置中心",
        subtitle = forkName ?: "管理构建自动化、通知、主题和仓库来源。",
        icon = Icons.Default.Tune,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        badge = {
            ExpressiveStatusChip(
                label = when (themeMode) {
                    "dark" -> "深色主题"
                    "light" -> "浅色主题"
                    else -> "跟随系统"
                },
                icon = Icons.Default.Palette,
                color = MaterialTheme.colorScheme.primary
            )
            ExpressiveStatusChip(
                label = if (forkName != null) "Fork 已连接" else "等待 Fork",
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
            stringResource(R.string.settings_account) -> "GitHub 账户、fork 仓库和退出登录。"
            stringResource(R.string.settings_build) -> "控制构建成功后的自动化行为。"
            stringResource(R.string.settings_notification) -> "同步工作流状态到系统通知。"
            stringResource(R.string.settings_theme) -> "Material 3 Expressive 主题显示模式。"
            else -> "应用版本与源码信息。"
        },
        icon = when (title) {
            stringResource(R.string.settings_account) -> Icons.Default.AccountCircle
            stringResource(R.string.settings_build) -> Icons.Default.Build
            stringResource(R.string.settings_notification) -> Icons.Default.Notifications
            stringResource(R.string.settings_theme) -> Icons.Default.Palette
            else -> Icons.Default.Info
        }
    ) {
        Column { content() }
    }
}

@Composable
private fun SwitchSettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = if (checked) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.76f)
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                icon,
                null,
                tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun MirrorSettingsItem(
    value: String,
    onValueChange: (String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.76f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.Public, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("下载镜像站", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "留空直连 GitHub；填写后会先镜像到 Release 再下载",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                placeholder = { Text("https://hk.gh-proxy.org/") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp)
            )
        }
    }
}
