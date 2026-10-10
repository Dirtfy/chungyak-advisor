package com.chungyak.advisor.ui

import com.chungyak.advisor.match.AccountType
import com.chungyak.advisor.match.NotifyMode
import com.chungyak.advisor.match.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** '내 조건' 탭과 온보딩이 함께 쓰는 입력 모델(v0.9.0~). */
class ProfileDraftTest {

    private val today = LocalDate.parse("2026-10-06")

    @Test fun ofThenBuild_keepsProfile() {
        val p = Profile(
            sido = "서울", residenceMonths = 24, homesOwned = 0, children = 2, account = AccountType.COMPREHENSIVE,
            accountOpened = "2019-03-15", payments = 80, depositManwon = 900, householdSize = 4, incomePct = 110,
            maxPriceManwon = 90_000, minAreaM2 = 59, maxAreaM2 = 85, dependents = 3,
        )
        assertEquals(p, ProfileDraft.of(p, NotifyMode.ELIGIBLE).build())
    }

    @Test fun emptyNumbers_meanUnset() {
        val d = ProfileDraft.of(Profile(sido = "경기"), NotifyMode.ALL)
        assertEquals("", d.num("payments")) // -1 → 빈칸
        val b = d.withNum("payments", "").withNum("homesOwned", "").build()
        assertEquals(-1, b.payments)
        assertEquals(0, b.homesOwned) // 주택 수·자녀 수는 빈칸이면 0
    }

    @Test fun withNum_keepsDigitsOnly() {
        val d = ProfileDraft.of(Profile(), NotifyMode.ALL).withNum("depositManwon", "1,500만원")
        assertEquals("1500", d.num("depositManwon"))
        assertEquals(1500, d.build().depositManwon)
    }

    @Test fun problems_taggedBySection() {
        val d = ProfileDraft.of(
            Profile(sido = "", married = true, marriageYm = "2022/05", accountOpened = "2027-01-01"),
            NotifyMode.ALL,
        ).withNum("dependents", "25")
        assertEquals(
            listOf(FormSection.RESIDENCE, FormSection.FAMILY, FormSection.ACCOUNT, FormSection.GAJEOM),
            d.problems(today).map { it.section },
        )
    }

    @Test fun residenceSince_futureOrBad_isProblem() {
        fun probs(since: String) = ProfileDraft.of(Profile(sido = "서울", residenceSince = since), NotifyMode.ALL).problems(today)
        assertEquals(listOf(FormSection.RESIDENCE), probs("2026-10-07").map { it.section })
        assertEquals(listOf(FormSection.RESIDENCE), probs("2026-13-01").map { it.section })
        assertTrue(probs("2026-10-06").isEmpty())
        assertTrue(probs("").isEmpty())
    }

    @Test fun validProfile_hasNoProblems() {
        val d = ProfileDraft.of(Profile(sido = "인천", married = true, marriageYm = "2022-5", accountOpened = "2026-10-06"), NotifyMode.ALL)
        assertTrue(d.problems(today).isEmpty())
    }

    @Test fun numKeysOfSteps_coverAllNumberFields() {
        // 자금(FUNDS)은 온보딩 단계가 아니라 전체 묶음으로 확인한다.
        assertEquals(ProfileDraft.NUM_KEYS.toSet(), FormSection.entries.flatMap(Onboarding::numKeys).toSet())
        assertEquals(ProfileDraft.NUM_KEYS.toSet(), ProfileDraft.of(Profile(), NotifyMode.ALL).nums.keys)
    }

    @Test fun funds_numbersAndDecimalRate() {
        val d = ProfileDraft.of(Profile(sido = "서울"), NotifyMode.ALL)
        assertEquals("4", d.num("loanRatePct"))
        assertEquals("30", d.num("loanYears"))
        assertEquals("", d.num("cashManwon"))
        val e = d.withNum("cashManwon", "30,000").withDecimal("loanRatePct", "3.5.2a").withNum("monthlyCapManwon", "200")
        val b = e.build()
        assertEquals(30_000, b.cashManwon)
        assertEquals(3.52, b.loanRatePct, 1e-9)
        assertEquals(200, b.monthlyCapManwon)
        assertEquals(-1, b.loanLimitManwon)
        // 비운 가정값은 기본값
        assertEquals(Profile.DEFAULT_LOAN_YEARS, e.withNum("loanYears", "").build().loanYears)
        assertEquals(Profile.DEFAULT_LOAN_RATE, e.withDecimal("loanRatePct", "").build().loanRatePct, 0.0)
    }

    @Test fun funds_badAssumptions_areProblems() {
        val d = ProfileDraft.of(Profile(sido = "서울"), NotifyMode.ALL)
        fun probs(f: (ProfileDraft) -> ProfileDraft) = f(d).problems(today).map { it.section }
        assertEquals(listOf(FormSection.FUNDS), probs { it.withNum("loanYears", "0") })
        assertEquals(listOf(FormSection.FUNDS), probs { it.withDecimal("loanRatePct", "45") })
        assertEquals(listOf(FormSection.FUNDS), probs { it.withNum("downPaymentPct", "120") })
        assertTrue(probs { it.withNum("cashManwon", "30000") }.isEmpty())
    }
}
