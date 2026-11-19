package com.smartvoices.keyboard.clipboard

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class ClipEntry(
    val id: Long,
    val text: String,
    val isPinned: Boolean,
    val copyCount: Int,
    val createdAt: Long
)

class ClipboardDbHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "smart_clipboard.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_CLIPS = "clips"

        private const val COL_ID = "id"
        private const val COL_TEXT = "text"
        private const val COL_PINNED = "is_pinned"
        private const val COL_COPY_COUNT = "copy_count"
        private const val COL_CREATED_AT = "created_at"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createQuery = """
            CREATE TABLE $TABLE_CLIPS (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_TEXT TEXT UNIQUE,
                $COL_PINNED INTEGER DEFAULT 0,
                $COL_COPY_COUNT INTEGER DEFAULT 1,
                $COL_CREATED_AT INTEGER
            )
        """.trimIndent()
        db.execSQL(createQuery)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_CLIPS")
        onCreate(db)
    }

    fun insertOrUpdateClip(text: String): Long {
        if (text.isBlank()) return -1
        val db = writableDatabase
        val now = System.currentTimeMillis()

        // Check if text already exists
        val cursor = db.query(
            TABLE_CLIPS,
            arrayOf(COL_ID, COL_COPY_COUNT),
            "$COL_TEXT = ?",
            arrayOf(text),
            null, null, null
        )

        val id = if (cursor.moveToFirst()) {
            val existingId = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID))
            val currentCount = cursor.getInt(cursor.getColumnIndexOrThrow(COL_COPY_COUNT))
            val values = ContentValues().apply {
                put(COL_COPY_COUNT, currentCount + 1)
                put(COL_CREATED_AT, now)
            }
            db.update(TABLE_CLIPS, values, "$COL_ID = ?", arrayOf(existingId.toString()))
            existingId
        } else {
            val values = ContentValues().apply {
                put(COL_TEXT, text)
                put(COL_PINNED, 0)
                put(COL_COPY_COUNT, 1)
                put(COL_CREATED_AT, now)
            }
            db.insert(TABLE_CLIPS, null, values)
        }
        cursor.close()
        return id
    }

    fun getAllClips(query: String = "", pinnedOnly: Boolean = false): List<ClipEntry> {
        val db = readableDatabase
        val selection = StringBuilder()
        val args = mutableListOf<String>()

        if (pinnedOnly) {
            selection.append("$COL_PINNED = 1")
        }

        if (query.isNotBlank()) {
            if (selection.isNotEmpty()) selection.append(" AND ")
            selection.append("$COL_TEXT LIKE ?")
            args.add("%$query%")
        }

        val cursor = db.query(
            TABLE_CLIPS,
            null,
            if (selection.isEmpty()) null else selection.toString(),
            if (args.isEmpty()) null else args.toTypedArray(),
            null, null,
            "$COL_PINNED DESC, $COL_CREATED_AT DESC"
        )

        val list = mutableListOf<ClipEntry>()
        while (cursor.moveToNext()) {
            list.add(
                ClipEntry(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID)),
                    text = cursor.getString(cursor.getColumnIndexOrThrow(COL_TEXT)),
                    isPinned = cursor.getInt(cursor.getColumnIndexOrThrow(COL_PINNED)) == 1,
                    copyCount = cursor.getInt(cursor.getColumnIndexOrThrow(COL_COPY_COUNT)),
                    createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_CREATED_AT))
                )
            )
        }
        cursor.close()
        return list
    }

    fun togglePin(id: Long, newPinnedState: Boolean) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_PINNED, if (newPinnedState) 1 else 0)
        }
        db.update(TABLE_CLIPS, values, "$COL_ID = ?", arrayOf(id.toString()))
    }

    fun deleteClip(id: Long) {
        val db = writableDatabase
        db.delete(TABLE_CLIPS, "$COL_ID = ?", arrayOf(id.toString()))
    }

    fun clearUnpinned(): Int {
        val db = writableDatabase
        return db.delete(TABLE_CLIPS, "$COL_PINNED = 0", null)
    }
}
