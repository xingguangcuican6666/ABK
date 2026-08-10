package com.abk.kernel

import android.app.Application
import android.content.Context
import android.os.Process
import android.os.SystemClock
import com.abk.kernel.utils.LocaleHelper
import com.abk.kernel.utils.NotificationUtils
import com.abk.kernel.utils.RootUtils
import com.abk.kernel.utils.WorkflowStepI18n

// Mirrors AOSP isolated UID ranges without calling newer Process APIs.
private const val PER_USER_RANGE = 100000
private const val FIRST_APP_ZYGOTE_ISOLATED_UID = 90000
private const val LAST_APP_ZYGOTE_ISOLATED_UID = 98999
private const val FIRST_ISOLATED_UID = 99000
private const val LAST_ISOLATED_UID = 99999

class AbkApplication : Application() {
    companion object {
        val processStartElapsedRealtimeMs: Long = SystemClock.elapsedRealtime()
        val processStartWallClockMs: Long = System.currentTimeMillis()
        val processStartPid: Int = Process.myPid()
    }

    override fun attachBaseContext(base: Context) {
        val attachedBase = if (isIsolatedProcess()) base else LocaleHelper.applyLocale(base)
        super.attachBaseContext(attachedBase)
    }

    override fun onCreate() {
        super.onCreate()
        if (isIsolatedProcess()) {
            return
        }
        LocaleHelper.init(this)
        WorkflowStepI18n.init(this)
        RootUtils.init(this)
        NotificationUtils.createChannels(this)
    }
}

private fun isIsolatedProcess(): Boolean {
    val appId = Process.myUid() % PER_USER_RANGE
    return appId in FIRST_APP_ZYGOTE_ISOLATED_UID..LAST_APP_ZYGOTE_ISOLATED_UID ||
        appId in FIRST_ISOLATED_UID..LAST_ISOLATED_UID
}
