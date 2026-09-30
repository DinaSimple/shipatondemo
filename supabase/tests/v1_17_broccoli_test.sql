-- SPDX-License-Identifier: AGPL-3.0-only
-- Copyright (C) 2026 Dina Yol (DinaSimple) — Free to Take, https://freetotake.app
-- v1.17 broccoli: start 20, request −1, finished pickup −1, post +1, confirmed handover +1, blocked at 0
\set ON_ERROR_STOP on
insert into auth.users (id, email) values
  ('00000000-0000-0000-0000-00000000a171', 'g17@x.io'),
  ('00000000-0000-0000-0000-00000000b171', 't17@x.io');

-- R-1.17-01 new account starts with 20; posting gives +1
begin; set local role authenticated; select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000a171', true);
do $$ begin assert public.my_broccoli() = 20, 'start with 20'; end $$;
select public.publish_item('Lamp17', '', 'furniture', 41.4, 2.2, 'P', 'A', 'Barcelona', '08005', current_date + 3, '10:00', '11:00', array['00000000-0000-0000-0000-00000000a171/l.jpg']);
do $$ begin assert public.my_broccoli() = 21, 'post +1'; end $$;
select public.t_expect_error($q$insert into public.broccoli_ledger (user_id, delta, reason, ref_id) values (auth.uid(), 1, 'post', gen_random_uuid())$q$, 'permission denied');
commit;

-- R-1.17-02 sending a request costs 1
begin; set local role authenticated; select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000b171', true);
select public.submit_claim((select id from public.items where title = 'Lamp17'), 'hi', current_date + 3, '10:00');
do $$ begin assert public.my_broccoli() = 19, 'request −1'; end $$;
commit;

-- R-1.17-03 finished pickup: collector −1; giver gets the confirm prompt, +1 only when confirmed
begin; set local role authenticated; select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000a171', true);
select public.approve_claim((select c.id from public.claims c join public.items i on i.id = c.item_id where i.title = 'Lamp17'));
commit;
update public.claims set pickup_at = now() - interval '1 minute'
  where item_id = (select id from public.items where title = 'Lamp17') and status = 'approved';
begin; set local role authenticated; select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000a171', true);
do $$ begin
  assert (select count(*) from public.pending_handovers()) = 1, 'giver asked to confirm';
  assert public.my_broccoli() = 21, 'no reward before confirming';
  assert public.confirm_handover((select id from public.items where title = 'Lamp17'), true) = 22, 'collected +1';
  assert public.confirm_handover((select id from public.items where title = 'Lamp17'), true) = 22, 'second confirm is a no-op';
  assert (select count(*) from public.pending_handovers()) = 0, 'prompt gone';
end $$;
commit;
begin; set local role authenticated; select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000b171', true);
do $$ begin assert public.my_broccoli() = 18, 'finished pickup −1'; end $$;
select public.t_expect_error($q$select public.confirm_handover((select id from public.items where title = 'Lamp17'), true)$q$, 'NOT_ALLOWED');
commit;

-- R-1.17-04 at 0 a request is refused
insert into public.broccoli_ledger (user_id, delta, reason, ref_id)
  select '00000000-0000-0000-0000-00000000b171', -1, 'request', gen_random_uuid() from generate_series(1, 18);
begin; set local role authenticated; select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000a171', true);
select public.publish_item('Book17', '', 'books', 41.4, 2.2, 'P', 'A', 'Barcelona', '08005', current_date + 3, '10:00', '11:00', array['00000000-0000-0000-0000-00000000a171/b.jpg']);
commit;
begin; set local role authenticated; select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000b171', true);
do $$ begin assert public.my_broccoli() = 0, 'empty'; end $$;
select public.t_expect_error($q$select public.submit_claim((select id from public.items where title = 'Book17'), 'hi', current_date + 3, '10:00')$q$, 'NO_BROCCOLI');
commit;
\echo 'V1.17 SQL TESTS PASSED'
