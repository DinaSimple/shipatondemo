-- SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
-- Copyright (C) 2026 Dina Elokhova (DinaSimple) — Free to Take, https://freetotake.app
-- v1.12 — Login & auth: captcha-gated email links (sign-up + password recovery).
-- The Edge Function `auth-email` (service role) is the only caller of these RPCs.
-- Links: random token (only its SHA-256 is stored), 5-minute expiry, single use.

create table if not exists public.auth_links (
  id uuid primary key default gen_random_uuid(),
  token_hash text not null unique,
  email text not null,
  purpose text not null check (purpose in ('signup', 'recover')),
  created_at timestamptz not null default now(),
  expires_at timestamptz not null,
  used_at timestamptz
);
create index if not exists auth_links_email_idx on public.auth_links (lower(email), created_at desc);
alter table public.auth_links enable row level security;   -- no policies: invisible to anon/authenticated
revoke all on public.auth_links from anon, authenticated;

-- 'none' | 'no_password' (started sign-up, never set a password) | 'active'
create or replace function public.auth_email_status(p_email text) returns text
language sql stable security definer set search_path = '' as $$
  select case
    when u.id is null then 'none'
    when coalesce(u.encrypted_password, '') = '' then 'no_password'
    else 'active' end
  from (select 1) x
  left join auth.users u on lower(u.email) = lower(trim(p_email)) and coalesce(u.is_anonymous, false) = false
  limit 1
$$;

-- Issue a link. Limits: 1 per minute and 5 per hour per email. Older unused links of the same purpose die.
create or replace function public.auth_link_issue(p_email text, p_purpose text, p_token_hash text, p_ttl_seconds int default 300)
returns text language plpgsql security definer set search_path = '' as $$
declare e text := lower(trim(p_email));
begin
  if p_purpose not in ('signup', 'recover') then return 'BAD_PURPOSE'; end if;
  if exists (select 1 from public.auth_links where lower(email) = e and created_at > now() - interval '60 seconds')
     or (select count(*) from public.auth_links where lower(email) = e and created_at > now() - interval '1 hour') >= 5
  then return 'RATE_LIMITED'; end if;
  delete from public.auth_links where created_at < now() - interval '1 day';
  update public.auth_links set used_at = now()
    where lower(email) = e and purpose = p_purpose and used_at is null;
  insert into public.auth_links (token_hash, email, purpose, expires_at)
    values (p_token_hash, e, p_purpose, now() + make_interval(secs => p_ttl_seconds));
  return 'ok';
end $$;

-- Non-consuming check (web confirmation page): 'ok' | 'LINK_EXPIRED' | 'LINK_USED' | 'LINK_INVALID'
create or replace function public.auth_link_check(p_token_hash text)
returns table (status text, purpose text) language sql stable security definer set search_path = '' as $$
  select coalesce(
           case when l.id is null then 'LINK_INVALID'
                when l.used_at is not null then 'LINK_USED'
                when l.expires_at <= now() then 'LINK_EXPIRED'
                else 'ok' end, 'LINK_INVALID'),
         l.purpose
  from (select 1) x left join public.auth_links l on l.token_hash = p_token_hash
$$;

-- Atomic single-use redeem. Returns the email for 'ok' and for 'LINK_EXPIRED' (so the app can offer a resend).
create or replace function public.auth_link_redeem(p_token_hash text)
returns table (status text, email text, purpose text) language plpgsql security definer set search_path = '' as $$
declare l public.auth_links;
begin
  update public.auth_links set used_at = now()
    where token_hash = p_token_hash and used_at is null and expires_at > now()
    returning * into l;
  if found then return query select 'ok'::text, l.email, l.purpose; return; end if;
  select * into l from public.auth_links where token_hash = p_token_hash;
  if not found then return query select 'LINK_INVALID'::text, null::text, null::text;
  elsif l.used_at is not null then return query select 'LINK_USED'::text, null::text, l.purpose;
  else return query select 'LINK_EXPIRED'::text, l.email, l.purpose;
  end if;
end $$;

-- Settings for the function, kept in Vault (update values in Dashboard → Vault or SQL; env vars override).
create or replace function public.auth_email_config()
returns table (name text, value text) language sql stable security definer set search_path = '' as $$
  select name, decrypted_secret from vault.decrypted_secrets
  where name in ('hcaptcha_secret', 'hcaptcha_sitekey', 'resend_api_key', 'mail_from', 'link_base_url')
$$;

revoke execute on function public.auth_email_status(text), public.auth_link_issue(text, text, text, int),
  public.auth_link_check(text), public.auth_link_redeem(text), public.auth_email_config() from public, anon, authenticated;
grant execute on function public.auth_email_status(text), public.auth_link_issue(text, text, text, int),
  public.auth_link_check(text), public.auth_link_redeem(text), public.auth_email_config() to service_role;

-- Defaults = hCaptcha public TEST keys (always pass) until real keys are stored. Replace before release.
select vault.create_secret('0x0000000000000000000000000000000000000000', 'hcaptcha_secret')
where not exists (select 1 from vault.decrypted_secrets where name = 'hcaptcha_secret');
select vault.create_secret('10000000-ffff-ffff-ffff-000000000001', 'hcaptcha_sitekey')
where not exists (select 1 from vault.decrypted_secrets where name = 'hcaptcha_sitekey');
