package com.abk.kernel

import com.abk.kernel.utils.LocaleHelper

fun tr(resId: Int, vararg args: Any?): String = LocaleHelper.str(resId, *args)
