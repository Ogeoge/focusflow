package com.focusflow.app.data.db

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.focusflow.app.data.db.entities.SessionEntity
import com.focusflow.app.data.db.entities.TaskEntity
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DaoTests {

    private lateinit var context: Context
    private lateinit var db: AppDatabase

    @get:Rule
    val migrationHelper = MigrationTestHelper(
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        listOf(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun taskDao_insertAndObserveAndUpdate() {
        val taskDao = db.taskDao()
        val now = System.currentTimeMillis().coerceAtLeast(1L)

        val t1 = TaskEntity(
            id = "11111111-1111-1111-1111-111111111111",
            title = "Write spec",
            notes = "Keep it short",
            estimatePomodoros = 3,
            completedPomodoros = 0,
            isCompleted = false,
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
        )

        taskDao.insert(t1)

        val loaded = taskDao.getById(t1.id)
        assertNotNull(loaded)
        assertEquals("Write spec", loaded!!.title)
        assertEquals(3, loaded.estimatePomodoros)

        val later = now + 1000
        taskDao.incrementCompletedPomodoros(id = t1.id, delta = 2, nowEpochMs = later)
        taskDao.setCompleted(id = t1.id, isCompleted = true, nowEpochMs = later + 1)

        val updated = taskDao.getById(t1.id)!!
        assertEquals(2, updated.completedPomodoros)
        assertTrue(updated.isCompleted)

        taskDao.deleteById(t1.id)
        val deleted = taskDao.getById(t1.id)
        assertNull(deleted)
    }

    @Test
    fun sessionDao_insertAndQueryRange_andLinkedTaskQuery() {
        val taskDao = db.taskDao()
        val sessionDao = db.sessionDao()
        val base = System.currentTimeMillis().coerceAtLeast(1L)

        val task = TaskEntity(
            id = "22222222-2222-2222-2222-222222222222",
            title = "Deep work",
            notes = null,
            estimatePomodoros = 1,
            completedPomodoros = 0,
            isCompleted = false,
            createdAtEpochMs = base,
            updatedAtEpochMs = base,
        )
        taskDao.insert(task)

        // Contract: type must be one of work|break|long_break and duration_ms=end-start and >0
        val s1Start = base + 10_000
        val s1End = s1Start + 25 * 60 * 1000L
        val s1 = SessionEntity(
            id = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
            type = "work",
            startEpochMs = s1Start,
            endEpochMs = s1End,
            durationMs = s1End - s1Start,
            linkedTaskId = task.id,
            planLabel = "Classic",
        )

        val s2Start = base + 50_000
        val s2End = s2Start + 5 * 60 * 1000L
        val s2 = SessionEntity(
            id = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
            type = "break",
            startEpochMs = s2Start,
            endEpochMs = s2End,
            durationMs = s2End - s2Start,
            linkedTaskId = null,
            planLabel = "Classic",
        )

        sessionDao.insertAll(listOf(s1, s2))

        val inRange = sessionDao.getInStartRange(
            startEpochMsInclusive = base,
            endEpochMsExclusive = base + 100_000,
        )
        assertEquals(2, inRange.size)
        assertEquals("work", inRange[0].type)
        assertEquals("break", inRange[1].type)

        val byType = sessionDao.getByType("work")
        assertEquals(1, byType.size)
        assertEquals(s1.id, byType.first().id)

        val byTask = sessionDao.getByLinkedTaskId(task.id)
        assertEquals(1, byTask.size)
        assertEquals(s1.id, byTask.first().id)
    }

    @Test
    fun migration_1_2_addsLinkedTaskIdColumn_andIndex() {
        val dbName = "migration-test"

        // Create schema at version 1.
        var v1Db: SupportSQLiteDatabase = migrationHelper.createDatabase(dbName, 1)

        // Minimal v1 schema as per contract notes:
        v1Db.execSQL(
            "CREATE TABLE IF NOT EXISTS tasks (" +
                "id TEXT PRIMARY KEY NOT NULL," +
                "title TEXT NOT NULL," +
                "notes TEXT," +
                "estimate_pomodoros INTEGER NOT NULL DEFAULT 0," +
                "completed_pomodoros INTEGER NOT NULL DEFAULT 0," +
                "is_completed INTEGER NOT NULL DEFAULT 0," +
                "created_at_epoch_ms INTEGER NOT NULL," +
                "updated_at_epoch_ms INTEGER NOT NULL" +
                ")"
        )
        v1Db.execSQL("CREATE INDEX IF NOT EXISTS idx_tasks_is_completed ON tasks(is_completed)")

        // v1 sessions WITHOUT linked_task_id; plan_label already exists in v2 DDL and can exist in v1 too.
        v1Db.execSQL(
            "CREATE TABLE IF NOT EXISTS sessions (" +
                "id TEXT PRIMARY KEY NOT NULL," +
                "type TEXT NOT NULL CHECK (type IN ('work','break','long_break'))," +
                "start_epoch_ms INTEGER NOT NULL," +
                "end_epoch_ms INTEGER NOT NULL," +
                "duration_ms INTEGER NOT NULL," +
                "plan_label TEXT NULL" +
                ")"
        )
        v1Db.execSQL("CREATE INDEX IF NOT EXISTS idx_sessions_start_epoch_ms ON sessions(start_epoch_ms)")
        v1Db.execSQL("CREATE INDEX IF NOT EXISTS idx_sessions_type ON sessions(type)")

        // Insert a row to verify data survives.
        v1Db.execSQL(
            "INSERT INTO sessions(id,type,start_epoch_ms,end_epoch_ms,duration_ms,plan_label) VALUES(" +
                "'cccccccc-cccc-cccc-cccc-cccccccccccc','work',1000,2000,1000,'Classic'" +
                ")"
        )

        v1Db.close()

        // Run migrations and validate.
        val migratedDb = migrationHelper.runMigrationsAndValidate(
            dbName,
            2,
            true,
            AppDatabase.MIGRATION_1_2,
        )

        // Verify new column exists by querying it.
        val cursor = migratedDb.query(
            "SELECT id, linked_task_id FROM sessions WHERE id='cccccccc-cccc-cccc-cccc-cccccccccccc'"
        )
        assertTrue(cursor.moveToFirst())
        val linkedTaskId = cursor.getString(1)
        assertNull(linkedTaskId)
        cursor.close()

        // Verify index exists.
        val indexCursor = migratedDb.query(
            "SELECT name FROM sqlite_master WHERE type='index' AND name='idx_sessions_linked_task_id'"
        )
        assertTrue(indexCursor.moveToFirst())
        assertEquals("idx_sessions_linked_task_id", indexCursor.getString(0))
        indexCursor.close()
    }
}
