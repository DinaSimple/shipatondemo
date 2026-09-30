-- SPDX-License-Identifier: AGPL-3.0-only
-- Copyright (C) 2026 Dina Elokhova (DinaSimple) — Free to Take, https://freetotake.app
-- v1.14 chat: access, expiry, archive, read-only history
\set ON_ERROR_STOP on
insert into auth.users (id, email) values
  ('00000000-0000-0000-0000-00000000a141', 'g14@x.io'),
  ('00000000-0000-0000-0000-00000000b141', 't14@x.io'),
  ('00000000-0000-0000-0000-00000000c141', 'o14@x.io');

begin; set local role authenticated; select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000a141', true);
select public.publish_item('Chair14', '', 'furniture', 41.4, 2.2, 'P', 'A', 'Barcelona', '08005', current_date + 3, '10:00', '11:00', array['00000000-0000-0000-0000-00000000a141/c.jpg']);
commit;
begin; set local role authenticated; select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000b141', true);
select public.submit_claim((select id from public.items where title = 'Chair14'), 'hi', current_date + 3, '10:00');
-- R-1.14-01 no chat before approval
select public.t_expect_error($q$select public.send_chat_message((select id from public.items where title = 'Chair14'), 'hello')$q$, 'CHAT_FORBIDDEN');
commit;
begin; set local role authenticated; select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000a141', true);
select public.approve_claim((select c.id from public.claims c join public.items i on i.id = c.item_id where i.title = 'Chair14'));
select public.send_chat_message((select id from public.items where title = 'Chair14'), '  See you at 10!  ');
commit;
-- R-1.14-02 approved collector can read and send; other users can't
begin; set local role authenticated; select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000b141', true);
do $$ declare s record; begin
  select * into s from public.chat_state((select id from public.items where title = 'Chair14'));
  assert s.participant and s.open, 'collector: open chat';
  assert (select body from public.chat_messages) = 'See you at 10!', 'trimmed, visible to collector';
end $$;
select public.send_chat_message((select id from public.items where title = 'Chair14'), 'Great');
select public.t_expect_error($q$select public.send_chat_message((select id from public.items where title = 'Chair14'), '   ')$q$, 'MESSAGE_EMPTY');
select public.t_expect_error($q$select public.send_chat_message((select id from public.items where title = 'Chair14'), repeat('x', 1001))$q$, 'MESSAGE_TOO_LONG');
select public.t_expect_error($q$insert into public.chat_messages (item_id, sender_id, body) values ((select id from public.items where title = 'Chair14'), auth.uid(), 'x')$q$, 'permission denied');
commit;
begin; set local role authenticated; select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000c141', true);
do $$ begin assert (select count(*) from public.chat_messages) = 0, 'outsider sees nothing'; end $$;
select public.t_expect_error($q$select public.send_chat_message((select i.id from public.items i where i.title = 'Chair14' limit 1), 'hey')$q$, 'CHAT_FORBIDDEN');
commit;
do $$ begin
  assert (select count(*) from public.notifications where kind = 'chat_message') = 2, 'each message notifies the other side';
end $$;

-- R-1.14-03 meeting time passed → archived (given / completed), chat read-only, history kept
update public.claims set pickup_at = now() - interval '1 minute'
  where item_id = (select id from public.items where title = 'Chair14') and status = 'approved';
begin; set local role authenticated; select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000b141', true);
select public.t_expect_error($q$select public.send_chat_message((select id from public.items where title = 'Chair14'), 'late')$q$, 'CHAT_CLOSED');
commit;
select public.archive_past_meetings();
do $$ begin
  assert (select status from public.items where title = 'Chair14') = 'given', 'publication archived';
  assert (select c.status from public.claims c join public.items i on i.id = c.item_id where i.title = 'Chair14') = 'completed';
end $$;
begin; set local role authenticated; select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000b141', true);
do $$ declare s record; begin
  select * into s from public.chat_state((select id from public.items where title = 'Chair14'));
  assert s.participant and not s.open, 'read-only after the meeting';
  assert (select count(*) from public.chat_messages) = 2, 'history still readable';
  assert (select count(*) from public.items where title = 'Chair14') = 1, 'collector still sees the finished listing';
end $$;
select public.t_expect_error($q$select public.send_chat_message((select id from public.items where title = 'Chair14'), 'late')$q$, 'CHAT_CLOSED');
commit;
\echo 'V1.14 SQL TESTS PASSED'
