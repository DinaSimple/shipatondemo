// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Dina Yol (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.domain

import app.freetotake.domain.rewards.BroccoliCopy
import app.freetotake.domain.rewards.BroccoliRules
import app.freetotake.domain.rewards.PendingHandover
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BroccoliTest {
    @Test fun r_1_17_02_start_20_request_minus_1_demo_free() {
        assertEquals(20, BroccoliRules.INITIAL)
        assertEquals(19, BroccoliRules.afterRequest(20, example = false))
        assertEquals(20, BroccoliRules.afterRequest(20, example = true), "demo cards never burn")
        assertEquals(0, BroccoliRules.afterRequest(0, example = false), "never negative")
    }

    @Test fun r_1_17_03_blocked_at_zero_except_demo() {
        assertFalse(BroccoliRules.canRequest(0, example = false))
        assertTrue(BroccoliRules.canRequest(0, example = true))
        assertTrue(BroccoliRules.canRequest(1, example = false))
    }

    @Test fun r_1_17_04_post_and_confirmed_collection_earn() {
        assertEquals(21, BroccoliRules.afterPost(20))
        assertEquals(22, BroccoliRules.afterCollected(21, collected = true))
        assertEquals(21, BroccoliRules.afterCollected(21, collected = false))
    }

    @Test fun r_1_17_05_copy() {
        assertEquals("You have 20 broccolies!", BroccoliCopy.balance(20))
        assertEquals("You have 1 broccoli!", BroccoliCopy.balance(1))
        assertEquals("Did @SnappyCroc pick up “Lamp”?", BroccoliCopy.confirmTitle(PendingHandover("i", "Lamp", "SnappyCroc")))
    }
}
