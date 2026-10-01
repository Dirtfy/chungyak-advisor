package com.chungyak.advisor.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PriceFormatTest {

    @Test fun full_koreanUnits() {
        assertEquals("9억 1,000만원", PriceFormat.full(91000))
        assertEquals("12억", PriceFormat.full(120000))
        assertEquals("8,500만원", PriceFormat.full(8500))
        assertEquals("24억 5,500만원", PriceFormat.full(245500))
        assertEquals("정보 없음", PriceFormat.full(0))
    }

    @Test fun short_and_range() {
        assertEquals("5.2억", PriceFormat.short(52000))
        assertEquals("9억", PriceFormat.short(90000))
        assertEquals("8.6억~10.8억", PriceFormat.range(86200, 107500))
        assertEquals("10.9억", PriceFormat.range(108650, 108650))
        assertEquals("10.9억", PriceFormat.range(0, 108650))
        assertEquals("정보 없음", PriceFormat.range(0, 0))
    }

    @Test fun area_and_perPyeong() {
        // 브라운스톤 월곡 센트럴 059.9442A: 공급 81.6956㎡, 91,000만원 (청약홈 공고와 대조)
        assertEquals("81.70㎡ (24.7평)", PriceFormat.area(81.6956))
        assertEquals(3682, PriceFormat.perPyeongManwon(91000, 81.6956))
        assertEquals(0, PriceFormat.perPyeongManwon(91000, 0.0))
        assertEquals("정보 없음", PriceFormat.area(0.0))
    }
}
