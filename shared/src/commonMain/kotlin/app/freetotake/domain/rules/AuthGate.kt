// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
// Copyright (C) 2026 Dina Yol (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.domain.rules

import app.freetotake.domain.model.Session
import app.freetotake.domain.model.Timestamp

/** A protected action the user tried, with the thing it targets (item id, claim id…). */
data class PendingAction(
    val action: UserAction,
    val targetId: String? = null,
)

sealed interface GateResult {
    data class Proceed(val request: PendingAction) : GateResult
    /** Show the authentication-required prompt; the request is remembered. */
    data class ShowAuthPrompt(val request: PendingAction) : GateResult
}

sealed interface ResumeResult {
    data class Resume(val request: PendingAction) : ResumeResult
    /** e.g. the item was taken while the user was signing in. */
    data class NoLongerValid(val request: PendingAction) : ResumeResult
    data object NothingToResume : ResumeResult
}

/**
 * Remembers the protected action a guest attempted and resumes it after sign-in,
 * only if it is still valid. Pure state holder — one instance per app session.
 */
class AuthGate {
    var pending: PendingAction? = null
        private set

    fun attempt(session: Session, request: PendingAction, now: Timestamp? = null): GateResult =
        when (AccessPolicy.check(session, request.action, now)) {
            AccessDecision.Allowed -> GateResult.Proceed(request)
            is AccessDecision.LoginRequired -> {
                pending = request
                GateResult.ShowAuthPrompt(request)
            }
        }

    /** User closed the prompt / auth screen without signing in. */
    fun onAuthCancelled() {
        pending = null
    }

    /**
     * Call when the session becomes valid. [isStillValid] checks current server state
     * (item still available, claim still pending…).
     */
    fun onAuthenticated(
        session: Session,
        now: Timestamp? = null,
        isStillValid: (PendingAction) -> Boolean = { true },
    ): ResumeResult {
        val request = pending ?: return ResumeResult.NothingToResume
        if (!session.isValidAt(now)) return ResumeResult.NothingToResume // keep pending, still signed out
        pending = null
        return if (isStillValid(request)) ResumeResult.Resume(request) else ResumeResult.NoLongerValid(request)
    }
}
