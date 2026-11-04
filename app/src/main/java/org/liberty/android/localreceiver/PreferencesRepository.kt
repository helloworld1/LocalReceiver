package org.liberty.android.localreceiver

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract

data class DestinationInfo(
    val customTreeUri: Uri?,
    val displayName: String,
    val details: String,
    val isCustom: Boolean,
    val hasValidPermission: Boolean
)

class PreferencesRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getCustomDirectoryUri(): Uri? {
        val uriString = prefs.getString(KEY_CUSTOM_DIR_URI, null) ?: return null
        return runCatching { Uri.parse(uriString) }.getOrNull()
    }

    fun setCustomDirectoryUri(uri: Uri?) {
        prefs.edit().apply {
            if (uri == null) {
                remove(KEY_CUSTOM_DIR_URI)
            } else {
                putString(KEY_CUSTOM_DIR_URI, uri.toString())
            }
            apply()
        }
    }

    fun clearCustomDirectory() {
        setCustomDirectoryUri(null)
    }

    fun getDestinationInfo(): DestinationInfo {
        val treeUri = getCustomDirectoryUri()
        if (treeUri == null) {
            return DestinationInfo(
                customTreeUri = null,
                displayName = context.getString(R.string.destination_default_name),
                details = context.getString(R.string.destination_default_subtitle),
                isCustom = false,
                hasValidPermission = true
            )
        }

        val hasWritePermission = hasPersistedWritePermission(treeUri)
        val folderName = resolveTreeDisplayName(treeUri)
        val readableDocId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }
            .getOrDefault(treeUri.toString())

        val detailsText = if (hasWritePermission) {
            readableDocId
        } else {
            "$readableDocId (Permission expired — please reselect folder)"
        }

        return DestinationInfo(
            customTreeUri = treeUri,
            displayName = folderName,
            details = detailsText,
            isCustom = true,
            hasValidPermission = hasWritePermission
        )
    }

    fun hasPersistedWritePermission(treeUri: Uri): Boolean {
        return context.contentResolver.persistedUriPermissions.any { perm ->
            perm.uri == treeUri && perm.isWritePermission
        }
    }

    fun takePersistablePermission(treeUri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching {
            context.contentResolver.takePersistableUriPermission(treeUri, flags)
        }
    }

    private fun resolveTreeDisplayName(treeUri: Uri): String {
        return runCatching {
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
            context.contentResolver.query(
                docUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    if (idx >= 0 && !cursor.isNull(idx)) {
                        return@runCatching cursor.getString(idx)
                    }
                }
                null
            } ?: docId.substringAfterLast(':').ifEmpty { docId }
        }.getOrElse {
            treeUri.lastPathSegment ?: Environment.DIRECTORY_DOWNLOADS
        }
    }

    companion object {
        private const val PREFS_NAME = "local_receiver_prefs"
        private const val KEY_CUSTOM_DIR_URI = "custom_dir_uri"
    }
}
