@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.abk.kernel.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abk.kernel.R
import com.abk.kernel.ui.components.ExpressiveHeroCard
import com.abk.kernel.ui.components.ExpressiveSectionCard
import com.abk.kernel.ui.components.ExpressiveStatusChip
import com.abk.kernel.ui.theme.uiSurfaceColor
import com.abk.kernel.viewmodel.AuthStep
import com.abk.kernel.viewmodel.MainViewModel

@Composable
fun AuthGateScreen(vm: MainViewModel) {
    val state by vm.uiState.collectAsState()

    LaunchedEffect(Unit) {
        vm.checkRoot()
    }

    when (state.authStep) {
        AuthStep.CHECK_ROOT -> RootCheckScreen(
            isLoading = state.isLoading,
            onRequestRoot = { vm.requestRoot() }
        )
        AuthStep.LOGIN -> LoginScreen(
            isLoading = state.isLoading,
            userCode = state.userCode,
            verificationUri = state.verificationUri,
            isPolling = state.isPollingToken,
            error = state.error,
            onLogin = { vm.startDeviceFlow() },
            onClearError = { vm.clearError() }
        )
        AuthStep.FORK_CHECK -> ForkCheckScreen(
            isLoading = state.isLoading,
            hasFork = state.forkRepo != null,
            behindBy = state.behindBy,
            showSyncDialog = state.showSyncDialog,
            error = state.error,
            onFork = { vm.forkRepo() },
            onSync = { vm.syncFork() },
            onSkip = { vm.dismissSyncDialog() },
            onClearError = { vm.clearError() }
        )
        AuthStep.READY -> { /* handled by parent */ }
    }
}

// ── Root Check ────────────────────────────────────────────────────────────────

