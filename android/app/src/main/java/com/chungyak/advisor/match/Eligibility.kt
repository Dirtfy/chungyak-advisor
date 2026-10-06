package com.chungyak.advisor.match

import com.chungyak.advisor.data.HouseModel
import com.chungyak.advisor.data.Notice
import java.time.LocalDate

/**
 * 청약 자격 매칭 규칙 엔진 — 순수 Kotlin(단위테스트 대상).
 *
 * 기준: 「주택공급에 관한 규칙」(국토교통부령)과 청약홈(applyhome.co.kr) 청약자격 안내.
 * 기준일 [RULES_DATE]. 제도가 바뀌면 이 파일의 상수·규칙을 고치고 기준일을 올린다.
 *
 * 판정은 참고용이다. 공고 데이터(API)나 프로필로 알 수 없는 조건은 "확인 필요"로 남기고
 * 단정하지 않는다. 최종 자격은 해당 단지 입주자모집공고문과 청약홈에서 확인해야 한다.
 */
object Eligibility {

    const val RULES_DATE = "2026-10-03"
    const val RULES_SOURCE = "주택공급에 관한 규칙 · 청약홈 청약자격 안내"

    enum class Verdict(val label: String, val rank: Int) {
        ELIGIBLE("신청 가능", 2),
        CHECK("확인 필요", 1),
        INELIGIBLE("불가", 0),
    }

    data class Track(val name: String, val verdict: Verdict, val reasons: List<String>)

    data class Result(
        val verdict: Verdict,
        val tracks: List<Track>,
        /** 관심 조건(지역·분양가·면적)으로 제외됐으면 그 이유. */
        val filteredOut: String? = null,
        val notes: List<String> = emptyList(),
    ) {
        /** 알림·목록용 한 줄 요약: 가능한 유형 이름들. */
        val summary: String
            get() = filteredOut ?: tracks.filter { it.verdict == verdict && verdict != Verdict.INELIGIBLE }
                .joinToString(", ") { it.name }.ifBlank { "조건에 맞는 유형 없음" }
    }

    /** 신혼부부·생애최초 추첨 물량의 부동산 자산 상한(만원). 2025년도 공고 기준 3억 3,100만원. */
    const val LOTTERY_ASSET_LIMIT_MANWON = 33_100

    /** 수도권 공고: 공급 지역 시·도. */
    private val METRO = setOf("서울", "경기", "인천")

    fun shouldNotify(p: Profile, mode: NotifyMode, r: Result?): Boolean = when {
        !p.isSet || mode == NotifyMode.ALL || r == null -> true
        mode == NotifyMode.ELIGIBLE -> r.verdict == Verdict.ELIGIBLE
        else -> r.verdict != Verdict.INELIGIBLE
    }

    /**
     * @param today 공고일을 읽을 수 없을 때 쓰는 기준일. 자격(가입기간 등)은 입주자모집공고일 기준으로 본다.
     */
    fun evaluate(profile: Profile, n: Notice, models: List<HouseModel>, today: LocalDate = LocalDate.now()): Result {
        val notes = ArrayList<String>()
        // 가입 일자가 있으면 판정할 때마다 공고일 기준으로 다시 계산 → 저장 시점 값으로 굳지 않는다.
        val asOf = AccountPeriod.parse(n.noticeDate) ?: today
        val p = profile.copy(accountMonths = profile.accountMonthsAt(asOf), residenceMonths = profile.residenceMonthsAt(asOf))
        if (AccountPeriod.parse(profile.accountOpened) != null) {
            notes += "통장 가입 ${AccountPeriod.label(p.accountMonths)} — 가입일 ${profile.accountOpened}, 공고일 $asOf 기준"
        }
        if (AccountPeriod.parse(profile.residenceSince) != null) {
            notes += "거주 ${AccountPeriod.label(p.residenceMonths)} — 전입일 ${profile.residenceSince}, 공고일 $asOf 기준"
        }
        val kind = kindOf(n.houseDtl)
        if (kind == Kind.UNKNOWN) notes += "주택 구분(민영/국민) 미확인 — 민영 기준으로 판정"
        val public = kind == Kind.PUBLIC
        val regulated = n.speculationArea || n.adjustmentArea
        notes += if (regulated) "규제지역(" + listOfNotNull(
            "투기과열지구".takeIf { n.speculationArea }, "조정대상지역".takeIf { n.adjustmentArea },
        ).joinToString("·") + ")" else "비규제지역"
        if (models.isEmpty()) notes += "주택형 정보 대기 중 — 전용 85㎡ 이하·특공 물량 미확인으로 판정"

        filter(p, n, models)?.let { return Result(Verdict.INELIGIBLE, emptyList(), it, notes) }

        val sido = METRO.firstOrNull { n.areaName.contains(it) } ?: n.areaName
        if (p.sido !in METRO) {
            val t = Track("전체", Verdict.INELIGIBLE, listOf("수도권($sido) 공고는 서울·경기·인천 거주자만 신청할 수 있습니다"))
            return Result(Verdict.INELIGIBLE, listOf(t), null, notes)
        }
        val area = areaPriority(p, n, sido, regulated)
        val tracks = ArrayList<Track>()
        val accountBlock = accountBlock(p, public)
        if (accountBlock != null) {
            tracks += Track("일반공급", Verdict.INELIGIBLE, listOf(accountBlock))
        } else {
            tracks += general(p, public, regulated, models, area)
            tracks += newlywed(p, n, public, models)
            tracks += firstLife(p, public, regulated, models)
            tracks += multiChild(p, public, models)
            tracks += oldParent(p, public, regulated, models)
            if (public || models.any { it.spNewborn > 0 }) tracks += newborn(p, public, models)
        }
        if (kind == Kind.UNKNOWN) {
            // 주택 구분을 모르면 '가능'으로 단정하지 않는다.
            tracks.replaceAll { if (it.verdict == Verdict.ELIGIBLE) it.copy(verdict = Verdict.CHECK) else it }
        }
        val best = tracks.maxOfOrNull { it.verdict.rank } ?: 0
        return Result(Verdict.entries.first { it.rank == best }, tracks, null, notes)
    }

