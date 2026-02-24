package com.focusflow.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.focusflow.app.data.db.dao.SessionDao
import com.focusflow.app.data.db.dao.TaskDao
import com.focusflow.app.data.db.entities.SessionEntity
import com.focusflow.app.data.db.entities.TaskEntity

/**
 * Room database for FocusFlow.
 *
 * Contract DB schema version: v2.
 *
 * Migration requirement:
 * - v1 -> v2: sessions adds linked_task_id (nullable) and index idx_sessions_linked_task_id.
 */
@Database(
    entities = [
        TaskEntity::class,
        SessionEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao

    abstract fun sessionDao(): SessionDao

    companion object {
        /**
         * v1 -> v2 migration:
         * - ALTER TABLE sessions ADD COLUMN linked_task_id TEXT NULL
         * - CREATE INDEX idx_sessions_linked_task_id ON sessions(linked_task_id)
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN linked_task_id TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_sessions_linked_task_id ON sessions(linked_task_id)")
            }
        }
    }
}
