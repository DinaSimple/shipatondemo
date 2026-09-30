# Third-Party Notices

Third-party software, fonts, icons, images, and other materials remain subject to their
respective licenses and attribution requirements.

| Component / asset | Version or source | License | Attribution / notes |
|---|---|---|---|
| Kotlin / Kotlin Gradle plugins | 2.2.0 | Apache-2.0 | https://github.com/JetBrains/kotlin |
| Jetpack Compose Multiplatform | 1.8.2 | Apache-2.0 | https://github.com/JetBrains/compose-multiplatform |
| Android Gradle Plugin | 8.13.2 | Apache-2.0 | https://developer.android.com/studio |
| kotlinx-coroutines | 1.10.2 | Apache-2.0 | https://github.com/Kotlin/kotlinx.coroutines |
| kotlinx-serialization | 1.9.0 | Apache-2.0 | https://github.com/Kotlin/kotlinx.serialization |
| kotlinx-datetime | 0.7.1 | Apache-2.0 | https://github.com/Kotlin/kotlinx-datetime |
| Ktor client | 3.2.3 | Apache-2.0 | https://github.com/ktorio/ktor |
| Supabase Kotlin SDK (`auth-kt`, `postgrest-kt`, …) | 3.2.2 (BOM) | MIT | https://github.com/supabase-community/supabase-kt |
| AndroidX Activity / Lifecycle (Compose) | see `gradle/libs.versions.toml` | Apache-2.0 | https://developer.android.com/jetpack/androidx |
| AndroidX Credentials + Google Identity | 1.5.0 / 1.1.1 | Apache-2.0 | Google Play services / AndroidX |
| MapLibre Android SDK | 11.8.0 | BSD-2-Clause | https://github.com/maplibre/maplibre-native |
| RevenueCat Purchases SDK | 9.x | MIT | https://github.com/RevenueCat/purchases-android |
| Google Play services Ads (AdMob) | 24.x | Android SDK License | https://developers.google.com/admob |
| Google User Messaging Platform (UMP) | 3.x | Android SDK License | https://developers.google.com/admob/ump |
| Inter font (bundled in app resources) | static TTF | SIL Open Font License 1.1 | https://rsms.me/inter/ |
| Photon geocoder API (runtime) | photon.komoot.io | OSM data ODbL; service terms apply | https://photon.komoot.io/ |
| OpenStreetMap map tiles (via MapLibre, runtime) | OSM contributors | ODbL | https://www.openstreetmap.org/copyright |
| Deno runtime + npm imports (Edge Functions) | Supabase-managed | MIT / various | `@supabase/supabase-js` and Deno std |

This list covers major dependencies declared in `gradle/libs.versions.toml` and runtime services
documented in the source. Run `./gradlew :composeApp:dependencies` (or your IDE’s dependency
report) for the full resolved graph before a release. Do not treat unlisted transitive packages as
owned by Free to Take.