@Composable
private fun RootCheckScreen(isLoading: Boolean, onRequestRoot: () -> Unit) {
    AuthShell {
        ExpressiveHeroCard(
            title = stringResource(R.string.root_check_title),
            subtitle = stringResource(R.string.root_check_desc),
            icon = Icons.Default.AdminPanelSettings,
            badge = {
                ExpressiveStatusChip(
                    label = "Root Required",
                    icon = Icons.Default.Lock,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        )
        ExpressiveSectionCard(
            title = "ABK will check device capabilities",
            subtitle = "Root is only called for flashing, module installation and root capability detection. Kernel version detection does not require root.",
            icon = Icons.Default.Security
        ) {
            Text(
                "After granting permission, GitHub login and fork verification will proceed automatically.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Button(
            onClick = onRequestRoot,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            if (isLoading) {
                LoadingIndicator(Modifier.size(24.dp))
            } else {
                Icon(Icons.Default.Lock, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.grant_root))
            }
        }
    }
}

// ── Login ─────────────────────────────────────────────────────────────────────

@Composable
private fun LoginScreen(
    isLoading: Boolean,
    userCode: String?,
    verificationUri: String?,
    isPolling: Boolean,
    error: String?,
    onLogin: () -> Unit,
    onClearError: () -> Unit
) {
    val context = LocalContext.current
    var showConsentDialog by remember { mutableStateOf(false) }

    if (showConsentDialog) {
        AlertDialog(
            onDismissRequest = { showConsentDialog = false },
            icon = { Icon(Icons.Default.VerifiedUser, null) },
            title = { Text("Authorize GitHub Access") },
            text = {
                Text("ABK will request repo and workflow permissions to check/sync your fork and trigger kernel build workflows.")
            },
            confirmButton = {
                Button(onClick = {
                    showConsentDialog = false
                    onLogin()
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showConsentDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    AuthShell {
        ExpressiveHeroCard(
            title = stringResource(R.string.login_title),
            subtitle = stringResource(R.string.login_desc),
            icon = Icons.Default.Code,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            badge = {
                ExpressiveStatusChip(
                    label = if (isPolling) "Waiting for GitHub" else "GitHub auth required",
                    icon = if (isPolling) Icons.Default.Sync else Icons.Default.VerifiedUser,
                    color = if (isPolling) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary
                )
            }
        )

        AnimatedVisibility(userCode != null) {
            userCode?.let { code ->
                DeviceCodeCard(
                    code = code,
                    verificationUri = verificationUri ?: "https://github.com/login/device",
                    isPolling = isPolling,
                    context = context
                )
            }
        }

        if (error != null) {
            ErrorCard(error = error, onClearError = onClearError)
        }

        if (userCode == null) {
            Button(
                onClick = { showConsentDialog = true },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                if (isLoading) {
                    LoadingIndicator(Modifier.size(24.dp))
                } else {
                    Icon(Icons.Default.Code, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.login_github))
                }
            }
        }
    }
}

@Composable
private fun AuthShell(content: @Composable ColumnScope.() -> Unit) {
    Scaffold(containerColor = uiSurfaceColor(MaterialTheme.colorScheme.surface)) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(uiSurfaceColor(MaterialTheme.colorScheme.surface))
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
                content = content
            )
        }
    }
}

@Composable
private fun DeviceCodeCard(
    code: String,
    verificationUri: String,
    isPolling: Boolean,
    context: Context
) {
    var copied by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                stringResource(R.string.auth_code_title),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                stringResource(R.string.auth_code_desc),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedCard {
                Text(
                    code,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("user_code", code))
                    copied = true
                }) {
                    Icon(
                        if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                        null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (copied) stringResource(R.string.copied) else stringResource(R.string.copy))
                }
                Button(onClick = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(verificationUri)))
                    }
                }) {
                    Icon(Icons.Default.OpenInBrowser, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.open_browser))
                }
            }
            if (isPolling) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LoadingIndicator(Modifier.size(22.dp))
                    Text(
                        stringResource(R.string.waiting_auth),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ── Fork Check ────────────────────────────────────────────────────────────────

@Composable
private fun ForkCheckScreen(
    isLoading: Boolean,
    hasFork: Boolean,
    behindBy: Int,
    showSyncDialog: Boolean,
    error: String?,
    onFork: () -> Unit,
    onSync: () -> Unit,
    onSkip: () -> Unit,
    onClearError: () -> Unit
) {
    if (showSyncDialog) {
        AlertDialog(
            onDismissRequest = onSkip,
            icon = { Icon(Icons.Default.Sync, null) },
            title = { Text(stringResource(R.string.sync_title)) },
            text = {
                Text("${stringResource(R.string.sync_desc)}\n\nBehind by $behindBy commits.")
            },
            confirmButton = {
                Button(onClick = onSync) { Text(stringResource(R.string.sync_action)) }
            },
            dismissButton = {
                TextButton(onClick = onSkip) { Text(stringResource(R.string.skip)) }
            }
        )
    }

    AuthShell {
        if (isLoading) {
            ExpressiveHeroCard(
                title = "Checking fork",
                subtitle = "ABK is verifying your repository, workflows, and upstream sync status.",
                icon = Icons.Default.Sync,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                badge = {
                    ExpressiveStatusChip(
                        label = stringResource(R.string.loading),
                        icon = Icons.Default.HourglassTop,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            ) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        } else if (!hasFork) {
            ExpressiveHeroCard(
                title = stringResource(R.string.fork_title),
                subtitle = stringResource(R.string.fork_desc),
                icon = Icons.Default.ForkRight,
                badge = {
                    ExpressiveStatusChip(
                        label = "Your build repository will be created",
                        icon = Icons.Default.CallSplit,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            )
            Button(
                onClick = onFork,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Icon(Icons.Default.ForkRight, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.fork_action))
            }
        } else {
            ExpressiveHeroCard(
                title = "Repository ready",
                subtitle = if (behindBy > 0) "Your fork is $behindBy commits behind upstream. Sync before building." else "Fork, permissions and workflow status verified.",
                icon = Icons.Default.CheckCircle,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                badge = {
                    ExpressiveStatusChip(
                        label = if (behindBy > 0) "Sync recommended" else "Ready to proceed",
                        icon = if (behindBy > 0) Icons.Default.Warning else Icons.Default.Verified,
                        color = if (behindBy > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                    )
                }
            )
        }
        if (error != null) {
            ErrorCard(error = error, onClearError = onClearError)
        }
    }
}

@Composable
private fun ErrorCard(error: String, onClearError: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(8.dp))
            Text(
                error,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onClearError) {
                Icon(Icons.Default.Close, contentDescription = "Dismiss error", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
