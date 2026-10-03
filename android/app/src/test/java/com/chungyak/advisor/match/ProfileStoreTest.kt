package com.chungyak.advisor.match

import com.chungyak.advisor.api.ApplyHomeClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class ProfileStoreTest {

    @Test fun roundTrip_keepsEveryField() {
        val p = Profile(
            sido = "서울", sigungu = "강남구", residenceMonths = 60, householdHead = true, homesOwned = 1,
            everOwned = true, wonWithin5y = true, usedSpecial = true, married = true, marriageYm = "2022-03",
            children = 2, hasNewborn = true, supportsParent = true, account = AccountType.DEPOSIT,
            accountMonths = 30, payments = 30, depositManwon = 600, householdSize = 4, incomePct = 120,
            dualIncome = true, realEstateManwon = 20_000, taxYears5 = true, interestSido = setOf("서울", "경기"),
            maxPriceManwon = 120_000, minAreaM2 = 59, maxAreaM2 = 85,
        )
        assertEquals(p, ProfileStore.decode(JSONObject(ProfileStore.encode(p).toString())))
        assertFalse(ProfileStore.decode(JSONObject()).isSet)
    }

    /** MDAT_TRGET_AREA_SECD: "N"은 비조정 — 예전엔 비어 있지 않으면 조정으로 봤다. */
    @Test fun adjustmentFlag() {
        assertTrue(ApplyHomeClient.adjustment("Y"))
        assertFalse(ApplyHomeClient.adjustment("N"))
        assertFalse(ApplyHomeClient.adjustment(""))
        assertFalse(ApplyHomeClient.adjustment(" n "))
    }
}
