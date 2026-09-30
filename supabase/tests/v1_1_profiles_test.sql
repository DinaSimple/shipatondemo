-- SPDX-License-Identifier: AGPL-3.0-only
-- Copyright (C) 2026 Dina Yol (DinaSimple) — Free to Take, https://freetotake.app
-- v1.1 profile naming tests
\set ON_ERROR_STOP on

insert into auth.users (id, email, raw_user_meta_data) values
  ('00000000-0000-0000-0000-0000000000d1', 'dana@x.io', '{}'::jsonb),
  ('00000000-0000-0000-0000-0000000000d2', 'eve@x.io', '{"public_name":"Eve"}'::jsonb);

-- R-1.1-10 every profile gets a unique generated nickname; email prefix never used
do $$ begin
  assert (select count(*) from public.profiles where nickname is null) = 0, 'missing nickname';
  assert (select count(distinct nickname) = count(*) from public.profiles), 'nickname not unique';
  assert (select nickname ~ '^[A-Z][a-z]+[A-Z][a-z]+[0-9]{4}$' from public.profiles where id = '00000000-0000-0000-0000-0000000000d1'), 'bad nickname format';
  assert (select public_name is null from public.profiles where id = '00000000-0000-0000-0000-0000000000d1'), 'public_name should default to null';
  assert (select public_name = 'Eve' from public.profiles where id = '00000000-0000-0000-0000-0000000000d2'), 'public_name from sign-up metadata';
  assert not exists (select 1 from public.profiles where public_name in ('dana', 'eve')), 'email prefix leaked';
end $$;

-- R-1.1-11 user can set own public name but not nickname
begin; set local role authenticated; select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-0000000000d1', true);
update public.profiles set public_name = 'Dana' where id = auth.uid();
select public.t_expect_error($q$update public.profiles set nickname = 'Hacker0001' where id = auth.uid()$q$, 'permission denied');
commit;
do $$ begin assert (select public_name = 'Dana' from public.profiles where id = '00000000-0000-0000-0000-0000000000d1'); end $$;

\echo 'V1.1 SQL TESTS PASSED'
