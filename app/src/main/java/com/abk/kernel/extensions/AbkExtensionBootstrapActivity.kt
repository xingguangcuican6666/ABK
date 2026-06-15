package com.abk.kernel.extensions

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.abk.kernel.MainActivity
import com.abk.kernel.data.repository.PreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AbkExtensionBootstrapActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            val prefs = PreferencesRepository(this@AbkExtensionBootstrapActivity)
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
