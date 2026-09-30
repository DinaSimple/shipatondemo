// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
// Copyright (C) 2026 Dina Elokhova (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.domain

import app.freetotake.domain.catalog.CardBadge
import app.freetotake.domain.catalog.CardStates
import app.freetotake.domain.catalog.ClaimFilter
import app.freetotake.domain.catalog.ExampleListing
import app.freetotake.domain.catalog.MyClaims
import app.freetotake.domain.chat.ChatCopy
import app.freetotake.domain.chat.ChatError
import app.freetotake.domain.chat.ChatInfo
import app.freetotake.domain.chat.ChatItem
import app.freetotake.domain.chat.ChatMessage
import app.freetotake.domain.chat.ChatRules
import app.freetotake.domain.model.Claim
import app.freetotake.domain.model.ClaimId
import app.freetotake.domain.model.ClaimStatus
import app.freetotake.domain.model.Item
import app.freetotake.domain.model.ItemId
import app.freetotake.domain.model.ItemStatus
import app.freetotake.domain.model.Timestamp
import app.freetotake.domain.model.UserId
import app.freetotake.domain.request.DateKey
import app.freetotake.domain.request.ExampleClaim
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** v1.14 — chat, Finished claims, example pending claim. */
class ChatTest {
    private val today = DateKey(2026, 10, 1)
    private fun msg(id: String, at: Long, day: DateKey, mine: Boolean = true) = ChatMessage(id, mine, "m$id", Timestamp(at), day)
    private val info = ChatInfo("i", "Chair", "@Bob", null, Timestamp(1_000), open = true)

    @Test fun r_1_14_1_copy_matches_figma() {
        assertEquals("Chat about Chair", ChatCopy.title("Chair"))
        assertEquals("Chat with @Bob", ChatCopy.subtitle("@Bob"))
        assertEquals("No messages here!", ChatCopy.EMPTY_TITLE)
        assertEquals("Write something here", ChatCopy.PLACEHOLDER)
    }

    @Test fun r_1_14_2_composer_only_before_meeting_and_while_server_open() {
        assertTrue(ChatRules.canSend(info, Timestamp(999)))
        assertFalse(ChatRules.canSend(info, Timestamp(1_000)), "meeting time reached → read-only")
        assertFalse(ChatRules.canSend(info.copy(open = false), Timestamp(0)), "server closed")
        assertFalse(ChatRules.canSend(null, Timestamp(0)), "not a participant")
    }

    @Test fun r_1_14_3_text_rules() {
        assertEquals(ChatError.EMPTY, ChatRules.validate("   "))
        assertEquals(ChatError.TOO_LONG, ChatRules.validate("x".repeat(1001)))
        assertNull(ChatRules.validate(" hi "))
        assertEquals("hi", ChatRules.normalized("  hi "))
        assertNull(ChatRules.normalized(" "))
        assertEquals(ChatError.CLOSED, ChatError.of("P0001: CHAT_CLOSED"))
        assertEquals(ChatError.FORBIDDEN, ChatError.of("CHAT_FORBIDDEN"))
        assertEquals(ChatError.NETWORK, ChatError.of(null))
    }

    @Test fun r_1_14_4_timeline_has_day_separators() {
        val y = DateKey(2026, 9, 30)
        val t = ChatRules.timeline(listOf(msg("b", 20, today), msg("a", 10, y, mine = false), msg("c", 30, today)), today)
        assertEquals(listOf("Yesterday", "a", "Today", "b", "c"), t.map { when (it) { is ChatItem.Day -> it.label; is ChatItem.Message -> it.message.id } })
        assertEquals("28 Sep", ChatRules.dayLabel(DateKey(2026, 9, 28), today))
    }

    @Test fun r_1_14_5_finished_chip_for_collected_items() {
        val item = Item(ItemId("x"), UserId("g"), "Lamp", "", status = ItemStatus.GIVEN, createdAt = Timestamp(0))
        val claim = Claim(ClaimId("c"), item.id, UserId("me"), ClaimStatus.COMPLETED, Timestamp(1))
        val state = CardStates.myClaims(item, claim, Timestamp(2))
        assertEquals(listOf(CardBadge.FINISHED), state.badges)
        assertFalse(state.showTrash)
        assertTrue(ClaimFilter.FINISHED.matches(state))
        assertFalse(ClaimFilter.APPROVED.matches(state))
        assertEquals("Finished", ClaimFilter.FINISHED.label)
        assertEquals(1, MyClaims.of(UserId("me"), listOf(claim), listOf(item)).size, "finished claims stay in My claims")
    }

