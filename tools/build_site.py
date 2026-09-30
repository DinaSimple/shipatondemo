#!/usr/bin/env python3
# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 Dina Yol (DinaSimple) — Free to Take, https://freetotake.app
"""Builds the public developer site (Cloudflare Pages) into web/site/.

Pages: home, Terms & Conditions (web/brand/TERMSANDCONDITIONS.docx), Privacy Policy.
Branding = the app: lime #C8FF00, black ink, #F2F2F7 background, Free to Take logo.
Run:  python3 tools/build_site.py --email you@example.com
"""
import argparse, html, pathlib, re, shutil

ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT = ROOT / "web" / "site"
DEVELOPER = "DinaSimple"
APP = "Free to Take"
DEMO_VIDEO = "svM-y8RIBHg"
# Public Android release countdown target (30 days from 30 Sep 2026, Madrid time). Change here to move the date.
LAUNCH_AT = "2026-10-30T12:00:00+01:00"   # YouTube demo (app walkthrough), shown on the home page
PACKAGE = "freetotake.app"
UPDATED = "28 September 2026"

CSS = """
:root{--lime:#C8FF00;--ink:#111;--muted:#5B5B61;--bg:#F2F2F7;--card:#fff;--line:#E5E5EA}
*{box-sizing:border-box}
body{margin:0;background:var(--bg);color:var(--ink);font:16px/1.55 -apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,Inter,sans-serif}
a{color:inherit;text-decoration-color:var(--lime);text-decoration-thickness:2px;text-underline-offset:3px}
.wrap{max-width:880px;margin:0 auto;padding:0 16px}
header.top{display:flex;align-items:center;justify-content:space-between;padding:16px 0}
header.top .brand{display:flex;align-items:center;gap:10px;font-weight:700;text-decoration:none}
header.top .brand img{width:36px;height:45px;object-fit:contain}
nav a{margin-left:18px;font-size:15px;color:var(--muted);text-decoration:none}
nav a:hover{color:var(--ink)}
.hero{display:grid;grid-template-columns:1.2fr 1fr;gap:32px;align-items:center;padding:40px 0 56px}
.hero h1{font-size:clamp(34px,6vw,56px);line-height:1.05;margin:0 0 16px;letter-spacing:-.02em}
.hero p{font-size:18px;color:var(--muted);margin:0 0 24px}
.hero img{width:100%;max-width:320px;justify-self:center}
.demo{display:flex;flex-direction:column;align-items:center;margin:0 0 56px}
.demo .frame{width:100%;max-width:760px;aspect-ratio:848/560;border-radius:18px;overflow:hidden;box-shadow:0 20px 50px rgba(0,0,0,.18);background:#000}
.demo iframe{width:100%;height:100%;border:0;display:block}
.demo p{color:var(--muted);font-size:14px;margin-top:12px}
.dl{margin-top:18px;min-width:240px;text-align:center;cursor:pointer;font-size:17px}
.dl small{display:block;font-weight:500;font-size:12px;opacity:.7}
.launch{background:var(--card);border-radius:18px;padding:28px 20px;text-align:center;margin:0 0 56px;border:2px solid var(--lime)}
.launch h2{margin:0 0 6px}
.launch p{color:var(--muted);margin:0 0 20px}
.count{display:flex;justify-content:center;gap:12px;flex-wrap:wrap}
.count div{min-width:84px;background:var(--lime);color:#000;border:1px solid #000;border-radius:14px;padding:12px 8px}
.count b{display:block;font-size:34px;line-height:1.1;font-variant-numeric:tabular-nums}
.count span{font-size:13px}
.toast{position:fixed;left:50%;bottom:24px;transform:translateX(-50%);background:#111;color:#fff;padding:10px 16px;border-radius:10px;font-size:14px;opacity:0;transition:opacity .2s;pointer-events:none}
.toast.on{opacity:1}
.mark{background:var(--lime);color:#000;padding:0 .15em;border-radius:6px}
.btn{display:inline-block;background:var(--lime);color:#000;border:1px solid #000;border-radius:12px;padding:14px 22px;font-weight:700;text-decoration:none}
.btn.ghost{background:transparent;color:var(--ink);border-color:var(--line);margin-left:8px}
.soon{font-size:13px;color:var(--muted);margin-top:10px}
h2{font-size:26px;margin:0 0 16px;letter-spacing:-.01em}
.steps{display:grid;grid-template-columns:repeat(3,1fr);gap:12px;margin-bottom:56px}
.card{background:var(--card);border-radius:14px;padding:20px}
.card .n{display:inline-grid;place-items:center;width:32px;height:32px;border-radius:50%;background:var(--lime);color:#000;font-weight:700;margin-bottom:10px}
.card h3{margin:0 0 6px;font-size:17px}
.card p{margin:0;color:var(--muted);font-size:15px}
article{background:var(--card);border-radius:14px;padding:28px 24px;margin:24px 0 48px}
article h1{font-size:32px;margin:0 0 4px}
article .meta{color:var(--muted);font-size:14px;margin:0 0 24px}
article h2{font-size:19px;margin:28px 0 8px}
article p,article li{color:var(--ink)}
article ul{padding-left:20px}
table{width:100%;border-collapse:collapse;font-size:15px;margin:8px 0}
th,td{text-align:left;padding:8px 10px;border-bottom:1px solid var(--line);vertical-align:top}
th{font-weight:600}
footer{border-top:1px solid var(--line);padding:24px 0 40px;color:var(--muted);font-size:14px}
footer .row{display:flex;flex-wrap:wrap;gap:8px 20px;justify-content:space-between}
footer a{color:var(--muted)}
@media (max-width:680px){header.top .brand span{display:none}nav a{font-size:14px}.hero{grid-template-columns:1fr;padding-top:16px}.hero img{max-width:220px;order:-1}.steps{grid-template-columns:1fr}nav a{margin-left:12px}.btn.ghost{margin:10px 0 0}}
"""


