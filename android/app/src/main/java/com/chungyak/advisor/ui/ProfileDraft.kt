package com.chungyak.advisor.ui

import com.chungyak.advisor.match.AccountPeriod
import com.chungyak.advisor.match.NotifyMode
import com.chungyak.advisor.match.Profile
import java.time.LocalDate

/**
 * '내 조건' 입력 묶음. 순서 = 온보딩 단계 순서(v0.9.0~). '내 조건' 탭과 온보딩이 같은 묶음·입력칸·검증을 쓴다.
 * [help]는 온보딩 단계 머리에 보이는 짧은 용어 설명.
 */
enum class FormSection(val title: String, val help: String) {
    RESIDENCE(
        "거주",
        "지금 주민등록상 사는 곳과 전입일입니다. '해당지역'(공고 지역에 일정 기간 이상 산 사람) 우선 공급과 1순위 판정에 씁니다. " +
            "전입일은 주민등록등본(정부24)에서 볼 수 있어요.",
    ),
    HOUSEHOLD(
        "세대·주택",
        "세대 = 같은 주민등록등본에 있는 가족 전체. 무주택세대 = 세대원 모두 집이 없음. 대부분의 청약은 무주택세대주가 유리합니다.",
    ),
    FAMILY(
        "혼인·자녀·부양",
        "신혼부부(혼인 7년 이내)·신생아·다자녀·노부모 부양 특별공급 판정에 씁니다. 해당 없으면 그대로 다음으로.",
    ),
    ACCOUNT(
        "청약통장",
        "가입 기간·납입 회차·예치금이 1순위 조건입니다. 가입 일자를 넣으면 기간은 공고일마다 자동 계산합니다. " +
            "납입 회차와 예치금은 은행 앱의 청약통장 화면에서 볼 수 있어요.",
    ),
    INCOME(
        "소득·자산",
        "특별공급의 소득·자산 기준 판정에 씁니다. 소득 %는 청약홈 기준표의 가구원 수별 금액과 비교한 값입니다. 모르면 비워 두세요.",
    ),
    GAJEOM(
        "청약 가점(민영주택 가점제)",
        "가점 84점 = 무주택기간 32 + 부양가족 35 + 통장 가입기간 17. 무주택기간은 만 30세(그 전에 혼인했으면 혼인일)부터 셉니다.",
    ),
    INTEREST(
        "관심 조건(선택 — 비우면 전체)",
        "관심 있는 지역·가격·면적만 골라 알림과 추천에 씁니다. 비워 두면 모든 공고가 대상입니다.",
    ),
    FUNDS(
        "자금(매매 가능 판정, 선택)",
        "지금 쓸 수 있는 현금과 대출로 분양가를 낼 수 있는지 공고마다 보여 줍니다. 대출 = LTV(담보 대비 비율)·DSR(소득 대비 " +
            "원리금 비율)·주담대 금액 상한·입력한 한도 중 가장 작은 값. 공개된 규정으로 계산한 추정이라 실제 심사와 다를 수 있어요.",
    ),
    NOTIFY(
        "알림 범위",
        "새 공고를 어디까지 알릴지 고릅니다. '확인 필요' = 입력이 부족하거나 공고문 확인이 필요한 경우.",
    ),
}

/** 입력 문제 하나. [section]이 있는 단계에서만 보여 준다(온보딩). */
data class FormProblem(val section: FormSection, val message: String)

/**
 * 입력 중인 프로필. 숫자 칸은 문자열로 들고 있다가 [build]에서 변환한다(빈칸 = 미입력 -1).
 * 순수 Kotlin — 화면 없이 단위 테스트한다(ProfileDraftTest).
 */
