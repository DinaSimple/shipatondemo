// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
// Copyright (C) 2026 Dina Yol (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.domain

import app.freetotake.domain.profile.ChangePasswordForm
import app.freetotake.domain.profile.MyProfile
import app.freetotake.domain.profile.NotificationKind
import app.freetotake.domain.profile.NotificationPrefs
import app.freetotake.domain.profile.NotificationText
import app.freetotake.domain.profile.PasswordPolicy
import app.freetotake.domain.profile.PasswordRule
import app.freetotake.domain.profile.ProfileRules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** v1.11 — spec 0.14 My Profile. */
class ProfileTest {
    private val me = MyProfile("u1", "mysteriouscapybara123", null, null, "tester@test.test", isAnonymous = false)

    @Test fun r_1_11_01_public_name_replaces_generated_nickname() {
        assertEquals("@mysteriouscapybara123", me.displayName)
        assertTrue(me.showsNickname)
        assertEquals("Martin", me.copy(publicName = "Martin").displayName)
        assertEquals("@mysteriouscapybara123", me.copy(publicName = "  ").displayName)
        assertEquals("Martin", ProfileRules.normalizedPublicName("  Martin "))
        assertNull(ProfileRules.normalizedPublicName("   "))
    }

    @Test fun r_1_11_02_placeholder_avatar_stable_per_user() {
        val a = ProfileRules.placeholderAvatar("user-a")
        assertEquals(a, ProfileRules.placeholderAvatar("user-a"))
        assertTrue(a in 0 until ProfileRules.PLACEHOLDER_AVATARS)
    }

    @Test fun r_1_11_03_password_rules() {
        assertEquals(emptyList(), PasswordPolicy.unmet("PaSSworD1!"))
        assertEquals(PasswordRule.entries.toList(), PasswordPolicy.unmet(""))
        assertEquals(listOf(PasswordRule.SPECIAL), PasswordPolicy.unmet("PaSSworD111"))
        assertEquals(listOf(PasswordRule.LENGTH), PasswordPolicy.unmet("Aa1!"))
        assertEquals(listOf(PasswordRule.UPPER), PasswordPolicy.unmet("abc12!"))
    }

    @Test fun r_1_11_04_change_password_form() {
        assertFalse(ChangePasswordForm.canSave("", "x", "x"))
        assertTrue(ChangePasswordForm.canSave("o", "n", "c"))
        val bad = ChangePasswordForm.validate("PaSSworD", "PaSSworD111", "PaSSworD112")
        assertEquals(listOf(PasswordRule.SPECIAL), bad.new)
        assertEquals("Passwords don’t match", bad.confirm)
        assertTrue(ChangePasswordForm.validate("old", "New1pass!", "New1pass!").isValid)
    }

    @Test fun r_1_11_05_email_and_notification_texts() {
        assertTrue(ProfileRules.isValidEmail("test@test.test")); assertFalse(ProfileRules.isValidEmail("test@test"))
        assertEquals(NotificationPrefs(true, true, true), NotificationPrefs())
        assertEquals(listOf("My claim approved", "New pickup request received", "New giveaways nearby added"), NotificationKind.entries.map { it.label })
        assertEquals("Your claim was approved!", NotificationText.title("claim_approved", "Lamp"))
        assertTrue(NotificationText.body("new_request", "Lamp", null).contains("Lamp"))
        assertEquals("custom", NotificationText.body("publication_removed", "Lamp", "custom"))
    }
}
