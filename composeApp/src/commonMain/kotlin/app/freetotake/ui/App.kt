// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Dina Yol (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.freetotake.domain.location.AreaLabel
import app.freetotake.domain.location.DeviceLocationProvider
import app.freetotake.domain.location.GeoPoint
import app.freetotake.domain.location.HomeLocationResolver
import app.freetotake.domain.location.LocationAccess
import app.freetotake.domain.location.LocationDefaults
import app.freetotake.domain.location.PickupPoint
import app.freetotake.domain.location.PlaceSearchPolicy
import app.freetotake.domain.location.PlaceSearchProvider
import app.freetotake.domain.location.PlaceSuggestion
import app.freetotake.domain.location.ReverseGeocoder
import app.freetotake.domain.model.Session
import app.freetotake.domain.model.User
import app.freetotake.domain.model.UserId
import app.freetotake.domain.onboarding.OnboardingEvent
import app.freetotake.domain.onboarding.OnboardingFlow
import app.freetotake.domain.onboarding.OnboardingStep
import app.freetotake.domain.prefs.AppPreferences
import app.freetotake.domain.rules.AuthGate
import app.freetotake.domain.rules.GateResult
import app.freetotake.domain.rules.PendingAction
import app.freetotake.domain.rules.ResumeResult
import app.freetotake.domain.rules.UserAction
import app.freetotake.ui.auth.AuthRequiredPrompt
import app.freetotake.ui.auth.AuthHost
import app.freetotake.data.supabase.AccountAuth
import app.freetotake.domain.auth.AuthFlow
import app.freetotake.domain.auth.AuthLink
import app.freetotake.domain.auth.AuthStep
import app.freetotake.ui.auth.label
import app.freetotake.ui.home.HomeScreen
import app.freetotake.ui.home.HomeTab
import app.freetotake.ui.location.LocationPickerScreen
import app.freetotake.ui.location.PlaceSearchScreen
import app.freetotake.ui.location.PickupConfirmationScreen
import app.freetotake.ui.home.CatalogScreen
import app.freetotake.domain.catalog.ExampleListing
import app.freetotake.data.supabase.FeedRepository
import app.freetotake.domain.catalog.FeedPolicy
import app.freetotake.domain.catalog.HomeCarousel
import app.freetotake.domain.catalog.MyClaims
import app.freetotake.domain.catalog.MyGiveaways
import app.freetotake.domain.location.PickupSelectionValidator
import app.freetotake.domain.location.PickupSource
import app.freetotake.domain.model.Item
import app.freetotake.domain.model.Timestamp
import app.freetotake.domain.rules.AccessPolicy
import app.freetotake.domain.rules.AuthMode
import app.freetotake.domain.rules.AuthModePolicy
import app.freetotake.ui.onboarding.OnboardingHost
import app.freetotake.ui.platform.PlatformBackHandler
import app.freetotake.ui.theme.BackChevron
import app.freetotake.ui.theme.FttColors
import app.freetotake.ui.theme.FttTheme
import app.freetotake.ui.theme.FttType
import kotlinx.coroutines.delay
import app.freetotake.domain.model.ClaimStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import app.freetotake.data.supabase.RequestRepository
import app.freetotake.data.supabase.FavoritesRepository
import app.freetotake.data.supabase.ProfileRepository
import app.freetotake.domain.profile.ChangePasswordForm
import app.freetotake.domain.profile.MyProfile
import app.freetotake.domain.profile.NotificationPrefs
import app.freetotake.domain.profile.NotificationText
import app.freetotake.domain.profile.PasswordFormErrors
import app.freetotake.domain.profile.ProfileCopy
import app.freetotake.domain.profile.ProfileRules
import app.freetotake.ui.profile.ChangePasswordScreen
import app.freetotake.ui.profile.EditProfileScreen
import app.freetotake.ui.profile.FavouritesScreen
import app.freetotake.ui.profile.NotificationsSheet
import app.freetotake.ui.profile.OkAlert
import app.freetotake.ui.profile.ProfileScreen
import app.freetotake.ui.profile.GuestProfileScreen
import app.freetotake.ui.profile.ProfileAvatar
import app.freetotake.ui.chat.ChatScreen
import app.freetotake.ui.chat.ChatAvatar
import app.freetotake.domain.chat.ChatInfo
import app.freetotake.domain.chat.ChatMessage
import app.freetotake.domain.chat.ChatRules
import app.freetotake.domain.chat.ChatSend
import app.freetotake.domain.chat.ChatError
import app.freetotake.domain.chat.ExampleChat
import app.freetotake.data.supabase.PublishRepository
import app.freetotake.data.supabase.MyPublicationsRepository
import app.freetotake.domain.publish.MyPublicationsRules
import app.freetotake.domain.publish.PublicationOverview
import app.freetotake.domain.publish.PublicationSection
import app.freetotake.domain.publish.PublicationsTab
import app.freetotake.ui.publish.BlockingLoader
import app.freetotake.ui.publish.DeletePublicationDialog
import app.freetotake.ui.publish.PublicationDeletedSheet
import app.freetotake.ui.publish.PublisherDetailsScreen
import app.freetotake.ui.publish.ReviewIntroScreen
import app.freetotake.ui.publish.CandidateReviewScreen
import app.freetotake.ui.publish.ExamplePhoto
import app.freetotake.domain.publish.Candidate
import app.freetotake.domain.publish.ExamplePublication
import app.freetotake.domain.publish.ReviewCopy
import app.freetotake.domain.publish.ReviewSession
import app.freetotake.resources.Res
import app.freetotake.resources.example_clothes
import app.freetotake.resources.empty_mascot
import app.freetotake.domain.model.ItemId
import org.jetbrains.compose.resources.painterResource
import app.freetotake.domain.media.ImageCompressor
import app.freetotake.domain.media.ImagePolicy
import app.freetotake.domain.publish.DateSheet
import app.freetotake.domain.publish.PublicationDraft
import app.freetotake.domain.publish.PublicationRules
import app.freetotake.domain.publish.PublishCopy
import app.freetotake.ui.publish.CreatePublicationScreen
import app.freetotake.ui.publish.DatePickerSheet
import app.freetotake.ui.publish.MaxPhotosAlert
import app.freetotake.ui.publish.MyPublicationsScreen
import app.freetotake.ui.publish.PhotoSourceSheet
import app.freetotake.domain.publish.PublishStep
import app.freetotake.ui.publish.TimePickerSheet
import app.freetotake.ui.request.SuccessScreen
import app.freetotake.domain.catalog.Favorites
import app.freetotake.domain.catalog.CardStates
import app.freetotake.ui.request.CancelRequestSheet
import app.freetotake.ui.request.MyClaimsScreen
import app.freetotake.domain.model.Claim
import app.freetotake.domain.model.ClaimId
import app.freetotake.domain.model.DomainResult
import app.freetotake.domain.request.DateKey
import app.freetotake.domain.request.ExampleRequest
import app.freetotake.domain.request.LocalNow
import app.freetotake.domain.request.PickupSlot
import app.freetotake.domain.request.RequestCheck
import app.freetotake.domain.request.RequestDraft
import app.freetotake.domain.request.RequestRules
import app.freetotake.domain.request.ShareLink
import app.freetotake.domain.request.SlotPicker
import app.freetotake.domain.request.TimeOfDay
import app.freetotake.domain.request.MyClaimsCopy
import app.freetotake.domain.rules.ClaimRules
import app.freetotake.ui.request.GiveawayDetailsScreen
import app.freetotake.ui.request.LoginSheet
import app.freetotake.ui.request.RequestSentScreen
import app.freetotake.ui.request.SubmittedRequest

