-- SPDX-License-Identifier: AGPL-3.0-only
-- Copyright (C) 2026 Dina Yol (DinaSimple) — Free to Take, https://freetotake.app
-- v1.3 active feed
\set ON_ERROR_STOP on

insert into auth.users (id, email) values ('00000000-0000-0000-0000-0000000000e1', 'g3@x.io');
insert into public.items (id, giver_id, title, area_city, status, expires_at) values
  ('33333333-0000-0000-0000-000000000001', '00000000-0000-0000-0000-0000000000e1', 'Active BCN', 'Barcelona', 'available', null),
  ('33333333-0000-0000-0000-000000000002', '00000000-0000-0000-0000-0000000000e1', 'Expired',    'Barcelona', 'available', now() - interval '1 minute'),
  ('33333333-0000-0000-0000-000000000003', '00000000-0000-0000-0000-0000000000e1', 'Withdrawn',  'Barcelona', 'withdrawn', null),
  ('33333333-0000-0000-0000-000000000004', '00000000-0000-0000-0000-0000000000e1', 'Madrid',     'Madrid',    'available', now() + interval '1 day');  -- was 'reserved' (hidden since 1.7, spec 0.10)

-- R-1.3-30 guests see only active listings (not deleted/withdrawn, not expired)
begin; set local role anon; select set_config('request.jwt.claim.sub', '', true);
do $$ begin
  assert (select count(*) from public.active_items where id::text like '33333333%') = 2, 'active count';
  assert not exists (select 1 from public.active_items where title in ('Expired', 'Withdrawn')), 'inactive leaked';
  -- R-1.3-31 city scope
  assert (select count(*) from public.active_items where lower(area_city) = lower('barcelona') and id::text like '33333333%') = 1, 'city scope';
end $$;
commit;

\echo 'V1.3 SQL TESTS PASSED'
