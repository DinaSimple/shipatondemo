// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
// Copyright (C) 2026 Dina Yol (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.domain

import app.freetotake.domain.auth.AuthCopy
import app.freetotake.domain.auth.AuthError
import app.freetotake.domain.auth.AuthFlow
import app.freetotake.domain.auth.AuthLink
import app.freetotake.domain.auth.AuthRules
import app.freetotake.domain.auth.AuthStep
import app.freetotake.domain.auth.LinkPurpose
import app.freetotake.domain.auth.NewPasswordForm
import app.freetotake.domain.legal.Terms
import app.freetotake.domain.profile.PasswordRule
import app.freetotake.domain.rules.AuthIntegrationPoint
import app.freetotake.domain.rules.AuthMode
import app.freetotake.domain.rules.AuthModePolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** v1.12 — Login and auth. */
class AuthTest {
    private val host = "dina.github.io"
    private val tok = "AbCdEfGhIjKlMnOpQrStUvWxYz_-0123456789abcd"

    @Test fun r_1_12_1_copy_matches_figma() {
        assertEquals("Sorry, try a different email or use forgot a password option", AuthError.EMAIL_TAKEN.message)
        assertEquals("This email is not registered in the service", AuthError.NOT_REGISTERED.message)
        assertEquals("Wrong or missing value", AuthError.WRONG_CREDENTIALS.message)
        assertEquals("Check my email app", AuthCopy.CHECK_EMAIL_APP)
        assertEquals("You’re registered!", AuthCopy.REGISTERED_TITLE)
        assertEquals("Log in to start searching or posting your giveaways!", AuthCopy.SUCCESS_CARD)
    }

    @Test fun r_1_12_2_login_validation_and_server_rejection() {
        assertEquals(true, AuthRules.loginErrors("", "").email)
        assertEquals(true, AuthRules.loginErrors("", "").password)
        assertTrue(AuthRules.loginErrors(" test@test.test ", "x").isValid)
        assertFalse(AuthRules.loginErrors("test@test", "x").isValid)
        // Wrong password: both fields flagged, no hint which one (no account enumeration on login).
        assertTrue(AuthRules.WRONG_CREDENTIALS.email && AuthRules.WRONG_CREDENTIALS.password)
        assertEquals(AuthCopy.WRONG_VALUE, AuthRules.emailError("nope"))
        assertNull(AuthRules.emailError("a@b.co"))
        assertEquals("a@b.co", AuthRules.normalizeEmail("  A@B.co "))
    }

    @Test fun r_1_12_3_new_password_twice_with_all_rules() {
        assertFalse(NewPasswordForm.canContinue("Abc1!x", ""))
        assertTrue(NewPasswordForm.validate("Somegoodpassword111!", "Somegoodpassword111!").isValid)
        val weak = NewPasswordForm.validate("abc", "abc")
        assertEquals(listOf(PasswordRule.LENGTH, PasswordRule.UPPER, PasswordRule.DIGIT, PasswordRule.SPECIAL), weak.password)
        assertEquals(AuthCopy.PASSWORDS_DIFFER, NewPasswordForm.validate("Abcdef1!", "Abcdef1?").confirm)
    }

    @Test fun r_1_12_4_links_only_from_trusted_host_or_app_scheme() {
        assertEquals(AuthLink(tok, LinkPurpose.SIGN_UP), AuthLink.parse("https://$host/auth/confirm/?t=$tok&p=signup", host))
        assertEquals(AuthLink(tok, LinkPurpose.RECOVER), AuthLink.parse("https://DINA.github.io/auth/confirm?t=$tok&p=recover", host))
        assertEquals(AuthLink(tok, LinkPurpose.SIGN_UP), AuthLink.parse("freetotake://auth/confirm?t=$tok&p=signup", host))
        assertNull(AuthLink.parse("https://evil.example/auth/confirm?t=$tok&p=signup", host))
        assertNull(AuthLink.parse("https://$host/other?t=$tok&p=signup", host))
        assertNull(AuthLink.parse("http://$host/auth/confirm?t=$tok&p=signup", host))
        assertNull(AuthLink.parse("https://$host/auth/confirm?t=short&p=signup", host))
        assertNull(AuthLink.parse("https://$host/auth/confirm?t=$tok&p=admin", host))
        assertNull(AuthLink.parse("https://$host/auth/confirm?t=$tok&p=signup", null), "no host configured → https links ignored")
    }

