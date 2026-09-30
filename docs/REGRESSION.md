# Regression checklist

Rule: every version re-runs **all** rows. A row is never deleted — only marked *superseded* with the version and reason.

Run:
```bash
./gradlew :shared:testDebugUnitTest           # Kotlin rule tests (R-x.x-NN = test name)
PGHOST=... PGUSER=postgres supabase/tests/run_local.sh   # Supabase RPC/RLS tests
```

## v1.0 — core domain + backend

| ID | Check | Kotlin test | SQL test |
|----|-------|:-:|:-:|
| R-1.0-01 | Guest can browse/view/search; feed hides non-open items | ✅ | ✅ |
| R-1.0-02 | Guest trying to post/request gets login prompt (action resumed); server denies anon RPC | ✅ | ✅ |
| R-1.0-03 | Logged-in user has both GIVE and TAKE actions | ✅ | ✅ |
| R-1.0-04 | Giver cannot request own item | ✅ | ✅ |
| R-1.0-05 | One active request per taker per item; re-request allowed after cancel | ✅ | ✅ |
| R-1.0-06 | New request = Pending approval | ✅ | ✅ |
| R-1.0-07 | Can queue on Reserved item; not on Awaiting/Given/Withdrawn | ✅ | — |
| R-1.0-08 | Only the giver can approve/reject | ✅ | ✅ |
| R-1.0-09 | Approve → item Reserved; only one approval per item; pickup must be in the future; reject pending only | ✅ | ✅ |
| R-1.0-10 | Pending request can always be cancelled, only by its owner | ✅ | ✅ |
| R-1.0-11 | Approved cancel > 8h before pickup → slot released, queue kept | ✅ | ✅ |
| R-1.0-12 | Approved cancel ≤ 8h (incl. exactly 8h) → Cancelled late, item Awaiting giver decision | ✅ | ✅ |
| R-1.0-13 | Giver can reopen or approve next after late cancel | ✅ | ✅ |
| R-1.0-14 | Terminal requests cannot be cancelled | ✅ | — |
| R-1.0-15 | Complete → item Given, others Closed | ✅ | ✅ |
| R-1.0-16 | Withdraw → item Withdrawn, active requests Closed | ✅ | ✅ |
| R-1.0-17 | Clients cannot write item status or insert claims directly | — | ✅ |
| R-1.0-18 | Takers see only their own requests; giver sees requests on own items | — | ✅ |
| R-1.0-19 | Profile auto-created on sign-up | — | ✅ |
| R-1.0-20 | App launches without Supabase keys (shell screen, login gate works) | manual | — |

## v1.0.1 — Supabase live project

| ID | Check | How |
|----|-------|-----|
| R-1.0-21 | Security advisor shows only the 7 intended "authenticated can execute RPC" warnings | Supabase → Advisors → Security |
| R-1.0-22 | Live DB passes the v1.0 rule scenario (run inside a rolled-back transaction) | `supabase/tests/live_smoke.sql` via SQL editor |

## v1.1 — General Requirements + onboarding

| ID | Check | Kotlin | SQL | Manual (emulator) |
|----|-------|:-:|:-:|:-:|
| R-1.1-01 | All spec protected actions require auth | ✅ | | |
| R-1.1-02 | Guest protected action → auth prompt, action remembered | ✅ | | ✅ tap Give away / Request |
| R-1.1-03 | After sign-in the action resumes once | ✅ | | ✅ debug test user → "Resumed…" |
| R-1.1-04 | Not resumed if no longer valid | ✅ | | |
| R-1.1-05 | "Not now"/back drops the pending action | ✅ | | ✅ |
| R-1.1-06 | Valid session → no prompt | ✅ | | ✅ |
| R-1.1-07 | Expired / restoring session → gate; public actions never gated | ✅ | | |
| R-1.1-08 | Resume waits until session valid | ✅ | | |
| R-1.1-10 | Unique generated nickname; email never used as name | | ✅ | |
| R-1.1-11 | User edits public name, not nickname | | ✅ | |
| R-1.1-20 | First launch → splash; later launches skip onboarding | ✅ | | ✅ relaunch app |
| R-1.1-21 | Auto-advance order splash→welcome→giveaway→meetup→request | ✅ | | ✅ |
| R-1.1-22 | Timings: splash 2–4s, info 4s, final no auto | ✅ | | ✅ |
| R-1.1-23 | Only final screen has Start; no Next | ✅ | | ✅ |
| R-1.1-24 | Start → location permission → catalog for any outcome | ✅ | | ✅ allow & deny |
| R-1.1-25 | Skip → final screen; Back → previous | ✅ | | ✅ |
| R-1.1-26 | Start ignored elsewhere | ✅ | | |
| R-1.1-27 | Denied location never blocks catalog (manual-area mode) | ✅ | | ✅ deny → "Location off" |
| R-1.1-28 | Meetup point requires explicit user choice | ✅ | | |
| R-1.1-29 | Onboarding flag + location access persist across launches | ✅ | | ✅ |
| R-1.1-30 | My Claims chip shows only requested listings; gated | ✅ | | ✅ chip prompts login |
| R-1.1-31 | Request never adds to Favorites | ✅ | | |
| R-1.1-32 | Display name = public name else nickname | ✅ | | |
| R-1.1-33 | Giver notified on approved cancel (reopened vs publish again) | ✅ | | |
| R-1.1-34 | Review requests is protected | ✅ | | |

