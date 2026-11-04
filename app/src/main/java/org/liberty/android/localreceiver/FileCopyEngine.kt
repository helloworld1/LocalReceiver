package org.liberty.android.localreceiver

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CancellationException

object FileCopyEngine {

    private const val BUFFER_SIZE = 64 * 1024 // 64 KB

    fun copyFile(
        contentResolver: ContentResolver,
        sourceFile: SharedFileInfo,
        customTreeUri: Uri?,
        isCancelled: () -> Boolean,
        onProgress: (bytesCopied: Long, totalBytes: Long) -> Unit
    ): Uri {
        return if (customTreeUri == null) {
            copyToDownloads(contentResolver, sourceFile, isCancelled, onProgress)
        } else {
            copyToCustomTree(contentResolver, sourceFile, customTreeUri, isCancelled, onProgress)
        }
    }

    private fun copyToDownloads(
        contentResolver: ContentResolver,
        sourceFile: SharedFileInfo,
        isCancelled: () -> Boolean,
        onProgress: (bytesCopied: Long, totalBytes: Long) -> Unit
    ): Uri {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, sourceFile.displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, sourceFile.mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val destUri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Unable to create destination entry in Downloads")

        try {
            contentResolver.openInputStream(sourceFile.uri)?.use { input ->
                contentResolver.openOutputStream(destUri)?.use { output ->
                    copyStreams(
                        input = input,
                        output = output,
                        totalBytes = sourceFile.sizeBytes,
                        isCancelled = isCancelled,
                        onProgress = onProgress
                    )
                } ?: throw IOException("Unable to open output stream for Downloads")
            } ?: throw IOException("Unable to open input stream for shared file")

            val completeValues = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            contentResolver.update(destUri, completeValues, null, null)
            return destUri
        } catch (t: Throwable) {
            runCatching { contentResolver.delete(destUri, null, null) }
            throw t
        }
    }

    private fun copyToCustomTree(
        contentResolver: ContentResolver,
        sourceFile: SharedFileInfo,
        treeUri: Uri,
        isCancelled: () -> Boolean,
        onProgress: (bytesCopied: Long, totalBytes: Long) -> Unit
    ): Uri {
        val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId)

        val destUri = DocumentsContract.createDocument(
            contentResolver,
            parentDocUri,
            sourceFile.mimeType,
            sourceFile.displayName
        ) ?: throw IOException("Unable to create file in selected directory (check folder permissions)")

        try {
            contentResolver.openInputStream(sourceFile.uri)?.use { input ->
                contentResolver.openOutputStream(destUri)?.use { output ->
                    copyStreams(
                        input = input,
                        output = output,
                        totalBytes = sourceFile.sizeBytes,
                        isCancelled = isCancelled,
                        onProgress = onProgress
                    )
                } ?: throw IOException("Unable to open output stream in selected directory")
            } ?: throw IOException("Unable to open input stream for shared file")

            return destUri
        } catch (t: Throwable) {
            runCatching { DocumentsContract.deleteDocument(contentResolver, destUri) }
            throw t
        }
    }

    fun copyStreams(
        input: InputStream,
        output: OutputStream,
        totalBytes: Long,
        isCancelled: () -> Boolean,
        onProgress: (bytesCopied: Long, totalBytes: Long) -> Unit
    ): Long {
        val buffer = ByteArray(BUFFER_SIZE)
        var bytesCopied = 0L
        onProgress(0L, totalBytes)

        while (true) {
            if (isCancelled()) {
                throw CancellationException("Copy cancelled by user")
            }
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            bytesCopied += read
            onProgress(bytesCopied, totalBytes)
        }
        output.flush()
        return bytesCopied
    }
}
