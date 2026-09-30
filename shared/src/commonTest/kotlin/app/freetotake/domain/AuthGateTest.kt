// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Dina Elokhova (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.domain

import app.freetotake.domain.F.NOW
import app.freetotake.domain.model.Session
import app.freetotake.domain.model.Timestamp
import app.freetotake.domain.rules.AccessDecision
import app.freetotake.domain.rules.AccessPolicy
import app.freetotake.domain.rules.AuthGate
import app.freetotake.domain.rules.GateResult
import app.freetotake.domain.rules.PendingAction
import app.freetotake.domain.rules.ResumeResult
import app.freetotake.domain.rules.UserAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthGateTest {

    private val request = PendingAction(UserAction.SUBMIT_CLAIM, "item-1")

    @Test fun r_1_1_01_all_spec_protected_actions_require_auth() {
        listOf(
            UserAction.POST_ITEM, UserAction.MANAGE_OWN_ITEM, UserAction.SUBMIT_CLAIM,
            UserAction.REVIEW_REQUESTS, UserAction.PRIVATE_CHAT, UserAction.VIEW_PICKUP_DETAILS,
            UserAction.VIEW_MY_CLAIMS,
        ).forEach { assertTrue(it.requiresAuth, "$it must be protected") }
    }

    @Test fun r_1_1_02_guest_attempt_shows_prompt_and_remembers_action() {
        val gate = AuthGate()
        assertEquals(GateResult.ShowAuthPrompt(request), gate.attempt(Session.Guest, request))
        assertEquals(request, gate.pending)
    }

    @Test fun r_1_1_03_after_sign_in_action_resumes_once() {
        val gate = AuthGate()
        gate.attempt(Session.Guest, request)
        assertEquals(ResumeResult.Resume(request), gate.onAuthenticated(F.session(F.alice)))
        assertNull(gate.pending)
        assertEquals(ResumeResult.NothingToResume, gate.onAuthenticated(F.session(F.alice)))
    }

    @Test fun r_1_1_04_action_not_resumed_when_no_longer_valid() {
        val gate = AuthGate()
        gate.attempt(Session.Guest, request)
        assertEquals(ResumeResult.NoLongerValid(request), gate.onAuthenticated(F.session(F.alice)) { false })
        assertNull(gate.pending)
    }

    @Test fun r_1_1_05_cancelled_auth_drops_pending_action() {
        val gate = AuthGate()
        gate.attempt(Session.Guest, request)
        gate.onAuthCancelled()
        assertEquals(ResumeResult.NothingToResume, gate.onAuthenticated(F.session(F.alice)))
    }

    @Test fun r_1_1_06_valid_session_proceeds_without_prompt() {
        val gate = AuthGate()
        assertEquals(GateResult.Proceed(request), gate.attempt(F.session(F.alice), request))
        assertNull(gate.pending)
    }

    @Test fun r_1_1_07_expired_or_restoring_session_applies_gate() {
        val expired = Session.Authenticated(F.alice, expiresAt = Timestamp(NOW.epochMillis - 1))
        assertEquals(AccessDecision.LoginRequired(UserAction.SUBMIT_CLAIM), AccessPolicy.check(expired, UserAction.SUBMIT_CLAIM, NOW))
        assertEquals(AccessDecision.LoginRequired(UserAction.SUBMIT_CLAIM), AccessPolicy.check(Session.Restoring, UserAction.SUBMIT_CLAIM, NOW))
        val valid = Session.Authenticated(F.alice, expiresAt = NOW.plusHours(1))
        assertEquals(AccessDecision.Allowed, AccessPolicy.check(valid, UserAction.SUBMIT_CLAIM, NOW))
        // public actions never gated
        assertEquals(AccessDecision.Allowed, AccessPolicy.check(expired, UserAction.VIEW_ITEM, NOW))
    }

    @Test fun r_1_1_08_resume_waits_until_session_is_valid() {
        val gate = AuthGate()
        gate.attempt(Session.Guest, request)
        assertEquals(ResumeResult.NothingToResume, gate.onAuthenticated(Session.Guest))
        assertEquals(request, gate.pending, "still pending while signed out")
    }
}
