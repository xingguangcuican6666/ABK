package com.abk.kernel.ui.navigation3

import android.os.Parcelable
import androidx.navigation3.runtime.NavKey
import com.abk.kernel.miuix.ui.screens.flash.common.FlashTerminalParams
import com.abk.kernel.miuix.ui.screens.runtime.ModuleActionTerminalParams
import com.abk.kernel.miuix.ui.screens.runtime.ModuleInstallParams
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

/**
 * Type-safe navigation keys for Navigation3.
 * MainRoute 是 tab 容器（tab 之间用内部状态切换，不走导航）。
 * 子页面（如 ThemeSettings）作为独立 Route push 到 back stack。
 */
sealed interface Route : NavKey, Parcelable {

    @Parcelize
    @Serializable
    data object Main : Route

    @Parcelize
    @Serializable
    data object ThemeSettings : Route

    @Parcelize
    @Serializable
    data object AppProfileTemplates : Route

    @Parcelize
    @Serializable
    data object ManagerTools : Route

    @Parcelize
    @Serializable
    data object About : Route

    @Parcelize
    @Serializable
    data object OpenSourceLicenses : Route

    @Parcelize
    @Serializable
    data object ExtensionManager : Route

    @Parcelize
    @Serializable
    data object BuildPlanLibrary : Route

    @Parcelize
    @Serializable
    data object BuildQueue : Route

    @Parcelize
    @Serializable
    data object BuildKernelOptions : Route

    @Parcelize
    @Serializable
    data object ManagerPatch : Route

    @Parcelize
    @Serializable
    data object SusfsControl : Route

    @Parcelize
    @Serializable
    data object BuildModuleRepoSettings : Route

    @Parcelize
    @Serializable
    data object RuntimeModuleRepoSettings : Route

    @Parcelize
    @Serializable
    data class FlashWorkflowDetail(val runId: Long) : Route

    @Parcelize
    @Serializable
    data class FlashPrebuiltDetail(val releaseId: Long) : Route

    @Parcelize
    @Serializable
    data class SuperUserProfile(val uid: Int) : Route

    @Parcelize
    @Serializable
    data class FlashTerminalLog(val params: FlashTerminalParams) : Route

    @Parcelize
    @Serializable
    data class ModuleInstallLog(val params: ModuleInstallParams) : Route

    @Parcelize
    @Serializable
    data class ModuleActionTerminal(val params: ModuleActionTerminalParams) : Route
}
