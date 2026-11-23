package com.abk.kernel

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.abk.kernel.data.repository.PreferencesRepository
import com.abk.kernel.extensions.AbkExtensionBootstrapActivity
import com.abk.kernel.ui.AppShell
import com.abk.kernel.ui.onboarding.OnboardingScreen
import com.abk.kernel.ui.onboarding.TermsAgreementDialog
import com.abk.kernel.ui.theme.AppTheme
import com.abk.kernel.utils.LocaleHelper
import com.abk.kernel.viewmodel.MainUiState
import com.abk.kernel.viewmodel.MainViewModel
import io.github.xingguangcuican6666.abk.ui.components.AbkButton
import io.github.xingguangcuican6666.abk.ui.components.AbkButtonStyle
import io.github.xingguangcuican6666.abk.ui.components.AbkDialog
import io.github.xingguangcuican6666.abk.ui.components.AbkText
import io.github.xingguangcuican6666.abk.ui.theme.AbkTheme
import io.github.xingguangcuican6666.abk.ui.theme.AbkTone

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
        val prefs = PreferencesRepository(applicationContext)

        setContent {
            val vm: MainViewModel = viewModel()
            val state by vm.uiState.collectAsState()
            var extensionBootstrapIssued by rememberSaveable { mutableStateOf(false) }

            LaunchedEffect(Unit) { vm.checkRoot() }

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

            LaunchedEffect(state.termsAccepted, state.showOobe, extensionBootstrapIssued) {
                if (state.termsAccepted && !state.showOobe && !extensionBootstrapIssued) {
                    extensionBootstrapIssued = true
                    startActivity(
                        Intent(this@MainActivity, AbkExtensionBootstrapActivity::class.java).apply {
                            putExtra("boot_action", "foreground")
                        }
                    )
                }
            }

            AppTheme(
                prefs = remember { prefs },
                backgroundUri = state.customBackgroundUri,
                backgroundEnabled = state.backgroundImageEnabled,
                uiSurfaceAlpha = state.uiSurfaceAlpha,
            ) {
                MainRoot(
                    vm = vm,
                    state = state,
                    pendingModuleInstallUri = pendingModuleInstallUri,
                    onModuleInstallUriConsumed = { pendingModuleInstallUri = null },
                )
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
private fun MainRoot(
    vm: MainViewModel,
    state: MainUiState,
    pendingModuleInstallUri: String?,
    onModuleInstallUriConsumed: () -> Unit,
) {
    val activity = LocalContext.current as? Activity
    when {
        // Preferences are still loading; paint the plain theme surface.
        !state.termsLoaded -> Box(Modifier.fillMaxSize().background(AbkTheme.colors.background))

        state.showPreferencesResetNotice -> AbkDialog(
            title = stringResource(R.string.preferences_reset_title),
            onDismissRequest = vm::dismissPreferencesResetNotice,
            actions = {
                AbkButton(onClick = vm::dismissPreferencesResetNotice, style = AbkButtonStyle.Primary) {
                    AbkText(stringResource(android.R.string.ok))
                }
            },
        ) {
            AbkText(stringResource(R.string.preferences_reset_message))
        }

        !state.termsAccepted -> TermsAgreementDialog(
            onAccept = vm::acceptTerms,
            onDecline = { activity?.finishAffinity() },
        )

        else -> Box(Modifier.fillMaxSize()) {
            AppShell(
                vm = vm,
                state = state,
                pendingModuleInstallUri = pendingModuleInstallUri,
                onModuleInstallUriConsumed = onModuleInstallUriConsumed,
            )

            if (!state.showOobe) {
                val recovery = state.rootGrantRecoveryNotice
                when {
                    recovery != null -> AbkDialog(
                        title = recovery.title,
                        onDismissRequest = vm::dismissRootGrantRecoveryNotice,
                        tone = AbkTone.Warning,
                        actions = {
                            AbkButton(onClick = vm::dismissRootGrantRecoveryNotice, style = AbkButtonStyle.Primary) {
                                AbkText(stringResource(android.R.string.ok))
                            }
                        },
                    ) { AbkText(recovery.message) }

                    state.showSyncPrompt -> AbkDialog(
                        title = stringResource(R.string.sync_title),
                        onDismissRequest = vm::dismissSyncPrompt,
                        actions = {
                            AbkButton(onClick = vm::dismissSyncPrompt, style = AbkButtonStyle.Ghost) {
                                AbkText(stringResource(R.string.skip))
                            }
                            AbkButton(onClick = vm::syncFork, style = AbkButtonStyle.Primary) {
                                AbkText(stringResource(R.string.sync_action))
                            }
                        },
                    ) {
                        AbkText(
                            stringResource(R.string.sync_desc) + "\n\n" +
                                stringResource(R.string.sync_behind_commits, state.behindBy)
                        )
                    }
                }
            }

            if (state.showOobe) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(AbkTheme.colors.background)
                        .zIndex(4f)
                ) {
                    OnboardingScreen(vm = vm, state = state)
                }
            }
        }
    }
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

private val MODULE_ZIP_MIME_TYPES = setOf(
    "application/zip",
    "application/x-zip",
    "application/x-zip-compressed",
    "application/octet-stream"
)
