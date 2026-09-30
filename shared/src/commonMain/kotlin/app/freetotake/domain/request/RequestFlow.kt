// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Dina Yol (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.domain.request

import app.freetotake.domain.model.ClaimStatus
import app.freetotake.domain.model.Item
import app.freetotake.domain.model.Session

/** Calendar date at the pickup place (local, no time zone). */
data class DateKey(val year: Int, val month: Int, val day: Int) : Comparable<DateKey> {
    override fun compareTo(other: DateKey) = compareValuesBy(this, other, { it.year }, { it.month }, { it.day })

    /** 0 = Monday … 6 = Sunday (Zeller-based, proleptic Gregorian). */
    val dayOfWeek: Int get() {
        var m = month; var y = year
        if (m < 3) { m += 12; y -= 1 }
        val h = (day + (13 * (m + 1)) / 5 + y + y / 4 - y / 100 + y / 400) % 7 // 0 = Saturday
        return (h + 5) % 7
    }

    fun plusDays(n: Int): DateKey {
        var d = this
        repeat(n) { d = d.next() }
        return d
    }

    private fun next(): DateKey {
        val dim = when (month) { 2 -> if (isLeap(year)) 29 else 28; 4, 6, 9, 11 -> 30; else -> 31 }
        return when {
            day < dim -> copy(day = day + 1)
            month < 12 -> DateKey(year, month + 1, 1)
            else -> DateKey(year + 1, 1, 1)
        }
    }

    override fun toString() = "$year-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"

    companion object {
        private fun isLeap(y: Int) = (y % 4 == 0 && y % 100 != 0) || y % 400 == 0
        val WEEKDAY_SHORT = listOf("Mon", "Tue", "Wed", "Thur", "Fri", "Sat", "Sun") // design labels
        fun parse(s: String): DateKey = s.take(10).split('-').let { DateKey(it[0].toInt(), it[1].toInt(), it[2].toInt()) }
    }
}

data class TimeOfDay(val hour: Int, val minute: Int) : Comparable<TimeOfDay> {
    init { require(hour in 0..23 && minute in 0..59) }
    override fun compareTo(other: TimeOfDay) = compareValuesBy(this, other, { it.hour }, { it.minute })
    fun label() = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
    companion object {
        fun parse(s: String): TimeOfDay = s.split(':').let { TimeOfDay(it[0].toInt(), it[1].toInt()) }
    }
}

/** One pickup option configured by the publisher (date + time at the pickup place). */
data class PickupSlot(val date: DateKey, val time: TimeOfDay) : Comparable<PickupSlot> {
    override fun compareTo(other: PickupSlot) = compareValuesBy(this, other, { it.date }, { it.time })
}

/** "Now" at the pickup place — the app passes the device's local date/time. */
data class LocalNow(val date: DateKey, val time: TimeOfDay)

data class DateOption(val date: DateKey, val enabled: Boolean)
data class TimeOption(val time: TimeOfDay, val enabled: Boolean)

/**
 * "Choose pickup date and time" (spec 0.8): the requester picks one of the publisher's dates,
 * then an available time on that date. Past options are never offered.
 * Date-only / time-only availability entries are not modelled yet (needs clarification — Q13).
 */
object SlotPicker {

    fun upcoming(slots: List<PickupSlot>, now: LocalNow): List<PickupSlot> =
        slots.filter { it.date > now.date || (it.date == now.date && it.time > now.time) }.distinct().sorted()

    /** Whole Mon–Sun weeks covering the upcoming slots; days without a slot are shown disabled (design). */
    fun dates(slots: List<PickupSlot>, now: LocalNow): List<DateOption> {
        val up = upcoming(slots, now)
        if (up.isEmpty()) return emptyList()
        val available = up.map { it.date }.toSet()
        var start = up.first().date
        repeat(start.dayOfWeek) { start = back(start) }
        var end = up.last().date
        while (end.dayOfWeek != 6) end = end.plusDays(1)
        val out = mutableListOf<DateOption>()
        var d = start
        while (d <= end) { out += DateOption(d, d in available); d = d.plusDays(1) }
        return out
    }

    /** Union of the publisher's time options; enabled only for the selected date (design: grey = unavailable). */
    fun times(slots: List<PickupSlot>, selectedDate: DateKey?, now: LocalNow): List<TimeOption> {
        val up = upcoming(slots, now)
        val all = up.map { it.time }.distinct().sorted()
        val forDate = up.filter { it.date == selectedDate }.map { it.time }.toSet()
        return all.map { TimeOption(it, selectedDate != null && it in forDate) }
    }

    /** Single suitable option → preselected. */
    fun defaultSelection(slots: List<PickupSlot>, now: LocalNow): PickupSlot? =
        upcoming(slots, now).singleOrNull()

    /**
     * v1.16.2: one available date → preselected; a chosen date → its first free time preselected (lime).
     * MVP: exactly one date and one time per request.
     */
    fun preselect(slots: List<PickupSlot>, now: LocalNow, draft: RequestDraft): RequestDraft {
        var d = draft
        if (d.date == null) dates(slots, now).filter { it.enabled }.singleOrNull()?.let { d = d.copy(date = it.date) }
        if (d.date != null && d.time == null) times(slots, d.date, now).firstOrNull { it.enabled }?.let { d = d.copy(time = it.time) }
        return d
    }

    private fun back(d: DateKey): DateKey {
        if (d.day > 1) return d.copy(day = d.day - 1)
        val (y, m) = if (d.month > 1) d.year to d.month - 1 else d.year - 1 to 12
        var last = DateKey(y, m, 28)
        while (last.plusDays(1).month == m) last = last.plusDays(1)
        return last
    }
}

