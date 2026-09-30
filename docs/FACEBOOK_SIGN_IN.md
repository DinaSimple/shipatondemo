# Facebook sign-in — one-time setup (v1.15.1, free)

## 1. Meta for Developers (developers.facebook.com)
1. **My Apps → Create app** → use case **"Authenticate and request data from users with Facebook Login"** → app name "Free to Take".
2. Use case → **Customize → Permissions**: add **email** (public_profile is on by default). No App Review needed for these two.
3. **Facebook Login → Settings → Valid OAuth Redirect URIs**:
   `https://YOUR_PROJECT_REF.supabase.co/auth/v1/callback`
4. **App settings → Basic**: copy **App ID** and **App secret**; fill **Privacy Policy URL**, **User data deletion** (instructions URL), category, icon.
5. Switch the app from **Development** to **Live** (top bar). Until then only you and added testers can log in.

## 2. Supabase
- Authentication → Sign In / Providers → **Facebook** → Enable → paste App ID (Client ID) and App secret.
- Authentication → URL Configuration → **Redirect URLs** → add `freetotake://login-callback`.
- Keep "Allow new users to sign up" ON.

## 3. App
Nothing to configure. Log in / Sign up → **Continue with Facebook** → browser → back to the app, signed in.
Note: users who registered Facebook with a phone number have no email — that's fine for the app.
