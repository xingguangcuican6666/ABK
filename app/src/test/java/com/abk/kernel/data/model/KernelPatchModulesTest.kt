package com.abk.kernel.data.model

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class KernelPatchModulesTest {
    @Test fun repositorySpecsFollowCiRefDelimiter() {
        listOf("https://github.com/org/repo.git", "https://host/r@v1", "git@host:org/repo.git", "git@host:org/repo.git@v1", "ssh://git@host/org/repo@v1").forEach {
            assertTrue(it, KernelPatchModules.isValidSpec(it))
        }
        listOf("", "-option", "https://host/r@feature/branch", "git@host:org/r@feature/branch", "https://host/r@bad:ref", "https://host/r@", "https://host/r|https://host/b", "https://user:secret@host/r", "https://host/r\nother").forEach {
            assertFalse(it, KernelPatchModules.isValidSpec(it))
        }
    }

    @Test fun oldPersistedJsonNormalizesMissingPatchFields() {
        val config = KernelSupport.normalize(Gson().fromJson("{\"buildTarget\":\"gki\"}", KernelBuildConfig::class.java))
        assertEquals(emptyList<String>(), config.ksuhookModules)
        assertEquals("", config.ksuhookSdkRef)
    }

    @Test fun switchingTargetsPreservesButBlocksUnsupportedDrafts() {
        val config = KernelBuildConfig(ksuhookModules = listOf("https://host/r@v1"))
        for (unsupported in listOf(config.copy(buildTarget = BUILD_TARGET_ONEPLUS), config.copy(kernelsuVariant = KSU_VARIANT_NONE))) {
            val normalized = KernelSupport.normalize(unsupported)
            assertEquals(config.ksuhookModules, normalized.ksuhookModules)
            assertNotNull(KernelPatchModules.validationError(normalized))
        }
        assertNull(KernelPatchModules.validationError(KernelSupport.normalize(config)))
    }
}
