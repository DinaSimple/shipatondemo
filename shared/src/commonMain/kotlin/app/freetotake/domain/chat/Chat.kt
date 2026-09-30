// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Dina Yol (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.domain.chat

import app.freetotake.domain.model.Timestamp
import app.freetotake.domain.request.DateKey

/** Copy from Figma "Chat and chat history" (v1.14). */
object ChatCopy {
    fun title(itemTitle: String) = "Chat about $itemTitle"
    fun subtitle(otherName: String) = "Chat with $otherName"
    const val EMPTY_TITLE = "No messages here!"
    const val EMPTY_TEXT = "Jot something down."
    const val PLACEHOLDER = "Write something here"
    const val TODAY = "Today"
    const val YESTERDAY = "Yesterday"
    const val CLOSED = "This chat is closed — the meeting time has passed. You can still read your messages."
    const val ATTACH_LATER = "Sending photos is coming soon."
    const val CHAT = "Chat"
}

/** Who may chat, until when (server `chat_state`; the server enforces the same rules on send). */
data class ChatInfo(
    val itemId: String,
    val itemTitle: String,
    val otherName: String,
    val otherAvatarUrl: String?,
    val meetingAt: Timestamp?,
    val open: Boolean,
)

data class ChatMessage(val id: String, val mine: Boolean, val body: String, val createdAt: Timestamp, val day: DateKey)

sealed interface ChatSend {
    data object Ok : ChatSend
    data class Failed(val error: ChatError) : ChatSend
}

enum class ChatError(val code: String, val message: String) {
    CLOSED("CHAT_CLOSED", ChatCopy.CLOSED),
    FORBIDDEN("CHAT_FORBIDDEN", "Chat is available only to the publisher and the approved collector."),
    EMPTY("MESSAGE_EMPTY", "Write a message first."),
    TOO_LONG("MESSAGE_TOO_LONG", "Keep messages under ${ChatRules.MAX_LENGTH} characters."),
    RATE_LIMITED("RATE_LIMITED", "You're sending messages too fast. Wait a moment."),
    NETWORK("NETWORK", "Message not sent. Check your connection.");

    companion object { fun of(text: String?): ChatError = entries.firstOrNull { text?.contains(it.code) == true } ?: NETWORK }
}

sealed interface ChatItem {
    data class Day(val label: String) : ChatItem
    data class Message(val message: ChatMessage) : ChatItem
}

object ChatRules {
    const val MAX_LENGTH = 1000
    const val POLL_MS = 4_000L

    /** Composer enabled only while the server says open AND the meeting time has not passed on this device. */
    fun canSend(info: ChatInfo?, now: Timestamp): Boolean =
        info != null && info.open && (info.meetingAt == null || now < info.meetingAt)

    fun normalized(text: String): String? = text.trim().takeIf { it.isNotEmpty() && it.length <= MAX_LENGTH }

    fun validate(text: String): ChatError? = when {
        text.isBlank() -> ChatError.EMPTY
        text.trim().length > MAX_LENGTH -> ChatError.TOO_LONG
        else -> null
    }

    fun dayLabel(day: DateKey, today: DateKey): String = when {
        day == today -> ChatCopy.TODAY
        day.plusDays(1) == today -> ChatCopy.YESTERDAY
        else -> "${day.day} ${MONTHS[day.month - 1]}"
    }

    /** Messages oldest → newest with a day separator before each new day ("Today" in the design). */
    fun timeline(messages: List<ChatMessage>, today: DateKey): List<ChatItem> = buildList {
        var last: DateKey? = null
        messages.sortedBy { it.createdAt.epochMillis }.forEach { m ->
            if (m.day != last) { add(ChatItem.Day(dayLabel(m.day, today))); last = m.day }
            add(ChatItem.Message(m))
        }
    }

    private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
}

/** Device-only chat for the example "Clothes" giveaway after approving a crocodile (teaches the flow). */
object ExampleChat {
    const val REPLY = "Great, thank you! See you at the meetup point."
    /** v1.16.5: the giraffe owner of the Free food example answers the requester. */
    const val OWNER_REPLY = "Hi! Sure, I'll keep it for you. See you by the fountain."

    /** Device-only demo chats: the Clothes example (publisher side) and the Free food example (requester side). */
    fun isExample(itemId: String): Boolean =
        app.freetotake.domain.publish.ExamplePublication.isExample(itemId) || itemId == app.freetotake.domain.catalog.ExampleListing.ID

    fun reply(itemId: String): String = if (itemId == app.freetotake.domain.catalog.ExampleListing.ID) OWNER_REPLY else REPLY
}

/** v1.16.5: mocked owner of the Free food example, shown to the requester in My claims (avatar: giraffe). */
object ExampleOwner {
    const val NICKNAME = "GentleGiraffe"
    /** Avatar index understood by the UI's example avatars (giraffe). */
    const val AVATAR = 100
    const val MEETUP = "Central square, by the fountain"
    const val LABEL = "Giveaway owner"
}
