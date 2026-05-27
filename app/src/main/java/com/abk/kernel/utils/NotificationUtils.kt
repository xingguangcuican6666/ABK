package com.abk.kernel.utils

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.net.Uri
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.abk.kernel.MainActivity
import com.abk.kernel.R
import org.json.JSONObject

object NotificationUtils {

    const val CHANNEL_BUILD = "build_status"
    const val CHANNEL_DOWNLOAD = "download_status"
    const val NOTIF_ID_BUILD = 1001
    const val NOTIF_ID_DOWNLOAD = 1002

    private const val EXTRA_MIUI_FOCUS_PARAM = "miui.focus.param"
    private const val EXTRA_MIUI_FOCUS_PICS = "miui.focus.pics"
    private const val MIUI_FOCUS_BUILD_ICON = "miui.focus.pic_abk_build"
    private const val MIUI_FOCUS_PROTOCOL_SETTING = "notification_focus_protocol"
    private const val MIUI_FOCUS_PROTOCOL_OS3 = 3
    private const val MIUI_ISLAND_PROPERTY = "persist.sys.feature.island"
    private const val MIUI_FOCUS_PERMISSION_METHOD = "canShowFocus"
    private const val MIUI_FOCUS_PERMISSION_PACKAGE = "package"
    private const val MIUI_FOCUS_PERMISSION_RESULT = "canShowFocus"
    private const val MIUI_BUILD_BUSINESS = "abk_build"
    private const val COLOR_BUILD_RUNNING = "#006EFF"
    private const val COLOR_BUILD_SUCCESS = "#1A7F37"
    private const val COLOR_BUILD_FAILED = "#D93025"
    private const val COLOR_PROGRESS_UNREACHED = "#1A000000"

    private val MIUI_FOCUS_PERMISSION_URI = Uri.parse("content://miui.statusbar.notification.public")
    private val whitespaceRegex = Regex("\\s+")
    private var lastBuildNotificationSignature: String? = null
    private var lastBuildProgressPercent: Int? = null