All v1.0 rows re-run: 21/21 Kotlin, SQL suite, live smoke — pass.

## v1.2 — Home + pickup location + platform constraints

| ID | Check | Kotlin | SQL | Manual (emulator) |
|----|-------|:-:|:-:|:-:|
| R-1.2-01 | Area label "City, Postcode" formatting | ✅ | | |
| R-1.2-02 | Home shows device area when permission + location available | ✅ | | ✅ Allow location → "Mountain View, 94043" on emulator default |
| R-1.2-03 | Denied permission → "Choose your pickup point", manual selection works | ✅ | | ✅ |
| R-1.2-04 | Chosen point overrides device area | ✅ | | ✅ |
| R-1.2-05 | Onboarding + chosen point persist, independent of sign-in/out | ✅ | | ✅ sign in/out, relaunch |
| R-1.2-06 | Exact pickup visible only to giver + approved taker | ✅ | ✅ | |
| R-1.2-07 | Public point is coarse (~1 km) | ✅ | ✅ | |
| R-1.2-08 | Search from 2 letters, prefix matches first | ✅ | | ✅ type "Pobl" |
| R-1.2-09 | Accent/case-insensitive, typed prefix bold | ✅ | | ✅ |
| R-1.2-10 | HEIC/HEIF accepted, resize ≤1600, max 3 photos | ✅ | | (UI in giveaway step) |
| R-1.2-11 | Anon cannot read exact pickup table | | ✅ | |
| R-1.2-12 | Pending taker / stranger cannot read or write exact point; approved taker can | | ✅ | |
| R-1.2-13 | Map: move map → address under pin fills field; Apply saves; back discards | | | ✅ |
| R-1.2-14 | Tabs My publications / My claims prompt sign-in for guests | | | ✅ |

All v1.0 + v1.1 rows re-run: 54/54 Kotlin (incl. 21 v1.0 + 23 v1.1), SQL v1.0/v1.1/v1.2 suites, live migration applied.

## v1.3 — Pickup confirmation + Home content

| ID | Check | Kotlin | SQL | Manual (emulator) |
|----|-------|:-:|:-:|:-:|
| R-1.3-01 | Continue only for valid selection | ✅ | | ✅ disabled while "Finding address…" |
| R-1.3-02 | Bare coordinates / nameless directory hit invalid | ✅ | | |
| R-1.3-03 | Confirmation text + auto-return 3 s | ✅ | | ✅ |
| R-1.3-10 | Carousel ≤5 cards, last "View More"; empty → empty state | ✅ | | ✅ debug demo data |
| R-1.3-11 | No location → all active listings | ✅ | ✅ | ✅ deny location |
| R-1.3-12 | Selected location → same city only | ✅ | ✅ | ✅ pick Madrid → 1 demo card |
| R-1.3-13 | Available first, newest first | ✅ | | |
| R-1.3-20 | Sign-in deferred; integration points tracked (placeholder) | ✅ | | ✅ Start/tabs open without prompt |
| R-1.3-30 | Guests see only active listings (withdrawn/expired hidden) | | ✅ | |
| R-1.3-31 | City scope query | | ✅ | |
| R-1.3-40 | My Giveaways: "You have no publications yet.", no chevron, Start → create flow | | | ✅ |
| R-1.3-41 | Available Giveaways chevron & View More → full catalog | | | ✅ |