VERIFY = ""   # Google Search Console "HTML tag" (domain ownership for the OAuth consent screen branding)


def page(title, body, desc, email):
    return f"""<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>{html.escape(title)}</title>
<meta name="description" content="{html.escape(desc)}">
<meta property="og:title" content="{html.escape(title)}"><meta property="og:description" content="{html.escape(desc)}">
<meta property="og:image" content="/logo.png"><meta name="theme-color" content="#C8FF00">
<link rel="icon" href="/favicon.png">{VERIFY}
<style>{CSS}</style></head>
<body><div class="wrap">
<header class="top"><a class="brand" href="/"><img src="/logo.png" alt="{APP}"><span>{APP}</span></a>
<nav><a href="/terms">Terms &amp; Conditions</a><a href="/privacy">Privacy Policy</a></nav></header>
{body}
<footer><div class="row"><span>© 2026 {APP} · Developer: {DEVELOPER}</span>
<span>Contact: <a href="mailto:{email}">{email}</a></span>
<span><a href="/terms">Terms &amp; Conditions</a> · <a href="/privacy">Privacy Policy</a></span></div></footer>
</div></body></html>
"""


def home(email):
    body = f"""
<section class="hero"><div>
<h1>Give away what you don't need. <span class="mark">For free.</span></h1>
<p>{APP} connects neighbours: post an item you no longer need, choose who picks it up, and meet at a time that suits you. No selling, no fees.</p>
<a class="btn" href="#demo">Watch the demo</a><a class="btn ghost" href="mailto:{email}">Contact the developer</a>
<div class="soon">Android app · coming soon to Google Play</div>
</div><img src="/logo.png" alt="{APP} logo"></section>

<h2 id="demo" style="text-align:center">See it in action</h2>
<div class="demo"><div class="frame"><iframe src="https://www.youtube-nocookie.com/embed/{DEMO_VIDEO}?rel=0&amp;modestbranding=1&amp;playsinline=1"
title="{APP} app demo" loading="lazy" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
referrerpolicy="strict-origin-when-cross-origin" allowfullscreen></iframe></div>
<p>30-second tour: onboarding, broccoli rewards, posting, approving a request and chatting.</p>
<a class="btn dl" href="#launch" id="download">Download app<small>Coming soon to Google Play</small></a></div>

<h2 id="how">How it works</h2>
<div class="steps">
<div class="card"><div class="n">1</div><h3>Post a giveaway</h3><p>Add photos, a short description, a meetup point and the times you are available.</p></div>
<div class="card"><div class="n">2</div><h3>Pick who gets it</h3><p>Neighbours request the item with a pickup time. You approve one request.</p></div>
<div class="card"><div class="n">3</div><h3>Meet and hand over</h3><p>Chat with the approved person to arrange the pickup. The chat closes after the meeting.</p></div>
</div>

<section class="launch" id="launch">
<h2>Public Android release is coming soon!</h2>
<p>Free to Take will be on Google Play for everyone in:</p>
<div class="count" id="count" data-at="{LAUNCH_AT}">
<div><b id="cd-d">30</b><span>days</span></div><div><b id="cd-h">00</b><span>hours</span></div>
<div><b id="cd-m">00</b><span>minutes</span></div><div><b id="cd-s">00</b><span>seconds</span></div></div>
</section>
<div class="toast" id="toast">The app isn't available yet — public release is coming soon!</div>
<script>
(function(){{
  var box=document.getElementById('count'), end=new Date(box.dataset.at).getTime();
  function pad(n){{return String(n).padStart(2,'0');}}
  function tick(){{
    var left=Math.max(0,end-Date.now()), s=Math.floor(left/1000);
    document.getElementById('cd-d').textContent=Math.floor(s/86400);
    document.getElementById('cd-h').textContent=pad(Math.floor(s%86400/3600));
    document.getElementById('cd-m').textContent=pad(Math.floor(s%3600/60));
    document.getElementById('cd-s').textContent=pad(s%60);
  }}
  tick(); setInterval(tick,1000);
  // Placeholder: the Download button does nothing yet except explain it.
  var t=document.getElementById('toast');
  document.getElementById('download').addEventListener('click',function(e){{
    e.preventDefault(); t.classList.add('on'); setTimeout(function(){{t.classList.remove('on');}},2200);
  }});
}})();
</script>

"""
    return page(f"{APP} — give away items to neighbours for free", body,
                f"{APP} is a free Android app for giving unwanted items to neighbours. Developer: {DEVELOPER}.", email)


