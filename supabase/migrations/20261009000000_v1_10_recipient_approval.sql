-- SPDX-License-Identifier: AGPL-3.0-only
-- Copyright (C) 2026 Dina Elokhova (DinaSimple) — Free to Take, https://freetotake.app
-- v1.10 — Recipient approval (spec 0.13)

create or replace function public.approve_claim(p_claim_id uuid, p_pickup_at timestamptz default null)
returns public.claims
language plpgsql security definer set search_path = public as $$
declare
  v_uid   uuid := public._require_uid();
  v_claim public.claims;
  v_item  public.items;
  v_row   public.claims;
  v_at    timestamptz;
begin
  select * into v_claim from public.claims where id = p_claim_id;
  if not found then raise exception 'INVALID_TRANSITION'; end if;
  select * into v_item from public.items where id = v_claim.item_id for update;
  if v_item.giver_id <> v_uid then raise exception 'NOT_ITEM_OWNER'; end if;
  select * into v_claim from public.claims where id = p_claim_id for update;
  if v_claim.status <> 'pending_approval' then raise exception 'INVALID_TRANSITION'; end if;
  if v_item.status = 'reserved' then raise exception 'ITEM_ALREADY_RESERVED'; end if;
  if v_item.status not in ('available', 'awaiting_giver_decision') then raise exception 'ITEM_NOT_CLAIMABLE'; end if;
  v_at := coalesce(p_pickup_at, (v_claim.requested_date + v_claim.requested_time) at time zone v_item.time_zone);
  if v_at is null or v_at <= now() then raise exception 'PICKUP_TIME_IN_PAST'; end if;
  update public.claims set status = 'approved', pickup_at = v_at where id = p_claim_id returning * into v_row;
  update public.items  set status = 'reserved' where id = v_item.id;
  -- 0.13: only one recipient; every other pending request is auto-rejected
  update public.claims set status = 'rejected'
    where item_id = v_item.id and id <> p_claim_id and status = 'pending_approval';
  return v_row;
end $$;

-- review cards show the requester's avatar
create or replace view public.item_requests with (security_invoker = true) as
  select c.id, c.item_id, c.taker_id, c.status, c.created_at, c.message, c.requested_date, c.requested_time, c.pickup_at,
         p.nickname as taker_nickname, p.avatar_url as taker_avatar_url
  from public.claims c join public.profiles p on p.id = c.taker_id;
grant select on public.item_requests to authenticated;
