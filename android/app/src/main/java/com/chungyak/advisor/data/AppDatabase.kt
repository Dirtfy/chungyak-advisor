package com.chungyak.advisor.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// v2: added 분양가 columns (priceMinManwon/priceMaxManwon) to Notice.
// v3: 주택형별 캐시 table (HouseModel) + Notice.modelsFetchedAt.
// v4: 경쟁률 table (Competition) + Notice.cmpet* — 실제 마이그레이션(목록 보존).
@Database(entities = [Notice::class, HouseModel::class, Competition::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noticeDao(): NoticeDao
    abstract fun houseModelDao(): HouseModelDao
    abstract fun competitionDao(): CompetitionDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        /** v3 → v4: SQL은 Room이 생성한 스키마(AppDatabase_Impl)와 동일하게 맞춤. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `notices` ADD COLUMN `cmpetMaxRate` REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `notices` ADD COLUMN `cmpetAvgRate` REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `notices` ADD COLUMN `cmpetFetchedAt` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `notices` ADD COLUMN `cmpetFinal` INTEGER NOT NULL DEFAULT 0")
                db.execSQL(COMPETITIONS_DDL)
            }
        }

        const val COMPETITIONS_DDL = "CREATE TABLE IF NOT EXISTS `competitions` (" +
            "`noticeId` TEXT NOT NULL, `modelNo` TEXT NOT NULL, `houseType` TEXT NOT NULL, " +
            "`rank` INTEGER NOT NULL, `resideCode` TEXT NOT NULL, `resideName` TEXT NOT NULL, " +
            "`units` INTEGER NOT NULL, `requests` INTEGER NOT NULL, `rate` REAL NOT NULL, " +
            "`rateText` TEXT NOT NULL, `shortfall` INTEGER NOT NULL, " +
            "PRIMARY KEY(`noticeId`, `modelNo`, `rank`, `resideCode`))"

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "chungyak.db",
                )
                    .addMigrations(MIGRATION_3_4)
                    // v1/v2 → 재수집 가능한 데이터라 파괴적 재생성으로 충분.
                    .fallbackToDestructiveMigration()
                    .build().also { instance = it }
            }
    }
}
