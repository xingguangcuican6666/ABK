package com.abk.kernel.utils

import com.abk.kernel.data.model.BuildProgress
import com.abk.kernel.data.model.BuildStepProgress
import com.abk.kernel.data.model.WorkflowJob
import com.abk.kernel.data.model.WorkflowRun

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
            "queued", "waiting", "requested" -> 0
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
}
