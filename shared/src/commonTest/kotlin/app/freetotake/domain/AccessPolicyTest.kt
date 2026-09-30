// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
// Copyright (C) 2026 Dina Yol (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.domain

import app.freetotake.domain.model.Session
import app.freetotake.domain.rules.AccessDecision
import app.freetotake.domain.rules.AccessPolicy
import app.freetotake.domain.rules.UserAction
import kotlin.test.Test
import kotlin.test.assertEquals

class AccessPolicyTest {

    @Test fun r_1_0_01_guest_can_browse_view_and_search() {
        listOf(UserAction.BROWSE_FEED, UserAction.VIEW_ITEM, UserAction.SEARCH).forEach {
            assertEquals(AccessDecision.Allowed, AccessPolicy.check(Session.Guest, it), "guest blocked from $it")
        }
    }

    @Test fun r_1_0_02_guest_is_sent_to_login_for_protected_actions_and_action_is_resumed() {
        UserAction.entries.filter { it.requiresAuth }.forEach {
            assertEquals(AccessDecision.LoginRequired(it), AccessPolicy.check(Session.Guest, it))
        }
    }

    @Test fun r_1_0_03_logged_in_user_has_both_give_and_take_roles() {
        val s = F.session(F.alice)
        UserAction.entries.forEach { assertEquals(AccessDecision.Allowed, AccessPolicy.check(s, it)) }
    }
}
