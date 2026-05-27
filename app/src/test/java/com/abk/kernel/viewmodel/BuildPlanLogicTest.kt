package com.abk.kernel.viewmodel

import com.abk.kernel.data.model.CustomExternalModule
import com.abk.kernel.data.model.CustomExternalModuleStage
import com.abk.kernel.data.model.BUILD_TARGET_GKI
import com.abk.kernel.data.model.BUILD_TARGET_ONEPLUS
import com.abk.kernel.data.model.KSU_BRANCH_CUSTOM
import com.abk.kernel.data.model.KSU_BRANCH_STABLE
import com.abk.kernel.data.model.KSU_VARIANT_NONE
import com.abk.kernel.data.model.KSU_VARIANT_RESUKISU
import com.abk.kernel.data.model.KSU_VARIANT_SUKISU
import com.abk.kernel.data.model.KernelBuildConfig
import com.abk.kernel.data.model.KernelSupport
import org.junit.Assert.assertEquals
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
                kernelsuVariant = KSU_VARIANT_SUKISU,
                kernelsuBranch = KSU_BRANCH_CUSTOM,
                customRef = "feature:5",
                version = "test-build",
                buildTime = "Sun Dec 01 08:10:00 UTC 2024",
                useZram = true,
                useBbg = true,
                useDdk = true,
                useNtsync = true,
                useNetworking = true,
                useKpm = true,
                useRekernel = true,
                cancelSusfs = true,
                suppOp = true,
                zramFullAlgo = true,
                zramExtraAlgos = "lz4,zstd",
                kpmPassword = "super-secret",
                virtualizationSupport = "678",
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
        assertEquals(config.kpmPassword, decoded.config.kpmPassword)
        assertEquals(config.virtualizationSupport, decoded.config.virtualizationSupport)
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
                virtualizationSupport = "678"
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
        assertEquals("true", inputs["use_custom_external_modules"])
        assertEquals(
            "https://github.com/example/a.git;after_patch|https://github.com/example/b.git;before_build",
            inputs["custom_external_modules"]
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
            kernelsuVariant = KSU_VARIANT_SUKISU,
            cancelSusfs = false,
            useKpm = true,
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
        assertEquals(KSU_VARIANT_SUKISU, inputs["ksu_variant"])
        assertEquals("true", inputs["enable_susfs"])
        assertEquals("true", inputs["use_kpm"])
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
                kernelsuVariant = KSU_VARIANT_SUKISU,
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
                kernelsuVariant = KSU_VARIANT_RESUKISU
            )
        )
        val sharedFeatures = KernelSupport.normalize(
            KernelBuildConfig(
                buildTarget = BUILD_TARGET_ONEPLUS,
                kernelsuVariant = KSU_VARIANT_SUKISU,
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
}
