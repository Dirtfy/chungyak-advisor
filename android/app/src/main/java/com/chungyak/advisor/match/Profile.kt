package com.chungyak.advisor.match

import java.time.LocalDate

/** 청약통장 종류 — 신청 가능한 주택(국민/민영)이 다르다. */
enum class AccountType(val label: String) {
    NONE("없음"),
    COMPREHENSIVE("주택청약종합저축"), // 국민·민영 모두
    SAVINGS("청약저축"),               // 국민주택만
    DEPOSIT("청약예금"),               // 민영주택만
    INSTALLMENT("청약부금");           // 민영 전용 85㎡ 이하만

    companion object {
        fun of(name: String?) = entries.firstOrNull { it.name == name } ?: NONE
    }
}

/** 알림 범위. 프로필이 비어 있으면 [ALL]처럼 동작한다(기존 동작). */
enum class NotifyMode(val label: String) {
    ALL("모든 신규 공고"),
    ELIGIBLE("신청 가능만"),
    ELIGIBLE_OR_CHECK("신청 가능 + 확인 필요");

    companion object {
        fun of(name: String?) = entries.firstOrNull { it.name == name } ?: ELIGIBLE_OR_CHECK
    }
}

/**
 * 사용자 청약 프로필. 기기 안(SharedPreferences, 자동 백업 제외)에만 저장한다.
 * 숫자 항목의 -1 = 미입력 → 그 조건이 필요한 판정은 "확인 필요"가 된다.
 */
data class Profile(
    // 거주
    val sido: String = "",              // "서울" / "경기" / "인천" / "기타"
    val sigungu: String = "",           // 예: "성남시", "강남구" (해당지역 판단용, 선택)
    val residenceSince: String = "",    // 현 거주지 전입일 "yyyy-MM-dd"(v0.10.0~) — 있으면 기간을 이 날짜로 계산(우선)
    val residenceMonths: Int = -1,      // 연속 거주 기간(개월). v0.9.0까지의 입력값 — 전입일이 없을 때만 사용
    // 세대
    val householdHead: Boolean = false, // 세대주
    val homesOwned: Int = 0,            // 세대 전체 보유 주택 수 (0 = 무주택세대)
    val everOwned: Boolean = false,     // 세대원 누구라도 과거 주택 소유 이력(생애최초 판단)
    val wonWithin5y: Boolean = false,   // 세대원 5년 내 당첨 이력(규제지역 1순위 제한)
    val usedSpecial: Boolean = false,   // 이미 특별공급 당첨(특공은 평생 1회)
    // 혼인·자녀·부양
    val married: Boolean = false,
    val marriageYm: String = "",        // 혼인신고 연월 "yyyy-MM"
    val children: Int = 0,              // 미성년 자녀 수(태아 포함)
    val hasNewborn: Boolean = false,    // 2세 미만 자녀(임신 포함)
    val supportsParent: Boolean = false, // 만 65세 이상 직계존속 3년 이상 같은 등본 부양
    // 청약통장
    val account: AccountType = AccountType.NONE,
    val accountOpened: String = "",     // 가입 일자 "yyyy-MM-dd" — 있으면 기간을 이 날짜로 계산(우선)
    val accountMonths: Int = -1,        // 가입 기간(개월) 직접 입력 — 가입 일자가 없을 때 사용
    val payments: Int = -1,             // 납입 인정 회차(국민주택)
    val depositManwon: Int = -1,        // 예치금/납입 총액(만원)
    // 소득·자산
    val householdSize: Int = -1,
    val incomePct: Int = -1,            // 세대 월평균소득, 전년도 도시근로자 대비 %
    val dualIncome: Boolean = false,
    val realEstateManwon: Int = -1,     // 세대 부동산 자산(만원, 선택)
    val taxYears5: Boolean = false,     // 소득세 5년 이상 납부(생애최초)
    // 가점(v0.8.0~, 선택)
    val birthDate: String = "",         // 생년월일 "yyyy-MM-dd" — 무주택기간 기산(만 30세)
    val homelessSince: String = "",     // 과거에 집이 있었다면 처분해 무주택이 된 날 "yyyy-MM-dd"
    val dependents: Int = -1,           // 부양가족 수(본인 제외). -1 = 미입력 → 배우자+자녀로 추정
    // 관심 필터(선택)
    val interestSido: Set<String> = emptySet(), // 비어 있으면 전체
    val maxPriceManwon: Int = -1,
    val minAreaM2: Int = -1,            // 전용면적
    val maxAreaM2: Int = -1,
    // 자금(v0.11.0~, 선택) — 매매 가능 판정(Affordability)용. 금액은 만원, -1 = 미입력.
    val cashManwon: Int = -1,           // 지금 쓸 수 있는 현금(가용 자본). 이게 있어야 판정한다
    val loanLimitManwon: Int = -1,      // 대출 가능 한도(은행 상담·앱 조회 금액)
    val monthlyCapManwon: Int = -1,     // 대출 시 월 상환액 상한(사용자가 정함)
    val loanRatePct: Double = DEFAULT_LOAN_RATE, // 가정: 연 금리(%)
    val loanYears: Int = DEFAULT_LOAN_YEARS,     // 가정: 상환 기간(년)
    val repayment: Repayment = Repayment.ANNUITY, // 가정: 상환 방식
    val downPaymentPct: Int = DEFAULT_DOWN_PCT,  // 가정: 계약금 비율(%) — 현금으로 내야 하는 몫
    // 대출 규정 판정(v0.12.0~, LoanRules) — 주택 수·생애최초는 위 '세대' 항목을 쓴다.
    val incomeManwon: Int = -1,         // 연소득(세전, 부부 공동이면 합산) — DSR·서민실수요자. -1 = 미입력 → DSR 미반영
    val debtAnnualManwon: Int = -1,     // 기존 대출의 연간 원리금 상환액(DSR에 더함). -1 = 없음
    val rateType: RateType = RateType.MIXED, // 가정: 금리 유형(스트레스 DSR 가산 비율)
    val graceYears: Int = 0,            // 가정: 거치기간(년, 이자만 내는 기간)
    val sellingHome: Boolean = false,   // 1주택: 기존 집을 6개월 안에 파는 조건(처분조건부) → 무주택과 같게
) {
    /** 프로필을 저장했는가(거주 시·도는 필수 항목). 아니면 매칭하지 않고 모든 공고를 알린다. */
    val isSet: Boolean get() = sido.isNotBlank()

    val homeless: Boolean get() = homesOwned == 0

    /** [asOf] 기준 연속 거주 개월 수. 전입일이 있으면 그걸로 계산하고, 없으면 예전 입력값(-1 = 미입력). */
    fun residenceMonthsAt(asOf: LocalDate): Int {
        val since = AccountPeriod.parse(residenceSince) ?: return residenceMonths
        return AccountPeriod.months(since, asOf).coerceAtLeast(0)
    }

    /** [asOf] 기준 통장 가입 개월 수. 가입 일자가 있으면 그걸로 계산하고, 없으면 직접 입력값(-1 = 미입력). */
    fun accountMonthsAt(asOf: LocalDate): Int {
        val opened = AccountPeriod.parse(accountOpened) ?: return accountMonths
        return AccountPeriod.months(opened, asOf).coerceAtLeast(0)
    }

    companion object {
        const val DEFAULT_LOAN_RATE = 4.0
        const val DEFAULT_LOAN_YEARS = 30
        const val DEFAULT_DOWN_PCT = 10
    }
}
