// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Dina Yol (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.android

import android.Manifest
import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import app.freetotake.data.geo.PhotonGeocoder
import app.freetotake.data.supabase.SupabaseAuthRepository
import app.freetotake.data.supabase.SupabaseFeedRepository
import app.freetotake.data.supabase.SupabaseRequestRepository
import app.freetotake.data.supabase.SupabaseFavoritesRepository
import app.freetotake.data.supabase.SupabasePublishRepository
import androidx.activity.result.PickVisualMediaRequest
import androidx.core.content.FileProvider
import java.io.File
import app.freetotake.domain.location.GeoPoint
import app.freetotake.domain.request.DateKey
import app.freetotake.domain.request.LocalNow
import app.freetotake.domain.request.TimeOfDay
import android.content.Intent
import android.net.Uri
import app.freetotake.domain.model.Timestamp
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import app.freetotake.data.supabase.SupabaseProvider
import app.freetotake.domain.location.LocationAccess
import app.freetotake.domain.model.Session
import app.freetotake.domain.prefs.AppPreferences
import app.freetotake.domain.prefs.KeyValueStore
import app.freetotake.ui.App
import app.freetotake.ui.AppDeps
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.channels.Channel
import app.freetotake.data.supabase.SupabaseAccountAuth
import io.github.jan.supabase.auth.handleDeeplinks

class FreeToTakeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Skipped until local.properties has credentials, so the app still runs offline.
        if (BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()) {
            SupabaseProvider.init(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY)
        }
        RevenueCat.init(this)   // v1.16
    }
}

private class SharedPrefsStore(context: Context) : KeyValueStore {
    private val sp = context.getSharedPreferences("free_to_take", Context.MODE_PRIVATE)
    override fun getBoolean(key: String, default: Boolean) = sp.getBoolean(key, default)
    override fun putBoolean(key: String, value: Boolean) { sp.edit().putBoolean(key, value).apply() }
    override fun getString(key: String): String? = sp.getString(key, null)
    override fun putString(key: String, value: String) { sp.edit().putString(key, value).apply() }
}

class MainActivity : ComponentActivity() {

    private var onLocationResult: ((LocationAccess) -> Unit)? = null