data class ProfileDraft(
    val profile: Profile,
    val nums: Map<String, String>,
    val mode: NotifyMode,
) {
    fun num(key: String): String = nums[key].orEmpty()

    fun withNum(key: String, raw: String) = copy(nums = nums + (key to raw.filter { it.isDigit() }.take(7)))

    /** 소수점 하나까지 받는 칸(대출 금리 %). */
    fun withDecimal(key: String, raw: String): ProfileDraft {
        val digits = raw.filter { it.isDigit() || it == '.' }
        val dot = digits.indexOf('.')
        val clean = if (dot < 0) digits else digits.substring(0, dot + 1) + digits.substring(dot + 1).replace(".", "")
        return copy(nums = nums + (key to clean.take(5)))
    }

    fun edit(f: (Profile) -> Profile) = copy(profile = f(profile))

    fun build(): Profile {
        fun n(k: String, unset: Int = -1) = nums[k]?.toIntOrNull() ?: unset
        return profile.copy(
            homesOwned = n("homesOwned", 0), children = n("children", 0),
            accountMonths = n("accountMonths"), payments = n("payments"), depositManwon = n("depositManwon"),
            householdSize = n("householdSize"), incomePct = n("incomePct"), realEstateManwon = n("realEstateManwon"),
            maxPriceManwon = n("maxPriceManwon"), minAreaM2 = n("minAreaM2"), maxAreaM2 = n("maxAreaM2"),
            dependents = n("dependents"),
            cashManwon = n("cashManwon"), loanLimitManwon = n("loanLimitManwon"), monthlyCapManwon = n("monthlyCapManwon"),
            loanRatePct = nums["loanRatePct"]?.toDoubleOrNull() ?: Profile.DEFAULT_LOAN_RATE,
            loanYears = n("loanYears", Profile.DEFAULT_LOAN_YEARS),
            downPaymentPct = n("downPaymentPct", Profile.DEFAULT_DOWN_PCT),
            incomeManwon = n("incomeManwon"), debtAnnualManwon = n("debtAnnualManwon"), graceYears = n("graceYears", 0),
        )
    }

    /** 저장을 막는 입력 문제들(없으면 빈 목록). */
    fun problems(today: LocalDate): List<FormProblem> {
        val b = build()
        return buildList {
            if (b.sido.isBlank()) add(FormProblem(FormSection.RESIDENCE, "거주 시·도를 골라 주세요."))
            if (b.residenceSince.isNotBlank() && AccountPeriod.parse(b.residenceSince).let { it == null || it.isAfter(today) })
                add(FormProblem(FormSection.RESIDENCE, "전입일이 올바르지 않습니다(미래 날짜 불가)."))
            if (b.married && b.marriageYm.isNotBlank() && !Regex("^\\d{4}-\\d{1,2}$").matches(b.marriageYm))
                add(FormProblem(FormSection.FAMILY, "혼인신고 연월은 2022-05 형식으로 입력하세요."))
            if (b.accountOpened.isNotBlank() && AccountPeriod.parse(b.accountOpened).let { it == null || it.isAfter(today) })
                add(FormProblem(FormSection.ACCOUNT, "청약통장 가입 일자가 올바르지 않습니다(미래 날짜 불가)."))
            if (b.dependents > 20) add(FormProblem(FormSection.GAJEOM, "부양가족 수가 너무 큽니다."))
            if (b.loanRatePct > 30) add(FormProblem(FormSection.FUNDS, "대출 금리는 0~30% 사이로 넣어 주세요."))
            if (b.loanYears !in 1..50) add(FormProblem(FormSection.FUNDS, "상환 기간은 1~50년 사이로 넣어 주세요."))
            if (b.graceYears >= b.loanYears && b.loanYears >= 1) add(FormProblem(FormSection.FUNDS, "거치기간은 상환 기간보다 짧아야 합니다."))
            if (b.downPaymentPct > 100) add(FormProblem(FormSection.FUNDS, "계약금 비율은 0~100% 사이로 넣어 주세요."))
        }
    }

    companion object {
        val NUM_KEYS = listOf(
            "homesOwned", "children", "accountMonths", "payments", "depositManwon",
            "householdSize", "incomePct", "realEstateManwon", "maxPriceManwon", "minAreaM2", "maxAreaM2", "dependents",
            "cashManwon", "loanLimitManwon", "monthlyCapManwon", "loanRatePct", "loanYears", "downPaymentPct",
            "incomeManwon", "debtAnnualManwon", "graceYears",
        )

        fun of(p: Profile, mode: NotifyMode): ProfileDraft {
            val values = mapOf(
                "homesOwned" to p.homesOwned,
                "children" to p.children, "accountMonths" to p.accountMonths,
                "payments" to p.payments, "depositManwon" to p.depositManwon,
                "householdSize" to p.householdSize, "incomePct" to p.incomePct,
                "realEstateManwon" to p.realEstateManwon, "maxPriceManwon" to p.maxPriceManwon,
                "minAreaM2" to p.minAreaM2, "maxAreaM2" to p.maxAreaM2,
                "dependents" to p.dependents,
                "cashManwon" to p.cashManwon, "loanLimitManwon" to p.loanLimitManwon, "monthlyCapManwon" to p.monthlyCapManwon,
                "loanYears" to p.loanYears, "downPaymentPct" to p.downPaymentPct,
                "incomeManwon" to p.incomeManwon, "debtAnnualManwon" to p.debtAnnualManwon, "graceYears" to p.graceYears,
            )
            val rate = p.loanRatePct.toString().removeSuffix(".0")
            return ProfileDraft(p, values.mapValues { (_, v) -> if (v >= 0) v.toString() else "" } + ("loanRatePct" to rate), mode)
        }
    }
}
