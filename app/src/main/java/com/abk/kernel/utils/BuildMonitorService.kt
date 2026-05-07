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
    private var monitorJob: Job? = null

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
            ACTION_STOP -> stopSelf()
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
        monitorJob?.cancel()
        monitorJob = scope.launch {
            val prefs = PreferencesRepository(applicationContext)
            val token = prefs.accessToken.first()
            if (token.isNullOrBlank()) {
                stopSelf()
                return@launch
            }
            val notifyBuild = prefs.notifyBuild.first()
            val github = GitHubRepository()
            github.updateToken(token)

            if (notifyBuild) NotificationUtils.notifyBuildRunning(applicationContext)

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
                    if (notifyBuild && run.status != "completed") {
                        NotificationUtils.notifyBuildRunning(
                            applicationContext,
                            progress.percent,
                            progress.currentStep
                        )
                    }
                    when (run.status) {
                        "completed" -> {
                            val success = run.conclusion == "success"
                            if (notifyBuild) NotificationUtils.notifyBuildDone(applicationContext, success)
                            stopSelf()
                            break
                        }
                        "queued", "waiting", "in_progress", "requested" -> {
                            delay(30_000)
                        }
                        else -> {
                            stopSelf()
                            break
                        }
                    }
                } else {
                    delay(30_000)
                }
            }
        }
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
        monitorJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }
}
