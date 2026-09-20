package com.abk.kernel.utils

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/** Resolves the hosting [Activity] when [Context] is wrapped (e.g. [ContextThemeWrapper]). */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