    @Test fun r_1_12_5_flow_signup_and_recovery_end_at_login() {
        val create = AuthFlow.afterRedeem(LinkPurpose.SIGN_UP, "a@b.co")
        assertEquals(AuthStep.CreatePassword(LinkPurpose.SIGN_UP, "a@b.co"), create)
        assertEquals(AuthStep.Success(LinkPurpose.SIGN_UP, "a@b.co"), AuthFlow.afterPasswordSet(LinkPurpose.SIGN_UP, "a@b.co"))
        assertEquals(AuthStep.Login, AuthFlow.afterSuccess())
        assertEquals(AuthStep.Login, AuthFlow.backFrom(AuthStep.SignUpEmail), "log in available throughout")
        assertEquals(AuthStep.Login, AuthFlow.backFrom(AuthStep.Recovery))
        assertNull(AuthFlow.backFrom(AuthStep.Login))
    }

    @Test fun r_1_12_6_expired_link_offers_resend() {
        val expired = AuthFlow.afterRedeemFailed(AuthError.LINK_EXPIRED, "a@b.co", LinkPurpose.RECOVER) as AuthStep.LinkProblem
        assertTrue(AuthFlow.canResend(expired))
        assertFalse(AuthFlow.canResend(AuthStep.LinkProblem(AuthError.LINK_USED, null, LinkPurpose.SIGN_UP)))
        assertEquals(AuthError.UNKNOWN, AuthError.of("WHATEVER"))
        assertEquals(AuthError.RATE_LIMITED, AuthError.of("RATE_LIMITED"))
    }

    @Test fun r_1_12_7_auth_enforced_and_terms_present() {
        assertEquals(AuthMode.ENFORCED, AuthModePolicy.CURRENT)
        assertTrue(AuthIntegrationPoint.entries.filter { it != AuthIntegrationPoint.PICKUP_POINT_SYNC }.all { it.implemented })
        assertEquals(20, Terms.blocks.count { it.heading })
        assertTrue(Terms.blocks.first().text.startsWith("Free to Take is operated"))
        assertTrue(Terms.blocks.none { "Free to Give" in it.text || "FREE TO GIVE" in it.text }, "v1.12.1: service name is Free to Take")
    }

    @Test fun r_1_12_8_example_giveaway_teaches_approval_until_first_publication() {
        val now = app.freetotake.domain.model.Timestamp(1_000)
        val list = app.freetotake.domain.publish.ExamplePublication.withExample(emptyList())
        assertEquals(1, list.size)
        val ex = list.single()
        assertEquals(3, ex.pendingRequests)
        assertEquals(app.freetotake.domain.publish.PublicationSection.ACTION_NEEDED, app.freetotake.domain.publish.MyPublicationsRules.section(ex, now))
        assertTrue(app.freetotake.domain.publish.MyPublicationsRules.canCancel(ex, now), "v1.15.4: example can be removed")
        assertTrue(app.freetotake.domain.publish.MyPublicationsRules.isClickable(ex, now))
        val mine = listOf(ex.copy(item = ex.item.copy(id = app.freetotake.domain.model.ItemId("real"))))
        assertEquals(mine, app.freetotake.domain.publish.ExamplePublication.withExample(mine), "gone once the user publishes")
        assertEquals("Clothes", app.freetotake.domain.publish.ExamplePublication.TITLE, "v1.13: no '(example)' in titles")
        assertEquals("Free food", app.freetotake.domain.catalog.ExampleListing.TITLE)
    }

    @Test fun r_1_13_1_image_captcha_rules() {
        val c = app.freetotake.domain.auth.CaptchaChallenge("id", "cars", List(9) { "t$it" })
        assertEquals("files/cap/t3.jpg", app.freetotake.domain.auth.CaptchaRules.tilePath(c.tiles[3]))
        assertEquals("Select all images with cars", AuthCopy.captchaPrompt(c.label))
        var sel = emptySet<Int>()
        assertFalse(app.freetotake.domain.auth.CaptchaRules.canVerify(sel))
        sel = app.freetotake.domain.auth.CaptchaRules.toggle(sel, 2); sel = app.freetotake.domain.auth.CaptchaRules.toggle(sel, 5)
        assertEquals(setOf(2, 5), sel)
        sel = app.freetotake.domain.auth.CaptchaRules.toggle(sel, 2)
        assertEquals(setOf(5), sel)
        // one puzzle per flow: a passed check is reused for 14 min (server keeps it 15) until an email is sent
        assertTrue(app.freetotake.domain.auth.CaptchaRules.reusable(0, 13 * 60_000L))
        assertFalse(app.freetotake.domain.auth.CaptchaRules.reusable(0, 14 * 60_000L))
        assertFalse(app.freetotake.domain.auth.CaptchaRules.reusable(null, 0))
        assertEquals(AuthError.CAPTCHA_REQUIRED, AuthError.of("CAPTCHA_REQUIRED"))
        assertTrue(runCatching { app.freetotake.domain.auth.CaptchaChallenge("x", "cars", listOf("a")) }.isFailure, "always 3×3")
    }

