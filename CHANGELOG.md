# Changelog

## 1.17 - Broccoli rewards
- Every account starts with 20 broccolies. Send a request −1, finished pickup (approved + pickup time passed) −1, post a giveaway +1 (max 5 rewarded per day), someone collected your giveaway (you confirm) +1. Demo cards never cost broccoli; no request at 0 (server-enforced: NO_BROCCOLI).
- Server: supabase migration v1_17_broccoli (append-only ledger, triggers, my_broccoli / pending_handovers / confirm_handover RPCs) — applied live.
- Home: lime balance pill top right (tap → rules); "You have N broccolies! Start sharing to get more." panel + Start under My giveaways (Figma "homescreen remade").
- After a giveaway's pickup time: modal "Did @name pick up …?" → Confirm (+1) / It didn't happen.
- Onboarding: request page button → Next; new broccoli page ("Earn broccoli…", Start); unskippable "You have a reward — 20 broccolies are yours" screen once after onboarding, also when onboarding was skipped.
- Tests: SQL v1_17_broccoli_test (R-1.17-01..04), Kotlin r_1_17_01..05.

## 1.16.10 - Package name freetotake.app
- applicationId app.freetotake → freetotake.app (the name registered in Play Console). Kotlin packages unchanged.
- Updated: email-link intent (Edge Function), assetlinks.json, site privacy page, docs.
- Google Cloud needs an Android OAuth client for freetotake.app (SHA-1 of debug, upload and Play signing keys).

## 1.16.9 - Guest publication flow, precise form hint, outlined toggles
- Guests can fill "Create your publication"; Next asks to log in ("Log in to create a publication"), then resumes on the photos step with the draft kept → Publish → success screen with the mascot.
- Debug "Continue as test user" without a server session: Publish is mocked locally and shows the same success screen.
- Disabled Next explains the real problem (e.g. "This time has already passed today…").
- Notification toggles: white knob with a dark outline on the lime track (was white on lime, low contrast).
- Tests: r_1_16_9_01..03.

## 1.16.8 - Target Android 16 (API 36)
- compileSdk/targetSdk 35 → 36: required by Google Play for new apps and updates since 31 August 2026.

## 1.16.5 - Details heart vs trash, owner row + chat (giraffe), Sign up page per Figma
- Giveaway details from Available Giveaways: heart (like) on the photo, no trash; trash only when opened from My claims.
- My claims → Free food example: "Giveaway owner" row (@GentleGiraffe, giraffe avatar) with a lime Chat button (device-only demo chat, owner replies), mocked meetup point "Central square, by the fountain".
- Profile → Sign up: "Sign up" page per Figma Registration Default (title, subtitle, "Already have an account? Sign in"); Log in page gets "Not account yet? Register". While no provider is enabled: design-shaped field + dimmed Continue.
- Tests: r_1_16_5_01, r_1_16_5_02.

## 1.16.4 - Example request flow, black chat icon, splash logo, solid lime selection
- Free food example opened from Available Giveaways: request form with "Send request" → mocked device-only request (never sent to the server), then Request sent → it appears in My claims as pending. The seeded demo request is shown only when opened from My claims.
- Selected pickup date/time: solid lime #C8FF00 (same as the "Available" tag), was light lime.
- Clothes example (My giveaways): pencil icon on the Chat button tinted black.
- Launch splash / adaptive icon: logo scaled into the safe circle (Android 12+ masks it) — no more cut corner.
- Test: r_1_16_4_01.

## 1.16.3 - Sign-in buttons fail-safe
- Google / Facebook buttons appear only after Supabase confirms the provider is enabled; while loading or when the check fails they stay hidden (no more "provider is not enabled" browser page).
- Test: AuthTest showProvider (null / empty → hidden).

## 1.16.2 - Pickup date carousel, preselection, read-only grey slots
- "Choose pickup date": one horizontal row (carousel), a week fits the width; unavailable days grey-filled.
- One available date → preselected (lime). A chosen date → its first free time preselected (lime). MVP: one date + one time.
- Sent request: only the chosen date/time stay lime, all other chips grey-filled (not white); header tags use the single My claims tag.
- Tests: r_1_16_2_01..03.

## 1.16.1 — "Available" tag lime everywhere; My claims: one tag (pending → "Available"), trash only; sign-in shows only providers enabled in Supabase

## 1.16 — Monetization: AdMob interstitial after a sent request / a new publication (EU consent via UMP, 2-min cap, no ads for demo cards), RevenueCat SDK with Supabase user id and the `no_ads` entitlement. Test ids by default. Setup: docs/MONETIZATION.md

## 1.15.4 — One control per section: Available = heart + one status (demo food: lime "Available"), My claims = trash + one status (Pending, grey), My giveaways = trash (demo clothes: lime "Pending my approval", text "Example"; removable). Splash logo no longer cropped.

## 1.15.2 — Email sign-up / email log in / password recovery hidden (AuthFeatures.EMAIL_FLOW = false, code kept); auth screen = Google + Facebook

## 1.15.1 — Facebook sign-in (browser OAuth, PKCE, returns via freetotake://login-callback). Setup: docs/FACEBOOK_SIGN_IN.md

