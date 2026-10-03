package com.chungyak.advisor.match

import com.chungyak.advisor.data.HouseModel
import com.chungyak.advisor.match.Eligibility.Verdict
import com.chungyak.advisor.notice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 대표 시나리오별 매칭 판정. 기준: 주택공급에 관한 규칙·청약홈 ([Eligibility.RULES_DATE]). */
class EligibilityTest {

    private fun seoulNotice(regulated: Boolean = true, dtl: String = "민영") = notice("S", noticeDate = "2026-10-01").copy(
        areaName = "서울", address = "서울특별시 강남구 개포동", houseDtl = dtl,
        speculationArea = regulated, adjustmentArea = regulated,
    )

    private fun gyeonggiNotice(dtl: String = "민영") = notice("G", noticeDate = "2026-10-01").copy(
        areaName = "경기", address = "경기도 성남시 수정구", houseDtl = dtl,
    )

    private fun model(
        area: String = "084.9800A", price: Int = 150_000, newlywed: Int = 10, first: Int = 10,
        multi: Int = 5, old: Int = 3, newborn: Int = 0,
    ) = HouseModel(
        "S", "01", area, 112.0, 50, price, spTotal = newlywed + first + multi + old + newborn,
        spMultiChild = multi, spNewlywed = newlywed, spFirstLife = first, spOldParent = old, spNewborn = newborn,
    )

    /** 서울 거주 10년차 무주택 세대주, 통장 5년·예치금 300만원. */
    private val seoulHead = Profile(
        sido = "서울", sigungu = "강남구", residenceMonths = 60, householdHead = true, homesOwned = 0,
        account = AccountType.COMPREHENSIVE, accountMonths = 60, payments = 60, depositManwon = 300,
        householdSize = 2, incomePct = 150,
    )

    private fun track(r: Eligibility.Result, name: String) = r.tracks.first { it.name.startsWith(name) }

    @Test fun homelessHead_seoul_rank1_eligible() {
        val r = Eligibility.evaluate(seoulHead, seoulNotice(), listOf(model()))
        assertEquals(Verdict.ELIGIBLE, r.verdict)
        val g = track(r, "일반공급 1순위")
        assertEquals(Verdict.ELIGIBLE, g.verdict)
        assertTrue(g.reasons.any { it.contains("해당지역 우선") })
        assertTrue(g.reasons.any { it.contains("가점제") })
    }

    @Test fun regulated_notHouseholdHead_onlyRank2() {
        val r = Eligibility.evaluate(seoulHead.copy(householdHead = false), seoulNotice(), listOf(model()))
        val g = track(r, "일반공급")
        assertEquals(Verdict.INELIGIBLE, g.verdict)
        assertTrue(g.reasons.any { it.contains("세대주") })
    }

    @Test fun nonRegulated_notHead_rank1Ok() {
        val p = seoulHead.copy(sido = "경기", sigungu = "성남시", householdHead = false, depositManwon = 200)
        val r = Eligibility.evaluate(p, gyeonggiNotice(), listOf(model()))
        assertEquals(Verdict.ELIGIBLE, track(r, "일반공급 1순위").verdict)
    }

    @Test fun deposit_bySeoulResidence_and_area() {
        assertEquals(300, Eligibility.requiredDeposit("서울", 84.98))
        assertEquals(600, Eligibility.requiredDeposit("서울", 101.9))
        assertEquals(1500, Eligibility.requiredDeposit("서울", 140.0))
        assertEquals(250, Eligibility.requiredDeposit("인천", 84.0))
        assertEquals(200, Eligibility.requiredDeposit("경기", 84.0))
        // 예치금 200만원 서울 거주자는 서울 민영 1순위 불가.
        val r = Eligibility.evaluate(seoulHead.copy(depositManwon = 200), seoulNotice(), listOf(model()))
        assertEquals(Verdict.INELIGIBLE, track(r, "일반공급").verdict)
    }

    @Test fun twoHomeOwner_regulated_ineligible_everything() {
        val r = Eligibility.evaluate(seoulHead.copy(homesOwned = 2), seoulNotice(), listOf(model()))
        assertEquals(Verdict.INELIGIBLE, r.verdict)
        assertTrue(r.tracks.filter { it.name.contains("특공") }.all { it.verdict == Verdict.INELIGIBLE })
    }

    @Test fun homeOwner_publicHousing_ineligible() {
        val r = Eligibility.evaluate(seoulHead.copy(homesOwned = 1), seoulNotice(dtl = "국민"), listOf(model()))
        assertEquals(Verdict.INELIGIBLE, r.verdict)
    }

