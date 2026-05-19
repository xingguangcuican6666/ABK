package com.abk.kernel.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KernelSupportTest {

    @Test
    fun normalizeCoercesInvalidValuesAndDisablesKsuOnlyFeaturesForNoneVariant() {
        val normalized = KernelSupport.normalize(
            KernelBuildConfig(
                androidVersion = "android16",
                kernelVersion = "6.12",
                subLevel = "999",
                osPatchLevel = "2099-01",
                kernelsuVariant = "none",
                kernelsuBranch = KSU_BRANCH_DEV,
                useKpm = true,
                cancelSusfs = false,
                kpmPassword = "secret",
                virtualizationSupport = "678",
                customExternalModules = listOf(
                    CustomExternalModule("  ", "before_build"),
                    CustomExternalModule(" https://github.com/example/module.git ", "before-build"),
                    CustomExternalModule("https://github.com/example/module.git", "before_build")
                )
            )
        )

        assertEquals("android16", normalized.androidVersion)
        assertEquals("6.12", normalized.kernelVersion)
        assertEquals("69", normalized.subLevel)
        assertEquals("2026-03", normalized.osPatchLevel)
        assertEquals(KSU_VARIANT_NONE, normalized.kernelsuVariant)
        assertEquals(KSU_BRANCH_STABLE, normalized.kernelsuBranch)
        assertFalse(normalized.useKpm)
        assertTrue(normalized.cancelSusfs)
        assertEquals("", normalized.kpmPassword)
        assertEquals("on", normalized.virtualizationSupport)
        assertEquals(
            listOf(CustomExternalModule("https://github.com/example/module.git", CustomExternalModuleStage.BEFORE_BUILD)),
            normalized.customExternalModules
        )
    }

    @Test
    fun normalizePreservesCustomKsuBranchAndTrimsCustomRef() {
        val normalized = KernelSupport.normalize(
            KernelBuildConfig(
                kernelsuVariant = KSU_VARIANT_SUKISU,
                kernelsuBranch = KSU_BRANCH_CUSTOM,
                customRef = "  feature:5  "
            )
        )

        assertEquals(KSU_BRANCH_CUSTOM, normalized.kernelsuBranch)
        assertEquals("feature:5", normalized.customRef)
    }

    @Test
    fun recommendedFromKernelDetectsAndroidLineSubLevelAndPatch() {
        val config = KernelSupport.recommendedFromKernel(
            "Linux version 6.1.162-android14-11-gabcdef SMP PREEMPT 2026-03"
        )

        assertEquals("android14", config.androidVersion)
        assertEquals("6.1", config.kernelVersion)
        assertEquals("162", config.subLevel)
        assertEquals("2026-03", config.osPatchLevel)
    }

    @Test
    fun virtualizationOptionsDependOnKernelLine() {
        assertEquals(listOf("off", "on"), KernelSupport.virtualizationSupportOptions("6.12"))
        assertEquals(listOf("off", "678", "123", "345"), KernelSupport.virtualizationSupportOptions("6.1"))
    }
}