Superseded in 1.3 (by product decision, not lost): R-1.1-02/03/05 and R-1.2-14 manual checks (auth prompt in UI) are paused while `AuthMode.DEFERRED`; their Kotlin tests still run and pass.
All previous rows re-run: 62/62 Kotlin, SQL v1.0–v1.3.

## v1.4 — Home empty states, My Claims, full catalog

| ID | Check | Kotlin | Manual (emulator) |
|----|-------|:-:|:-:|
| R-1.4-01 | Empty-state texts (My Giveaways, My Claims, Available) | ✅ | ✅ |
| R-1.4-02 | My Giveaways = own live publications only; no user → empty + Start | ✅ | ✅ |
| R-1.4-03 | My Claims = open requests on others' items, newest first | ✅ | |
| R-1.4-04 | Carousel 5 total, last View More (8 superseded) | ✅ | ✅ |
| R-1.4-05 | Catalog without location = all active, no cap | ✅ | ✅ deny location → View More |
| R-1.4-06 | Catalog with city = that city only | ✅ | ✅ pick Madrid → 1 card |
| R-1.4-07 | Category chips from feed + filtering | ✅ | ✅ tap "Kids toys" |
| R-1.4-08 | Reserved listing visible with "Reserved" badge, not faded (1.4.1) | ✅ | ✅ "Wooden toys" demo |

All previous rows re-run: 70/70 Kotlin (62 earlier + 8 new); SQL v1.0–v1.3 unchanged.

## v1.5 — Catalog categories & sorting

| ID | Check | Kotlin | SQL | Manual (emulator) |
|----|-------|:-:|:-:|:-:|
| R-1.5-01 | 7 fixed chips, All default | ✅ | | ✅ |
| R-1.5-02 | All = every category; chip filters one | ✅ | | ✅ |
| R-1.5-03 | Default Recent First | ✅ | | |
| R-1.5-04 | Sheet: exactly 2 sort options, no filters | ✅ | | ✅ |
| R-1.5-05 | Recently published available first | ✅ | | |
| R-1.5-06 | Closest nearby first by distance | ✅ | | |
| R-1.5-07 | Closest without location → recent first service-wide (Q10 answered) | ✅ | | |
| R-1.5-08 | Apply commits; Cancel discards pending only, earlier applied settings stay (1.5.1, supersedes "reset all") | ✅ | | ✅ |
| R-1.5-09 | Sort kept while switching chips | ✅ | | ✅ |
| R-1.5-10 | Example "Free to Take" card only until first real publication | ✅ | | ✅ carousel shows example + View More |
| R-1.5-20 | DB accepts only fixed category keys | | ✅ | |
| R-1.5-30 | Chip icon: selected lime chip + white icon fill; unselected white chip + lime icon fill | | | ✅ |

Superseded: R-1.4-07 (chips derived from feed) → fixed chips S2; test kept for the helper. Debug demo listings removed (R-1.3-10/12 manual checks now use the example card / real data).
All previous rows re-run: 80/80 Kotlin, SQL v1.0–v1.5.

## v1.6 — Giveaway details & request submission (0.8)

| ID | Check | Kotlin | SQL | Manual (emulator) |
|----|-------|:-:|:-:|:-:|
| R-1.6-01 | Calendar helpers (weekday, month/year rollover) | ✅ | | |
| R-1.6-02 | Past slots never offered | ✅ | ✅ | |
| R-1.6-03 | Whole weeks; only publisher dates enabled | ✅ | | ✅ grey vs white days |
| R-1.6-04 | Times enabled only for selected date | ✅ | | ✅ |
| R-1.6-05 | Single option preselected | ✅ | | |
| R-1.6-06 | Slot required and must be offered | ✅ | ✅ | ✅ Send disabled until date+time |
| R-1.6-07 | Note ≤1000 | ✅ | ✅ | ✅ counter near limit |
| R-1.6-08 | Guest → login sheet; form errors first; own item blocked | ✅ | ✅ | ✅ Continue / Next time |
| R-1.6-09 | My claims: Pending dimmed; collect only when approved | ✅ | | ✅ |
| R-1.6-10 | Copy (sheet, success, placeholder) | ✅ | | ✅ |
| R-1.6-11 | Example has future demo availability | ✅ | | ✅ |
| R-1.6-12 | Example request local, Pending in My claims | ✅ | | ✅ |
| R-1.6-13 | DB status mapping, "submitted on" label | ✅ | | ✅ reopen from My claims |
| R-1.6-20 | Availability readable by guests, not writable | | ✅ | |
| R-1.6-21..24 | submit_claim: slot rules, note, stored slot+note, exact point still private | | ✅ | |
| R-1.6-25 | Approve uses requested slot as pickup time | | ✅ | |
| R-1.6-30 | Details layout fits 390/412 widths, 16dp margins, no clipped chips | | | ✅ Pixel 8 + small phone |
| R-1.6-31 | Back returns to catalog or Home (where opened); Share opens share sheet; map icon opens maps | | | ✅ |

