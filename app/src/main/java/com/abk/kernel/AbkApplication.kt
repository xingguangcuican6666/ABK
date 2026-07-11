package com.abk.kernel

import android.app.Application
import android.content.Context
import android.os.Process
import android.os.SystemClock
import com.abk.kernel.utils.LocaleHelper
import com.abk.kernel.utils.NotificationUtils
import com.abk.kernel.utils.RootUtils
import com.abk.kernel.utils.WorkflowStepI18n

class AbkApplication : Application() {
    companion object {
        val processStartElapsedRealtimeMs: Long = SystemClock.elapsedRealtime()
        val processStartWallClockMs: Long = System.currentTimeMillis()
        val processStartPid: Int = Process.myPid()
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(base))
    }

    override fun onCreate() {
        super.onCreate()
        LocaleHelper.init(this)
        WorkflowStepI18n.init(this)
        RootUtils.init(this)
        NotificationUtils.createChannels(this)
    }
}
