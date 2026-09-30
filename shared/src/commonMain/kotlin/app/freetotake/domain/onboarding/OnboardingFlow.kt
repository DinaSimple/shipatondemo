// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
// Copyright (C) 2026 Dina Yol (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.domain.onboarding

/**
 * First-launch sequence (spec 0.3, updated by Figma "Complete onboarding" in 1.8.2):
 * SPLASH (2 s, auto) → WELCOME [Next] → GIVEAWAY [Next] → MEETUP [Next] → REQUEST [Start]
 *   → NOTIFICATION_PERMISSION (system dialog) → LOCATION_PERMISSION (approximate, system dialog) → DONE.
 * Skip (screens 1–3) goes straight to the permission requests in the same order; Back returns one screen.
 * No permission is requested before the onboarding screens are finished or skipped.
 */
enum class OnboardingStep(val autoAdvanceMs: Long?, val pageIndex: Int?) {
    SPLASH(2_000, null),
    WELCOME(null, 0),
    GIVEAWAY(null, 1),
    MEETUP(null, 2),
    REQUEST(null, 3),               // v1.17: "Next" (was the final screen)
    BROCCOLI(null, 4),              // v1.17 final screen: "Start" (Figma "Broccoli / Onboarding last step")
    NOTIFICATION_PERMISSION(null, null),
    LOCATION_PERMISSION(null, null),
    DONE(null, null);

    val isPage: Boolean get() = pageIndex != null
    val showsStartButton: Boolean get() = this == BROCCOLI
    val showsNextButton: Boolean get() = this == WELCOME || this == GIVEAWAY || this == MEETUP || this == REQUEST
    val showsSkip: Boolean get() = this == WELCOME || this == GIVEAWAY || this == MEETUP
    val showsBack: Boolean get() = this == GIVEAWAY || this == MEETUP || this == REQUEST || this == BROCCOLI
    val isPermissionStep: Boolean get() = this == NOTIFICATION_PERMISSION || this == LOCATION_PERMISSION

    companion object {
        const val PAGE_COUNT = 5
    }
}

sealed interface OnboardingEvent {
    data object AutoAdvanceElapsed : OnboardingEvent
    data object NextTapped : OnboardingEvent
    data object Skip : OnboardingEvent
    data object Back : OnboardingEvent
    data object StartTapped : OnboardingEvent
    /** Any outcome (allowed, denied, dismissed, not needed on this OS version). */
    data object NotificationPermissionResolved : OnboardingEvent
    /** Any outcome (granted, approximate only, denied, dismissed) — never blocks the catalog. */
    data object LocationPermissionResolved : OnboardingEvent
}

object OnboardingFlow {

    fun initialStep(onboardingCompleted: Boolean): OnboardingStep =
        if (onboardingCompleted) OnboardingStep.DONE else OnboardingStep.SPLASH

    fun reduce(step: OnboardingStep, event: OnboardingEvent): OnboardingStep = when (event) {
        OnboardingEvent.AutoAdvanceElapsed -> if (step == OnboardingStep.SPLASH) OnboardingStep.WELCOME else step
        OnboardingEvent.NextTapped -> when (step) {
            OnboardingStep.WELCOME -> OnboardingStep.GIVEAWAY
            OnboardingStep.GIVEAWAY -> OnboardingStep.MEETUP
            OnboardingStep.MEETUP -> OnboardingStep.REQUEST
            OnboardingStep.REQUEST -> OnboardingStep.BROCCOLI
            else -> step
        }
        OnboardingEvent.Skip -> if (step.showsSkip) OnboardingStep.NOTIFICATION_PERMISSION else step
        OnboardingEvent.Back -> when (step) {
            OnboardingStep.GIVEAWAY -> OnboardingStep.WELCOME
            OnboardingStep.MEETUP -> OnboardingStep.GIVEAWAY
            OnboardingStep.REQUEST -> OnboardingStep.MEETUP
            OnboardingStep.BROCCOLI -> OnboardingStep.REQUEST
            else -> step
        }
        OnboardingEvent.StartTapped ->
            if (step == OnboardingStep.BROCCOLI) OnboardingStep.NOTIFICATION_PERMISSION else step
        OnboardingEvent.NotificationPermissionResolved ->
            if (step == OnboardingStep.NOTIFICATION_PERMISSION) OnboardingStep.LOCATION_PERMISSION else step
        OnboardingEvent.LocationPermissionResolved ->
            if (step == OnboardingStep.LOCATION_PERMISSION) OnboardingStep.DONE else step
    }

    /** Persist "onboarding completed" only once the whole sequence has finished. */
    fun shouldMarkCompleted(step: OnboardingStep): Boolean = step == OnboardingStep.DONE
}

/** v1.17: the "You have a reward — 20 broccolies" screen is shown once after onboarding, even if it was skipped. */
object BroccoliReward {
    fun mustShow(onboardingDone: Boolean, rewardSeen: Boolean): Boolean = onboardingDone && !rewardSeen
}
