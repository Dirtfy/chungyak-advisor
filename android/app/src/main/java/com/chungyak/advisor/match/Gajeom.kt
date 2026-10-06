package com.chungyak.advisor.match

import java.time.LocalDate

/**
 * 청약 가점(84점 만점) 계산 — 순수 Kotlin(단위테스트 대상).
 *
 * 기준: 「주택공급에 관한 규칙」 별표 1(가점제 적용기준). 기준일은 입주자모집공고일.
 * - 무주택기간(32점): 만 30세가 되는 날(그 전에 혼인했으면 혼인신고일)부터 계속 무주택인 기간.
 *   집을 가졌다가 처분했으면 무주택이 된 날부터. 1년 미만 2점, 1년마다 2점씩, 15년 이상 32점.
 *   유주택 세대·만 30세 미만 미혼은 0점.
 * - 부양가족 수(35점): 본인 제외. 0명 5점, 1명마다 5점씩, 6명 이상 35점.
 * - 청약통장 가입기간(17점): 6개월 미만 1점, 6개월~1년 2점, 1년 이상은 (만 년수 + 2)점, 15년 이상 17점.
 *
 * 소형·저가주택 무주택 간주, 부양가족 인정 범위(3년 이상 같은 등본의 직계존속 등) 같은 세부 예외는
 * 사용자가 입력한 값을 그대로 믿는다. 참고용 — 최종 점수는 청약홈 가점 계산기·모집공고문 기준.
 */
object Gajeom {

    const val MAX = 84

    data class Part(
        val name: String,
        val max: Int,
        /** null = 입력이 모자라 계산 못 함. */
        val score: Int?,
        /** 화면에 보여 줄 계산 근거. */
        val basis: String,
        /** 직접 입력이 아니라 다른 항목에서 추정했는가. */
        val estimated: Boolean = false,
    )

    data class Result(val parts: List<Part>, val asOf: LocalDate) {
        /** 계산된 항목의 합(모르는 항목은 0으로). */
        val known: Int get() = parts.sumOf { it.score ?: 0 }
        /** 모든 항목을 계산했을 때만 총점. */
        val total: Int? get() = if (parts.all { it.score != null }) known else null
        val missing: List<String> get() = parts.filter { it.score == null }.map { it.name }
        val estimated: Boolean get() = parts.any { it.estimated }
        /** "가점 54점/84" · "가점 계산에 생년월일 필요". */
        val summary: String
            get() = total?.let { "가점 ${it}점/$MAX" + if (estimated) "(추정 포함)" else "" }
                ?: "가점 계산에 필요: ${missing.joinToString(", ")}"
    }

    fun calc(p: Profile, asOf: LocalDate): Result = Result(listOf(homeless(p, asOf), dependents(p), account(p, asOf)), asOf)

    // ---- 무주택기간 ----
    fun homeless(p: Profile, asOf: LocalDate): Part {
        val name = "무주택기간"
        if (!p.homeless) return Part(name, 32, 0, "세대에 주택이 있어 0점(무주택 세대만 가산)")
        val birth = AccountPeriod.parse(p.birthDate) ?: return Part(name, 32, null, "생년월일을 입력하면 계산합니다")
        val age30 = birth.plusYears(30)
        val marriage = if (p.married) parseYm(p.marriageYm) else null
        var start = age30
        var why = "만 30세가 된 날"
        if (marriage != null && marriage.isBefore(age30)) {
            start = marriage
            why = "만 30세 전 혼인신고"
        }
        if (p.everOwned) {
            val since = AccountPeriod.parse(p.homelessSince)
                ?: return Part(name, 32, null, "과거에 집이 있었다면 '무주택이 된 날'을 입력하면 계산합니다")
            if (since.isAfter(start)) {
                start = since
                why = "집을 처분해 무주택이 된 날"
            }
        }
        if (asOf.isBefore(start)) return Part(name, 32, 0, "만 30세 전·미혼이라 무주택기간을 세지 않아 0점")
        val months = AccountPeriod.months(start, asOf)
        val score = homelessScore(months)
        return Part(name, 32, score, "${AccountPeriod.label(months)} 무주택 — 기산일 $start($why) → ${score}점")
    }

    /** 무주택 개월 수 → 점수. 1년 미만 2점, 만 1년마다 +2, 15년 이상 32점. */
    fun homelessScore(months: Int): Int = if (months < 0) 0 else minOf(32, 2 * (months / 12 + 1))

    // ---- 부양가족 ----
    fun dependents(p: Profile): Part {
        val name = "부양가족"
        if (p.dependents >= 0) {
            val s = dependentsScore(p.dependents)
            return Part(name, 35, s, "부양가족 ${p.dependents}명(본인 제외) → ${s}점")
        }
        // 미입력: 배우자 + 자녀로 추정(부모님은 인원을 몰라 넣지 않음).
        val est = (if (p.married) 1 else 0) + p.children.coerceAtLeast(0)
        val s = dependentsScore(est)
        val parent = if (p.supportsParent) " — 부모님 부양 중이면 부양가족 수를 직접 입력하세요" else ""
        return Part(name, 35, s, "입력이 없어 배우자·자녀로 추정: ${est}명 → ${s}점$parent", estimated = true)
    }

    /** 부양가족 수 → 점수. 0명 5점, 1명당 +5, 6명 이상 35점. */
    fun dependentsScore(n: Int): Int = 5 + 5 * n.coerceIn(0, 6)

    // ---- 청약통장 ----
    fun account(p: Profile, asOf: LocalDate): Part {
        val name = "청약통장 가입기간"
        if (p.account == AccountType.NONE) return Part(name, 17, 0, "청약통장이 없어 0점")
        val months = p.accountMonthsAt(asOf)
        if (months < 0) return Part(name, 17, null, "통장 가입 일자(또는 기간)를 입력하면 계산합니다")
        val s = accountScore(months)
        val src = if (AccountPeriod.parse(p.accountOpened) != null) "가입일 ${p.accountOpened}부터" else "직접 입력한 기간"
        return Part(name, 17, s, "${AccountPeriod.label(months)} ($src) → ${s}점")
    }

    /** 가입 개월 수 → 점수. 6개월 미만 1, 6개월~1년 2, 이후 (만 년수 + 2), 15년 이상 17. */
    fun accountScore(months: Int): Int = when {
        months < 6 -> 1
        months < 12 -> 2
        else -> minOf(17, months / 12 + 2)
    }

    /** "yyyy-MM" → 그 달 1일(혼인신고 연월). */
    private fun parseYm(s: String): LocalDate? {
        val m = Regex("^(\\d{4})-(\\d{1,2})$").matchEntire(s.trim()) ?: return null
        return runCatching { LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), 1) }.getOrNull()
    }
}
