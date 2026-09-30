-- SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
-- Copyright (C) 2026 Dina Yol (DinaSimple) — Free to Take, https://freetotake.app
-- v1.13 — own image captcha (tiles bundled in the app as files/cap/<id>.jpg; labels only on the server).
-- Replaces hCaptcha. A solved challenge yields a pass token (hash stored) valid 15 min, consumed when an email is sent.

create table if not exists public.captcha_tiles (id text primary key, label text not null);
create table if not exists public.captcha_challenges (
  id uuid primary key default gen_random_uuid(),
  tiles text[] not null,
  answer int[] not null,
  label text not null,
  created_at timestamptz not null default now(),
  expires_at timestamptz not null default now() + interval '3 minutes',
  used_at timestamptz
);
create table if not exists public.captcha_passes (
  token_hash text primary key,
  created_at timestamptz not null default now(),
  expires_at timestamptz not null default now() + interval '15 minutes',
  used_at timestamptz
);
alter table public.captcha_tiles enable row level security;
alter table public.captcha_challenges enable row level security;
alter table public.captcha_passes enable row level security;
revoke all on public.captcha_tiles, public.captcha_challenges, public.captcha_passes from anon, authenticated;

-- PUBLIC DEMO COPY: the 180 tile answers (labels) are intentionally omitted so the image captcha
-- cannot be solved from source. Regenerate your own tiles + labels with tools/captcha_tiles_gen.py
-- (a random seed is used) and insert them here before enabling the email sign-up flow.
-- insert into public.captcha_tiles (id, label) values ('<tile id>', '<label>'), ...;

-- New 3×3 challenge: 3–4 tiles of one label, the rest from other labels, shuffled.
create or replace function public.captcha_new()
returns table (id uuid, label text, tiles text[]) language plpgsql security definer set search_path = '' as $$
declare lbl text; pos int := 3 + floor(random() * 2)::int; t text[]; ans int[]; cid uuid;
begin
  delete from public.captcha_challenges where created_at < now() - interval '1 hour';
  delete from public.captcha_passes where created_at < now() - interval '1 day';
  select c.label into lbl from (select distinct ct.label from public.captcha_tiles ct) c order by random() limit 1;
  with p as (select ct.id, true as hit from public.captcha_tiles ct where ct.label = lbl order by random() limit pos),
       n as (select ct.id, false as hit from public.captcha_tiles ct where ct.label <> lbl order by random() limit 9 - pos),
       a as (select x.id, x.hit, row_number() over (order by random()) - 1 as i from (select * from p union all select * from n) x)
  select array_agg(a.id order by a.i), array_agg(a.i::int order by a.i) filter (where a.hit) into t, ans from a;
  insert into public.captcha_challenges (tiles, answer, label) values (t, ans, lbl) returning captcha_challenges.id into cid;
  return query select cid, lbl, t;
end $$;

-- One attempt per challenge. 'ok' | 'WRONG' | 'EXPIRED'. On 'ok' the pass (hash from the function) is stored.
create or replace function public.captcha_verify(p_id uuid, p_selected int[], p_pass_hash text)
returns text language plpgsql security definer set search_path = '' as $$
declare c public.captcha_challenges;
begin
  update public.captcha_challenges set used_at = now()
    where captcha_challenges.id = p_id and used_at is null and expires_at > now() returning * into c;
  if not found then return 'EXPIRED'; end if;
  if (select coalesce(array_agg(distinct s order by s), '{}') from unnest(p_selected) s)
     is distinct from (select array_agg(a order by a) from unnest(c.answer) a) then return 'WRONG'; end if;
  insert into public.captcha_passes (token_hash) values (p_pass_hash);
  return 'ok';
end $$;

create or replace function public.captcha_pass_valid(p_hash text) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.captcha_passes where token_hash = p_hash and used_at is null and expires_at > now())
$$;

create or replace function public.captcha_pass_consume(p_hash text) returns boolean
language plpgsql security definer set search_path = '' as $$
begin
  update public.captcha_passes set used_at = now() where token_hash = p_hash and used_at is null and expires_at > now();
  return found;
end $$;

revoke execute on function public.captcha_new(), public.captcha_verify(uuid, int[], text),
  public.captcha_pass_valid(text), public.captcha_pass_consume(text) from public, anon, authenticated;
grant execute on function public.captcha_new(), public.captcha_verify(uuid, int[], text),
  public.captcha_pass_valid(text), public.captcha_pass_consume(text) to service_role;

-- Email provider settings for auth-email (Vault): Brevo, Resend or a webhook (n8n / Make / Zapier).
create or replace function public.auth_email_config()
returns table (name text, value text) language sql stable security definer set search_path = '' as $$
  select name, decrypted_secret from vault.decrypted_secrets
  where name in ('brevo_api_key', 'resend_api_key', 'email_webhook_url', 'email_webhook_secret', 'mail_from', 'link_base_url')
$$;
revoke execute on function public.auth_email_config() from public, anon, authenticated;
grant execute on function public.auth_email_config() to service_role;
