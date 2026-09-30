// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
// Copyright (C) 2026 Dina Yol (DinaSimple) — Free to Take, https://freetotake.app
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

// Supabase credentials live in local.properties (never committed):
//   supabase.url=https://<project>.supabase.co
//   supabase.anonKey=<anon public key>
//   auth.linkHost=<your GitHub Pages host, e.g. yourname.github.io>   (v1.12 email links / Android App Links)
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

// Play release signing (never committed): keystore.properties next to local.properties
//   storeFile=/Users/<you>/keys/freetotake-upload.jks
//   storePassword=…   keyAlias=upload   keyPassword=…
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.shared)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.runtime.compose)
        }
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.maplibre.android)
            implementation(libs.ktor.client.android)
            // v1.15 Google sign-in (Credential Manager)
            implementation(libs.androidx.credentials)
            implementation(libs.androidx.credentials.play)
            implementation(libs.googleid)
            // v1.16 monetization: RevenueCat (ad-free entitlement) + AdMob interstitials + EU consent (UMP)
            implementation(libs.revenuecat.purchases)
            implementation(libs.play.services.ads)
            implementation(libs.ump)
        }
    }
}

compose.resources {
    packageOfResClass = "app.freetotake.resources"
    publicResClass = false
    generateResClass = always
}

android {
    namespace = "app.freetotake.android"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "freetotake.app"   // v1.16.10: package registered in Play Console (Kotlin code stays in app.freetotake.*)
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        // Bump for every Play upload (versionCode must always increase).
        versionCode = (localProps.getProperty("app.versionCode") ?: "114").toInt()
        versionName = localProps.getProperty("app.versionName") ?: "1.14"
        buildConfigField("String", "SUPABASE_URL", "\"${localProps.getProperty("supabase.url", "")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${localProps.getProperty("supabase.anonKey", "")}\"")
        // Google sign-in: the OAuth *Web* client ID (Google Cloud → Credentials), also entered in Supabase → Auth → Google.
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${localProps.getProperty("google.webClientId", "").trim()}\"")
        // RevenueCat public SDK key (goog_… or the Test Store key) — never the secret key.
        buildConfigField("String", "REVENUECAT_API_KEY", "\"${localProps.getProperty("revenuecat.apiKey", "").trim()}\"")
        // AdMob: Google's official TEST ids by default; put your real ones in local.properties for release.
        val admobAppId = localProps.getProperty("admob.appId", "ca-app-pub-3940256099942544~3347511713").trim()
        manifestPlaceholders["admobAppId"] = admobAppId
        buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"${localProps.getProperty("admob.interstitialId", "ca-app-pub-3940256099942544/1033173712").trim()}\"")
        val linkHost = localProps.getProperty("auth.linkHost", "").trim()
        buildConfigField("String", "AUTH_LINK_HOST", "\"$linkHost\"")
        manifestPlaceholders["authLinkHost"] = linkHost.ifEmpty { "links.invalid" }
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
    signingConfigs {
        if (keystoreProps.getProperty("storeFile") != null) create("release") {
            storeFile = file(keystoreProps.getProperty("storeFile"))
            storePassword = keystoreProps.getProperty("storePassword")
            keyAlias = keystoreProps.getProperty("keyAlias")
            keyPassword = keystoreProps.getProperty("keyPassword")
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
