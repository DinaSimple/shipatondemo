// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
// Copyright (C) 2026 Dina Yol (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.domain.publish

import app.freetotake.domain.catalog.Category
import app.freetotake.domain.location.PickupPoint
import app.freetotake.domain.media.ImagePolicy
import app.freetotake.domain.request.DateKey
import app.freetotake.domain.request.LocalNow
import app.freetotake.domain.request.PickupSlot
import app.freetotake.domain.request.TimeOfDay

/** Copy from Figma "Create a publication flow". */
object PublishCopy {
    const val SCREEN_TITLE = "Create your publication"
    const val LOGIN_TITLE = "Log in to create a publication"
    const val TITLE_PLACEHOLDER = "What are you giving away?"
    const val DESCRIPTION_PLACEHOLDER = "Tell us why you’re giving it away, note any defects or signs of wear, and share any other details that might be helpful to the collector."
    const val WHEN_LABEL = "When do you want to give it away?"
    const val WHEN_PLACEHOLDER = "Choose date"
    const val TIME_LABEL = "What time do you want to meet?"
    const val TIME_PLACEHOLDER = "Choose time"
    const val LOCATION_LABEL = "Meetup location"
    const val LOCATION_PLACEHOLDER = "Insert your preferred address"
    const val DATE_SHEET_TITLE = "Choose date"
    const val DATE_SHEET_SUBTITLE = "Select your preferred date"   // single date (answer Q24)
    const val TIME_SHEET_TITLE = "Choose time"
    const val TIME_SHEET_SUBTITLE = "Select your preferred time"
    const val PHOTOS_LABEL = "Add one photo"
    const val PHOTOS_CARD_TITLE = "Add photos of your giveaway"
    const val PICK_EXISTING = "Select existing photo"
    const val TAKE_PICTURE = "Take a picture"
    const val MAX_PHOTOS_ALERT = "Sorry, the maximum photo limit is exceeded. You can attach not more than 3 photos."
    const val PHOTOS_HINT = "Add at least 1 photo, up to 3. Photos are compressed on your phone."
    const val PHOTOS_CLEANUP_NOTE = "Photos are deleted automatically 14 days after your publication expires."
    const val READY_TITLE = "Ready!"
    const val READY_BODY = "Your new publication is now live. Get requests and choose one recipient for approval."
    const val CONTINUE = "Continue"
}

/** 12-hour clock used by the time sheet (design: hour : minutes AM/PM). */
data class Clock12(val hour: Int, val minute: Int, val pm: Boolean) {
    fun to24(): TimeOfDay? {
        if (hour !in 1..12 || minute !in 0..59) return null
        val h = when { hour == 12 && !pm -> 0; hour == 12 && pm -> 12; pm -> hour + 12; else -> hour }
        return TimeOfDay(h, minute)
    }

    companion object {
        fun of(t: TimeOfDay) = Clock12(if (t.hour % 12 == 0) 12 else t.hour % 12, t.minute, t.hour >= 12)
        /** "4:30 pm" as in the design field. */
        /** "9:50PM" as on My publications cards. */
        fun compact(t: TimeOfDay): String = of(t).let { "${it.hour}:${t.minute.toString().padStart(2, '0')}${if (it.pm) "PM" else "AM"}" }
        fun label(t: TimeOfDay): String = of(t).let { "${it.hour}:${t.minute.toString().padStart(2, '0')} ${if (it.pm) "pm" else "am"}" }
    }
}

data class PublicationDraft(
    val title: String = "",
    val description: String = "",
    val category: Category? = null,
    val date: DateKey? = null,
    val from: TimeOfDay? = null,
    val to: TimeOfDay? = null,
    val pickup: PickupPoint? = null,
    // Apartment / entrance / floor are deliberately NOT collected (sensitive, product decision 1.9.1).
    val notes: String = "",
    /** Local photo references (platform URIs), compressed + uploaded on Publish. */
    val photos: List<String> = emptyList(),
    /** Editing (spec 0.12): the publication being edited and its already uploaded photos. */
    val editingItemId: String? = null,
    val storedPhotos: List<StoredPhoto> = emptyList(),
) {
    val photoCount: Int get() = photos.size + storedPhotos.size
}

