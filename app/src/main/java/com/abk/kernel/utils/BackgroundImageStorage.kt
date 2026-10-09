package com.abk.kernel.utils

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/**
 * Stores the user-picked custom background as a plain file in app-private storage.
 *
 * The picker returns a `content://` URI backed by an external provider. Decoding it on
 * every cold start means a cross-process read (the provider process may itself be cold,
 * and the persistable permission can be lost), which is exactly why the wallpaper can
 * arrive late — and the screen shows the bare surface — after the app is killed and
 * reopened. Copying once into app-private storage makes every later launch decode a
 * local file: fast, deterministic, and independent of any provider.
 */
object BackgroundImageStorage {

    private const val BACKGROUND_FILE_NAME = "abk_custom_background.jpg"
    /** Upper bound on the copied background; generous for wallpapers. */
    private const val MAX_BACKGROUND_BYTES = 64L * 1024 * 1024

    fun internalFile(context: Context): File = File(context.filesDir, BACKGROUND_FILE_NAME)

    /**
     * Copies [uri] into app-private storage. Returns the `file://` Uri on success, or
     * null when the source cannot be read (e.g. the persistable permission was lost),
     * is not an image, or exceeds [MAX_BACKGROUND_BYTES].
     */
    fun copyToInternalStorage(context: Context, uri: Uri): Uri? {
        // Only images belong here; a provider lying about type or size would otherwise
        // waste storage and stall the picker / startup migration.
        val type = context.contentResolver.getType(uri)
        if (type != null && !type.startsWith("image/")) return null

        val file = internalFile(context)
        file.parentFile?.mkdirs()
        // Unique temp so concurrent copies (user pick + startup migration) cannot
        // interleave writes to the same file; the fully-written temp is then renamed.
        val temp = File.createTempFile("abk_bg", ".tmp", file.parentFile)
        try {
            val input = context.contentResolver.openInputStream(uri) ?: return null
            try {
                FileOutputStream(temp).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_BACKGROUND_BYTES) return null
                        output.write(buffer, 0, read)
                    }
                    output.flush()
                }
            } finally {
                input.close()
            }
            if (!temp.renameTo(file)) {
                temp.copyTo(file, overwrite = true)
            }
            return Uri.fromFile(file)
        } catch (e: Exception) {
            return null
        } finally {
            runCatching { temp.delete() }
        }
    }

    /** True when [uriString] is not already a local `file://` URI and should be copied. */
    fun needsCopy(uriString: String?): Boolean {
        if (uriString.isNullOrBlank()) return false
        val scheme = Uri.parse(uriString).scheme
        return scheme != null && scheme != "file"
    }
}
