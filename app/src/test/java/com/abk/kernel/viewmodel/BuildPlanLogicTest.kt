package com.abk.kernel.viewmodel

import com.abk.kernel.data.model.CustomExternalModule
import com.abk.kernel.data.model.CustomExternalModuleEntryKind
import com.abk.kernel.data.model.CustomExternalModuleStage
import com.abk.kernel.data.model.CustomKernelOption
import com.abk.kernel.data.model.CustomKernelOptionMode
import com.abk.kernel.data.model.BUILD_TARGET_GKI
import com.abk.kernel.data.model.BUILD_TARGET_CUSTOM_SOURCE
import com.abk.kernel.data.model.BUILD_TARGET_ONEPLUS
import com.abk.kernel.data.model.KSU_BRANCH_CUSTOM
import com.abk.kernel.data.model.KSU_BRANCH_STABLE
import com.abk.kernel.data.model.KSU_VARIANT_NONE
import com.abk.kernel.data.model.KSU_VARIANT_NYASU
import com.abk.kernel.data.model.SOURCE_ACCESS_GITHUB_PRIVATE
import com.abk.kernel.data.model.SOURCE_ACCESS_PUBLIC
import com.abk.kernel.data.model.KernelBuildConfig
import com.abk.kernel.data.model.KernelSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildPlanLogicTest {

    @Test
    fun fullBuildPlanPayloadRoundTripsConfigAndModules() {
        val config = KernelSupport.normalize(
            KernelBuildConfig(
                androidVersion = "android14",
                kernelVersion = "6.1",
                subLevel = "162",
                osPatchLevel = "2026-03",
                kernelsuVariant = KSU_VARIANT_NYASU,
                kernelsuBranch = KSU_BRANCH_CUSTOM,
                customRef = "feature:5",
                version = "test-build",
                buildTime = "Sun Dec 01 08:10:00 UTC 2024",
                useZram = true,
                useBbg = true,
                useDdk = true,
                useNtsync = true,
                useNetworking = true,
                useRekernel = true,
                suppOp = true,
                zramFullAlgo = true,
                zramExtraAlgos = "lz4,zstd",
                patchModules = "https://github.com/example/ksu-module.git",
                virtualizationSupport = "678",
                customKernelOptions = listOf(
                    CustomKernelOption("CONFIG_TMPFS", CustomKernelOptionMode.ENABLED_Y),
                    CustomKernelOption("CONFIG_IP_SET_MAX", CustomKernelOptionMode.RAW, "65534")
                ),
                useCustomExternalModules = true,
                customExternalModules = listOf(
                    CustomExternalModule(" https://github.com/example/module.git ", "before-build")
                )
            )
        )

        val payload = encodeBuildPlanPayload(config, "My Plan", BuildPlanShareScope.FULL)
        val decoded = decodeBuildPlanPayload(payload, KernelBuildConfig())

        assertEquals("My Plan", decoded.name)
        assertEquals(BuildPlanShareScope.FULL, decoded.scope)
        assertEquals(config.androidVersion, decoded.config.androidVersion)
        assertEquals(config.kernelVersion, decoded.config.kernelVersion)
        assertEquals(config.subLevel, decoded.config.subLevel)
        assertEquals(config.osPatchLevel, decoded.config.osPatchLevel)
        assertEquals(config.kernelsuVariant, decoded.config.kernelsuVariant)
        assertEquals(config.kernelsuBranch, decoded.config.kernelsuBranch)
        assertEquals(config.customRef, decoded.config.customRef)
        assertEquals(config.version, decoded.config.version)
        assertEquals(config.buildTime, decoded.config.buildTime)
        assertEquals(config.zramExtraAlgos, decoded.config.zramExtraAlgos)
        assertEquals(KSU_VARIANT_NYASU, decoded.config.kernelsuVariant)
        assertEquals(config.virtualizationSupport, decoded.config.virtualizationSupport)
        assertEquals(config.customKernelOptions, decoded.config.customKernelOptions)
        assertEquals(
            listOf(CustomExternalModule("https://github.com/example/module.git", CustomExternalModuleStage.BEFORE_BUILD)),
            decoded.config.customExternalModules
        )
    }

    @Test
    fun featuresOnlyPayloadKeepsBaseVersionLine() {
        val baseConfig = KernelSupport.normalize(
            KernelBuildConfig(
                androidVersion = "android12",
                kernelVersion = "5.10",
                subLevel = "66",
                osPatchLevel = "2022-01"
            )
        )
        val sharedFeatures = KernelSupport.normalize(
            KernelBuildConfig(
                androidVersion = "android16",
                kernelVersion = "6.12",
                subLevel = "69",
                osPatchLevel = "2026-03",
                useZram = true,
                useNetworking = true,
                virtualizationSupport = "678",
                customKernelOptions = listOf(
                    CustomKernelOption("CONFIG_TMPFS", CustomKernelOptionMode.ENABLED_Y)
                )
            )
        )

        val payload = encodeBuildPlanPayload(sharedFeatures, "features", BuildPlanShareScope.FEATURES_ONLY)
        val decoded = decodeBuildPlanPayload(payload, baseConfig)

        assertEquals(BuildPlanShareScope.FEATURES_ONLY, decoded.scope)
        assertEquals(baseConfig.androidVersion, decoded.config.androidVersion)
        assertEquals(baseConfig.kernelVersion, decoded.config.kernelVersion)
        assertEquals(baseConfig.subLevel, decoded.config.subLevel)
        assertEquals(baseConfig.osPatchLevel, decoded.config.osPatchLevel)
        assertEquals(sharedFeatures.useZram, decoded.config.useZram)
        assertEquals(sharedFeatures.useNetworking, decoded.config.useNetworking)
        assertEquals(sharedFeatures.customKernelOptions, decoded.config.customKernelOptions)
    }

    @Test
    fun customSourceFullPlanRoundTripsWithoutCredentials() {
        val config = KernelSupport.normalize(
            KernelBuildConfig(
                buildTarget = BUILD_TARGET_CUSTOM_SOURCE,
                sourceUrl = "https://github.com/example/private-kernel.git",
                sourceRef = "lineage-23.2",
                sourceAccessMode = SOURCE_ACCESS_GITHUB_PRIVATE,
                sourceDefconfigs = listOf(
                    "gki_defconfig",
                    "vendor/pineapple_GKI.config",
                    "vendor/peridot_GKI.config"
                ),
                sourceDeviceLabel = "POCO F6 / peridot",
                osPatchLevel = "2025-09",
                kernelsuVariant = KSU_VARIANT_NONE,
            )
        )

        val payload = encodeBuildPlanPayload(config, "private source", BuildPlanShareScope.FULL)
        val decoded = decodeBuildPlanPayload(payload, KernelBuildConfig())

        assertEquals(BUILD_TARGET_CUSTOM_SOURCE, decoded.config.buildTarget)
        assertEquals(config.sourceUrl, decoded.config.sourceUrl)
        assertEquals(config.sourceRef, decoded.config.sourceRef)
        assertEquals(config.sourceAccessMode, decoded.config.sourceAccessMode)
        assertEquals(config.sourceDefconfigs, decoded.config.sourceDefconfigs)
        assertEquals(config.sourceDeviceLabel, decoded.config.sourceDeviceLabel)
        assertEquals(config.osPatchLevel, decoded.config.osPatchLevel)
    }

    @Test
    fun customSourceWorkflowInputMapMatchesTwentyFiveInputContract() {
        val inputs = KernelBuildConfig(
            buildTarget = BUILD_TARGET_CUSTOM_SOURCE,
            sourceUrl = "https://github.com/LineageOS/android_kernel_xiaomi_sm8635.git",
            sourceRef = "lineage-23.2",
            sourceAccessMode = SOURCE_ACCESS_GITHUB_PRIVATE,
            sourceDefconfigs = listOf("gki_defconfig", "vendor/peridot_GKI.config"),
            sourceDeviceLabel = "POCO F6 / peridot",
            osPatchLevel = "2025-09",
            kernelsuVariant = KSU_VARIANT_NONE,
        ).toInputMap()

        assertEquals(25, inputs.size)
        assertEquals("true", inputs["source_private"])
        assertEquals("gki_defconfig\nvendor/peridot_GKI.config", inputs["defconfigs"])
        assertEquals("None", inputs["kernelsu_variant"])
        assertEquals(false, inputs.keys.any { it.contains("token", ignoreCase = true) || it.contains("credential", ignoreCase = true) })
        // os_patch_level 与 kernel_version_override 合并进 version_overrides（JSON），规避 dispatch 25 输入上限
        assertEquals(false, inputs.containsKey("os_patch_level"))
        assertEquals(false, inputs.containsKey("kernel_version_override"))
        val overrides = com.google.gson.Gson().fromJson(inputs["version_overrides"], Map::class.java)
        assertEquals("2025-09", overrides["os_patch_level"])
        assertEquals("", overrides["kernel_version_override"])
    }

    @Test
    fun workflowInputMapSerializesBooleansAndExternalModules() {
        val inputs = KernelBuildConfig(
            androidVersion = "android14",
            kernelVersion = "6.1",
            subLevel = "162",
            osPatchLevel = "2026-03",
            useZram = true,
            useDdk = true,
            kernelsuBranch = KSU_BRANCH_CUSTOM,
            customRef = "  main:5  ",
            useCustomExternalModules = true,
            customExternalModules = listOf(
                CustomExternalModule(" https://github.com/example/a.git ", "after-patch"),
                CustomExternalModule("https://github.com/example/b.git", "before-build")
            )
        ).toInputMap()

        assertEquals("android14", inputs["android_version"])
        assertEquals("6.1", inputs["kernel_version"])
        assertEquals("162", inputs["sub_level"])
        assertEquals("true", inputs["use_zram"])
        assertEquals("true", inputs["use_ddk"])
        assertEquals(KSU_BRANCH_CUSTOM, inputs["kernelsu_branch"])
        assertEquals("main:5", inputs["custom_ref"])
        assertEquals("", inputs["custom_kernel_options"])
        assertEquals(
            "module:https://github.com/example/a.git;after_patch|module:https://github.com/example/b.git;before_build",
            inputs["custom_external_modules"]
        )
    }

    @Test
    fun workflowInputMapSerializesModuleSetChildren() {
        val inputs = KernelBuildConfig(
            useCustomExternalModules = true,
            customExternalModules = listOf(
                CustomExternalModule(
                    url = "https://github.com/example/security-suite.git",
                    stage = "before_build",
                    entryKind = CustomExternalModuleEntryKind.MODULE_SET_CHILD,
                    groupRepoUrl = "https://github.com/example/security-suite.git",
                    childId = "fix_cleanup",
                    childName = "Policy Cleanup",
                    groupId = "security_suite",
                    groupName = "Security Suite"
                )
            )
        ).toInputMap()

        assertEquals(
            "set:https://github.com/example/security-suite.git#fix_cleanup;before_build",
            inputs["custom_external_modules"]
        )
    }

    @Test
    fun workflowInputMapSerializesCustomKernelOptionsWithoutIgnoreEntries() {
        val inputs = KernelBuildConfig(
            customKernelOptions = listOf(
                CustomKernelOption("CONFIG_TMPFS", CustomKernelOptionMode.ENABLED_Y),
                CustomKernelOption("CONFIG_NETFILTER", CustomKernelOptionMode.ENABLED_M),
                CustomKernelOption("CONFIG_UNUSED", CustomKernelOptionMode.IGNORE),
                CustomKernelOption("CONFIG_IP_SET_MAX", CustomKernelOptionMode.RAW, "65534"),
                CustomKernelOption("CONFIG_FOO", CustomKernelOptionMode.DISABLED)
            )
        ).toInputMap()

        assertEquals(
            "CONFIG_TMPFS=y\nCONFIG_NETFILTER=m\nCONFIG_IP_SET_MAX=65534\n# CONFIG_FOO is not set",
            inputs["custom_kernel_options"]
        )
    }

    @Test
    fun workflowInputMapKeepsNoneVariantAsNone() {
        val inputs = KernelBuildConfig(
            kernelsuVariant = KSU_VARIANT_NONE,
            kernelsuBranch = KSU_BRANCH_CUSTOM,
            customRef = "ignored"
        ).toInputMap()

        assertEquals(KSU_VARIANT_NONE, inputs["kernelsu_variant"])
        assertEquals(KSU_BRANCH_STABLE, inputs["kernelsu_branch"])
        assertEquals("", inputs["custom_ref"])
    }

    @Test
    fun onePlusWorkflowInputMapUsesOnePlusWorkflowFields() {
        val inputs = KernelBuildConfig(
            buildTarget = BUILD_TARGET_ONEPLUS,
            androidVersion = "android14",
            kernelVersion = "6.1",
            kernelsuVariant = KSU_VARIANT_NYASU,
            useBbg = true,
            onePlusCpu = "sm8650",
            onePlusDeviceManifest = "oneplus_12_b",
            onePlusUseLz4kd = true,
            onePlusUseBbr = true,
            onePlusUseProxyOptimization = true,
            onePlusUseUnicodeBypass = true
        ).toInputMap()

        assertEquals("sm8650", inputs["cpu"])
        assertEquals("oneplus_12_b", inputs["device_manifest"])
        assertEquals("android14", inputs["android_version"])
        assertEquals("6.1", inputs["kernel_version"])
        assertEquals(KSU_VARIANT_NYASU, inputs["ksu_variant"])
        assertFalse(inputs.containsKey("enable_susfs"))
        assertFalse(inputs.containsKey("use_kpm"))
        assertEquals("true", inputs["use_lz4kd"])
        assertEquals("true", inputs["use_bbr"])
        assertEquals("true", inputs["use_proxy_optimization"])
        assertEquals("true", inputs["use_unicode_bypass"])
    }

    @Test
    fun onePlusBuildPlanPayloadRoundTripsTargetAndDeviceFields() {
        val config = KernelSupport.normalize(
            KernelBuildConfig(
                buildTarget = BUILD_TARGET_ONEPLUS,
                androidVersion = "android15",
                kernelVersion = "6.6",
                kernelsuVariant = KSU_VARIANT_NYASU,
                onePlusCpu = "sm8750",
                onePlusDeviceManifest = "oneplus_13_b",
                onePlusUseLz4kd = true,
                onePlusUseBbr = true,
                onePlusUseProxyOptimization = true,
                onePlusUseUnicodeBypass = true
            )
        )

        val payload = encodeBuildPlanPayload(config, "OnePlus", BuildPlanShareScope.FULL)
        val decoded = decodeBuildPlanPayload(payload, KernelBuildConfig())

        assertEquals(BUILD_TARGET_ONEPLUS, decoded.config.buildTarget)
        assertEquals("sm8750", decoded.config.onePlusCpu)
        assertEquals("oneplus_13_b", decoded.config.onePlusDeviceManifest)
        assertEquals(config.onePlusUseLz4kd, decoded.config.onePlusUseLz4kd)
        assertEquals(config.onePlusUseBbr, decoded.config.onePlusUseBbr)
        assertEquals(config.onePlusUseProxyOptimization, decoded.config.onePlusUseProxyOptimization)
        assertEquals(config.onePlusUseUnicodeBypass, decoded.config.onePlusUseUnicodeBypass)
    }

    @Test
    fun onePlusFeaturesOnlyPayloadKeepsBaseBuildTarget() {
        val baseConfig = KernelSupport.normalize(
            KernelBuildConfig(
                buildTarget = BUILD_TARGET_GKI,
                kernelsuVariant = KSU_VARIANT_NONE
            )
        )
        val sharedFeatures = KernelSupport.normalize(
            KernelBuildConfig(
                buildTarget = BUILD_TARGET_ONEPLUS,
                kernelsuVariant = KSU_VARIANT_NYASU,
                onePlusUseLz4kd = true,
                onePlusUseBbr = true
            )
        )

        val payload = encodeBuildPlanPayload(sharedFeatures, "features", BuildPlanShareScope.FEATURES_ONLY)
        val decoded = decodeBuildPlanPayload(payload, baseConfig)

        assertEquals(BUILD_TARGET_GKI, decoded.config.buildTarget)
        assertEquals(baseConfig.onePlusDeviceManifest, decoded.config.onePlusDeviceManifest)
        assertEquals(false, decoded.config.onePlusUseLz4kd)
        assertEquals(false, decoded.config.onePlusUseBbr)
    }

    @Test
    fun parseCustomKernelOptionsTextSupportsCommonKconfigFormats() {
        val result = parseCustomKernelOptionsText(
            """
            CONFIG_TMPFS=y
            CONFIG_NETFILTER=m
            # CONFIG_FOO is not set
            CONFIG_IP_SET_MAX=65534
            CONFIG_LOCALVERSION=\"-abk\"
            CONFIG_KEEP
            CONFIG_TMPFS=m
            # comment only

            """.trimIndent()
        )

        assertEquals(6, result.importedCount)
        assertEquals(1, result.duplicateCount)
        assertEquals(2, result.skippedCount)
        assertEquals(CustomKernelOptionMode.ENABLED_M, result.options.first { it.symbol == "CONFIG_TMPFS" }.mode)
        assertEquals(CustomKernelOptionMode.ENABLED_M, result.options.first { it.symbol == "CONFIG_NETFILTER" }.mode)
        assertEquals(CustomKernelOptionMode.DISABLED, result.options.first { it.symbol == "CONFIG_FOO" }.mode)
        assertEquals("65534", result.options.first { it.symbol == "CONFIG_IP_SET_MAX" }.rawValue)
        assertEquals("\"-abk\"", result.options.first { it.symbol == "CONFIG_LOCALVERSION" }.rawValue)
        assertEquals(CustomKernelOptionMode.IGNORE, result.options.first { it.symbol == "CONFIG_KEEP" }.mode)
    }

    @Test
    fun summarizeCustomKernelOptionsCountsRawAsEnabled() {
        val summary = summarizeCustomKernelOptions(
            listOf(
                CustomKernelOption("CONFIG_TMPFS", CustomKernelOptionMode.ENABLED_Y),
                CustomKernelOption("CONFIG_NETFILTER", CustomKernelOptionMode.ENABLED_M),
                CustomKernelOption("CONFIG_IP_SET_MAX", CustomKernelOptionMode.RAW, "65534"),
                CustomKernelOption("CONFIG_FOO", CustomKernelOptionMode.DISABLED),
                CustomKernelOption("CONFIG_KEEP", CustomKernelOptionMode.IGNORE)
            )
        )

        assertEquals(5, summary.total)
        assertEquals(3, summary.enabled)
        assertEquals(1, summary.disabled)
        assertEquals(1, summary.ignored)
    }

    @Test
    fun mergeCustomKernelOptionsLetsLaterEntriesOverrideEarlierOnes() {
        val merged = mergeCustomKernelOptions(
            options = listOf(
                CustomKernelOption("CONFIG_TMPFS", CustomKernelOptionMode.ENABLED_Y),
                CustomKernelOption("CONFIG_NETFILTER", CustomKernelOptionMode.ENABLED_M)
            ),
            updates = listOf(
                CustomKernelOption("config_tmpfs", CustomKernelOptionMode.DISABLED),
                CustomKernelOption("CONFIG_IP_SET_MAX", CustomKernelOptionMode.RAW, "65534")
            )
        )

        assertEquals(
            listOf(
                CustomKernelOption("CONFIG_NETFILTER", CustomKernelOptionMode.ENABLED_M),
                CustomKernelOption("CONFIG_TMPFS", CustomKernelOptionMode.DISABLED),
                CustomKernelOption("CONFIG_IP_SET_MAX", CustomKernelOptionMode.RAW, "65534")
            ),
            merged
        )
    }

    @Test
    fun removeCustomKernelOptionsAtIndicesOnlyDropsSelectedRows() {
        val remaining = removeCustomKernelOptionsAtIndices(
            options = listOf(
                CustomKernelOption("CONFIG_TMPFS", CustomKernelOptionMode.ENABLED_Y),
                CustomKernelOption("CONFIG_TMPFS", CustomKernelOptionMode.DISABLED),
                CustomKernelOption("CONFIG_IP_SET_MAX", CustomKernelOptionMode.RAW, "65534")
            ),
            indices = listOf(1)
        )

        assertEquals(
            listOf(
                CustomKernelOption("CONFIG_TMPFS", CustomKernelOptionMode.ENABLED_Y),
                CustomKernelOption("CONFIG_IP_SET_MAX", CustomKernelOptionMode.RAW, "65534")
            ),
            remaining
        )
    }

    @Test
    fun workflowInputMapSendsPatchModulesForNyasuAndDropsThemForNone() {
        val nyasu = KernelBuildConfig(
            kernelsuVariant = KSU_VARIANT_NYASU,
            patchModules = "  https://github.com/example/a.git@main|https://github.com/example/b.git  ",
        ).toInputMap()
        assertEquals(
            "https://github.com/example/a.git@main|https://github.com/example/b.git",
            nyasu["patch_modules"],
        )
        assertEquals(KSU_VARIANT_NYASU, nyasu["kernelsu_variant"])
        assertFalse(nyasu.keys.any { it in setOf("enable_susfs", "use_kpm", "kpm_password") })

        val none = KernelBuildConfig(
            kernelsuVariant = KSU_VARIANT_NONE,
            patchModules = "https://github.com/example/a.git",
        ).toInputMap()
        assertEquals("", none["patch_modules"])
        assertEquals(KSU_VARIANT_NONE, none["kernelsu_variant"])
    }

    @Test
    fun legacyVersionEightPlanDecodesEveryRetiredVariantIntoNyasu() {
        listOf(0, 1, 2).forEach { legacyIndex ->
            val decoded = decodeBuildPlanPayload(
                legacyVersionEightPayload(ksuVariantIndex = legacyIndex),
                KernelBuildConfig(),
            )

            assertEquals(KSU_VARIANT_NYASU, decoded.config.kernelsuVariant)
            assertEquals("legacy", decoded.name)
            assertEquals("android14", decoded.config.androidVersion)
            assertEquals("6.1", decoded.config.kernelVersion)
            assertTrue(decoded.config.useZram)
        }
        // 索引 3 仍代表 None，不能被折叠成 NyaSU。
        assertEquals(
            KSU_VARIANT_NONE,
            decodeBuildPlanPayload(legacyVersionEightPayload(ksuVariantIndex = 3), KernelBuildConfig())
                .config.kernelsuVariant,
        )
    }

    private fun legacyVersionEightPayload(ksuVariantIndex: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        fun byte(value: Int) = out.write(value and 0xff)
        fun varInt(value: Int) {
            var remaining = value
            do {
                var b = remaining and 0x7f
                remaining = remaining ushr 7
                if (remaining != 0) b = b or 0x80
                byte(b)
            } while (remaining != 0)
        }
        fun string(value: String) {
            val bytes = value.toByteArray(Charsets.UTF_8)
            varInt(bytes.size)
            out.write(bytes)
        }
        // 版本 8：仍带 KPM 密码字符串，变体索引 0/1/2 分别是 Official/SukiSU/BakaSU。
        byte(8)
        byte(BuildPlanShareScope.FULL.toWireValue())
        string("legacy")
        string("android14")
        string("6.1")
        string("162")
        string("2026-03")
        string("")
        string(BUILD_TARGET_GKI)
        string("sm8650")
        string("oneplus_12_b")
        string("https://github.com/example/kernel.git")
        string("lineage-21")
        string(SOURCE_ACCESS_PUBLIC)
        varInt(1)
        string("gki_defconfig")
        string("POCO F6")
        byte(ksuVariantIndex)
        byte(1) // Stable
        byte(0) // virtualization off
        varInt(1 shl 0) // useZram
        string("")
        string("")
        string("")
        string("") // 旧版 KPM 密码字段
        string("") // customRef
        varInt(0) // 无内核选项
        varInt(0) // 无外部模块
        return out.toByteArray()
    }
}
