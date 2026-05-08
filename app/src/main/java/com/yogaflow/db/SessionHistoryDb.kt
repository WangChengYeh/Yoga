package com.yogaflow.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

class SessionHistoryDb(context: Context) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_NAME (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                ts_ms INTEGER NOT NULL,
                duration_ms INTEGER NOT NULL,
                steps_completed INTEGER NOT NULL,
                correction_count INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    fun record(durationMs: Long, stepsCompleted: Int, correctionCount: Int) {
        try {
            val db = writableDatabase
            val values = ContentValues().apply {
                put("ts_ms", System.currentTimeMillis())
                put("duration_ms", durationMs)
                put("steps_completed", stepsCompleted)
                put("correction_count", correctionCount)
            }
            db.insert(TABLE_NAME, null, values)
            trimIfNeeded(db)
            Log.d(TAG, "Recorded session history (duration=$durationMs, steps=$stepsCompleted, corrections=$correctionCount)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to record session history", e)
        }
    }

    private fun trimIfNeeded(db: SQLiteDatabase) {
        db.execSQL(
            """
            DELETE FROM $TABLE_NAME
            WHERE id IN (
                SELECT id FROM $TABLE_NAME
                ORDER BY id ASC
                LIMIT (
                    SELECT CASE
                        WHEN COUNT(*) > $MAX_ROWS THEN COUNT(*) - $MAX_ROWS
                        ELSE 0
                    END
                    FROM $TABLE_NAME
                )
            )
            """.trimIndent()
        )
    }

    companion object {
        private const val TAG = "SessionHistoryDb"
        private const val DATABASE_NAME = "session_history.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_NAME = "session_history"
        private const val MAX_ROWS = 1000
    }
}
