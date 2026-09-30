// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Dina Yol (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.domain

import app.freetotake.domain.model.Timestamp
import app.freetotake.domain.monetization.AdMoment
import app.freetotake.domain.monetization.AdPolicy
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** v1.16 — interstitial after a request / a new publication. */
class AdsTest {
    @Test fun r_1_16_1_ad_after_request_or_publication() {
        assertTrue(AdPolicy.shouldShow(AdMoment.REQUEST_SENT, isDemo = false, adFree = false, lastShownAt = null, now = Timestamp(0)))
        assertTrue(AdPolicy.shouldShow(AdMoment.PUBLICATION_CREATED, false, false, null, Timestamp(0)))
    }

    @Test fun r_1_16_2_no_ad_for_demo_or_ad_free() {
        assertFalse(AdPolicy.shouldShow(AdMoment.REQUEST_SENT, isDemo = true, adFree = false, lastShownAt = null, now = Timestamp(0)))
        assertFalse(AdPolicy.shouldShow(AdMoment.REQUEST_SENT, isDemo = false, adFree = true, lastShownAt = null, now = Timestamp(0)))
    }

    @Test fun r_1_16_3_frequency_cap() {
        assertFalse(AdPolicy.shouldShow(AdMoment.REQUEST_SENT, false, false, Timestamp(1_000), Timestamp(1_000 + AdPolicy.MIN_GAP_MS - 1)))
        assertTrue(AdPolicy.shouldShow(AdMoment.REQUEST_SENT, false, false, Timestamp(1_000), Timestamp(1_000 + AdPolicy.MIN_GAP_MS)))
    }
}