    @Test fun r_1_13_2_one_empty_state_copy() {
        assertEquals("Nothing here yet.", app.freetotake.domain.catalog.HomeCopy.EMPTY)
        assertEquals("Nothing here yet.", app.freetotake.domain.catalog.HomeCopy.AVAILABLE_EMPTY)
        assertEquals("Nothing here yet.", app.freetotake.domain.profile.ProfileCopy.EMPTY)
    }

    @Test fun r_1_15_1_google_sign_in_messages() {
        assertEquals("Continue with Google", AuthCopy.GOOGLE)
        assertNull(app.freetotake.domain.auth.GoogleCopy.message(app.freetotake.domain.auth.GoogleResult.Cancelled), "cancel is silent")
        assertNull(app.freetotake.domain.auth.GoogleCopy.message(app.freetotake.domain.auth.GoogleResult.Token("t", "n")))
        assertEquals(app.freetotake.domain.auth.GoogleCopy.NOT_CONFIGURED, app.freetotake.domain.auth.GoogleCopy.message(app.freetotake.domain.auth.GoogleResult.NotConfigured))
        assertEquals(app.freetotake.domain.auth.GoogleCopy.NO_ACCOUNT, app.freetotake.domain.auth.GoogleCopy.message(app.freetotake.domain.auth.GoogleResult.NoAccount))
        val google = app.freetotake.domain.profile.MyProfile("u", "n", null, null, "a@gmail.com", isAnonymous = false, passwordLogin = false)
        assertFalse(google.passwordLogin, "Google accounts have no password to change")
    }

    @Test fun r_1_15_2_facebook_copy_and_callback_is_not_an_email_link() {
        assertEquals("Continue with Facebook", AuthCopy.FACEBOOK)
        assertNull(AuthLink.parse("freetotake://login-callback?code=abc", host), "OAuth return is handled by Supabase, not as an email link")
    }

    @Test fun r_1_15_3_email_flow_hidden_but_kept() {
        assertFalse(app.freetotake.domain.auth.AuthFeatures.EMAIL_FLOW)
        listOf(AuthStep.Login, AuthStep.SignUpEmail, AuthStep.Recovery).forEach {
            assertTrue(app.freetotake.domain.auth.AuthFeatures.showsSocialOnly(it), "$it → Google/Facebook page")
            assertFalse(app.freetotake.domain.auth.AuthFeatures.showsSocialOnly(it, emailFlow = true), "flag on → email screens back")
        }
        assertFalse(app.freetotake.domain.auth.AuthFeatures.showsSocialOnly(AuthStep.Success(LinkPurpose.SIGN_UP, "a@b.co")))
    }

    @Test fun r_1_16_4_buttons_only_for_enabled_providers() {
        assertFalse(app.freetotake.domain.auth.AuthFeatures.showProvider("google", null), "v1.16.3: loading/offline → hidden (fail-safe)")
        assertTrue(app.freetotake.domain.auth.AuthFeatures.showProvider("google", setOf("google", "email")))
        assertFalse(app.freetotake.domain.auth.AuthFeatures.showProvider("facebook", setOf("google", "email")))
        assertFalse(app.freetotake.domain.auth.AuthFeatures.showProvider("facebook", emptySet()), "v1.16.3: failed check → hidden")
    }
}

class SocialPageTest {
    @Test fun r_1_16_5_02_profile_sign_up_shows_sign_up_page_per_figma() {
        val up = app.freetotake.domain.auth.AuthFeatures.socialPage(app.freetotake.domain.auth.AuthStep.SignUpEmail)
        kotlin.test.assertEquals("Sign up", up.title)
        kotlin.test.assertEquals("Already have an account? ", up.bottomPrefix); kotlin.test.assertEquals("Sign in", up.bottomLink)
        kotlin.test.assertEquals(app.freetotake.domain.auth.AuthStep.Login, up.switchTo)
        val login = app.freetotake.domain.auth.AuthFeatures.socialPage(app.freetotake.domain.auth.AuthStep.Login)
        kotlin.test.assertEquals("Log in", login.title); kotlin.test.assertEquals("Register", login.bottomLink)
        kotlin.test.assertEquals(app.freetotake.domain.auth.AuthStep.SignUpEmail, login.switchTo)
    }
}
