-- SPDX-License-Identifier: AGPL-3.0-only
-- Copyright (C) 2026 Dina Elokhova (DinaSimple) — Free to Take, https://freetotake.app
-- v1.0 server-side rule tests. Run: supabase/tests/run_local.sh
-- Every block maps to a regression ID in docs/REGRESSION.md.
\set ON_ERROR_STOP on

create or replace function public.t_expect_error(p_sql text, p_msg text) returns void
language plpgsql as $$
begin
  execute p_sql;
  raise exception 'EXPECTED ERROR % BUT SUCCEEDED: %', p_msg, p_sql;
exception when others then
  if sqlerrm not like '%' || p_msg || '%' then
    raise exception 'EXPECTED % GOT %', p_msg, sqlerrm;
  end if;
end $$;
grant execute on function public.t_expect_error(text, text) to anon, authenticated;

-- users: G = giver, A/B = takers
insert into auth.users (id, email) values
  ('00000000-0000-0000-0000-00000000000a', 'giver@x.io'),
  ('00000000-0000-0000-0000-00000000000b', 'alice@x.io'),
  ('00000000-0000-0000-0000-00000000000c', 'bob@x.io');
\set G '''00000000-0000-0000-0000-00000000000a'''
\set A '''00000000-0000-0000-0000-00000000000b'''
\set B '''00000000-0000-0000-0000-00000000000c'''

-- R-1.0-19 profile auto-created on sign-up
do $$ begin assert (select count(*) from public.profiles) = 3, 'profiles not auto-created'; end $$;

-- giver posts two items
begin; set local role authenticated; select set_config('request.jwt.claim.sub', :G, true);
insert into public.items (id, giver_id, title) values
  ('11111111-0000-0000-0000-000000000001', :G, 'Chair'),
  ('11111111-0000-0000-0000-000000000002', :G, 'Lamp');
commit;

-- R-1.0-01 guests can browse without auth
begin; set local role anon; select set_config('request.jwt.claim.sub', '', true);
do $$ begin assert (select count(*) from public.items) = 2, 'anon cannot browse'; end $$;
-- R-1.0-02 guests cannot act
select public.t_expect_error($q$select public.submit_claim('11111111-0000-0000-0000-000000000001')$q$, 'permission denied');
commit;

-- R-1.0-04 giver cannot claim own item
begin; set local role authenticated; select set_config('request.jwt.claim.sub', :G, true);
select public.t_expect_error($q$select public.submit_claim('11111111-0000-0000-0000-000000000001')$q$, 'CANNOT_CLAIM_OWN_ITEM');
-- R-1.0-17 status is not client-writable
select public.t_expect_error($q$update public.items set status = 'given' where id = '11111111-0000-0000-0000-000000000001'$q$, 'permission denied');
commit;

-- R-1.0-03 / 05 A and B submit; A cannot submit twice
begin; set local role authenticated; select set_config('request.jwt.claim.sub', :A, true);
select public.submit_claim('11111111-0000-0000-0000-000000000001', 'hi');
select public.t_expect_error($q$select public.submit_claim('11111111-0000-0000-0000-000000000001')$q$, 'ALREADY_HAS_ACTIVE_CLAIM');
-- R-1.0-17 claims not directly insertable
select public.t_expect_error($q$insert into public.claims (item_id, taker_id) values ('11111111-0000-0000-0000-000000000001', auth.uid())$q$, 'permission denied');
commit;
begin; set local role authenticated; select set_config('request.jwt.claim.sub', :B, true);
select public.submit_claim('11111111-0000-0000-0000-000000000001');
-- R-1.0-18 B cannot see A's claim
do $$ begin assert (select count(*) from public.claims) = 1, 'taker sees foreign claims'; end $$;
commit;

-- R-1.0-06 pending status after submit
do $$ begin assert (select count(*) from public.claims where status = 'pending_approval') = 2; end $$;

-- R-1.0-09 approve A with pickup in 9h → item reserved; only one approval
begin; set local role authenticated; select set_config('request.jwt.claim.sub', :G, true);
do $$ begin assert (select count(*) from public.claims) = 2, 'giver cannot see incoming claims'; end $$;
select public.approve_claim((select id from public.claims where taker_id = '00000000-0000-0000-0000-00000000000b'), now() + interval '9 hours');
-- 1.10 (0.13): approving one recipient auto-rejects the others → a second approval is impossible
select public.t_expect_error($q$select public.approve_claim((select id from public.claims where taker_id = '00000000-0000-0000-0000-00000000000c'), now() + interval '9 hours')$q$, 'INVALID_TRANSITION');
commit;
do $$ begin assert (select status from public.items where title = 'Chair') = 'reserved'; end $$;

