-- SPDX-License-Identifier: AGPL-3.0-only
-- Copyright (C) 2026 Dina Yol (DinaSimple) — Free to Take, https://freetotake.app
-- v1.7 — Catalog card states, My Claims removal, favorites (spec 0.9 + 0.10)

-- 0.10: a listing with a selected recipient (reserved) is hidden from the public feed
create or replace view public.active_items
with (security_invoker = true) as
  select id, giver_id, title, description, photo_paths, category, area_city, area_postal,
         approx_lat, approx_lng, status, created_at, expires_at
  from public.items
  where status = 'available'
    and (expires_at is null or expires_at > now());

-- 0.10: ... and takes no new requests
create or replace function public.submit_claim(p_item_id uuid, p_message text default null,
                                               p_slot_date date default null, p_slot_time time default null)
returns public.claims
language plpgsql security definer set search_path = public as $$
declare
  v_uid  uuid := public._require_uid();
  v_item public.items;
  v_row  public.claims;
begin
  select * into v_item from public.items where id = p_item_id for update;
  if not found then raise exception 'ITEM_NOT_CLAIMABLE'; end if;
  if v_item.giver_id = v_uid then raise exception 'CANNOT_CLAIM_OWN_ITEM'; end if;
  if v_item.status <> 'available' then raise exception 'ITEM_NOT_CLAIMABLE'; end if;
  if exists (select 1 from public.claims
             where item_id = p_item_id and taker_id = v_uid and status in ('pending_approval', 'approved')) then
    raise exception 'ALREADY_HAS_ACTIVE_CLAIM';
  end if;
  if char_length(coalesce(p_message, '')) > 1000 then raise exception 'NOTE_TOO_LONG'; end if;
  if exists (select 1 from public.item_availability where item_id = p_item_id) then
    if p_slot_date is null or p_slot_time is null then raise exception 'SLOT_REQUIRED'; end if;
    if not exists (select 1 from public.item_availability
                   where item_id = p_item_id and slot_date = p_slot_date and slot_time = p_slot_time) then
      raise exception 'SLOT_NOT_OFFERED';
    end if;
    if (p_slot_date + p_slot_time) at time zone v_item.time_zone <= now() then raise exception 'SLOT_NOT_OFFERED'; end if;
  elsif p_slot_date is not null or p_slot_time is not null then
    raise exception 'SLOT_NOT_OFFERED';
  end if;
  insert into public.claims (item_id, taker_id, message, requested_date, requested_time)
  values (p_item_id, v_uid, nullif(btrim(p_message), ''), p_slot_date, p_slot_time)
  returning * into v_row;
  return v_row;
end $$;

-- Publisher notifications (delivered in-app / push later). Written only by RPCs.
create table public.notifications (
  id         uuid primary key default gen_random_uuid(),
  user_id    uuid not null references public.profiles (id) on delete cascade,
  kind       text not null check (kind in ('taker_left_queue', 'pickup_cancelled_reopened', 'pickup_cancelled_republish_needed')),
  item_id    uuid references public.items (id) on delete cascade,
  claim_id   uuid references public.claims (id) on delete cascade,
  created_at timestamptz not null default now(),
  read_at    timestamptz
);
create index notifications_user_idx on public.notifications (user_id, created_at desc);
alter table public.notifications enable row level security;
create policy notifications_own_read on public.notifications for select to authenticated using (user_id = auth.uid());
revoke insert, update, delete on public.notifications from anon, authenticated;
grant update (read_at) on public.notifications to authenticated;
create policy notifications_own_mark_read on public.notifications for update to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());

-- cancel now notifies the publisher in every case (0.9: pending requester left the queue)
create or replace function public.cancel_claim(p_claim_id uuid)
returns public.claims
language plpgsql security definer set search_path = public as $$
declare
  v_uid   uuid := public._require_uid();
  v_claim public.claims;
  v_item  public.items;
  v_row   public.claims;
  v_kind  text;
begin
  select * into v_claim from public.claims where id = p_claim_id;
  if not found or v_claim.taker_id <> v_uid then raise exception 'NOT_CLAIM_OWNER'; end if;
  select * into v_item from public.items where id = v_claim.item_id for update;
  select * into v_claim from public.claims where id = p_claim_id for update;

  if v_claim.status = 'pending_approval' then
    update public.claims set status = 'cancelled_by_taker' where id = p_claim_id returning * into v_row;
    v_kind := 'taker_left_queue';
  elsif v_claim.status = 'approved' then
    if v_claim.pickup_at - now() > interval '8 hours' then
      update public.claims set status = 'cancelled_by_taker' where id = p_claim_id returning * into v_row;
      update public.items  set status = 'available' where id = v_claim.item_id;
      v_kind := 'pickup_cancelled_reopened';
    else
      update public.claims set status = 'cancelled_late' where id = p_claim_id returning * into v_row;
      update public.items  set status = 'awaiting_giver_decision' where id = v_claim.item_id;
      v_kind := 'pickup_cancelled_republish_needed';
    end if;
  else
    raise exception 'INVALID_TRANSITION';
  end if;
  insert into public.notifications (user_id, kind, item_id, claim_id) values (v_item.giver_id, v_kind, v_item.id, p_claim_id);
  return v_row;
end $$;

-- Trash on a My Claims card: cancel if still open, then hide from the taker's list.
alter table public.claims add column hidden_by_taker boolean not null default false;

create or replace function public.remove_my_request(p_claim_id uuid)
returns public.claims
language plpgsql security definer set search_path = public as $$
declare
  v_uid   uuid := public._require_uid();
  v_claim public.claims;
  v_row   public.claims;
begin
  select * into v_claim from public.claims where id = p_claim_id;
  if not found or v_claim.taker_id <> v_uid then raise exception 'NOT_CLAIM_OWNER'; end if;
  if v_claim.status in ('pending_approval', 'approved') then
    perform public.cancel_claim(p_claim_id);
  elsif v_claim.status <> 'rejected' then
    raise exception 'INVALID_TRANSITION';
  end if;
  update public.claims set hidden_by_taker = true where id = p_claim_id returning * into v_row;
  return v_row;
end $$;

revoke execute on function public.remove_my_request(uuid) from public, anon;
grant execute on function public.remove_my_request(uuid) to authenticated;

-- Favorites (heart)
create table public.favorites (
  user_id    uuid not null default auth.uid() references public.profiles (id) on delete cascade,
  item_id    uuid not null references public.items (id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (user_id, item_id)
);
alter table public.favorites enable row level security;
create policy favorites_own_read   on public.favorites for select to authenticated using (user_id = auth.uid());
create policy favorites_own_insert on public.favorites for insert to authenticated with check (user_id = auth.uid());
create policy favorites_own_delete on public.favorites for delete to authenticated using (user_id = auth.uid());
revoke update on public.favorites from anon, authenticated;