data class RequestDraft(
    val date: DateKey? = null,
    val time: TimeOfDay? = null,
    val note: String = "",
) {
    val slot: PickupSlot? get() = if (date != null && time != null) PickupSlot(date, time) else null
}

sealed interface RequestCheck {
    data object Ok : RequestCheck
    data object LoginRequired : RequestCheck
    data object SlotRequired : RequestCheck
    data object SlotNotOffered : RequestCheck
    data object NoteTooLong : RequestCheck
    data object OwnItem : RequestCheck
    data object NotClaimable : RequestCheck
}

object RequestRules {
    /** Provisional (spec: "approximately 1,000 characters, to be confirmed"). */
    const val NOTE_MAX = 1_000
    const val NOTE_PLACEHOLDER = "Few words why you need this to help the author choose you."
    const val LOGIN_SHEET_TITLE = "Log in to finish this action"
    const val SENT_TITLE = "Your request has been sent!"
    const val SENT_BODY = "Wait for the owner to review your submission. Check your homepage and turn on notifications to track your status."
    const val GO_HOME = "Go to homepage"
    const val DESCRIPTION_COLLAPSED_LINES = 3

    /** Order matters: form errors first, then sign-in (so the user never logs in to a broken form). */
    fun check(session: Session, item: Item, draft: RequestDraft, slots: List<PickupSlot>, now: LocalNow): RequestCheck {
        if (!item.status.acceptsClaims) return RequestCheck.NotClaimable
        val slot = draft.slot ?: return RequestCheck.SlotRequired
        if (slot !in SlotPicker.upcoming(slots, now)) return RequestCheck.SlotNotOffered
        if (draft.note.length > NOTE_MAX) return RequestCheck.NoteTooLong
        val user = (session as? Session.Authenticated)?.user ?: return RequestCheck.LoginRequired
        if (user.id == item.giverId) return RequestCheck.OwnItem
        return RequestCheck.Ok
    }

    fun canSend(draft: RequestDraft): Boolean = draft.slot != null && draft.note.length <= NOTE_MAX
}

/** Requester-facing status of a request ("My claims"). */
object PickupStatusPolicy {
    fun label(status: ClaimStatus): String = when (status) {
        ClaimStatus.PENDING_APPROVAL -> "Pending for approval"
        ClaimStatus.APPROVED -> "Approved"
        ClaimStatus.REJECTED -> "Rejected"
        ClaimStatus.CANCELLED_BY_TAKER, ClaimStatus.CANCELLED_LATE -> "Cancelled"
        ClaimStatus.CLOSED -> "Closed"
        ClaimStatus.COMPLETED -> "Collected"
    }

    /** Only an approved request authorizes collection. */
    fun canCollect(status: ClaimStatus) = status == ClaimStatus.APPROVED

    /** Visually dimmed until the publisher approves. */
    fun dimmed(status: ClaimStatus) = !canCollect(status)
}

/** Share link placeholder — final format/destination not defined yet (Q12). */
object ShareLink {
    fun forItem(item: Item) = "Free to Take: ${item.title} — https://freetotake.app/g/${item.id.value}"
}

/**
 * Educational "Free to Take" example (spec 0.7) gets demo availability so the request flow can be tried
 * before real publications exist. Requests on it are kept on the device only — never sent to the backend.
 */
object ExampleRequest {
    const val MEETUP_AREA = "Example meetup point — the real address is shared after approval"
    private val TIMES = listOf(TimeOfDay(9, 0), TimeOfDay(10, 30), TimeOfDay(12, 0), TimeOfDay(18, 0))

    /** Every other day for the next ~2 weeks, starting tomorrow (so there are always enabled + disabled days). */
    fun slots(today: DateKey): List<PickupSlot> =
        (1..13 step 2).flatMap { d -> TIMES.map { PickupSlot(today.plusDays(d), it) } }
}

/** v1.14: educational pending request on the food example (device only). */
object ExampleClaim {
    const val ID = "example-claim"
    fun isExample(id: String) = id == ID

    /**
     * v1.16.4: the seeded demo request belongs to "My claims" only. Opened from Available Giveaways the example shows the
     * request form with "Send request" (a mocked, device-only request); a request the user really sent is always shown.
     */
    fun shownOnDetails(claim: app.freetotake.domain.model.Claim?, openedFromMyClaims: Boolean): app.freetotake.domain.model.Claim? =
        claim?.takeUnless { !openedFromMyClaims && isExample(it.id.value) }
    fun claim(user: app.freetotake.domain.model.UserId?, today: DateKey, now: app.freetotake.domain.model.Timestamp) =
        app.freetotake.domain.model.Claim(
            id = app.freetotake.domain.model.ClaimId(ID),
            itemId = app.freetotake.domain.catalog.ExampleListing.item.id,
            takerId = user ?: app.freetotake.domain.model.UserId("guest"),
            status = app.freetotake.domain.model.ClaimStatus.PENDING_APPROVAL,
            createdAt = now,
            message = "Hi! I would love to pick this up.",
            requested = ExampleRequest.slots(today).first(),
        )
}

/** "My claims" (Home, spec 0.8): the requester's own open requests, newest first. */
object MyClaimsCopy {
    const val TITLE = "My claims"
    fun submittedLabel(date: DateKey): String =
        "You submitted request on ${MONTHS[date.month - 1]}, ${date.day} ${date.year}."
    private val MONTHS = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
}
