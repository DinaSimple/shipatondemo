// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Dina Elokhova (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.domain.rules

import app.freetotake.domain.model.Session
import app.freetotake.domain.model.Timestamp

/** Everything a user can try to do. Public catalog is open; acting requires a valid session. */
enum class UserAction(val requiresAuth: Boolean) {
    // public
    BROWSE_FEED(false),
    VIEW_ITEM(false),
    SEARCH(false),

    // protected (General Requirements)
    POST_ITEM(true),                 // create a giveaway
    MANAGE_OWN_ITEM(true),           // edit / withdraw / republish a giveaway
    SUBMIT_CLAIM(true),              // send a request
    CANCEL_CLAIM(true),
    REVIEW_REQUESTS(true),           // approve (swipe right) / decline (swipe left)
    PRIVATE_CHAT(true),
    VIEW_PICKUP_DETAILS(true),       // exact meetup point / time after approval
    VIEW_MY_CLAIMS(true),            // "My Claims" catalog chip
    VIEW_MY_ITEMS(true),             // My Applications
    FAVORITES(true),
}

sealed interface AccessDecision {
    data object Allowed : AccessDecision
    /** UI shows the auth-required prompt, then resumes [resumeAction] after success. */
    data class LoginRequired(val resumeAction: UserAction) : AccessDecision
}

object AccessPolicy {
    /** Absent, restoring or expired session → gate applies. */
    fun check(session: Session, action: UserAction, now: Timestamp? = null): AccessDecision =
        if (action.requiresAuth && !session.isValidAt(now)) {
            AccessDecision.LoginRequired(action)
        } else {
            AccessDecision.Allowed
        }
}