Superseded: "My claims" title → "My claims" (R-1.4-01 copy for the empty state unchanged).
All previous rows re-run: 93/93 Kotlin, SQL v1.0–v1.6 (old 2-arg submit_claim calls still valid for items without availability).

## v1.7 — Card states (0.9 + 0.10)

| ID | Check | Kotlin | SQL | Manual (emulator) |
|----|-------|:-:|:-:|:-:|
| R-1.7-01 | Public card: Available + heart, no trash | ✅ | | ✅ catalog / nearby |
| R-1.7-02 | My Claims pending + open: Pending + Available + heart + trash | ✅ | | ✅ My claims |
| R-1.7-03 | Every My Claims card has trash | ✅ | | ✅ |
| R-1.7-04 | Rejected greyed and removable | ✅ | ✅ | |
| R-1.7-05 | Recipient selected: no Available/heart | ✅ | | |
| R-1.7-06 | Remove pending: leaves queue, publisher notified, hidden | ✅ | ✅ | ✅ trash → Yes |
| R-1.7-07 | Remove rejected: hidden only | ✅ | ✅ | |
| R-1.7-08 | Remove approved: 8h rule; only owner | ✅ | ✅ | |
| R-1.7-09 | Reserved takes no new requests | ✅ | ✅ | |
| R-1.7-10 | My claims filters; favorites toggle | ✅ | | ✅ chips, heart |
| R-1.7-20 | Reserved hidden from public feed | | ✅ | |
| R-1.7-21 | Removed requester cannot be approved; notifications not client-writable | | ✅ | |
| R-1.7-23 | Favorites own rows only | | ✅ | |
| R-1.7-30 | Guest heart → login sheet; Close keeps screen | | | ✅ |

Superseded (tests kept, updated): R-1.0-07 (queue on reserved → no new requests), R-1.4-08 (reserved badge → hidden), R-1.3-11/13 + SQL R-1.3-30 (reserved no longer active), R-1.1-33 (pending cancel now notifies).
All previous rows re-run: 103/103 Kotlin, SQL v1.0–v1.7.

## v1.7.1 — Answers Q18–Q20, "My claims" terminology

| ID | Check | Kotlin | SQL | Manual |
|----|-------|:-:|:-:|:-:|
| R-1.7-05 | Pending + someone else selected → "Rejected for you", greyed, removable (supersedes "Reserved") | ✅ | | |
| R-1.7-11 | Approved: trash until pickup time; after → no trash, cancel refused | ✅ | ✅ | |
| R-1.7-12 | Rejected cannot request again | ✅ | ✅ | |
| R-1.7-13 | My claims sort (recent/oldest) + filter; Cancel keeps applied | ✅ | | ✅ sort icon |
| R-1.7-14 | "My claims" wording on Home and screen | | | ✅ |
| R-1.7-15 | Opening a rejected card shows the sent request read-only with "Rejected for you" | | | ✅ |

Superseded: R-1.0-12 no longer covers cancel at/after pickup (now refused). All rows re-run: 106/106 Kotlin, SQL v1.0–v1.7.1.

## v1.8 — Create publication (0.11)

