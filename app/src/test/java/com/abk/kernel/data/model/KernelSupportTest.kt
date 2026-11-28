package com.abk.kernel.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KernelSupportTest {

    @Test
    fun normalizeKsuVariantFoldsRetiredVariantsIntoNyasu() {
        // 编译链只剩 NyaSU / None；旧构建记录与旧分享码里的变体一律归一到 NyaSU。
        listOf(
            "ReSukiSU", "resukisu", "Re-SukiSU", " re-suki ",
            KSU_VARIANT_OFFICIAL, KSU_VARIANT_SUKISU, KSU_VARIANT_BAKASU, "  bakasu ", "",
        ).forEach { legacy ->
            assertEquals(
                "legacy variant <$legacy> should normalize to NyaSU",
                KSU_VARIANT_NYASU,
                KernelSupport.normalizeKsuVariant(legacy, BUILD_TARGET_GKI),
            )
            assertEquals(
                "legacy variant <$legacy> should normalize to NyaSU on OnePlus",
                KSU_VARIANT_NYASU,
                KernelSupport.normalizeKsuVariant(legacy, BUILD_TARGET_ONEPLUS),
            )
        }
        // None（含中文写法）是唯一保留的第二条路径。
        assertEquals(KSU_VARIANT_NONE, KernelSupport.normalizeKsuVariant("none"))
        assertEquals(KSU_VARIANT_NONE, KernelSupport.normalizeKsuVariant(" 无 "))
        assertEquals(
            listOf(KSU_VARIANT_NYASU, KSU_VARIANT_NONE),
            KernelSupport.ksuVariantOptions(),
        )
        assertEquals(
            listOf(KSU_VARIANT_NYASU, KSU_VARIANT_NONE),
            KernelSupport.onePlusKsuVariantOptions(),
        )
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
        // 已装机的 NyaSU 内核要能识别出来（只影响运行时展示，不影响可编译选项）。
        listOf("NyaSU", "nyasu", "v6.6.30@NyaSU-main").forEach { raw ->
            assertEquals(
                "<$raw> should classify as NyaSU",
                KSU_VARIANT_NYASU,
                KernelSupport.inferRuntimeKsuFamily(raw),
            )
        }
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
                patchModules = "https://github.com/example/module.git",
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
        // None 不注入任何补丁模块，输入里残留的模块地址会被清空。
        assertEquals("", normalized.patchModules)
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
                kernelsuVariant = KSU_VARIANT_NYASU,
                onePlusCpu = "mt6991",
                onePlusDeviceManifest = "oneplus_ace5_ultra_b",
                onePlusUseProxyOptimization = true,
                useDdk = true,
                useCustomExternalModules = true,
                customExternalModules = listOf(CustomExternalModule("https://github.com/example/module"))
            )
        )

        assertEquals(BUILD_TARGET_ONEPLUS, normalized.buildTarget)
        assertEquals("android15", normalized.androidVersion)
        assertEquals("6.6", normalized.kernelVersion)
        assertEquals(KSU_VARIANT_NYASU, normalized.kernelsuVariant)
        assertEquals("mt6991", normalized.onePlusCpu)
        assertEquals("oneplus_ace5_ultra_b", normalized.onePlusDeviceManifest)
        assertFalse(normalized.onePlusUseProxyOptimization)
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
    fun normalizeOnePlusLegacyVariantBecomesNyasuOnStableBranch() {
        val normalized = KernelSupport.normalize(
            KernelBuildConfig(
                buildTarget = BUILD_TARGET_ONEPLUS,
                kernelsuVariant = KSU_VARIANT_BAKASU,
                kernelsuBranch = KSU_BRANCH_DEV,
                onePlusDeviceManifest = "oneplus_10t_v"
            )
        )

        assertEquals("android12", normalized.androidVersion)
        assertEquals("5.10", normalized.kernelVersion)
        assertEquals(KSU_VARIANT_NYASU, normalized.kernelsuVariant)
        // OnePlus 线固定走 Stable，不吃 GKI 的分支选择。
        assertEquals(KSU_BRANCH_STABLE, normalized.kernelsuBranch)
    }

    @Test
    fun normalizeOnePlus15tUsesSm8850Android16Profile() {
        val normalized = KernelSupport.normalize(
            KernelBuildConfig(
                buildTarget = BUILD_TARGET_ONEPLUS,
                kernelsuVariant = KSU_VARIANT_NYASU,
                onePlusDeviceManifest = "oneplus_15t",
                onePlusUseLz4kd = true
            )
        )

        assertEquals("sm8850", normalized.onePlusCpu)
        assertEquals("android16", normalized.androidVersion)
        assertEquals("6.12", normalized.kernelVersion)
        assertFalse(normalized.onePlusUseLz4kd)
        assertEquals(
            "OnePlus 15T · ColorOS/OxygenOS 16 · android16/6.12 · sm8850",
            KernelSupport.onePlusDeviceLabel(normalized.onePlusDeviceManifest)
        )
    }

    @Test
    fun normalizeTrimsPatchModulesAndKeepsThemForNyasu() {
        val normalized = KernelSupport.normalize(
            KernelBuildConfig(
                kernelsuVariant = KSU_VARIANT_NYASU,
                kernelsuBranch = KSU_BRANCH_DEV,
                patchModules = "  https://github.com/example/ksu-module.git  "
            )
        )

        assertEquals(KSU_VARIANT_NYASU, normalized.kernelsuVariant)
        assertEquals("https://github.com/example/ksu-module.git", normalized.patchModules)
    }

    @Test
    fun normalizeClearsPatchModulesWhenNoRootSchemeIsChosen() {
        val config = KernelBuildConfig(
            kernelsuVariant = KSU_VARIANT_NONE,
            patchModules = "https://github.com/example/ksu-module.git",
        )

        assertEquals("", KernelSupport.normalize(config).patchModules)
        // 切回 NyaSU 时补丁模块重新生效，不会被 normalize 永久吞掉。
        assertEquals(
            "https://github.com/example/ksu-module.git",
            KernelSupport.normalize(config.copy(kernelsuVariant = KSU_VARIANT_NYASU)).patchModules,
        )
    }

    @Test
    fun normalizeFoldsEveryRetiredVariantToNyasuOnEveryTarget() {
        listOf(BUILD_TARGET_GKI, BUILD_TARGET_ONEPLUS, BUILD_TARGET_CUSTOM_SOURCE).forEach { target ->
            listOf(KSU_VARIANT_OFFICIAL, KSU_VARIANT_SUKISU, KSU_VARIANT_BAKASU).forEach { legacy ->
                val normalized = KernelSupport.normalize(
                    KernelBuildConfig(buildTarget = target, kernelsuVariant = legacy)
                )
                assertEquals(
                    "retired variant <$legacy> on <$target>",
                    KSU_VARIANT_NYASU,
                    normalized.kernelsuVariant,
                )
            }
        }
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
    fun normalizePreservesNyasuOnAndroid17Kernel618() {
        val normalized = KernelSupport.normalize(
            KernelBuildConfig(
                androidVersion = "android17",
                kernelVersion = "6.18",
                subLevel = "32",
                osPatchLevel = "2026-09",
                kernelsuVariant = KSU_VARIANT_NYASU,
                kernelsuBranch = KSU_BRANCH_STABLE
            )
        )

        assertEquals("android17", normalized.androidVersion)
        assertEquals("6.18", normalized.kernelVersion)
        assertEquals("32", normalized.subLevel)
        assertEquals("2026-09", normalized.osPatchLevel)
        assertEquals(KSU_VARIANT_NYASU, normalized.kernelsuVariant)

        val back = KernelSupport.normalize(
            normalized.copy(androidVersion = "android16", kernelVersion = "6.12")
        )
        assertEquals("android16", back.androidVersion)
        assertEquals(KSU_VARIANT_NYASU, back.kernelsuVariant)
    }

    @Test
    fun android17Kernel618DisablesLineSpecificFeatures() {
        assertFalse(KernelSupport.bbgSupported("android17", "6.18"))
        assertFalse(KernelSupport.suppOpSupported("android17", "6.18"))
        assertTrue(KernelSupport.bbgSupported("android16", "6.12"))
        assertTrue(KernelSupport.suppOpSupported("android16", "6.12"))
    }

    @Test
    fun normalizeKeepsNyasuOnAndroid16Kernel612() {
        val normalized = KernelSupport.normalize(
            KernelBuildConfig(
                androidVersion = "android16",
                kernelVersion = "6.12",
                kernelsuVariant = KSU_VARIANT_NYASU,
                kernelsuBranch = KSU_BRANCH_STABLE
            )
        )

        assertEquals("android16", normalized.androidVersion)
        assertEquals("6.12", normalized.kernelVersion)
        assertEquals("69", normalized.subLevel)
        assertEquals("2026-03", normalized.osPatchLevel)
        assertEquals(KSU_VARIANT_NYASU, normalized.kernelsuVariant)
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
