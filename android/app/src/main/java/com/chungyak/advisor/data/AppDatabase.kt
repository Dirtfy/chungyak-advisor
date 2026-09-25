package com.chungyak.advisor.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// v2: added 분양가 columns (priceMinManwon/priceMaxManwon) to Notice.
@Database(entities = [Notice::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noticeDao(): NoticeDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "chungyak.db",
                )
                    // Collected 공고 are re-fetchable from the API, so a schema
                    // bump can safely drop and rebuild rather than migrate.
                    .fallbackToDestructiveMigration()
                    .build().also { instance = it }
            }
    }
}
