package com.abk.kernel.extensions

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import com.abk.kernel.MainActivity
import com.abk.kernel.R
import com.abk.kernel.data.repository.PreferencesRepository
import com.abk.kernel.ui.components.AbkCenteredLoadingTransition
import com.abk.kernel.ui.components.AppBackgroundHost
import com.abk.kernel.ui.theme.AbkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AbkExtensionBootstrapActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = PreferencesRepository(applicationContext)
        setContent {
            val themeMode by prefs.themeMode.collectAsState(initial = "dark")
            val dynamicColorEnabled by prefs.dynamicColorEnabled.collectAsState(initial = true)
            val customThemeColorArgb by prefs.customThemeColorArgb.collectAsState(initial = null)
            val customAccentColorArgb by prefs.customAccentColorArgb.collectAsState(initial = null)
            val customBackgroundUri by prefs.customBackgroundUri.collectAsState(initial = null)
            val backgroundImageEnabled by prefs.backgroundImageEnabled.collectAsState(initial = false)
            val uiSurfaceAlpha by prefs.uiSurfaceAlpha.collectAsState(initial = 1f)

            AbkTheme(
                themeMode = themeMode,
                dynamicColorEnabled = dynamicColorEnabled,
                customThemeColorArgb = customThemeColorArgb,
                customAccentColorArgb = customAccentColorArgb
            ) {
                AppBackgroundHost(
                    backgroundUri = customBackgroundUri,
                    backgroundEnabled = backgroundImageEnabled,
                    uiSurfaceAlpha = uiSurfaceAlpha
                ) {
                    AbkCenteredLoadingTransition(text = stringResource(R.string.loading))
                }
            }
        }
        lifecycleScope.launch {
            val termsAccepted = prefs.termsAcceptedVersion.first() >= PreferencesRepository.CURRENT_TERMS_VERSION
            if (!termsAccepted) {
                startActivity(
                    Intent(this@AbkExtensionBootstrapActivity, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                )
                finish()
                return@launch
            }

            val pending = withContext(Dispatchers.IO) {
                abkPickPendingExtension(this@AbkExtensionBootstrapActivity)
            }
            if (pending == null) {
                finish()
                return@launch
            }

            if (pending.canStartServiceSilently) {
                abkLaunchExtensionServiceActivity(this@AbkExtensionBootstrapActivity, pending)
                finish()
                return@launch
            }

            startActivity(
                abkOpenExtensionManager(
                    context = this@AbkExtensionBootstrapActivity,
                    extensionId = pending.extensionId,
                    bootstrapMode = true
                )
            )
            finish()
        }
    }
}
