// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Dina Yol (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.android

import android.app.Activity
import android.app.Application
import android.util.Log
import app.freetotake.domain.model.Timestamp
import app.freetotake.domain.monetization.AdMoment
import app.freetotake.domain.monetization.AdPolicy
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.getCustomerInfoWith
import com.revenuecat.purchases.logInWith
import com.revenuecat.purchases.logOutWith
import java.util.concurrent.atomic.AtomicBoolean

/**
 * v1.16 — RevenueCat (subscriptions / "no ads" entitlement) + Google AdMob interstitials.
 * RevenueCat does not serve ads; it only tells us whether the user bought the ad-free entitlement.
 */
object RevenueCat {
    fun init(app: Application) {
        val key = BuildConfig.REVENUECAT_API_KEY
        if (key.isBlank()) { Log.w("FreeToTake", "RevenueCat key missing (local.properties revenuecat.apiKey) — SDK not configured"); return }
        if (BuildConfig.DEBUG) Purchases.logLevel = LogLevel.DEBUG
        Purchases.configure(PurchasesConfiguration.Builder(app, key).build())
    }

    /** Ties purchases to the Supabase user (null = signed out → anonymous RevenueCat user). */
    fun identify(userId: String?) {
        if (!Purchases.isConfigured) return
        val p = Purchases.sharedInstance
        if (userId != null) {
            if (p.appUserID != userId) p.logInWith(userId, onError = { Log.w("FreeToTake", "RevenueCat logIn: ${it.message}") }) { _, _ -> }
        } else if (!p.isAnonymous) p.logOutWith(onError = { }) { }
    }

    fun adFree(result: (Boolean) -> Unit) {
        if (!Purchases.isConfigured) { result(false); return }
        Purchases.sharedInstance.getCustomerInfoWith(onError = { result(false) }) { info ->
            result(info.entitlements[AdPolicy.AD_FREE_ENTITLEMENT]?.isActive == true)
        }
    }
}

/** Interstitials with EU consent (Google UMP) first; ads are requested only when consent allows it. */
class InterstitialAds(private val activity: Activity) {
    private var ad: InterstitialAd? = null
    private var lastShownAt: Timestamp? = null
    private val started = AtomicBoolean(false)
    private val ready = AtomicBoolean(false)

    fun start() {
        val consent = UserMessagingPlatform.getConsentInformation(activity)
        consent.requestConsentInfoUpdate(activity, ConsentRequestParameters.Builder().build(), {
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { _ ->
                if (consent.canRequestAds()) initAds()
            }
        }, { err -> Log.w("FreeToTake", "Consent update failed: ${err.message}") })
        if (consent.canRequestAds()) initAds()   // consent from a previous session
    }

    private fun initAds() {
        if (!started.compareAndSet(false, true)) return
        MobileAds.initialize(activity) { ready.set(true); preload() }
    }

    private fun preload() {
        if (!ready.get() || ad != null) return
        InterstitialAd.load(activity, BuildConfig.ADMOB_INTERSTITIAL_ID, AdRequest.Builder().build(), object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(loaded: InterstitialAd) { ad = loaded }
            override fun onAdFailedToLoad(error: LoadAdError) { ad = null; Log.w("FreeToTake", "Ad load failed: ${error.message}") }
        })
    }

    fun show(moment: AdMoment, isDemo: Boolean) {
        RevenueCat.adFree { adFree ->
            val now = Timestamp(System.currentTimeMillis())
            val current = ad
            if (current == null || !AdPolicy.shouldShow(moment, isDemo, adFree, lastShownAt, now)) { preload(); return@adFree }
            current.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() { ad = null; preload() }
                override fun onAdFailedToShowFullScreenContent(error: AdError) { ad = null; preload() }
            }
            lastShownAt = now
            current.show(activity)
        }
    }
}
