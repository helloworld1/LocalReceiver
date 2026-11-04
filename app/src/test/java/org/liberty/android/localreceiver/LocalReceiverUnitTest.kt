package org.liberty.android.localreceiver

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.CancellationException

class LocalReceiverUnitTest {

    @Test
    fun formatFileSize_formatsValuesCorrectly() {
        assertEquals("Unknown size", FileMetadataResolver.formatFileSize(-1L))
        assertEquals("0 B", FileMetadataResolver.formatFileSize(0L))
        assertEquals("512 B", FileMetadataResolver.formatFileSize(512L))
        assertEquals("1.5 KB", FileMetadataResolver.formatFileSize(1536L))
        assertEquals("2.0 MB", FileMetadataResolver.formatFileSize(2L * 1024L * 1024L))
    }

    @Test
    fun sanitizeFileName_stripsPathSegmentsAndControlChars() {
        assertEquals("report.pdf", FileMetadataResolver.sanitizeFileName("/storage/emulated/0/Download/report.pdf"))
        assertEquals("archive.zip", FileMetadataResolver.sanitizeFileName("C:\\Users\\test\\archive.zip"))
        assertTrue(FileMetadataResolver.sanitizeFileName("   ").startsWith("shared_file_"))
    }

    @Test
    fun copyStreams_copiesAllBytesAndReportsProgress() {
        val payload = ByteArray(150_000) { (it % 251).toByte() }
        val input = ByteArrayInputStream(payload)
        val output = ByteArrayOutputStream()
        val progressUpdates = mutableListOf<Pair<Long, Long>>()

        val totalCopied = FileCopyEngine.copyStreams(
            input = input,
            output = output,
            totalBytes = payload.size.toLong(),
            isCancelled = { false },
            onProgress = { copied, total ->
                progressUpdates.add(copied to total)
            }
        )

        assertEquals(payload.size.toLong(), totalCopied)
        assertArrayEquals(payload, output.toByteArray())
        assertTrue(progressUpdates.isNotEmpty())
        assertEquals(0L to payload.size.toLong(), progressUpdates.first())
        assertEquals(payload.size.toLong() to payload.size.toLong(), progressUpdates.last())
    }

    @Test(expected = CancellationException::class)
    fun copyStreams_throwsCancellationExceptionWhenCancelled() {
        val payload = ByteArray(100_000) { 1 }
        val input = ByteArrayInputStream(payload)
        val output = ByteArrayOutputStream()

        FileCopyEngine.copyStreams(
            input = input,
            output = output,
            totalBytes = payload.size.toLong(),
            isCancelled = { true },
            onProgress = { _, _ -> }
        )
    }

    @Test
    fun copyHistoryEntry_instantiatesAndMaintainsFields() {
        val entry = CopyHistoryEntry(
            id = 42L,
            timestamp = 1700000000000L,
            sourceUri = "content://media/external/file/123",
            sourceName = "document.pdf",
            destinationDir = "Downloads (System Default)",
            destinationUri = "content://media/external/downloads/456",
            sizeBytes = 1048576L,
            mimeType = "application/pdf",
            status = CopyHistoryEntry.Status.SUCCESS,
            errorMessage = null
        )

        assertEquals(42L, entry.id)
        assertEquals(1700000000000L, entry.timestamp)
        assertEquals("document.pdf", entry.sourceName)
        assertEquals(CopyHistoryEntry.Status.SUCCESS, entry.status)
        assertEquals(1048576L, entry.sizeBytes)
        assertEquals("content://media/external/downloads/456", entry.destinationUri)

        val failedEntry = entry.copy(
            status = CopyHistoryEntry.Status.FAILURE,
            destinationUri = null,
            errorMessage = "Permission denied"
        )
        assertEquals(CopyHistoryEntry.Status.FAILURE, failedEntry.status)
        assertEquals("Permission denied", failedEntry.errorMessage)
        assertEquals(null, failedEntry.destinationUri)
    }
}
