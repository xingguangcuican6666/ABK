package com.abk.kernel.utils

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.abk.kernel.MainActivity
import com.abk.kernel.R

object NotificationUtils {

    const val CHANNEL_BUILD = "build_status"
    const val CHANNEL_DOWNLOAD = "download_status"
    const val NOTIF_ID_BUILD = 1001
    const val NOTIF_ID_DOWNLOAD = 1002

    fun createChannels(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_BUILD,
                context.getString(R.string.notif_channel_build),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "内核构建状态通知" }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_DOWNLOAD,
                context.getString(R.string.notif_channel_download),
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "构建产物下载进度" }
        )
    }

    fun notifyBuildRunning(
        context: Context,
        progress: Int? = null,
        currentStep: String? = null
    ) {
        val intent = Intent(context, MainActivity::class.java)
        val pi = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        val text = when {
            progress != null && !currentStep.isNullOrBlank() -> "$progress% · $currentStep"
            !currentStep.isNullOrBlank() -> currentStep
            else -> context.getString(R.string.build_running)
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_BUILD)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle(context.getString(R.string.notif_build_running))
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(pi)
            .setOnlyAlertOnce(true)
        if (progress != null) {
            builder.setProgress(100, progress.coerceIn(0, 100), false)
        } else {
            builder.setProgress(100, 0, true)
        }
        val notif = builder.build()
        post(context, NOTIF_ID_BUILD, notif)
    }

    fun notifyBuildDone(context: Context, success: Boolean) {
        val intent = Intent(context, MainActivity::class.java)
        val pi = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        val title = if (success)
            context.getString(R.string.notif_build_done)
        else
            context.getString(R.string.notif_build_failed)
        val notif = NotificationCompat.Builder(context, CHANNEL_BUILD)
            .setSmallIcon(
                if (success) android.R.drawable.ic_dialog_info
                else android.R.drawable.ic_dialog_alert
            )
            .setContentTitle(title)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        post(context, NOTIF_ID_BUILD, notif)
    }

    fun notifyDownloadProgress(context: Context, progress: Int, fileName: String) {
        val notif = NotificationCompat.Builder(context, CHANNEL_DOWNLOAD)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(context.getString(R.string.notif_downloading))
            .setContentText(fileName)
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .build()
        post(context, NOTIF_ID_DOWNLOAD, notif)
    }

    fun notifyDownloadDone(context: Context, fileName: String) {
        val notif = NotificationCompat.Builder(context, CHANNEL_DOWNLOAD)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(context.getString(R.string.notif_download_done))
            .setContentText(fileName)
            .setAutoCancel(true)
            .build()
        post(context, NOTIF_ID_DOWNLOAD, notif)
    }

    fun cancelBuildNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIF_ID_BUILD)
    }

    @SuppressLint("MissingPermission")
    private fun post(context: Context, id: Int, notification: android.app.Notification) {
        if (!canPostNotifications(context)) return
        runCatching {
            NotificationManagerCompat.from(context).notify(id, notification)
        }
    }

    private fun canPostNotifications(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }
}
