-- SPDX-License-Identifier: AGPL-3.0-only
-- Copyright (C) 2026 Dina Yol (DinaSimple) — Free to Take, https://freetotake.app
-- v1.9 — My Publications: Active and Archived (spec 0.12)

-- in-app messages to requesters (chat comes later; stored here meanwhile)
alter table public.notifications add column body text check (char_length(body) <= 500);
alter table public.notifications drop constraint notifications_kind_check;
alter table public.notifications add constraint notifications_kind_check
  check (kind in ('taker_left_queue', 'pickup_cancelled_reopened', 'pickup_cancelled_republish_needed', 'publication_removed'));

-- My publications list: one row per own publication with schedule, pending count and assigned collector
create or replace view public.my_publications with (security_invoker = true) as
  select i.id, i.giver_id, i.title, i.description, i.photo_paths, i.category, i.area_city, i.area_postal,
         i.approx_lat, i.approx_lng, i.status, i.created_at, i.expires_at,
         s.slot_date as schedule_date, s.first_slot as schedule_from, (s.last_slot + interval '30 minutes')::time as schedule_to,
         (s.slot_date + s.first_slot) at time zone i.time_zone as schedule_start,
         (select count(*) from public.claims c where c.item_id = i.id and c.status = 'pending_approval')::int as pending_requests,
         a.nickname as collector_nickname, a.pickup_at as collector_pickup_at
  from public.items i
  left join lateral (select min(slot_date) as slot_date, min(slot_time) as first_slot, max(slot_time) as last_slot
                     from public.item_availability v where v.item_id = i.id
                     and v.slot_date = (select min(slot_date) from public.item_availability w where w.item_id = i.id)) s on true
  left join lateral (select p.nickname, c.pickup_at from public.claims c join public.profiles p on p.id = c.taker_id
                     where c.item_id = i.id and c.status = 'approved' limit 1) a on true
  where i.giver_id = auth.uid();
grant select on public.my_publications to authenticated;

-- Request review list for the publisher (RLS on claims: giver of the item or the taker)
create or replace view public.item_requests with (security_invoker = true) as
  select c.id, c.item_id, c.taker_id, c.status, c.created_at, c.message, c.requested_date, c.requested_time, c.pickup_at,
         p.nickname as taker_nickname
  from public.claims c join public.profiles p on p.id = c.taker_id;
grant select on public.item_requests to authenticated;

-- Cancel/unpublish: up to 2 h before the pickup start; requesters are informed.
create or replace function public.withdraw_item(p_item_id uuid)
returns public.items
language plpgsql security definer set search_path = public as $$
declare
  v_uid   uuid := public._require_uid();
  v_item  public.items;
  v_start timestamptz;
begin
  select * into v_item from public.items where id = p_item_id for update;
  if not found or v_item.giver_id <> v_uid then raise exception 'NOT_ITEM_OWNER'; end if;
  if v_item.status in ('given', 'withdrawn') then raise exception 'INVALID_TRANSITION'; end if;
  v_start := coalesce(
    (select pickup_at from public.claims where item_id = p_item_id and status = 'approved' limit 1),
    (select min((slot_date + slot_time) at time zone v_item.time_zone) from public.item_availability where item_id = p_item_id));
  if v_start is not null and v_start - now() <= interval '2 hours' then raise exception 'CANCEL_TOO_LATE'; end if;

  insert into public.notifications (user_id, kind, item_id, claim_id, body)
  select c.taker_id, 'publication_removed', p_item_id, c.id,
         'Sorry, the publisher removed this publication. Please try your luck again next time. We are very sorry.'
  from public.claims c where c.item_id = p_item_id and c.status in ('pending_approval', 'approved');

  update public.claims set status = 'closed'
    where item_id = p_item_id and status in ('pending_approval', 'approved');
  update public.items set status = 'withdrawn' where id = p_item_id returning * into v_item;
  return v_item;
end $$;

-- Edit a publication (same rules as publishing; only before a collector is assigned)
create or replace function public.update_item(
  p_item_id uuid,
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

  insert into public.item_pickup_points (item_id, lat, lng, place_name, address, apartment, entrance, floor, notes)
  values (p_item_id, p_lat, p_lng, left(p_place_name, 200), left(p_address, 300),
          nullif(btrim(p_apartment), ''), nullif(btrim(p_entrance), ''), nullif(btrim(p_floor), ''), nullif(btrim(p_notes), ''))
  on conflict (item_id) do update set lat = excluded.lat, lng = excluded.lng, place_name = excluded.place_name, address = excluded.address,
         apartment = excluded.apartment, entrance = excluded.entrance, floor = excluded.floor, notes = excluded.notes, updated_at = now();

  -- new schedule replaces the offered slots; already sent requests keep the slot they asked for
  delete from public.item_availability where item_id = p_item_id;
  insert into public.item_availability (item_id, slot_date, slot_time)
  select p_item_id, p_date, t::time
  from generate_series(p_date + p_from, p_date + p_to - interval '1 second', interval '30 minutes') t;

  select * into v_item from public.items where id = p_item_id;
  return v_item;
end $$;

revoke execute on function public.update_item(uuid, text, text, text, double precision, double precision, text, text, text, text, date, time, time, text[], text, text, text, text, text) from public, anon;
grant execute on function public.update_item(uuid, text, text, text, double precision, double precision, text, text, text, text, date, time, time, text[], text, text, text, text, text) to authenticated;
