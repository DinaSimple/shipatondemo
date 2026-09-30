# Ads + RevenueCat (v1.16)

How it works
- **Ads = Google AdMob** interstitial (full-screen) shown right after a user sends a request or publishes a new giveaway.
  Never for the demo cards, at most one every 2 minutes. EU/UK users first see Google's consent form (UMP) — required in Spain/EU.
- **RevenueCat** does not serve ads. It manages purchases: users with the active entitlement **`no_ads`** never see ads
  (sell it later as a "Remove ads" subscription/one-time purchase with a RevenueCat paywall).
- Out of the box the app uses **Google's test ad ids** — you'll see "Test Ad" banners, no real revenue.

## 1. RevenueCat (free up to $2.5k monthly revenue)
1. app.revenuecat.com → sign up → **Create project** "Free to Take".
2. Apps → the **Test Store** app is already there. Copy its **public API key** (Project settings → API keys).
   Later, when the app is on Google Play: **+ New app → Google Play**, package `freetotake.app`, upload the Play service-account JSON, and use its `goog_…` key instead.
3. Product catalog → **Entitlements → + New** → identifier **`no_ads`**.
4. `local.properties`: `revenuecat.apiKey=<public key>` → rebuild. Logcat shows `Purchases is configured`.

## 2. AdMob (for real ads)
1. admob.google.com → sign up → **Apps → Add app** → Android, "Free to Take".
2. Copy the **App ID** (`ca-app-pub-…~…`).
3. **Ad units → Add → Interstitial** → copy the unit id (`ca-app-pub-…/…`).
4. **Privacy & messaging → European regulations (GDPR)**: create and publish the consent message (the app shows it via UMP).
5. `local.properties`:
   ```
   admob.appId=ca-app-pub-XXXXXXXX~YYYYYYYY
   admob.interstitialId=ca-app-pub-XXXXXXXX/ZZZZZZZZ
   ```
   Keep the test ids while developing — clicking your own real ads can get the AdMob account banned.
6. After the Play listing exists, link it in AdMob and add `app-ads.txt` on your developer website (AdMob shows the line).

## 3. Google Play Console
- Store listing / App content: **Contains ads = Yes**; Data safety: device/advertising ID + approximate location used by the ads SDK;
  **Advertising ID** declaration = Yes (the ads SDK adds the AD_ID permission).
