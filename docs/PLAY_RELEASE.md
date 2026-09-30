# Publishing Free to Take on Google Play (production)

## 0. One-time prerequisites
- Google Play developer account (play.google.com/console, one-time $25; identity verification can take a few days).
- A **public Privacy Policy URL** (required: the app collects email, approximate location and photos). E.g. a page on GitHub Pages / Notion / Google Sites.
- Email sending configured (docs/AUTH_SETUP.md), otherwise nobody can register.
- New personal developer accounts must run a **closed test with ≥12 testers for 14 days** before production access is granted.

## 1. Create the upload key (once, keep it safe — back it up)
```
mkdir -p ~/keys
keytool -genkeypair -v -keystore ~/keys/freetotake-upload.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
```
Create `~/projects/free-to-take/keystore.properties` (git-ignored):
```
storeFile=/Users/you/keys/freetotake-upload.jks
storePassword=YOUR_STORE_PASSWORD
keyAlias=upload
keyPassword=YOUR_KEY_PASSWORD
```

## 2. Set the version (every upload needs a higher versionCode)
In `local.properties`:
```
app.versionCode=114
app.versionName=1.14
```

## 3. Build the App Bundle
```
cd ~/projects/free-to-take
./gradlew clean :composeApp:bundleRelease
```
Result: `composeApp/build/outputs/bundle/release/composeApp-release.aab`
(Android Studio alternative: Build → Generate Signed App Bundle / APK → Android App Bundle.)

## 4. Play Console
1. **Create app** → name "Free to Take", default language, App, Free.
2. **Set up your app** (Dashboard checklist): App access (explain login: give a test email/password), Ads = No,
   Content rating questionnaire, Target audience (18+ recommended — users meet strangers), News app = No,
   **Data safety** (collected: email, name/nickname, photos, approximate location, messages; encrypted in transit; users can request deletion),
   Privacy policy URL, App category = Lifestyle.
3. **Main store listing**: short + full description, 512×512 icon, 1024×500 feature graphic, ≥2 phone screenshots.
4. **Play App Signing**: accept (Google keeps the app signing key; your upload key signs uploads).
5. **Testing → Closed testing** → create track → upload the .aab → add testers (email list) → roll out. Keep ≥12 testers for 14 days.
6. **Production → Create new release** → upload the .aab (or promote from closed testing) → release notes → **Review release → Start rollout**.
   First review usually takes a few days.

## 5. After Play App Signing (for email links opening the app directly — optional)
Play Console → Setup → App signing → copy the **SHA-256 of the app signing key** into `web/.well-known/assetlinks.json` if you use Verified App Links.

## 6. Before release checklist
- Supabase: keep "Allow new users to sign up" ON (needed by Google sign-in) and "Confirm email" ON.
- Google Cloud: add an Android OAuth client with the SHA-1 of the Play app signing key (docs/GOOGLE_SIGN_IN.md).
- Debug "Continue as test user" is hidden automatically in release builds.
- Every next upload: raise `app.versionCode`, rebuild, upload in Production → Create new release.
