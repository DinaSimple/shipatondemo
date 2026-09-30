-- SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
-- Copyright (C) 2026 Dina Yol (DinaSimple) — Free to Take, https://freetotake.app
-- v1.6 request submission
\set ON_ERROR_STOP on
insert into auth.users (id, email) values
  ('00000000-0000-0000-0000-0000000000a6', 'giver6@x.io'),
  ('00000000-0000-0000-0000-0000000000b6', 'taker6@x.io');
insert into public.items (id, giver_id, title) values ('66666666-0000-0000-0000-000000000001', '00000000-0000-0000-0000-0000000000a6', 'Toddler toys');
insert into public.item_availability values
  ('66666666-0000-0000-0000-000000000001', current_date + 3, '09:00'),
  ('66666666-0000-0000-0000-000000000001', current_date + 3, '09:30'),
  ('66666666-0000-0000-0000-000000000001', current_date - 1, '09:00');

-- R-1.6-20 guests can read availability of public listings
begin; set local role anon; select set_config('request.jwt.claim.sub', '', true);
do $$ begin assert (select count(*) from public.item_availability where item_id = '66666666-0000-0000-0000-000000000001') = 3; end $$;
select public.t_expect_error($q$insert into public.item_availability values ('66666666-0000-0000-0000-000000000001', current_date + 5, '10:00')$q$, 'row-level security');
commit;

begin; set local role authenticated; select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-0000000000b6', true);
-- R-1.6-21 slot required / must be offered / not past
select public.t_expect_error($q$select public.submit_claim('66666666-0000-0000-0000-000000000001', 'hi')$q$, 'SLOT_REQUIRED');
select public.t_expect_error($q$select public.submit_claim('66666666-0000-0000-0000-000000000001', 'hi', current_date + 3, '11:00')$q$, 'SLOT_NOT_OFFERED');
select public.t_expect_error($q$select public.submit_claim('66666666-0000-0000-0000-000000000001', 'hi', current_date - 1, '09:00')$q$, 'SLOT_NOT_OFFERED');
-- R-1.6-22 note ≤ 1000
select public.t_expect_error($q$select public.submit_claim('66666666-0000-0000-0000-000000000001', repeat('x', 1001), current_date + 3, '09:00')$q$, 'NOTE_TOO_LONG');
-- R-1.6-23 valid request stored with slot + note, pending
select public.submit_claim('66666666-0000-0000-0000-000000000001', 'I have 2 kids', current_date + 3, '09:30');
do $$ begin assert (select status = 'pending_approval' and message = 'I have 2 kids' and requested_time = '09:30' from public.claims where taker_id = auth.uid()); end $$;
-- R-1.6-24 exact pickup point still private while pending (v1.2 rule holds)
do $$ begin assert (select count(*) from public.item_pickup_points) = 0; end $$;
commit;

-- R-1.6-25 approval uses the requested slot as pickup time
begin; set local role authenticated; select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-0000000000a6', true);
select public.approve_claim((select id from public.claims where item_id = '66666666-0000-0000-0000-000000000001'));
do $$ begin assert (select pickup_at = ((current_date + 3) + time '09:30') at time zone 'Europe/Madrid' and status = 'approved' from public.claims where item_id = '66666666-0000-0000-0000-000000000001'); end $$;
commit;
\echo 'V1.6 SQL TESTS PASSED'
