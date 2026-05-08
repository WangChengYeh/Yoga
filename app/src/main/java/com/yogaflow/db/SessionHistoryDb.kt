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
                correction_count INTEGER NOT NULL,
                course_name TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN course_name TEXT NOT NULL DEFAULT ''")
        }
    }

    fun record(durationMs: Long, stepsCompleted: Int, correctionCount: Int, courseName: String = "") {
        try {
            val db = writableDatabase
            val values = ContentValues().apply {
                put("ts_ms", System.currentTimeMillis())
                put("duration_ms", durationMs)
                put("steps_completed", stepsCompleted)
                put("correction_count", correctionCount)
                put("course_name", courseName)
            }
            db.insert(TABLE_NAME, null, values)
            trimIfNeeded(db)
            Log.d(TAG, "Recorded session history (course=$courseName, duration=$durationMs, steps=$stepsCompleted, corrections=$correctionCount)")
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

    data class SessionEntry(
        val tsMs: Long,
        val durationMs: Long,
        val stepsCompleted: Int,
        val correctionCount: Int,
        val courseName: String
    )

    fun getAll(): List<SessionEntry> {
        val result = mutableListOf<SessionEntry>()
        try {
            val db = readableDatabase
            val cursor = db.query(
                TABLE_NAME, null, null, null, null, null,
                "ts_ms DESC"
            )
            cursor.use {
                while (it.moveToNext()) {
                    result.add(SessionEntry(
                        tsMs = it.getLong(it.getColumnIndexOrThrow("ts_ms")),
                        durationMs = it.getLong(it.getColumnIndexOrThrow("duration_ms")),
                        stepsCompleted = it.getInt(it.getColumnIndexOrThrow("steps_completed")),
                        correctionCount = it.getInt(it.getColumnIndexOrThrow("correction_count")),
                        courseName = it.getString(it.getColumnIndexOrThrow("course_name")) ?: ""
                    ))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read session history", e)
        }
        return result
    }

    data class WeeklySummary(
        val sessionCount: Int,
        val totalMs: Long,
        val streakDays: Int
    )

    fun getWeeklySummary(): WeeklySummary {
        val all = getAll()
        val nowMs = System.currentTimeMillis()
        val weekMs = 7L * 24 * 60 * 60 * 1000
        val weekly = all.filter { nowMs - it.tsMs <= weekMs }

        // Streak: consecutive calendar days with at least one session, going back from today
        val cal = java.util.Calendar.getInstance()
        fun dayKey(tsMs: Long): String {
            cal.timeInMillis = tsMs
            return "%d-%02d-%02d".format(
                cal.get(java.util.Calendar.YEAR),
                cal.get(java.util.Calendar.MONTH) + 1,
                cal.get(java.util.Calendar.DAY_OF_MONTH)
            )
        }
        val sessionDays = all.map { dayKey(it.tsMs) }.toSet()
        var streak = 0
        cal.timeInMillis = nowMs
        while (true) {
            val key = "%d-%02d-%02d".format(
                cal.get(java.util.Calendar.YEAR),
                cal.get(java.util.Calendar.MONTH) + 1,
                cal.get(java.util.Calendar.DAY_OF_MONTH)
            )
            if (!sessionDays.contains(key)) break
            streak++
            cal.add(java.util.Calendar.DAY_OF_MONTH, -1)
        }

        return WeeklySummary(
            sessionCount = weekly.size,
            totalMs = weekly.sumOf { it.durationMs },
            streakDays = streak
        )
    }

    companion object {
        private const val TAG = "SessionHistoryDb"
        private const val DATABASE_NAME = "session_history.db"
        private const val DATABASE_VERSION = 2
        private const val TABLE_NAME = "session_history"
        private const val MAX_ROWS = 1000
    }
}