def privacy(email):
    body = f"""<article>
<h1>Privacy Policy</h1><p class="meta">{APP} (Android app, package <code>{PACKAGE}</code>) · Last updated {UPDATED}</p>

<p>This policy explains what personal data {APP} processes, why, and your rights. The service is provided by the developer {DEVELOPER} (“we”). Contact: <a href="mailto:{email}">{email}</a>.</p>

<h2>1. Data we process</h2>
<table><tr><th>Data</th><th>Why</th></tr>
<tr><td>Account: email address, name and profile photo from the sign-in provider you choose (Google or Facebook), a user ID</td><td>Create and secure your account, show your nickname and avatar to people you exchange items with</td></tr>
<tr><td>Profile: nickname, avatar you upload</td><td>Show who is giving or requesting an item</td></tr>
<tr><td>Giveaways: title, description, photos, category, pickup times, the meetup point you type in</td><td>Publish your giveaway; the exact meetup point is shown only to the person you approve</td></tr>
<tr><td>Requests: chosen pickup time and your note</td><td>Let the giver choose a recipient</td></tr>
<tr><td>Chat messages (text only) between approved participants</td><td>Arrange the pickup; chats become read-only after the meeting</td></tr>
<tr><td>Ratings after a completed exchange</td><td>Build trust between users</td></tr>
<tr><td>Favourites, notification preferences</td><td>Features you use in the app</td></tr>
<tr><td>Advertising ID and consent choice (Android)</td><td>Show ads via Google AdMob according to your consent</td></tr>
<tr><td>Purchase status (e.g. “remove ads”)</td><td>Unlock purchases via RevenueCat and Google Play Billing</td></tr>
</table>
<p>We do <b>not</b> access your device GPS location, contacts or call history, and we never ask for apartment, floor or entrance numbers.</p>

<h2>2. Legal bases (GDPR)</h2>
<ul><li>Contract — providing the service you sign up for (account, giveaways, requests, chat).</li>
<li>Consent — personalised advertising (you can change it in the consent dialog at any time).</li>
<li>Legitimate interest — security, abuse prevention and keeping the service working.</li>
<li>Legal obligation — where the law requires us to keep or disclose data.</li></ul>

<h2>3. Service providers</h2>
<ul><li><b>Supabase</b> — database, authentication and file storage (servers in the EU, Frankfurt).</li>
<li><b>Google</b> — Google Sign-In, Google Play, AdMob advertising and consent (UMP).</li>
<li><b>Meta</b> — Facebook Login.</li>
<li><b>RevenueCat</b> — management of in-app purchases.</li>
<li><b>YouTube</b> (Google) — the demo video on our website is embedded in privacy-enhanced mode (youtube-nocookie.com); Google processes data when you play it.</li></ul>
<p>Providers process data on our behalf or as independent controllers under their own policies. Some may transfer data outside the EU under appropriate safeguards such as Standard Contractual Clauses.</p>

<h2>4. Google user data</h2>
<p>When you sign in with Google we receive only your name, email address and profile picture (scopes <code>openid</code>, <code>email</code>, <code>profile</code>) to create your account. {APP}'s use and transfer of information received from Google APIs will adhere to the <a href="https://developers.google.com/terms/api-services-user-data-policy">Google API Services User Data Policy</a>, including the Limited Use requirements. We do not sell this data or use it for advertising.</p>

<h2>5. Retention</h2>
<ul><li>Account and profile — until you delete your account.</li>
<li>Giveaways — active up to 14 days, then archived; removed when you delete your account.</li>
<li>Chats — read-only after the meeting; deleted when you delete your account.</li>
<li>Technical logs — kept by our hosting provider for a limited period for security.</li></ul>

<h2>6. Your rights</h2>
<p>You can request access, correction, deletion, restriction, objection and a copy of your data (portability). Write to <a href="mailto:{email}">{email}</a>. You can also complain to your data-protection authority (in Spain: Agencia Española de Protección de Datos, <a href="https://www.aepd.es">aepd.es</a>).</p>

<h2>7. Children</h2>
<p>{APP} is not intended for children under 16. We do not knowingly collect their data.</p>

<h2>8. Security</h2>
<p>Data is encrypted in transit (HTTPS), access is restricted by row-level security, and exact meetup points are only released to approved participants. No online service can guarantee absolute security.</p>

<h2>9. Changes</h2>
<p>We will update this page and the date above when this policy changes, and notify you in the app about important changes.</p>
</article>"""
    return page(f"Privacy Policy — {APP}", body, f"How {APP} processes personal data.", email)


