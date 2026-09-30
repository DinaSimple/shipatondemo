// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Dina Yol (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.domain.model

/**
 * Side effects produced by rules. The data layer turns them into notifications
 * (server-side in RPCs; client only renders them). Keeps rules free of I/O.
 */
sealed interface DomainEvent {
    data class NotifyGiver(val giverId: UserId, val itemId: ItemId, val notice: GiverNotice) : DomainEvent
}

enum class GiverNotice {
    /** Pending requester removed their request: they left the queue and can no longer be approved (0.9). */
    TAKER_LEFT_QUEUE,
    /** Approved taker cancelled > 8h before pickup; giveaway is open for requests again. */
    PICKUP_CANCELLED_REOPENED,
    /** Approved taker cancelled ≤ 8h before pickup; giver must publish the giveaway again. */
    PICKUP_CANCELLED_REPUBLISH_NEEDED,
}
