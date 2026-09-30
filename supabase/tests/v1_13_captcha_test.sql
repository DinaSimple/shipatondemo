-- SPDX-License-Identifier: AGPL-3.0-only
-- Copyright (C) 2026 Dina Elokhova (DinaSimple) — Free to Take, https://freetotake.app
begin;
-- Public demo copy: real tile answers are not published, so the test seeds 180 synthetic tiles (10 labels × 18).
insert into public.captcha_tiles (id, label)
  select 'test' || l || '_' || n, 'label' || l from generate_series(1, 10) l, generate_series(1, 18) n
  on conflict do nothing;
do $$
declare r record; r2 record; s text; wrong int[];
begin
  assert (select count(*) from public.captcha_tiles) = 180;
  select * into r from public.captcha_new();
  assert array_length(r.tiles, 1) = 9, 'nine tiles';
  assert (select count(*) from unnest(r.tiles) t join public.captcha_tiles ct on ct.id = t where ct.label = r.label) between 3 and 4;
  -- correct answer passes once
  s := public.captcha_verify(r.id, (select array_agg((i - 1)::int) from unnest(r.tiles) with ordinality u(t, i) join public.captcha_tiles ct on ct.id = u.t where ct.label = r.label), 'pass1');
  assert s = 'ok', s;
  assert public.captcha_verify(r.id, '{}', 'pass2') = 'EXPIRED', 'one attempt per challenge';
  assert public.captcha_pass_valid('pass1');
  assert public.captcha_pass_consume('pass1');
  assert not public.captcha_pass_consume('pass1'), 'single use';
  assert not public.captcha_pass_valid('pass1');
  -- wrong answer fails, no pass
  select * into r2 from public.captcha_new();
  assert public.captcha_verify(r2.id, '{0}', 'pass3') in ('WRONG', 'ok');
  -- expired challenge
  select * into r2 from public.captcha_new();
  update public.captcha_challenges set expires_at = now() - interval '1 second' where id = r2.id;
  assert public.captcha_verify(r2.id, '{}', 'pass4') = 'EXPIRED';
  assert not public.captcha_pass_valid('pass4');
  assert not has_table_privilege('anon', 'public.captcha_tiles', 'select'), 'labels hidden';
  assert not has_function_privilege('anon', 'public.captcha_new()', 'execute');
end $$;
rollback;
\echo V1.13 SQL TESTS PASSED
