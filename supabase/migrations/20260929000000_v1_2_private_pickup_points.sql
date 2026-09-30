-- SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
-- Copyright (C) 2026 Dina Yol (DinaSimple) — Free to Take, https://freetotake.app
-- v1.2 — exact pickup information stays private until the giver approves a request.
-- Public: items.area_city / area_postal / approx_lat / approx_lng (~1 km grid).
-- Private: item_pickup_points (exact point, place name, address) — giver + approved taker only.

alter table public.items
  add column area_city   text,
  add column area_postal text,
  add column approx_lat  numeric(5,2),
  add column approx_lng  numeric(5,2);

create table public.item_pickup_points (
  item_id    uuid primary key references public.items (id) on delete cascade,
  lat        double precision not null check (lat between -90 and 90),
  lng        double precision not null check (lng between -180 and 180),
  place_name text check (char_length(place_name) <= 200),
  address    text check (char_length(address) <= 300),
  updated_at timestamptz not null default now()
);
alter table public.item_pickup_points enable row level security;

create policy pickup_read_giver_or_approved on public.item_pickup_points for select to authenticated
  using (
    exists (select 1 from public.items i where i.id = item_id and i.giver_id = auth.uid())
    or exists (select 1 from public.claims c
               where c.item_id = item_pickup_points.item_id and c.taker_id = auth.uid()
                 and c.status in ('approved', 'completed'))
  );
create policy pickup_write_giver on public.item_pickup_points for insert to authenticated
  with check (exists (select 1 from public.items i where i.id = item_id and i.giver_id = auth.uid()));
create policy pickup_update_giver on public.item_pickup_points for update to authenticated
  using (exists (select 1 from public.items i where i.id = item_id and i.giver_id = auth.uid()))
  with check (exists (select 1 from public.items i where i.id = item_id and i.giver_id = auth.uid()));
revoke all on public.item_pickup_points from anon;

-- keep the public approximate point in sync (rounded, never exact)
create or replace function public.sync_item_approx_point() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  update public.items set approx_lat = round(new.lat::numeric, 2), approx_lng = round(new.lng::numeric, 2)
  where id = new.item_id;
  return new;
end $$;
revoke execute on function public.sync_item_approx_point() from public, anon, authenticated;
create trigger item_pickup_sync after insert or update on public.item_pickup_points
  for each row execute function public.sync_item_approx_point();

-- area/approx columns are service-maintained or set by owner (not status)
grant update (area_city, area_postal) on public.items to authenticated;
