// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Dina Yol (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.domain.publish

/** Copy from Figma "Candidates approval" (spec 0.13). */
object ReviewCopy {
    const val CHOOSE_RECIPIENT = "Choose your recipient"
    const val INTRO_TITLE = "Time to choose\nyour giveaway recipient!"
    const val INTRO_BODY = "Swipe right to approve. Swipe left to reject. You can choose only one person. Others will be auto-rejected."
    const val WHY = "Why do you need this?"
    const val APPROVED_STAMP = "Approved"
    const val REJECTED_STAMP = "Rejected"
    const val DONE_TITLE = "You have approved your\ngiveaway receiver!"
    const val DONE_BODY = "Now you can chat with each other. All other requests will be automatically rejected."
    const val PENDING_COLLECTION_FROM = "Pending collection from"
    const val CHAT = "Chat"
    const val CONTINUE = "Continue"
    const val NOTE_COLLAPSED_LINES = 4
    fun counter(position: Int, total: Int) = "$position out of $total"
}

/** A candidate as shown on the review card. */
data class Candidate(val id: String, val nickname: String, val note: String, val avatarUrl: String? = null, val exampleAvatar: Int? = null)

/**
 * Review session: one card at a time, arrows move between cards, reject removes the card and shows the next,
 * approve ends the session (others are auto-rejected server-side).
 */
data class ReviewSession(val candidates: List<Candidate>, val index: Int = 0) {
    val current: Candidate? get() = candidates.getOrNull(index)
    val total: Int get() = candidates.size
    val counter: String get() = if (candidates.isEmpty()) "" else ReviewCopy.counter(index + 1, total)
    val canPrev: Boolean get() = index > 0
    val canNext: Boolean get() = index < total - 1
    fun prev() = if (canPrev) copy(index = index - 1) else this
    fun next() = if (canNext) copy(index = index + 1) else this
    /** Rejected card leaves; the next one takes its place (or the previous one if it was the last). */
    fun reject(id: String): ReviewSession {
        val rest = candidates.filterNot { it.id == id }
        return ReviewSession(rest, index.coerceAtMost((rest.size - 1).coerceAtLeast(0)))
    }
    val isEmpty: Boolean get() = candidates.isEmpty()
}

/** Horizontal swipe decision (right = approve, left = reject), threshold as a fraction of the card width. */
object SwipeRules {
    const val THRESHOLD = 0.3f
    enum class Decision { APPROVE, REJECT, NONE }
    fun decide(dragPx: Float, cardWidthPx: Float): Decision = when {
        cardWidthPx <= 0f -> Decision.NONE
        dragPx / cardWidthPx >= THRESHOLD -> Decision.APPROVE
        dragPx / cardWidthPx <= -THRESHOLD -> Decision.REJECT
        else -> Decision.NONE
    }
}

/**
 * Educational example for users with no publications yet (1.10): a clothes giveaway with 3 requests
 * from mascot "crocodiles" — two spam-like, one relevant. Everything stays on the device.
 */
object ExamplePublication {
    const val ID = "example-my-giveaway"
    const val TITLE = "Clothes"   // v1.13: the "Example" badge is the only marker
    const val DESCRIPTION = "This is an example. Hey! Looks like some people are queueing for your giveaway. You have 3 pending requests. It’s time to choose somebody."
    val candidates = listOf(
        Candidate("ex-1", "SnappyCroc", "Is it still available?", exampleAvatar = 0),
        Candidate("ex-2", "GreenGator22", "I need it ASAP!", exampleAvatar = 1),
        Candidate(
            "ex-3", "HappyFreeToTaker",
            "Hello! Thank you for your donation. I am very interested in your lot and I am ready to pick it up when it’s convenient for you. Regards, Happy Free to Taker",
            exampleAvatar = 2,
        ),
    )
    const val MEETUP = "Example meetup point"
    const val CARD_TEXT = "Example"
    val item = app.freetotake.domain.model.Item(
        id = app.freetotake.domain.model.ItemId(ID), giverId = app.freetotake.domain.model.UserId("me"),
        title = TITLE, description = CARD_TEXT, category = "clothes",   // card secondary text; details use DESCRIPTION
        createdAt = app.freetotake.domain.model.Timestamp(0),
    )
    fun isExample(id: String) = id == ID

    /** v1.12.1: shown in My giveaways ("Action needed") while the user has no publications of their own. */
    fun overview(pendingRequests: Int = candidates.size) = app.freetotake.domain.publish.PublicationOverview(
        item = item, schedule = null, scheduleStart = null, pendingRequests = pendingRequests, collector = null,
    )

    fun withExample(mine: List<app.freetotake.domain.publish.PublicationOverview>, pendingRequests: Int = candidates.size, dismissed: Boolean = false) =
        if (mine.isEmpty() && !dismissed) listOf(overview(pendingRequests)) else mine
}
