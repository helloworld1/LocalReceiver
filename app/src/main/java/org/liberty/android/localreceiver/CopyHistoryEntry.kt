package org.liberty.android.localreceiver

data class CopyHistoryEntry(
    val id: Long = 0,
    val timestamp: Long,
    val sourceUri: String,
    val sourceName: String,
    val destinationDir: String,
    val destinationUri: String?,
    val sizeBytes: Long,
    val mimeType: String?,
    val status: Status,
    val errorMessage: String? = null
) {
    enum class Status {
        SUCCESS,
        FAILURE,
        CANCELLED
    }
}
