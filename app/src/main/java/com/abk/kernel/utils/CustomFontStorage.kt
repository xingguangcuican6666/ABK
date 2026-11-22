package com.abk.kernel.utils

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.CancellationException

/** Imports user-selected TTF/OTF files into app-private storage. */
object CustomFontStorage {
    private const val FONT_DIRECTORY = "fonts"
    private const val MAX_FONT_BYTES = 32L * 1024 * 1024
    private val idPattern = Regex("^[a-f0-9-]{36}$")

    data class ImportedFont(val id: String, val displayName: String)

    enum class Failure { READ, EMPTY, TOO_LARGE, INVALID, STORAGE }

    class ImportException(val failure: Failure, cause: Throwable? = null) : Exception(cause)

    fun fileFor(context: Context, id: String): File? {
        if (!idPattern.matches(id)) return null
        return File(File(context.filesDir, FONT_DIRECTORY), "$id.font")
    }

    fun loadFamily(context: Context, id: String?): FontFamily? {
        if (id.isNullOrBlank()) return null
        val file = fileFor(context, id)?.takeIf(File::isFile) ?: return null
        return runCatching {
            FontFamily(Font(file, weight = FontWeight.Normal))
        }.getOrNull()
    }

    /** Copies and validates the picked font before it becomes selectable. */
    fun import(context: Context, uri: Uri, sourceName: String?): ImportedFont {
        val directory = File(context.filesDir, FONT_DIRECTORY)
        try {
            if (!directory.exists() && !directory.mkdirs()) throw ImportException(Failure.STORAGE)
            val temp = File.createTempFile("import-", ".tmp", directory)
            var promoted: File? = null
            try {
                val input = context.contentResolver.openInputStream(uri)
                    ?: throw ImportException(Failure.READ)
                input.use { stream ->
                    FileOutputStream(temp).use { output ->
                        val buffer = ByteArray(8 * 1024)
                        var total = 0L
                        while (true) {
                            val count = stream.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > MAX_FONT_BYTES) throw ImportException(Failure.TOO_LARGE)
                            output.write(buffer, 0, count)
                        }
                        output.flush()
                    }
                }
                if (temp.length() == 0L) throw ImportException(Failure.EMPTY)
                try {
                    Typeface.createFromFile(temp)
                } catch (error: Exception) {
                    throw ImportException(Failure.INVALID, error)
                }

                val id = UUID.randomUUID().toString()
                val target = checkNotNull(fileFor(context, id))
                promoted = target
                if (!temp.renameTo(target)) temp.copyTo(target, overwrite = false)
                return ImportedFont(id, sanitizeDisplayName(sourceName))
            } catch (error: CancellationException) {
                promoted?.delete()
                throw error
            } catch (error: ImportException) {
                promoted?.delete()
                throw error
            } catch (error: Exception) {
                promoted?.delete()
                throw ImportException(Failure.STORAGE, error)
            } finally {
                temp.delete()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: ImportException) {
            throw error
        } catch (error: Exception) {
            throw ImportException(Failure.STORAGE, error)
        }
    }

    fun delete(context: Context, id: String?) {
        if (id.isNullOrBlank()) return
        fileFor(context, id)?.delete()
    }

    fun sanitizeDisplayName(raw: String?): String {
        val name = raw.orEmpty()
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .replace(Regex("[\\p{Cntrl}]"), "")
            .trim()
            .take(80)
        return name.ifBlank { "Custom font" }
    }
}
