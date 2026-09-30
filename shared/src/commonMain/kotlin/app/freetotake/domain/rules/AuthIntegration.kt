// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
// Copyright (C) 2026 Dina Yol (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.domain.rules

/**
 * Sign-in is deferred (spec: "sign in will come later"). While [AuthMode.DEFERRED] the app
 * skips the auth prompt, but every place where sign-in must be embedded is listed here and
 * covered by a placeholder test, so nothing is forgotten when the Auth step lands.
 */
enum class AuthMode { DEFERRED, ENFORCED }

enum class AuthIntegrationPoint(val description: String, val implemented: Boolean = false) {
    CREATE_GIVEAWAY("Start → create first giveaway requires an account (POST_ITEM)", implemented = true),
    PICKUP_POINT_SYNC("Giveaway pickup point is saved on the device; must be saved to the account for the user's publications"),
    SUBMIT_REQUEST("Sending a request (SUBMIT_CLAIM)", implemented = true),
    MY_CLAIMS("My claims section / chip (VIEW_MY_CLAIMS)", implemented = true),
    MY_PUBLICATIONS("My publications tab (VIEW_MY_ITEMS)", implemented = true),
    PROFILE("Profile tab", implemented = true),
    RESUME_AFTER_SIGN_IN("AuthGate resume of the pending action after real sign-in (debug user today)", implemented = true),
}

object AuthModePolicy {
    /** Current product decision. Flip to ENFORCED in the Auth step. */
    val CURRENT: AuthMode = AuthMode.ENFORCED   // v1.12: real sign-in

    /** In DEFERRED mode protected actions proceed without prompting (placeholder behaviour). */
    fun shouldPrompt(mode: AuthMode, decision: AccessDecision): Boolean =
        mode == AuthMode.ENFORCED && decision is AccessDecision.LoginRequired
}
