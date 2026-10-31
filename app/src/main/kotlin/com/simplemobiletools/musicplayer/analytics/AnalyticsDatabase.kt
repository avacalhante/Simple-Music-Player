package com.simplemobiletools.musicplayer.analytics

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.simplemobiletools.musicplayer.analytics.daos.PlayEndDao
import com.simplemobiletools.musicplayer.analytics.daos.PlayStartDao
import com.simplemobiletools.musicplayer.analytics.entities.PlayEnd
import com.simplemobiletools.musicplayer.analytics.entities.PlayStart

@Database(
    entities = [PlayStart::class, PlayEnd::class],
    version = 1,
    exportSchema = true
)
abstract class AnalyticsDatabase : RoomDatabase() {
    abstract fun playStartDao(): PlayStartDao
    abstract fun playEndDao(): PlayEndDao

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
                            .build()
                    }
                }
            }
            return db!!
        }

        fun destroyInstance() {
            db = null
        }
    }
}
