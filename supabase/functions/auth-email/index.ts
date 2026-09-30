// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
// Copyright (C) 2026 Dina Yol (DinaSimple) — Free to Take, https://freetotake.app
// v1.12/v1.13 — captcha-gated email links for sign-up and password recovery (spec "Login and auth").
// POST JSON (always HTTP 200 with {ok, ...} or {ok:false, error}):
//   captcha_new                                  → { challengeId, label, tiles[9] }   (images are bundled in the app)
//   captcha_verify {challengeId, selected[]}     → { captchaToken }  valid 15 min, consumed only when an email is sent
//   signup | recover {email, captchaToken}       → account checked, 5-min single-use link emailed
//   check  {token}                               → non-consuming status
//   redeem {token}                               → consumes the link; returns a one-time Supabase token_hash (verifyOtp magiclink)
// GET ?t=&p=  (the link in the email)            → Android: 302 into the app (freetotake://), others: short text.
// Email providers (first configured wins, Vault or env): brevo_api_key | resend_api_key | email_webhook_url (n8n/Make/Zapier).
import { createClient } from "npm:@supabase/supabase-js@2";

const LINK_TTL_SECONDS = 300;
const SELF_URL = `${Deno.env.get("SUPABASE_URL")}/functions/v1/auth-email`;
const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};
const admin = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, {
  auth: { persistSession: false, autoRefreshToken: false },
});

const json = (body: unknown) =>
  new Response(JSON.stringify(body), { headers: { ...cors, "Content-Type": "application/json" } });
const fail = (error: string, extra: Record<string, unknown> = {}) => json({ ok: false, error, ...extra });

async function config(): Promise<Record<string, string>> {
  const { data } = await admin.rpc("auth_email_config");
  const c: Record<string, string> = {};
  for (const r of (data ?? []) as { name: string; value: string }[]) c[r.name] = r.value;
  const env = (k: string, n: string) => { const v = Deno.env.get(k); if (v) c[n] = v; };
  env("RESEND_API_KEY", "resend_api_key"); env("BREVO_API_KEY", "brevo_api_key"); env("EMAIL_WEBHOOK_URL", "email_webhook_url");
  env("MAIL_FROM", "mail_from"); env("LINK_BASE_URL", "link_base_url"); env("EMAIL_WEBHOOK_SECRET", "email_webhook_secret");
  return c;
}

