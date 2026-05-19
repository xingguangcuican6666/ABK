package com.abk.kernel.utils
import com.abk.kernel.tr
import com.abk.kernel.R

import com.abk.kernel.data.model.BuildProgress
import com.abk.kernel.data.model.BuildStepProgress
import com.abk.kernel.data.model.WorkflowJob
import com.abk.kernel.data.model.WorkflowRun
import kotlin.math.roundToInt

object BuildProgressUtils {

    fun from(run: WorkflowRun, jobs: List<WorkflowJob>): BuildProgress {
        val steps = jobs.flatMapIndexed { jobIndex, job ->
            val jobSteps = job.steps.orEmpty()
            if (jobSteps.isEmpty()) {
                listOf(
                    BuildStepProgress(
                        name = job.name,
                        status = job.status ?: run.status,
                        conclusion = job.conclusion,
                        index = jobIndex + 1
                    )
                )
            } else {
                jobSteps.sortedBy { it.number }.map { step ->
                    BuildStepProgress(
                        name = "${job.name} / ${step.name}",
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
        progressByRunId: Map<Long, BuildProgress>
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
        val runningCount = activeRuns.count { it.status == "in_progress" }
        val queuedCount = activeRuns.size - runningCount
        val detail = pairs
            .filter { (run, _) -> run.status == "in_progress" }
            .ifEmpty { pairs }
            .take(2)
            .joinToString("；") { (run, progress) ->
                "${runDisplayLabel(run)} ${progress.currentStep}"
            }
        val currentStep = buildString {
            append(tr(R.string.bp_merge_progress, activeRuns.size))
            if (runningCount > 0) append(tr(R.string.bp_merge_running, runningCount))
            if (queuedCount > 0) append(tr(R.string.bp_merge_queued, queuedCount))
            if (detail.isNotBlank()) append(" · ").append(detail)
        }
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

    private fun runDisplayLabel(run: WorkflowRun): String =
        if (run.runNumber > 0) "#${run.runNumber}" else "#${run.id}"

    private val ACTIVE_RUN_STATUSES = setOf("queued", "waiting", "requested", "pending", "in_progress")
}
