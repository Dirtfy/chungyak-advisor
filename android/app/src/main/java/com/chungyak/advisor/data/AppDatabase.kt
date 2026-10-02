package com.chungyak.advisor.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// v1 (앱 v0.1.0): notices.
// v2: added 분양가 columns (priceMinManwon/priceMaxManwon) to Notice.
// v3 (앱 v0.2.0): 주택형별 캐시 table (HouseModel) + Notice.modelsFetchedAt.
// v4 (앱 v0.3.0~): 경쟁률 table (Competition) + Notice.cmpet*.
//
// 규칙: 업데이트 시 사용자 데이터 보존 — 파괴적 마이그레이션 금지. 스키마를 바꾸면 version을 올리고
// MIGRATION_n_n+1을 추가해 [MIGRATIONS]에 넣는다(기존 것은 지우지 않는다). 스키마 JSON은
// app/schemas/에 export되어 커밋되며 MigrationTest가 모든 이전 버전 → 최신 경로를 검증한다.
@Database(entities = [Notice::class, HouseModel::class, Competition::class], version = 4, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noticeDao(): NoticeDao
    abstract fun houseModelDao(): HouseModelDao
    abstract fun competitionDao(): CompetitionDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        const val NAME = "chungyak.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `notices` ADD COLUMN `priceMinManwon` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `notices` ADD COLUMN `priceMaxManwon` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `notices` ADD COLUMN `modelsFetchedAt` INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `house_models` (`noticeId` TEXT NOT NULL, " +
                        "`modelNo` TEXT NOT NULL, `houseType` TEXT NOT NULL, `supplyArea` REAL NOT NULL, " +
                        "`units` INTEGER NOT NULL, `priceManwon` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`noticeId`, `modelNo`))"
                )
            }
        }

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

        /** 모든 버전 경로. 새 마이그레이션은 여기에 추가만 한다. */
        val MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)

        fun build(context: Context, name: String = NAME): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, name)
                .addMigrations(*MIGRATIONS)
                .build()

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context).also { instance = it }
            }
    }
}
