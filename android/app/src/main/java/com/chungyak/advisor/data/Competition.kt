package com.chungyak.advisor.data

import androidx.room.Entity

/**
 * One 경쟁률 row of a 공고 from the 청약홈 경쟁률 API
 * (ApplyhomeInfoCmpetRtSvc/v1/getAPTLttotPblancCmpet): 주택형 × 순위 × 거주지역.
 * Only exists after 접수; cached per 공고 and re-fetched until the 공고 is final.
 */
@Entity(tableName = "competitions", primaryKeys = ["noticeId", "modelNo", "rank", "resideCode"])
data class Competition(
    val noticeId: String,     // Notice.id (HOUSE_MANAGE_NO:PBLANC_NO)
    val modelNo: String,      // MODEL_NO
    val houseType: String,    // HOUSE_TY
    val rank: Int,            // SUBSCRPT_RANK_CODE 순위(1, 2)
    val resideCode: String,   // RESIDE_SECD 01 해당지역 / 02 기타지역 / 03 기타경기
    val resideName: String,   // RESIDE_SENM
    val units: Int,           // SUPLY_HSHLDCO 공급세대수
    val requests: Int,        // REQ_CNT 접수건수
    val rate: Double,         // CMPET_RATE 경쟁률(n:1). 미달이면 접수/공급(<1), 모르면 0
    val rateText: String,     // CMPET_RATE 원문 (예: "12.34", "(△3)")
    val shortfall: Boolean,   // 미달 여부
)
