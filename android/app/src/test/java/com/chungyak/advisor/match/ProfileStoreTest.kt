package com.chungyak.advisor.match

import com.chungyak.advisor.api.ApplyHomeClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class ProfileStoreTest {

    @Test fun roundTrip_keepsEveryField() {
        val p = Profile(
            sido = "서울", sigungu = "강남구", residenceSince = "2016-04-01", residenceMonths = 60, householdHead = true, homesOwned = 1,
            everOwned = true, wonWithin5y = true, usedSpecial = true, married = true, marriageYm = "2022-03",
            children = 2, hasNewborn = true, supportsParent = true, account = AccountType.DEPOSIT, accountOpened = "2019-03-15",
            accountMonths = 30, payments = 30, depositManwon = 600, householdSize = 4, incomePct = 120,
            dualIncome = true, realEstateManwon = 20_000, taxYears5 = true, interestSido = setOf("서울", "경기"),
            maxPriceManwon = 120_000, minAreaM2 = 59, maxAreaM2 = 85,
            birthDate = "1990-02-03", homelessSince = "2020-05-06", dependents = 3,
            cashManwon = 30_000, loanLimitManwon = 50_000, monthlyCapManwon = 200, loanRatePct = 3.75, loanYears = 40,
            repayment = Repayment.EQUAL_PRINCIPAL, downPaymentPct = 20,
        )
        assertEquals(p, ProfileStore.decode(JSONObject(ProfileStore.encode(p).toString())))
        assertFalse(ProfileStore.decode(JSONObject()).isSet)
    }

    /** v0.5.0에 저장된 프로필(가입 일자 없음, 기간 직접 입력)은 업데이트 후에도 그대로 읽힌다. */
    @Test fun v050Json_keepsManualMonths() {
        val old = JSONObject(
            """{"sido":"서울","householdHead":true,"homesOwned":0,"account":"COMPREHENSIVE",""" +
                """"accountMonths":30,"payments":30,"depositManwon":300,"interestSido":["서울"]}"""
        )
        val p = ProfileStore.decode(old)
        assertEquals(30, p.accountMonths)
        assertEquals("", p.accountOpened)
        assertEquals(30, p.accountMonthsAt(java.time.LocalDate.parse("2030-01-01"))) // 직접 입력값은 그대로
        assertEquals(setOf("서울"), p.interestSido)
    }

    /** 실제 SharedPreferences 저장 → 새 인스턴스로 복원. */
    @Test fun savesAndRestores_throughPrefs() {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val p = Profile(sido = "경기", account = AccountType.COMPREHENSIVE, accountOpened = "2020-02-29", accountMonths = 5)
        ProfileStore(ctx).profile = p
        assertEquals(p, ProfileStore(ctx).profile)
    }

    /** 온보딩 완료 여부(v0.9.0~): 저장·복원되고, '조건 지우기'로는 지워지지 않는다. */
    @Test fun onboardingDone_survivesClear() {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = ProfileStore(ctx)
        assertFalse(store.onboardingDone)
        store.profile = Profile(sido = "서울")
        store.onboardingDone = true
        assertTrue(ProfileStore(ctx).onboardingDone)
        store.clear()
        assertFalse(ProfileStore(ctx).profile.isSet)
        assertTrue(ProfileStore(ctx).onboardingDone)
    }

    /** 업데이트: v0.8.0까지 조건을 넣어 둔 사용자는 플래그가 없어도 온보딩이 뜨지 않는다. */
    @Test fun existingUser_afterUpdate_noOnboarding() {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        ctx.getSharedPreferences(ProfileStore.FILE, android.content.Context.MODE_PRIVATE).edit()
            .putString("profile_v1", """{"sido":"경기","account":"COMPREHENSIVE","accountOpened":"2019-03-15"}""").commit()
        val store = ProfileStore(ctx)
        assertFalse(com.chungyak.advisor.ui.Onboarding.shouldShow(store.profile, store.onboardingDone))
        assertEquals("2019-03-15", store.profile.accountOpened)
    }

    /** v0.10.0: 예전 '거주 기간(개월)'은 전입일(오늘 − N개월)로 바뀌고, 오늘 판정 기간은 그대로다. */
    @Test fun migrateResidence_monthsToDate() {
        val today = java.time.LocalDate.parse("2026-10-06")
        val m = ProfileStore.migrateResidence(Profile(sido = "서울", residenceMonths = 30), today)
        assertEquals("2024-04-06", m.residenceSince)
        assertEquals(-1, m.residenceMonths)
        assertEquals(30, m.residenceMonthsAt(today))
        assertEquals(32, m.residenceMonthsAt(java.time.LocalDate.parse("2026-12-06"))) // 이후로는 자동으로 늘어난다
        // 전입일이 이미 있거나 기간 미입력이면 그대로
        val set = Profile(residenceSince = "2020-01-01", residenceMonths = 5)
        assertEquals(set, ProfileStore.migrateResidence(set, today))
        assertEquals(Profile(), ProfileStore.migrateResidence(Profile(), today))
    }

    /** 덮어쓰기 설치: v0.9.0이 저장한 JSON(residenceMonths만)을 읽으면 전입일로 바뀌어 저장된다. */
    @Test fun oldResidenceMonths_migratedOnRead() {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        ctx.getSharedPreferences(ProfileStore.FILE, android.content.Context.MODE_PRIVATE).edit()
            .putString("profile_v1", """{"sido":"경기","residenceMonths":12,"accountOpened":"2019-03-15"}""").commit()
        val p = ProfileStore(ctx).profile
        assertEquals(java.time.LocalDate.now().minusMonths(12).toString(), p.residenceSince)
        assertEquals(12, p.residenceMonthsAt(java.time.LocalDate.now()))
        assertEquals("2019-03-15", p.accountOpened)
        assertEquals(p, ProfileStore(ctx).profile) // 두 번째 읽기에서 또 바뀌지 않는다
    }

    /** MDAT_TRGET_AREA_SECD: "N"은 비조정 — 예전엔 비어 있지 않으면 조정으로 봤다. */
    @Test fun adjustmentFlag() {
        assertTrue(ApplyHomeClient.adjustment("Y"))
        assertFalse(ApplyHomeClient.adjustment("N"))
        assertFalse(ApplyHomeClient.adjustment(""))
        assertFalse(ApplyHomeClient.adjustment(" n "))
    }

    /** v0.10.0까지 저장된 프로필(자금 항목 없음) → 자금 미입력 + 기본 가정값. 다른 값은 그대로. */
    @Test fun v0100Json_fundsDefaults() {
        val p = ProfileStore.decode(JSONObject("""{"sido":"경기","homesOwned":0,"maxPriceManwon":90000}"""))
        assertEquals("경기", p.sido)
        assertEquals(90_000, p.maxPriceManwon)
        assertEquals(-1, p.cashManwon)
        assertEquals(-1, p.loanLimitManwon)
        assertEquals(-1, p.monthlyCapManwon)
        assertEquals(Profile.DEFAULT_LOAN_RATE, p.loanRatePct, 0.0)
        assertEquals(Profile.DEFAULT_LOAN_YEARS, p.loanYears)
        assertEquals(Repayment.ANNUITY, p.repayment)
        assertEquals(Profile.DEFAULT_DOWN_PCT, p.downPaymentPct)
        assertFalse(Affordability.isSet(p))
    }
}
