package com.abk.kernel

import android.app.Application
import android.content.Context
import com.abk.kernel.utils.LocaleHelper
import com.abk.kernel.utils.NotificationUtils
import com.abk.kernel.utils.RootUtils
import com.abk.kernel.utils.WorkflowStepI18n

class AbkApplication : Application() {
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