| ID | Check | Kotlin | SQL | Manual (emulator) |
|----|-------|:-:|:-:|:-:|
| R-1.8-01 | Required fields; description/photos optional | ✅ | ✅ | ✅ Next disabled until valid |
| R-1.8-02 | Description ≤1000, no expand | ✅ | ✅ | |
| R-1.8-03 | Categories = catalog list without All | ✅ | ✅ | ✅ |
| R-1.8-04 | Date sheet Apply commits / Close keeps previous or placeholder | ✅ | | ✅ |
| R-1.8-05 | Calendar Sunday-first, past days disabled | ✅ | | ✅ |
| R-1.8-06 | Time range valid, not in past | ✅ | ✅ | ✅ |
| R-1.8-07 | 12h clock conversion + labels | ✅ | | |
| R-1.8-08 | Range → 30-min request slots | ✅ | ✅ | ✅ request on own new listing from another user |
| R-1.8-09 | Max 3 photos (alert), optional | ✅ | ✅ | ✅ gallery + camera |
| R-1.8-10 | Copy, 14-day lifetime | ✅ | ✅ | |
| R-1.8-20 | Guests cannot publish | | ✅ | ✅ Start → login sheet; Next time stays Home; after login → Home |
| R-1.8-21 | Server validation (title, description, time, category, photo ownership, count) | | ✅ | |
| R-1.8-22 | Published: live, approx point, private details, slots | | ✅ | ✅ appears in My publications + catalog |
| R-1.8-23 | Others see listing, not private meetup details | | ✅ | |
| R-1.8-30 | Ready! → Continue → My publications | | | ✅ |

All previous rows re-run: 116/116 Kotlin, SQL v1.0–v1.8. Note: after the first real publication the example card disappears (by design, 0.7).

## v1.8.1 — Answers Q22–Q24

| ID | Check | Kotlin | SQL | Manual / live |
|----|-------|:-:|:-:|:-:|
| R-1.8-09 | ≥1 photo required to publish (supersedes "optional") | ✅ | ✅ | ✅ Publish disabled with 0 photos |
| R-1.8.1-21 | Cleanup only for listings expired > 14 days; functions service_role only | | ✅ | |
| R-1.8.1-22 | Cleanup function refuses wrong token (403), runs with Vault token (200) | | | ✅ live smoke |
| R-1.8.1-23 | Grey note on photos step; "My giveaways location" caption; calendar single date | ✅ | | ✅ |

All previous rows re-run: 116/116 Kotlin, SQL v1.0–v1.8.1.

## v1.8.2 — Onboarding permissions order

| ID | Check | Kotlin | Manual (emulator, fresh install) |
|----|-------|:-:|:-:|
| R-1.1-21 | Next advances pages in order; only splash is timed (supersedes auto-advance) | ✅ | ✅ |
| R-1.1-22 | Splash 2 s; pages manual | ✅ | ✅ |
| R-1.1-23 | Next on 1–3, Start on 4 | ✅ | ✅ |
| R-1.1-24 | Start → notifications → location → catalog (any answers) | ✅ | ✅ allow/deny both |
| R-1.1-25 | Skip → notifications → location (supersedes "Skip → final screen"); Back one step | ✅ | ✅ |
| R-1.8.2-01 | No permission dialog before pages finished/skipped | ✅ | ✅ |

All previous rows re-run: 117/117 Kotlin.

## v1.8.3 — Home layout polish

| ID | Check | Manual (emulator) |
|----|-------|:-:|
| R-1.8.3-01 | Listing cards 170×238 (design); View More card same size, white background, "View More ›" | ✅ |
| R-1.8.3-02 | "Available Giveaways" header chevron hidden while the service has no real publications | ✅ |
| R-1.8.3-03 | My giveaways / Available Giveaways / My claims sections have equal height, 16dp side margins | ✅ |
| R-1.8.3-04 | My giveaways empty: "Nothing here yet," alone on line 1, rest below, then Start | ✅ (Kotlin copy test) |

## v1.8.4 — Food example card

| ID | Check | Kotlin | Manual |
|----|-------|:-:|:-:|
| R-1.8.4-01 | Example = food photo from design, title "Free food (example)", "Example" badge, no heart | ✅ | ✅ |
| R-1.8.4-02 | Example details: "This is only an example" banner + text about sharing surplus food with neighbours, safe to eat, not expired | ✅ | ✅ tap the card |

## v1.9 — My Publications (0.12)

