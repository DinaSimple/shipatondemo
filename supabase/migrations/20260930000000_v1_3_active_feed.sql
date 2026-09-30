-- SPDX-License-Identifier: AGPL-3.0-only
-- Copyright (C) 2026 Dina Yol (DinaSimple) — Free to Take, https://freetotake.app
-- v1.3 — Home "Available Giveaways": active listings only, optional city scope.
-- Active = available/reserved AND not expired. Expiry is evaluated server-side at query time,
-- so it never depends on the app being open (a pg_cron job can flip statuses later).

alter table public.items add column expires_at timestamptz;
create index items_city_idx on public.items (lower(area_city)) where status in ('available', 'reserved');

create or replace view public.active_items
with (security_invoker = true) as
  select id, giver_id, title, description, photo_paths, category, area_city, area_postal,
         approx_lat, approx_lng, status, created_at, expires_at
  from public.items
  where status in ('available', 'reserved')
    and (expires_at is null or expires_at > now());

grant select on public.active_items to anon, authenticated;
