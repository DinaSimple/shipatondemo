-- SPDX-License-Identifier: AGPL-3.0-only
-- Copyright (C) 2026 Dina Yol (DinaSimple) — Free to Take, https://freetotake.app
-- v1.9.1 — privacy: apartment / entrance / floor are never collected (product decision). Columns + params removed.

drop function public.publish_item(text, text, text, double precision, double precision, text, text, text, text, date, time, time, text[], text, text, text, text, text);
drop function public.update_item(uuid, text, text, text, double precision, double precision, text, text, text, text, date, time, time, text[], text, text, text, text, text);
alter table public.item_pickup_points drop column apartment, drop column entrance, drop column floor;

create or replace function public.publish_item(
  p_title text, p_description text, p_category text,
  p_lat double precision, p_lng double precision, p_place_name text, p_address text,
  p_area_city text, p_area_postal text,
  p_date date, p_from time, p_to time,
  p_photo_paths text[] default '{}',
  p_notes text default null,
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

  insert into public.item_pickup_points (item_id, lat, lng, place_name, address, notes)
  values (v_item.id, p_lat, p_lng, left(p_place_name, 200), left(p_address, 300), nullif(btrim(p_notes), ''));

  insert into public.item_availability (item_id, slot_date, slot_time)
  select v_item.id, p_date, t::time
  from generate_series(p_date + p_from, p_date + p_to - interval '1 second', interval '30 minutes') t;

  select * into v_item from public.items where id = v_item.id;
  return v_item;
end $$;

create or replace function public.update_item(
  p_item_id uuid,
  p_title text, p_description text, p_category text,
  p_lat double precision, p_lng double precision, p_place_name text, p_address text,
  p_area_city text, p_area_postal text,
  p_date date, p_from time, p_to time,
  p_photo_paths text[] default '{}',
  p_notes text default null,
  p_time_zone text default 'Europe/Madrid'
) returns public.items
language plpgsql security definer set search_path = public as $$
declare
  v_uid  uuid := public._require_uid();
  v_item public.items;
  v_path text;
begin
  select * into v_item from public.items where id = p_item_id for update;
  if not found or v_item.giver_id <> v_uid then raise exception 'NOT_ITEM_OWNER'; end if;
  -- editing while no collector is assigned (Q27)
  if v_item.status <> 'available' then raise exception 'NOT_EDITABLE'; end if;
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

  update public.items set title = btrim(p_title), description = coalesce(p_description, ''), category = p_category,
         photo_paths = p_photo_paths, area_city = nullif(btrim(p_area_city), ''), area_postal = nullif(btrim(p_area_postal), ''),
         time_zone = p_time_zone
  where id = p_item_id;

  insert into public.item_pickup_points (item_id, lat, lng, place_name, address, notes)
  values (p_item_id, p_lat, p_lng, left(p_place_name, 200), left(p_address, 300), nullif(btrim(p_notes), ''))
  on conflict (item_id) do update set lat = excluded.lat, lng = excluded.lng, place_name = excluded.place_name, address = excluded.address, notes = excluded.notes, updated_at = now();

  -- new schedule replaces the offered slots; already sent requests keep the slot they asked for
  delete from public.item_availability where item_id = p_item_id;
  insert into public.item_availability (item_id, slot_date, slot_time)
  select p_item_id, p_date, t::time
  from generate_series(p_date + p_from, p_date + p_to - interval '1 second', interval '30 minutes') t;

  select * into v_item from public.items where id = p_item_id;
  return v_item;
end $$;

revoke execute on function public.publish_item(text, text, text, double precision, double precision, text, text, text, text, date, time, time, text[], text, text), public.update_item(uuid, text, text, text, double precision, double precision, text, text, text, text, date, time, time, text[], text, text) from public, anon;
grant execute on function public.publish_item(text, text, text, double precision, double precision, text, text, text, text, date, time, time, text[], text, text), public.update_item(uuid, text, text, text, double precision, double precision, text, text, text, text, date, time, time, text[], text, text) to authenticated;
