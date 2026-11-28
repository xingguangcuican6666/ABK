package com.abk.kernel.ui.build

import com.abk.kernel.data.model.BUILD_TARGET_CUSTOM_SOURCE
import com.abk.kernel.data.model.BUILD_TARGET_GKI
import com.abk.kernel.data.model.BUILD_TARGET_ONEPLUS
import com.abk.kernel.data.model.KSU_VARIANT_NONE
import com.abk.kernel.data.model.KernelBuildConfig
import com.abk.kernel.data.model.KernelSupport
import io.github.xingguangcuican6666.abk.ui.components.AbkStepState

/**
 * Build wizard steps. The page renders one step at a time so the form never
 * shows an entire kernel build at once; the order mirrors how a build is put
 * together: what to build, which kernel, extras, root scheme, confirm.
 */
enum class BuildWizardStep {
    Target,
    Kernel,
    Features,
    Root,
    Review,
}

/** Wire order of the steps; [stepStates] and the navigation rows read this. */
val BuildWizardSteps: List<BuildWizardStep> = BuildWizardStep.entries

/**
 * Step marker state for [index]: earlier steps are complete, the current one is
 * current, later ones are pending. Pure so the wizard can be unit-tested.
 */
fun stepStates(currentIndex: Int, count: Int = BuildWizardSteps.size): List<AbkStepState> {
    require(count >= 0) { "count must not be negative" }
    val current = currentIndex.coerceIn(0, (count - 1).coerceAtLeast(0))
    return List(count) { index ->
        when {
            index < current -> AbkStepState.Complete
            index == current -> AbkStepState.Current
            else -> AbkStepState.Pending
        }
    }
}

/** Whether [step] has everything it needs before the wizard may move on. */
fun canAdvanceFrom(config: KernelBuildConfig, step: BuildWizardStep): Boolean {
    val normalized = KernelSupport.normalize(config)
    return when (step) {
        BuildWizardStep.Target ->
            normalized.buildTarget in setOf(BUILD_TARGET_GKI, BUILD_TARGET_ONEPLUS, BUILD_TARGET_CUSTOM_SOURCE)
        BuildWizardStep.Kernel ->
            if (normalized.buildTarget == BUILD_TARGET_CUSTOM_SOURCE) {
                KernelSupport.validateCustomSource(normalized) == null
            } else {
                normalized.androidVersion.isNotBlank() &&
                    normalized.kernelVersion.isNotBlank() &&
                    normalized.subLevel.isNotBlank() &&
                    normalized.osPatchLevel.isNotBlank()
            }
        BuildWizardStep.Features -> true
        BuildWizardStep.Root ->
            normalized.kernelsuVariant == KSU_VARIANT_NONE || normalized.kernelsuVariant.isNotBlank()
        BuildWizardStep.Review -> true
    }
}
