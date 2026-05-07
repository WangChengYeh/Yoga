package com.yogaflow.llm

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import com.yogaflow.coach.CoachState
import com.yogaflow.yoga.YogaPose

class LlmInteractionDb(context: Context) : SQLiteOpenHelper(
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
                pose TEXT NOT NULL,
                state TEXT NOT NULL,
                raw_cue TEXT NOT NULL,
                prompt TEXT NOT NULL,
                response TEXT NOT NULL,
                is_fallback INTEGER NOT NULL,
                elapsed_ms INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    fun log(
        pose: YogaPose,
        state: CoachState,
        rawCue: String,
        prompt: String,
        response: String,
        isFallback: Boolean,
        elapsedMs: Long
    ) {
        try {
            val db = writableDatabase
            val values = ContentValues().apply {
                put("ts_ms", System.currentTimeMillis())
                // YogaPose is a data class (no enum name); use stable id for analytics.
                put("pose", pose.id)
                put("state", state.name)
                put("raw_cue", rawCue)
                put("prompt", prompt)
                put("response", response)
                put("is_fallback", if (isFallback) 1 else 0)
                put("elapsed_ms", elapsedMs)
            }
            db.insert(TABLE_NAME, null, values)
            trimIfNeeded(db)
            Log.d(TAG, "Logged LLM interaction (fallback=$isFallback, elapsedMs=$elapsedMs)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log LLM interaction", e)
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
        private const val TAG = "LlmInteractionDb"
        private const val DATABASE_NAME = "llm_interactions.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_NAME = "llm_interactions"
        private const val MAX_ROWS = 10_000
    }
}
