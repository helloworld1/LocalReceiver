package org.liberty.android.localreceiver

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File

object FileDeleter {

    /**
     * Attempts to delete the target file given its destination URI string.
     * Works for MediaStore items (Downloads) created by the app,
     * DocumentsProvider / SAF items, and direct file:// paths.
     */
    fun deleteFile(context: Context, destinationUriString: String?): Boolean {
        if (destinationUriString.isNullOrBlank()) return false
        val uri = Uri.parse(destinationUriString)
        val cr = context.contentResolver

        return try {
            when (uri.scheme) {
                ContentResolver.SCHEME_CONTENT -> {
                    // Try DocumentsContract first
                    var deleted = runCatching {
                        DocumentsContract.deleteDocument(cr, uri)
                    }.getOrDefault(false)

                    // If not deleted via DocumentsContract, try MediaStore / ContentResolver delete
                    if (!deleted) {
                        deleted = runCatching {
                            cr.delete(uri, null, null) > 0
                        }.getOrDefault(false)
                    }
                    deleted
                }
                ContentResolver.SCHEME_FILE -> {
                    val path = uri.path ?: return false
                    runCatching { File(path).delete() }.getOrDefault(false)
                }
                else -> false
            }
        } catch (t: Throwable) {
            false
        }
    }
}
