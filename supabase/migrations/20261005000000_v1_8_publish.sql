-- SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
-- Copyright (C) 2026 Dina Yol (DinaSimple) — Free to Take, https://freetotake.app
-- v1.8 — Create publication flow (spec 0.11)

-- private meetup details (visible only to giver + approved taker, like the exact point)
alter table public.item_pickup_points
  add column apartment text check (char_length(apartment) <= 40),
  add column entrance  text check (char_length(entrance) <= 40),
  add column floor     text check (char_length(floor) <= 40),
  add column notes     text check (char_length(notes) <= 300);

alter table public.items add constraint items_description_len check (char_length(description) <= 1000) not valid;

-- One atomic call: listing + private pickup point + 30-min request slots in [from, to).
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
  foreach v_path in array coalesce(p_photo_paths, '{}') loop
    if split_part(v_path, '/', 1) <> v_uid::text then raise exception 'PHOTO_NOT_OWNED'; end if;
  end loop;

  insert into public.items (giver_id, title, description, category, photo_paths, area_city, area_postal, status, expires_at, time_zone)
  values (v_uid, btrim(p_title), coalesce(p_description, ''), p_category, coalesce(p_photo_paths, '{}'),
          nullif(btrim(p_area_city), ''), nullif(btrim(p_area_postal), ''), 'available', now() + interval '14 days', p_time_zone)
  returning * into v_item;

  insert into public.item_pickup_points (item_id, lat, lng, place_name, address, apartment, entrance, floor, notes)
  values (v_item.id, p_lat, p_lng, left(p_place_name, 200), left(p_address, 300),
          nullif(btrim(p_apartment), ''), nullif(btrim(p_entrance), ''), nullif(btrim(p_floor), ''), nullif(btrim(p_notes), ''));

  insert into public.item_availability (item_id, slot_date, slot_time)
  select v_item.id, p_date, t::time
  from generate_series(p_date + p_from, p_date + p_to - interval '1 second', interval '30 minutes') t;

  select * into v_item from public.items where id = v_item.id;   -- includes approx point from trigger
  return v_item;
end $$;

revoke execute on function public.publish_item(text, text, text, double precision, double precision, text, text, text, text, date, time, time, text[], text, text, text, text, text) from public, anon;
grant execute on function public.publish_item(text, text, text, double precision, double precision, text, text, text, text, date, time, time, text[], text, text, text, text, text) to authenticated;
