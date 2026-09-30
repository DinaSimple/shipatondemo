-- SPDX-License-Identifier: AGPL-3.0-only
-- Copyright (C) 2026 Dina Elokhova (DinaSimple) — Free to Take, https://freetotake.app
-- v1.17 — Broccoli rewards.
-- Every account starts with 20. Ledger is append-only and written only by triggers / security-definer RPCs.
--   request      −1  sending a request (claims insert). Blocked with NO_BROCCOLI at 0.
--   pickup_done  −1  collector, when an approved pickup is finished (claim → completed, meeting time passed).
--   post         +1  publishing a giveaway (max 5 rewarded posts per 24 h — anti-farming).
--   collected    +1  giver, after confirming in the app that the item was picked up.
-- Demo cards never reach the server, so they never burn broccoli.

create table if not exists public.broccoli_ledger (
  id bigint generated always as identity primary key,
  user_id uuid not null references auth.users (id) on delete cascade,
  delta int not null check (delta in (-1, 1)),
  reason text not null check (reason in ('request', 'pickup_done', 'post', 'collected')),
  ref_id uuid not null,
  created_at timestamptz not null default now(),
  unique (user_id, reason, ref_id)
);
create index if not exists broccoli_ledger_user_idx on public.broccoli_ledger (user_id, created_at desc);
alter table public.broccoli_ledger enable row level security;
revoke all on public.broccoli_ledger from anon, authenticated;
grant select on public.broccoli_ledger to authenticated;
drop policy if exists broccoli_ledger_own on public.broccoli_ledger;
create policy broccoli_ledger_own on public.broccoli_ledger for select to authenticated using (user_id = auth.uid());

create or replace function public.broccoli_balance_of(p_user uuid) returns int
language sql stable security definer set search_path = '' as $$
  select 20 + coalesce((select sum(delta)::int from public.broccoli_ledger where user_id = p_user), 0)
$$;
revoke execute on function public.broccoli_balance_of(uuid) from public, anon, authenticated;

create or replace function public.my_broccoli() returns int
language sql stable security definer set search_path = '' as $$
  select case when auth.uid() is null then 20 else public.broccoli_balance_of(auth.uid()) end
$$;
revoke execute on function public.my_broccoli() from public;
grant execute on function public.my_broccoli() to anon, authenticated;

-- Request: −1, refused at 0.
create or replace function public.broccoli_on_claim_insert() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  if public.broccoli_balance_of(new.taker_id) < 1 then raise exception 'NO_BROCCOLI'; end if;
  insert into public.broccoli_ledger (user_id, delta, reason, ref_id) values (new.taker_id, -1, 'request', new.id)
    on conflict do nothing;
  return new;
end $$;
drop trigger if exists broccoli_claim_insert on public.claims;
create trigger broccoli_claim_insert after insert on public.claims
  for each row execute function public.broccoli_on_claim_insert();

-- Finished pickup (approved → completed): collector −1 (never below 0).
create or replace function public.broccoli_on_claim_completed() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  if new.status = 'completed' and old.status is distinct from 'completed'
     and public.broccoli_balance_of(new.taker_id) >= 1 then
    insert into public.broccoli_ledger (user_id, delta, reason, ref_id) values (new.taker_id, -1, 'pickup_done', new.id)
      on conflict do nothing;
  end if;
  return new;
end $$;
drop trigger if exists broccoli_claim_completed on public.claims;
create trigger broccoli_claim_completed after update of status on public.claims
  for each row execute function public.broccoli_on_claim_completed();

-- New giveaway: +1 (max 5 rewarded per 24 h).
create or replace function public.broccoli_on_item_insert() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  if (select count(*) from public.broccoli_ledger
      where user_id = new.giver_id and reason = 'post' and created_at > now() - interval '24 hours') < 5 then
    insert into public.broccoli_ledger (user_id, delta, reason, ref_id) values (new.giver_id, 1, 'post', new.id)
      on conflict do nothing;
  end if;
  return new;
end $$;
drop trigger if exists broccoli_item_insert on public.items;
create trigger broccoli_item_insert after insert on public.items
  for each row execute function public.broccoli_on_item_insert();

-- Handover confirmation (giver answers the "Did it get picked up?" modal once per finished giveaway).
alter table public.items add column if not exists handover_confirmed boolean;
update public.items set handover_confirmed = true where status = 'given' and handover_confirmed is null; -- before rewards existed

create or replace function public.pending_handovers()
returns table (item_id uuid, title text, collector_nickname text, pickup_at timestamptz)
language plpgsql security definer set search_path = '' as $$
begin
  if auth.uid() is null then return; end if;
  perform public.archive_past_meetings();
  return query
    select i.id, i.title, p.nickname, c.pickup_at
    from public.items i
    join public.claims c on c.item_id = i.id and c.status = 'completed'
    left join public.profiles p on p.id = c.taker_id
    where i.giver_id = auth.uid() and i.status = 'given' and i.handover_confirmed is null
    order by c.pickup_at;
end $$;
revoke execute on function public.pending_handovers() from public, anon;
grant execute on function public.pending_handovers() to authenticated;

-- Returns the new balance.
create or replace function public.confirm_handover(p_item_id uuid, p_collected boolean) returns int
language plpgsql security definer set search_path = '' as $$
declare it public.items;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  select * into it from public.items where id = p_item_id for update;
  if not found or it.giver_id <> auth.uid() then raise exception 'NOT_ALLOWED'; end if;
  if it.status <> 'given' then raise exception 'NOT_FINISHED'; end if;
  if it.handover_confirmed is not null then return public.broccoli_balance_of(auth.uid()); end if;
  update public.items set handover_confirmed = p_collected where id = p_item_id;
  if p_collected then
    insert into public.broccoli_ledger (user_id, delta, reason, ref_id) values (auth.uid(), 1, 'collected', p_item_id)
      on conflict do nothing;
  end if;
  return public.broccoli_balance_of(auth.uid());
end $$;
revoke execute on function public.confirm_handover(uuid, boolean) from public, anon;
grant execute on function public.confirm_handover(uuid, boolean) to authenticated;
