package com.abk.kernel.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KernelSupportTest {

    @Test
    fun normalizeKsuVariantFoldsRetiredResukisuAliasesIntoBakasu() {
        listOf("ReSukiSU", "resukisu", "Re-SukiSU", " re-suki ").forEach { legacy ->
            assertEquals(
                "legacy alias <$legacy> should normalize to BakaSU",
                KSU_VARIANT_BAKASU,
                KernelSupport.normalizeKsuVariant(legacy, BUILD_TARGET_GKI),
            )
            assertEquals(
                "legacy alias <$legacy> should normalize to BakaSU on OnePlus",
                KSU_VARIANT_BAKASU,
                KernelSupport.normalizeKsuVariant(legacy, BUILD_TARGET_ONEPLUS),
            )
        }
        // The retired spelling must not be mistaken for the current BakaSU option itself.
        assertFalse(KernelSupport.isDeprecatedKsuVariantAlias(KSU_VARIANT_BAKASU))
        assertTrue(KernelSupport.isDeprecatedKsuVariantAlias("ReSukiSU"))
    }

    @Test
    fun inferRuntimeKsuFamilyDistinguishesBakaSuFromSukiSu() {
        // Canonical and hyphenated BakaSU spellings, plus the retired ReSukiSU alias which
        // contains "sukisu" and must still land on BakaSU.
        listOf("BakaSU", "bakasu", "Baka-SU", "Baka_SU", "ReSukiSU", "resukisu", "re-suki").forEach { raw ->
            assertEquals(
                "<$raw> should classify as BakaSU",
                KSU_VARIANT_BAKASU,
                KernelSupport.inferRuntimeKsuFamily(raw),
            )
        }
        // SukiSU spellings, including the kernel full-version string that carries the
        // "SukiSU-Ultra" repo name.
        listOf("SukiSU", "sukisu", "SukiSU-Ultra", "v4.3.0-1a2b3c4d@SukiSU-Ultra-main").forEach { raw ->
            assertEquals(
                "<$raw> should classify as SukiSU",
                KSU_VARIANT_SUKISU,
                KernelSupport.inferRuntimeKsuFamily(raw),
            )
        }
        // A hyphenated BakaSU org name in the kernel full-version string wins over any
        // SukiSU substring in the same probe text.
        assertEquals(
            KSU_VARIANT_BAKASU,
            KernelSupport.inferRuntimeKsuFamily("v4.2.0-deadbeef@Baka-SU-main · kernel 31500 · Inline"),
        )
        // Pre-rename short spelling must not fall through to the SukiSU arm.
        assertEquals(
            KSU_VARIANT_BAKASU,
            KernelSupport.inferRuntimeKsuFamily("ReSuki-v3.2.0-1a2b3c4d"),
        )
        assertNull(KernelSupport.inferRuntimeKsuFamily(null))
        assertNull(KernelSupport.inferRuntimeKsuFamily(""))
        assertNull(KernelSupport.inferRuntimeKsuFamily("KernelSU"))
        assertNull(KernelSupport.inferRuntimeKsuFamily("31500"))
    }

    @Test
    fun customSourceValidationAcceptsOrderedDuplicatesAndRejectsUnsafePaths() {
        val valid = KernelBuildConfig(
            buildTarget = BUILD_TARGET_CUSTOM_SOURCE,
            sourceUrl = "https://github.com/LineageOS/android_kernel_xiaomi_sm8635.git",
            sourceRef = "lineage-23.2",
            sourceDefconfigs = listOf("vendor/base.config", "gki_defconfig", "vendor/base.config"),
            osPatchLevel = "2025-09",
            kernelsuVariant = KSU_VARIANT_NONE,
        )
        assertEquals(null, KernelSupport.validateCustomSource(valid))
        assertTrue(
            KernelSupport.validateCustomSource(
                valid.copy(sourceDefconfigs = listOf("gki_defconfig", "../secret"))
            ) != null
        )
        assertTrue(
            KernelSupport.validateCustomSource(
                valid.copy(sourceUrl = "https://user:pass@github.com/example/kernel.git")
            ) != null
        )
        assertTrue(KernelSupport.validateCustomSource(valid.copy(sourceRef = "refs/heads/../main")) != null)
        assertTrue(KernelSupport.validateCustomSource(valid.copy(sourceRef = "lineage\n23.2")) != null)
    }

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
    fun android17KernelLineUsesModernZramAndVirtualizationRules() {
        // 6.18 与 6.12 同为“新式”内核：虚拟化只有开关两档，legacy lz4kd 补丁栈不适用。
        assertEquals(listOf("off", "on"), KernelSupport.virtualizationSupportOptions("6.18"))
        assertFalse(KernelSupport.onePlusLz4kdSupported("6.18"))
        assertFalse(KernelSupport.onePlusLz4kdSupported("6.12"))
        assertTrue(KernelSupport.onePlusLz4kdSupported("6.6"))
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
    fun normalizeOnePlus15tUsesSm8850Android16Profile() {
        val normalized = KernelSupport.normalize(
            KernelBuildConfig(
                buildTarget = BUILD_TARGET_ONEPLUS,
                kernelsuVariant = KSU_VARIANT_SUKISU,
                cancelSusfs = false,
                onePlusDeviceManifest = "oneplus_15t",
                onePlusUseLz4kd = true
            )
        )

        assertEquals("sm8850", normalized.onePlusCpu)
        assertEquals("android16", normalized.androidVersion)
        assertEquals("6.12", normalized.kernelVersion)
        assertFalse(normalized.cancelSusfs)
        assertFalse(normalized.onePlusUseLz4kd)
        assertEquals(
            "OnePlus 15T · ColorOS/OxygenOS 16 · android16/6.12 · sm8850",
            KernelSupport.onePlusDeviceLabel(normalized.onePlusDeviceManifest)
        )
    }

    @Test
    fun normalizeDisablesKpmForBakasuDevAndLatest() {
        val dev = KernelSupport.normalize(
            KernelBuildConfig(
                kernelsuVariant = KSU_VARIANT_BAKASU,
                kernelsuBranch = KSU_BRANCH_DEV,
                useKpm = true,
                kpmPassword = "secret"
            )
        )
        val latest = KernelSupport.normalize(
            KernelBuildConfig(
                kernelsuVariant = KSU_VARIANT_BAKASU,
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
    fun normalizeDisablesKpmAndSusfsForBakasuOnAllBranches() {
        val stable = KernelSupport.normalize(
            KernelBuildConfig(
                kernelsuVariant = KSU_VARIANT_BAKASU,
                kernelsuBranch = KSU_BRANCH_STABLE,
                useKpm = true,
                kpmPassword = "secret",
                cancelSusfs = false
            )
        )
        val custom = KernelSupport.normalize(
            KernelBuildConfig(
                kernelsuVariant = KSU_VARIANT_BAKASU,
                kernelsuBranch = KSU_BRANCH_CUSTOM,
                useKpm = true,
                kpmPassword = "secret",
                cancelSusfs = false
            )
        )

        assertFalse(stable.useKpm)
        assertEquals("", stable.kpmPassword)
        assertTrue(stable.cancelSusfs)
        assertFalse(custom.useKpm)
        assertEquals("", custom.kpmPassword)
        assertTrue(custom.cancelSusfs)
        assertFalse(KernelSupport.isKpmSupported(BUILD_TARGET_GKI, KSU_VARIANT_BAKASU, KSU_BRANCH_STABLE))
        assertFalse(KernelSupport.isSusfsSelectable(KSU_VARIANT_BAKASU))
    }

    @Test
    fun normalizeKeepsKpmAndSusfsForSukisu() {
        val normalized = KernelSupport.normalize(
            KernelBuildConfig(
                kernelsuVariant = KSU_VARIANT_SUKISU,
                kernelsuBranch = KSU_BRANCH_STABLE,
                useKpm = true,
                kpmPassword = "secret",
                cancelSusfs = false
            )
        )

        assertTrue(normalized.useKpm)
        assertEquals("secret", normalized.kpmPassword)
        assertFalse(normalized.cancelSusfs)
        assertTrue(KernelSupport.isKpmSupported(BUILD_TARGET_GKI, KSU_VARIANT_SUKISU, KSU_BRANCH_STABLE))
        assertTrue(KernelSupport.isSusfsSelectable(KSU_VARIANT_SUKISU))
        assertTrue(KernelSupport.isSusfsSelectable(KSU_VARIANT_OFFICIAL))
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

    @Test
    fun customSourceValidationAcceptsBlankAndWellFormedKernelOverride() {
        val base = KernelBuildConfig(
            buildTarget = BUILD_TARGET_CUSTOM_SOURCE,
            sourceUrl = "https://github.com/LineageOS/android_kernel_google_gs201.git",
            sourceRef = "lineage-21",
            sourceDefconfigs = listOf("gki_defconfig"),
            osPatchLevel = "2025-09",
            kernelsuVariant = KSU_VARIANT_NONE,
        )
        // 空 override 合法（走服务端自动检测）
        assertEquals(null, KernelSupport.validateCustomSource(base))
        // X.Y 与 X.Y.Z 合法
        assertEquals(null, KernelSupport.validateCustomSource(base.copy(sourceKernelVersionOverride = "5.10")))
        assertEquals(null, KernelSupport.validateCustomSource(base.copy(sourceKernelVersionOverride = "6.13.42")))
        // 非法格式被拒
        assertTrue(KernelSupport.validateCustomSource(base.copy(sourceKernelVersionOverride = "abc")) != null)
        assertTrue(KernelSupport.validateCustomSource(base.copy(sourceKernelVersionOverride = "5")) != null)
        assertTrue(KernelSupport.validateCustomSource(base.copy(sourceKernelVersionOverride = "5.10.1.2")) != null)
    }

    @Test
    fun normalizeClearsKernelOverrideForNonCustomSourceTargets() {
        val gki = KernelSupport.normalize(
            KernelBuildConfig(
                buildTarget = BUILD_TARGET_GKI,
                sourceKernelVersionOverride = "5.10.177",
            )
        )
        assertEquals("", gki.sourceKernelVersionOverride)

        val custom = KernelSupport.normalize(
            KernelBuildConfig(
                buildTarget = BUILD_TARGET_CUSTOM_SOURCE,
                sourceUrl = "https://github.com/example/kernel.git",
                sourceRef = "lineage-21",
                sourceKernelVersionOverride = "  6.6.50  ",
                kernelsuVariant = KSU_VARIANT_NONE,
            )
        )
        assertEquals("6.6.50", custom.sourceKernelVersionOverride)
    }

    @Test
    fun bbgAndSuppOpAreUnsupportedOnAndroid17Kernel618Only() {
        assertFalse(KernelSupport.bbgSupported("android17", "6.18"))
        assertFalse(KernelSupport.suppOpSupported("android17", "6.18"))
        assertTrue(KernelSupport.bbgSupported("android16", "6.12"))
        assertTrue(KernelSupport.suppOpSupported("android16", "6.12"))
    }

    @Test
    fun normalizePreservesSusfsPreferenceOnAndroid17Kernel618() {
        val normalized = KernelSupport.normalize(
            KernelBuildConfig(
                androidVersion = "android17",
                kernelVersion = "6.18",
                subLevel = "32",
                osPatchLevel = "2026-09",
                kernelsuVariant = KSU_VARIANT_SUKISU,
                kernelsuBranch = KSU_BRANCH_STABLE,
                cancelSusfs = false
            )
        )

        assertEquals("android17", normalized.androidVersion)
        assertEquals("6.18", normalized.kernelVersion)
        assertEquals("32", normalized.subLevel)
        assertEquals("2026-09", normalized.osPatchLevel)
        assertFalse(normalized.cancelSusfs)

        val back = KernelSupport.normalize(
            normalized.copy(androidVersion = "android16", kernelVersion = "6.12")
        )
        assertEquals("android16", back.androidVersion)
        assertFalse(back.cancelSusfs)
    }

    @Test
    fun android17Kernel618DisablesLineSpecificFeatures() {
        assertFalse(KernelSupport.bbgSupported("android17", "6.18"))
        assertFalse(KernelSupport.suppOpSupported("android17", "6.18"))
        assertTrue(KernelSupport.bbgSupported("android16", "6.12"))
        assertTrue(KernelSupport.suppOpSupported("android16", "6.12"))
    }

    @Test
    fun normalizeKeepsSusfsSelectableOnAndroid16Kernel612() {
        val normalized = KernelSupport.normalize(
            KernelBuildConfig(
                androidVersion = "android16",
                kernelVersion = "6.12",
                kernelsuVariant = KSU_VARIANT_SUKISU,
                kernelsuBranch = KSU_BRANCH_STABLE,
                cancelSusfs = false
            )
        )

        assertEquals("android16", normalized.androidVersion)
        assertEquals("6.12", normalized.kernelVersion)
        assertEquals("69", normalized.subLevel)
        assertEquals("2026-03", normalized.osPatchLevel)
        // 6.12 不受 6.18 门控影响
        assertFalse(normalized.cancelSusfs)
    }

    @Test
    fun android17Kernel618IsGkiOnlyAndHasThreeSupportEntries() {
        assertTrue("android17" in KernelSupport.androidVersions())
        assertTrue("6.18" in KernelSupport.kernelVersions())
        // 一加没有 A17 机型，一加线不接入 android17
        assertFalse("android17" in KernelSupport.onePlusAndroidVersions())
        assertFalse("6.18" in KernelSupport.onePlusKernelVersions())

        assertEquals(
            listOf("20", "21", "32"),
            KernelSupport.subLevelOptions("android17", "6.18").filter { it != "X" }
        )
        assertEquals(
            listOf("2026-04"),
            KernelSupport.patchLevelOptions("android17", "6.18", "20")
        )
        assertEquals(
            listOf("2026-06"),
            KernelSupport.patchLevelOptions("android17", "6.18", "21")
        )
        assertEquals(
            listOf("2026-09"),
            KernelSupport.patchLevelOptions("android17", "6.18", "32")
        )
    }
}
