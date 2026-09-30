-- SPDX-License-Identifier: AGPL-3.0-only
-- Copyright (C) 2026 Dina Yol (DinaSimple) — Free to Take, https://freetotake.app
-- v1.14 — Chat between the publisher and the approved collector (text only).
-- Source of truth: items.giver_id + the item's approved/completed claim (taker_id, pickup_at = meeting time).
-- Access, meeting expiry and message limits are enforced here, not in the app.

-- ---------- participants / state ----------
-- Chat state for an item as seen by the caller.
--   participant: caller is the publisher or the approved (or already finished) collector
--   open:        claim still approved, item reserved and the meeting time is in the future
create or replace function public.chat_state(p_item_id uuid)
returns table (participant boolean, open boolean, publisher_id uuid, collector_id uuid, meeting_at timestamptz,
               item_title text, other_nickname text, other_public_name text, other_avatar_url text)
language sql stable security definer set search_path = '' as $$
  with c as (
    select cl.taker_id, cl.pickup_at, cl.status, i.giver_id, i.status as item_status, i.title
    from public.items i
    join public.claims cl on cl.item_id = i.id and cl.status in ('approved', 'completed')
    where i.id = p_item_id
    order by (cl.status = 'approved') desc, cl.pickup_at desc
    limit 1
  )
  select auth.uid() in (c.giver_id, c.taker_id),
         c.status = 'approved' and c.item_status = 'reserved' and c.pickup_at > now(),
         c.giver_id, c.taker_id, c.pickup_at, c.title,
         o.nickname, o.public_name, o.avatar_url
  from c
  left join public.profiles o on o.id = case when auth.uid() = c.giver_id then c.taker_id else c.giver_id end
  where auth.uid() in (c.giver_id, c.taker_id)
$$;
revoke execute on function public.chat_state(uuid) from public, anon;
grant execute on function public.chat_state(uuid) to authenticated;

create or replace function public.is_chat_participant(p_item_id uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (
    select 1 from public.items i
    join public.claims cl on cl.item_id = i.id and cl.status in ('approved', 'completed')
    where i.id = p_item_id and auth.uid() in (i.giver_id, cl.taker_id))
$$;
revoke execute on function public.is_chat_participant(uuid) from public, anon;
grant execute on function public.is_chat_participant(uuid) to authenticated;

-- The collector keeps seeing the listing after it is reserved / finished (My claims "Finished", chat header).
create policy items_read_collector on public.items for select to authenticated
  using (public.is_chat_participant(id));

-- ---------- messages ----------
create table if not exists public.chat_messages (
  id         uuid primary key default gen_random_uuid(),
  item_id    uuid not null references public.items (id) on delete cascade,
  sender_id  uuid not null references public.profiles (id) on delete cascade,
  body       text not null check (char_length(btrim(body)) between 1 and 1000),
  created_at timestamptz not null default now()
);
create index if not exists chat_messages_item_idx on public.chat_messages (item_id, created_at);
alter table public.chat_messages enable row level security;
create policy chat_read on public.chat_messages for select to authenticated using (public.is_chat_participant(item_id));
revoke insert, update, delete on public.chat_messages from anon, authenticated;   -- writes only via send_chat_message

-- Finish meetings whose time has passed: claim → completed, publication → given (archived), chat read-only.
create or replace function public.archive_past_meetings() returns int
language plpgsql security definer set search_path = '' as $$
declare n int := 0; r record;
begin
  for r in select cl.id, cl.item_id from public.claims cl join public.items i on i.id = cl.item_id
           where cl.status = 'approved' and i.status = 'reserved' and cl.pickup_at <= now()
           for update of cl, i skip locked
  loop
    update public.claims set status = 'completed' where id = r.id;
    update public.claims set status = 'closed' where item_id = r.item_id and id <> r.id and status in ('pending_approval', 'approved');
    update public.items set status = 'given' where id = r.item_id;
    n := n + 1;
  end loop;
  return n;
end $$;
revoke execute on function public.archive_past_meetings() from public, anon, authenticated;

create or replace function public.send_chat_message(p_item_id uuid, p_body text)
returns public.chat_messages language plpgsql security definer set search_path = '' as $$
declare s record; m public.chat_messages; b text := btrim(coalesce(p_body, ''));
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  perform public.archive_past_meetings();
  select * into s from public.chat_state(p_item_id);
  if not found or not s.participant then raise exception 'CHAT_FORBIDDEN'; end if;
  if not s.open then raise exception 'CHAT_CLOSED'; end if;
  if char_length(b) = 0 then raise exception 'MESSAGE_EMPTY'; end if;
  if char_length(b) > 1000 then raise exception 'MESSAGE_TOO_LONG'; end if;
  if (select count(*) from public.chat_messages where sender_id = auth.uid() and created_at > now() - interval '1 minute') >= 20
    then raise exception 'RATE_LIMITED'; end if;
  insert into public.chat_messages (item_id, sender_id, body) values (p_item_id, auth.uid(), b) returning * into m;
  insert into public.notifications (user_id, kind, item_id, body)
    values (case when auth.uid() = s.publisher_id then s.collector_id else s.publisher_id end, 'chat_message', p_item_id, left(b, 140));
  return m;
end $$;
revoke execute on function public.send_chat_message(uuid, text) from public, anon;
grant execute on function public.send_chat_message(uuid, text) to authenticated;

alter table public.notifications drop constraint notifications_kind_check;
alter table public.notifications add constraint notifications_kind_check
  check (kind in ('taker_left_queue', 'pickup_cancelled_reopened', 'pickup_cancelled_republish_needed', 'publication_removed',
                  'claim_approved', 'new_request', 'new_giveaway_nearby', 'chat_message'));

-- Every 5 minutes (also run lazily on each send).
select cron.schedule('archive-past-meetings', '*/5 * * * *', 'select public.archive_past_meetings()');