async function sha256(s: string): Promise<string> {
  const d = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(s));
  return [...new Uint8Array(d)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

function newToken(): string {
  const b = crypto.getRandomValues(new Uint8Array(32));
  return btoa(String.fromCharCode(...b)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

const EMAIL_RE = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;
const TOKEN_RE = /^[A-Za-z0-9_-]{20,128}$/;

async function captchaNew() {
  const { data, error } = await admin.rpc("captcha_new");
  const row = (data ?? [])[0];
  if (error || !row) { console.error(error); return fail("SERVER_ERROR"); }
  return json({ ok: true, challengeId: row.id, label: row.label, tiles: row.tiles });
}

async function captchaVerify(challengeId: string, selected: unknown) {
  if (typeof challengeId !== "string" || !Array.isArray(selected)) return fail("CAPTCHA_FAILED");
  const sel = selected.map(Number).filter((n) => Number.isInteger(n) && n >= 0 && n < 9);
  const token = newToken();
  const { data, error } = await admin.rpc("captcha_verify", { p_id: challengeId, p_selected: sel, p_pass_hash: await sha256(token) });
  if (error) { console.error(error); return fail("CAPTCHA_FAILED"); }
  return data === "ok" ? json({ ok: true, captchaToken: token }) : fail("CAPTCHA_FAILED", { reason: data });
}

const passValid = async (t: string) => !!t && TOKEN_RE.test(t) && (await admin.rpc("captcha_pass_valid", { p_hash: await sha256(t) })).data === true;

function emailHtml(purpose: string, link: string): { subject: string; html: string } {
  const signup = purpose === "signup";
  const subject = signup ? "Confirm your email — Free to Take" : "Reset your password — Free to Take";
  const title = signup ? "Confirm your email" : "Reset your password";
  const text = signup
    ? "Tap the button to confirm your email and create your password."
    : "Tap the button to create a new password for your account.";
  const html = `<div style="font-family:-apple-system,Segoe UI,Roboto,sans-serif;max-width:420px;margin:0 auto;padding:24px;color:#111">
  <h1 style="font-size:24px;margin:0 0 8px">${title}</h1>
  <p style="font-size:15px;margin:0 0 20px">${text}</p>
  <a href="${link}" style="display:block;text-align:center;background:#D4FF00;color:#111;font-weight:600;text-decoration:none;padding:12px;border-radius:8px;border:1px solid #111">${signup ? "Confirm email" : "Create a new password"}</a>
  <p style="font-size:13px;color:#777;margin:20px 0 0">Open this email on the phone where Free to Take is installed. The link expires in 5 minutes and works once. If you didn't ask for it, ignore this email.</p>
</div>`;
  return { subject, html };
}

const emailConfigured = (c: Record<string, string>) => !!(c.brevo_api_key || c.resend_api_key || c.email_webhook_url);

async function sendEmail(to: string, purpose: string, link: string, c: Record<string, string>): Promise<boolean> {
  const { subject, html } = emailHtml(purpose, link);
  const from = c.mail_from || "Free to Take <onboarding@resend.dev>";
  const m = from.match(/^(.*)<(.+)>$/);
  const fromName = (m ? m[1] : "Free to Take").trim() || "Free to Take", fromEmail = (m ? m[2] : from).trim();
  let r: Response;
  if (c.brevo_api_key) {
    r = await fetch("https://api.brevo.com/v3/smtp/email", {
      method: "POST", headers: { "api-key": c.brevo_api_key, "Content-Type": "application/json", accept: "application/json" },
      body: JSON.stringify({ sender: { name: fromName, email: fromEmail }, to: [{ email: to }], subject, htmlContent: html }),
    });
  } else if (c.resend_api_key) {
    r = await fetch("https://api.resend.com/emails", {
      method: "POST", headers: { Authorization: `Bearer ${c.resend_api_key}`, "Content-Type": "application/json" },
      body: JSON.stringify({ from, to: [to], subject, html }),
    });
  } else {
    // Generic webhook (n8n / Make / Zapier): the workflow sends {to, subject, html} with e.g. its Gmail node.
    r = await fetch(c.email_webhook_url, {
      method: "POST", headers: { "Content-Type": "application/json", ...(c.email_webhook_secret ? { "x-ftt-secret": c.email_webhook_secret } : {}) },
      body: JSON.stringify({ to, subject, html, link, purpose }),
    });
  }
  if (!r.ok) console.error("email", r.status, await r.text());
  return r.ok;
}

async function requestLink(purpose: "signup" | "recover", emailIn: string, captchaToken: string) {
  const email = String(emailIn ?? "").trim().toLowerCase();
  if (!EMAIL_RE.test(email) || email.length > 254) return fail("INVALID_EMAIL");
  const c = await config();
  if (!(await passValid(captchaToken))) return fail("CAPTCHA_REQUIRED");
  const { data: status } = await admin.rpc("auth_email_status", { p_email: email });
  if (purpose === "signup" && status === "active") return fail("EMAIL_TAKEN");
  if (purpose === "recover" && status !== "active") return fail("NOT_REGISTERED");
  if (!emailConfigured(c)) return fail("EMAIL_NOT_CONFIGURED");
  const token = newToken();
  const { data: issued, error } = await admin.rpc("auth_link_issue", {
    p_email: email, p_purpose: purpose, p_token_hash: await sha256(token), p_ttl_seconds: LINK_TTL_SECONDS,
  });
  if (error) { console.error(error); return fail("SERVER_ERROR"); }
  if (issued !== "ok") return fail(String(issued));
  // Default link = this function (GET → 302 into the app); a GitHub Pages host can be set as link_base_url.
  const link = c.link_base_url
    ? `${c.link_base_url.replace(/\/+$/, "")}/auth/confirm/?t=${token}&p=${purpose}`
    : `${SELF_URL}?t=${token}&p=${purpose}`;
  if (!(await sendEmail(email, purpose, link, c))) return fail("EMAIL_SEND_FAILED");
  await admin.rpc("captcha_pass_consume", { p_hash: await sha256(captchaToken) });   // one captcha per sent email
  return json({ ok: true });
}

async function check(token: string) {
  if (!TOKEN_RE.test(token ?? "")) return fail("LINK_INVALID");
  const { data } = await admin.rpc("auth_link_check", { p_token_hash: await sha256(token) });
  const row = (data ?? [])[0];
  return row?.status === "ok" ? json({ ok: true, purpose: row.purpose }) : fail(row?.status ?? "LINK_INVALID", { purpose: row?.purpose });
}

async function redeem(token: string) {
  if (!TOKEN_RE.test(token ?? "")) return fail("LINK_INVALID");
  const { data, error } = await admin.rpc("auth_link_redeem", { p_token_hash: await sha256(token) });
  if (error) { console.error(error); return fail("SERVER_ERROR"); }
  const row = (data ?? [])[0] as { status: string; email: string | null; purpose: string | null } | undefined;
  if (!row || row.status !== "ok") return fail(row?.status ?? "LINK_INVALID", { email: row?.email, purpose: row?.purpose });
  const email = row.email!;
  const { data: status } = await admin.rpc("auth_email_status", { p_email: email });
  if (row.purpose === "signup") {
    if (status === "active") return fail("EMAIL_TAKEN", { email, purpose: row.purpose });
    if (status === "none") {
      const { error: e } = await admin.auth.admin.createUser({ email, email_confirm: true });
      if (e) { console.error(e); return fail("SERVER_ERROR"); }
    }
  } else if (status !== "active") return fail("NOT_REGISTERED", { email, purpose: row.purpose });
  const { data: gen, error: ge } = await admin.auth.admin.generateLink({ type: "magiclink", email });
  if (ge || !gen?.properties?.hashed_token) { console.error(ge); return fail("SERVER_ERROR"); }
  return json({ ok: true, purpose: row.purpose, email, tokenHash: gen.properties.hashed_token });
}

/** The emailed link. Android → straight into the app (the app redeems the token); elsewhere → plain text. */
async function openLink(u: URL, ua: string) {
  const t = u.searchParams.get("t") ?? "", p = u.searchParams.get("p") ?? "";
  if (!TOKEN_RE.test(t) || !["signup", "recover"].includes(p)) return text("This link is not valid. Open Free to Take and request a new one.");
  const q = `t=${encodeURIComponent(t)}&p=${p}`;
  if (/Android/i.test(ua)) {
    return new Response(null, { status: 302, headers: { Location: `intent://auth/confirm?${q}#Intent;scheme=freetotake;package=freetotake.app;end` } });
  }
  const { data } = await admin.rpc("auth_link_check", { p_token_hash: await sha256(t) });
  const st = (data ?? [])[0]?.status;
  if (st === "LINK_EXPIRED") return text("This link has expired (links are valid for 5 minutes). Open Free to Take and request a new one.");
  if (st !== "ok") return text("This link was already used or is not valid. Open Free to Take to log in or request a new link.");
  return text("Almost there! Open this email on your Android phone with Free to Take installed and tap the link again to continue.");
}
const text = (s: string) => new Response(s + "\n", { headers: { "Content-Type": "text/plain; charset=utf-8" } });

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method === "GET") return openLink(new URL(req.url), req.headers.get("user-agent") ?? "");
  if (req.method !== "POST") return fail("METHOD");
  try {
    const b = await req.json();
    switch (b?.action) {
      case "captcha_new": return await captchaNew();
      case "captcha_verify": return await captchaVerify(b.challengeId, b.selected);
      case "signup": case "recover": return await requestLink(b.action, b.email, b.captchaToken);
      case "check": return await check(b.token);
      case "redeem": return await redeem(b.token);
      default: return fail("BAD_ACTION");
    }
  } catch (e) {
    console.error(e);
    return fail("SERVER_ERROR");
  }
});
