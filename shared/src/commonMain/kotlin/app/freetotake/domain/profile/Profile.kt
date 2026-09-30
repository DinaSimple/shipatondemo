// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
// Copyright (C) 2026 Dina Elokhova (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.domain.profile

/** Copy from Figma "My profile" (spec 0.14). */
object ProfileCopy {
    const val TITLE = "Profile"
    const val FAVOURITES = "Favourites"
    const val NOTIFICATIONS = "Notifications"
    const val LOG_OUT = "Log out"
    const val EDIT_TITLE = "Edit your profile"
    const val PUBLIC_NAME = "Public name"
    const val PUBLIC_NAME_PLACEHOLDER = "Add your name"
    const val NICKNAME = "Your Free to Take nickname"
    const val EMAIL = "Email"
    const val CHANGE_PASSWORD = "Change password"
    const val DELETE_ACCOUNT = "Delete account"
    const val SAVE = "Save"
    const val OLD_PASSWORD = "Old password"
    const val NEW_PASSWORD = "New password"
    const val CONFIRM_PASSWORD = "Confirm new password"
    const val OLD_PLACEHOLDER = "Insert your old password"
    const val NEW_PLACEHOLDER = "Create new password"
    const val CONFIRM_PLACEHOLDER = "Confirm new password"
    const val FORGOT = "Forgot password?"
    const val PASSWORD_CHANGED = "Password changed"
    const val FAVOURITES_TITLE = "Favourites"
    const val EMPTY = "Nothing here yet."
    const val REMOVED_FROM_FAVOURITES = "Removed from favourites"
    const val OK = "OK"
    // Figma "Profile placeholder" (guest)
    const val GUEST_LINE1 = "You have no account yet!"
    const val GUEST_SIGN_UP = "Sign up"
    const val GUEST_LINE2 = "to start posting and claiming."
}

/** What the Profile screens show about the signed-in user. */
data class MyProfile(
    val id: String,
    val nickname: String,
    val publicName: String?,
    val avatarUrl: String?,
    val email: String?,
    /** Anonymous (test) account: no email/password yet. */
    val isAnonymous: Boolean,
    val prefs: NotificationPrefs = NotificationPrefs(),
    /** v1.15: false for Google-only accounts (no password to change; email managed by Google). */
    val passwordLogin: Boolean = true,
) {
    /** Public name replaces the generated nickname once set (spec 0.14). */
    val displayName: String get() = publicName?.trim()?.takeIf { it.isNotEmpty() } ?: "@$nickname"
    val showsNickname: Boolean get() = publicName.isNullOrBlank()
}

object ProfileRules {
    const val PUBLIC_NAME_MAX = 40
    /** Placeholder avatars generated in the app (mascots); stable per user. */
    const val PLACEHOLDER_AVATARS = 4

    fun placeholderAvatar(userId: String): Int {
        var h = 0
        userId.forEach { h = 31 * h + it.code }
        return ((h % PLACEHOLDER_AVATARS) + PLACEHOLDER_AVATARS) % PLACEHOLDER_AVATARS
    }

    fun normalizedPublicName(input: String): String? = input.trim().take(PUBLIC_NAME_MAX).takeIf { it.isNotEmpty() }

    fun isValidEmail(e: String): Boolean = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(e.trim())
}

/** New-password rules (spec 0.14): ≥6 chars, upper, lower, digit, special symbol. */
enum class PasswordRule(val message: String) {
    LENGTH("At least 6 characters"),
    UPPER("At least one uppercase letter"),
    LOWER("At least one lowercase letter"),
    DIGIT("At least one number"),
    SPECIAL("At least one special symbol"),
}

object PasswordPolicy {
    const val MIN_LENGTH = 6
    fun unmet(p: String): List<PasswordRule> = buildList {
        if (p.length < MIN_LENGTH) add(PasswordRule.LENGTH)
        if (p.none { it.isUpperCase() }) add(PasswordRule.UPPER)
        if (p.none { it.isLowerCase() }) add(PasswordRule.LOWER)
        if (p.none { it.isDigit() }) add(PasswordRule.DIGIT)
        if (p.all { it.isLetterOrDigit() }) add(PasswordRule.SPECIAL)
    }
}

/** Inline errors under each field of "Change password". */
data class PasswordFormErrors(val old: String? = null, val new: List<PasswordRule> = emptyList(), val confirm: String? = null) {
    val isValid: Boolean get() = old == null && new.isEmpty() && confirm == null
}

object ChangePasswordForm {
    /** Save is enabled once all three fields are filled (design); rules are then checked field by field. */
    fun canSave(old: String, new: String, confirm: String) = old.isNotEmpty() && new.isNotEmpty() && confirm.isNotEmpty()

    fun validate(old: String, new: String, confirm: String): PasswordFormErrors = PasswordFormErrors(
        old = if (old.isEmpty()) "Enter your old password" else null,
        new = PasswordPolicy.unmet(new),
        confirm = if (confirm != new) "Passwords don’t match" else null,
    )
}

/** Push-notification topics (Figma "Notifications" sheet). */
data class NotificationPrefs(
    val claimApproved: Boolean = true,
    val newRequest: Boolean = true,
    val nearby: Boolean = true,
)

enum class NotificationKind(val db: String, val label: String) {
    CLAIM_APPROVED("claim_approved", "My claim approved"),
    NEW_REQUEST("new_request", "New pickup request received"),
    NEARBY("new_giveaway_nearby", "New giveaways nearby added");
}

/** Text of a device notification for a server event. */
object NotificationText {
    fun title(kind: String, itemTitle: String?): String = when (kind) {
        NotificationKind.CLAIM_APPROVED.db -> "Your claim was approved!"
        NotificationKind.NEW_REQUEST.db -> "New pickup request"
        NotificationKind.NEARBY.db -> "New giveaway nearby"
        "publication_removed" -> "Publication removed"
        "taker_left_queue" -> "A requester left the queue"
        "chat_message" -> "New message${itemTitle?.let { " · $it" } ?: ""}"
        else -> "Free to Take"
    }

    fun body(kind: String, itemTitle: String?, body: String?): String = body ?: when (kind) {
        NotificationKind.CLAIM_APPROVED.db -> "You can collect “${itemTitle.orEmpty()}”. Check the pickup date and time."
        NotificationKind.NEW_REQUEST.db -> "Someone wants “${itemTitle.orEmpty()}”. Choose your recipient."
        NotificationKind.NEARBY.db -> "“${itemTitle.orEmpty()}” was just published near you."
        else -> itemTitle.orEmpty()
    }
}
