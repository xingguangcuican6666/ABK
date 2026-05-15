package com.abk.kernel.utils

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.abk.kernel.data.model.BuildProgress
import com.abk.kernel.data.model.WorkflowJob
import com.abk.kernel.data.model.WorkflowRun
import com.abk.kernel.data.repository.GitHubRepository
import com.abk.kernel.data.repository.PreferencesRepository
import com.abk.kernel.data.repository.Result
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class BuildMonitorService : Service() {

    companion object {
        const val ACTION_START = "com.abk.kernel.BUILD_MONITOR_START"
        const val ACTION_STOP = "com.abk.kernel.BUILD_MONITOR_STOP"
        const val EXTRA_RUN_ID = "run_id"
        const val EXTRA_OWNER = "owner"
        const val EXTRA_REPO = "repo"

        // Broadcast action for UI updates
        const val BROADCAST_STATUS = "com.abk.kernel.BUILD_STATUS"
        const val EXTRA_STATUS = "status"
        const val EXTRA_RUN = "run_json"
        const val EXTRA_PROGRESS = "progress_json"

        fun startMonitoring(context: Context, owner: String, repo: String, runId: Long) {
            val intent = Intent(context, BuildMonitorService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_OWNER, owner)
                putExtra(EXTRA_REPO, repo)
                putExtra(EXTRA_RUN_ID, runId)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopMonitoring(context: Context) {
            context.startService(Intent(context, BuildMonitorService::class.java).apply {
                action = ACTION_STOP
            })
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val monitorLock = Any()
    private val monitorJobs = mutableMapOf<Long, Job>()
    private val runSnapshots = mutableMapOf<Long, WorkflowRun>()
    private val progressSnapshots = mutableMapOf<Long, BuildProgress>()
    private val completedRunSuccess = mutableMapOf<Long, Boolean>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val runId = intent.getLongExtra(EXTRA_RUN_ID, -1L)
                val owner = intent.getStringExtra(EXTRA_OWNER) ?: return START_NOT_STICKY
                val repo = intent.getStringExtra(EXTRA_REPO) ?: return START_NOT_STICKY
                if (runId <= 0L) return START_NOT_STICKY
                startForeground(NotificationUtils.NOTIF_ID_BUILD, buildForegroundNotification())
                startMonitoring(owner, repo, runId)
            }
            ACTION_STOP -> stopAllMonitoring()
        }
        return START_NOT_STICKY
    }

    private fun buildForegroundNotification(): Notification {
        NotificationUtils.createChannels(this)
        return NotificationCompat.Builder(this, NotificationUtils.CHANNEL_BUILD)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle(getString(com.abk.kernel.R.string.notif_build_running))
            .setOngoing(true)
            .build()
    }

    private fun startMonitoring(owner: String, repo: String, runId: Long) {
        synchronized(monitorLock) {
            if (monitorJobs[runId]?.isActive == true) return
        }
        val job = scope.launch(start = CoroutineStart.LAZY) {
            val prefs = PreferencesRepository(applicationContext)
            val token = prefs.accessToken.first()
            if (token.isNullOrBlank()) {
                return@launch
            }
            val notifyBuild = prefs.notifyBuild.first()
            val github = GitHubRepository()
            github.updateToken(token)

            if (notifyBuild) NotificationUtils.notifyBuildRunning(applicationContext)

            try {
                while (isActive) {
                    val result = github.getWorkflowRun(owner, repo, runId)
                    if (result is Result.Success) {
                        val run = result.data
                        val jobs: List<WorkflowJob> = when (val jobsResult = github.listRunJobs(owner, repo, runId)) {
                            is Result.Success -> jobsResult.data
                            else -> emptyList()
                        }
                        val progress = BuildProgressUtils.from(run, jobs)
                        broadcastStatus(run, progress)
                        updateSnapshot(run, progress)
                        if (notifyBuild && run.status != "completed") {
                            val merged = mergedActiveProgress()
                            NotificationUtils.notifyBuildRunning(
                                applicationContext,
                                merged?.percent ?: progress.percent,
                                merged?.currentStep ?: progress.currentStep
                            )
                        }
                        when (run.status) {
                            "completed" -> {
                                val success = run.conclusion == "success"
                                val finish = finishMonitoring(runId, success)
                                if (notifyBuild && finish.shouldStop) {
                                    NotificationUtils.notifyBuildDone(
                                        applicationContext,
                                        finish.allSucceeded
                                    )
                                }
                                break
                            }
                            "queued", "waiting", "in_progress", "requested", "pending" -> {
                                delay(30_000)
                            }
                            else -> {
                                val finish = finishMonitoring(runId, success = false)
                                if (notifyBuild && finish.shouldStop) {
                                    NotificationUtils.notifyBuildDone(applicationContext, success = false)
                                }
                                break
                            }
                        }
                    } else {
                        delay(30_000)
                    }
                }
            } finally {
                finishMonitoring(runId)
            }
        }
        synchronized(monitorLock) {
            if (monitorJobs[runId]?.isActive == true) {
                job.cancel()
                return
            }
            monitorJobs[runId] = job
        }
        job.start()
    }

    private fun updateSnapshot(run: WorkflowRun, progress: BuildProgress) {
        synchronized(monitorLock) {
            runSnapshots[run.id] = run
            progressSnapshots[run.id] = progress
        }
    }

    private fun mergedActiveProgress(): BuildProgress? = synchronized(monitorLock) {
        val activeRuns = runSnapshots.values.filter { it.status in ACTIVE_MONITOR_STATUSES }
        if (activeRuns.isEmpty()) null else BuildProgressUtils.merge(activeRuns, progressSnapshots)
    }

    private fun finishMonitoring(runId: Long, success: Boolean? = null): MonitorFinish {
        val finish = synchronized(monitorLock) {
            monitorJobs.remove(runId)
            runSnapshots.remove(runId)
            progressSnapshots.remove(runId)
            success?.let { completedRunSuccess[runId] = it }
            MonitorFinish(
                shouldStop = monitorJobs.isEmpty(),
                allSucceeded = completedRunSuccess.values.all { it }
            )
        }
        if (finish.shouldStop) stopSelf()
        return finish
    }

    private fun stopAllMonitoring() {
        val jobs = synchronized(monitorLock) {
            val current = monitorJobs.values.toList()
            monitorJobs.clear()
            runSnapshots.clear()
            progressSnapshots.clear()
            completedRunSuccess.clear()
            current
        }
        jobs.forEach { it.cancel() }
        stopSelf()
    }

    private fun broadcastStatus(run: WorkflowRun, progress: BuildProgress) {
        val intent = Intent(BROADCAST_STATUS).apply {
            putExtra(EXTRA_STATUS, run.status)
            putExtra(EXTRA_RUN, com.google.gson.Gson().toJson(run))
            putExtra(EXTRA_PROGRESS, com.google.gson.Gson().toJson(progress))
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    override fun onDestroy() {
        synchronized(monitorLock) {
            monitorJobs.values.forEach { it.cancel() }
            monitorJobs.clear()
            runSnapshots.clear()
            progressSnapshots.clear()
            completedRunSuccess.clear()
        }
        scope.cancel()
        super.onDestroy()
    }

    private val ACTIVE_MONITOR_STATUSES = setOf("queued", "waiting", "requested", "pending", "in_progress")

    private data class MonitorFinish(
        val shouldStop: Boolean,
        val allSucceeded: Boolean
    )
}
