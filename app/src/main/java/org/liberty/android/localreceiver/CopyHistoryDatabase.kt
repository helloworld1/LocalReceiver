package org.liberty.android.localreceiver

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class CopyHistoryDatabase(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_HISTORY (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_TIMESTAMP INTEGER NOT NULL,
                $COLUMN_SOURCE_URI TEXT NOT NULL,
                $COLUMN_SOURCE_NAME TEXT NOT NULL,
                $COLUMN_DESTINATION_DIR TEXT NOT NULL,
                $COLUMN_DESTINATION_URI TEXT,
                $COLUMN_SIZE_BYTES INTEGER NOT NULL,
                $COLUMN_MIME_TYPE TEXT,
                $COLUMN_STATUS TEXT NOT NULL,
                $COLUMN_ERROR_MESSAGE TEXT
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_HISTORY")
        onCreate(db)
    }

    fun insertEntry(entry: CopyHistoryEntry): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_TIMESTAMP, entry.timestamp)
            put(COLUMN_SOURCE_URI, entry.sourceUri)
            put(COLUMN_SOURCE_NAME, entry.sourceName)
            put(COLUMN_DESTINATION_DIR, entry.destinationDir)
            put(COLUMN_DESTINATION_URI, entry.destinationUri)
            put(COLUMN_SIZE_BYTES, entry.sizeBytes)
            put(COLUMN_MIME_TYPE, entry.mimeType)
            put(COLUMN_STATUS, entry.status.name)
            put(COLUMN_ERROR_MESSAGE, entry.errorMessage)
        }
        return db.insert(TABLE_HISTORY, null, values)
    }

    fun getAllEntries(): List<CopyHistoryEntry> {
        val list = mutableListOf<CopyHistoryEntry>()
        val db = readableDatabase
        val cursor: Cursor = db.query(
            TABLE_HISTORY,
            null,
            null,
            null,
            null,
            null,
            "$COLUMN_TIMESTAMP DESC"
        )
        cursor.use {
            val idIdx = it.getColumnIndexOrThrow(COLUMN_ID)
            val timeIdx = it.getColumnIndexOrThrow(COLUMN_TIMESTAMP)
            val srcUriIdx = it.getColumnIndexOrThrow(COLUMN_SOURCE_URI)
            val srcNameIdx = it.getColumnIndexOrThrow(COLUMN_SOURCE_NAME)
            val destDirIdx = it.getColumnIndexOrThrow(COLUMN_DESTINATION_DIR)
            val destUriIdx = it.getColumnIndexOrThrow(COLUMN_DESTINATION_URI)
            val sizeIdx = it.getColumnIndexOrThrow(COLUMN_SIZE_BYTES)
            val mimeIdx = it.getColumnIndexOrThrow(COLUMN_MIME_TYPE)
            val statusIdx = it.getColumnIndexOrThrow(COLUMN_STATUS)
            val errorIdx = it.getColumnIndexOrThrow(COLUMN_ERROR_MESSAGE)

            while (it.moveToNext()) {
                val statusStr = it.getString(statusIdx)
                val status = runCatching {
                    CopyHistoryEntry.Status.valueOf(statusStr)
                }.getOrDefault(CopyHistoryEntry.Status.FAILURE)

                list.add(
                    CopyHistoryEntry(
                        id = it.getLong(idIdx),
                        timestamp = it.getLong(timeIdx),
                        sourceUri = it.getString(srcUriIdx),
                        sourceName = it.getString(srcNameIdx),
                        destinationDir = it.getString(destDirIdx),
                        destinationUri = if (it.isNull(destUriIdx)) null else it.getString(destUriIdx),
                        sizeBytes = it.getLong(sizeIdx),
                        mimeType = if (it.isNull(mimeIdx)) null else it.getString(mimeIdx),
                        status = status,
                        errorMessage = if (it.isNull(errorIdx)) null else it.getString(errorIdx)
                    )
                )
            }
        }
        return list
    }

    fun deleteEntry(id: Long): Boolean {
        val db = writableDatabase
        return db.delete(TABLE_HISTORY, "$COLUMN_ID = ?", arrayOf(id.toString())) > 0
    }

    fun updateDestinationUri(id: Long, destinationUri: String?): Boolean {
        val db = writableDatabase
        val values = ContentValues().apply {
            if (destinationUri == null) {
                putNull(COLUMN_DESTINATION_URI)
            } else {
                put(COLUMN_DESTINATION_URI, destinationUri)
            }
        }
        return db.update(TABLE_HISTORY, values, "$COLUMN_ID = ?", arrayOf(id.toString())) > 0
    }

    fun clearAll(): Boolean {
        val db = writableDatabase
        return db.delete(TABLE_HISTORY, null, null) >= 0
    }

    companion object {
        const val DATABASE_NAME = "copy_history.db"
        const val DATABASE_VERSION = 1
        const val TABLE_HISTORY = "history"

        const val COLUMN_ID = "_id"
        const val COLUMN_TIMESTAMP = "timestamp"
        const val COLUMN_SOURCE_URI = "source_uri"
        const val COLUMN_SOURCE_NAME = "source_name"
        const val COLUMN_DESTINATION_DIR = "destination_dir"
        const val COLUMN_DESTINATION_URI = "destination_uri"
        const val COLUMN_SIZE_BYTES = "size_bytes"
        const val COLUMN_MIME_TYPE = "mime_type"
        const val COLUMN_STATUS = "status"
        const val COLUMN_ERROR_MESSAGE = "error_message"
    }
}