/** Platform-provided dependencies (every external service behind an interface). */
class AppDeps(
    val prefs: AppPreferences,
    /** Launches the OS permission dialog for approximate location; reports the outcome. */
    val requestLocationPermission: (onResult: (LocationAccess) -> Unit) -> Unit,
    /** Push notifications system dialog (Android 13+); reports allowed/denied. Asked before location (1.8.2). */
    val requestNotificationPermission: (onResult: (Boolean) -> Unit) -> Unit = { it(false) },
    val deviceLocation: DeviceLocationProvider? = null,
    val reverseGeocoder: ReverseGeocoder? = null,
    val placeSearch: PlaceSearchProvider? = null,
    /** Public feed of active listings (Supabase view `active_items`). */
    val feed: FeedRepository? = null,
    /** Availability, send request, own requests (spec 0.8). */
    val requests: RequestRepository? = null,
    /** Heart / Favorites (spec 0.9/0.10). */
    val favorites: FavoritesRepository? = null,
    /** My Profile (spec 0.14). */
    val profile: ProfileRepository? = null,
    /** Shows a device notification (Android: only if notifications are permitted). */
    val postNotification: (title: String, body: String) -> Unit = { _, _ -> },
    /** Create publication (spec 0.11): upload + publish RPC, on-device compression, photo sources. */
    val publish: PublishRepository? = null,
    /** My Publications (spec 0.12). */
    val myPublications: MyPublicationsRepository? = null,
    /** v1.14 chat (publisher ↔ approved collector). */
    val chat: app.freetotake.data.supabase.ChatRepository? = null,
    /** v1.17 broccoli rewards (balance + handover confirmation). */
    val broccoli: app.freetotake.data.supabase.BroccoliRepository? = null,
    val compressor: ImageCompressor? = null,
    /** Compress a picked/taken photo right after selection (≤1600 px JPEG, ≤600 KB) → local file reference. */
    val preparePhoto: suspend (String) -> String = { it },
    /** Bytes of a prepared (already compressed) photo, for upload. */
    val readPhoto: suspend (String) -> ByteArray = { error("NO_PHOTO_READER") },
    val timeZone: () -> String = { "Europe/Madrid" },
    val pickPhoto: ((String?) -> Unit) -> Unit = { it(null) },
    val takePhoto: ((String?) -> Unit) -> Unit = { it(null) },
    /** Debug "Continue as test user": Supabase anonymous session when enabled; false → local-only test user. */
    val debugSignIn: suspend () -> Boolean = { false },
    /** Local date/time at the device (pickup options are local to the pickup place). */
    val localNow: () -> LocalNow = { LocalNow(DateKey(2026, 1, 1), TimeOfDay(0, 0)) },
    /** Platform share sheet. */
    val share: (String) -> Unit = {},
    /** Opens the point in the device's maps app (optional map icon). */
    val openMap: (GeoPoint?, String) -> Unit = { _, _ -> },
    /** Sign-in deferred by product decision; placeholders tracked in AuthIntegrationPoint. */
    val authMode: AuthMode = AuthModePolicy.CURRENT,
    val now: () -> Timestamp = { Timestamp(0) },
    /** Persisted session (Supabase); emits Guest when backend is not configured. */
    val session: Flow<Session> = flowOf(Session.Guest),
    val signOut: suspend () -> Unit = {},
    val isDebug: Boolean = false,
    /** v1.12 Login and auth: email + password accounts, captcha-gated email links. */
    val accountAuth: AccountAuth? = null,
    val openEmailApp: () -> Unit = {},
    /** URIs that opened the app (App Links / freetotake://). */
    val incomingLinks: Flow<String> = emptyFlow(),
    /** Host of the verified App Link domain (e.g. <user>.github.io); null → only the app scheme is accepted. */
    val authLinkHost: String? = null,
    /** v1.15: Google account picker (Android Credential Manager). */
    val googleSignIn: (suspend () -> app.freetotake.domain.auth.GoogleResult)? = null,
    /** v1.16: interstitial after a request / a new publication (Android: AdMob; skipped for ad-free users). */
    val showAd: (moment: app.freetotake.domain.monetization.AdMoment, isDemo: Boolean) -> Unit = { _, _ -> },
    /** v1.16: RevenueCat identity = Supabase user id (null → anonymous). */
    val identifyPurchaser: (String?) -> Unit = {},
)

private sealed interface Screen {
    data object Home : Screen
    data object LocationPicker : Screen
    data object PlaceSearch : Screen
    data object SignIn : Screen
    data object PickupConfirmed : Screen
    data object Catalog : Screen
    data class Details(val item: Item) : Screen
    data object RequestSent : Screen
    data object MyClaimsList : Screen
    data object CreatePublication : Screen
    data object PublicationLive : Screen
    data object MyPublications : Screen
    /** Publisher's details of one of my publications (or the example: [ExamplePublication.ID]). */
    data class PublisherDetails(val itemId: String) : Screen
    data class ReviewIntro(val itemId: String) : Screen
    data class Review(val itemId: String) : Screen
    data class RecipientApproved(val itemId: String) : Screen
    data object Profile : Screen
    /** v1.14: chat about a publication; [back] = where Back returns. */
    data class Chat(val itemId: String, val back: Screen) : Screen
    data object EditProfile : Screen
    data object ChangePassword : Screen
    data object Favourites : Screen
    data class ComingSoon(val title: String) : Screen
}

@Composable
fun App(deps: AppDeps) {
    FttTheme {
        var step by remember { mutableStateOf(OnboardingFlow.initialStep(deps.prefs.onboardingCompleted)) }
        var locationAccess by remember { mutableStateOf(deps.prefs.locationAccess) }

        fun onEvent(e: OnboardingEvent) {
            step = OnboardingFlow.reduce(step, e)
            // Device-local flag, deliberately independent of sign-in state.
            if (OnboardingFlow.shouldMarkCompleted(step)) deps.prefs.onboardingCompleted = true
        }

        LaunchedEffect(step) {
            // 1.8.2: permissions only after the onboarding pages are finished (Start) or skipped:
            // first push notifications, then approximate location.
            if (step == OnboardingStep.NOTIFICATION_PERMISSION) {
                deps.requestNotificationPermission { onEvent(OnboardingEvent.NotificationPermissionResolved) }
            }
            if (step == OnboardingStep.LOCATION_PERMISSION) {
                deps.requestLocationPermission { result ->
                    deps.prefs.locationAccess = result
                    locationAccess = result
                    onEvent(OnboardingEvent.LocationPermissionResolved)
                }
            }
        }

        // v1.17: "You have a reward — 20 broccolies" once after onboarding, even when it was skipped.
        var rewardSeen by remember { mutableStateOf(deps.prefs.broccoliRewardSeen) }
        when {
            step != OnboardingStep.DONE -> OnboardingHost(step, ::onEvent)
            app.freetotake.domain.onboarding.BroccoliReward.mustShow(onboardingDone = true, rewardSeen = rewardSeen) ->
                app.freetotake.ui.rewards.BroccoliRewardScreen(onContinue = { deps.prefs.broccoliRewardSeen = true; rewardSeen = true })
            else -> MainArea(deps, locationAccess)
        }
    }
}

