package com.abk.kernel

import android.app.Application
import android.content.Context
import com.abk.kernel.utils.LocaleHelper
import com.abk.kernel.utils.NotificationUtils
import com.abk.kernel.utils.RootUtils

class AbkApplication : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(base))
    }

    override fun onCreate() {
        super.onCreate()
        LocaleHelper.init(this)
        RootUtils.init(this)
        NotificationUtils.createChannels(this)
    }
}
