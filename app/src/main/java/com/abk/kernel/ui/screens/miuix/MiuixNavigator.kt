package com.abk.kernel.ui.screens.miuix

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface MiuixScreen : NavKey {
    @Serializable data object Root : MiuixScreen
    @Serializable data object PlanLib : MiuixScreen
    @Serializable data object BuildQueue : MiuixScreen
    @Serializable data object RepoSettings : MiuixScreen
    @Serializable data object RuntimeRepoSettings : MiuixScreen
    @Serializable data object ThemeSettings : MiuixScreen
    @Serializable data object AboutSettings : MiuixScreen
    @Serializable data object OpenSourceLicenses : MiuixScreen
    @Serializable data class FlashDetail(val runId: Long) : MiuixScreen
    @Serializable data class PrebuiltDetail(val releaseId: Long) : MiuixScreen
}

val LocalMiuixBackStack = staticCompositionLocalOf<SnapshotStateList<NavKey>> { error("LocalMiuixBackStack not provided") }
