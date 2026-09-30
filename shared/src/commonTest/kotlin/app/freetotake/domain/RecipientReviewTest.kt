// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Dina Elokhova (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.domain

import app.freetotake.domain.publish.Candidate
import app.freetotake.domain.publish.ExamplePublication
import app.freetotake.domain.publish.ReviewCopy
import app.freetotake.domain.publish.ReviewSession
import app.freetotake.domain.publish.SwipeRules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** v1.10 — spec 0.13 Recipient approval. */
class RecipientReviewTest {
    private val c = (1..3).map { Candidate("c$it", "n$it", "note $it") }

    @Test fun r_1_10_01_first_request_by_default_counter_and_arrows() {
        val s = ReviewSession(c)
        assertEquals("c1", s.current?.id)
        assertEquals("1 out of 3", s.counter)
        assertFalse(s.canPrev); assertTrue(s.canNext)
        assertEquals("2 out of 3", s.next().counter)
        assertEquals("3 out of 3", s.next().next().next().counter)   // stops at the last
        assertEquals("1 out of 3", s.prev().counter)
    }

    @Test fun r_1_10_02_reject_removes_card_and_advances() {
        val s = ReviewSession(c).reject("c1")
        assertEquals("c2", s.current?.id); assertEquals("1 out of 2", s.counter)
        val last = ReviewSession(c, 2).reject("c3")
        assertEquals("c2", last.current?.id)
        val empty = ReviewSession(listOf(c[0])).reject("c1")
        assertTrue(empty.isEmpty); assertNull(empty.current); assertEquals("", empty.counter)
    }

    @Test fun r_1_10_03_swipe_right_approves_left_rejects() {
        assertEquals(SwipeRules.Decision.APPROVE, SwipeRules.decide(120f, 300f))
        assertEquals(SwipeRules.Decision.REJECT, SwipeRules.decide(-120f, 300f))
        assertEquals(SwipeRules.Decision.NONE, SwipeRules.decide(50f, 300f))
    }

    @Test fun r_1_10_04_copy_from_design() {
        assertTrue(ReviewCopy.INTRO_BODY.startsWith("Swipe right to approve. Swipe left to reject."))
        assertTrue(ReviewCopy.DONE_BODY.contains("automatically rejected"))
        assertEquals("Choose your recipient", ReviewCopy.CHOOSE_RECIPIENT)
    }

    @Test fun r_1_10_05_example_publication_three_candidates() {
        assertEquals(3, ExamplePublication.candidates.size)
        assertEquals(listOf("Is it still available?", "I need it ASAP!"), ExamplePublication.candidates.take(2).map { it.note })
        assertTrue(ExamplePublication.candidates[2].note.startsWith("Hello! Thank you for your donation."))
        assertTrue(ExamplePublication.DESCRIPTION.contains("You have 3 pending requests"))
        assertEquals("Clothes", ExamplePublication.TITLE)   // v1.13: badge only
    }

    @Test fun r_1_10_06_intro_shown_once_per_device() {
        val prefs = app.freetotake.domain.prefs.AppPreferences(app.freetotake.domain.prefs.InMemoryKeyValueStore())
        assertFalse(prefs.reviewIntroSeen)
        prefs.reviewIntroSeen = true
        assertTrue(prefs.reviewIntroSeen)
    }
}
