-- SPDX-License-Identifier: AGPL-3.0-only
-- Copyright (C) 2026 Dina Yol (DinaSimple) — Free to Take, https://freetotake.app
-- NOTE (1.10): written for the v1.0 queue behaviour; since 0.13 approving auto-rejects other requests (steps 14/19 differ).
-- Use supabase/tests/run_local.sh for the up-to-date suite.
-- Live smoke test for the v1.0 rules on a real Supabase project.
-- Paste into SQL Editor and run. It ends by raising
--   "ALL N STEPS PASSED (rolled back)"
-- so every insert (test users, items, claims) is rolled back. Any other error = failure.
do $t$
declare
  G constant text := '00000000-0000-0000-0000-00000000000a';
  A constant text := '00000000-0000-0000-0000-00000000000b';
  B constant text := '00000000-0000-0000-0000-00000000000c';
  CHAIR constant text := '11111111-0000-0000-0000-000000000001';
  LAMP  constant text := '11111111-0000-0000-0000-000000000002';
  s record; ok boolean; n int := 0;
begin
  insert into auth.users (id, email, aud, role) values
    (G::uuid, 'ftt-test-giver@example.invalid', 'authenticated', 'authenticated'),
    (A::uuid, 'ftt-test-alice@example.invalid', 'authenticated', 'authenticated'),
    (B::uuid, 'ftt-test-bob@example.invalid',   'authenticated', 'authenticated');

  for s in
    select * from (values
      (1,  'assert','postgres', null, format($$select count(*)=3 from public.profiles where id in (%L,%L,%L)$$, G,A,B), null),
      (2,  'run',   'authenticated', G, format($$insert into public.items (id, giver_id, title) values (%L,%L,'Chair'),(%L,%L,'Lamp')$$, CHAIR,G,LAMP,G), null),
      (3,  'assert','anon', '', format($$select count(*)=2 from public.items where id in (%L,%L)$$, CHAIR, LAMP), null),
      (4,  'err',   'anon', '', format($$select public.submit_claim(%L)$$, CHAIR), 'permission denied'),
      (5,  'err',   'authenticated', G, format($$select public.submit_claim(%L)$$, CHAIR), 'CANNOT_CLAIM_OWN_ITEM'),
      (6,  'err',   'authenticated', G, format($$update public.items set status='given' where id=%L$$, CHAIR), 'permission denied'),
      (7,  'run',   'authenticated', A, format($$select public.submit_claim(%L,'hi')$$, CHAIR), null),
      (8,  'err',   'authenticated', A, format($$select public.submit_claim(%L)$$, CHAIR), 'ALREADY_HAS_ACTIVE_CLAIM'),
      (9,  'err',   'authenticated', A, format($$insert into public.claims (item_id,taker_id) values (%L,%L)$$, CHAIR, A), 'permission denied'),
      (10, 'run',   'authenticated', B, format($$select public.submit_claim(%L)$$, CHAIR), null),
      (11, 'assert','authenticated', B, format($$select count(*)=1 from public.claims where item_id=%L$$, CHAIR), null),
      (12, 'assert','authenticated', G, format($$select count(*)=2 and bool_and(status='pending_approval') from public.claims where item_id=%L$$, CHAIR), null),
      (13, 'run',   'authenticated', G, format($$select public.approve_claim((select id from public.claims where taker_id=%L and item_id=%L), now()+interval '9 hours')$$, A, CHAIR), null),
      (14, 'err',   'authenticated', G, format($$select public.approve_claim((select id from public.claims where taker_id=%L and item_id=%L), now()+interval '9 hours')$$, B, CHAIR), 'ITEM_ALREADY_RESERVED'),
      (15, 'assert','postgres', null, format($$select status='reserved' from public.items where id=%L$$, CHAIR), null),
      (16, 'err',   'authenticated', B, format($$select public.reject_claim((select id from public.claims where taker_id=%L and item_id=%L))$$, B, CHAIR), 'NOT_ITEM_OWNER'),
      (17, 'run',   'authenticated', A, format($$select public.cancel_claim((select id from public.claims where taker_id=%L and item_id=%L))$$, A, CHAIR), null),
      (18, 'assert','postgres', null, format($$select (select status from public.items where id=%L)='available'
                                              and (select status from public.claims where taker_id=%L and item_id=%L)='cancelled_by_taker'
                                              and (select status from public.claims where taker_id=%L and item_id=%L)='pending_approval'$$, CHAIR, A, CHAIR, B, CHAIR), null),
      (19, 'run',   'authenticated', G, format($$select public.approve_claim((select id from public.claims where taker_id=%L and item_id=%L), now()+interval '7 hours')$$, B, CHAIR), null),
      (20, 'run',   'authenticated', B, format($$select public.cancel_claim((select id from public.claims where taker_id=%L and item_id=%L))$$, B, CHAIR), null),
      (21, 'assert','postgres', null, format($$select (select status from public.items where id=%L)='awaiting_giver_decision'
                                              and (select status from public.claims where taker_id=%L and item_id=%L)='cancelled_late'$$, CHAIR, B, CHAIR), null),
      (22, 'assert','anon', '', format($$select count(*)=0 from public.items where id=%L$$, CHAIR), null),
      (23, 'run',   'authenticated', G, format($$select public.reopen_item(%L)$$, CHAIR), null),
      (24, 'run',   'authenticated', A, format($$select public.submit_claim(%L)$$, CHAIR), null),
      (25, 'err',   'authenticated', G, format($$select public.approve_claim((select id from public.claims where item_id=%L and status='pending_approval'), now()-interval '1 hour')$$, CHAIR), 'PICKUP_TIME_IN_PAST'),
      (26, 'run',   'authenticated', G, format($$select public.approve_claim((select id from public.claims where item_id=%L and status='pending_approval'), now()+interval '1 day')$$, CHAIR), null),
      (27, 'run',   'authenticated', G, format($$select public.complete_claim((select id from public.claims where item_id=%L and status='approved'))$$, CHAIR), null),
      (28, 'assert','postgres', null, format($$select (select status from public.items where id=%L)='given'
                                              and (select count(*) from public.claims where item_id=%L and status='completed')=1$$, CHAIR, CHAIR), null),
      (29, 'run',   'authenticated', B, format($$select public.submit_claim(%L)$$, LAMP), null),
      (30, 'run',   'authenticated', B, format($$select public.cancel_claim((select id from public.claims where item_id=%L and taker_id=%L and status='pending_approval'))$$, LAMP, B), null),
      (31, 'run',   'authenticated', B, format($$select public.submit_claim(%L)$$, LAMP), null),
      (32, 'run',   'authenticated', G, format($$select public.withdraw_item(%L)$$, LAMP), null),
      (33, 'assert','postgres', null, format($$select (select status from public.items where id=%L)='withdrawn'
                                              and (select count(*) from public.claims where item_id=%L and status='closed')=1$$, LAMP, LAMP), null)
    ) v(id, kind, rl, uid, q, expect)
    order by id
  loop
    execute 'reset role';
    perform set_config('request.jwt.claim.sub', coalesce(s.uid, ''), true);
    perform set_config('request.jwt.claims', case when s.uid is null or s.uid = '' then '' else json_build_object('sub', s.uid, 'role', s.rl)::text end, true);
    if s.rl <> 'postgres' then execute format('set local role %I', s.rl); end if;

    if s.kind = 'run' then
      execute s.q;
    elsif s.kind = 'assert' then
      execute s.q into ok;
      if ok is distinct from true then raise exception 'STEP % ASSERT FAILED: %', s.id, s.q; end if;
    else
      begin
        execute s.q;
        raise exception 'STEP % EXPECTED % BUT SUCCEEDED', s.id, s.expect using errcode = 'P0099';
      exception
        when sqlstate 'P0099' then raise;
        when others then
          if sqlerrm not like '%' || s.expect || '%' then
            raise exception 'STEP % EXPECTED % GOT %', s.id, s.expect, sqlerrm;
          end if;
      end;
    end if;
    n := n + 1;
  end loop;
  execute 'reset role';
  raise exception 'ALL % STEPS PASSED (rolled back)', n;
end $t$;