    // ---- 판정 단위 ----

    private enum class Kind { PRIVATE, PUBLIC, UNKNOWN }

    private fun kindOf(dtl: String) = when {
        dtl.contains("국민") || dtl.contains("공공") -> Kind.PUBLIC
        dtl.contains("민영") -> Kind.PRIVATE
        else -> Kind.UNKNOWN
    }

    /** 실패/미확인 사유를 모아 판정. 실패가 하나라도 있으면 불가, 미확인만 있으면 확인 필요. */
    private class Check {
        val fails = ArrayList<String>()
        val unknowns = ArrayList<String>()
        val oks = ArrayList<String>()
        fun need(ok: Boolean?, okMsg: String?, failMsg: String, unknownMsg: String = failMsg) {
            when (ok) {
                true -> okMsg?.let { oks += it }
                false -> fails += failMsg
                null -> unknowns += unknownMsg
            }
        }
        val verdict get() = when {
            fails.isNotEmpty() -> Verdict.INELIGIBLE
            unknowns.isNotEmpty() -> Verdict.CHECK
            else -> Verdict.ELIGIBLE
        }
        fun reasons(extra: List<String> = emptyList()) =
            fails.map { "✗ $it" } + unknowns.map { "? $it" } + oks.map { "✓ $it" } + extra
        fun track(name: String, extra: List<String> = emptyList()) = Track(name, verdict, reasons(extra))
    }

    private fun known(v: Int) = v >= 0

    /** 통장 종류로 이 공고에 아예 신청할 수 없으면 그 이유. */
    private fun accountBlock(p: Profile, public: Boolean): String? = when (p.account) {
        AccountType.NONE -> "청약통장이 없으면 신청할 수 없습니다"
        AccountType.SAVINGS -> if (!public) "청약저축은 국민주택만 신청 가능(이 공고는 민영)" else null
        AccountType.DEPOSIT, AccountType.INSTALLMENT ->
            if (public) "${p.account.label}은 민영주택만 신청 가능(이 공고는 국민)" else null
        AccountType.COMPREHENSIVE -> null
    }

    /** 신청 대상 주택형의 전용면적들(청약부금은 85㎡ 이하만). 모르면 85㎡ 하나로 가정. */
    private fun areas(p: Profile, models: List<HouseModel>): List<Double> {
        val all = models.map { it.exclusiveArea }.filter { it > 0 }.ifEmpty { listOf(85.0) }
        return if (p.account == AccountType.INSTALLMENT) all.filter { it <= 85.0 } else all
    }

    /** 민영주택 지역별 예치금(만원) — 신청자 거주지 기준(주택공급에 관한 규칙 별표2). */
    fun requiredDeposit(residenceSido: String, exclusiveArea: Double): Int {
        val tier = when {
            exclusiveArea <= 85.0 -> 0
            exclusiveArea <= 102.0 -> 1
            exclusiveArea <= 135.0 -> 2
            else -> 3
        }
        val table = when (residenceSido) {
            "서울" -> intArrayOf(300, 600, 1000, 1500)       // 특별시·부산
            "인천" -> intArrayOf(250, 400, 700, 1000)        // 그 밖의 광역시
            else -> intArrayOf(200, 300, 400, 500)          // 특별시·광역시 외 시·군(경기)
        }
        return table[tier]
    }