/** Photo already in storage: [path] in the bucket, [url] to display it. */
data class StoredPhoto(val path: String, val url: String)

enum class DraftIssue { PHOTO_MISSING, TITLE_MISSING, TITLE_TOO_LONG, DESCRIPTION_TOO_LONG, CATEGORY_MISSING, DATE_MISSING, DATE_IN_PAST, TIME_MISSING, TIME_RANGE_INVALID, TIME_IN_PAST, LOCATION_MISSING, NOTES_TOO_LONG, TOO_MANY_PHOTOS }

object PublicationRules {
    const val TITLE_MAX = 80                 // provisional
    const val DESCRIPTION_MAX = 1_000        // spec 0.11 (proposed)
    const val NOTES_MAX = 300                // provisional
    const val SLOT_STEP_MINUTES = 30         // requesters pick a 30-min slot inside the range (spec 0.8 picker)
    /** Listing lifetime (spec 0.11 "~2 weeks"). */
    const val LIFETIME_DAYS = 14
    /** Answer Q22: photo files are deleted this many days after the publication expired (server job). */
    const val PHOTO_RETENTION_AFTER_EXPIRY_DAYS = 14

    /** Form step (Next). Description optional; photos are checked on the photos step (answer Q23: required). */
    fun issues(d: PublicationDraft, now: LocalNow): List<DraftIssue> = buildList {
        if (d.title.isBlank()) add(DraftIssue.TITLE_MISSING)
        if (d.title.trim().length > TITLE_MAX) add(DraftIssue.TITLE_TOO_LONG)
        if (d.description.length > DESCRIPTION_MAX) add(DraftIssue.DESCRIPTION_TOO_LONG)
        if (d.category == null) add(DraftIssue.CATEGORY_MISSING)
        if (d.date == null) add(DraftIssue.DATE_MISSING) else if (d.date < now.date) add(DraftIssue.DATE_IN_PAST)
        if (d.from == null || d.to == null) add(DraftIssue.TIME_MISSING)
        else {
            if (d.from >= d.to) add(DraftIssue.TIME_RANGE_INVALID)
            else if (d.date == now.date && d.from <= now.time) add(DraftIssue.TIME_IN_PAST)
        }
        if (d.pickup == null) add(DraftIssue.LOCATION_MISSING)
        if (d.notes.length > NOTES_MAX) add(DraftIssue.NOTES_TOO_LONG)
        if (d.photoCount > ImagePolicy.MAX_PHOTOS) add(DraftIssue.TOO_MANY_PHOTOS)
    }

    fun canContinue(d: PublicationDraft, now: LocalNow) = issues(d, now).isEmpty()

    /** v1.16.9: the hint under a disabled Next names the actual problem (e.g. a time that already passed today). */
    fun hint(d: PublicationDraft, now: LocalNow): String? {
        val all = issues(d, now)
        return when {
            all.isEmpty() -> null
            DraftIssue.TIME_IN_PAST in all -> "This time has already passed today. Choose a later time or another day."
            DraftIssue.DATE_IN_PAST in all -> "This date is in the past. Choose today or a later day."
            DraftIssue.TIME_RANGE_INVALID in all -> "The end time must be after the start time."
            DraftIssue.TITLE_TOO_LONG in all -> "Keep the title under $TITLE_MAX characters."
            DraftIssue.DESCRIPTION_TOO_LONG in all -> "Keep the description under $DESCRIPTION_MAX characters."
            DraftIssue.NOTES_TOO_LONG in all -> "Keep the meetup notes under $NOTES_MAX characters."
            else -> "Fill in title, category, date, time and meetup location to continue."
        }
    }

    /** Publish (photos step): form valid + at least one photo (answer Q23). */
    fun publishIssues(d: PublicationDraft, now: LocalNow): List<DraftIssue> =
        issues(d, now) + if (d.photoCount == 0) listOf(DraftIssue.PHOTO_MISSING) else emptyList()

    fun canPublish(d: PublicationDraft, now: LocalNow) = publishIssues(d, now).isEmpty()

