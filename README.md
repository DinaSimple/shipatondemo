# Free to Take

[![License: PolyForm Noncommercial 1.0.0](https://img.shields.io/badge/License-PolyForm%20Noncommercial%201.0.0-2ea043?labelColor=111)](LICENSE)

**Give away what you don't need — for free.** An Android app that connects neighbours: post an item,
choose who picks it up, meet at a time that suits you. No selling, no fees.

🎥 Demo: https://youtu.be/svM-y8RIBHg · 🌐 https://freetotake.app

> Author: **Dina Elokhova** ([@DinaSimple](https://github.com/DinaSimple))

## License

**Source code** is licensed under the **[PolyForm Noncommercial License 1.0.0](LICENSE)**  
([summary at polyformproject.org](https://polyformproject.org/licenses/noncommercial/1.0.0)).

- Copyright © 2026 **Dina Elokhova** (DinaSimple) — [freetotake.app](https://freetotake.app)
- You may use, study, modify, and share the code for **noncommercial** purposes only.
- **Commercial use** (paid apps, SaaS, for-profit products, etc.) requires **written permission** from the copyright holder — see [NOTICE](NOTICE).
- **Brand assets** (name “Free to Take”, logo, mascots, illustrations) are **not** covered by that license — [BRAND-ASSETS.md](BRAND-ASSETS.md) (all rights reserved).

The full legal text is in **[LICENSE](LICENSE)** (also linked from the repository sidebar on GitHub).

## Features
- Onboarding + **broccoli rewards** (start with 20; post +1, collected +1, request −1, finished pickup −1)
- Create a giveaway: photos (on-device compression), category, date & time slots, meetup point (no GPS tracking)
- Requests with pickup time + note; publisher approves one recipient (swipe/heart), others auto-rejected
- Chat between publisher and approved collector, read-only after the meeting
- Nearby catalog with categories and favourites
- Google / Facebook sign-in (Supabase Auth), **RevenueCat** SDK (`no_ads` entitlement) + AdMob interstitials with UMP consent
- Server-side rules in PostgreSQL (RLS, security-definer RPCs, pg_cron) with SQL regression tests

## Tech
Kotlin Multiplatform + Compose Multiplatform (Android), Supabase (Postgres, Auth, Storage, Edge Functions), RevenueCat, AdMob.

## Run it
1. `cp local.properties.example local.properties` and fill in your own values
   (leave Supabase empty to see the demo content only).
2. Backend (optional): create a Supabase project and run `supabase/migrations/*.sql` in order.
   SQL tests: `PGHOST=... PGUSER=postgres ./supabase/tests/run_local.sh`.
3. Open in Android Studio → Sync → Run, or `./gradlew :composeApp:installDebug`.

---

## Original developer notes
# Free to Take

Give unwanted items away for free, or request items others give away. One account, two roles (GIVE / TAKE).

**Stack:** Kotlin Multiplatform · Compose Multiplatform · Supabase (Auth, Postgres, Storage — free tier) · Android only for now.

```
shared/        business logic (commonMain) — rules, models, repository contracts, Supabase client
composeApp/    Android app (Compose Multiplatform UI, built per flow from Figma)
supabase/      migrations (schema, RLS, RPCs) + SQL tests
docs/          SPEC.md (rules, open questions) · REGRESSION.md (checklist per version)
```

## Setup
1. Android Studio (latest) + JDK 17. First time only, generate the wrapper jar: `gradle wrapper --gradle-version 8.14.3`.
2. Backend: Supabase project `free-to-take` (ref `YOUR_PROJECT_REF`, eu-central-1) — migrations in `supabase/migrations/` are already applied. For a new project, run them in order.
3. `local.properties` (not committed):
   ```
   supabase.url=https://YOUR_PROJECT_REF.supabase.co
   supabase.anonKey=<publishable key sb_publishable_… from Project Settings → API Keys>
   ```
4. Run `composeApp` on a device/emulator.

## Tests
See `docs/REGRESSION.md`. Kotlin: `./gradlew :shared:testDebugUnitTest`. SQL: `supabase/tests/run_local.sh`.

## Versioning
Each business flow is one commit `<version> - <note>` (1.0, 1.1, …) and extends the regression checklist.

---

**Legal:** [LICENSE](LICENSE) · [NOTICE](NOTICE) · [BRAND-ASSETS.md](BRAND-ASSETS.md)
