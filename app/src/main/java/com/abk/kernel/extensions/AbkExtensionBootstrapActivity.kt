package com.abk.kernel.extensions

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.abk.kernel.MainActivity
import com.abk.kernel.R
import com.abk.kernel.data.repository.PreferencesRepository
import com.abk.kernel.ui.theme.AppTheme
import io.github.xingguangcuican6666.abk.ui.components.AbkSpinner
import io.github.xingguangcuican6666.abk.ui.components.AbkText
import io.github.xingguangcuican6666.abk.ui.theme.AbkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Invisible hand-off activity: runs the extension bootstrap, then either starts
 * the extension service, opens the manager in bootstrap mode, or finishes.
 */
class AbkExtensionBootstrapActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = PreferencesRepository(applicationContext)
        setContent {
            AppTheme(prefs = prefs) {
                BootstrapLoading(stringResource(R.string.loading))
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

@Composable
private fun BootstrapLoading(text: String) {
    Column(
        Modifier.fillMaxSize().background(AbkTheme.colors.background).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        AbkSpinner(label = text)
        AbkText(text, color = AbkTheme.colors.inkMuted)
    }
}
