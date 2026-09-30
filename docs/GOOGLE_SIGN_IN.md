# Google sign-in — one-time setup (v1.15, free)

## 1. Google Cloud (console.cloud.google.com)
1. Create a project "Free to Take".
2. **APIs & Services → OAuth consent screen**: External, app name "Free to Take", support email, add scopes `email`, `profile`, `openid`. Publish the app (In production) — basic scopes need no Google review.
3. **Credentials → Create credentials → OAuth client ID → Web application** (name "Supabase"). No redirect URI needed for the native flow.
   Copy the **Client ID** and **Client secret**.
4. **Credentials → Create credentials → OAuth client ID → Android**:
   - Package name: `freetotake.app`
   - SHA-1 of your debug key: `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android | grep SHA1`
   - After Play upload, create one more Android client with the **SHA-1 of the Play app signing key** (Play Console → Setup → App signing).

## 2. Supabase
Authentication → Sign In / Providers → **Google** → Enable:
- Client IDs: the **Web** client ID (comma-separate if you add more)
- Client Secret: the Web client secret
- Keep **Allow new users to sign up = ON** (Google creates the account on first sign-in).

## 3. App
`local.properties`: `google.webClientId=<the Web client ID>.apps.googleusercontent.com` → rebuild.
Log in / Sign up now show **Continue with Google**.
