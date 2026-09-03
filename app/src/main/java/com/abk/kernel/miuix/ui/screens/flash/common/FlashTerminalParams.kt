package com.abk.kernel.miuix.ui.screens.flash.common

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Parcelize
@Serializable
data class FlashTerminalParams(
    val artifactPath: String,
    val artifactName: String,
    val artifactType: String,       // ArtifactType.name (KERNEL_IMG/ANYKERNEL3/SUSFS_MODULE/KSU_MANAGER)
    val ak3SlotTarget: String?,     // "CURRENT" or "INACTIVE" (for ANYKERNEL3)
    val allowHighRiskFallback: Boolean,
    val operationTitle: String,     // 操作标题，用于 TopAppBar
) : Parcelable
