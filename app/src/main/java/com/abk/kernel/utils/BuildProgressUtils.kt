package com.abk.kernel.utils
import com.abk.kernel.tr
import com.abk.kernel.R

import com.abk.kernel.data.model.BuildProgress
import com.abk.kernel.data.model.BuildStepProgress
import com.abk.kernel.data.model.WorkflowJob
import com.abk.kernel.data.model.WorkflowRun
import kotlin.math.roundToInt

object BuildProgressUtils {

    /**
     * Per-run formatting hint for [merge]. When populated, [merge] uses a
     * compact technical format instead of the old "Объединённый прогресс по
     * N workflow…" wall of text:
     *   single:   "#42 SukiSU SUSFS 6.6.89-android15-2025-06"
     *   multiple: "#42 SukiSU SUSFS 6.6.89-… · Manager Dev"
     *
     * Sourced from BuildQueueItem.config in the ViewModel — runs without a
     * descriptor fall back to the older "#N {step.name}" rendering.
     */
    data class RunDescriptor(
        val isManager: Boolean = false,
        val managerIsDev: Boolean = false,
        val ksuVariant: String = "",
        val susfs: Boolean = false,
        val kernelLabel: String = ""
    )

    fun from(run: WorkflowRun, jobs: List<WorkflowJob>): BuildProgress {
        val steps = jobs.flatMapIndexed { jobIndex, job ->
            val jobSteps = job.steps.orEmpty()
            val jobName = translateWorkflowStepName(job.name)
            if (jobSteps.isEmpty()) {
                listOf(
                    BuildStepProgress(
                        name = jobName,
                        status = job.status ?: run.status,
                        conclusion = job.conclusion,
                        index = jobIndex + 1
                    )
                )
            } else {
                jobSteps.sortedBy { it.number }.map { step ->
                    BuildStepProgress(
                        name = "$jobName / ${translateWorkflowStepName(step.name)}",
                        status = step.status ?: job.status ?: run.status,
                        conclusion = step.conclusion,
                        index = step.number
                    )
                }
            }
        }

        if (steps.isEmpty()) {
            return when (run.status) {
                "completed" -> BuildProgress(
                    percent = 100,
                    currentStep = if (run.conclusion == "success") tr(R.string.bp_all_steps_done) else tr(R.string.bp_build_finished),
                    completedSteps = 1,
                    totalSteps = 1
                )
                "in_progress" -> BuildProgress(percent = 5, currentStep = tr(R.string.bp_waiting_steps))
                else -> BuildProgress(percent = 0, currentStep = tr(R.string.bp_build_queued))
            }
        }

        val total = steps.size
        val completed = steps.count { it.status == "completed" || it.conclusion != null }
        val active = steps.firstOrNull { it.status == "in_progress" }
        val next = steps.firstOrNull { it.status != "completed" && it.conclusion == null }
        val current = active ?: next ?: steps.last()
        val percent = when (run.status) {
            "completed" -> 100
            "queued", "waiting", "requested", "pending" -> 0
            else -> ((completed * 100f) / total).toInt().coerceIn(1, 99)
        }

        return BuildProgress(
            percent = percent,
            currentStep = current.name,
            completedSteps = completed,
            totalSteps = total,
            steps = steps
        )
    }

    fun defaultFor(run: WorkflowRun): BuildProgress = when (run.status) {
        "completed" -> BuildProgress(
            percent = 100,
            currentStep = if (run.conclusion == "success") tr(R.string.bp_all_steps_done) else tr(R.string.bp_build_finished),
            completedSteps = 1,
            totalSteps = 1
        )
        "in_progress" -> BuildProgress(
            percent = 5,
            currentStep = tr(R.string.bp_run_waiting_steps, runDisplayLabel(run)),
            completedSteps = 0,
            totalSteps = 1
        )
        "queued", "waiting", "requested", "pending" -> BuildProgress(
            percent = 0,
            currentStep = tr(R.string.bp_run_queued, runDisplayLabel(run)),
            completedSteps = 0,
            totalSteps = 1
        )
        else -> BuildProgress(
            percent = 0,
            currentStep = tr(R.string.bp_run_waiting_sync, runDisplayLabel(run)),
            completedSteps = 0,
            totalSteps = 1
        )
    }