    /**
     * 민영: 예치금 충족 여부와 신청 가능한 최대 면적 안내. 국민: 납입 횟수.
     * 반환: (충족 여부, 메시지). null = 미입력.
     */
    private fun savings(p: Profile, public: Boolean, models: List<HouseModel>, minPayments: Int): Pair<Boolean?, String> {
        if (public) {
            if (!known(p.payments)) return null to "납입 횟수 미입력(국민주택 ${minPayments}회 이상 필요)"
            return (p.payments >= minPayments) to "납입 ${p.payments}회 (필요 ${minPayments}회)"
        }
        val areas = areas(p, models)
        if (areas.isEmpty()) return false to "청약부금은 전용 85㎡ 이하만 신청 가능 — 이 공고엔 해당 주택형 없음"
        val needMin = areas.minOf { requiredDeposit(p.sido, it) }
        if (!known(p.depositManwon)) return null to "예치금 미입력(${p.sido} 거주 기준 최소 ${needMin}만원)"
        if (p.depositManwon < needMin) return false to "예치금 ${p.depositManwon}만원 < 필요 ${needMin}만원(${p.sido} 거주, 최소 면적 기준)"
        val ok = areas.filter { requiredDeposit(p.sido, it) <= p.depositManwon }
        val maxArea = ok.max()
        val all = ok.size == areas.size
        return true to "예치금 ${p.depositManwon}만원 — " +
            (if (all) "전 주택형 신청 가능" else "전용 ${"%.0f".format(maxArea)}㎡ 이하 주택형까지")
    }

    /** 해당지역/기타지역 우선순위 안내 문구. */
    private fun areaPriority(p: Profile, n: Notice, sido: String, regulated: Boolean): String {
        if (p.sido != sido) return "기타지역(수도권) 순위 — 해당지역 신청자 다음"
        if (sido == "경기") {
            val sg = p.sigungu.trim()
            if (sg.isEmpty()) return "경기 거주 — 해당 시·군 여부 확인 필요(시·군 미입력)"
            val stem = sg.removeSuffix("시").removeSuffix("군").removeSuffix("구")
            if (stem.isEmpty() || !n.address.contains(stem)) return "경기 기타 시·군 — 해당지역 다음 순위(대규모 택지는 경기 우선 물량)"
        }
        val period = if (regulated && known(p.residenceMonths) && p.residenceMonths < 24)
            " · 거주 ${p.residenceMonths}개월: 규제지역 해당지역 우선은 보통 2년 이상 거주(공고 확인)" else
            if (!known(p.residenceMonths)) " · 거주기간 요건은 공고 확인" else ""
        return "해당지역 우선$period"
    }

    /** 일반공급 1순위 요건 판정(생애최초·노부모 특공도 1순위를 요구하므로 공유). */
    private fun rank1(p: Profile, public: Boolean, regulated: Boolean, models: List<HouseModel>): Check {
        val c = Check()
        val months = if (regulated) 24 else 12
        c.need(
            if (known(p.accountMonths)) p.accountMonths >= months else null,
            "통장 가입 ${p.accountMonths}개월 (필요 ${months}개월)",
            "통장 가입 ${p.accountMonths}개월 < ${months}개월",
            "통장 가입기간 미입력(${months}개월 이상 필요)",
        )
        val (ok, msg) = savings(p, public, models, months)
        c.need(ok, msg, msg)
        if (public) c.need(p.homeless, null, "국민주택은 무주택세대구성원만 신청 가능")
        if (regulated) {
            c.need(p.householdHead, "세대주", "규제지역 1순위는 세대주만")
            c.need(!p.wonWithin5y, null, "규제지역 1순위: 세대원 5년 내 당첨 이력 있음")
            if (!public) c.need(p.homesOwned <= 1, null, "규제지역 1순위: 2주택 이상 보유")
        }
        return c
    }