## 1.15 — Google sign-in (Continue with Google on Log in / Sign up, native account picker, nonce-checked ID token → Supabase). Google accounts: no password change, email read-only. Setup: docs/GOOGLE_SIGN_IN.md

## 1.14.3 — Example labels lime (Home cards + My publications)

## 1.14.2 — Guest Profile placeholder from Figma ("You have no account yet! Sign up to start posting and claiming." + mascot)

## 1.14 — Chat, Finished claims, guest profile
- Chat between the publisher and the approved collector (Figma "Chat and chat history"): empty / conversation / closed states, day separators, polling every 4 s. Attachment button shown, photos out of scope.
- Server: chat_messages (RLS: participants only), send_chat_message (participant + meeting-in-future + 1–1000 chars + rate limit), chat_state, archive_past_meetings (pg_cron every 5 min: claim → completed, publication → given, chat read-only), chat notifications.
- My claims: "Finished" chip (collected items); collectors keep seeing finished listings.
- Example food card pre-listed in My claims as a pending request you can cancel; example header lime; picking a date scrolls to "Choose time".
- Location saved screen: Continue button. Guest Profile tab: placeholder profile with Register (no forced Log in).

## 1.13 — Picture captcha, dark mode, empty states
- Own 3×3 picture check ("Select all images with cars") in a compact bottom sheet; answer checked on the server; one puzzle per sign-up / reset flow (reused until an email is sent, 15 min). hCaptcha removed.
- Email link now opens the app straight from the auth function (no website needed); senders: Brevo, Resend or n8n/Make webhook (docs/AUTH_SETUP.md).
- Example cards: only the "Example" badge; titles "Clothes", "Free food".
- One empty state everywhere: "Nothing here yet." + the hero mascot; Start card = mascot + Start.
- Catalog without the bottom tabs (back → Home).
- Dark mode (system): inverted neutrals, lime kept; bars follow the theme. Portrait only.

## 1.12.1 — Navigation states, example giveaway, Terms name
- Bottom navigation from Figma "Navigation items active and inactive states": active tab = lime top bar + lime spotlight, black icon/label (Home filled lime); inactive = grey #8E8E93.
- My publications: Create pencil icon black.
- Example "Clothes (example)" with 3 requests now also in My publications ("Action needed") while you have no publications; Home example card shows "Example" + "Pending your approval" next to Start.
- Terms: "Free to Give" → "Free to Take".

## 1.12 — Login and auth
- Log in, Sign up, Create a password, You're registered, Password recovery, Create a new password, Ready!, Terms and conditions — from Figma.
- hCaptcha (free) before sending any sign-up or password-reset email; verified on the server.
- Edge Function `auth-email` + 5-minute single-use links (hashed, rate-limited); Android App Links + web confirmation page (`web/`).
- Persistent sign-in; protected actions now require an account. Profile "Forgot password?" uses the captcha flow.
- Tests: 142 Kotlin + SQL v1.0–v1.12; function smoke-tested live.

## 1.11 — My Profile
- Profile, Edit profile (public name, email, avatar upload), Change password with inline rules + Forgot password, Favourites, Notifications preferences, Log out.
- Server notification events (new request, approval, nearby) shown as device notifications while the app runs.
- Supabase: avatars bucket, profile prefs + home city, notification triggers, my_notifications view (applied live).
- Tests: 135 Kotlin + SQL v1.0–v1.11.

## 1.10 — Recipient approval
- My Publication Details with "Choose your recipient" / "Pending collection from" + Chat; one-time intro; swipeable candidate cards with Approved/Rejected stamps, "i out of N" bar; approval screen; others auto-rejected.
- Example "Clothes (example)" publication with 3 crocodile-mascot requests for users with no publications.
- Supabase: approve_claim auto-rejects other requests; avatar in item_requests (applied live).
- Tests: 130 Kotlin + SQL v1.0–v1.10.

## 1.9.1 — No apartment/entrance/floor anywhere (removed from DB too); photos compressed right after selection; photo tiles #C8FF00

## 1.9 — My Publications: Active and Archived
- Active (Waiting for requests / Action needed / Pending collection) and Archived tabs, Create button, horizontal cards with status badges.
- Cancel/unpublish with confirmation, loader and "Publication deleted" sheet; 2-hour cutoff; requesters notified.
- Edit a publication (before assignment); minimal request review (Approve / Reject).
- Supabase: my_publications / item_requests views, update_item, withdraw_item cutoff + notices (applied live).
- Tests: 124 Kotlin + SQL v1.0–v1.9.

## 1.8.4 — Example card: food photo (design), labelled as example, details explain food sharing and safety

## 1.8.3 — Home layout: design card size, white View More card, equal section heights, no catalog chevron without publications

## 1.8.2 — Onboarding: Next buttons, notifications then location after Start/Skip

## 1.8.1 — Answers Q22–Q24
- Photos required (1–3). Photo files deleted 14 days after a publication expired (daily pg_cron → Edge Function), grey note in the UI.
- Single meetup date; "My giveaways" / "My claims" wording kept.

