// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
// Copyright (C) 2026 Dina Elokhova (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.domain.monetization

import app.freetotake.domain.model.Timestamp

/** Moments after which a full-screen ad may appear (v1.16). */
enum class AdMoment { REQUEST_SENT, PUBLICATION_CREATED }

/**
 * Interstitial rules: only after a real request / a new publication (never for the demo cards),
 * never for users with the ad-free entitlement (RevenueCat), and at most one ad every [MIN_GAP_MS].
 */
object AdPolicy {
    const val MIN_GAP_MS = 120_000L
    /** RevenueCat entitlement identifier that removes ads (create it in the RevenueCat dashboard). */
    const val AD_FREE_ENTITLEMENT = "no_ads"

    fun shouldShow(moment: AdMoment, isDemo: Boolean, adFree: Boolean, lastShownAt: Timestamp?, now: Timestamp): Boolean =
        !isDemo && !adFree && (lastShownAt == null || now.epochMillis - lastShownAt.epochMillis >= MIN_GAP_MS)
}
