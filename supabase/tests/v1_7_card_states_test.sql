-- SPDX-License-Identifier: AGPL-3.0-only
-- Copyright (C) 2026 Dina Yol (DinaSimple) — Free to Take, https://freetotake.app
-- v1.7 card states / removal / favorites
\set ON_ERROR_STOP on
insert into auth.users (id, email) values
  ('00000000-0000-0000-0000-0000000000a7', 'giver7@x.io'),
  ('00000000-0000-0000-0000-0000000000b7', 'taker7@x.io'),
  ('00000000-0000-0000-0000-0000000000c7', 'other7@x.io');
insert into public.items (id, giver_id, title, status) values
  ('77777777-0000-0000-0000-000000000001', '00000000-0000-0000-0000-0000000000a7', 'Lamp', 'available'),
  ('77777777-0000-0000-0000-000000000002', '00000000-0000-0000-0000-0000000000a7', 'Desk', 'reserved');

-- R-1.7-20 reserved hidden from public feed, no new requests
begin; set local role anon; select set_config('request.jwt.claim.sub', '', true);
do $$ begin assert not exists (select 1 from public.active_items where id = '77777777-0000-0000-0000-000000000002');
             assert exists (select 1 from public.active_items where id = '77777777-0000-0000-0000-000000000001'); end $$;
commit;
begin; set local role authenticated; select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-0000000000b7', true);
select public.t_expect_error($q$select public.submit_claim('77777777-0000-0000-0000-000000000002')$q$, 'ITEM_NOT_CLAIMABLE');
-- R-1.7-21 remove pending: leaves queue (cancelled), hidden, publisher notified
select public.submit_claim('77777777-0000-0000-0000-000000000001', 'hi');
select public.remove_my_request((select id from public.claims where taker_id = auth.uid()));
do $$ begin assert (select status = 'cancelled_by_taker' and hidden_by_taker from public.claims where taker_id = auth.uid()); end $$;
-- taker cannot read or forge notifications
do $$ begin assert (select count(*) from public.notifications) = 0; end $$;
select public.t_expect_error($q$insert into public.notifications (user_id, kind) values (auth.uid(), 'taker_left_queue')$q$, 'permission denied');
-- R-1.7-23 favorites: own rows only
insert into public.favorites (item_id) values ('77777777-0000-0000-0000-000000000001');
do $$ begin assert (select count(*) from public.favorites) = 1; end $$;
commit;

begin; set local role authenticated; select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-0000000000a7', true);
do $$ begin assert (select count(*) from public.notifications where kind = 'taker_left_queue') = 1; end $$;
-- removed requester can no longer be approved
select public.t_expect_error($q$select public.approve_claim((select id from public.claims where item_id = '77777777-0000-0000-0000-000000000001'), now() + interval '1 day')$q$, 'INVALID_TRANSITION');
do $$ begin assert (select count(*) from public.favorites) = 0; end $$;   -- not other users' favorites
commit;

-- R-1.7-22 rejected: removable (hidden only); other statuses/users refused
begin; set local role authenticated; select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-0000000000c7', true);
select public.submit_claim('77777777-0000-0000-0000-000000000001');
commit;
begin; set local role authenticated; select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-0000000000a7', true);
select public.reject_claim((select id from public.claims where taker_id = '00000000-0000-0000-0000-0000000000c7'));
select public.t_expect_error($q$select public.remove_my_request((select id from public.claims where taker_id = '00000000-0000-0000-0000-0000000000c7'))$q$, 'NOT_CLAIM_OWNER');
commit;
begin; set local role authenticated; select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-0000000000c7', true);
select public.remove_my_request((select id from public.claims where taker_id = auth.uid()));
do $$ begin assert (select status = 'rejected' and hidden_by_taker from public.claims where taker_id = auth.uid()); end $$;
commit;
\echo 'V1.7 SQL TESTS PASSED'

-- R-1.7.1-20 rejected requester cannot request again (Q18)
begin; set local role authenticated; select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-0000000000c7', true);
select public.t_expect_error($q$select public.submit_claim('77777777-0000-0000-0000-000000000001')$q$, 'ALREADY_REJECTED');
commit;
-- R-1.7.1-21 no cancel once pickup time has come (Q19)
insert into public.items (id, giver_id, title, status) values ('77777777-0000-0000-0000-000000000003', '00000000-0000-0000-0000-0000000000a7', 'Past', 'reserved');
insert into public.claims (id, item_id, taker_id, status, pickup_at) values
  ('77777777-0000-0000-0000-0000000000f1', '77777777-0000-0000-0000-000000000003', '00000000-0000-0000-0000-0000000000b7', 'approved', now() - interval '1 minute');
begin; set local role authenticated; select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-0000000000b7', true);
select public.t_expect_error($q$select public.remove_my_request('77777777-0000-0000-0000-0000000000f1')$q$, 'PICKUP_PASSED');
commit;
\echo 'V1.7.1 SQL TESTS PASSED'
