-- SPDX-License-Identifier: AGPL-3.0-only
-- Copyright (C) 2026 Dina Elokhova (DinaSimple) — Free to Take, https://freetotake.app
-- v1.8.1 — answers Q22/Q23: photos required (1–3); photo files deleted 14 days after the publication expired.

do $$ begin create extension if not exists pg_cron; exception when others then raise notice 'pg_cron unavailable (local test DB)'; end $$;
do $$ begin create extension if not exists pg_net with schema extensions; exception when others then raise notice 'pg_net unavailable (local test DB)'; end $$;

alter table public.items add column photos_deleted_at timestamptz;

-- ---------- publish: at least one photo ----------
create or replace function public.publish_item(
  p_title text, p_description text, p_category text,
  p_lat double precision, p_lng double precision, p_place_name text, p_address text,
  p_area_city text, p_area_postal text,
  p_date date, p_from time, p_to time,
  p_photo_paths text[] default '{}',
  p_apartment text default null, p_entrance text default null, p_floor text default null, p_notes text default null,
  p_time_zone text default 'Europe/Madrid'
) returns public.items
language plpgsql security definer set search_path = public as $$
declare
  v_uid  uuid := public._require_uid();
  v_item public.items;
  v_path text;
begin
  if coalesce(btrim(p_title), '') = '' or char_length(btrim(p_title)) > 80 then raise exception 'TITLE_INVALID'; end if;
  if char_length(coalesce(p_description, '')) > 1000 then raise exception 'DESCRIPTION_TOO_LONG'; end if;
  if p_date is null or p_from is null or p_to is null or p_from >= p_to then raise exception 'TIME_RANGE_INVALID'; end if;
  if (p_date + p_from) at time zone p_time_zone <= now() then raise exception 'TIME_IN_PAST'; end if;
  if p_lat is null or p_lng is null then raise exception 'LOCATION_REQUIRED'; end if;
  if coalesce(array_length(p_photo_paths, 1), 0) > 3 then raise exception 'TOO_MANY_PHOTOS'; end if;
  if coalesce(array_length(p_photo_paths, 1), 0) < 1 then raise exception 'PHOTO_REQUIRED'; end if;
  foreach v_path in array p_photo_paths loop
    if split_part(v_path, '/', 1) <> v_uid::text then raise exception 'PHOTO_NOT_OWNED'; end if;
  end loop;

  insert into public.items (giver_id, title, description, category, photo_paths, area_city, area_postal, status, expires_at, time_zone)
  values (v_uid, btrim(p_title), coalesce(p_description, ''), p_category, p_photo_paths,
          nullif(btrim(p_area_city), ''), nullif(btrim(p_area_postal), ''), 'available', now() + interval '14 days', p_time_zone)
  returning * into v_item;

  insert into public.item_pickup_points (item_id, lat, lng, place_name, address, apartment, entrance, floor, notes)
  values (v_item.id, p_lat, p_lng, left(p_place_name, 200), left(p_address, 300),
          nullif(btrim(p_apartment), ''), nullif(btrim(p_entrance), ''), nullif(btrim(p_floor), ''), nullif(btrim(p_notes), ''));

  insert into public.item_availability (item_id, slot_date, slot_time)
  select v_item.id, p_date, t::time
  from generate_series(p_date + p_from, p_date + p_to - interval '1 second', interval '30 minutes') t;

  select * into v_item from public.items where id = v_item.id;
  return v_item;
end $$;

-- ---------- cleanup job (Edge Function `cleanup-expired-photos`, called daily by pg_cron) ----------
-- Only service_role (the Edge Function) may call these.
create or replace function public.expired_photo_batch(p_limit int default 200)
returns table (item_id uuid, photo_paths text[])
language sql stable security definer set search_path = public as $$
  select id, photo_paths from public.items
  where expires_at < now() - interval '14 days' and photo_paths <> '{}'
  order by expires_at limit p_limit
$$;

create or replace function public.mark_photos_deleted(p_item_ids uuid[])
returns int language sql security definer set search_path = public as $$
  with u as (update public.items set photo_paths = '{}', photos_deleted_at = now()
             where id = any(p_item_ids) and expires_at < now() - interval '14 days' returning 1)
  select count(*)::int from u
$$;

-- Shared secret between pg_cron and the function (function runs with verify_jwt off + this check).
create or replace function public.cleanup_token_ok(p_token text)
returns boolean language sql stable security definer set search_path = public as $$
  select exists (select 1 from vault.decrypted_secrets where name = 'photo_cleanup_token' and decrypted_secret = p_token)
$$;

revoke execute on function public.expired_photo_batch(int), public.mark_photos_deleted(uuid[]), public.cleanup_token_ok(text) from public, anon, authenticated;
grant execute on function public.expired_photo_batch(int), public.mark_photos_deleted(uuid[]), public.cleanup_token_ok(text) to service_role;

select vault.create_secret(gen_random_uuid()::text || gen_random_uuid()::text, 'photo_cleanup_token')
where not exists (select 1 from vault.decrypted_secrets where name = 'photo_cleanup_token');

select cron.schedule(
  'photo-cleanup-daily', '17 3 * * *',
  $cmd$ select net.http_post(
      url := 'https://YOUR_PROJECT_REF.supabase.co/functions/v1/cleanup-expired-photos',
      headers := jsonb_build_object('Content-Type', 'application/json',
        'x-cleanup-token', (select decrypted_secret from vault.decrypted_secrets where name = 'photo_cleanup_token')),
      body := '{}'::jsonb) $cmd$
);
