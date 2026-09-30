// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Dina Yol (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.domain.prefs

import app.freetotake.domain.location.AreaLabel
import app.freetotake.domain.location.GeoPoint
import app.freetotake.domain.location.LocationAccess
import app.freetotake.domain.location.PickupPoint

/** Minimal platform key-value storage (SharedPreferences on Android). */
interface KeyValueStore {
    fun getBoolean(key: String, default: Boolean): Boolean
    fun putBoolean(key: String, value: Boolean)
    fun getString(key: String): String?
    fun putString(key: String, value: String)
}

class AppPreferences(private val store: KeyValueStore) {

    var onboardingCompleted: Boolean
        get() = store.getBoolean(KEY_ONBOARDING_DONE, false)
        set(value) = store.putBoolean(KEY_ONBOARDING_DONE, value)

    var locationAccess: LocationAccess
        get() = store.getString(KEY_LOCATION_ACCESS)
            ?.let { v -> LocationAccess.entries.firstOrNull { it.name == v } }
            ?: LocationAccess.NOT_ASKED
        set(value) = store.putString(KEY_LOCATION_ACCESS, value.name)

    /** Default giveaway pickup point chosen on Home. Device-local, independent of sign-in. */
    /** Recipient-review intro ("Time to choose…") shown once — per device for now (0.13 open question). */
    var reviewIntroSeen: Boolean
        get() = store.getBoolean(KEY_REVIEW_INTRO, false)
        set(value) = store.putBoolean(KEY_REVIEW_INTRO, value)

    /** v1.12: a link session was opened to set a password; if the app died before it was set, sign out on next start. */
    var passwordSetupPending: Boolean
        get() = store.getBoolean(KEY_PW_SETUP, false)
        set(value) = store.putBoolean(KEY_PW_SETUP, value)

    /** Last email used to log in / sign up (prefills Log in). */
    var lastEmail: String?
        get() = store.getString(KEY_LAST_EMAIL)?.ifEmpty { null }
        set(value) = store.putString(KEY_LAST_EMAIL, value.orEmpty())

    /** v1.14: the example pending request in My claims was cancelled. */
    var exampleClaimDismissed: Boolean
        get() = store.getBoolean(KEY_EX_CLAIM, false)
        set(value) = store.putBoolean(KEY_EX_CLAIM, value)

    /** v1.15.4: the example "Clothes" giveaway was removed with the trash. */
    var exampleGiveawayDismissed: Boolean
        get() = store.getBoolean(KEY_EX_GIVEAWAY, false)
        set(value) = store.putBoolean(KEY_EX_GIVEAWAY, value)

    /** v1.17: the unskippable "You have a reward — 20 broccolies" screen was shown. */
    var broccoliRewardSeen: Boolean
        get() = store.getBoolean(KEY_BROCCOLI_REWARD, false)
        set(value) = store.putBoolean(KEY_BROCCOLI_REWARD, value)

    /** v1.17: local balance for guests / the debug test user (signed-in users read it from the server). */
    var localBroccoli: Int
        get() = store.getString(KEY_BROCCOLI_LOCAL)?.toIntOrNull() ?: app.freetotake.domain.rewards.BroccoliRules.INITIAL
        set(value) = store.putString(KEY_BROCCOLI_LOCAL, value.toString())

    var chosenPickup: PickupPoint?
        get() = store.getString(KEY_PICKUP)?.let(::decode)
        set(value) = store.putString(KEY_PICKUP, value?.let(::encode) ?: "")

    private fun encode(p: PickupPoint) = listOf(
        p.point.lat.toString(), p.point.lng.toString(),
        p.placeName.orEmpty(), p.address.orEmpty(), p.area.city.orEmpty(), p.area.postalCode.orEmpty(),
    ).joinToString(SEP)

    private fun decode(s: String): PickupPoint? {
        val f = s.split(SEP)
        if (f.size != 6) return null
        val lat = f[0].toDoubleOrNull() ?: return null
        val lng = f[1].toDoubleOrNull() ?: return null
        fun n(v: String) = v.ifEmpty { null }
        return PickupPoint(GeoPoint(lat, lng), n(f[2]), n(f[3]), AreaLabel(n(f[4]), n(f[5])))
    }

    private companion object {
        const val KEY_ONBOARDING_DONE = "onboarding_completed_v1"
        const val KEY_LOCATION_ACCESS = "location_access"
        const val KEY_PICKUP = "chosen_pickup_v1"
        const val KEY_REVIEW_INTRO = "review_intro_seen_v1"
        const val KEY_PW_SETUP = "password_setup_pending_v1"
        const val KEY_LAST_EMAIL = "last_email_v1"
        const val KEY_EX_CLAIM = "example_claim_dismissed_v1"
        const val KEY_EX_GIVEAWAY = "example_giveaway_dismissed_v1"
        const val KEY_BROCCOLI_REWARD = "broccoli_reward_seen_v1"
        const val KEY_BROCCOLI_LOCAL = "broccoli_local_v1"
        const val SEP = "\u001F"
    }
}

/** In-memory store for tests and previews. */
class InMemoryKeyValueStore : KeyValueStore {
    private val map = mutableMapOf<String, Any>()
    override fun getBoolean(key: String, default: Boolean) = map[key] as? Boolean ?: default
    override fun putBoolean(key: String, value: Boolean) { map[key] = value }
    override fun getString(key: String) = map[key] as? String
    override fun putString(key: String, value: String) { map[key] = value }
}
