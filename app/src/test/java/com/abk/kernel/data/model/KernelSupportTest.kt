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

    @Test
    fun normalizeOnePlusConfigUsesOnePlusDefaultsAndDisablesMtkProxy() {
        val normalized = KernelSupport.normalize(
            KernelBuildConfig(
                buildTarget = BUILD_TARGET_ONEPLUS,
                androidVersion = "android16",
                kernelVersion = "6.12",
                kernelsuVariant = KSU_VARIANT_SUKISU,
                onePlusCpu = "mt6991",
                onePlusDeviceManifest = "oneplus_ace5_ultra_b",
                onePlusUseProxyOptimization = true,
                useKpm = true,
                useDdk = true,
                useCustomExternalModules = true,
                customExternalModules = listOf(CustomExternalModule("https://github.com/example/module"))
            )
        )

        assertEquals(BUILD_TARGET_ONEPLUS, normalized.buildTarget)
        assertEquals("android15", normalized.androidVersion)
        assertEquals("6.6", normalized.kernelVersion)
        assertEquals(KSU_VARIANT_SUKISU, normalized.kernelsuVariant)
        assertEquals("mt6991", normalized.onePlusCpu)
        assertEquals("oneplus_ace5_ultra_b", normalized.onePlusDeviceManifest)
        assertFalse(normalized.onePlusUseProxyOptimization)
        assertFalse(normalized.useKpm)
        assertFalse(normalized.useDdk)
        assertTrue(normalized.customExternalModules.isEmpty())
    }

    @Test
    fun onePlusDeviceLabelUsesAbkProfileInsteadOfManifestSuffixRule() {
        assertEquals(
            "OnePlus Turbo 6V · ColorOS/OxygenOS 16 · android14/6.1 · sm7635",
            KernelSupport.onePlusDeviceLabel("oneplus_turbo_6v")
        )
    }

    @Test
    fun normalizeOnePlusDisablesSusfsWhenNoUpstreamBranchExists() {
        val normalized = KernelSupport.normalize(
            KernelBuildConfig(
                buildTarget = BUILD_TARGET_ONEPLUS,
                kernelsuVariant = KSU_VARIANT_SUKISU,
                cancelSusfs = false,
                onePlusDeviceManifest = "oneplus_10t_v"
            )
        )

        assertEquals("android12", normalized.androidVersion)
        assertEquals("5.10", normalized.kernelVersion)
        assertTrue(normalized.cancelSusfs)
    }

    @Test
    fun normalizeDisablesKpmForResukisuDevAndLatest() {
        val dev = KernelSupport.normalize(
            KernelBuildConfig(
                kernelsuVariant = KSU_VARIANT_RESUKISU,
                kernelsuBranch = KSU_BRANCH_DEV,
                useKpm = true,
                kpmPassword = "secret"
            )
        )
        val latest = KernelSupport.normalize(
            KernelBuildConfig(
                kernelsuVariant = KSU_VARIANT_RESUKISU,
                kernelsuBranch = KSU_BRANCH_LATEST,
                useKpm = true,
                kpmPassword = "secret"
            )
        )

        assertFalse(dev.useKpm)
        assertEquals("", dev.kpmPassword)
        assertFalse(latest.useKpm)
        assertEquals("", latest.kpmPassword)
    }

    @Test
    fun normalizeKeepsKpmForResukisuStableAndCustom() {
        val stable = KernelSupport.normalize(
            KernelBuildConfig(
                kernelsuVariant = KSU_VARIANT_RESUKISU,
                kernelsuBranch = KSU_BRANCH_STABLE,
                useKpm = true,
                kpmPassword = "secret"
            )
        )
        val custom = KernelSupport.normalize(
            KernelBuildConfig(
                kernelsuVariant = KSU_VARIANT_RESUKISU,
                kernelsuBranch = KSU_BRANCH_CUSTOM,
                useKpm = true,
                kpmPassword = "secret"
            )
        )

        assertTrue(stable.useKpm)
        assertEquals("secret", stable.kpmPassword)
        assertTrue(custom.useKpm)
        assertEquals("secret", custom.kpmPassword)
    }

    @Test
    fun normalizeDisablesKpmForOfficialOnStableAndCustom() {
        val stable = KernelSupport.normalize(
            KernelBuildConfig(
                kernelsuVariant = KSU_VARIANT_OFFICIAL,
                kernelsuBranch = KSU_BRANCH_STABLE,
                useKpm = true,
                kpmPassword = "secret"
            )
        )
        val custom = KernelSupport.normalize(
            KernelBuildConfig(
                kernelsuVariant = KSU_VARIANT_OFFICIAL,
                kernelsuBranch = KSU_BRANCH_CUSTOM,
                useKpm = true,
                kpmPassword = "secret"
            )
        )

        assertFalse(stable.useKpm)
        assertEquals("", stable.kpmPassword)
        assertFalse(custom.useKpm)
        assertEquals("", custom.kpmPassword)
    }
}
