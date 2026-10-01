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
)
