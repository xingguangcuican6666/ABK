package com.abk.kernel.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import com.abk.kernel.R
import java.io.File

internal fun launchAppUpdateInstaller(context: Context, apkPath: String) {
    val apkFile = File(apkPath)
    if (!apkFile.isFile) {
        Toast.makeText(context, context.getString(R.string.ru_apk_not_found, apkPath), Toast.LENGTH_SHORT).show()
        return
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
        !context.packageManager.canRequestPackageInstalls()
    ) {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
            .onFailure {
                Toast.makeText(context, context.getString(R.string.settings_app_update_install_permission), Toast.LENGTH_LONG).show()
            }
        return
    }
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        apkFile
    )
    val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
        data = uri
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
        putExtra(Intent.EXTRA_RETURN_RESULT, false)
    }
    runCatching { context.startActivity(intent) }
        .onFailure {
            Toast.makeText(context, context.getString(R.string.settings_app_update_install_failed), Toast.LENGTH_LONG).show()
        }
}
