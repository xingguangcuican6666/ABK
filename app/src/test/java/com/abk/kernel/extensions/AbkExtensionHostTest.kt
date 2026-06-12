package com.abk.kernel.extensions

import com.abk.kernel.data.model.AbkRuntimeModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AbkExtensionHostTest {
    @Test
    fun `does not expose modules that only declare an extension id`() {
        val module = AbkRuntimeModule(
            id = "module-only",
            extensionId = "demo.extension"
        )

        assertFalse(abkShouldExposeManagedExtension(module, hasDiscoveredApp = false))
    }

    @Test
    fun `exposes entries backed by a discovered app or explicit companion metadata`() {
        val discoveredOnly = AbkRuntimeModule(
            id = "installed-app",
            extensionId = "demo.extension"
        )
        val declaredCompanion = AbkRuntimeModule(
            id = "downloadable-app",
            extensionId = "demo.extension",
            companionDownloadUrl = "https://example.invalid/companion.apk"
        )

        assertTrue(abkShouldExposeManagedExtension(discoveredOnly, hasDiscoveredApp = true))
        assertTrue(abkShouldExposeManagedExtension(declaredCompanion, hasDiscoveredApp = false))
    }

    @Test
    fun `prefers the module that explicitly declares companion app metadata`() {
        val dependencyOnly = AbkRuntimeModule(
            id = "builtin-dependency",
            extensionId = "demo.extension",
            type = "builtin"
        )
        val appBacked = AbkRuntimeModule(
            id = "builtin-app-entry",
            extensionId = "demo.extension",
            type = "builtin",
            requiresCompanionApp = true,
            companionPackage = "com.example.demo",
            companionDownloadUrl = "https://example.invalid/demo.apk"
        )

        val selected = abkPickManagedExtensionModule(
            modules = listOf(dependencyOnly, appBacked),
            hasDiscoveredApp = true
        )

        assertEquals("builtin-app-entry", selected?.id)
    }

    @Test
    fun `falls back to a single discovered app backed entry when no companion metadata exists`() {
        val first = AbkRuntimeModule(
            id = "fallback-first",
            extensionId = "demo.extension",
            oobePriority = 5
        )
        val second = AbkRuntimeModule(
            id = "fallback-second",
            extensionId = "demo.extension",
            oobePriority = 1
        )

        val selected = abkPickManagedExtensionModule(
            modules = listOf(second, first),
            hasDiscoveredApp = true
        )

        assertEquals("fallback-first", selected?.id)
    }

    @Test
    fun `returns null when no companion metadata or discovered app exists`() {
        val selected = abkPickManagedExtensionModule(
            modules = listOf(
                AbkRuntimeModule(id = "dependency-only", extensionId = "demo.extension")
            ),
            hasDiscoveredApp = false
        )

        assertNull(selected)
    }
}
