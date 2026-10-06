package com.chungyak.advisor.ui

import com.chungyak.advisor.match.Profile
import java.time.LocalDate

/**
 * 첫 실행 온보딩(v0.9.0~, docs/13): 내 조건을 [steps] 순서로 한 묶음씩 받는다. 화면은 OnboardingScreen.
 * 입력칸·검증은 '내 조건' 탭과 같은 것(ProfileFields, ProfileDraft)을 쓴다.
 */
object Onboarding {

    /** 단계 = 입력 묶음. 순서: 거주 → 세대/주택 → 가족 → 통장 → 소득/자산 → 가점 → 관심 → 알림. */
    val steps: List<FormSection> = FormSection.entries

    /**
     * 자동으로 띄울지. 프로필이 이미 있으면(기존 사용자) 띄우지 않고, 한 번 끝내거나 '나중에 하기'를 누른 뒤에도
     * 띄우지 않는다. 다시 하려면 설정 → '온보딩 다시 하기'.
     */
    fun shouldShow(profile: Profile, done: Boolean): Boolean = !profile.isSet && !done

    /** [step] 단계의 입력 문제(다음으로 넘어가지 못하게 하는 것). 다른 단계의 문제는 그 단계에서 보여 준다. */
    fun problems(draft: ProfileDraft, step: FormSection, today: LocalDate): List<FormProblem> =
        draft.problems(today).filter { it.section == step }

    /**
     * [step]에서 '건너뛰기': 이 단계에서 고친 값은 되돌리고([atStepStart] 상태로) 다른 단계 값은 그대로 둔다.
     * 숫자 칸·알림 범위도 이 단계 것만 되돌린다.
     */
    fun skip(draft: ProfileDraft, atStepStart: ProfileDraft, step: FormSection): ProfileDraft {
        val keys = numKeys(step)
        val nums = draft.nums.toMutableMap()
        for (k in keys) {
            val v = atStepStart.nums[k]
            if (v != null) nums[k] = v else nums.remove(k)
        }
        val p = draft.profile
        val o = atStepStart.profile
        val profile = when (step) {
            FormSection.RESIDENCE -> p.copy(sido = o.sido, sigungu = o.sigungu)
            FormSection.HOUSEHOLD -> p.copy(
                householdHead = o.householdHead, everOwned = o.everOwned, wonWithin5y = o.wonWithin5y, usedSpecial = o.usedSpecial,
            )
            FormSection.FAMILY -> p.copy(
                married = o.married, marriageYm = o.marriageYm, hasNewborn = o.hasNewborn, supportsParent = o.supportsParent,
            )
            FormSection.ACCOUNT -> p.copy(account = o.account, accountOpened = o.accountOpened)
            FormSection.INCOME -> p.copy(dualIncome = o.dualIncome, taxYears5 = o.taxYears5)
            FormSection.GAJEOM -> p.copy(birthDate = o.birthDate, homelessSince = o.homelessSince)
            FormSection.INTEREST -> p.copy(interestSido = o.interestSido)
            FormSection.NOTIFY -> p
        }
        return draft.copy(
            profile = profile, nums = nums,
            mode = if (step == FormSection.NOTIFY) atStepStart.mode else draft.mode,
        )
    }

    /** 단계별 숫자 칸(ProfileDraft.NUM_KEYS의 부분 집합). */
    fun numKeys(step: FormSection): List<String> = when (step) {
        FormSection.RESIDENCE -> listOf("residenceMonths")
        FormSection.HOUSEHOLD -> listOf("homesOwned")
        FormSection.FAMILY -> listOf("children")
        FormSection.ACCOUNT -> listOf("accountMonths", "payments", "depositManwon")
        FormSection.INCOME -> listOf("householdSize", "incomePct", "realEstateManwon")
        FormSection.GAJEOM -> listOf("dependents")
        FormSection.INTEREST -> listOf("maxPriceManwon", "minAreaM2", "maxAreaM2")
        FormSection.NOTIFY -> emptyList()
    }
}
