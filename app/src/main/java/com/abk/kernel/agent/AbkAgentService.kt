package com.abk.kernel.agent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.abk.kernel.MainActivity

class AbkAgentService : Service() {
    private var server: AbkAgentServer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        return when (action) {
            ACTION_STOP -> {
                stopSelf()
                START_NOT_STICKY
            }
            else -> {
                val port = intent?.getIntExtra(EXTRA_PORT, DEFAULT_PORT)?.takeIf { it > 0 } ?: DEFAULT_PORT
                val host = intent?.getStringExtra(EXTRA_HOST)?.ifBlank { DEFAULT_HOST } ?: DEFAULT_HOST
                startForegroundInternal(port)
                startOrRestartServer(host = host, port = port)
                START_STICKY
            }
        }
    }

    override fun onDestroy() {
        server?.stop()
        server = null
        super.onDestroy()
    }

    private fun startOrRestartServer(host: String, port: Int) {
        val current = server
        if (current != null && current.listeningPort == port) return
        current?.stop()
        server = AbkAgentServer(applicationContext, host, port).apply {
            start(SOCKET_READ_TIMEOUT, false)
        }
    }

    private fun startForegroundInternal(port: Int) {
        ensureNotificationChannel()
        val notification = buildNotification(port)
        val foregroundType =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, foregroundType)
    }

    private fun buildNotification(port: Int): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = android.app.PendingIntent.getActivity(
            this,
            0,
            intent,
            android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("ABK Agent running")
            .setContentText("Listening on 127.0.0.1:$port")
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "ABK Agent",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Desktop bridge for ABK runtime control"
            },
        )
    }

    companion object {
        const val ACTION_START = "com.abk.kernel.agent.START"
        const val ACTION_STOP = "com.abk.kernel.agent.STOP"
        const val EXTRA_HOST = "host"
        const val EXTRA_PORT = "port"
        const val DEFAULT_HOST = "127.0.0.1"
        const val DEFAULT_PORT = 48765

        private const val CHANNEL_ID = "abk_agent"
        private const val NOTIFICATION_ID = 1203
        private const val SOCKET_READ_TIMEOUT = 5_000
    }
}
