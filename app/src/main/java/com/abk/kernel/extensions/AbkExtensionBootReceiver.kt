package com.abk.kernel.extensions

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.abk.kernel.utils.RootUtils

class AbkExtensionBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action.orEmpty()
        if (
            action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            action != Intent.ACTION_USER_UNLOCKED
        ) {
            return
        }
        val pendingResult = goAsync()
        Thread {
            try {
                val component = "${context.packageName}/${AbkExtensionBootstrapActivity::class.java.name}"
                RootUtils.launchActivityAsRoot(
                    componentName = component,
                    extras = mapOf("boot_action" to action)
                )
            } finally {
                pendingResult.finish()
            }
        }.start()
    }
}
