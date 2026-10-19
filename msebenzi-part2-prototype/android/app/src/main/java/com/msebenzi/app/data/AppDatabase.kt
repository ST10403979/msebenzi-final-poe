package com.msebenzi.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Local offline-first database (Room over SQLite). The job feed reads from here first so
 * the app is usable without connectivity, then refreshes from the network when it is
 * available (see JobRepository). Pending offline actions live in the same database.
 *
 * Reference: Android Developers (2026) Save data in a local database using Room.
 */
@Database(entities = [Job::class, SyncAction::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun jobDao(): JobDao
    abstract fun syncActionDao(): SyncActionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "msebenzi.db"
                )
                    // v1 (prototype) only held a disposable cache of the feed, so rebuilding is safe.
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
