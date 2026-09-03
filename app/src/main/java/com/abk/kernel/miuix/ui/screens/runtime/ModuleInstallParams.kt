package com.abk.kernel.miuix.ui.screens.runtime

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Parcelize
@Serializable
data class ModuleInstallParams(
    val uri: String,
    val displayName: String,
) : Parcelable
