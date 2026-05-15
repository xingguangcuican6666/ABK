package com.abk.kernel.utils

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
                    currentStep = if (run.conclusion == "success") "全部步骤完成" else "构建已结束",
                    completedSteps = 1,
                    totalSteps = 1
                )
                "in_progress" -> BuildProgress(percent = 5, currentStep = "等待 GitHub 返回步骤")
                else -> BuildProgress(percent = 0, currentStep = "构建已排队")
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
            currentStep = if (run.conclusion == "success") "全部步骤完成" else "构建已结束",
            completedSteps = 1,
            totalSteps = 1
        )
        "in_progress" -> BuildProgress(
            percent = 5,
            currentStep = "${runDisplayLabel(run)} 等待 GitHub 返回步骤",
            completedSteps = 0,
            totalSteps = 1
        )
        "queued", "waiting", "requested", "pending" -> BuildProgress(
            percent = 0,
            currentStep = "${runDisplayLabel(run)} 已排队",
            completedSteps = 0,
            totalSteps = 1
        )
        else -> BuildProgress(
            percent = 0,
            currentStep = "${runDisplayLabel(run)} 等待状态同步",
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
            append("${activeRuns.size} 个工作流合并进度")
            if (runningCount > 0) append("，$runningCount 个运行中")
            if (queuedCount > 0) append("，$queuedCount 个排队中")
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
