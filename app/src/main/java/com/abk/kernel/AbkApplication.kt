package com.abk.kernel

import android.app.Application
import com.abk.kernel.utils.NotificationUtils
import com.abk.kernel.utils.RootUtils

class AbkApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        RootUtils.init()
        NotificationUtils.createChannels(this)
    }
}