@Composable
private fun MainArea(deps: AppDeps, locationAccess: LocationAccess) {
    val scope = rememberCoroutineScope()
    val backendSession by deps.session.collectAsState(initial = Session.Restoring)
    var debugSession by remember { mutableStateOf<Session?>(null) }
    // ---- auth (v1.12) ----
    var authStep by remember { mutableStateOf<AuthStep>(AuthStep.Login) }
    var linkSession by remember { mutableStateOf(false) }   // one-off session from an email link, password not set yet
    val session = debugSession ?: backendSession

    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var message by remember { mutableStateOf<String?>(null) }

    // ---- location state (spec 0.4) ----
    var chosen by remember { mutableStateOf(deps.prefs.chosenPickup) }
    var devicePoint by remember { mutableStateOf<GeoPoint?>(null) }
    var deviceArea by remember { mutableStateOf<AreaLabel?>(null) }
    var lookingUp by remember { mutableStateOf(locationAccess.hasDeviceLocation) }
    LaunchedEffect(locationAccess) {
        if (!locationAccess.hasDeviceLocation) { lookingUp = false; return@LaunchedEffect }
        lookingUp = true
        val p = deps.deviceLocation?.approximateLocation()
        devicePoint = p
        deviceArea = p?.let { runCatching { deps.reverseGeocoder?.areaAt(it) }.getOrNull() }
        lookingUp = false
    }
    val homeLocation = HomeLocationResolver.resolve(chosen, locationAccess, deviceArea, lookingUp)

    // ---- Available Giveaways (spec 0.5) ----
    val feedScope = FeedPolicy.scope(chosen, locationAccess, deviceArea)
    var rawFeed by remember { mutableStateOf<List<Item>>(emptyList()) }
    var serviceHasReal by remember { mutableStateOf(true) } // assume real data until checked (no flash of the example)
    var feedVersion by remember { mutableStateOf(0) }   // bumped after publishing
    LaunchedEffect(feedScope, feedVersion) {
        rawFeed = runCatching { deps.feed?.activeFeed(feedScope, limit = 100).orEmpty() }.getOrDefault(emptyList())
        serviceHasReal = rawFeed.isNotEmpty() || runCatching { deps.feed?.hasAnyActive() ?: false }.getOrDefault(true)
    }
    // Educational "Free to Take" example only while the whole service has no real publications (spec 0.7).
    val feed = ExampleListing.apply(FeedPolicy.apply(rawFeed, feedScope, deps.now()), serviceHasReal)

    // ---- picker state ----
    var candidate by remember { mutableStateOf<PickupPoint?>(null) }
    var candidateSource by remember { mutableStateOf<PickupSource?>(null) }
    var resolving by remember { mutableStateOf(false) }
    var moveTo by remember { mutableStateOf<GeoPoint?>(null) }
    var skipNextIdleLookup by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<PlaceSuggestion>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    val bias = chosen?.point ?: devicePoint

    LaunchedEffect(query) {
        searchError = null
        if (!PlaceSearchPolicy.shouldSearch(query)) { results = emptyList(); searching = false; return@LaunchedEffect }
        searching = true
        delay(PlaceSearchPolicy.DEBOUNCE_MS)
        runCatching { deps.placeSearch?.search(query, bias).orEmpty() }
            .onSuccess { results = it }
            .onFailure { searchError = "Search is unavailable right now. Move the map to pick a point." }
        searching = false
    }

    // ---- auth gate ----
    val gate = remember { AuthGate() }
    var prompt by remember { mutableStateOf<PendingAction?>(null) }

    // ---- request submission (spec 0.8) ----
    var slots by remember { mutableStateOf<List<PickupSlot>?>(null) }
    var draft by remember { mutableStateOf(RequestDraft()) }
    var detailsFor by remember { mutableStateOf<app.freetotake.domain.model.ItemId?>(null) }
    var sending by remember { mutableStateOf(false) }
    var requestError by remember { mutableStateOf<String?>(null) }
    var loginSheet by remember { mutableStateOf(false) }
    var lastDetails by remember { mutableStateOf<Screen.Details?>(null) }
    var detailsOrigin by remember { mutableStateOf<Screen>(Screen.Home) }
    var detailsFromClaims by remember { mutableStateOf(false) }   // v1.16.4: opened from My claims (vs Available Giveaways)   // Back returns to catalog or Home
    var localClaims by remember { mutableStateOf<List<Claim>>(emptyList()) }   // example-card requests (device only)
    var remoteClaims by remember { mutableStateOf<List<Claim>>(emptyList()) }
    LaunchedEffect(session) {
        val user = (session as? Session.Authenticated)?.user
        remoteClaims = if (user == null) emptyList()
            else runCatching { deps.requests?.myRequests(user.id).orEmpty() }.getOrDefault(emptyList())
    }
    val userId = (session as? Session.Authenticated)?.user?.id
    val allClaims = localClaims.filter { it.takerId == userId } + remoteClaims
    fun myRequestFor(item: Item): Claim? = allClaims.firstOrNull { it.itemId == item.id && it.status.isActive && !it.hiddenByTaker }

    // ---- card states, favorites, removal (spec 0.9 / 0.10) ----
    // Real backend session (the debug test user is local-only and cannot write to Supabase).
    val realSession = debugSession == null && session is Session.Authenticated
    LaunchedEffect(userId, realSession) { deps.identifyPurchaser(if (realSession) userId?.value else null) }   // v1.16 RevenueCat

    // ---- v1.17 broccoli rewards: server balance for signed-in users, device balance for guests / debug user ----
    var broccoli by remember { mutableStateOf(deps.prefs.localBroccoli) }
    var broccoliVersion by remember { mutableStateOf(0) }
    var showBroccoliRules by remember { mutableStateOf(false) }
    var pendingHandovers by remember { mutableStateOf<List<app.freetotake.domain.rewards.PendingHandover>>(emptyList()) }
    var handoverBusy by remember { mutableStateOf(false) }
    LaunchedEffect(realSession, userId, broccoliVersion) {
        val repo = deps.broccoli
        if (realSession && repo != null) {
            runCatching { repo.balance() }.onSuccess { broccoli = it }
            runCatching { repo.pendingHandovers() }.onSuccess { pendingHandovers = it }
        } else { broccoli = deps.prefs.localBroccoli; pendingHandovers = emptyList() }
    }
    fun setLocalBroccoli(n: Int) { deps.prefs.localBroccoli = n; broccoli = n }
    var favorites by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(realSession, userId) {
        favorites = if (!realSession) emptySet()
            else runCatching { deps.favorites?.list().orEmpty().map { it.value }.toSet() }.getOrDefault(emptySet())
    }
    // Listings behind my requests — reserved ones are no longer in the public feed (0.10).
    var claimItems by remember { mutableStateOf<List<Item>>(emptyList()) }
    LaunchedEffect(remoteClaims) {
        val ids = remoteClaims.map { it.itemId }.distinct()
        claimItems = runCatching { deps.feed?.itemsByIds(ids).orEmpty() }.getOrDefault(emptyList())
    }
    var exampleClaimDismissed by remember { mutableStateOf(deps.prefs.exampleClaimDismissed) }
    val myEntries = MyClaims.withExample(
        MyClaims.of(userId, allClaims, (claimItems + feed + ExampleListing.item).distinctBy { it.id }),
        exampleClaimDismissed, app.freetotake.domain.request.ExampleClaim.claim(userId, deps.localNow().date, deps.now()),
    )
    fun claimFor(item: Item): Claim? = myEntries.firstOrNull { it.item.id == item.id }?.claim
    var removeTarget by remember { mutableStateOf<MyClaims.Entry?>(null) }
    var beforeLogin by remember { mutableStateOf<Screen>(Screen.Home) }

    // Publication draft lives here (declared before perform(): sign-in resumes the publication flow).
    var pubDraft by remember { mutableStateOf(PublicationDraft()) }
    var pubStep by remember { mutableStateOf(PublishStep.FORM) }

    fun perform(request: PendingAction, resumed: Boolean) {
        screen = when (request.action) {
            // v1.16.9: signing in from "Next" resumes the publication (draft kept), on the photos step when the form is complete.
            UserAction.POST_ITEM -> { pubStep = app.freetotake.domain.publish.PublishFlow.resumeStep(pubDraft, deps.localNow()); Screen.CreatePublication }
            UserAction.VIEW_MY_ITEMS -> Screen.MyPublications
            UserAction.VIEW_MY_CLAIMS -> Screen.MyClaimsList
            // Heart after login: back where the user was; tap the heart again (same pattern as Q14).
            UserAction.FAVORITES -> beforeLogin
            // Resume after login: back to the same details with the form kept; user taps Send again (Q: auto-send?).
            UserAction.SUBMIT_CLAIM -> lastDetails ?: Screen.Home
            else -> Screen.Home
        }
        message = if (resumed) "Resumed after sign-in: ${request.action.label()}" else null
    }

    fun attempt(request: PendingAction) {
        // PLACEHOLDER (AuthIntegrationPoint): while sign-in is deferred, protected actions proceed.
        if (!AuthModePolicy.shouldPrompt(deps.authMode, AccessPolicy.check(session, request.action))) {
            perform(request, resumed = false); return
        }
        when (val r = gate.attempt(session, request)) {
            is GateResult.Proceed -> perform(r.request, resumed = false)
            is GateResult.ShowAuthPrompt -> prompt = r.request
        }
    }

    LaunchedEffect(Unit) {
        // App died between opening the link and setting the password → close that one-off session.
        if (deps.prefs.passwordSetupPending) { runCatching { deps.signOut() }; deps.prefs.passwordSetupPending = false }
        deps.incomingLinks.collect { uri ->
            AuthLink.parse(uri, deps.authLinkHost)?.let { link -> authStep = AuthStep.Redirecting(link); screen = Screen.SignIn }
        }
    }

    LaunchedEffect(session) {
        if (session is Session.Authenticated && !linkSession) {
            when (val r = gate.onAuthenticated(session)) {
                is ResumeResult.Resume -> perform(r.request, resumed = true)
                is ResumeResult.NoLongerValid -> { screen = Screen.Home; message = "That action is no longer available." }
                ResumeResult.NothingToResume -> if (screen == Screen.SignIn) screen = Screen.Home
            }
        }
    }

    fun openListing(item: Item, fromClaims: Boolean = false) {
        requestError = null; sending = false; slots = null; detailsFromClaims = fromClaims
        if (screen == Screen.Home || screen == Screen.Catalog || screen == Screen.MyClaimsList) detailsOrigin = screen
        if (detailsFor != item.id) { draft = RequestDraft(); detailsFor = item.id }
        screen = Screen.Details(item).also { lastDetails = it }
        scope.launch {
            val loaded = if (ExampleListing.isExample(item)) ExampleRequest.slots(deps.localNow().date)
                else runCatching { deps.requests?.availability(item.id).orEmpty() }.getOrElse { emptyList() }
            slots = loaded
            // Single suitable option → preselected (spec 0.8).
            if (draft.slot == null) draft = SlotPicker.preselect(loaded, deps.localNow(), draft)
        }
    }

    fun sendRequest(item: Item) {
        val available = slots ?: return
        when (RequestRules.check(session, item, draft, available, deps.localNow())) {
            RequestCheck.LoginRequired -> {
                // Spec 0.8: guests get the "Log in to finish this action" sheet (even while sign-in is deferred elsewhere).
                gate.attempt(session, PendingAction(UserAction.SUBMIT_CLAIM, item.id.value))
                loginSheet = true
            }
            RequestCheck.Ok -> {
                val slot = draft.slot ?: return
                if (ExampleListing.isExample(item)) {
                    // Educational example: kept on this device only, never sent to the backend.
                    val r = ClaimRules.submit(session, item, localClaims.filter { it.itemId == item.id }, ClaimId("local-${deps.now().epochMillis}"), deps.now(), draft.note.ifBlank { null })
                    if (r is DomainResult.Ok) {
                        localClaims = localClaims + r.value.copy(requested = slot)
                        draft = RequestDraft(); screen = Screen.RequestSent
                        deps.showAd(app.freetotake.domain.monetization.AdMoment.REQUEST_SENT, true)   // demo: policy skips it
                    } else requestError = if ((r as DomainResult.Err).error == app.freetotake.domain.model.DomainError.ALREADY_REJECTED) "Your request for this giveaway was rejected — you can't request it again." else "You already requested this giveaway."
                    return
                }
                val repo = deps.requests ?: run { requestError = "Can't reach the server. Try again later."; return }
                // v1.17: a request costs 1 broccoli (the server enforces it too).
                if (!app.freetotake.domain.rewards.BroccoliRules.canRequest(broccoli, example = false)) {
                    requestError = app.freetotake.domain.rewards.BroccoliCopy.NOT_ENOUGH; return
                }
                sending = true
                scope.launch {
                    runCatching { repo.submit(item.id, slot, draft.note) }
                        .onSuccess { claim ->
                            remoteClaims = listOf(claim) + remoteClaims; draft = RequestDraft(); screen = Screen.RequestSent
                            broccoli = app.freetotake.domain.rewards.BroccoliRules.afterRequest(broccoli, example = false); broccoliVersion++
                            deps.showAd(app.freetotake.domain.monetization.AdMoment.REQUEST_SENT, false)
                        }
                        .onFailure { e -> requestError = serverMessage(e.message) }
                    sending = false
                }
            }
            RequestCheck.SlotRequired -> requestError = "Choose a pickup date and time."
            RequestCheck.SlotNotOffered -> requestError = "This pickup time is no longer available. Choose another one."
            RequestCheck.NoteTooLong -> requestError = "Keep your note under ${RequestRules.NOTE_MAX} characters."
            RequestCheck.OwnItem -> requestError = "This is your own giveaway."
            RequestCheck.NotClaimable -> requestError = "This giveaway is no longer available."
        }
    }

    // ---- create publication (spec 0.11) ----
    var publishing by remember { mutableStateOf(false) }
    var pubError by remember { mutableStateOf<String?>(null) }
    var dateSheet by remember { mutableStateOf<DateSheet?>(null) }
    var timeSheet by remember { mutableStateOf(false) }
    var photoSheet by remember { mutableStateOf(false) }
    var maxPhotosAlert by remember { mutableStateOf(false) }
    var pickerForPublication by remember { mutableStateOf(false) }
    var loginTitle by remember { mutableStateOf(RequestRules.LOGIN_SHEET_TITLE) }
    var myItems by remember { mutableStateOf<List<Item>>(emptyList()) }
    LaunchedEffect(userId, realSession, feedVersion) {
        val uid = userId
        myItems = if (uid == null || !realSession) emptyList()
            else runCatching { deps.feed?.myItems(uid).orEmpty() }.getOrDefault(emptyList())
    }

    fun startPublication() {
        // v1.16.9: guests may fill the form; they are asked to log in on "Next" (see onNext).
        if (pubDraft.editingItemId != null) pubDraft = PublicationDraft()        // never continue an edit as a new publication
        if (pubDraft.pickup == null) pubDraft = pubDraft.copy(pickup = chosen)   // Home location = default pickup
        pubStep = PublishStep.FORM; pubError = null; screen = Screen.CreatePublication
    }

    fun addPhotoTapped() {
        if (!ImagePolicy.canAddPhoto(pubDraft.photoCount)) maxPhotosAlert = true else photoSheet = true
    }

    fun onPhotoPicked(uri: String?) {
        if (uri == null) return
        if (!ImagePolicy.canAddPhoto(pubDraft.photoCount)) { maxPhotosAlert = true; return }
        scope.launch {
            // Auto-compression right after selection (spec 0.11); the draft keeps only the compressed copy.
            runCatching { deps.preparePhoto(uri) }
                .onSuccess { prepared -> PublicationRules.addPhoto(pubDraft, prepared)?.let { pubDraft = it } ?: run { maxPhotosAlert = true } }
                .onFailure { pubError = "This photo can't be used. Try another one (JPEG, PNG, WebP or HEIC)." }
        }
    }

    // ---- My Publications (spec 0.12) ----
    var publications by remember { mutableStateOf<List<PublicationOverview>>(emptyList()) }
    var pubsTab by remember { mutableStateOf(PublicationsTab.DEFAULT) }
    var pubsMessage by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<PublicationOverview?>(null) }
    var deleting by remember { mutableStateOf(false) }
    var deletedSheet by remember { mutableStateOf(false) }
    var reviewBusy by remember { mutableStateOf(false) }
    // ---- recipient approval (spec 0.13) ----
    var reviewSession by remember { mutableStateOf(ReviewSession(emptyList())) }
    var exampleSession by remember { mutableStateOf(ReviewSession(ExamplePublication.candidates)) }   // device-only example
    var exampleCollector by remember { mutableStateOf<Candidate?>(null) }
    var publisherOrigin by remember { mutableStateOf<Screen>(Screen.Home) }   // where the example details return to
    var exampleGiveawayDismissed by remember { mutableStateOf(deps.prefs.exampleGiveawayDismissed) }

    // ---- chat (v1.14): server decides access + expiry; the app polls while the chat is open ----
    var chatInfo by remember { mutableStateOf<ChatInfo?>(null) }
    var chatMessages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var chatError by remember { mutableStateOf<String?>(null) }
    var chatLoading by remember { mutableStateOf(false) }
    var exampleChats by remember { mutableStateOf<Map<String, List<ChatMessage>>>(emptyMap()) }   // device-only demo chats, per example item

    fun openChat(itemId: String, back: Screen) {
        chatInfo = null; chatMessages = emptyList(); chatError = null; chatLoading = true
        screen = Screen.Chat(itemId, back)
    }

    fun sendChat(itemId: String, body: String) {
        chatError = null
        if (ExampleChat.isExample(itemId)) {
            val now = deps.now(); val today = deps.localNow().date
            fun add(m: ChatMessage) { exampleChats = exampleChats + (itemId to (exampleChats[itemId].orEmpty() + m)) }
            add(ChatMessage("ex-m-${now.epochMillis}", true, body, now, today))
            scope.launch {
                delay(1_200)
                val t = deps.now()
                add(ChatMessage("ex-r-${t.epochMillis}", false, ExampleChat.reply(itemId), t, deps.localNow().date))
            }
            return
        }
        val repo = deps.chat ?: run { chatError = ChatError.NETWORK.message; return }
        scope.launch {
            when (val r = repo.send(itemId, body)) {
                ChatSend.Ok -> chatMessages = runCatching { repo.messages(itemId) }.getOrDefault(chatMessages)
                is ChatSend.Failed -> {
                    chatError = r.error.message
                    if (r.error == ChatError.CLOSED) chatInfo = chatInfo?.copy(open = false)
                }
            }
        }
    }

    LaunchedEffect(screen) {
        val s = screen as? Screen.Chat ?: return@LaunchedEffect
        if (ExampleChat.isExample(s.itemId)) { chatLoading = false; return@LaunchedEffect }
        val repo = deps.chat ?: run { chatLoading = false; chatError = ChatError.NETWORK.message; return@LaunchedEffect }
        while (true) {
            runCatching {
                val info = repo.info(s.itemId)
                chatInfo = info
                if (info == null) chatError = ChatError.FORBIDDEN.message
                else chatMessages = repo.messages(s.itemId)
            }
            chatLoading = false
            delay(ChatRules.POLL_MS)
        }
    }
    var editOriginalPaths by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(userId, realSession, feedVersion) {
        publications = if (!realSession) emptyList()
            else runCatching { deps.myPublications?.list().orEmpty() }.getOrDefault(emptyList())
    }
    LaunchedEffect(pubsMessage) { if (pubsMessage != null) { delay(3_000); pubsMessage = null } }

    fun editPublication(p: PublicationOverview) {
        scope.launch {
            val (point, details) = runCatching { deps.myPublications?.meetup(p.item.id) }.getOrNull() ?: (null to app.freetotake.domain.publish.MeetupDetails())
            pubDraft = MyPublicationsRules.draftFor(p, point, details)
            editOriginalPaths = p.item.photoPaths
            pubStep = PublishStep.FORM; pubError = null; screen = Screen.CreatePublication
        }
    }

    fun openPublication(p: PublicationOverview) {
        // 0.13: opening a publication shows "My Publication Details" (edit from there); archived is not clickable.
        if (ExamplePublication.isExample(p.item.id.value)) publisherOrigin = Screen.MyPublications
        if (MyPublicationsRules.section(p, deps.now()) != PublicationSection.ARCHIVED) screen = Screen.PublisherDetails(p.item.id.value)
    }

    fun openReview(itemId: String) {
        if (ExamplePublication.isExample(itemId)) reviewSession = exampleSession
        else {
            reviewSession = ReviewSession(emptyList())
            scope.launch {
                reviewSession = ReviewSession(runCatching { deps.myPublications?.requests(ItemId(itemId)).orEmpty() }.getOrDefault(emptyList())
                    .map { Candidate(it.claimId.value, it.takerNickname, it.message.orEmpty(), it.avatarUrl) })
            }
        }
        // First entry only: "Time to choose your giveaway recipient!" (persisted per device — open question).
        screen = if (deps.prefs.reviewIntroSeen) Screen.Review(itemId) else Screen.ReviewIntro(itemId)
    }

    fun approveCandidate(itemId: String, c: Candidate) {
        if (ExamplePublication.isExample(itemId)) {
            exampleCollector = c; exampleSession = ReviewSession(emptyList()); screen = Screen.RecipientApproved(itemId); return
        }
        val repo = deps.myPublications ?: return
        reviewBusy = true
        scope.launch {
            runCatching { repo.approve(app.freetotake.domain.model.ClaimId(c.id)) }
                .onSuccess { feedVersion++; screen = Screen.RecipientApproved(itemId) }   // others auto-rejected server-side
                .onFailure { e -> pubsMessage = serverMessage(e.message); screen = Screen.PublisherDetails(itemId) }
            reviewBusy = false
        }
    }

    fun rejectCandidate(itemId: String, c: Candidate) {
        reviewSession = reviewSession.reject(c.id)
        if (ExamplePublication.isExample(itemId)) exampleSession = reviewSession
        else deps.myPublications?.let { repo ->
            scope.launch {
                runCatching { repo.reject(app.freetotake.domain.model.ClaimId(c.id)) }
                    .onSuccess { feedVersion++ }
                    .onFailure { e -> pubsMessage = serverMessage(e.message) }
            }
        }
        if (reviewSession.isEmpty) screen = Screen.PublisherDetails(itemId)
    }

    // ---- My Profile (spec 0.14) ----
    var profileVersion by remember { mutableStateOf(0) }
    var myProfile by remember { mutableStateOf<MyProfile?>(null) }
    LaunchedEffect(userId, realSession, profileVersion) {
        myProfile = when {
            realSession -> runCatching { deps.profile?.me() }.getOrNull()
            userId != null -> MyProfile(userId.value, (session as? Session.Authenticated)?.user?.nickname ?: "TestOtter0001", null, null, null, isAnonymous = true)
            else -> null
        }
    }
    var editName by remember { mutableStateOf("") }
    var editEmail by remember { mutableStateOf("") }
    var profileSaving by remember { mutableStateOf(false) }
    var profileError by remember { mutableStateOf<String?>(null) }
    var profileMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(profileMessage) { if (profileMessage != null) { delay(3_000); profileMessage = null } }
    var pwOld by remember { mutableStateOf("") }
    var pwNew by remember { mutableStateOf("") }
    var pwConfirm by remember { mutableStateOf("") }
    var pwErrors by remember { mutableStateOf<PasswordFormErrors?>(null) }
    var pwServerError by remember { mutableStateOf<String?>(null) }
    var okAlert by remember { mutableStateOf<String?>(null) }
    var notifSheet by remember { mutableStateOf(false) }
    var favouriteItems by remember { mutableStateOf<List<Item>?>(null) }

    // Coarse home city (never an address) for "new giveaways nearby".
    val homeCity = (feedScope as? app.freetotake.domain.catalog.FeedScope.City)?.city
    LaunchedEffect(realSession, homeCity) { if (realSession) runCatching { deps.profile?.setHomeCity(homeCity) } }
    // Server events → device notifications while the app runs (background push needs FCM — next step).
    LaunchedEffect(realSession) {
        while (realSession) {
            runCatching {
                val repo = deps.profile ?: return@runCatching
                val pending = repo.unreadNotifications()
                pending.forEach { n -> deps.postNotification(NotificationText.title(n.kind, n.itemTitle), NotificationText.body(n.kind, n.itemTitle, n.body)) }
                repo.markRead(pending.map { it.id })
            }
            delay(60_000)
        }
    }

    fun openProfile() {
        screen = Screen.Profile   // v1.14: guests see a placeholder profile with Register
    }

    fun startEditProfile() {
        val p = myProfile ?: return
        editName = p.publicName.orEmpty(); editEmail = p.email.orEmpty(); profileError = null
        screen = Screen.EditProfile
    }

    fun saveProfile() {
        val p = myProfile ?: return
        val repo = deps.profile
        if (!realSession || repo == null) { profileError = "Saving needs a server account (enable anonymous sign-ins, then sign in with the test user)."; return }
        val email = editEmail.trim()
        if (email.isNotEmpty() && !ProfileRules.isValidEmail(email)) { profileError = "Enter a valid email address."; return }
        profileSaving = true; profileError = null
        scope.launch {
            runCatching {
                val name = ProfileRules.normalizedPublicName(editName)
                if (name != p.publicName) repo.setPublicName(name)
                if (email.isNotEmpty() && email != p.email) repo.setEmail(email)
            }.onSuccess {
                profileVersion++; screen = Screen.Profile
                profileMessage = if (email.isNotEmpty() && email != p.email) "Saved. Confirm the new email from your inbox." else "Saved."
            }.onFailure { e -> profileError = "Couldn't save: ${e.message?.take(80) ?: "try again"}" }
            profileSaving = false
        }
    }

    fun changeAvatar() {
        val repo = deps.profile
        if (!realSession || repo == null) { profileError = "Uploading an avatar needs a server account."; return }
        deps.pickPhoto { uri ->
            if (uri != null) scope.launch {
                runCatching { repo.uploadAvatar(deps.readPhoto(deps.preparePhoto(uri))) }   // compressed like listing photos
                    .onSuccess { profileVersion++ }
                    .onFailure { profileError = "Couldn't upload the photo. Try another one." }
            }
        }
    }

    fun savePassword() {
        val errs = ChangePasswordForm.validate(pwOld, pwNew, pwConfirm)
        pwErrors = errs; pwServerError = null
        if (!errs.isValid) return
        val repo = deps.profile
        if (myProfile?.isAnonymous != false || repo == null) { pwServerError = "Your test account has no password yet — it comes with Log in / Registration."; return }
        profileSaving = true
        scope.launch {
            runCatching { repo.changePassword(pwOld, pwNew) }
                .onSuccess { pwOld = ""; pwNew = ""; pwConfirm = ""; pwErrors = null; okAlert = ProfileCopy.PASSWORD_CHANGED }
                .onFailure { e -> pwServerError = if ("WRONG_OLD_PASSWORD" in (e.message ?: "")) "The old password is not correct." else "Couldn't change the password. Try again." }
            profileSaving = false
        }
    }

    /** v1.12: reset only through the captcha-gated recovery flow (same as the Log in screen). */
    fun forgotPassword() {
        myProfile?.email?.let { deps.prefs.lastEmail = it }
        authStep = AuthStep.Recovery
        screen = Screen.SignIn
    }

    fun openFavourites() {
        favouriteItems = null; screen = Screen.Favourites
        scope.launch {
            val ids = favorites.map { ItemId(it) }
            val known = (feed + myItems).filter { it.id in ids }
            val missing = ids - known.map { it.id }.toSet()
            favouriteItems = (known + runCatching { deps.feed?.itemsByIds(missing).orEmpty() }.getOrDefault(emptyList())).distinctBy { it.id }
        }
    }

    fun setPrefs(prefs: NotificationPrefs) {
        val p = myProfile ?: return
        myProfile = p.copy(prefs = prefs)
        if (realSession) scope.launch { runCatching { deps.profile?.setPrefs(prefs) }.onFailure { myProfile = p; profileMessage = "Couldn't save the setting." } }
    }

    fun deletePublication(p: PublicationOverview) {
        if (ExamplePublication.isExample(p.item.id.value)) {   // v1.15.4: example removed on this device only
            exampleGiveawayDismissed = true; deps.prefs.exampleGiveawayDismissed = true
            if ((screen as? Screen.PublisherDetails)?.itemId == ExamplePublication.ID) screen = publisherOrigin
            return
        }
        val repo = deps.myPublications ?: return
        deleting = true
        scope.launch {
            runCatching { repo.cancel(p.item.id) }
                .onSuccess { publications = publications.filterNot { it.item.id == p.item.id }; feedVersion++; deletedSheet = true }
                .onFailure { e -> pubsMessage = if ("CANCEL_TOO_LATE" in (e.message ?: "")) "Less than 2 hours before pickup — it can't be cancelled now." else "Couldn't delete. Try again." }
            deleting = false
        }
    }

    fun publishNow() {
        val repo = deps.publish
        // v1.16.9: debug test user without a server session → mocked publish, straight to the success screen.
        if (app.freetotake.domain.publish.PublishFlow.mockPublish(debugSession != null, repo?.currentUserId() != null) && pubDraft.editingItemId == null) {
            pubDraft = PublicationDraft(); pubStep = PublishStep.FORM
            setLocalBroccoli(app.freetotake.domain.rewards.BroccoliRules.afterPost(broccoli))
            screen = Screen.PublicationLive; return
        }
        if (repo == null || repo.currentUserId() == null) {
            pubError = "Publishing needs a server session. Enable anonymous sign-ins in Supabase (see instructions) and sign in with the test user again."
            return
        }
        publishing = true; pubError = null
        scope.launch {
            runCatching {
                val draftId = "d${deps.now().epochMillis}"
                // Compressed on the device before upload (ImagePolicy: ≤1600 px, JPEG, HEIC accepted).
                val paths = pubDraft.photos.mapIndexed { i, uri ->
                    repo.uploadPhoto(draftId, i + 1, deps.readPhoto(uri))   // already compressed at selection
                }
                val editing = pubDraft.editingItemId
                if (editing != null) {
                    val kept = pubDraft.storedPhotos.map { it.path }
                    repo.update(editing, pubDraft, kept + paths, deps.timeZone())
                    runCatching { repo.deletePhotos(editOriginalPaths - kept.toSet()) }   // photos removed while editing
                } else repo.publish(pubDraft, paths, deps.timeZone())
                editing
            }.onSuccess { edited ->
                pubDraft = PublicationDraft(); pubStep = PublishStep.FORM; feedVersion++
                if (edited != null) { screen = Screen.MyPublications; pubsMessage = "Changes saved." }
                else { broccoliVersion++; screen = Screen.PublicationLive; deps.showAd(app.freetotake.domain.monetization.AdMoment.PUBLICATION_CREATED, false) }
            }.onFailure { e -> pubError = publishMessage(e.message) }
            publishing = false
        }
    }

    fun toggleHeart(item: Item) {
        if (session !is Session.Authenticated) {
            gate.attempt(session, PendingAction(UserAction.FAVORITES, item.id.value))
            beforeLogin = screen; loginSheet = true; return
        }
        val before = favorites
        favorites = Favorites.toggle(favorites, item.id.value)
        val repo = deps.favorites
        if (!realSession || repo == null || ExampleListing.isExample(item)) return   // device-only (example / debug user)
        scope.launch {
            runCatching { if (item.id.value in favorites) repo.add(item.id) else repo.remove(item.id) }
                .onFailure { favorites = before; message = "Couldn't update favorites. Try again." }
        }
    }

    fun removeRequest(entry: MyClaims.Entry) {
        val c = entry.claim
        if (app.freetotake.domain.request.ExampleClaim.isExample(c.id.value)) {
            exampleClaimDismissed = true; deps.prefs.exampleClaimDismissed = true
            if ((screen as? Screen.Details)?.item?.id == entry.item.id) screen = detailsOrigin
            return
        }
        val uid = userId ?: return
        if (c.id.value.startsWith("local-")) {
            // Example card request (device only): same rule as the server — leave the queue, then hide.
            val r = ClaimRules.removeFromMyClaims(uid, entry.item, c, deps.now())
            if (r is DomainResult.Ok) {
                val changed = r.value.changedClaims.associateBy { it.id }
                localClaims = localClaims.map { changed[it.id] ?: it }
            } else { message = "The pickup time has passed — this request can no longer be cancelled."; return }
        } else {
            val repo = deps.requests ?: return
            scope.launch {
                runCatching { repo.remove(c.id) }
                    .onSuccess { remoteClaims = remoteClaims.filterNot { it.id == c.id } }
                    .onFailure { e -> message = serverMessage(e.message) }
            }
        }
        if ((screen as? Screen.Details)?.item?.id == entry.item.id) screen = detailsOrigin
    }

    PlatformBackHandler(enabled = screen != Screen.Home) {
        screen = when (screen) {
            Screen.PlaceSearch -> Screen.LocationPicker
            is Screen.Details -> detailsOrigin
            Screen.RequestSent -> Screen.Home
            Screen.MyClaimsList -> Screen.Home
            Screen.CreatePublication -> if (pubStep == PublishStep.PHOTOS) { pubStep = PublishStep.FORM; Screen.CreatePublication }
                else if (pubDraft.editingItemId != null) { pubDraft = PublicationDraft(); Screen.MyPublications } else Screen.Home
            is Screen.PublisherDetails -> if (ExamplePublication.isExample((screen as Screen.PublisherDetails).itemId)) publisherOrigin else Screen.MyPublications
            is Screen.ReviewIntro -> Screen.PublisherDetails((screen as Screen.ReviewIntro).itemId)
            is Screen.Review -> Screen.PublisherDetails((screen as Screen.Review).itemId)
            is Screen.RecipientApproved -> Screen.PublisherDetails((screen as Screen.RecipientApproved).itemId)
            is Screen.Chat -> (screen as Screen.Chat).back
            Screen.MyPublications -> Screen.Home
            Screen.Profile -> Screen.Home
            Screen.EditProfile -> Screen.Profile
            Screen.ChangePassword -> Screen.EditProfile
            Screen.Favourites -> Screen.Profile
            Screen.PublicationLive -> Screen.MyPublications
            Screen.LocationPicker -> if (pickerForPublication) { pickerForPublication = false; Screen.CreatePublication } else Screen.Home
            Screen.PickupConfirmed -> Screen.Home
            Screen.SignIn -> if (authStep != AuthStep.Login && authStep !is AuthStep.Redirecting) {
                authStep = AuthFlow.backFrom(authStep) ?: AuthStep.Login; Screen.SignIn
            } else { gate.onAuthCancelled(); Screen.Home }
            else -> Screen.Home
        }
    }

    when (val s = screen) {
        Screen.Home -> { LaunchedEffect(Unit) { broccoliVersion++ }; HomeScreen(
            broccoli = broccoli,
            onBroccoli = { showBroccoliRules = true },
            location = homeLocation,
            message = message,
            onEditLocation = {
                pickerForPublication = false
                candidate = chosen; candidateSource = chosen?.let { PickupSource.DIRECTORY }
                moveTo = null; query = ""; screen = Screen.LocationPicker
            },
            onStartGiveaway = ::startPublication,
            // PLACEHOLDER (AuthIntegrationPoint): no signed-in user yet → both sections show empty states.
            myGiveaways = MyGiveaways.of(userId, myItems),
            carousel = HomeCarousel.build(feed),
            myClaims = myEntries,
            onOpenPickup = { openListing(it.item, fromClaims = true) },
            favorites = favorites,
            claimFor = ::claimFor,
            now = deps.now(),
            onHeart = ::toggleHeart,
            onRemove = { removeTarget = it },
            onOpenExampleGiveaway = { publisherOrigin = Screen.Home; screen = Screen.PublisherDetails(ExamplePublication.ID) },
            showExampleGiveaway = !exampleGiveawayDismissed,
            onDeleteGiveaway = { g ->
                val p = if (g == null) ExamplePublication.overview() else publications.firstOrNull { it.item.id == g.id }
                when {
                    p == null -> message = "Open My publications to manage this giveaway."
                    MyPublicationsRules.canCancel(p, deps.now()) -> deleteTarget = p
                    else -> message = "Less than 2 hours before pickup — it can't be cancelled now."
                }
            },
            onOpenAvailable = { screen = Screen.Catalog },
            onOpenListing = { openListing(it) },
            onOpenMyClaims = { attempt(PendingAction(UserAction.VIEW_MY_CLAIMS)) },
            onTab = { tab ->
                when (tab) {
                    HomeTab.HOME -> Unit
                    HomeTab.MY_PUBLICATIONS -> screen = Screen.MyPublications   // guests see the empty state + Create (0.12)
                    HomeTab.PROFILE -> openProfile()
                }
            },
        ) }
        Screen.LocationPicker -> LocationPickerScreen(
            initialCenter = candidate?.point ?: chosen?.point ?: devicePoint ?: LocationDefaults.FALLBACK_CENTER,
            moveTo = moveTo,
            candidate = candidate,
            resolving = resolving,
            canLocateMe = devicePoint != null,
            canContinue = PickupSelectionValidator.canContinue(PickupSelectionValidator.validate(candidate, candidateSource, resolving)),
            onCameraIdle = { center ->
                if (skipNextIdleLookup) {
                    skipNextIdleLookup = false
                } else {
                    resolving = true
                    scope.launch {
                        val place = runCatching { deps.reverseGeocoder?.placeAt(center) }.getOrNull()
                        candidate = PickupPoint(center, place?.name, place?.subtitle, place?.area ?: AreaLabel(null, null))
                        candidateSource = PickupSource.MAP_PIN
                        resolving = false
                    }
                }
            },
            onOpenSearch = { screen = Screen.PlaceSearch },
            onLocateMe = { devicePoint?.let { moveTo = it } },
            onContinue = {
                if (pickerForPublication) {
                    // Meetup location of this publication only; the Home default stays unchanged.
                    candidate?.let { pubDraft = pubDraft.copy(pickup = it) }
                    pickerForPublication = false; screen = Screen.CreatePublication
                } else {
                    // PLACEHOLDER (AuthIntegrationPoint.PICKUP_POINT_SYNC): saved on device until sign-in exists.
                    candidate?.let { deps.prefs.chosenPickup = it; chosen = it }
                    screen = Screen.PickupConfirmed
                }
            },
            onBack = { screen = if (pickerForPublication) { pickerForPublication = false; Screen.CreatePublication } else Screen.Home },
        )
        Screen.PlaceSearch -> PlaceSearchScreen(
            query = query, results = results, loading = searching, error = searchError,
            onQueryChange = { query = it },
            onPick = { r ->
                candidate = PickupPoint(r.point, r.name, r.subtitle, r.area)
                candidateSource = PickupSource.DIRECTORY
                skipNextIdleLookup = true // keep the searched name instead of reverse-geocoding the centre
                moveTo = r.point
                screen = Screen.LocationPicker
            },
            onClose = { screen = Screen.LocationPicker },
        )
        is Screen.Details -> {
            val item = s.item
            // incl. rejected (read-only, "Rejected for you"); the seeded demo request only when opened from My claims.
            val mine = app.freetotake.domain.request.ExampleClaim.shownOnDetails(claimFor(item), detailsFromClaims)
            val exampleItem = ExampleListing.isExample(item)
            // v1.16.5: My claims view of the Free food example mimics an owner (giraffe) with a meetup point and a chat.
            val ownerDemo = exampleItem && detailsFromClaims && mine != null
            val meetup = when {
                ownerDemo -> app.freetotake.domain.chat.ExampleOwner.MEETUP
                exampleItem -> ExampleRequest.MEETUP_AREA
                else -> item.area?.format()?.let { "$it · exact point shared after approval" } ?: "Shared after approval"
            }
            GiveawayDetailsScreen(
                item = item,
                fromMyClaims = detailsFromClaims,
                favorite = item.id.value in favorites,
                onHeart = { toggleHeart(item) },
                owner = if (ownerDemo) app.freetotake.ui.request.OwnerRow(
                    app.freetotake.domain.chat.ExampleOwner.NICKNAME, app.freetotake.domain.chat.ExampleOwner.AVATAR,
                ) { openChat(item.id.value, s) } else null,
                meetupText = meetup,
                slots = slots,
                now = deps.localNow(),
                draft = draft,
                submitted = mine?.let {
                    SubmittedRequest(it.requested, it.message.orEmpty(), CardStates.myClaimCard(item, it, deps.now()), submittedLabel(it))
                },
                sending = sending,
                error = requestError,
                onDraft = { draft = it; requestError = null },
                onBack = { screen = detailsOrigin },
                onShare = { deps.share(ShareLink.forItem(item)) },
                onMap = { deps.openMap(item.approxPoint, meetup) },
                onSend = { sendRequest(item) },
                onRemove = { mine?.let { removeTarget = MyClaims.Entry(it, item) } },
                // v1.14: approved (or finished) collector can open the chat with the publisher.
                onChat = mine?.takeIf { (it.status == ClaimStatus.APPROVED || it.status == ClaimStatus.COMPLETED) && !it.id.value.startsWith("local-") && !ExampleListing.isExample(item) }
                    ?.let { { openChat(item.id.value, s) } },
            )
        }
        Screen.RequestSent -> RequestSentScreen(onGoHome = { screen = Screen.Home })
        is Screen.Chat -> {
            val example = ExampleChat.isExample(s.itemId)
            val ownerDemo = s.itemId == ExampleListing.ID   // requester side: chat with the giraffe owner
            val info = when {
                ownerDemo -> ChatInfo(s.itemId, ExampleListing.item.title, "@${app.freetotake.domain.chat.ExampleOwner.NICKNAME}", null, null, open = true)
                example -> ChatInfo(s.itemId, ExamplePublication.TITLE, exampleCollector?.let { "@${it.nickname}" } ?: "@SnappyCroc", null, null, open = true)
                else -> chatInfo
            }
            ChatScreen(
                title = info?.itemTitle.orEmpty(),
                otherName = info?.otherName,
                timeline = ChatRules.timeline(if (example) exampleChats[s.itemId].orEmpty() else chatMessages, deps.localNow().date),
                canSend = ChatRules.canSend(info, deps.now()),
                loading = chatLoading,
                error = chatError,
                otherAvatar = { ChatAvatar(info?.otherAvatarUrl, when { ownerDemo -> app.freetotake.domain.chat.ExampleOwner.AVATAR; example -> exampleCollector?.exampleAvatar ?: 0; else -> null }) },
                myAvatar = { myProfile?.let { ProfileAvatar(it, 24.dp) } ?: ChatAvatar(null) },
                onSend = { sendChat(s.itemId, it) },
                onBack = { screen = s.back },
            )
        }
        Screen.Profile -> {
            val p = myProfile
            if (p == null && session !is Session.Authenticated) GuestProfileScreen(
                onRegister = { authStep = AuthStep.SignUpEmail; beforeLogin = Screen.Profile; screen = Screen.SignIn },
                onLogIn = { authStep = AuthStep.Login; beforeLogin = Screen.Profile; screen = Screen.SignIn },
                onNavTab = { t -> when (t) { HomeTab.HOME -> screen = Screen.Home; HomeTab.MY_PUBLICATIONS -> screen = Screen.MyPublications; HomeTab.PROFILE -> Unit } },
            )
            else if (p != null) ProfileScreen(
                profile = p,
                message = profileMessage,
                onEdit = ::startEditProfile,
                onFavourites = ::openFavourites,
                onNotifications = { notifSheet = true },
                onLogOut = { debugSession = null; myProfile = null; scope.launch { deps.signOut() }; screen = Screen.Home },
                onNavTab = { t -> when (t) { HomeTab.HOME -> screen = Screen.Home; HomeTab.MY_PUBLICATIONS -> screen = Screen.MyPublications; HomeTab.PROFILE -> Unit } },
            )
        }
        Screen.EditProfile -> myProfile?.let { p ->
            EditProfileScreen(
                profile = p, name = editName, email = editEmail, saving = profileSaving, error = profileError,
                onName = { editName = it.take(ProfileRules.PUBLIC_NAME_MAX); profileError = null },
                onEmail = { editEmail = it.trim(); profileError = null },
                onAvatar = ::changeAvatar,
                onChangePassword = { pwOld = ""; pwNew = ""; pwConfirm = ""; pwErrors = null; pwServerError = null; screen = Screen.ChangePassword },
                onDeleteAccount = { profileError = "Account deletion isn't specified yet — it will come in a later step." },
                onSave = ::saveProfile,
                onBack = { screen = Screen.Profile },
            )
        }
        Screen.ChangePassword -> ChangePasswordScreen(
            old = pwOld, new = pwNew, confirm = pwConfirm, errors = pwErrors, serverError = pwServerError, saving = profileSaving,
            onOld = { pwOld = it; pwServerError = null }, onNew = { pwNew = it }, onConfirm = { pwConfirm = it },
            onForgot = ::forgotPassword,
            onSave = ::savePassword,
            onBack = { screen = Screen.EditProfile },
        )
        Screen.Favourites -> FavouritesScreen(
            items = favouriteItems,
            onBack = { screen = Screen.Profile },
            onOpen = { openListing(it) },
            onUnfavourite = { item ->
                toggleHeart(item)
                favouriteItems = favouriteItems?.filterNot { it.id == item.id }
                okAlert = ProfileCopy.REMOVED_FROM_FAVOURITES
            },
        )
        Screen.CreatePublication -> CreatePublicationScreen(
            step = pubStep,
            draft = pubDraft,
            now = deps.localNow(),
            publishing = publishing,
            error = pubError,
            onDraft = { pubDraft = it; pubError = null },
            onBack = {
                if (pubStep == PublishStep.PHOTOS) pubStep = PublishStep.FORM
                else if (pubDraft.editingItemId != null) { pubDraft = PublicationDraft(); screen = Screen.MyPublications }
                else screen = Screen.Home
            },
            onNext = {
                when (app.freetotake.domain.publish.PublishFlow.onNext(session is Session.Authenticated)) {
                    app.freetotake.domain.publish.PublishFlow.NextAction.GO_TO_PHOTOS -> pubStep = PublishStep.PHOTOS
                    app.freetotake.domain.publish.PublishFlow.NextAction.ASK_LOGIN -> {
                        gate.attempt(session, PendingAction(UserAction.POST_ITEM, "draft"))
                        loginTitle = PublishCopy.LOGIN_TITLE; beforeLogin = Screen.CreatePublication; loginSheet = true
                    }
                }
            },
            onOpenDate = { dateSheet = DateSheet.open(pubDraft.date, deps.localNow().date) },
            onOpenTime = { timeSheet = true },
            onOpenLocation = {
                candidate = pubDraft.pickup ?: chosen; candidateSource = candidate?.let { PickupSource.DIRECTORY }
                moveTo = null; query = ""; pickerForPublication = true; screen = Screen.LocationPicker
            },
            onAddPhoto = ::addPhotoTapped,
            onRemovePhoto = { uri -> pubDraft = pubDraft.copy(photos = pubDraft.photos - uri) },
            onPublish = ::publishNow,
            onRemoveStoredPhoto = { sp -> pubDraft = pubDraft.copy(storedPhotos = pubDraft.storedPhotos - sp) },
        )
        Screen.PublicationLive -> SuccessScreen(PublishCopy.READY_TITLE, PublishCopy.READY_BODY, PublishCopy.CONTINUE) { screen = Screen.MyPublications }
        Screen.MyPublications -> MyPublicationsScreen(
            // v1.12.1: no own publications yet → the example "Clothes" with its requests teaches approve/reject.
            publications = ExamplePublication.withExample(publications, exampleSession.total, exampleGiveawayDismissed),
            tab = pubsTab,
            now = deps.now(),
            today = deps.localNow().date,
            message = pubsMessage,
            onTab = { pubsTab = it },
            onOpen = ::openPublication,
            onCancel = { deleteTarget = it },
            onChat = { openChat(it.item.id.value, Screen.MyPublications) },
            // Guest: "Log in to create a publication"; Next time keeps the user here (spec 0.12).
            onCreate = ::startPublication,
            onNavTab = { t ->
                when (t) {
                    HomeTab.HOME -> screen = Screen.Home
                    HomeTab.MY_PUBLICATIONS -> Unit
                    HomeTab.PROFILE -> openProfile()
                }
            },
        )
        is Screen.PublisherDetails -> {
            val example = ExamplePublication.isExample(s.itemId)
            val p = publications.firstOrNull { it.item.id.value == s.itemId }
            val today = deps.localNow().date
            if (!example && p == null) LaunchedEffect(s.itemId) { screen = Screen.MyPublications }
            else PublisherDetailsScreen(
                title = if (example) ExamplePublication.TITLE else p?.item?.title.orEmpty(),
                photo = { m ->
                    val url = p?.item?.photoUrls?.firstOrNull()
                    when {
                        example -> ExamplePhoto(Res.drawable.example_clothes, m)
                        url != null -> app.freetotake.ui.platform.RemotePhoto(url, m) { androidx.compose.foundation.layout.Box(m) }
                        else -> androidx.compose.foundation.Image(painterResource(Res.drawable.empty_mascot), null, m.padding(48.dp))
                    }
                },
                scheduleDate = if (example) "today" else p?.schedule?.let { MyPublicationsRules.dateLabel(it.date, today) },
                scheduleTime = if (example) "6:00PM-7:00PM" else p?.schedule?.let { MyPublicationsRules.timeRangeLabel(it) },
                meetup = if (example) ExamplePublication.MEETUP else (p?.item?.area?.format() ?: "—"),
                description = if (example) ExamplePublication.DESCRIPTION else p?.item?.description.orEmpty(),
                pendingRequests = if (example) exampleSession.total else (p?.pendingRequests ?: 0),
                collector = if (example) exampleCollector else p?.collector?.let { Candidate("", it.nickname, "") },
                canCancel = !example && p != null && MyPublicationsRules.canCancel(p, deps.now()),
                canEdit = !example && p != null && MyPublicationsRules.canEdit(p, deps.now()),
                onBack = { screen = if (example) publisherOrigin else Screen.MyPublications },
                onCancel = { if (p != null) deleteTarget = p },
                onReview = { openReview(s.itemId) },
                onChat = { openChat(s.itemId, Screen.PublisherDetails(s.itemId)) },
                onEdit = { if (p != null) editPublication(p) },
                message = pubsMessage,
            )
        }
        is Screen.ReviewIntro -> ReviewIntroScreen(
            onContinue = { deps.prefs.reviewIntroSeen = true; screen = Screen.Review(s.itemId) },
            onBack = { screen = Screen.PublisherDetails(s.itemId) },
        )
        is Screen.Review -> CandidateReviewScreen(
            session = reviewSession,
            busy = reviewBusy,
            onSession = { reviewSession = it; if (ExamplePublication.isExample(s.itemId)) exampleSession = it },
            onApprove = { approveCandidate(s.itemId, it) },
            onReject = { rejectCandidate(s.itemId, it) },
            onBack = { screen = Screen.PublisherDetails(s.itemId) },
        )
        is Screen.RecipientApproved -> SuccessScreen(ReviewCopy.DONE_TITLE, ReviewCopy.DONE_BODY, ReviewCopy.CONTINUE) { screen = Screen.PublisherDetails(s.itemId) }
        Screen.MyClaimsList -> MyClaimsScreen(
            entries = myEntries,
            favorites = favorites,
            now = deps.now(),
            onBack = { screen = Screen.Home },
            onOpen = { openListing(it.item, fromClaims = true) },
            onHeart = { toggleHeart(it.item) },
            onRemove = { removeTarget = it },
        )
        Screen.PickupConfirmed -> PickupConfirmationScreen(onDone = { screen = Screen.Home })
        Screen.Catalog -> CatalogScreen(
            items = feed,
            reference = chosen?.point ?: devicePoint,
            location = homeLocation,
            onEditLocation = { candidate = chosen; candidateSource = chosen?.let { PickupSource.DIRECTORY }; moveTo = null; query = ""; screen = Screen.LocationPicker },
            onBack = { screen = Screen.Home },
            onOpen = { openListing(it) },
            favorites = favorites,
            claimFor = ::claimFor,
            now = deps.now(),
            onHeart = ::toggleHeart,
            onRemove = { removeTarget = it },
            onTab = { screen = when (it) { HomeTab.HOME -> Screen.Home; HomeTab.MY_PUBLICATIONS -> Screen.MyPublications; HomeTab.PROFILE -> Screen.Profile } },
        )
        Screen.SignIn -> AuthHost(
            step = authStep,
            onStep = { authStep = it },
            auth = deps.accountAuth,
            openEmailApp = deps.openEmailApp,
            initialEmail = deps.prefs.lastEmail,
            onEmailUsed = { deps.prefs.lastEmail = it },
            onLinkSession = { active -> linkSession = active; deps.prefs.passwordSetupPending = active },
            onLeave = { gate.onAuthCancelled(); authStep = AuthStep.Login; screen = Screen.Home },
            isDebug = deps.isDebug,
            googleSignIn = deps.googleSignIn,
            onDebugSignIn = {
                scope.launch {
                    // Real (anonymous) Supabase session when enabled; otherwise a local-only test user.
                    if (!deps.debugSignIn()) debugSession = Session.Authenticated(User(UserId("debug-user"), "TestOtter0001"))
                }
            },
        )
        is Screen.ComingSoon -> ComingSoon(
            title = s.title, session = session,
            onBack = { screen = Screen.Home },
            onSignOut = { debugSession = null; message = null; scope.launch { deps.signOut() }; screen = Screen.Home },
        )
    }

    dateSheet?.let { ds ->
        DatePickerSheet(ds, deps.localNow().date,
            onApply = { d -> pubDraft = pubDraft.copy(date = d); dateSheet = null },
            onClose = { dateSheet = null })
    }
    if (timeSheet) TimePickerSheet(pubDraft.from, pubDraft.to,
        onApply = { a, b -> pubDraft = pubDraft.copy(from = a, to = b); timeSheet = false },
        onClose = { timeSheet = false })
    if (photoSheet) PhotoSourceSheet(
        onPick = { photoSheet = false; deps.pickPhoto(::onPhotoPicked) },
        onCamera = { photoSheet = false; deps.takePhoto(::onPhotoPicked) },
        onClose = { photoSheet = false },
    )
    if (maxPhotosAlert) MaxPhotosAlert(onOk = { maxPhotosAlert = false })
    if (notifSheet) myProfile?.let { p -> NotificationsSheet(p.prefs, onChange = ::setPrefs, onClose = { notifSheet = false }) }
    okAlert?.let { t ->
        OkAlert(t, onOk = { okAlert = null; if (t == ProfileCopy.PASSWORD_CHANGED) screen = Screen.EditProfile })
    }
    deleteTarget?.let { p ->
        DeletePublicationDialog(onYes = { deleteTarget = null; deletePublication(p) }, onNo = { deleteTarget = null })
    }
    if (deleting) BlockingLoader()
    if (deletedSheet) PublicationDeletedSheet(
        onGoHome = { deletedSheet = false; screen = Screen.Home },
        onClose = { deletedSheet = false },
    )

    removeTarget?.let { e ->
        CancelRequestSheet(
            onYes = { removeTarget = null; removeRequest(e) },
            onClose = { removeTarget = null },
        )
    }

    if (showBroccoliRules) app.freetotake.ui.rewards.BroccoliRulesDialog(broccoli) { showBroccoliRules = false }
    // v1.17: after a giveaway's pickup time, the giver confirms the handover (+1 broccoli when collected).
    if (screen == Screen.Home) pendingHandovers.firstOrNull()?.let { h ->
        app.freetotake.ui.rewards.HandoverConfirmDialog(h, handoverBusy) { collected ->
            val repo = deps.broccoli ?: return@HandoverConfirmDialog
            handoverBusy = true
            scope.launch {
                runCatching { repo.confirmHandover(h.itemId, collected) }
                    .onSuccess { b -> broccoli = b; pendingHandovers = pendingHandovers.drop(1) }
                    .onFailure { pendingHandovers = pendingHandovers.drop(1) }
                handoverBusy = false
            }
        }
    }

    if (loginSheet) LoginSheet(
        onContinue = { loginSheet = false; loginTitle = RequestRules.LOGIN_SHEET_TITLE; if (screen != Screen.SignIn) beforeLogin = screen; screen = Screen.SignIn },
        onNextTime = { loginSheet = false; loginTitle = RequestRules.LOGIN_SHEET_TITLE; gate.onAuthCancelled() },
        title = loginTitle,
    )

    prompt?.let { p ->
        AuthRequiredPrompt(
            action = p.action,
            onSignIn = { prompt = null; screen = Screen.SignIn },
            onDismiss = { prompt = null; gate.onAuthCancelled() },
        )
    }
}

