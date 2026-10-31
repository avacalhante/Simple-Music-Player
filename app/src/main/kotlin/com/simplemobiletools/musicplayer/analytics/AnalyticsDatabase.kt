package com.simplemobiletools.musicplayer.analytics

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.simplemobiletools.musicplayer.analytics.daos.ListeningSessionDao
import com.simplemobiletools.musicplayer.analytics.daos.PlayEndDao
import com.simplemobiletools.musicplayer.analytics.daos.PlayStartDao
import com.simplemobiletools.musicplayer.analytics.entities.ListeningSession
import com.simplemobiletools.musicplayer.analytics.entities.PlayEnd
import com.simplemobiletools.musicplayer.analytics.entities.PlayStart

@Database(
    entities = [PlayStart::class, PlayEnd::class, ListeningSession::class],
    version = 2,
    exportSchema = true
)
abstract class AnalyticsDatabase : RoomDatabase() {
    abstract fun playStartDao(): PlayStartDao
    abstract fun playEndDao(): PlayEndDao
    abstract fun listeningSessionDao(): ListeningSessionDao

    companion object {
        private const val DATABASE_NAME = "analytics.db"
        @Volatile
        private var db: AnalyticsDatabase? = null

        fun getInstance(context: Context): AnalyticsDatabase {
            if (db == null) {
                synchronized(this) {
                    if (db == null) {
                        db = Room.databaseBuilder(
                            context.applicationContext,
                            AnalyticsDatabase::class.java,
                            DATABASE_NAME
                        )
                            .setQueryExecutor(AnalyticsExecutor.myExecutor)
                            .setTransactionExecutor(AnalyticsExecutor.myExecutor)
                            .addMigrations(MIGRATION_1_2)
                            .build()
                    }
                }
            }
            return db!!
        }

        fun destroyInstance() {
            db = null
        }

        /** Adds the session table. Existing plays keep session_id 0 and are simply not part of any session. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `listening_sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`started_at` INTEGER NOT NULL, `ended_at` INTEGER NOT NULL)"
                )
            }
        }
    }
}
