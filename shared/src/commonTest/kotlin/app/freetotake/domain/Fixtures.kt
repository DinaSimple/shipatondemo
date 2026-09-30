// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Dina Yol (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.domain

import app.freetotake.domain.model.Claim
import app.freetotake.domain.model.ClaimId
import app.freetotake.domain.model.ClaimStatus
import app.freetotake.domain.model.DomainError
import app.freetotake.domain.model.DomainResult
import app.freetotake.domain.model.Item
import app.freetotake.domain.model.ItemId
import app.freetotake.domain.model.ItemStatus
import app.freetotake.domain.model.Session
import app.freetotake.domain.model.Timestamp
import app.freetotake.domain.model.User
import app.freetotake.domain.model.UserId
import kotlin.test.assertEquals
import kotlin.test.fail

object F {
    const val HOUR = 3_600_000L
    val NOW = Timestamp(1_800_000_000_000L)

    val giver = User(UserId("giver"), "Giver")
    val alice = User(UserId("alice"), "Alice")
    val bob = User(UserId("bob"), "Bob")

    fun item(status: ItemStatus = ItemStatus.AVAILABLE) =
        Item(ItemId("item-1"), giver.id, "Chair", "Wooden chair", status = status, createdAt = NOW)

    fun claim(
        taker: User = alice,
        status: ClaimStatus = ClaimStatus.PENDING_APPROVAL,
        pickupAt: Timestamp? = null,
        id: String = "c-${taker.id.value}",
    ) = Claim(ClaimId(id), ItemId("item-1"), taker.id, status, NOW, pickupAt = pickupAt)

    fun session(u: User) = Session.Authenticated(u)
}

fun <T> DomainResult<T>.ok(): T = when (this) {
    is DomainResult.Ok -> value
    is DomainResult.Err -> fail("Expected Ok, got $error")
}

fun DomainResult<*>.assertErr(expected: DomainError) {
    when (this) {
        is DomainResult.Ok -> fail("Expected $expected, got Ok($value)")
        is DomainResult.Err -> assertEquals(expected, error)
    }
}