| ID | Check | Kotlin | SQL | Manual (emulator) |
|----|-------|:-:|:-:|:-:|
| R-1.9-01 | Active default; sections Waiting / Action needed / Pending collection; Archived for given, withdrawn, expired | ✅ | ✅ | ✅ |
| R-1.9-02 | Badges "0 pending requests" grey, "N pending requests" lime; copy | ✅ | ✅ | ✅ |
| R-1.9-03 | Section order and tab split; empty state + Create | ✅ | | ✅ |
| R-1.9-04 | Cancel until 2 h before pickup start; trash hidden after; server CANCEL_TOO_LATE | ✅ | ✅ | ✅ |
| R-1.9-05 | Edit only before assignment; archived not clickable | ✅ | ✅ | ✅ |
| R-1.9-06 | Card labels ("today", dd/MM/yy, time range); edit prefill incl. stored photos | ✅ | | ✅ |
| R-1.9-20 | my_publications view: schedule, pending count, collector; private to the publisher | | ✅ | |
| R-1.9-21 | item_requests with nicknames for the publisher | | ✅ | ✅ review screen |
| R-1.9-22 | update_item: fields, private details, schedule replaced; photo required; NOT_EDITABLE after assignment | | ✅ | ✅ Save |
| R-1.9-23 | Cancel: all requesters notified with the spec message, claims closed, publication archived | | ✅ | ✅ delete flow + sheet |
| R-1.9-24 | Guest Create → login sheet; Next time stays on My publications | | | ✅ |

All previous rows re-run: 124/124 Kotlin, SQL v1.0–v1.9.

## v1.9.1 — Privacy + compression on selection

| ID | Check | Kotlin | SQL | Manual |
|----|-------|:-:|:-:|:-:|
| R-1.9.1-01 | No apartment/entrance/floor fields in the form, API or DB | ✅ | ✅ | ✅ form shows date, time, location, notes only |
| R-1.9.1-02 | Photo compressed immediately after pick/camera; upload sends the compressed file | | | ✅ |
| R-1.9.1-03 | Empty photo tiles #C8FF00 | | | ✅ |

All previous rows re-run: 124/124 Kotlin, SQL v1.0–v1.9.1.

## v1.10 — Recipient approval (0.13)

| ID | Check | Kotlin | SQL | Manual (emulator) |
|----|-------|:-:|:-:|:-:|
| R-1.10-01 | First request by default; "i out of N"; arrows | ✅ | | ✅ |
| R-1.10-02 | Reject removes card, next shows; last reject → back to details | ✅ | ✅ | ✅ swipe left / X |
| R-1.10-03 | Swipe right approves, left rejects (30% threshold); stamps | ✅ | | ✅ |
| R-1.10-04 | Copy from design (intro, approved screen, details labels) | ✅ | | ✅ |
| R-1.10-05 | Example publication with 3 crocodile requests (device-only) | ✅ | | ✅ Home → My giveaways example |
| R-1.10-06 | Intro shown once per device | ✅ | | ✅ |
| R-1.10-07 | Approve auto-rejects all other pending requests | ✅ | ✅ | ✅ |
| R-1.10-08 | No avatar → mascot on lime | | | ✅ |
| R-1.10-20 | item_requests exposes avatar; approval auto-reject server-side | | ✅ | |

Superseded: R-1.0-11 queue part, R-1.9 "tap Waiting → edit" (now details → Edit). All previous rows re-run: 130/130 Kotlin, SQL v1.0–v1.10.

## v1.11 — My Profile (0.14)

| ID | Check | Kotlin | SQL | Manual (emulator) |
|----|-------|:-:|:-:|:-:|
| R-1.11-01 | Public name replaces nickname; blank → nickname | ✅ | ✅ | ✅ |
| R-1.11-02 | Placeholder avatar stable per user | ✅ | | ✅ |
| R-1.11-03 | Password rules (6+, upper, lower, digit, special) | ✅ | | ✅ inline errors |
| R-1.11-04 | Change password form: Save enabled when filled; confirm must match | ✅ | | ✅ |
| R-1.11-05 | Email validation; notification texts; default prefs | ✅ | | |
| R-1.11-20 | Own profile editable (name, prefs, city); nickname not | | ✅ | |
| R-1.11-21 | Nearby notification to same-city users with pref on (not publisher) | | ✅ | |
| R-1.11-22 | New request → publisher; approval → requester; mark read | | ✅ | ✅ device notification |
| R-1.11-30 | Favourites grid, un-heart alert, empty state | | | ✅ |
| R-1.11-31 | Log out returns Home as guest | | | ✅ |

All previous rows re-run: 135/135 Kotlin, SQL v1.0–v1.11.

