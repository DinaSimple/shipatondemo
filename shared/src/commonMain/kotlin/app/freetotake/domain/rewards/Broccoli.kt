// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
// Copyright (C) 2026 Dina Elokhova (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.domain.rewards

/**
 * v1.17 Broccoli rewards (server is the source of truth: supabase migration v1_17_broccoli).
 * Start: 20. Send a request −1. Finished pickup (approved + meeting time passed) −1.
 * Post a giveaway +1. Your item was collected (you confirm the handover) +1. Demo cards never cost broccoli.
 */
object BroccoliRules {
    const val INITIAL = 20
    const val REQUEST_COST = 1
    const val PICKUP_COST = 1
    const val POST_REWARD = 1
    const val COLLECTED_REWARD = 1

    fun canRequest(balance: Int, example: Boolean): Boolean = example || balance >= REQUEST_COST
    fun afterRequest(balance: Int, example: Boolean): Int = if (example) balance else (balance - REQUEST_COST).coerceAtLeast(0)
    fun afterPost(balance: Int): Int = balance + POST_REWARD
    fun afterCollected(balance: Int, collected: Boolean): Int = if (collected) balance + COLLECTED_REWARD else balance
}

/** A finished giveaway the giver still has to confirm ("Did it get picked up?"). */
data class PendingHandover(val itemId: String, val title: String, val collector: String?)

object BroccoliCopy {
    const val RULES_TITLE = "Your broccolies"
    fun balance(n: Int) = "You have $n ${if (n == 1) "broccoli" else "broccolies"}!"
    const val HOME_HINT = "Start sharing to get more."
    val RULES = listOf(
        "+1  when you post a giveaway",
        "+1  when someone collects your giveaway (you confirm it)",
        "−1  when you send a request",
        "−1  when your pickup is finished",
        "Example cards never use broccolies.",
    )
    const val RULES_START = "Everyone starts with 20 broccolies."
    const val NOT_ENOUGH = "You need 1 broccoli to send a request. Post a giveaway to earn more."
    const val OK = "Got it"

    // Onboarding (Figma "Broccoli"): extra page + unskippable reward screen.
    const val ONBOARDING_CAPTION = "Earn broccoli when you post something! Spend broccoli when you send requests!"
    const val REWARD_TITLE = "You have a reward"
    const val REWARD_SUBTITLE = "20 broccolies are yours"
    const val REWARD_TEXT = "Start posting to get more broccolies!\nStart claiming to use your broccolies!"
    const val CONTINUE = "Continue"

    // Handover confirmation after the pickup time has passed.
    fun confirmTitle(h: PendingHandover) = "Did ${h.collector?.let { "@$it" } ?: "the collector"} pick up “${h.title}”?"
    const val CONFIRM_TEXT = "Confirm the handover and get 1 broccoli."
    const val CONFIRM_YES = "Confirm"
    const val CONFIRM_NO = "It didn't happen"
}
