package com.abk.kernel.viewmodel

import com.abk.kernel.data.model.AbkRuntimeModule
import com.abk.kernel.data.model.AbkRuntimeStatus
import com.abk.kernel.data.model.MainUiState
import com.abk.kernel.data.model.RootGrantApp
import com.abk.kernel.data.model.RootGrantProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeCoordinatorStatePatchTest {

    @Test
    fun `saving loaded root profile updates detail and preserves list order`() {
        val deniedApp = RootGrantApp(
            packageName = "com.example.alpha",
            label = "Alpha",
            uid = 1002,
            profile = RootGrantProfile(
                name = "com.example.alpha",
                currentUid = 1002,
                allowSu = false
            )
        )
        val allowedApp = RootGrantApp(
            packageName = "com.example.beta",
            label = "Beta",
            uid = 1001,
            profile = RootGrantProfile(
                name = "com.example.beta",
                currentUid = 1001,
                allowSu = true
            ),
            profileLoaded = true
        )
        val updated = MainUiState(
            rootGrantApps = listOf(allowedApp, deniedApp),
            rootGrantDetailApp = deniedApp.copy(profileLoaded = true)
        ).applySavedRootGrantProfile(
            packageName = "com.example.alpha",
            savedProfile = RootGrantProfile(
                name = "com.example.alpha",
                currentUid = 1002,
                allowSu = true,
                rootUseDefault = false,
                rootTemplate = "custom",
                umountModules = false
            )
        )

        assertEquals(
            listOf("com.example.beta", "com.example.alpha"),
            updated.rootGrantApps.map { it.packageName }
        )
        assertTrue(updated.rootGrantApps.last().profile.allowSu)
        assertTrue(updated.rootGrantApps.last().profileLoaded)
        assertEquals("custom", updated.rootGrantApps.last().profile.rootTemplate)
        assertTrue(updated.rootGrantDetailApp?.profileLoaded == true)
        assertEquals("custom", updated.rootGrantDetailApp?.profile?.rootTemplate)
    }

    @Test
    fun `list toggle only updates allow state when full profile is not loaded`() {
        val app = RootGrantApp(
            packageName = "com.example.app",
            label = "Example",
            uid = 1001,
            profile = RootGrantProfile(
                name = "com.example.app",
                currentUid = 1001,
                allowSu = false
            ),
            profileLoaded = false
        )

        val updated = MainUiState(rootGrantApps = listOf(app)).applySavedRootGrantProfile(
            packageName = "com.example.app",
            savedProfile = RootGrantProfile(
                name = "com.example.app",
                currentUid = 1001,
                allowSu = true,
                rootUseDefault = false,
                rootTemplate = "custom"
            )
        )

        assertTrue(updated.rootGrantApps.single().profile.allowSu)
        assertFalse(updated.rootGrantApps.single().profileLoaded)
        assertEquals("", updated.rootGrantApps.single().profile.rootTemplate)
    }

    @Test
    fun `runtime module enabled patch preserves current order`() {
        val updated = MainUiState(
            abkRuntimeStatus = AbkRuntimeStatus(
                modules = listOf(
                    runtimeModule(id = "a", name = "Alpha", enabled = false),
                    runtimeModule(id = "b", name = "Beta", enabled = true),
                    runtimeModule(id = "c", name = "Gamma", enabled = true)
                )
            )
        ).applyRuntimeModuleEnabled("c", false)

        assertEquals(listOf("a", "b", "c"), updated.abkRuntimeStatus?.modules?.map { it.id })
        assertFalse(updated.abkRuntimeStatus?.modules?.last()?.enabled ?: true)
    }

    @Test
    fun `runtime module pending uninstall patch updates only target module`() {
        val updated = MainUiState(
            abkRuntimeStatus = AbkRuntimeStatus(
                modules = listOf(
                    runtimeModule(id = "a", name = "Alpha", remove = false),
                    runtimeModule(id = "b", name = "Beta", remove = false)
                )
            )
        ).applyRuntimeModulePendingUninstall("b", true)

        assertEquals(listOf("a", "b"), updated.abkRuntimeStatus?.modules?.map { it.id })
        assertFalse(updated.abkRuntimeStatus?.modules?.first()?.remove ?: true)
        assertTrue(updated.abkRuntimeStatus?.modules?.last()?.remove == true)
    }

    @Test
    fun `sort runtime modules for display uses type enabled then name`() {
        val sorted = sortRuntimeModulesForDisplay(
            listOf(
                runtimeModule(id = "standard-enabled", name = "Beta", type = "standard", enabled = true),
                runtimeModule(id = "builtin-enabled", name = "Gamma", type = "builtin", enabled = true),
                runtimeModule(id = "standard-disabled", name = "Alpha", type = "standard", enabled = false),
                runtimeModule(id = "kpm-module", name = "Kappa", type = "kpm", enabled = true)
            )
        )

        assertEquals(
            listOf("builtin-enabled", "standard-disabled", "standard-enabled", "kpm-module"),
            sorted.map { it.id }
        )
    }

    private fun runtimeModule(
        id: String,
        name: String,
        type: String = "standard",
        enabled: Boolean = true,
        remove: Boolean = false
    ): AbkRuntimeModule = AbkRuntimeModule(
        id = id,
        name = name,
        type = type,
        enabled = enabled,
        remove = remove,
        controllable = true,
        source = when (type) {
            "builtin" -> "abk"
            "kpm" -> "kpm"
            else -> "ksud"
        }
    )
}