## 1.8 — Create publication flow
- Login prompt for guests (no auto-resume), 2-step form (details + photos), calendar and time sheets, meetup location from the map picker, photo picker/camera with on-device compression, Publish, "Ready!" → My publications (minimal).
- Real photos on cards and details. Debug test user = Supabase anonymous session (when enabled).
- Supabase: publish_item RPC, private meetup details (applied live).
- Tests: 116 Kotlin + SQL v1.0–v1.8.

## 1.7.1 — Answers Q18–Q20 + "My claims" wording
- "Rejected for you" (rejected or someone else selected), no re-request; approved: cancel only until pickup time.
- My claims screen: sorting sheet + filter chips. "My pickups" renamed "My claims" everywhere.
- Supabase: submit_claim ALREADY_REJECTED, cancel_claim PICKUP_PASSED (applied live).

## 1.7 — Card states: request, favorite, availability
- Public cards: Available + heart. My Claims cards: trash on every card, Pending (+ Available + heart while open), Approved, Rejected (greyed).
- Trash with confirmation: leaves the queue, publisher notified, hidden from My Claims. "My claims" screen with status chips.
- Reserved listings hidden from the public catalog and closed to new requests. Favorites.
- Supabase: notifications, favorites, hidden_by_taker, remove_my_request, cancel_claim notifies, submit_claim available-only (applied live).
- Tests: 103 Kotlin + SQL v1.0–v1.7.

## 1.6 — Giveaway details & request submission
- Details screen (share, photo/placeholder, 3-line description, meetup area + map), date→time picker from publisher availability, note ≤1000, Send request with guest login sheet, success screen.
- Home "My claims" with status (Pending dimmed). Example card has demo availability; its requests stay on device.
- Width-adaptive layout (16dp margins, equal-width date/time cells).
- Supabase: item_availability, requested slot on claims, submit/approve RPCs updated (applied live).
- Fix: 1.5 back-handler compile error (Example screen) removed.
- Tests: 93 Kotlin + SQL v1.0–v1.6.

## 1.5.1 — answers applied: Cancel keeps applied settings; chip icon fill states (lime/white); closest w/o location = recent first service-wide

## 1.5 — Catalog categories & sorting
- Fixed category chips, sorting sheet (Closest nearby / Recently published available first; Apply/Cancel), Recent First default, session-scoped state.
- Educational "Free to Take" example card replaces debug demos; disappears after the first real publication.
- Supabase: category constraint (applied live). Tests: 80 Kotlin + SQL v1.0–v1.5.

## 1.4.1 — design update: reserved cards keep the badge but are no longer faded

## 1.4 — Home empty states, My Claims, full catalog
- Empty states per spec/design; My Claims section logic; full catalog (Figma) with location row, category chips, 2-column grid, reserved badge.
- Tests: 70 Kotlin.

## 1.3.2 — build fixes (location type, Supabase API exposed from shared, AGP 8.13.2)

## 1.3 — Pickup confirmation + Home content
- Continue validation, confirmation screen with 3 s auto-return.
- Home: My Giveaways empty state + Start; Available Giveaways carousel (≤5, View More) scoped by city; full catalog screen.
- Supabase: `items.expires_at`, view `active_items` (applied live). Sign-in deferred with tracked placeholders.
- Tests: 62 Kotlin + SQL v1.0–v1.3.

## 1.2 — Home screen + giveaway pickup location
- Home (Figma): location row with edit, My giveaways / Available Giveaways / My claims empty states, tab bar.
- Map picker (MapLibre + OpenFreeMap, no fees) with search-as-you-type sheet (Photon), Android Geocoder for "City, Postcode".
- Private exact pickup points (Supabase table + RLS), public coarse area; image policy with HEIC + on-device compression.
- Onboarding persistence independent of auth. Tests: 54 Kotlin + SQL v1.0–v1.2.

## 1.1 — General Requirements + first launch & onboarding
- Auth gate with resume-after-sign-in, session persistence/expiry, expanded protected actions.
- Onboarding flow (splash → 4 auto-advancing screens → Start → approximate-location permission → catalog) from Figma.
- My Claims filter, public name vs generated nickname, giver notifications on approved cancel (domain events).
- Supabase migration v1.1 (profiles nickname/public_name) — applied live.
- Inter font bundled; launcher icon from logo. Tests: 44 Kotlin + SQL v1.0/v1.1.

## 1.0.1 — Supabase project live
- Project `free-to-take` created (eu-central-1, free plan); v1.0 migration applied.
- Security hardening migration: pinned search_path on helpers, sign-up trigger not callable via API.
- Rule tests run against the live DB (33 steps, rolled back) — all pass.

## 1.0 — project setup + core give/take rules
- KMP project: `shared` (business logic, Android + iOS-ready) and `composeApp` (Android, Compose Multiplatform shell).
- Domain: items, claims, sessions; access policy (guest browse, login gate); claim rules incl. 8h late-cancel rule.
- Supabase migration: tables, RLS, status-changing RPCs, photo bucket.
- Tests: 21 Kotlin rule tests + SQL RPC/RLS suite; `docs/REGRESSION.md`.
