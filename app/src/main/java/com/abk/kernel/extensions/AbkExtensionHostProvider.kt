package com.abk.kernel.extensions

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process
import com.abk.kernel.utils.RootUtils

class AbkExtensionHostProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val context = context ?: return errorBundle("context unavailable")
        val extensionId = (arg ?: extras?.getString(ABK_EXTENSION_EXTRA_ID))
            ?.trim()
            .orEmpty()
        if (extensionId.isBlank()) return errorBundle("extension_id missing")

        val callerPackage = callingPackageCompat()
        if (!isCallerAuthorized(context, extensionId, callerPackage)) {
            return errorBundle("unauthorized caller")
        }

        return when (method) {
            METHOD_GET_STATE -> {
                val state = RootUtils.readAbkExtensionState(extensionId)
                Bundle().apply {
                    putBoolean("success", true)
                    putBoolean("exists", state != null)
                    putString("state_json", state)
                }
            }
            METHOD_PUT_STATE -> {
                val stateJson = extras?.getString("state_json").orEmpty()
                if (stateJson.isBlank()) return errorBundle("state_json missing")
                val result = RootUtils.writeAbkExtensionState(extensionId, stateJson)
                result.toBundle()
            }
            METHOD_CLEAR_STATE -> RootUtils.clearAbkExtensionState(extensionId).toBundle()
            METHOD_GET_CONTROL_STATUS -> {
                val result = RootUtils.readAbkControlStatus()
                Bundle().apply {
                    putBoolean("success", result.success)
                    putString("status_json", result.output.joinToString("\n"))
                    if (!result.success) {
                        putString("error", result.output.lastOrNull().orEmpty())
                    }
                }
            }
            METHOD_RUN_CONTROL_COMMAND -> {
                val command = extras?.getString("command").orEmpty()
                if (command.isBlank()) return errorBundle("command missing")
                RootUtils.writeAbkControlCommand(command).toBundle()
            }
            METHOD_GET_FOREGROUND_PACKAGE -> {
                val foreground = RootUtils.readForegroundPackage().orEmpty()
                Bundle().apply {
                    putBoolean("success", true)
                    putString("package_name", foreground)
                }
            }
            else -> errorBundle("unsupported method")
        }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0

    private fun RootUtils.ShellResult.toBundle(): Bundle = Bundle().apply {
        putBoolean("success", success)
        putStringArrayList("output", ArrayList(output))
        if (!success) {
            putString("error", output.lastOrNull().orEmpty())
        }
    }

    private fun errorBundle(message: String): Bundle = Bundle().apply {
        putBoolean("success", false)
        putString("error", message)
    }

    private fun callingPackageCompat(): String? {
        val direct = callingPackage
        if (!direct.isNullOrBlank()) return direct
        if (Binder.getCallingUid() == Process.myUid()) return context?.packageName
        val context = context ?: return null
        return context.packageManager.getPackagesForUid(Binder.getCallingUid())
            ?.firstOrNull()
    }

    private fun isCallerAuthorized(
        context: android.content.Context,
        extensionId: String,
        callerPackage: String?
    ): Boolean {
        if (callerPackage.isNullOrBlank()) return false
        if (callerPackage == context.packageName) return true
        return abkLoadManagedExtensions(context).any { extension ->
            extension.extensionId == extensionId &&
                extension.companionPackage.isNotBlank() &&
                extension.companionPackage == callerPackage
        }
    }

    companion object {
        const val METHOD_GET_STATE = "get_extension_state"
        const val METHOD_PUT_STATE = "put_extension_state"
        const val METHOD_CLEAR_STATE = "clear_extension_state"
        const val METHOD_GET_CONTROL_STATUS = "get_control_status"
        const val METHOD_RUN_CONTROL_COMMAND = "run_control_command"
        const val METHOD_GET_FOREGROUND_PACKAGE = "get_foreground_package"
    }
}
