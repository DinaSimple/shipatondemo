-- SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
-- Copyright (C) 2026 Dina Elokhova (DinaSimple) — Free to Take, https://freetotake.app
-- v1.2 exact pickup privacy
\set ON_ERROR_STOP on

insert into auth.users (id, email) values
  ('00000000-0000-0000-0000-0000000000f1', 'g2@x.io'),
  ('00000000-0000-0000-0000-0000000000f2', 't2@x.io'),
  ('00000000-0000-0000-0000-0000000000f3', 'o2@x.io');

begin; set local role authenticated; select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-0000000000f1', true);
insert into public.items (id, giver_id, title, area_city, area_postal) values
  ('22222222-0000-0000-0000-000000000001', '00000000-0000-0000-0000-0000000000f1', 'Sofa', 'Barcelona', '08019');
insert into public.item_pickup_points (item_id, lat, lng, place_name, address) values
  ('22222222-0000-0000-0000-000000000001', 41.403612, 2.204623, 'Poblenou Bar Sol', 'Rambla del Poblenou 125');
commit;

-- R-1.2-11 public sees area + coarse point only
begin; set local role anon; select set_config('request.jwt.claim.sub', '', true);
do $$ begin
  assert (select approx_lat = 41.40 and approx_lng = 2.20 and area_city = 'Barcelona' from public.items where id = '22222222-0000-0000-0000-000000000001'), 'approx point';
end $$;
select public.t_expect_error($q$select * from public.item_pickup_points$q$, 'permission denied');
commit;

-- R-1.2-12 requester with pending claim cannot read exact point; after approval can
begin; set local role authenticated; select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-0000000000f2', true);
select public.submit_claim('22222222-0000-0000-0000-000000000001');
do $$ begin assert (select count(*) from public.item_pickup_points) = 0, 'pending taker sees exact point'; end $$;
commit;
begin; set local role authenticated; select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-0000000000f3', true);
do $$ begin assert (select count(*) from public.item_pickup_points) = 0, 'stranger sees exact point'; end $$;
select public.t_expect_error($q$insert into public.item_pickup_points (item_id, lat, lng) values ('22222222-0000-0000-0000-000000000001', 1, 1)$q$, 'row-level security');
commit;
begin; set local role authenticated; select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-0000000000f1', true);
do $$ begin assert (select count(*) from public.item_pickup_points) = 1, 'giver cannot see own point'; end $$;
select public.approve_claim((select id from public.claims where item_id = '22222222-0000-0000-0000-000000000001'), now() + interval '1 day');
commit;
begin; set local role authenticated; select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-0000000000f2', true);
do $$ begin assert (select place_name = 'Poblenou Bar Sol' from public.item_pickup_points), 'approved taker cannot see point'; end $$;
commit;

\echo 'V1.2 SQL TESTS PASSED'