    @Test fun oneHome_nonRegulated_private_lotteryOnly() {
        val p = seoulHead.copy(sido = "경기", sigungu = "성남시", homesOwned = 1, depositManwon = 200)
        val g = track(Eligibility.evaluate(p, gyeonggiNotice(), listOf(model())), "일반공급 1순위")
        assertEquals(Verdict.ELIGIBLE, g.verdict)
        assertTrue(g.reasons.any { it.contains("추첨제 물량만") })
    }

    @Test fun newlywed_withinSevenYears_incomeOk() {
        val p = seoulHead.copy(married = true, marriageYm = "2022-03", incomePct = 130, dualIncome = false)
        val t = track(Eligibility.evaluate(p, seoulNotice(), listOf(model())), "신혼부부")
        assertEquals(Verdict.ELIGIBLE, t.verdict)
    }

    @Test fun newlywed_overSevenYears_ineligible() {
        val p = seoulHead.copy(married = true, marriageYm = "2018-01", incomePct = 90)
        assertEquals(Verdict.INELIGIBLE, track(Eligibility.evaluate(p, seoulNotice(), listOf(model())), "신혼부부").verdict)
    }

    @Test fun newlywed_incomeOver_lotteryCheck_or_assetFail() {
        val p = seoulHead.copy(married = true, marriageYm = "2024-01", incomePct = 180, dualIncome = true)
        assertEquals(Verdict.CHECK, track(Eligibility.evaluate(p, seoulNotice(), listOf(model())), "신혼부부").verdict)
        val rich = p.copy(realEstateManwon = 50_000)
        assertEquals(Verdict.INELIGIBLE, track(Eligibility.evaluate(rich, seoulNotice(), listOf(model())), "신혼부부").verdict)
    }

    @Test fun newlywed_noUnitsInNotice_ineligible() {
        val p = seoulHead.copy(married = true, marriageYm = "2024-01", incomePct = 90)
        val t = track(Eligibility.evaluate(p, seoulNotice(), listOf(model(newlywed = 0))), "신혼부부")
        assertEquals(Verdict.INELIGIBLE, t.verdict)
        assertTrue(t.reasons.any { it.contains("물량 없음") })
    }

    @Test fun firstLife_eligible_whenNeverOwned_rank1_incomeOk_taxPaid() {
        val p = seoulHead.copy(married = true, marriageYm = "2020-01", incomePct = 120, taxYears5 = true)
        assertEquals(Verdict.ELIGIBLE, track(Eligibility.evaluate(p, seoulNotice(), listOf(model())), "생애최초").verdict)
    }

    @Test fun firstLife_everOwned_ineligible() {
        val p = seoulHead.copy(everOwned = true, incomePct = 100, taxYears5 = true, married = true, marriageYm = "2020-01")
        assertEquals(Verdict.INELIGIBLE, track(Eligibility.evaluate(p, seoulNotice(), listOf(model())), "생애최초").verdict)
    }

    @Test fun firstLife_unknownTax_isCheck_notEligible() {
        val p = seoulHead.copy(married = true, marriageYm = "2020-01", incomePct = 100, taxYears5 = false)
        assertEquals(Verdict.CHECK, track(Eligibility.evaluate(p, seoulNotice(), listOf(model())), "생애최초").verdict)
    }

    @Test fun multiChild_twoKids_eligible_private_noIncomeLimit() {
        val p = seoulHead.copy(children = 2, incomePct = 300)
        assertEquals(Verdict.ELIGIBLE, track(Eligibility.evaluate(p, seoulNotice(), listOf(model())), "다자녀").verdict)
        assertEquals(Verdict.INELIGIBLE, track(Eligibility.evaluate(p.copy(children = 1), seoulNotice(), listOf(model())), "다자녀").verdict)
    }

    @Test fun usedSpecialBefore_blocksAllSpecial() {
        val p = seoulHead.copy(children = 3, usedSpecial = true)
        val r = Eligibility.evaluate(p, seoulNotice(), listOf(model()))
        assertTrue(r.tracks.filter { it.name.contains("특공") }.all { it.verdict == Verdict.INELIGIBLE })
    }

    @Test fun nonMetroResident_ineligible() {
        val r = Eligibility.evaluate(seoulHead.copy(sido = "기타"), seoulNotice(), listOf(model()))
        assertEquals(Verdict.INELIGIBLE, r.verdict)
    }

    @Test fun accountTypeMismatch() {
        val r = Eligibility.evaluate(seoulHead.copy(account = AccountType.SAVINGS), seoulNotice(), listOf(model()))
        assertEquals(Verdict.INELIGIBLE, r.verdict)
        val noAccount = Eligibility.evaluate(seoulHead.copy(account = AccountType.NONE), seoulNotice(), listOf(model()))
        assertEquals(Verdict.INELIGIBLE, noAccount.verdict)
    }

