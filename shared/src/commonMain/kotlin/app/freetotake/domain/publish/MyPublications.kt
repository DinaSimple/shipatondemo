// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
// Copyright (C) 2026 Dina Yol (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.domain.publish

import app.freetotake.domain.model.Item
import app.freetotake.domain.model.ItemStatus
import app.freetotake.domain.model.Timestamp
import app.freetotake.domain.request.DateKey
import app.freetotake.domain.request.TimeOfDay

/** Publisher's pickup schedule as published (one date, From–To). */
data class PickupSchedule(val date: DateKey, val from: TimeOfDay, val to: TimeOfDay)

/** Private meetup details (giver + approved collector only). */
data class MeetupDetails(val notes: String = "")   // no apartment/entrance/floor (sensitive, 1.9.1)

/** Assigned collector (approved request). */
data class Collector(val nickname: String, val pickupAt: Timestamp)

/** One of my publications with what the list needs (spec 0.12). */
data class PublicationOverview(
    val item: Item,
    val schedule: PickupSchedule?,
    /** Start of the published schedule as an instant (server-computed in the item's time zone). */
    val scheduleStart: Timestamp?,
    val pendingRequests: Int,
    val collector: Collector?,
)

enum class PublicationsTab(val label: String) { ACTIVE("Active"), ARCHIVED("Archived"); companion object { val DEFAULT = ACTIVE } }

/** Sections of the Active tab + Archived (Figma "My active publications" / "My archived publications"). */
enum class PublicationSection(val title: String, val subtitle: String) {
    WAITING("Waiting for requests", "Waiting for claims"),
    ACTION_NEEDED("Action needed", "Review requests and choose who will pick up your giveaways"),
    PENDING_COLLECTION("Pending collection", "Don’t forget to meet at the scheduled date and time"),
    ARCHIVED("Archived", "Your cancelled or given away goods.");

    val tab: PublicationsTab get() = if (this == ARCHIVED) PublicationsTab.ARCHIVED else PublicationsTab.ACTIVE
}

object MyPublicationsRules {
    const val TITLE = "My publications"
    const val EMPTY = "Nothing here yet."          // Figma copy (spec wording: "No things here yet")
    const val CREATE = "Create"
    const val CANCEL_CUTOFF_HOURS = 2L
    const val DELETE_QUESTION = "Are you sure you want to delete your publication?"
    const val DELETE_NO = "Cancel"
    const val DELETE_YES = "Yes, delete"
    const val DELETED_TITLE = "Publication deleted"
    const val DELETED_BODY = "Your announcement is removed. Your pending recipients will be informed about your decision."
    /** Sent to the assigned collector when the publisher removes the publication (spec 0.12). */
    const val COLLECTOR_NOTICE = "Sorry, the publisher removed this publication. Please try your luck again next time. We are very sorry."
    const val ARCHIVED_BADGE = "Archived"

    fun section(p: PublicationOverview, now: Timestamp): PublicationSection {
        val expired = p.item.expiresAt?.let { now >= it } ?: false
        return when (p.item.status) {
            ItemStatus.GIVEN, ItemStatus.WITHDRAWN -> PublicationSection.ARCHIVED
            ItemStatus.RESERVED -> PublicationSection.PENDING_COLLECTION
            // Late cancel by the collector: the publisher has to decide → needs action.
            ItemStatus.AWAITING_GIVER_DECISION -> if (expired) PublicationSection.ARCHIVED else PublicationSection.ACTION_NEEDED
            ItemStatus.AVAILABLE -> when {
                expired -> PublicationSection.ARCHIVED
                p.pendingRequests > 0 -> PublicationSection.ACTION_NEEDED
                else -> PublicationSection.WAITING
            }
        }
    }

    /** Active sections in design order; each list newest first. */
    fun grouped(list: List<PublicationOverview>, tab: PublicationsTab, now: Timestamp): List<Pair<PublicationSection, List<PublicationOverview>>> =
        PublicationSection.entries.filter { it.tab == tab }
            .map { s -> s to list.filter { section(it, now) == s }.sortedByDescending { it.item.createdAt.epochMillis } }
            .filter { it.second.isNotEmpty() }

    /** Prefill the Create form to edit an existing publication (photos stay in storage unless removed). */
    fun draftFor(p: PublicationOverview, pickup: app.freetotake.domain.location.PickupPoint?, details: MeetupDetails): PublicationDraft =
        PublicationDraft(
            title = p.item.title, description = p.item.description,
            category = app.freetotake.domain.catalog.Category.from(p.item.category),
            date = p.schedule?.date, from = p.schedule?.from, to = p.schedule?.to,
            pickup = pickup, notes = details.notes,
            editingItemId = p.item.id.value,
            storedPhotos = p.item.photoPaths.zip(p.item.photoUrls) { path, url -> StoredPhoto(path, url) },
        )

    fun pendingLabel(n: Int) = if (n == 1) "1 pending request" else "$n pending requests"

    /**
     * Cancel/unpublish: up to 2 h before the scheduled pickup start (the collector's slot once assigned,
     * otherwise the start of the published schedule). Applied to every active state (Q25 to confirm).
     */
    fun canCancel(p: PublicationOverview, now: Timestamp): Boolean {
        if (ExamplePublication.isExample(p.item.id.value)) return true   // v1.15.4: the example can be removed (device only)
        if (section(p, now) == PublicationSection.ARCHIVED) return false
        val start = p.collector?.pickupAt ?: p.scheduleStart ?: return true
        return start.epochMillis - now.epochMillis > CANCEL_CUTOFF_HOURS * 3_600_000L
    }

    /** Editing: while no collector is assigned (after assignment not specified — Q27). */
    fun canEdit(p: PublicationOverview, now: Timestamp): Boolean =
        section(p, now) == PublicationSection.WAITING || section(p, now) == PublicationSection.ACTION_NEEDED

    /** Archived cards are not clickable (design); see Q26. */
    fun isClickable(p: PublicationOverview, now: Timestamp): Boolean = section(p, now) != PublicationSection.ARCHIVED

    /** "today" as in the design, otherwise dd/MM/yy. */
    fun dateLabel(d: DateKey, today: DateKey): String =
        if (d == today) "today" else "${d.day.pad()}/${d.month.pad()}/${(d.year % 100).pad()}"

    fun timeRangeLabel(s: PickupSchedule): String = "${Clock12.compact(s.from)}-${Clock12.compact(s.to)}"

    private fun Int.pad() = toString().padStart(2, '0')
}