    // ---- push notifications permission (1.8.2): asked after onboarding, before location ----
    private var onNotificationResult: ((Boolean) -> Unit)? = null
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        onNotificationResult?.invoke(granted); onNotificationResult = null
    }

    // ---- photos for a new publication (spec 0.11) ----
    private var onPhoto: ((String?) -> Unit)? = null
    private var cameraUri: Uri? = null
    /** Android photo picker: no storage permission needed. */
    private val pickPhoto = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        onPhoto?.invoke(uri?.toString()); onPhoto = null
    }
    /** System camera writes into our cache (FileProvider); no CAMERA permission needed. */
    private val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        onPhoto?.invoke(if (ok) cameraUri?.toString() else null); onPhoto = null
    }

    private val locationPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val access = when {
            grants[Manifest.permission.ACCESS_FINE_LOCATION] == true -> LocationAccess.PRECISE
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true -> LocationAccess.APPROXIMATE
            else -> LocationAccess.DENIED
        }
        onLocationResult?.invoke(access)
        onLocationResult = null
    }

    /** Device notification for a server event (spec 0.14); silently skipped if not permitted. */
    private fun showNotification(title: String, body: String) {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
        val nm = getSystemService(android.app.NotificationManager::class.java) ?: return
        val channel = "updates"
        nm.createNotificationChannel(android.app.NotificationChannel(channel, "Free to Take updates", android.app.NotificationManager.IMPORTANCE_DEFAULT))
        val open = android.app.PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        val n = android.app.Notification.Builder(this, channel)
            .setSmallIcon(applicationInfo.icon)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(android.app.Notification.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify((System.nanoTime() % Int.MAX_VALUE).toInt(), n)
    }

    /** App Links / freetotake:// URIs (email verification and password recovery). */
    private val links = Channel<String>(Channel.BUFFERED)
    private val ads by lazy { InterstitialAds(this) }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (SupabaseProvider.isInitialized) SupabaseProvider.client.handleDeeplinks(intent)   // Facebook OAuth return
        intent.data?.let { links.trySend(it.toString()) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // v1.13: status/navigation bar icons follow light/dark mode.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        // Portrait only (also declared in the manifest).
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        val auth = if (SupabaseProvider.isInitialized) SupabaseAuthRepository(SupabaseProvider.client) else null
        if (savedInstanceState == null) {
            if (SupabaseProvider.isInitialized) intent?.let { SupabaseProvider.client.handleDeeplinks(it) }
            intent?.data?.let { links.trySend(it.toString()) }
        }
        val linkHost = BuildConfig.AUTH_LINK_HOST.trim()
        ads.start()   // consent (EU) → AdMob init → preload
        val photon = PhotonGeocoder(HttpClient(Android))
        val deps = AppDeps(
            prefs = AppPreferences(SharedPrefsStore(applicationContext)),
            deviceLocation = AndroidDeviceLocation(applicationContext),
            reverseGeocoder = AndroidReverseGeocoder(applicationContext, fallback = photon),
            placeSearch = photon,
            feed = if (SupabaseProvider.isInitialized) SupabaseFeedRepository(SupabaseProvider.client) else null,
            requests = if (SupabaseProvider.isInitialized) SupabaseRequestRepository(SupabaseProvider.client) else null,
            favorites = if (SupabaseProvider.isInitialized) SupabaseFavoritesRepository(SupabaseProvider.client) else null,
            publish = if (SupabaseProvider.isInitialized) SupabasePublishRepository(SupabaseProvider.client) else null,
            profile = if (SupabaseProvider.isInitialized) app.freetotake.data.supabase.SupabaseProfileRepository(SupabaseProvider.client) else null,
            postNotification = { title, body -> showNotification(title, body) },
            chat = if (SupabaseProvider.isInitialized) app.freetotake.data.supabase.SupabaseChatRepository(SupabaseProvider.client) else null,
            broccoli = if (SupabaseProvider.isInitialized) app.freetotake.data.supabase.SupabaseBroccoliRepository(SupabaseProvider.client) else null,
            myPublications = if (SupabaseProvider.isInitialized) app.freetotake.data.supabase.SupabaseMyPublicationsRepository(SupabaseProvider.client) else null,
            compressor = AndroidImageCompressor(applicationContext),
            preparePhoto = { uri ->
                // Compress immediately after selection and keep only the compressed copy (app cache).
                val img = AndroidImageCompressor(applicationContext).compress(uri)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val dir = File(cacheDir, "photos").apply { mkdirs() }
                    val out = File(dir, "p_${System.nanoTime()}.jpg").apply { writeBytes(img.bytes) }
                    Uri.fromFile(out).toString()
                }
            },
            readPhoto = { uri ->
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    contentResolver.openInputStream(Uri.parse(uri))!!.use { it.readBytes() }
                }
            },
            timeZone = { java.util.TimeZone.getDefault().id },
            pickPhoto = { cb ->
                onPhoto = cb
                pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            takePhoto = { cb ->
                val dir = File(cacheDir, "camera").apply { mkdirs() }
                val uri = FileProvider.getUriForFile(this, "$packageName.photos", File(dir, "shot_${System.currentTimeMillis()}.jpg"))
                cameraUri = uri; onPhoto = cb
                runCatching { takePhoto.launch(uri) }.onFailure { onPhoto = null; cb(null) }
            },
            debugSignIn = { auth?.signInAnonymously() ?: false },
            now = { Timestamp(System.currentTimeMillis()) },
            localNow = {
                val t = java.time.LocalDateTime.now()
                LocalNow(DateKey(t.year, t.monthValue, t.dayOfMonth), TimeOfDay(t.hour, t.minute))
            },
            share = { text ->
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                startActivity(Intent.createChooser(send, null))
            },
            openMap = { p: GeoPoint?, label: String ->
                val q = Uri.encode(label)
                val uri = if (p != null) "geo:${p.lat},${p.lng}?q=${p.lat},${p.lng}($q)" else "geo:0,0?q=$q"
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri))) }
            },
            requestNotificationPermission = { cb ->
                if (android.os.Build.VERSION.SDK_INT >= 33) {
                    onNotificationResult = cb
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else cb(true)   // no runtime dialog before Android 13
            },
            requestLocationPermission = { cb ->
                onLocationResult = cb
                // Approximate location only (spec 0.3).
                locationPermission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION))
            },
            session = auth?.session ?: flowOf(Session.Guest),
            signOut = { auth?.signOut() },
            isDebug = BuildConfig.DEBUG,
            accountAuth = if (SupabaseProvider.isInitialized)
                SupabaseAccountAuth(SupabaseProvider.client, BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY, HttpClient(Android)) else null,
            openEmailApp = {
                runCatching {
                    startActivity(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_EMAIL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            },
            incomingLinks = links.receiveAsFlow(),
            googleSignIn = { googleIdToken(this, BuildConfig.GOOGLE_WEB_CLIENT_ID) },
            showAd = { moment, demo -> ads.show(moment, demo) },
            identifyPurchaser = { id -> RevenueCat.identify(id) },
            authLinkHost = linkHost.ifEmpty { null },
        )
        setContent { App(deps) }
    }
}
