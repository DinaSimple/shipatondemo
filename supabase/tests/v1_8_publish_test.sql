-- SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
-- Copyright (C) 2026 Dina Elokhova (DinaSimple) — Free to Take, https://freetotake.app
-- v1.8 publish
\set ON_ERROR_STOP on
insert into auth.users (id, email) values ('00000000-0000-0000-0000-0000000000a8', 'giver8@x.io'), ('00000000-0000-0000-0000-0000000000b8', 'guest8@x.io');

begin; set local role anon; select set_config('request.jwt.claim.sub', '', true);
-- R-1.8-20 guests cannot publish
select public.t_expect_error($q$select public.publish_item('T', '', 'books', 41.4, 2.2, 'P', 'A', 'Barcelona', '08005', current_date + 2, '16:00', '17:30')$q$, 'permission denied');
commit;

begin; set local role authenticated; select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-0000000000a8', true);
-- R-1.8-21 validation
select public.t_expect_error($q$select public.publish_item(' ', '', 'books', 41.4, 2.2, 'P', 'A', 'Barcelona', '08005', current_date + 2, '16:00', '17:30')$q$, 'TITLE_INVALID');
select public.t_expect_error($q$select public.publish_item('T', repeat('x', 1001), 'books', 41.4, 2.2, 'P', 'A', 'Barcelona', '08005', current_date + 2, '16:00', '17:30')$q$, 'DESCRIPTION_TOO_LONG');
select public.t_expect_error($q$select public.publish_item('T', '', 'books', 41.4, 2.2, 'P', 'A', 'Barcelona', '08005', current_date + 2, '17:30', '16:00')$q$, 'TIME_RANGE_INVALID');
select public.t_expect_error($q$select public.publish_item('T', '', 'books', 41.4, 2.2, 'P', 'A', 'Barcelona', '08005', current_date - 1, '16:00', '17:30')$q$, 'TIME_IN_PAST');
select public.t_expect_error($q$select public.publish_item('T', '', 'toys', 41.4, 2.2, 'P', 'A', 'Barcelona', '08005', current_date + 2, '16:00', '17:30', array['00000000-0000-0000-0000-0000000000a8/c.jpg'])$q$, 'items_category_chk');
-- R-1.8.1-20 at least one photo (answer Q23)
select public.t_expect_error($q$select public.publish_item('T', '', 'books', 41.4, 2.2, 'P', 'A', 'Barcelona', '08005', current_date + 2, '16:00', '17:30')$q$, 'PHOTO_REQUIRED');
select public.t_expect_error($q$select public.publish_item('T', '', 'books', 41.4, 2.2, 'P', 'A', 'Barcelona', '08005', current_date + 2, '16:00', '17:30', array['someoneelse/x.jpg'])$q$, 'PHOTO_NOT_OWNED');
select public.t_expect_error($q$select public.publish_item('T', '', 'books', 41.4, 2.2, 'P', 'A', 'Barcelona', '08005', current_date + 2, '16:00', '17:30', array['00000000-0000-0000-0000-0000000000a8/1.jpg','00000000-0000-0000-0000-0000000000a8/2.jpg','00000000-0000-0000-0000-0000000000a8/3.jpg','00000000-0000-0000-0000-0000000000a8/4.jpg'])$q$, 'TOO_MANY_PHOTOS');
-- R-1.8-22 publish: live, public area/approx point, private exact point + details, 30-min slots, 14-day lifetime
select public.publish_item('Free toys', 'desc', 'kids_toys', 41.4036, 2.2011, 'Poblenou', 'Poblenou, 19', 'Barcelona', '08005',
                           current_date + 2, '16:00', '17:30', array['00000000-0000-0000-0000-0000000000a8/p/1.jpg'], 'Ring twice');
do $$ declare v public.items; begin
  select * into v from public.items where title = 'Free toys';
  assert v.status = 'available' and v.approx_lat = 41.40 and v.area_city = 'Barcelona';
  assert v.expires_at between now() + interval '13 days' and now() + interval '15 days';
  assert (select array_agg(slot_time order by slot_time)::text from public.item_availability where item_id = v.id) = '{16:00:00,16:30:00,17:00:00}';
  assert (select notes = 'Ring twice' from public.item_pickup_points where item_id = v.id);
  -- 1.9.1: no apartment/entrance/floor columns at all
  assert not exists (select 1 from information_schema.columns where table_name = 'item_pickup_points' and column_name in ('apartment', 'entrance', 'floor'));
end $$;
commit;

-- R-1.8-23 another user sees the listing but not the private meetup details
begin; set local role authenticated; select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-0000000000b8', true);
do $$ begin
  assert exists (select 1 from public.active_items where title = 'Free toys');
  assert not exists (select 1 from public.item_pickup_points p join public.items i on i.id = p.item_id where i.title = 'Free toys');
end $$;
commit;
-- R-1.8.1-21 cleanup: only photos of listings expired > 14 days ago; only service_role
insert into public.items (id, giver_id, title, photo_paths, status, expires_at) values
  ('88888888-0000-0000-0000-000000000001', '00000000-0000-0000-0000-0000000000a8', 'Old',    array['00000000-0000-0000-0000-0000000000a8/o/1.jpg'], 'available', now() - interval '15 days'),
  ('88888888-0000-0000-0000-000000000002', '00000000-0000-0000-0000-0000000000a8', 'Recent', array['00000000-0000-0000-0000-0000000000a8/r/1.jpg'], 'available', now() - interval '13 days');
begin; set local role authenticated; select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-0000000000a8', true);
select public.t_expect_error($q$select * from public.expired_photo_batch()$q$, 'permission denied');
select public.t_expect_error($q$select public.cleanup_token_ok('x')$q$, 'permission denied');
commit;
begin; set local role service_role;
do $$ begin
  assert (select array_agg(item_id) from public.expired_photo_batch()) = array['88888888-0000-0000-0000-000000000001'::uuid];
  assert public.mark_photos_deleted(array['88888888-0000-0000-0000-000000000001'::uuid, '88888888-0000-0000-0000-000000000002'::uuid]) = 1;
  assert not public.cleanup_token_ok('wrong');
end $$;
commit;
select set_config('ftt.tok', (select decrypted_secret from vault.decrypted_secrets where name = 'photo_cleanup_token'), false);
begin; set local role service_role;
do $$ begin assert public.cleanup_token_ok(current_setting('ftt.tok')); end $$;
commit;
do $$ begin
  assert (select photo_paths = '{}' and photos_deleted_at is not null from public.items where title = 'Old');
  assert (select photo_paths <> '{}' from public.items where title = 'Recent');
  assert exists (select 1 from cron.jobs where name = 'photo-cleanup-daily');
end $$;
\echo 'V1.8 SQL TESTS PASSED'