    @Test fun r_1_14_6_example_pending_claim_until_dismissed_or_real_claims() {
        val ex = ExampleClaim.claim(null, today, Timestamp(5))
        assertEquals(ClaimStatus.PENDING_APPROVAL, ex.status)
        assertEquals(ExampleListing.item.id, ex.itemId)
        val shown = MyClaims.withExample(emptyList(), dismissed = false, example = ex)
        assertEquals(1, shown.size)
        assertTrue(CardStates.myClaims(shown.single().item, ex, Timestamp(5)).showTrash, "can cancel it")
        assertTrue(MyClaims.withExample(emptyList(), dismissed = true, example = ex).isEmpty())
        val real = MyClaims.Entry(ex.copy(id = ClaimId("r")), ExampleListing.item)
        assertEquals(listOf(real), MyClaims.withExample(listOf(real), dismissed = false, example = ex))
    }

    @Test fun r_1_14_2_guest_profile_copy_matches_figma() {
        assertEquals("You have no account yet!", app.freetotake.domain.profile.ProfileCopy.GUEST_LINE1)
        assertEquals("Sign up", app.freetotake.domain.profile.ProfileCopy.GUEST_SIGN_UP)
        assertEquals("to start posting and claiming.", app.freetotake.domain.profile.ProfileCopy.GUEST_LINE2)
    }

    @Test fun r_1_15_4_one_control_per_section() {
        val item = Item(ItemId("x"), UserId("g"), "Lamp", "", status = ItemStatus.AVAILABLE, createdAt = Timestamp(0))
        val a = CardStates.available(item)
        assertEquals(listOf(CardBadge.AVAILABLE), a.badges); assertTrue(a.showHeart); assertFalse(a.showTrash)
        val ex = CardStates.available(ExampleListing.item)
        assertEquals(listOf(CardBadge.AVAILABLE), ex.badges, "demo card: one 'Available' tag"); assertTrue(ex.showHeart); assertFalse(ex.showTrash)
        val pending = Claim(ClaimId("c"), item.id, UserId("me"), ClaimStatus.PENDING_APPROVAL, Timestamp(1))
        val c = CardStates.myClaimCard(item, pending, Timestamp(2))
        assertEquals(listOf(CardBadge.AVAILABLE), c.badges, "pending claim: one 'Available' tag"); assertFalse(c.showHeart); assertTrue(c.showTrash)
        assertTrue(ClaimFilter.PENDING.matches(CardStates.myClaims(item, pending, Timestamp(2))), "Pending filter still works")
        val g = CardStates.myGiveaway(example = true)
        assertEquals(listOf(CardBadge.PENDING_MY_APPROVAL), g.badges); assertEquals("Pending my approval", CardBadge.PENDING_MY_APPROVAL.label); assertFalse(g.showHeart)
        assertEquals("Clothes", app.freetotake.domain.publish.ExamplePublication.item.title)
        assertEquals("Example", app.freetotake.domain.publish.ExamplePublication.item.description); assertTrue(g.showTrash)
        assertTrue(app.freetotake.domain.publish.ExamplePublication.withExample(emptyList(), dismissed = true).isEmpty())
    }
}

class ExampleOwnerChatTest {
    @Test fun r_1_16_5_01_free_food_example_chat_is_device_only_with_owner_reply() {
        val food = app.freetotake.domain.catalog.ExampleListing.ID
        kotlin.test.assertTrue(app.freetotake.domain.chat.ExampleChat.isExample(food), "requester-side demo chat")
        kotlin.test.assertTrue(app.freetotake.domain.chat.ExampleChat.isExample(app.freetotake.domain.publish.ExamplePublication.ID), "publisher-side demo chat")
        kotlin.test.assertFalse(app.freetotake.domain.chat.ExampleChat.isExample("real-item"))
        kotlin.test.assertEquals(app.freetotake.domain.chat.ExampleChat.OWNER_REPLY, app.freetotake.domain.chat.ExampleChat.reply(food))
        kotlin.test.assertEquals(app.freetotake.domain.chat.ExampleChat.REPLY, app.freetotake.domain.chat.ExampleChat.reply(app.freetotake.domain.publish.ExamplePublication.ID))
    }
}