-- R-1.0-08 non-owner cannot approve
begin; set local role authenticated; select set_config('request.jwt.claim.sub', :B, true);
select public.t_expect_error($q$select public.reject_claim((select id from public.claims where taker_id = auth.uid()))$q$, 'NOT_ITEM_OWNER');
commit;

-- R-1.0-11 A cancels > 8h before pickup → slot released, item available (B was auto-rejected at approval, 1.10)
begin; set local role authenticated; select set_config('request.jwt.claim.sub', :A, true);
select public.cancel_claim((select id from public.claims where taker_id = auth.uid()));
commit;
do $$ begin
  assert (select status from public.items where title = 'Chair') = 'available', 'slot not released';
  assert (select status from public.claims where taker_id = '00000000-0000-0000-0000-00000000000b') = 'cancelled_by_taker';
  assert (select status from public.claims where taker_id = '00000000-0000-0000-0000-00000000000c') = 'rejected';
end $$;
-- test setup for R-1.0-12: B requests again (simulated directly; rejected requesters can't re-request via RPC)
update public.claims set status = 'pending_approval' where taker_id = '00000000-0000-0000-0000-00000000000c';

-- R-1.0-12 approve B with pickup in 7h, B cancels → late, slot NOT released
begin; set local role authenticated; select set_config('request.jwt.claim.sub', :G, true);
select public.approve_claim((select id from public.claims where taker_id = '00000000-0000-0000-0000-00000000000c'), now() + interval '7 hours');
commit;
begin; set local role authenticated; select set_config('request.jwt.claim.sub', :B, true);
select public.cancel_claim((select id from public.claims where taker_id = auth.uid()));
commit;
do $$ begin
  assert (select status from public.items where title = 'Chair') = 'awaiting_giver_decision', 'late cancel released slot';
  assert (select status from public.claims where taker_id = '00000000-0000-0000-0000-00000000000c') = 'cancelled_late';
end $$;
-- R-1.0-01 hidden from public feed while awaiting giver decision
begin; set local role anon; select set_config('request.jwt.claim.sub', '', true);
do $$ begin assert (select count(*) from public.items) = 1; end $$;
commit;

-- R-1.0-13 giver reopens; A re-requests, approved, completed → GIVEN
begin; set local role authenticated; select set_config('request.jwt.claim.sub', :G, true);
select public.reopen_item('11111111-0000-0000-0000-000000000001');
commit;
begin; set local role authenticated; select set_config('request.jwt.claim.sub', :A, true);
select public.submit_claim('11111111-0000-0000-0000-000000000001');
commit;
begin; set local role authenticated; select set_config('request.jwt.claim.sub', :G, true);
select public.t_expect_error($q$select public.approve_claim((select id from public.claims where status = 'pending_approval'), now() - interval '1 hour')$q$, 'PICKUP_TIME_IN_PAST');
select public.approve_claim((select id from public.claims where status = 'pending_approval'), now() + interval '1 day');
select public.complete_claim((select id from public.claims where status = 'approved'));
commit;
do $$ begin
  assert (select status from public.items where title = 'Chair') = 'given';
  assert (select count(*) from public.claims where status = 'completed') = 1;
end $$;

-- R-1.0-16 withdraw closes active requests
begin; set local role authenticated; select set_config('request.jwt.claim.sub', :B, true);
select public.submit_claim('11111111-0000-0000-0000-000000000002');
-- R-1.0-10 pending cancel always allowed … re-submit after cancel is allowed
select public.cancel_claim((select id from public.claims where item_id = '11111111-0000-0000-0000-000000000002'));
select public.submit_claim('11111111-0000-0000-0000-000000000002');
commit;
begin; set local role authenticated; select set_config('request.jwt.claim.sub', :G, true);
select public.withdraw_item('11111111-0000-0000-0000-000000000002');
commit;
do $$ begin
  assert (select status from public.items where title = 'Lamp') = 'withdrawn';
  assert (select count(*) from public.claims where item_id = '11111111-0000-0000-0000-000000000002' and status = 'closed') = 1;
end $$;

\echo 'ALL SQL TESTS PASSED'
