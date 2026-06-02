package com.abk.kernel.utils

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.abk.kernel.data.model.BuildProgress
import com.abk.kernel.data.model.WorkflowJob
import com.abk.kernel.data.model.WorkflowRun
import com.abk.kernel.data.model.isKernelBuild
import com.abk.kernel.data.model.isManagerBuild
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
    private val completedOutcomes = mutableMapOf<Long, BuildSessionOutcome>()
    private val completedRunKinds = mutableMapOf<Long, NotificationUtils.BuildKind>()

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
        return NotificationUtils.buildBuildRunningNotification(this, rememberAsShown = true)
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

            try {
                while (coroutineContext.isActive) {
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
                                merged?.currentStep ?: progress.currentStep,
                                kind = mergedActiveKind()
                            )
                        }
                        when (run.status) {
                            "completed" -> {
                                val outcome = when (run.conclusion) {
                                    "success" -> BuildSessionOutcome.Success
                                    "cancelled" -> BuildSessionOutcome.Cancelled
                                    else -> BuildSessionOutcome.Failure
                                }
                                val completedKind = kindForRun(run)
                                val finish = finishMonitoring(
                                    runId,
                                    outcome = outcome,
                                    kind = completedKind
                                )
                                if (notifyBuild) {
                                    if (finish.shouldStop) {
                                        publishSessionDoneNotification()
                                    } else {
                                        publishMergedRunningNotification()
                                    }
                                }
                                break
                            }
                            // Pre-start states change in seconds: poll fast so
                            // the UI sees "queued → in_progress" promptly and
                            // the runner-pickup spinner stops being a lie.
                            "queued", "waiting", "requested", "pending" -> {
                                delay(10_000)
                            }
                            // Long-running compile steps: slow polling is fine
                            // and keeps us well under the GitHub rate limit
                            // (~120 req/h per monitor at 30s × 2 endpoints).
                            "in_progress" -> {
                                delay(30_000)
                            }
                            else -> {
                                val failedKind = kindForRun(run)
                                val finish = finishMonitoring(
                                    runId,
                                    outcome = BuildSessionOutcome.Failure,
                                    kind = failedKind
                                )
                                if (notifyBuild) {
                                    if (finish.shouldStop) {
                                        publishSessionDoneNotification()
                                    } else {
                                        publishMergedRunningNotification()
                                    }
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

    /**
     * Classify currently-monitored active runs so the notification can pick
     * the right title and decide whether to attach the HyperOS island.
     */
    private fun mergedActiveKind(): NotificationUtils.BuildKind = synchronized(monitorLock) {
        val activeRuns = runSnapshots.values.filter { it.status in ACTIVE_MONITOR_STATUSES }
        val kernels = activeRuns.count { it.isKernelBuild() }
        val managers = activeRuns.count { it.isManagerBuild() }
        when {
            kernels >= 1 && managers >= 1 -> NotificationUtils.BuildKind.Mixed
            kernels > 1 -> NotificationUtils.BuildKind.MultipleKernels
            kernels == 1 -> NotificationUtils.BuildKind.Kernel
            managers >= 1 -> NotificationUtils.BuildKind.ManagerOnly
            else -> NotificationUtils.BuildKind.Unknown
        }
    }

    private fun publishMergedRunningNotification() {
        val merged = mergedActiveProgress() ?: return
        NotificationUtils.notifyBuildRunning(
            applicationContext,
            merged.percent,
            merged.currentStep,
            kind = mergedActiveKind()
        )
    }

    private fun kindForRun(run: WorkflowRun): NotificationUtils.BuildKind = when {
        run.isManagerBuild() -> NotificationUtils.BuildKind.ManagerOnly
        run.isKernelBuild() -> NotificationUtils.BuildKind.Kernel
        else -> NotificationUtils.BuildKind.Unknown
    }

    private fun finishMonitoring(
        runId: Long,
        outcome: BuildSessionOutcome? = null,
        kind: NotificationUtils.BuildKind? = null,
    ): MonitorFinish {
        val finish = synchronized(monitorLock) {
            monitorJobs.remove(runId)
            runSnapshots.remove(runId)
            progressSnapshots.remove(runId)
            if (outcome != null) {
                completedOutcomes[runId] = outcome
                kind?.let { completedRunKinds[runId] = it }
            }
            MonitorFinish(shouldStop = monitorJobs.isEmpty())
        }
        if (finish.shouldStop) stopServiceAndForeground()
        return finish
    }

    private fun publishSessionDoneNotification() {
        val action = synchronized(monitorLock) {
            resolveBuildSessionNotificationAction(completedOutcomes.values)
        } ?: return
        val kind = synchronized(monitorLock) { resolveSessionDoneKind() }
        when (action) {
            BuildSessionNotificationAction.NotifySuccess ->
                NotificationUtils.notifyBuildDone(applicationContext, success = true, kind = kind)
            BuildSessionNotificationAction.NotifyFailure ->
                NotificationUtils.notifyBuildDone(applicationContext, success = false, kind = kind)
            BuildSessionNotificationAction.CancelNotification ->
                NotificationUtils.cancelBuildNotification(applicationContext)
        }
    }

    private fun resolveSessionDoneKind(): NotificationUtils.BuildKind {
        val kinds: Set<NotificationUtils.BuildKind> =
            synchronized(monitorLock) { completedRunKinds.values.toSet() }
        return when {
            NotificationUtils.BuildKind.Kernel in kinds &&
                NotificationUtils.BuildKind.ManagerOnly in kinds ->
                NotificationUtils.BuildKind.Mixed
            kinds.size == 1 -> kinds.single()
            NotificationUtils.BuildKind.Kernel in kinds -> NotificationUtils.BuildKind.Kernel
            NotificationUtils.BuildKind.ManagerOnly in kinds -> NotificationUtils.BuildKind.ManagerOnly
            else -> NotificationUtils.BuildKind.Unknown
        }
    }

    private fun stopAllMonitoring() {
        val jobs = synchronized(monitorLock) {
            val current = monitorJobs.values.toList()
            monitorJobs.clear()
            runSnapshots.clear()
            progressSnapshots.clear()
            completedOutcomes.clear()
            completedRunKinds.clear()
            current
        }
        jobs.forEach { it.cancel() }
        stopServiceAndForeground()
    }

    private fun removeForegroundNotification() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun stopServiceAndForeground() {
        removeForegroundNotification()
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
            completedOutcomes.clear()
            completedRunKinds.clear()
        }
        scope.cancel()
        removeForegroundNotification()
        super.onDestroy()
    }

    private val ACTIVE_MONITOR_STATUSES = setOf("queued", "waiting", "requested", "pending", "in_progress")

    private data class MonitorFinish(val shouldStop: Boolean)
}
