package com.focusflow.app

import android.app.Application
import androidx.room.Room
import com.focusflow.app.data.db.AppDatabase
import com.focusflow.app.data.prefs.SettingsDataStore
import com.focusflow.app.notifications.NotificationChannels

class FocusFlowApp : Application() {

    override fun onCreate() {
        super.onCreate()
        AppServices.init(this)
        NotificationChannels.ensureCreated(this)
    }
}

/**
 * Minimal service locator (offline-first).
 *
 * Note: This app does not require backend availability for core features.
 * The only backend contract endpoint currently is GET /health returning {"status":"ok"}.
 */
object AppServices {
    @Volatile
    private var initialized: Boolean = false

    lateinit var db: AppDatabase
        private set

    lateinit var settings: SettingsDataStore
        private set

    fun init(app: Application) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return

            db = Room.databaseBuilder(app, AppDatabase::class.java, "focusflow.db")
                .addMigrations(AppDatabase.MIGRATION_1_2)
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()

            settings = SettingsDataStore(app)

            initialized = true
        }
    }
}