/** Placeholder for screens delivered in later steps. */
@Composable
private fun ComingSoon(title: String, session: Session, onBack: () -> Unit, onSignOut: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(FttColors.BackgroundPrimary).statusBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        androidx.compose.foundation.layout.Box(Modifier.clickable(onClick = onBack)) { BackChevron() }
        Text(title, style = FttType.largeTitleBold())
        Text("Coming in the next step.", style = FttType.body(), color = FttColors.LabelSecondary)
        if (session is Session.Authenticated) {
            Text("Signed in as ${session.user.displayName}", style = FttType.subheadline())
            Text("Sign out", style = FttType.bodyBold(), modifier = Modifier.clickable(onClick = onSignOut))
        }
    }
}

private fun submittedLabel(c: Claim): String? {
    if (c.createdAt.epochMillis <= 0) return null
    val days = c.createdAt.epochMillis / 86_400_000L
    return MyClaimsCopy.submittedLabel(DateKey(1970, 1, 1).plusDays(days.toInt()))
}

/** Publish errors (RPC / upload) to user copy. */
private fun publishMessage(raw: String?): String = when {
    raw == null -> "Couldn't publish. Try again."
    "TIME_IN_PAST" in raw -> "The meetup time is in the past. Choose a later time."
    "TIME_RANGE_INVALID" in raw -> "Choose a meetup time range where “To” is later than “From”."
    "TITLE_INVALID" in raw -> "Add a title (up to ${PublicationRules.TITLE_MAX} characters)."
    "TOO_MANY_PHOTOS" in raw -> "You can attach not more than 3 photos."
    "PHOTO_REQUIRED" in raw -> "Add at least one photo."
    "NOT_AUTHENTICATED" in raw || "JWT" in raw -> "Your session expired. Sign in again."
    else -> "Couldn't publish. Check your connection and try again."
}

/** Maps server rule errors (RPC exceptions) to user copy. */
private fun serverMessage(raw: String?): String = when {
    raw == null -> "Couldn't send the request. Try again."
    "SLOT_" in raw -> "This pickup time is no longer available. Choose another one."
    "ALREADY_HAS_ACTIVE_CLAIM" in raw -> "You already requested this giveaway."
    "CANNOT_CLAIM_OWN_ITEM" in raw -> "This is your own giveaway."
    "NOTE_TOO_LONG" in raw -> "Keep your note under ${RequestRules.NOTE_MAX} characters."
    "ITEM_NOT_CLAIMABLE" in raw -> "This giveaway is no longer available."
    "ALREADY_REJECTED" in raw -> "Your request for this giveaway was rejected — you can't request it again."
    "NO_BROCCOLI" in raw -> app.freetotake.domain.rewards.BroccoliCopy.NOT_ENOUGH
    "PICKUP_PASSED" in raw -> "The pickup time has passed — this request can no longer be cancelled."
    "NOT_AUTHENTICATED" in raw || "JWT" in raw -> "Sign-in isn't connected yet — requests on real giveaways need a real account."
    else -> "Couldn't send the request. Try again."
}
