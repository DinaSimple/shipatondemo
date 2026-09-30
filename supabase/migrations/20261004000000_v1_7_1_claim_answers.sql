-- SPDX-License-Identifier: AGPL-3.0-only
-- Copyright (C) 2026 Dina Elokhova (DinaSimple) — Free to Take, https://freetotake.app
-- v1.7.1 — answers Q18/Q19: no re-request after rejection; no cancel after pickup time

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
  -- answer Q18: a rejected requester cannot request the same giveaway again
  if exists (select 1 from public.claims where item_id = p_item_id and taker_id = v_uid and status = 'rejected') then
    raise exception 'ALREADY_REJECTED';
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
    -- answer Q19: no cancelling once the pickup time has come (claim goes to the archive)
    if v_claim.pickup_at <= now() then raise exception 'PICKUP_PASSED'; end if;
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
