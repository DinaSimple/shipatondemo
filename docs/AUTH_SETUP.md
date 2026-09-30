# Login & auth — email setup (v1.13)

Everything else is live: captcha (our own 3×3 picture check), 5-minute single-use links, the redirect into the app.
The email link points to the `auth-email` function itself, which on Android jumps straight into the app —
**no website / GitHub Pages is needed any more.** Only an email sender is missing.

## Option A — Brevo (recommended: free 300 emails/day, no own domain needed) ~5 min
1. Sign up at brevo.com (free plan).
2. Senders, Domains & Dedicated IPs → Senders → **Add a sender** → your Gmail address → confirm the email Brevo sends you.
3. SMTP & API → API Keys → **Generate a new API key**.
4. Supabase → SQL Editor, run (put your values):
   ```sql
   select vault.create_secret('xkeysib-…', 'brevo_api_key');
   select vault.create_secret('Free to Take <your.address@gmail.com>', 'mail_from');
   ```
5. Done — sign up in the app again.

## Option B — Resend (best once you own a domain)
Without a verified domain Resend only delivers to your own address (fine for testing).
```sql
select vault.create_secret('re_…', 'resend_api_key');
select vault.create_secret('Free to Take <onboarding@resend.dev>', 'mail_from');
```

## Option C — n8n / Make / Zapier webhook (send from your own Gmail)
1. Workflow: **Webhook** (POST) → **Gmail: Send message** — To `{{$json.body.to}}`, Subject `{{$json.body.subject}}`,
   Message `{{$json.body.html}}` (HTML). Optionally check header `x-ftt-secret`.
2. ```sql
   select vault.create_secret('https://<your-n8n>/webhook/…', 'email_webhook_url');
   select vault.create_secret('<any random string>', 'email_webhook_secret');   -- optional
   ```
Payload the function posts: `{ to, subject, html, link, purpose }`.

To change a value later: `select vault.update_secret((select id from vault.decrypted_secrets where name = 'mail_from'), 'new value');`

## Optional
- Verified App Links (open the app without the browser hop): GitHub Pages host with `web/.well-known/assetlinks.json`,
  then `select vault.create_secret('https://<host>', 'link_base_url');` and `auth.linkHost=<host>` in local.properties.
- Keep **Allow new users to sign up: ON** — Google sign-in (v1.15) creates accounts through it. Keep "Confirm email" ON so direct API sign-ups without our email link stay unconfirmed.
