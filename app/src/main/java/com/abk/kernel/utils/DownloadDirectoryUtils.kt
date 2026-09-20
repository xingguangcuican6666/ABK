package com.abk.kernel.utils

import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import java.io.File

object DownloadDirectoryUtils {

    private const val APP_DOWNLOAD_FOLDER = "ABK"
    private const val EXTERNAL_STORAGE_DOCUMENTS_AUTHORITY = "com.android.externalstorage.documents"

    fun defaultDirectoryPath(): String {
        val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        return File(publicDownloads, APP_DOWNLOAD_FOLDER).absolutePath
    }

    fun normalizeDirectoryPath(path: String?): String {
        val raw = path?.trim().orEmpty()
        val target = if (raw.isBlank()) defaultDirectoryPath() else raw
        val normalized = runCatching { File(target).canonicalFile.path }
            .getOrElse { File(target).absoluteFile.path }
        return if (normalized.length > 1) {
            normalized.trimEnd(File.separatorChar)
        } else {
            normalized
        }
    }

    fun directoryPathFromTreeUri(uri: Uri): String? {
        if (uri.authority != EXTERNAL_STORAGE_DOCUMENTS_AUTHORITY) return null
        val documentId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: return null
        val volumeId = documentId.substringBefore(':')
        val relativePath = documentId.substringAfter(':', "")
        val base = when {
            volumeId.equals("primary", ignoreCase = true) -> Environment.getExternalStorageDirectory()
            else -> return null
        }
        return normalizeDirectoryPath(File(base, relativePath).path)
    }
}
