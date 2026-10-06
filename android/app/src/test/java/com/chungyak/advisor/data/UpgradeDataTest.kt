package com.chungyak.advisor.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 업데이트 시 데이터 보존 검증. 실제로 배포된 각 버전의 DB 스키마(v1=앱 0.1.0, v3=0.2.0, v4=0.3.0~)
 * 로 DB 파일을 만들고 데이터를 넣은 뒤, 현재 앱의 [AppDatabase]로 열어 데이터가 그대로인지 본다.
 * Room은 열 때 스키마를 검증하므로 마이그레이션이 틀리면 여기서 예외가 난다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class) // WorkManager 스케줄링 없이
class UpgradeDataTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val name = "upgrade-test.db"

    @Before @After fun clean() {
        ctx.deleteDatabase(name)
    }

    private val v1Notices = "CREATE TABLE IF NOT EXISTS `notices` (`id` TEXT NOT NULL, " +
        "`houseManageNo` TEXT NOT NULL, `pblancNo` TEXT NOT NULL, `name` TEXT NOT NULL, " +
        "`areaName` TEXT NOT NULL, `address` TEXT NOT NULL, `totalUnits` INTEGER NOT NULL, " +
        "`noticeDate` TEXT NOT NULL, `rank1Start` TEXT NOT NULL, `rank1End` TEXT NOT NULL, " +
        "`resultDate` TEXT NOT NULL, `houseKind` TEXT NOT NULL, `speculationArea` INTEGER NOT NULL, " +
        "`adjustmentArea` INTEGER NOT NULL, `url` TEXT NOT NULL, `homepage` TEXT NOT NULL, " +
        "`firstSeen` INTEGER NOT NULL, `notified` INTEGER NOT NULL, PRIMARY KEY(`id`))"

    /** 지정한 과거 버전 스키마로 DB를 만들고 [seed]로 데이터를 넣는다. */
    private fun createOld(version: Int, ddl: List<String>, seed: (SupportSQLiteDatabase) -> Unit) {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(ctx).name(name).callback(
                object : SupportSQLiteOpenHelper.Callback(version) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        ddl.forEach(db::execSQL)
                        seed(db)
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                }
            ).build()
        )
        helper.writableDatabase.close()
        helper.close()
    }

    private fun insertV1(db: SupportSQLiteDatabase, id: String, notified: Int) = db.execSQL(
        "INSERT INTO notices VALUES ('$id','H$id','P$id','단지$id','경기','주소',100,'2026-09-01'," +
            "'2026-09-10','2026-09-11','2026-09-20','APT',0,1,'u','h',123,$notified)"
    )

    private fun openCurrent(): AppDatabase = AppDatabase.build(ctx, name)

    @Test fun v1_app010_upgradesWithDataIntact() = runBlocking {
        createOld(1, listOf(v1Notices)) { insertV1(it, "a", 1); insertV1(it, "b", 0) }
        val db = openCurrent()
        val rows = db.noticeDao().all().sortedBy { it.id }
        assertEquals(listOf("a", "b"), rows.map { it.id })
        assertEquals(listOf(true, false), rows.map { it.notified })
        assertEquals("단지a", rows[0].name)
        assertTrue(rows[0].adjustmentArea)
        assertEquals(0, rows[0].priceMaxManwon)        // 새 컬럼은 기본값
        assertEquals(0L, rows[0].modelsFetchedAt)
        db.close()
    }

    @Test fun v3_app020_upgradesWithPriceCacheIntact() = runBlocking {
        val v2 = AppDatabase.MIGRATION_1_2; val v3 = AppDatabase.MIGRATION_2_3
        // v1 스키마 위에 1→2, 2→3 SQL을 적용한 것이 0.2.0이 실제로 만든 v3 스키마와 같다.
        createOld(3, listOf(v1Notices)) { db ->
            insertV1(db, "a", 1)
            v2.migrate(db); v3.migrate(db)
            db.execSQL("UPDATE notices SET priceMinManwon=87700, priceMaxManwon=119800, modelsFetchedAt=5")
            db.execSQL("INSERT INTO house_models VALUES ('a','01','059.9742A',78.5038,10,87900)")
        }
        val db = openCurrent()
        val n = db.noticeDao().all().single()
        assertEquals(87700, n.priceMinManwon)
        assertEquals(119800, n.priceMaxManwon)
        assertEquals(0L, n.modelsFetchedAt) // v5: 특공 세대수를 받으려고 주택형을 다시 받는다(가격은 유지)
        assertEquals(87900, db.houseModelDao().all().single().priceManwon)
        assertEquals(0.0, n.cmpetMaxRate, 0.0)
        db.close()
    }

    @Test fun v4_app030to044_upgradesTo5_keepsDataAndRefetchesModels() = runBlocking {
        createOld(4, listOf(v1Notices)) { db ->
            insertV1(db, "a", 1); insertV1(db, "b", 0)
            AppDatabase.MIGRATION_1_2.migrate(db); AppDatabase.MIGRATION_2_3.migrate(db); AppDatabase.MIGRATION_3_4.migrate(db)
            db.execSQL("UPDATE notices SET priceMinManwon=87700, priceMaxManwon=119800, modelsFetchedAt=5, cmpetMaxRate=12.5, cmpetFinal=1")
            db.execSQL("INSERT INTO house_models VALUES ('a','01','059.9742A',78.5038,10,87900)")
            db.execSQL("INSERT INTO competitions VALUES ('a','01','059.9742A',1,'01','해당지역',10,125,12.5,'12.50',0)")
        }
        val db = openCurrent()
        val rows = db.noticeDao().all().sortedBy { it.id }
        assertEquals(listOf(true, false), rows.map { it.notified })
        assertEquals(119800, rows[0].priceMaxManwon)
        assertEquals(12.5, rows[0].cmpetMaxRate, 0.0)
        assertTrue(rows[0].cmpetFinal)
        assertEquals("", rows[0].houseDtl)
        assertEquals(0L, rows[0].modelsFetchedAt)
        val m = db.houseModelDao().all().single()
        assertEquals(87900, m.priceManwon)
        assertEquals(0, m.spNewlywed)
        assertEquals(59.9742, m.exclusiveArea, 1e-9)
        assertEquals(125, db.competitionDao().all().single().requests)
        db.close()
    }

    @Test fun v5_app050to090_upgradesTo6_keepsDataAndFillsBuilderOnPoll() = runBlocking {
        createOld(5, listOf(v1Notices)) { db ->
            insertV1(db, "a", 1)
            AppDatabase.MIGRATION_1_2.migrate(db); AppDatabase.MIGRATION_2_3.migrate(db)
            AppDatabase.MIGRATION_3_4.migrate(db); AppDatabase.MIGRATION_4_5.migrate(db)
            db.execSQL("UPDATE notices SET houseDtl='민영', priceMaxManwon=119800, modelsFetchedAt=5")
        }
        val db = openCurrent()
        val n = db.noticeDao().all().single()
        assertTrue(n.notified)
        assertEquals("민영", n.houseDtl)
        assertEquals(119800, n.priceMaxManwon)
        assertEquals(5L, n.modelsFetchedAt) // v6는 주택형을 다시 받지 않는다
        assertEquals("", n.builder)
        assertEquals("", n.contractor)
        // 다음 폴링: 이미 있는 공고에도 사업주체·시공사를 채운다(알림 여부는 그대로).
        db.noticeDao().updateMeta("a", "민영", false, true, "OO도시개발", "XX건설")
        val u = db.noticeDao().all().single()
        assertEquals("OO도시개발", u.builder)
        assertEquals("XX건설", u.contractor)
        assertTrue(u.notified)
        db.close()
    }

    @Test fun v4_sameVersion_reopenKeepsEverything() = runBlocking {
        // 0.3.0/0.4.0 → 0.4.1: 스키마 동일. 현재 앱으로 만들고 닫았다 다시 열어도 그대로.
        var db = openCurrent()
        db.noticeDao().upsertAll(listOf(sample("x")))
        db.competitionDao().insertAll(listOf(Competition("x", "01", "084", 1, "01", "해당지역", 10, 120, 12.0, "12.00", false)))
        db.close()
        db = openCurrent()
        assertEquals(sample("x"), db.noticeDao().all().single())
        assertEquals(120, db.competitionDao().all().single().requests)
        db.close()
    }

    @Test fun backup_roundTrip_restoresSettingsAndData() = runBlocking {
        val s = Settings(ctx)
        s.serviceKey = "MY-KEY%2B"
        s.lookbackDays = 90
        s.regions = setOf("서울", "인천")
        s.sortOrder = "PRICE_ASC"
        val db = AppDatabase.get(ctx)
        db.noticeDao().upsertAll(listOf(sample("p"), sample("q").copy(notified = false)))
        db.houseModelDao().insertAll(listOf(HouseModel("p", "01", "084A", 109.18, 36, 118500)))
        val file = Backup.export(ctx)

        // 재설치 상황: 데이터·설정 모두 비움.
        db.houseModelDao().clear(); db.competitionDao().clear(); db.noticeDao().clear()
        ctx.getSharedPreferences("chungyak_prefs", Context.MODE_PRIVATE).edit().clear().commit()

        val r = Backup.import(ctx, file)
        assertEquals(2, r.notices)
        val s2 = Settings(ctx)
        assertEquals("MY-KEY%2B", s2.serviceKey)
        assertEquals(90, s2.lookbackDays)
        assertEquals(setOf("서울", "인천"), s2.regions)
        assertEquals("PRICE_ASC", s2.sortOrder)
        assertTrue(s2.baselineDone)
        assertEquals(listOf(sample("p"), sample("q").copy(notified = false)), db.noticeDao().all().sortedBy { it.id })
        assertEquals(118500, db.houseModelDao().all().single().priceManwon)
    }

    @Test fun prefsMigration_keepsExistingSettings() {
        val p = ctx.getSharedPreferences("chungyak_prefs", Context.MODE_PRIVATE)
        p.edit().clear().putString("service_key", "OLD").putLong("last_check", 1L).putString("sort_order", "CMPET_DESC").commit()
        Settings(ctx).migrate()
        val s = Settings(ctx)
        assertEquals("OLD", s.serviceKey)
        assertEquals("CMPET_DESC", s.sortOrder)
        assertTrue(s.baselineDone)
        assertEquals(Settings.PREFS_VERSION, p.getInt("prefs_version", 0))
    }

    private fun sample(id: String) = Notice(
        id = id, houseManageNo = "H", pblancNo = "P", name = "단지$id", areaName = "서울", address = "a",
        totalUnits = 10, noticeDate = "2026-09-30", rank1Start = "2026-10-10", rank1End = "2026-10-11",
        resultDate = "2026-10-20", houseKind = "APT", speculationArea = true, adjustmentArea = false,
        url = "u", homepage = "h", priceMinManwon = 50000, priceMaxManwon = 60000, modelsFetchedAt = 7,
        cmpetMaxRate = 3.5, cmpetAvgRate = 1.2, cmpetFetchedAt = 8, cmpetFinal = true,
        builder = "OO도시개발", contractor = "XX건설", firstSeen = 9, notified = true,
    )
}