    fun merge(
        runs: List<WorkflowRun>,
        progressByRunId: Map<Long, BuildProgress>,
        descriptors: Map<Long, RunDescriptor> = emptyMap()
    ): BuildProgress {
        val activeRuns = runs
            .filter { it.status in ACTIVE_RUN_STATUSES }
            .distinctBy { it.id }
            .sortedByDescending { it.id }
        if (activeRuns.isEmpty()) return BuildProgress()

        val pairs = activeRuns.map { run -> run to (progressByRunId[run.id] ?: defaultFor(run)) }
        val percent = (pairs.sumOf { it.second.percent.coerceIn(0, 100) } / pairs.size.toFloat())
            .roundToInt()
            .coerceIn(0, 99)
        val totalSteps = pairs.sumOf { (_, progress) -> progress.totalSteps.takeIf { it > 0 } ?: 1 }
        val completedSteps = pairs.sumOf { (_, progress) ->
            if (progress.totalSteps > 0) {
                progress.completedSteps.coerceIn(0, progress.totalSteps)
            } else if (progress.percent >= 100) {
                1
            } else {
                0
            }
        }
        // Both branches use the compact "#65 SukiSU SUSFS 6.6.89-…"-style
        // chip output. The descriptor map is normally populated from the VM
        // (buildQueue → KernelBuildConfig); when empty (e.g. notification
        // service in a process that lacks queue context) the helper falls
        // back to "#N {step.name}" per run — still without the old
        // "Объединённый прогресс по N workflow" prefix.
        val currentStep = buildCompactMergedStep(activeRuns, pairs.toMap(), descriptors)
        val steps = pairs.flatMap { (run, progress) ->
            progress.steps.map { step ->
                step.copy(name = "${runDisplayLabel(run)} ${step.name}")
            }
        }

        return BuildProgress(
            percent = percent,
            currentStep = currentStep,
            completedSteps = completedSteps,
            totalSteps = totalSteps,
            steps = steps
        )
    }

    /**
     * "#42 SukiSU SUSFS 6.6.89-android15-2025-06" for one run,
     * "2 Workflows · #42 SukiSU … · Manager Dev" for many.
     */
    private fun buildCompactMergedStep(
        activeRuns: List<WorkflowRun>,
        progressMap: Map<WorkflowRun, BuildProgress>,
        descriptors: Map<Long, RunDescriptor>
    ): String {
        val entries = activeRuns.map { run ->
            val desc = descriptors[run.id]
            when {
                desc?.isManager == true -> buildString {
                    append(runDisplayLabel(run))
                    append(' ')
                    append(if (desc.managerIsDev) "Manager Dev" else "Manager")
                }
                desc != null && desc.kernelLabel.isNotBlank() -> buildString {
                    append(runDisplayLabel(run))
                    if (desc.ksuVariant.isNotBlank()) append(' ').append(desc.ksuVariant)
                    if (desc.susfs) append(" SUSFS")
                    append(' ').append(desc.kernelLabel)
                }
                else -> {
                    // No descriptor — fall back to the step name but stripped of
                    // the noisy "{job}/{step}" prefixes that the old format used.
                    val progress = progressMap[run] ?: defaultFor(run)
                    "${runDisplayLabel(run)} ${progress.currentStep}"
                }
            }
        }
        // Keep the same middle-dot separator the card already uses between
        // percent and the first workflow label so multi-run rows read
        // consistently for both kernel and manager sections.
        return entries.joinToString(" · ")
    }

    private fun runDisplayLabel(run: WorkflowRun): String =
        if (run.runNumber > 0) "#${run.runNumber}" else "#${run.id}"

    private val ACTIVE_RUN_STATUSES = setOf("queued", "waiting", "requested", "pending", "in_progress")

    /** Upstream YAML step names are Chinese; [WorkflowStepI18n] maps them for non-zh UI. */
    private fun translateWorkflowStepName(name: String): String = WorkflowStepI18n.translate(name)
}
