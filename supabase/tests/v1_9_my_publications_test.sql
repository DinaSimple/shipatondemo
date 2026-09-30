-- SPDX-License-Identifier: AGPL-3.0-only
-- Copyright (C) 2026 Dina Elokhova (DinaSimple) — Free to Take, https://freetotake.app
-- v1.9 My Publications
\set ON_ERROR_STOP on
insert into auth.users (id, email) values
  ('00000000-0000-0000-0000-0000000000a9', 'giver9@x.io'),
  ('00000000-0000-0000-0000-0000000000b9', 'taker9@x.io'),
  ('00000000-0000-0000-0000-0000000000c9', 'other9@x.io');

begin; set local role authenticated; select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-0000000000a9', true);
select public.publish_item('Lamp9', '', 'furniture', 41.4, 2.2, 'P', 'A', 'Barcelona', '08005', current_date + 3, '16:00', '17:30', array['00000000-0000-0000-0000-0000000000a9/l.jpg']);
select public.publish_item('Soon9', '', 'food', 41.4, 2.2, 'P', 'A', 'Barcelona', '08005', current_date + 3, '10:00', '11:00', array['00000000-0000-0000-0000-0000000000a9/s.jpg']);
-- R-1.9-20 overview: schedule + 0 pending
do $$ declare r record; begin
  select * into r from public.my_publications where title = 'Lamp9';
  assert r.pending_requests = 0 and r.schedule_from = '16:00' and r.schedule_to = '17:30' and r.schedule_date = current_date + 3;
  assert r.collector_nickname is null;
end $$;
commit;

begin; set local role authenticated; select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-0000000000b9', true);
select public.submit_claim((select id from public.items where title = 'Lamp9'), 'please', current_date + 3, '16:30');
-- others never see someone's publications list
do $$ begin assert (select count(*) from public.my_publications) = 0; end $$;
commit;
begin; set local role authenticated; select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-0000000000c9', true);
select public.submit_claim((select id from public.items where title = 'Lamp9'), null, current_date + 3, '17:00');
commit;

begin; set local role authenticated; select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-0000000000a9', true);
-- R-1.9-21 action needed: pending count; review list with nicknames
do $$ begin
  assert (select pending_requests from public.my_publications where title = 'Lamp9') = 2;
  assert (select count(*) from public.item_requests r join public.items i on i.id = r.item_id where i.title = 'Lamp9' and r.taker_nickname is not null) = 2;
end $$;
-- R-1.9-22 edit before assignment: fields, schedule replaced, photos kept rules
select public.update_item((select id from public.items where title = 'Lamp9'), 'Desk lamp9', 'works', 'furniture', 41.4, 2.2, 'P', 'A', 'Barcelona', '08005',
                          current_date + 4, '09:00', '10:00', array['00000000-0000-0000-0000-0000000000a9/l.jpg'], 'bell');
do $$ begin
  assert (select schedule_date = current_date + 4 and schedule_from = '09:00' and schedule_to = '10:00' from public.my_publications where title = 'Desk lamp9');
  assert (select notes = 'bell' from public.item_pickup_points p join public.items i on i.id = p.item_id where i.title = 'Desk lamp9');
end $$;
select public.t_expect_error($q$select public.update_item((select id from public.items where title = 'Desk lamp9'), 'X', '', 'furniture', 41.4, 2.2, 'P', 'A', 'B', '1', current_date + 4, '09:00', '10:00', '{}')$q$, 'PHOTO_REQUIRED');
-- approve the first requester (legacy slot kept on the request) → pending collection
select public.approve_claim((select id from public.claims where taker_id = '00000000-0000-0000-0000-0000000000b9'));
do $$ begin assert (select collector_nickname is not null and collector_pickup_at is not null from public.my_publications where title = 'Desk lamp9'); end $$;
select public.t_expect_error($q$select public.update_item((select id from public.items where title = 'Desk lamp9'), 'X', '', 'furniture', 41.4, 2.2, 'P', 'A', 'B', '1', current_date + 4, '09:00', '10:00', array['00000000-0000-0000-0000-0000000000a9/l.jpg'])$q$, 'NOT_EDITABLE');
-- R-1.9-23 cancel: requesters informed, publication archived
select public.withdraw_item((select id from public.items where title = 'Desk lamp9'));
do $$ begin
  assert (select status = 'withdrawn' from public.items where title = 'Desk lamp9');
  assert (select count(*) from public.claims c join public.items i on i.id = c.item_id where i.title = 'Desk lamp9' and c.status = 'closed') = 1;   -- 1.10: the other request was auto-rejected at approval
end $$;
commit;
do $$ begin
  assert (select count(*) from public.notifications n join public.items i on i.id = n.item_id where i.title = 'Desk lamp9' and n.kind = 'publication_removed' and n.body like 'Sorry, the publisher removed this publication.%') = 1;   -- the collector
end $$;

-- R-1.9-24 no cancel within 2 h of the pickup start
delete from public.item_availability where item_id = (select id from public.items where title = 'Soon9');
insert into public.item_availability select id, (now() + interval '1 hour')::date, (now() + interval '1 hour')::time from public.items where title = 'Soon9';
update public.items set time_zone = current_setting('TimeZone') where title = 'Soon9';
begin; set local role authenticated; select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-0000000000a9', true);
select public.t_expect_error($q$select public.withdraw_item((select id from public.items where title = 'Soon9'))$q$, 'CANCEL_TOO_LATE');
commit;
\echo 'V1.9 SQL TESTS PASSED'

-- R-1.10-20 approving one recipient auto-rejects all other pending requests (0.13)
begin; set local role authenticated; select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-0000000000a9', true);
select public.publish_item('Coat10', '', 'clothes', 41.4, 2.2, 'P', 'A', 'Barcelona', '08005', current_date + 5, '10:00', '11:00', array['00000000-0000-0000-0000-0000000000a9/c.jpg']);
commit;
begin; set local role authenticated; select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-0000000000b9', true);
select public.submit_claim((select id from public.items where title = 'Coat10'), 'Is it still available?', current_date + 5, '10:00');
commit;
begin; set local role authenticated; select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-0000000000c9', true);
select public.submit_claim((select id from public.items where title = 'Coat10'), 'Hello! Thank you', current_date + 5, '10:30');
commit;
begin; set local role authenticated; select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-0000000000a9', true);
do $$ begin assert (select count(*) from public.item_requests r join public.items i on i.id = r.item_id where i.title = 'Coat10' and r.taker_avatar_url is null) = 2; end $$;
select public.approve_claim((select c.id from public.claims c join public.items i on i.id = c.item_id where i.title = 'Coat10' and c.taker_id = '00000000-0000-0000-0000-0000000000c9'));
do $$ begin
  assert (select c.status from public.claims c join public.items i on i.id = c.item_id where i.title = 'Coat10' and c.taker_id = '00000000-0000-0000-0000-0000000000b9') = 'rejected';
  assert (select collector_nickname is not null and pending_requests = 0 from public.my_publications where title = 'Coat10');
end $$;
commit;
\echo 'V1.10 SQL TESTS PASSED'
