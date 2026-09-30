-- SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
-- Copyright (C) 2026 Dina Elokhova (DinaSimple) — Free to Take, https://freetotake.app
-- v1.6 — Giveaway details & request submission (spec 0.8)
-- Publisher availability (date + time at the pickup place), requester's chosen slot + note.

alter table public.items add column time_zone text not null default 'Europe/Madrid';

create table public.item_availability (
  item_id   uuid not null references public.items (id) on delete cascade,
  slot_date date not null,
  slot_time time not null,
  primary key (item_id, slot_date, slot_time)
);
alter table public.item_availability enable row level security;
-- readable wherever the listing itself is readable
create policy availability_read on public.item_availability for select to anon, authenticated
  using (exists (select 1 from public.items i where i.id = item_id
                 and (i.status in ('available', 'reserved') or i.giver_id = auth.uid())));
create policy availability_giver_write on public.item_availability for insert to authenticated
  with check (exists (select 1 from public.items i where i.id = item_id and i.giver_id = auth.uid()));
create policy availability_giver_delete on public.item_availability for delete to authenticated
  using (exists (select 1 from public.items i where i.id = item_id and i.giver_id = auth.uid()));

alter table public.claims add column requested_date date, add column requested_time time;
alter table public.claims drop constraint if exists claims_message_check;
alter table public.claims add constraint claims_message_len check (char_length(message) <= 1000); -- provisional

-- submit: slot required and must be one of the publisher's future options
-- (legacy items without any availability: no slot allowed).
drop function public.submit_claim(uuid, text);
create function public.submit_claim(p_item_id uuid, p_message text default null,
                                    p_slot_date date default null, p_slot_time time default null)
returns public.claims
language plpgsql security definer set search_path = public as $$
declare
  v_uid  uuid := public._require_uid();
  v_item public.items;
  v_row  public.claims;
  v_has_slots boolean;
begin
  select * into v_item from public.items where id = p_item_id for update;
  if not found then raise exception 'ITEM_NOT_CLAIMABLE'; end if;
  if v_item.giver_id = v_uid then raise exception 'CANNOT_CLAIM_OWN_ITEM'; end if;
  if v_item.status not in ('available', 'reserved') then raise exception 'ITEM_NOT_CLAIMABLE'; end if;
  if exists (select 1 from public.claims
             where item_id = p_item_id and taker_id = v_uid and status in ('pending_approval', 'approved')) then
    raise exception 'ALREADY_HAS_ACTIVE_CLAIM';
  end if;
  if char_length(coalesce(p_message, '')) > 1000 then raise exception 'NOTE_TOO_LONG'; end if;
  v_has_slots := exists (select 1 from public.item_availability where item_id = p_item_id);
  if v_has_slots then
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

-- approve: pickup time defaults to the requester's chosen slot
drop function public.approve_claim(uuid, timestamptz);
create function public.approve_claim(p_claim_id uuid, p_pickup_at timestamptz default null)
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
  return v_row;
end $$;

revoke execute on function public.submit_claim(uuid, text, date, time), public.approve_claim(uuid, timestamptz) from public, anon;
grant execute on function public.submit_claim(uuid, text, date, time), public.approve_claim(uuid, timestamptz) to authenticated;
