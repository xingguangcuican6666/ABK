package com.abk.kernel.ui.screens

import com.abk.kernel.data.model.AbkRuntimeModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeModulePresentationTest {
    @Test
    fun onlyControllableWritableStandardModulesCanBeUninstalled() {
        val module = AbkRuntimeModule(type = "standard", controllable = true)
        assertTrue(module.canUninstallRuntimeModule())
        assertFalse(module.copy(readonly = true).canUninstallRuntimeModule())
        assertFalse(module.copy(controllable = false).canUninstallRuntimeModule())
        assertFalse(module.copy(type = "kpm").canUninstallRuntimeModule())
        assertFalse(module.copy(type = "builtin").canUninstallRuntimeModule())
        assertTrue(module.copy(type = "", source = "abk, ksud").canUninstallRuntimeModule())
        assertFalse(module.copy(type = "", source = "abk, kpm, ksud").canUninstallRuntimeModule())
        // Pending removal still needs a revoke control, not a different uninstall capability.
        assertTrue(module.copy(remove = true).canUninstallRuntimeModule())
    }

    @Test
    fun searchKeepsEveryReportedMetadataFieldAndTrimsInput() {
        val module =
            AbkRuntimeModule(
                id = "module-id",
                name = "Module Name",
                version = "2.0",
                author = "Fixture Author",
                description = "Sample description",
                repoUrl = "https://example.invalid/repository",
                stage = "before_build",
                type = "standard",
                source = "ksud",
            )
        listOf(
                "  ",
                "MODULE-ID",
                " name ",
                "2.0",
                "author",
                "description",
                "repository",
                "before_build",
                "standard",
                "ksud",
            )
            .forEach { assertTrue(it, module.matchesRuntimeModuleQuery(it)) }
        assertFalse(module.matchesRuntimeModuleQuery("not-present"))
    }

    @Test
    fun displayNameFallsBackToIdThenRepositoryWithoutGitSuffix() {
        assertEquals("Name", AbkRuntimeModule(name = "Name", id = "id").displayName())
        assertEquals("id", AbkRuntimeModule(id = "id").displayName())
        assertEquals(
            "repo",
            AbkRuntimeModule(repoUrl = "https://example.invalid/repo.git/").displayName(),
        )
        assertEquals("unknown", AbkRuntimeModule().displayName())
    }

    @Test
    fun groupingKeepsRepositoryPriorityAndStableModuleOrderWithoutMergingUngroupedItems() {
        val modules =
            listOf(
                AbkRuntimeModule(
                    id = "z",
                    name = "Zeta",
                    groupRepoUrl = "https://example.invalid/repo",
                    groupId = "ignored",
                    groupName = "Repo group",
                ),
                AbkRuntimeModule(
                    id = "a",
                    name = "Alpha",
                    groupRepoUrl = " HTTPS://EXAMPLE.INVALID/REPO ",
                    groupId = "other",
                ),
                AbkRuntimeModule(id = "id-group", groupId = "shared", groupName = "Id group"),
                AbkRuntimeModule(id = "id-group-2", groupId = "shared"),
                AbkRuntimeModule(id = "named", groupName = "Named group"),
                AbkRuntimeModule(id = "named-2", groupName = " named GROUP "),
                AbkRuntimeModule(id = "single-1"),
                AbkRuntimeModule(id = "single-2"),
            )
        val groups = groupRuntimeModulesForDisplay(modules)
        assertEquals(5, groups.size)
        assertEquals(
            listOf("a", "z"),
            groups.single { it.groupName == "Repo group" }.modules.map { it.id },
        )
        assertEquals(2, groups.single { it.groupName == "Id group" }.modules.size)
        assertEquals(2, groups.single { it.groupName == "Named group" }.modules.size)
        assertEquals(2, groups.count { it.groupName == null && it.modules.size == 1 })
        assertEquals(
            modules.map { it.id }.sorted(),
            groups.flatMap { it.modules }.map { it.id }.sorted(),
        )
    }
}
