package com.chungyak.advisor.match

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * 프로필·알림 범위 저장소. 개인정보라 별도 prefs 파일([FILE])에 두고, 서버·백업 파일·로그 어디로도
 * 보내지 않는다. Android 자동 백업(구글 드라이브)에서도 제외한다(res/xml/backup_rules.xml,
 * data_extraction_rules.xml). 앱 백업 JSON(Backup)에도 넣지 않는다.
 */
class ProfileStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var profile: Profile
        get() {
            val stored = prefs.getString(KEY_PROFILE, null)?.let { runCatching { decode(JSONObject(it)) }.getOrNull() } ?: return Profile()
            // v0.9.0까지 개월 수로 넣은 거주 기간 → 전입일로 한 번 바꿔 저장(이후 날이 지나면 기간이 자동으로 늘어난다).
            val migrated = migrateResidence(stored, LocalDate.now())
            if (migrated != stored) prefs.edit().putString(KEY_PROFILE, encode(migrated).toString()).apply()
            return migrated
        }
        set(v) = prefs.edit().putString(KEY_PROFILE, encode(v).toString()).apply()

    var notifyMode: NotifyMode
        get() = NotifyMode.of(prefs.getString(KEY_MODE, null))
        set(v) = prefs.edit().putString(KEY_MODE, v.name).apply()

    /**
     * 첫 실행 온보딩을 마쳤거나 '나중에 하기'로 넘겼는지(v0.9.0~). 프로필과 같은 파일이라 백업·기기 이전에서
     * 함께 빠진다 → 프로필 없이 옮겨진 새 기기에서는 온보딩이 다시 뜬다.
     */
    var onboardingDone: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_DONE, false)
        set(v) = prefs.edit().putBoolean(KEY_ONBOARDING_DONE, v).apply()

    /** 프로필·알림 범위만 지운다. 온보딩 완료 여부는 남긴다('조건 지우기' 뒤에 온보딩이 다시 뜨지 않게). */
    fun clear() = prefs.edit().remove(KEY_PROFILE).remove(KEY_MODE).apply()

    companion object {
        const val FILE = "chungyak_profile"
        private const val KEY_PROFILE = "profile_v1"
        private const val KEY_MODE = "notify_mode"
        private const val KEY_ONBOARDING_DONE = "onboarding_done"

        /**
         * 예전 '연속 거주 기간(개월)' N → 전입일 = [today] − N개월(근사값). 오늘 기준 계산하면 N개월 그대로라
         * 판정 결과가 바뀌지 않는다. 전입일이 이미 있거나 기간이 미입력이면 그대로 둔다.
         */
        fun migrateResidence(p: Profile, today: LocalDate): Profile =
            if (p.residenceSince.isNotBlank() || p.residenceMonths < 0) p
            else p.copy(residenceSince = today.minusMonths(p.residenceMonths.toLong()).toString(), residenceMonths = -1)

        /**
         * 관심 지역(v0.13.0~ interestRegions). v0.12.0 이하는 시·도만 interestSido에 저장했다 → 그 값을 그대로 쓴다.
         * "경기"는 [Regions]에서 '경기 전체 = 31개 시·군 모두'라 경기 공고가 하나도 빠지지 않는다.
         */
        fun migrateInterest(o: JSONObject): Set<String> {
            val a = o.optJSONArray("interestRegions") ?: o.optJSONArray("interestSido") ?: return emptySet()
            return Regions.normalize((0 until a.length()).map { a.getString(it) }.toSet())
        }

        fun encode(p: Profile) = JSONObject()
            .put("sido", p.sido).put("residenceSince", p.residenceSince).put("sigungu", p.sigungu).put("residenceMonths", p.residenceMonths)
            .put("householdHead", p.householdHead).put("homesOwned", p.homesOwned)
            .put("everOwned", p.everOwned).put("wonWithin5y", p.wonWithin5y).put("usedSpecial", p.usedSpecial)
            .put("married", p.married).put("marriageYm", p.marriageYm).put("children", p.children)
            .put("hasNewborn", p.hasNewborn).put("supportsParent", p.supportsParent)
            .put("account", p.account.name).put("accountMonths", p.accountMonths)
            .put("accountOpened", p.accountOpened)
            .put("payments", p.payments).put("depositManwon", p.depositManwon)
            .put("householdSize", p.householdSize).put("incomePct", p.incomePct)
            .put("dualIncome", p.dualIncome).put("realEstateManwon", p.realEstateManwon)
            .put("taxYears5", p.taxYears5).put("interestRegions", JSONArray(p.interestRegions.toList()))
            // 옛 버전(v0.12.0 이하)으로 내려 설치해도 읽히게 시·도만 따로 남긴다(넓게: 일부만 골라도 그 시·도).
            .put("interestSido", JSONArray(Regions.parents(p.interestRegions).toList()))
            .put("maxPriceManwon", p.maxPriceManwon).put("minAreaM2", p.minAreaM2).put("maxAreaM2", p.maxAreaM2)
            .put("birthDate", p.birthDate).put("homelessSince", p.homelessSince).put("dependents", p.dependents)
            .put("cashManwon", p.cashManwon).put("loanLimitManwon", p.loanLimitManwon).put("monthlyCapManwon", p.monthlyCapManwon)
            .put("loanRatePct", p.loanRatePct).put("loanYears", p.loanYears).put("repayment", p.repayment.name)
            .put("downPaymentPct", p.downPaymentPct)
            .put("incomeManwon", p.incomeManwon).put("debtAnnualManwon", p.debtAnnualManwon)
            .put("rateType", p.rateType.name).put("graceYears", p.graceYears).put("sellingHome", p.sellingHome)

        fun decode(o: JSONObject) = Profile(
            sido = o.optString("sido"), sigungu = o.optString("sigungu"), residenceSince = o.optString("residenceSince"),
            residenceMonths = o.optInt("residenceMonths", -1), householdHead = o.optBoolean("householdHead"),
            homesOwned = o.optInt("homesOwned", 0), everOwned = o.optBoolean("everOwned"),
            wonWithin5y = o.optBoolean("wonWithin5y"), usedSpecial = o.optBoolean("usedSpecial"),
            married = o.optBoolean("married"), marriageYm = o.optString("marriageYm"),
            children = o.optInt("children", 0), hasNewborn = o.optBoolean("hasNewborn"),
            supportsParent = o.optBoolean("supportsParent"), account = AccountType.of(o.optString("account")),
            accountMonths = o.optInt("accountMonths", -1), accountOpened = o.optString("accountOpened"), payments = o.optInt("payments", -1),
            depositManwon = o.optInt("depositManwon", -1), householdSize = o.optInt("householdSize", -1),
            incomePct = o.optInt("incomePct", -1), dualIncome = o.optBoolean("dualIncome"),
            realEstateManwon = o.optInt("realEstateManwon", -1), taxYears5 = o.optBoolean("taxYears5"),
            interestRegions = migrateInterest(o),
            maxPriceManwon = o.optInt("maxPriceManwon", -1), minAreaM2 = o.optInt("minAreaM2", -1),
            maxAreaM2 = o.optInt("maxAreaM2", -1),
            birthDate = o.optString("birthDate"), homelessSince = o.optString("homelessSince"),
            dependents = o.optInt("dependents", -1),
            cashManwon = o.optInt("cashManwon", -1), loanLimitManwon = o.optInt("loanLimitManwon", -1),
            monthlyCapManwon = o.optInt("monthlyCapManwon", -1),
            loanRatePct = o.optDouble("loanRatePct", Profile.DEFAULT_LOAN_RATE).takeUnless { it.isNaN() } ?: Profile.DEFAULT_LOAN_RATE,
            loanYears = o.optInt("loanYears", Profile.DEFAULT_LOAN_YEARS),
            repayment = Repayment.of(o.optString("repayment")),
            downPaymentPct = o.optInt("downPaymentPct", Profile.DEFAULT_DOWN_PCT),
            incomeManwon = o.optInt("incomeManwon", -1), debtAnnualManwon = o.optInt("debtAnnualManwon", -1),
            rateType = RateType.of(o.optString("rateType")), graceYears = o.optInt("graceYears", 0),
            sellingHome = o.optBoolean("sellingHome"),
        )
    }
}
