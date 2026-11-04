package org.liberty.android.localreceiver

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.io.File
import java.util.Locale

data class SharedFileInfo(
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long,
    val mimeType: String
)

object FileMetadataResolver {

    fun extractSharedUris(intent: Intent?): List<Uri> {
        if (intent == null) return emptyList()
        val result = linkedSetOf<Uri>()

        when (intent.action) {
            Intent.ACTION_SEND -> {
                val singleUri = getParcelableExtraCompat(intent, Intent.EXTRA_STREAM, Uri::class.java)
                if (singleUri != null) {
                    result.add(singleUri)
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val multipleUris = getParcelableArrayListExtraCompat(intent, Intent.EXTRA_STREAM, Uri::class.java)
                if (multipleUris != null) {
                    result.addAll(multipleUris.filterNotNull())
                }
            }
        }

        val clipData = intent.clipData
        if (clipData != null) {
            for (i in 0 until clipData.itemCount) {
                val itemUri = clipData.getItemAt(i)?.uri
                if (itemUri != null) {
                    result.add(itemUri)
                }
            }
        }

        return result.toList()
    }

    fun resolve(contentResolver: ContentResolver, uri: Uri): SharedFileInfo {
        var displayName: String? = null
        var sizeBytes = -1L

        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            runCatching {
                contentResolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                            displayName = cursor.getString(nameIndex)
                        }
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                            sizeBytes = cursor.getLong(sizeIndex)
                        }
                    }
                }
            }
        } else if (uri.scheme == ContentResolver.SCHEME_FILE) {
            val path = uri.path
            if (path != null) {
                val file = File(path)
                displayName = file.name
                if (file.exists()) {
                    sizeBytes = file.length()
                }
            }
        }

        val mimeType = contentResolver.getType(uri)
            ?: inferMimeTypeFromName(displayName ?: uri.lastPathSegment)
            ?: "application/octet-stream"

        val resolvedName = sanitizeFileName(
            displayName?.takeIf { it.isNotBlank() }
                ?: fallbackNameFromUri(uri, mimeType)
        )

        return SharedFileInfo(
            uri = uri,
            displayName = resolvedName,
            sizeBytes = sizeBytes,
            mimeType = mimeType
        )
    }

    fun sanitizeFileName(rawName: String): String {
        val cleaned = rawName
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .replace(Regex("[\\x00-\\x1F]"), "_")
            .trim()
        return cleaned.ifEmpty { "shared_file_${System.currentTimeMillis()}" }
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes < 0) return "Unknown size"
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
        val gb = mb / 1024.0
        return String.format(Locale.US, "%.2f GB", gb)
    }

    private fun fallbackNameFromUri(uri: Uri, mimeType: String): String {
        val lastSegment = uri.lastPathSegment
            ?.substringAfterLast('/')
            ?.substringAfterLast(':')
            ?.takeIf { it.isNotBlank() }

        if (lastSegment != null && lastSegment.contains('.')) {
            return lastSegment
        }

        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
        val base = lastSegment ?: "shared_file_${System.currentTimeMillis()}"
        return if (!ext.isNullOrBlank() && !base.endsWith(".$ext", ignoreCase = true)) {
            "$base.$ext"
        } else {
            base
        }
    }

    private fun inferMimeTypeFromName(name: String?): String? {
        if (name == null) return null
        val ext = name.substringAfterLast('.', "").lowercase(Locale.US)
        if (ext.isEmpty()) return null
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
    }

    @Suppress("DEPRECATION")
    private fun <T> getParcelableExtraCompat(intent: Intent, name: String, clazz: Class<T>): T? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(name, clazz)
        } else {
            intent.getParcelableExtra(name)
        }
    }

    @Suppress("DEPRECATION")
    private fun <T : android.os.Parcelable> getParcelableArrayListExtraCompat(
        intent: Intent,
        name: String,
        clazz: Class<T>
    ): ArrayList<T>? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(name, clazz)
        } else {
            intent.getParcelableArrayListExtra(name)
        }
    }
}