    private fun general(p: Profile, public: Boolean, regulated: Boolean, models: List<HouseModel>, area: String): Track {
        val c = rank1(p, public, regulated, models)
        if (public && !p.homeless) return Track("일반공급", Verdict.INELIGIBLE, c.reasons())
        if (c.verdict == Verdict.INELIGIBLE) {
            // 2순위는 통장만 있으면 되지만, 수도권은 대부분 1순위에서 마감되므로 매칭으로 치지 않는다.
            return Track("일반공급 2순위만", Verdict.INELIGIBLE, c.reasons(listOf("1순위 요건 미충족 → 2순위만 가능(1순위 마감 시 기회 없음)")))
        }
        val extra = ArrayList<String>()
        extra += area
        if (!public) extra += when {
            p.homesOwned >= 1 -> "유주택: 가점제 불가, 추첨제 물량만" + if (regulated) "(기존 주택 처분 조건)" else ""
            else -> "무주택: 가점제 + 추첨제"
        }
        if (public && models.any { it.exclusiveArea in 0.1..60.0 }) extra += "전용 60㎡ 이하 공공분양은 소득·자산 기준 있음(공고 확인)"
        return c.track("일반공급 1순위", extra)
    }

    /** 특별공급 공통: 무주택·1회 한정·통장(6개월 + 예치금/6회)·물량. */
    private fun special(p: Profile, public: Boolean, models: List<HouseModel>, units: (HouseModel) -> Int, label: String): Check {
        val c = Check()
        if (models.isNotEmpty()) {
            val total = models.sumOf(units)
            c.need(total > 0, "$label 물량 ${total}세대", "이 공고에 $label 물량 없음")
        } else c.unknowns += "$label 물량 미확인"
        c.need(!p.usedSpecial, null, "특별공급은 평생 1회(이미 당첨 이력)")
        c.need(p.homeless, null, "특별공급은 무주택세대구성원만")
        c.need(
            if (known(p.accountMonths)) p.accountMonths >= 6 else null,
            null, "통장 가입 6개월 미만", "통장 가입기간 미입력(6개월 이상 필요)",
        )
        val (ok, msg) = savings(p, public, models, 6)
        c.need(ok, null, msg)
        return c
    }

    /**
     * 소득 판정. [limit] 이하면 통과, 초과 시 [lottery]면 추첨 물량(자산 기준)으로 '확인 필요'.
     * [priority] 이하이면 우선공급 안내.
     */
    private fun income(c: Check, p: Profile, limit: Int, priority: Int?, lottery: Boolean) {
        if (!known(p.incomePct)) {
            c.unknowns += "소득 미입력(도시근로자 월평균소득 ${limit}% 이하 필요)"
            return
        }
        when {
            p.incomePct <= limit -> c.oks += "소득 ${p.incomePct}% ≤ ${limit}%" +
                if (priority != null && p.incomePct <= priority) " (우선공급 ${priority}% 이하)" else ""
            lottery && known(p.realEstateManwon) && p.realEstateManwon > LOTTERY_ASSET_LIMIT_MANWON ->
                c.fails += "소득 ${p.incomePct}% > ${limit}%, 부동산 자산도 추첨 기준(${LOTTERY_ASSET_LIMIT_MANWON}만원) 초과"
            lottery -> c.unknowns += "소득 ${p.incomePct}% > ${limit}% — 추첨 물량(부동산 ${LOTTERY_ASSET_LIMIT_MANWON}만원 이하)만 가능"
            else -> c.fails += "소득 ${p.incomePct}% > ${limit}%"
        }
    }

    /** 혼인신고 "yyyy-MM"부터 공고일까지 개월 수. 모르면 null. */
    fun marriageMonths(marriageYm: String, noticeDate: String): Int? {
        val m = Regex("^(\\d{4})-(\\d{1,2})").find(marriageYm.trim()) ?: return null
        val d = Regex("^(\\d{4})-(\\d{2})").find(noticeDate) ?: return null
        val months = (d.groupValues[1].toInt() - m.groupValues[1].toInt()) * 12 +
            (d.groupValues[2].toInt() - m.groupValues[2].toInt())
        return months.takeIf { it >= 0 }
    }

    private fun newlywed(p: Profile, n: Notice, public: Boolean, models: List<HouseModel>): Track {
        val c = special(p, public, models, { it.spNewlywed }, "신혼부부")
        if (!p.married) {
            c.fails += "혼인 중이 아님(혼인 7년 이내 부부 대상)"
        } else {
            val mm = marriageMonths(p.marriageYm, n.noticeDate)
            c.need(mm?.let { it <= 84 }, "혼인 ${mm?.let { it / 12 }}년 ${mm?.let { it % 12 }}개월", "혼인 7년 초과", "혼인신고 연월 미입력(7년 이내 필요)")
        }
        val extra = ArrayList<String>()
        if (public) income(c, p, if (p.dualIncome) 140 else 130, if (p.dualIncome) 100 else 80, lottery = false)
        else income(c, p, if (p.dualIncome) 160 else 140, if (p.dualIncome) 120 else 100, lottery = true)
        if (p.hasNewborn) extra += "2세 미만 자녀: 신생아 우선공급 대상"
        if (public) extra += "공공 신혼부부는 자산 기준 있음(공고 확인)"
        return c.track("신혼부부 특공", extra)
    }

