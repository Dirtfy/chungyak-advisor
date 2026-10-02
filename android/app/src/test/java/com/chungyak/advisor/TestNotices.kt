package com.chungyak.advisor

import com.chungyak.advisor.data.Notice

/** 테스트용 공고 생성기 — 필요한 필드만 바꿔 쓴다. */
fun notice(
    id: String,
    noticeDate: String = "2026-09-01",
    rank1Start: String = "",
    rank1End: String = "",
    resultDate: String = "",
    priceMin: Int = 0,
    priceMax: Int = 0,
    cmpetMax: Double = 0.0,
    cmpetAvg: Double = 0.0,
    cmpetFetchedAt: Long = 0,
    cmpetFinal: Boolean = false,
) = Notice(
    id = id, houseManageNo = id, pblancNo = id, name = id, areaName = "경기", address = "",
    totalUnits = 0, noticeDate = noticeDate, rank1Start = rank1Start, rank1End = rank1End,
    resultDate = resultDate, houseKind = "APT", speculationArea = false, adjustmentArea = false,
    url = "", homepage = "", priceMinManwon = priceMin, priceMaxManwon = priceMax,
    cmpetMaxRate = cmpetMax, cmpetAvgRate = cmpetAvg, cmpetFetchedAt = cmpetFetchedAt,
    cmpetFinal = cmpetFinal, firstSeen = 0, notified = true,
)