def docx_paragraphs(path):
    """(style id, text) per paragraph — stdlib only, no python-docx needed."""
    import zipfile, xml.etree.ElementTree as ET
    ns = {"w": "http://schemas.openxmlformats.org/wordprocessingml/2006/main"}
    root = ET.fromstring(zipfile.ZipFile(path).read("word/document.xml"))
    for p in root.iter(f"{{{ns['w']}}}p"):
        st = p.find("w:pPr/w:pStyle", ns)
        style = st.get(f"{{{ns['w']}}}val") if st is not None else ""
        yield style, "".join(t.text or "" for t in p.iter(f"{{{ns['w']}}}t"))


def terms(email):
    """Verbatim from web/brand/TERMSANDCONDITIONS.docx (Figma); "Free to Give" → "Free to Take" (product rename, v1.12.1)."""
    parts, version = [], ""
    for style, raw in docx_paragraphs(ROOT / "web/brand/TERMSANDCONDITIONS.docx"):
        text = raw.strip().replace("Free to Give", APP).replace("FREE TO GIVE", APP.upper())
        if not text or text in (APP.upper(), "Terms & Conditions"):
            continue
        if text.startswith("Version"):
            version = text; continue
        t = html.escape(text)
        if "contact method provided in the app or Privacy Policy" in text:
            t += f' Email: <a href="mailto:{email}">{email}</a>.'
        parts.append(f"<h2>{t}</h2>" if style.startswith("Heading") else f"<p>{t}</p>")
    body = f"""<article><h1>Terms &amp; Conditions</h1><p class="meta">{APP} · {version}</p>
{''.join(parts)}</article>"""
    return page(f"Terms & Conditions — {APP}", body, f"Terms & Conditions of {APP}.", email)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--email", required=True)
    ap.add_argument("--google-verification", default="", help="content= value of Search Console's HTML-tag method")
    a = ap.parse_args()
    email = a.email.strip()
    global VERIFY
    if a.google_verification.strip():
        VERIFY = f'\n<meta name="google-site-verification" content="{html.escape(a.google_verification.strip())}">'
    OUT.mkdir(parents=True, exist_ok=True)   # overwrite in place (no delete needed)
    for stale in ("delete-account.html",):   # pages removed in 1.16.7
        try: (OUT / stale).unlink()
        except OSError: pass
    shutil.copy(ROOT / "web/brand/logo_mascot.png", OUT / "logo.png")   # logo with mascot (brand, 2026-09-28)
    shutil.copy(ROOT / "web/brand/favicon.png", OUT / "favicon.png")
    for name, fn in [("index", home), ("terms", terms), ("privacy", privacy)]:
        (OUT / f"{name}.html").write_text(fn(email))
    shutil.copytree(ROOT / "web/auth", OUT / "auth", dirs_exist_ok=True)   # email-link confirmation page (kept for the hidden email flow)
    (OUT / "_headers").write_text("/*\n  X-Content-Type-Options: nosniff\n  Referrer-Policy: strict-origin-when-cross-origin\n")
    print("built", OUT)


if __name__ == "__main__":
    main()