    @Test fun missingInputs_areCheck_notAsserted() {
        val p = Profile(sido = "서울", account = AccountType.COMPREHENSIVE, householdHead = true)
        val r = Eligibility.evaluate(p, seoulNotice(), listOf(model()))
        assertEquals(Verdict.CHECK, r.verdict)
        assertTrue(track(r, "일반공급").reasons.any { it.startsWith("?") })
    }

    @Test fun unknownHouseKind_neverEligible() {
        val r = Eligibility.evaluate(seoulHead, seoulNotice(dtl = ""), listOf(model()))
        assertEquals(Verdict.CHECK, r.verdict)
    }

    @Test fun modelsNotYetFetched_specialIsCheck() {
        val p = seoulHead.copy(children = 2)
        val r = Eligibility.evaluate(p, seoulNotice(), emptyList())
        assertEquals(Verdict.CHECK, track(r, "다자녀").verdict)
        assertEquals(Verdict.ELIGIBLE, track(r, "일반공급 1순위").verdict)
    }

    @Test fun interestFilters() {
        val p = seoulHead.copy(interestSido = setOf("경기"))
        val r = Eligibility.evaluate(p, seoulNotice(), listOf(model()))
        assertEquals(Verdict.INELIGIBLE, r.verdict)
        assertNotNull(r.filteredOut)
        val cheap = seoulHead.copy(maxPriceManwon = 100_000)
        assertNotNull(Eligibility.evaluate(cheap, seoulNotice(), listOf(model(price = 150_000))).filteredOut)
        val small = seoulHead.copy(maxAreaM2 = 60)
        assertNotNull(Eligibility.evaluate(small, seoulNotice(), listOf(model(area = "084.9800A"))).filteredOut)
    }

    @Test fun regulated_shortResidence_noted() {
        val g = track(Eligibility.evaluate(seoulHead.copy(residenceMonths = 6), seoulNotice(), listOf(model())), "일반공급 1순위")
        assertTrue(g.reasons.any { it.contains("2년") })
    }

    @Test fun gyeonggi_otherCity_isOtherArea() {
        val p = seoulHead.copy(sido = "경기", sigungu = "수원시", depositManwon = 200, householdHead = false)
        val g = track(Eligibility.evaluate(p, gyeonggiNotice(), listOf(model())), "일반공급 1순위")
        assertTrue(g.reasons.any { it.contains("경기 기타 시·군") })
    }

    @Test fun shouldNotify_modes() {
        val r = Eligibility.evaluate(seoulHead.copy(householdHead = false), seoulNotice(), listOf(model()))
        assertTrue(Eligibility.shouldNotify(Profile(), NotifyMode.ELIGIBLE, null)) // 프로필 없음 → 전체 알림
        assertTrue(Eligibility.shouldNotify(seoulHead, NotifyMode.ALL, r))
        val ok = Eligibility.evaluate(seoulHead, seoulNotice(), listOf(model()))
        assertTrue(Eligibility.shouldNotify(seoulHead, NotifyMode.ELIGIBLE, ok))
        val bad = Eligibility.evaluate(seoulHead.copy(homesOwned = 3), seoulNotice(), listOf(model()))
        assertFalse(Eligibility.shouldNotify(seoulHead, NotifyMode.ELIGIBLE_OR_CHECK, bad))
    }

    @Test fun accountOpenedDate_recomputedAtEachNoticeDate() {
        // 2024-11-20 가입: 2026-10-01 공고 기준 22개월 → 규제지역 1순위(24개월) 미달, 2026-11-20 이후 공고면 충족.
        val p = seoulHead.copy(accountOpened = "2024-11-20", accountMonths = 60)
        val early = Eligibility.evaluate(p, seoulNotice(), listOf(model()))
        assertEquals(Verdict.INELIGIBLE, track(early, "일반공급").verdict)
        assertTrue(track(early, "일반공급").reasons.any { it.contains("22개월") })
        assertTrue(early.notes.any { it.contains("1년 10개월 (22개월)") })
        val later = Eligibility.evaluate(p, seoulNotice().copy(noticeDate = "2026-11-20"), listOf(model()))
        assertEquals(Verdict.ELIGIBLE, track(later, "일반공급 1순위").verdict)
    }

    @Test fun marriageMonths() {
        assertEquals(55, Eligibility.marriageMonths("2022-03", "2026-10-01"))
        assertEquals(null, Eligibility.marriageMonths("", "2026-10-01"))
    }
}
