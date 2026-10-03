package com.chungyak.advisor.data

import androidx.room.Entity

/**
 * One 주택형 of a 공고 from the 청약홈 주택형별 상세 API (getAPTLttotPblancMdl).
 * Cached on-device per 공고 so the 2차 호출 happens once, not on every poll.
 */
@Entity(tableName = "house_models", primaryKeys = ["noticeId", "modelNo"])
data class HouseModel(
    val noticeId: String,     // Notice.id (HOUSE_MANAGE_NO:PBLANC_NO)
    val modelNo: String,      // MODEL_NO
    val houseType: String,    // HOUSE_TY (예: 084.9458A)
    val supplyArea: Double,   // SUPLY_AR 공급면적(㎡), 0 = 정보 없음
    val units: Int,           // SUPLY_HSHLDCO 일반공급 세대수
    val priceManwon: Int,     // LTTOT_TOP_AMOUNT 분양최고금액(만원), 0 = 정보 없음
    // 특별공급 세대수(개인화 매칭용, v0.5.0~). 0 = 해당 물량 없음.
    val spTotal: Int = 0,        // SPSPLY_HSHLDCO
    val spMultiChild: Int = 0,   // MNYCH_HSHLDCO 다자녀
    val spNewlywed: Int = 0,     // NWWDS_HSHLDCO 신혼부부
    val spFirstLife: Int = 0,    // LFE_FRST_HSHLDCO 생애최초
    val spOldParent: Int = 0,    // OLD_PARNTS_SUPORT_HSHLDCO 노부모부양
    val spInstitution: Int = 0,  // INSTT_RECOMEND_HSHLDCO 기관추천
    val spNewborn: Int = 0,      // NWBB_HSHLDCO 신생아
    val spYouth: Int = 0,        // YGMN_HSHLDCO 청년
) {
    /** HOUSE_TY "084.9458A" → 전용면적 84.9458㎡. 숫자가 없으면 0. */
    val exclusiveArea: Double
        get() = Regex("^\\d+(\\.\\d+)?").find(houseType.trim())?.value?.toDoubleOrNull() ?: 0.0
}
