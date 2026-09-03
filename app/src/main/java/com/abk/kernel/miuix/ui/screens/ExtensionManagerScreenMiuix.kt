package com.abk.kernel.miuix.ui.screens

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abk.kernel.R
import com.abk.kernel.extensions.AbkManagedExtension
import com.abk.kernel.extensions.abkLaunchExtensionOobe
import com.abk.kernel.extensions.abkLaunchExtensionServiceActivity
import com.abk.kernel.extensions.abkLoadManagedExtensions
import com.abk.kernel.extensions.companionLabel
import com.abk.kernel.extensions.installExtensionCompanion
import com.abk.kernel.ui.navigation3.LocalNavigator
import com.abk.kernel.utils.RootUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ExtensionManagerScreenMiuix(
    onBack: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var loading by remember { mutableStateOf(true) }
    var extensions by remember { mutableStateOf<List<AbkManagedExtension>>(emptyList()) }
    var refreshToken by remember { mutableIntStateOf(0) }
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

    fun launchOobe(extension: AbkManagedExtension, finishAfterLaunch: Boolean = false) {
        val hostActivity = context as? android.app.Activity ?: return
        if (!extension.isCompanionInstalled) {
            missingCompanionDialog = extension
            return
        }
        if (!abkLaunchExtensionOobe(hostActivity, extension)) {
            Toast.makeText(
                hostActivity,
                hostActivity.getString(R.string.extension_oobe_missing),
                Toast.LENGTH_SHORT
            ).show()
            requestRefresh()
        } else if (finishAfterLaunch) {
            onBack?.invoke() ?: navigator.pop()
        }
    }

    fun launchServiceActivity(extension: AbkManagedExtension, finishAfterLaunch: Boolean = false) {
        val hostActivity = context as? android.app.Activity ?: return
        if (!extension.isCompanionInstalled) {
            missingCompanionDialog = extension
            return
        }
        if (!abkLaunchExtensionServiceActivity(hostActivity, extension)) {
            Toast.makeText(
                hostActivity,
                hostActivity.getString(R.string.extension_launch_failed),
                Toast.LENGTH_SHORT
            ).show()
            requestRefresh()
        } else if (finishAfterLaunch) {
            onBack?.invoke() ?: navigator.pop()
        }
    }

    fun installExtension(extension: AbkManagedExtension) {
        scope.launch {
            val installResult = withContext(Dispatchers.IO) {
                installExtensionCompanion(context, extension)
            }
            Toast.makeText(
                context,
                if (installResult.success) {
                    context.getString(
                        R.string.extension_install_success,
                        extension.companionDisplayName.ifBlank { extension.name }
                    )
                } else {
                    installResult.output.lastOrNull()
                        ?: context.getString(R.string.extension_install_failed)
                },
                Toast.LENGTH_LONG
            ).show()
            if (!installResult.success) return@launch

            val refreshed = withContext(Dispatchers.IO) {
                abkLoadManagedExtensions(context).firstOrNull { it.extensionId == extension.extensionId }
            }
            val hostActivity = context as? android.app.Activity
            if (hostActivity != null && refreshed?.needsOobe == true && refreshed.isCompanionInstalled) {
                if (refreshed.discoveredApp?.oobeComponent != null &&
                    abkLaunchExtensionOobe(hostActivity, refreshed)
                ) {
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(R.string.extension_manager_title),
                navigationIcon = {
                    IconButton(onClick = { onBack?.invoke() ?: navigator.pop() }) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = stringResource(R.string.settings_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (extensions.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Card(modifier = Modifier.widthIn(max = 360.dp)) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Extension,
                                    contentDescription = null,
                                    tint = MiuixTheme.colorScheme.onSurfaceSecondary,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(Modifier.size(12.dp))
                                Text(
                                    text = stringResource(R.string.extension_manager_empty_title),
                                    style = MiuixTheme.textStyles.subtitle,
                                    color = MiuixTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.size(4.dp))
                                Text(
                                    text = stringResource(R.string.extension_manager_empty_desc),
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                )
                            }
                        }
                    }
                }
            } else {
                items(
                    items = extensions,
                    key = { it.extensionId }
                ) { extension ->
                    ExtensionCardMiuix(
                        extension = extension,
                        onInstall = { installExtension(extension) },
                        onOpenOobe = { launchOobe(extension) },
                        onOpenService = { launchServiceActivity(extension) },
                        onReset = { resetExtension(extension) }
                    )
                }
                item { Spacer(Modifier.size(80.dp)) }
            }
        }
    }

    missingCompanionDialog?.let { extension ->
        AlertDialog(
            onDismissRequest = { missingCompanionDialog = null },
            title = { Text(stringResource(R.string.extension_bootstrap_install_dialog_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.extension_bootstrap_install_dialog_desc,
                        extension.name,
                        extension.companionLabel()
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        missingCompanionDialog = null
                        installExtension(extension)
                    },
                    enabled = extension.companionDownloadUrl.isNotBlank()
                ) {
                    Text(stringResource(R.string.extension_install_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { missingCompanionDialog = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun ExtensionCardMiuix(
    extension: AbkManagedExtension,
    onInstall: () -> Unit,
    onOpenOobe: () -> Unit,
    onOpenService: () -> Unit,
    onReset: () -> Unit,
) {
    val companionReady = extension.isCompanionInstalled
    val needsOobe = extension.needsOobe
    val canLaunchOobe = companionReady && extension.discoveredApp?.oobeComponent != null

    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Extension,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = extension.name,
                        style = MiuixTheme.textStyles.title4,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    if (extension.description.isNotBlank()) {
                        Spacer(Modifier.size(2.dp))
                        Text(
                            text = extension.description,
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                    }
                }
            }

            Spacer(Modifier.size(12.dp))

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusChipMiuix(
                    label = stringResource(
                        if (needsOobe) R.string.extension_status_pending_oobe
                        else R.string.extension_status_oobe_complete
                    ),
                    color = if (needsOobe) MiuixTheme.colorScheme.primary
                    else MiuixTheme.colorScheme.onSurfaceSecondary
                )
                StatusChipMiuix(
                    label = stringResource(
                        if (companionReady) R.string.extension_status_companion_ready
                        else R.string.extension_status_companion_missing
                    ),
                    color = if (companionReady) MiuixTheme.colorScheme.secondary
                    else MiuixTheme.colorScheme.error
                )
            }

            if (extension.summary.isNotBlank()) {
                Spacer(Modifier.size(8.dp))
                Text(
                    text = extension.summary,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }

            if (!companionReady) {
                Spacer(Modifier.size(8.dp))
                Text(
                    text = stringResource(
                        R.string.extension_missing_companion_desc,
                        extension.companionLabel()
                    ),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }

            Spacer(Modifier.size(12.dp))

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!companionReady) {
                    Button(
                        onClick = onInstall,
                        enabled = extension.companionDownloadUrl.isNotBlank(),
                        colors = ButtonDefaults.buttonColorsPrimary()
                    ) {
                        Icon(
                            imageVector = Icons.Default.InstallMobile,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(stringResource(R.string.extension_install_action))
                    }
                } else {
                    when {
                        extension.canLaunchServiceActivity -> {
                            Button(
                                onClick = onOpenService,
                                colors = ButtonDefaults.buttonColorsPrimary()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.size(6.dp))
                                Text(stringResource(R.string.extension_run_service_action))
                            }
                        }

                        canLaunchOobe -> {
                            Button(
                                onClick = onOpenOobe,
                                colors = ButtonDefaults.buttonColorsPrimary()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Build,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.size(6.dp))
                                Text(stringResource(R.string.extension_run_oobe_action))
                            }
                        }
                    }
                }

                Button(onClick = onReset) {
                    Text(stringResource(R.string.extension_reset_action))
                }
            }
        }
    }
}

@Composable
private fun StatusChipMiuix(label: String, color: Color) {
    Box(
        modifier = Modifier
            .padding(vertical = 2.dp)
            .padding(horizontal = 8.dp)
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.body2,
            color = color
        )
    }
}