    fun createChannels(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_BUILD,
                context.getString(R.string.notif_channel_build),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = context.getString(R.string.notif_channel_build_desc) }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_DOWNLOAD,
                context.getString(R.string.notif_channel_download),
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = context.getString(R.string.notif_channel_download_desc) }
        )
    }

    fun notifyBuildRunning(
        context: Context,
        progress: Int? = null,
        currentStep: String? = null
    ) {
        val normalizedProgress = progress?.coerceIn(0, 100)
        val normalizedStep = currentStep.orEmpty().trim()
        val signature = buildRunningNotificationSignature(normalizedProgress, normalizedStep)
        if (signature == lastBuildNotificationSignature) return
        post(
            context,
            NOTIF_ID_BUILD,
            buildBuildRunningNotification(
                context = context,
                progress = normalizedProgress,
                currentStep = normalizedStep,
                rememberAsShown = true
            )
        )
    }

    fun buildBuildRunningNotification(
        context: Context,
        progress: Int? = null,
        currentStep: String? = null,
        rememberAsShown: Boolean = false
    ): android.app.Notification {
        val normalizedProgress = progress?.coerceIn(0, 100)
        val normalizedStep = currentStep.orEmpty().trim()
        if (rememberAsShown) {
            lastBuildNotificationSignature = buildRunningNotificationSignature(normalizedProgress, normalizedStep)
            lastBuildProgressPercent = normalizedProgress ?: 0
        }
        val intent = Intent(context, MainActivity::class.java)
        val pi = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        val text = when {
            normalizedProgress != null && normalizedStep.isNotBlank() -> "$normalizedProgress% · $normalizedStep"
            normalizedStep.isNotBlank() -> normalizedStep
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
            builder.setProgress(100, normalizedProgress ?: 0, false)
        } else {
            builder.setProgress(100, 0, true)
        }
        return builder.build().apply {
            attachMiuiBuildIsland(
                context = context,
                content = BuildIslandContent(
                    title = context.getString(R.string.notif_build_running),
                    content = text,
                    progress = normalizedProgress ?: 0,
                    color = COLOR_BUILD_RUNNING
                )
            )
        }
    }

    fun notifyBuildDone(context: Context, success: Boolean) {
        val intent = Intent(context, MainActivity::class.java)
        val pi = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        val title = if (success)
            context.getString(R.string.notif_build_done)
        else
            context.getString(R.string.notif_build_failed)
        val progress = if (success) 100 else lastBuildProgressPercent ?: 0
        val notif = NotificationCompat.Builder(context, CHANNEL_BUILD)
            .setSmallIcon(
                if (success) android.R.drawable.ic_dialog_info
                else android.R.drawable.ic_dialog_alert
            )
            .setContentTitle(title)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
            .apply {
                attachMiuiBuildIsland(
                    context = context,
                    content = BuildIslandContent(
                        title = title,
                        content = if (success) {
                            context.getString(R.string.bp_all_steps_done)
                        } else {
                            context.getString(R.string.build_failed)
                        },
                        progress = progress,
                        color = if (success) COLOR_BUILD_SUCCESS else COLOR_BUILD_FAILED
                    )
                )
            }
        lastBuildNotificationSignature = null
        lastBuildProgressPercent = null
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

    fun cancelDownloadNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIF_ID_DOWNLOAD)
    }

    fun cancelBuildNotification(context: Context) {
        lastBuildNotificationSignature = null
        lastBuildProgressPercent = null
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

    private fun android.app.Notification.attachMiuiBuildIsland(
        context: Context,
        content: BuildIslandContent
    ) {
        if (!canUseMiuiSuperIsland(context)) return
        extras.putString(EXTRA_MIUI_FOCUS_PARAM, buildMiuiBuildIslandParams(content))
        extras.putBundle(
            EXTRA_MIUI_FOCUS_PICS,
            Bundle().apply {
                putParcelable(MIUI_FOCUS_BUILD_ICON, Icon.createWithResource(context, R.mipmap.ic_launcher))
            }
        )
    }

    private fun canUseMiuiSuperIsland(context: Context): Boolean {
        return isLikelyXiaomiDevice() &&
            isSupportIslandSystemProperty() &&
            isOs3FocusProtocol(context) &&
            hasMiuiFocusPermission(context)
    }

    private fun isLikelyXiaomiDevice(): Boolean {
        return listOf(Build.MANUFACTURER, Build.BRAND).any { value ->
            val marker = value.orEmpty().lowercase()
            marker.contains("xiaomi") || marker.contains("redmi") || marker.contains("poco")
        }
    }

    private fun isSupportIslandSystemProperty(): Boolean {
        return runCatching {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getDeclaredMethod("getBoolean", String::class.java, java.lang.Boolean.TYPE)
            method.invoke(null, MIUI_ISLAND_PROPERTY, false) as? Boolean ?: false
        }.getOrDefault(false)
    }

    private fun isOs3FocusProtocol(context: Context): Boolean {
        return runCatching {
            Settings.System.getInt(context.contentResolver, MIUI_FOCUS_PROTOCOL_SETTING, 0) == MIUI_FOCUS_PROTOCOL_OS3
        }.getOrDefault(false)
    }

    private fun hasMiuiFocusPermission(context: Context): Boolean {
        return runCatching {
            val extras = Bundle().apply {
                putString(MIUI_FOCUS_PERMISSION_PACKAGE, context.packageName)
            }
            context.contentResolver
                .call(MIUI_FOCUS_PERMISSION_URI, MIUI_FOCUS_PERMISSION_METHOD, null, extras)
                ?.getBoolean(MIUI_FOCUS_PERMISSION_RESULT, false) == true
        }.getOrDefault(false)
    }

    private fun buildMiuiBuildIslandParams(content: BuildIslandContent): String {
        val title = content.title.cleanIslandText(24)
        val body = content.content.cleanIslandText(48)
        val progress = content.progress.coerceIn(0, 100)
        val progressLabel = "$progress%"
        val baseInfo = JSONObject()
            .put("type", 1)
            .put("title", title)
            .put("content", body)
            .put("colorTitle", content.color)
        val progressInfo = JSONObject()
            .put("progress", progress)
            .put("colorProgress", content.color)
            .put("colorProgressEnd", content.color)
        val iconInfo = JSONObject()
            .put("type", 1)
            .put("pic", MIUI_FOCUS_BUILD_ICON)
            .put("picDark", MIUI_FOCUS_BUILD_ICON)
        val percentTextInfo = JSONObject()
            .put("title", progressLabel)
            .put("narrowFont", true)
            .put("showHighlightColor", false)
        val bigIslandIconInfo = JSONObject()
            .put("type", 1)
            .put("picInfo", iconInfo)
        val progressTextInfo = JSONObject()
            .put(
                "progressInfo",
                JSONObject()
                    .put("progress", progress)
                    .put("colorReach", content.color)
                    .put("colorUnReach", COLOR_PROGRESS_UNREACHED)
            )
            .put("textInfo", percentTextInfo)

        return JSONObject()
            .put(
                "param_v2",
                JSONObject()
                    .put("protocol", 1)
                    .put("business", MIUI_BUILD_BUSINESS)
                    .put("islandFirstFloat", false)
                    .put("enableFloat", false)
                    .put("updatable", true)
                    .put("filterWhenNoPermission", false)
                    .put("ticker", "$progressLabel $title".cleanIslandText(32))
                    .put("aodTitle", title)
                    .put("baseInfo", baseInfo)
                    .put(
                        "param_island",
                        JSONObject()
                            .put("islandProperty", 1)
                            .put("highlightColor", content.color)
                            .put(
                                "bigIslandArea",
                                JSONObject()
                                    .put("imageTextInfoLeft", bigIslandIconInfo)
                                    .put("progressTextInfo", progressTextInfo)
                            )
                            .put(
                                "smallIslandArea",
                                JSONObject()
                                    .put("picInfo", iconInfo)
                                    .put("textInfo", percentTextInfo)
                            )
                            .put("shareData", JSONObject().put("title", title))
                    )
                    .put("progressInfo", progressInfo)
                    .put(
                        "hintInfo",
                        JSONObject()
                            .put("type", 1)
                            .put("title", progressLabel)
                    )
            )
            .toString()
    }

    private fun String.cleanIslandText(maxLength: Int): String {
        val cleaned = trim().replace(whitespaceRegex, " ")
        if (cleaned.length <= maxLength) return cleaned
        return cleaned.take((maxLength - 3).coerceAtLeast(1)).trimEnd() + "..."
    }

    private fun buildRunningNotificationSignature(progress: Int?, currentStep: String): String {
        return "running|${progress ?: "indef"}|$currentStep"
    }

    private data class BuildIslandContent(
        val title: String,
        val content: String,
        val progress: Int,
        val color: String
    )
}