    private fun firstLife(p: Profile, public: Boolean, regulated: Boolean, models: List<HouseModel>): Track {
        val c = special(p, public, models, { it.spFirstLife }, "생애최초")
        c.need(!p.everOwned, null, "세대원 과거 주택 소유 이력 있음(생애최초 아님)")
        val r1 = rank1(p, public, regulated, models)
        c.fails += r1.fails.map { "1순위 필요: $it" }
        c.unknowns += r1.unknowns.map { "1순위 필요: $it" }
        if (public) c.need(
            if (known(p.depositManwon)) p.depositManwon >= 600 else null,
            null, "국민주택 생애최초는 저축액 600만원 이상", "저축액 미입력(국민주택 생애최초 600만원 이상)",
        )
        if (!p.taxYears5) c.unknowns += "소득세 5년 이상 납부 여부 확인 필요"
        if (!p.married && p.children == 0) c.unknowns += "미혼·무자녀 1인 가구는 추첨 물량 일부만(공고 확인)"
        if (public) income(c, p, 130, 100, lottery = false)
        else income(c, p, 160, 130, lottery = true)
        return c.track("생애최초 특공", if (p.hasNewborn) listOf("2세 미만 자녀: 신생아 우선공급 대상") else emptyList())
    }

    private fun multiChild(p: Profile, public: Boolean, models: List<HouseModel>): Track {
        val c = special(p, public, models, { it.spMultiChild }, "다자녀")
        c.need(p.children >= 2, "미성년 자녀 ${p.children}명", "미성년 자녀 ${p.children}명(2명 이상 필요, 태아 포함)")
        if (public) income(c, p, 120, null, lottery = false)
        return c.track("다자녀 특공", listOf("배점(자녀 수·무주택기간·거주기간 등)으로 선정"))
    }

    private fun oldParent(p: Profile, public: Boolean, regulated: Boolean, models: List<HouseModel>): Track {
        val c = special(p, public, models, { it.spOldParent }, "노부모부양")
        c.need(p.supportsParent, null, "만 65세 이상 직계존속 3년 이상 부양 필요")
        c.need(p.householdHead, null, "노부모부양 특공은 세대주만")
        val r1 = rank1(p, public, regulated, models)
        c.fails += r1.fails.map { "1순위 필요: $it" }
        c.unknowns += r1.unknowns.map { "1순위 필요: $it" }
        if (public) income(c, p, 120, null, lottery = false)
        return c.track("노부모부양 특공")
    }

    private fun newborn(p: Profile, public: Boolean, models: List<HouseModel>): Track {
        val c = special(p, public, models, { it.spNewborn }, "신생아")
        c.need(p.hasNewborn, null, "2세 미만 자녀(임신 포함) 필요")
        income(c, p, if (p.dualIncome) 150 else 140, if (p.dualIncome) 120 else 100, lottery = false)
        return c.track("신생아 특공")
    }

    /** 관심 조건(지역·분양가 상한·전용면적)으로 걸러지면 그 이유. 데이터가 없으면 거르지 않는다. */
    private fun filter(p: Profile, n: Notice, models: List<HouseModel>): String? {
        if (p.interestSido.isNotEmpty() && p.interestSido.none { n.areaName.contains(it) })
            return "관심 지역(${p.interestSido.joinToString("·")}) 아님"
        var ms = models
        if (known(p.minAreaM2) || known(p.maxAreaM2)) {
            val sized = ms.filter { it.exclusiveArea > 0 }
            if (sized.isNotEmpty()) {
                ms = sized.filter {
                    (!known(p.minAreaM2) || it.exclusiveArea >= p.minAreaM2) &&
                        (!known(p.maxAreaM2) || p.maxAreaM2 == 0 || it.exclusiveArea <= p.maxAreaM2)
                }
                if (ms.isEmpty()) return "희망 면적에 맞는 주택형 없음"
            }
        }
        if (known(p.maxPriceManwon) && p.maxPriceManwon > 0) {
            val priced = ms.filter { it.priceManwon > 0 }
            if (priced.isNotEmpty() && priced.none { it.priceManwon <= p.maxPriceManwon })
                return "모든 주택형이 분양가 상한(${p.maxPriceManwon}만원) 초과"
            if (priced.isEmpty() && models.isEmpty() && n.priceMinManwon > p.maxPriceManwon)
                return "분양가 상한(${p.maxPriceManwon}만원) 초과"
        }
        return null
    }
}