    /** Pickup options offered to requesters: every 30 min from `from` (inclusive) until `to` (exclusive). */
    fun slots(date: DateKey, from: TimeOfDay, to: TimeOfDay): List<PickupSlot> {
        val out = mutableListOf<PickupSlot>()
        var m = from.hour * 60 + from.minute
        val end = to.hour * 60 + to.minute
        while (m < end) { out += PickupSlot(date, TimeOfDay(m / 60, m % 60)); m += SLOT_STEP_MINUTES }
        return out
    }

    /** 1–3 photos; the 4th is refused with the design alert. */
    fun addPhoto(d: PublicationDraft, uri: String): PublicationDraft? =
        if (ImagePolicy.canAddPhoto(d.photoCount)) d.copy(photos = d.photos + uri) else null

    fun timeLabel(d: PublicationDraft): String? =
        if (d.from != null && d.to != null) "${Clock12.label(d.from)} – ${Clock12.label(d.to)}" else null

    fun dateLabel(date: DateKey): String = "${MONTHS[date.month - 1]}, ${date.day}"

    val MONTHS = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
}

/** Calendar sheet: Close discards the pending pick; Apply commits it (spec 0.11). */
data class DateSheet(val saved: DateKey?, val pending: DateKey? = saved, val month: DateKey) {
    fun pick(d: DateKey) = copy(pending = d)
    fun apply(): DateKey? = pending ?: saved
    fun close(): DateKey? = saved
    fun nextMonth() = copy(month = if (month.month == 12) DateKey(month.year + 1, 1, 1) else DateKey(month.year, month.month + 1, 1))
    fun prevMonth() = copy(month = if (month.month == 1) DateKey(month.year - 1, 12, 1) else DateKey(month.year, month.month - 1, 1))

    companion object {
        fun open(saved: DateKey?, today: DateKey) = DateSheet(saved, saved, (saved ?: today).copy(day = 1))
    }
}

/** Month grid, Sunday-first (design), 5–6 rows; days outside the month are shown greyed. */
object MonthGrid {
    data class Day(val date: DateKey, val inMonth: Boolean, val selectable: Boolean)

    fun of(month: DateKey, today: DateKey): List<Day> {
        val first = month.copy(day = 1)
        val lead = (first.dayOfWeek + 1) % 7            // dayOfWeek: 0 = Mon → Sunday-first offset
        var start = first
        repeat(lead) { start = minusDay(start) }
        val days = mutableListOf<Day>()
        var d = start
        while (days.size < 42) {
            val inMonth = d.month == month.month && d.year == month.year
            days += Day(d, inMonth, inMonth && d >= today)
            d = d.plusDays(1)
            if (days.size % 7 == 0 && d.month != month.month && days.size >= 35) break
        }
        return days
    }

    private fun minusDay(d: DateKey): DateKey {
        if (d.day > 1) return d.copy(day = d.day - 1)
        val (y, m) = if (d.month > 1) d.year to d.month - 1 else d.year - 1 to 12
        var last = DateKey(y, m, 28)
        while (last.plusDays(1).month == m) last = last.plusDays(1)
        return last
    }
}

/** Create-publication steps: the form, then photos. */
enum class PublishStep { FORM, PHOTOS }

/**
 * v1.16.9: guests can fill the form; "Next" asks them to log in, then the flow resumes on the photos step
 * with the draft kept. A debug test user without a server session gets a local (mocked) publish + success screen.
 */
object PublishFlow {
    enum class NextAction { GO_TO_PHOTOS, ASK_LOGIN }

    fun onNext(authenticated: Boolean): NextAction = if (authenticated) NextAction.GO_TO_PHOTOS else NextAction.ASK_LOGIN

    /** Where to land after signing in from the publication flow. */
    fun resumeStep(d: PublicationDraft, now: LocalNow): PublishStep =
        if (PublicationRules.canContinue(d, now)) PublishStep.PHOTOS else PublishStep.FORM

    /** Debug "test user" with no backend session: pretend the publish worked (nothing is uploaded). */
    fun mockPublish(debugUser: Boolean, hasServerSession: Boolean): Boolean = debugUser && !hasServerSession
}
