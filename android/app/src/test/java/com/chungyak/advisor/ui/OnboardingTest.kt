package com.chungyak.advisor.ui

import com.chungyak.advisor.match.AccountType
import com.chungyak.advisor.match.NotifyMode
import com.chungyak.advisor.match.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 첫 실행 판정·단계 순서·건너뛰기(v0.9.0~). */
class OnboardingTest {

    @Test fun shownOnlyOnFirstRun() {
        assertTrue(Onboarding.shouldShow(Profile(), done = false))      // 새 사용자
        assertFalse(Onboarding.shouldShow(Profile(), done = true))      // 끝냈거나 '나중에 하기'
        assertFalse(Onboarding.shouldShow(Profile(sido = "서울"), done = false)) // 이미 조건을 넣은 기존 사용자
    }

    @Test fun stepOrder() {
        assertEquals(
            listOf(
                FormSection.RESIDENCE, FormSection.HOUSEHOLD, FormSection.FAMILY, FormSection.ACCOUNT,
                FormSection.INCOME, FormSection.GAJEOM, FormSection.INTEREST, FormSection.NOTIFY,
            ),
            Onboarding.steps,
        )
        assertTrue(Onboarding.steps.all { it.help.isNotBlank() })
    }

    @Test fun problems_onlyForCurrentStep() {
        val today = LocalDate.parse("2026-10-06")
        val d = ProfileDraft.of(Profile(sido = "", accountOpened = "2030-01-01"), NotifyMode.ALL)
        assertEquals(listOf(FormSection.RESIDENCE), Onboarding.problems(d, FormSection.RESIDENCE, today).map { it.section })
        assertEquals(listOf(FormSection.ACCOUNT), Onboarding.problems(d, FormSection.ACCOUNT, today).map { it.section })
        assertTrue(Onboarding.problems(d, FormSection.HOUSEHOLD, today).isEmpty())
    }

    @Test fun skip_revertsOnlyThisStep() {
        val start = ProfileDraft.of(Profile(sido = "서울", householdHead = true), NotifyMode.ELIGIBLE_OR_CHECK)
        val edited = start
            .edit { it.copy(account = AccountType.COMPREHENSIVE, accountOpened = "2020-01-01", householdHead = false) }
            .withNum("payments", "40").withNum("depositManwon", "300").withNum("children", "1")
            .copy(mode = NotifyMode.ALL)
        val skipped = Onboarding.skip(edited, start, FormSection.ACCOUNT)
        val b = skipped.build()
        // 통장 단계 값은 되돌림
        assertEquals(AccountType.NONE, b.account)
        assertEquals("", b.accountOpened)
        assertEquals(-1, b.payments)
        assertEquals(-1, b.depositManwon)
        // 다른 단계 값은 그대로
        assertFalse(b.householdHead)
        assertEquals(1, b.children)
        assertEquals(NotifyMode.ALL, skipped.mode)
        // 알림 단계 건너뛰기는 알림 범위만 되돌림
        assertEquals(NotifyMode.ELIGIBLE_OR_CHECK, Onboarding.skip(edited, start, FormSection.NOTIFY).mode)
    }

    @Test fun skipResidence_onEmptyProfile_leavesProfileUnset() {
        val start = ProfileDraft.of(Profile(), NotifyMode.ALL)
        val skipped = Onboarding.skip(start.edit { it.copy(sido = "경기", residenceSince = "2024-03-01") }, start, FormSection.RESIDENCE)
        assertFalse(skipped.build().isSet)
        assertEquals("", skipped.build().residenceSince)
    }
}
