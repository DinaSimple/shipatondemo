-- SPDX-License-Identifier: AGPL-3.0-only
-- Copyright (C) 2026 Dina Yol (DinaSimple) — Free to Take, https://freetotake.app
-- v1.12 auth links: status, rate limit, expiry, single use, privileges.
begin;
insert into auth.users (id, email, encrypted_password) values
  ('00000000-0000-0000-0000-00000000a121', 'active@t.test', 'x'),
  ('00000000-0000-0000-0000-00000000a122', 'half@t.test', null);
do $$
declare s text; r record;
begin
  assert public.auth_email_status('Active@T.test ') = 'active';
  assert public.auth_email_status('half@t.test') = 'no_password';
  assert public.auth_email_status('nobody@t.test') = 'none';

  assert public.auth_link_issue('New@t.test', 'signup', 'h1') = 'ok';
  assert public.auth_link_issue('new@t.test', 'signup', 'h2') = 'RATE_LIMITED', 'one per minute';
  assert public.auth_link_issue('new@t.test', 'bogus', 'h3') = 'BAD_PURPOSE';

  select * into r from public.auth_link_check('h1');  assert r.status = 'ok' and r.purpose = 'signup';
  select * into r from public.auth_link_redeem('h1'); assert r.status = 'ok' and r.email = 'new@t.test';
  select * into r from public.auth_link_redeem('h1'); assert r.status = 'LINK_USED', 'single use';
  select * into r from public.auth_link_check('h1');  assert r.status = 'LINK_USED';
  select * into r from public.auth_link_redeem('nope'); assert r.status = 'LINK_INVALID';

  -- expired (5 min): back-date
  update public.auth_links set created_at = now() - interval '10 minutes' where token_hash = 'h1';
  assert public.auth_link_issue('new@t.test', 'recover', 'h4') = 'ok';
  update public.auth_links set expires_at = now() - interval '1 second' where token_hash = 'h4';
  select * into r from public.auth_link_redeem('h4'); assert r.status = 'LINK_EXPIRED' and r.email = 'new@t.test', 'expired → email for resend';
  select * into r from public.auth_link_check('h4'); assert r.status = 'LINK_EXPIRED';

  -- a newer link kills the older unused one of the same purpose
  update public.auth_links set created_at = now() - interval '2 minutes';
  assert public.auth_link_issue('x@t.test', 'recover', 'h5') = 'ok';
  update public.auth_links set created_at = now() - interval '2 minutes' where token_hash = 'h5';
  assert public.auth_link_issue('x@t.test', 'recover', 'h6') = 'ok';
  select * into r from public.auth_link_redeem('h5'); assert r.status = 'LINK_USED';
  select * into r from public.auth_link_redeem('h6'); assert r.status = 'ok';

  -- 5 per hour
  update public.auth_links set created_at = now() - interval '2 minutes';
  for i in 1..3 loop
    update public.auth_links set created_at = created_at - interval '2 minutes' where lower(email) = 'x@t.test';
    assert public.auth_link_issue('x@t.test', 'signup', 'hh' || i) = 'ok';
  end loop;
  update public.auth_links set created_at = created_at - interval '2 minutes' where lower(email) = 'x@t.test';
  assert public.auth_link_issue('x@t.test', 'signup', 'hh9') = 'RATE_LIMITED', '5 per hour';

  assert not has_table_privilege('anon', 'public.auth_links', 'select');
  assert not has_table_privilege('authenticated', 'public.auth_links', 'select');
  assert not has_function_privilege('anon', 'public.auth_link_redeem(text)', 'execute');
  assert not has_function_privilege('authenticated', 'public.auth_email_status(text)', 'execute');
  assert not has_function_privilege('authenticated', 'public.auth_email_config()', 'execute');
  assert (select count(*) from public.auth_email_config()) >= 0;
end $$;
rollback;
\echo V1.12 SQL TESTS PASSED
