package com.chungyak.advisor.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One 청약 공고 (APT 일반공급) as collected from the 청약홈 분양정보 API and
 * stored on-device. [id] is the stable key `HOUSE_MANAGE_NO:PBLANC_NO` used to
 * deduplicate across polls so a given 공고 only notifies once.
 */
@Entity(tableName = "notices")
data class Notice(
    @PrimaryKey val id: String,
    val houseManageNo: String,
    val pblancNo: String,
    val name: String,
    val areaName: String,        // SUBSCRPT_AREA_CODE_NM — 서울/경기/인천
    val address: String,
    val totalUnits: Int,
    val noticeDate: String,      // RCRIT_PBLANC_DE (모집공고일, ISO yyyy-MM-dd)
    val rank1Start: String,      // 일반공급 1순위 해당지역 접수 시작
    val rank1End: String,        // 일반공급 1순위 해당지역 접수 종료
    val resultDate: String,      // PRZWNER_PRESNATN_DE (당첨자 발표일)
    val houseKind: String,       // HOUSE_SECD_NM (주택 구분: APT 등)
    val speculationArea: Boolean, // 투기과열지구 여부
    val adjustmentArea: Boolean,  // 조정대상지역 여부
    val url: String,             // 청약홈 공고 상세 URL
    val homepage: String,
    // 분양가(주택형별 최고분양가 LTTOT_TOP_AMOUNT, 만원)의 최소/최대. 0 = 미확인.
    val priceMinManwon: Int = 0,
    val priceMaxManwon: Int = 0,
    // 주택형별 상세를 받아 캐시한 시각(epoch millis). 0 = 아직 못 받음 → 다음 폴링에서 재시도.
    val modelsFetchedAt: Long = 0,
    // 경쟁률 요약(1순위 해당지역 기준, 없으면 전체). 0 = 데이터 없음.
    val cmpetMaxRate: Double = 0.0,
    val cmpetAvgRate: Double = 0.0,
    // 경쟁률을 마지막으로 받은 시각(0 = 아직) / 확정 여부(true면 더 이상 재조회 안 함).
    val cmpetFetchedAt: Long = 0,
    val cmpetFinal: Boolean = false,
    // HOUSE_DTL_SECD_NM 주택상세구분: "민영" / "국민". "" = 미확인(v0.4.4 이하에서 수집) → 다음 폴링에서 채움.
    val houseDtl: String = "",
    // BSNS_MBY_NM 사업주체(시행사) / CNSTRCT_ENTRPS_NM 시공사(v0.10.0~, 검색용). "" = 미확인 → 다음 폴링에서 채움.
    val builder: String = "",
    val contractor: String = "",
    val firstSeen: Long,         // epoch millis this row was first collected
    val notified: Boolean,       // whether a local notification was already posted
)