## v1.12 — Login and auth

| ID | Check | Kotlin | SQL | Manual (emulator) |
|----|-------|:-:|:-:|:-:|
| R-1.12-01 | Figma copy (errors, sheet, success texts) | ✅ | | ✅ |
| R-1.12-02 | Log in validation; server rejection marks both fields | ✅ | | ✅ |
| R-1.12-03 | New password twice + all rules | ✅ | | ✅ |
| R-1.12-04 | Links accepted only from the App Link host or freetotake:// | ✅ | | ✅ |
| R-1.12-05 | Sign-up / recovery end at Log in; Log in reachable from every step | ✅ | | ✅ |
| R-1.12-06 | Expired link → resend; used/invalid → back to email step | ✅ | ✅ | ✅ |
| R-1.12-07 | Auth enforced for protected actions; Terms (20 sections) | ✅ | | ✅ |
| R-1.12-20 | Email status (none / no password / active) | | ✅ | |
| R-1.12-21 | Link: 5-min expiry, single use, 1/min + 5/h, newer kills older | | ✅ | |
| R-1.12-22 | auth_links + RPCs not reachable by anon/authenticated | | ✅ | |
| R-1.12-30 | Edge Function: invalid email, captcha rejected/accepted, EMAIL_TAKEN / NOT_REGISTERED, redeem → session (verified live), second redeem → LINK_USED | | live | |
| R-1.12-31 | Captcha sheet before sign-up and reset emails | | | ✅ |
| R-1.12-32 | Email link opens the app → Create password → You're registered → Log in | | | ✅ (after setup) |

All previous rows re-run: 142/142 Kotlin, SQL v1.0–v1.12.

## v1.12.1

| ID | Check | Kotlin | Manual |
|----|-------|:-:|:-:|
| R-1.12.1-01 | Tab bar: selected lime bar + spotlight, black; others grey | | ✅ |
| R-1.12.1-02 | Example giveaway in My publications until the first own publication; not deletable; opens approval flow | ✅ | ✅ |
| R-1.12.1-03 | Home example card badges Example + Pending your approval | ✅ | ✅ |
| R-1.12.1-04 | Terms say "Free to Take" everywhere | ✅ | ✅ |

All previous rows re-run: 143/143 Kotlin.

## v1.13

| ID | Check | Kotlin | SQL | Manual |
|----|-------|:-:|:-:|:-:|
| R-1.13-01 | Captcha 3×3, 3–4 matches, one attempt per puzzle, pass single-use/15 min, labels hidden | ✅ | ✅ | ✅ |
| R-1.13-02 | Captcha once per flow (reused after EMAIL_TAKEN / NOT_REGISTERED) | ✅ | | ✅ |
| R-1.13-03 | Email link (function GET) → Android 302 into app; desktop text | | live | ✅ |
| R-1.13-04 | Example titles without "(example)", single badge | ✅ | | ✅ |
| R-1.13-05 | Empty states: "Nothing here yet." + mascot | ✅ | | ✅ |
| R-1.13-06 | Catalog: no tab bar, back → Home | | | ✅ |
| R-1.13-07 | Dark mode: neutrals inverted, lime + black-on-lime kept | | | ✅ |
| R-1.13-08 | Portrait lock | | | ✅ |

All previous rows re-run: 145/145 Kotlin, SQL v1.0–v1.13.

## v1.14

| ID | Check | Kotlin | SQL | Manual |
|----|-------|:-:|:-:|:-:|
| R-1.14-01 | No chat before approval; outsiders can't read/send | | ✅ | ✅ |
| R-1.14-02 | Approved collector + publisher read/send; trim, 1–1000 chars, no direct inserts | ✅ | ✅ | ✅ |
| R-1.14-03 | Meeting passed → CHAT_CLOSED, publication given, claim completed, history readable | ✅ | ✅ | ✅ |
| R-1.14-04 | Timeline day separators (Today / Yesterday / d MMM) | ✅ | | ✅ |
| R-1.14-05 | My claims "Finished" chip | ✅ | | ✅ |
| R-1.14-06 | Example pending claim, cancellable, hidden once real claims exist | ✅ | | ✅ |
| R-1.14-07 | Location saved → Continue; guest Profile placeholder with Register | | | ✅ |

All previous rows re-run: 151/151 Kotlin, SQL v1.0–v1.14.
