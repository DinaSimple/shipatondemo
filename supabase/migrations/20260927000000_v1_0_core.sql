-- SPDX-License-Identifier: AGPL-3.0-only
-- Copyright (C) 2026 Dina Yol (DinaSimple) — Free to Take, https://freetotake.app
-- Free to Take — v1.0 core schema
-- Mirrors shared/src/commonMain/.../domain/rules/ClaimRules.kt.
-- Clients never change item/claim status directly: all transitions go through the
-- SECURITY DEFINER RPCs below, which raise the DomainError name as the message.

create extension if not exists pgcrypto;

-- ---------- enums ----------
create type public.item_status as enum
  ('available', 'reserved', 'awaiting_giver_decision', 'given', 'withdrawn');

create type public.claim_status as enum
  ('pending_approval', 'approved', 'rejected', 'cancelled_by_taker', 'cancelled_late', 'closed', 'completed');

-- ---------- tables ----------
create table public.profiles (
  id           uuid primary key references auth.users (id) on delete cascade,
  display_name text not null default '',
  avatar_url   text,
  created_at   timestamptz not null default now()
);

create table public.items (
  id          uuid primary key default gen_random_uuid(),
  giver_id    uuid not null references public.profiles (id) on delete cascade,
  title       text not null check (char_length(title) between 1 and 120),
  description text not null default '' check (char_length(description) <= 2000),
  photo_paths text[] not null default '{}',
  category    text,
  area        text,
  status      public.item_status not null default 'available',
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now()
);
create index items_feed_idx on public.items (status, created_at desc);
create index items_giver_idx on public.items (giver_id);

create table public.claims (
  id         uuid primary key default gen_random_uuid(),
  item_id    uuid not null references public.items (id) on delete cascade,
  taker_id   uuid not null references public.profiles (id) on delete cascade,
  status     public.claim_status not null default 'pending_approval',
  message    text check (char_length(message) <= 500),
  pickup_at  timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint approved_needs_pickup check (status <> 'approved' or pickup_at is not null)
);
create index claims_item_idx on public.claims (item_id, created_at);
create index claims_taker_idx on public.claims (taker_id, created_at desc);
-- at most one approved claim per item
create unique index claims_one_approved_per_item on public.claims (item_id) where status = 'approved';
-- at most one active claim per taker per item
create unique index claims_one_active_per_taker on public.claims (item_id, taker_id)
  where status in ('pending_approval', 'approved');

-- ---------- housekeeping ----------
create or replace function public.touch_updated_at() returns trigger
language plpgsql as $$ begin new.updated_at := now(); return new; end $$;

create trigger items_touch  before update on public.items  for each row execute function public.touch_updated_at();
create trigger claims_touch before update on public.claims for each row execute function public.touch_updated_at();

-- auto-create profile on sign-up
create or replace function public.handle_new_user() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  insert into public.profiles (id, display_name)
  values (new.id, coalesce(new.raw_user_meta_data ->> 'display_name', split_part(coalesce(new.email, ''), '@', 1)))
  on conflict (id) do nothing;
  return new;
end $$;

create trigger on_auth_user_created after insert on auth.users
  for each row execute function public.handle_new_user();

-- ---------- RLS ----------
alter table public.profiles enable row level security;
alter table public.items    enable row level security;
alter table public.claims   enable row level security;

-- profiles: public read (names/avatars on cards), self update
create policy profiles_read   on public.profiles for select to anon, authenticated using (true);
create policy profiles_update on public.profiles for update to authenticated
  using (id = auth.uid()) with check (id = auth.uid());

-- items: guests browse open items; owners see all their own
create policy items_read on public.items for select to anon, authenticated
  using (status in ('available', 'reserved') or giver_id = auth.uid());
create policy items_insert on public.items for insert to authenticated
  with check (giver_id = auth.uid() and status = 'available');
create policy items_update_own on public.items for update to authenticated
  using (giver_id = auth.uid()) with check (giver_id = auth.uid());

-- status is not client-writable: only content columns
revoke update on public.items from anon, authenticated;
grant  update (title, description, photo_paths, category, area) on public.items to authenticated;
revoke delete on public.items from anon, authenticated;

-- claims: visible to the taker and to the giver of the item; writes only via RPC
create policy claims_read on public.claims for select to authenticated
  using (taker_id = auth.uid()
         or exists (select 1 from public.items i where i.id = claims.item_id and i.giver_id = auth.uid()));
revoke insert, update, delete on public.claims from anon, authenticated;

-- ---------- RPCs (business rules) ----------
create or replace function public._require_uid() returns uuid
language plpgsql stable as $$
declare v uuid := auth.uid();
begin
  if v is null then raise exception 'LOGIN_REQUIRED'; end if;
  return v;
end $$;

-- TAKE: submit a request
create or replace function public.submit_claim(p_item_id uuid, p_message text default null)
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
  if v_item.status not in ('available', 'reserved') then raise exception 'ITEM_NOT_CLAIMABLE'; end if;
  if exists (select 1 from public.claims
             where item_id = p_item_id and taker_id = v_uid and status in ('pending_approval', 'approved')) then
    raise exception 'ALREADY_HAS_ACTIVE_CLAIM';
  end if;
  insert into public.claims (item_id, taker_id, message)
  values (p_item_id, v_uid, nullif(btrim(p_message), ''))
  returning * into v_row;
  return v_row;
end $$;

-- TAKE: cancel. Pending → always. Approved → frees slot only if pickup is > 8h away.
create or replace function public.cancel_claim(p_claim_id uuid)
returns public.claims
language plpgsql security definer set search_path = public as $$
declare
  v_uid   uuid := public._require_uid();
  v_claim public.claims;
  v_row   public.claims;
begin
  select * into v_claim from public.claims where id = p_claim_id;
  if not found or v_claim.taker_id <> v_uid then raise exception 'NOT_CLAIM_OWNER'; end if;
  perform 1 from public.items where id = v_claim.item_id for update;
  select * into v_claim from public.claims where id = p_claim_id for update;

  if v_claim.status = 'pending_approval' then
    update public.claims set status = 'cancelled_by_taker' where id = p_claim_id returning * into v_row;
  elsif v_claim.status = 'approved' then
    if v_claim.pickup_at - now() > interval '8 hours' then
      update public.claims set status = 'cancelled_by_taker' where id = p_claim_id returning * into v_row;
      update public.items  set status = 'available' where id = v_claim.item_id;
    else
      update public.claims set status = 'cancelled_late' where id = p_claim_id returning * into v_row;
      update public.items  set status = 'awaiting_giver_decision' where id = v_claim.item_id;
    end if;
  else
    raise exception 'INVALID_TRANSITION';
  end if;
  return v_row;
end $$;

-- GIVE: approve one request and set pickup time
create or replace function public.approve_claim(p_claim_id uuid, p_pickup_at timestamptz)
returns public.claims
language plpgsql security definer set search_path = public as $$
declare
  v_uid   uuid := public._require_uid();
  v_claim public.claims;
  v_item  public.items;
  v_row   public.claims;
begin
  select * into v_claim from public.claims where id = p_claim_id;
  if not found then raise exception 'INVALID_TRANSITION'; end if;
  select * into v_item from public.items where id = v_claim.item_id for update;
  if v_item.giver_id <> v_uid then raise exception 'NOT_ITEM_OWNER'; end if;
  select * into v_claim from public.claims where id = p_claim_id for update;
  if v_claim.status <> 'pending_approval' then raise exception 'INVALID_TRANSITION'; end if;
  if v_item.status = 'reserved' then raise exception 'ITEM_ALREADY_RESERVED'; end if;
  if v_item.status not in ('available', 'awaiting_giver_decision') then raise exception 'ITEM_NOT_CLAIMABLE'; end if;
  if p_pickup_at is null or p_pickup_at <= now() then raise exception 'PICKUP_TIME_IN_PAST'; end if;

  update public.claims set status = 'approved', pickup_at = p_pickup_at where id = p_claim_id returning * into v_row;
  update public.items  set status = 'reserved' where id = v_item.id;
  return v_row;
end $$;

-- GIVE: reject a pending request
create or replace function public.reject_claim(p_claim_id uuid)
returns public.claims
language plpgsql security definer set search_path = public as $$
declare
  v_uid   uuid := public._require_uid();
  v_claim public.claims;
  v_row   public.claims;
begin
  select * into v_claim from public.claims where id = p_claim_id for update;
  if not found then raise exception 'INVALID_TRANSITION'; end if;
  if not exists (select 1 from public.items where id = v_claim.item_id and giver_id = v_uid) then
    raise exception 'NOT_ITEM_OWNER';
  end if;
  if v_claim.status <> 'pending_approval' then raise exception 'INVALID_TRANSITION'; end if;
  update public.claims set status = 'rejected' where id = p_claim_id returning * into v_row;
  return v_row;
end $$;

-- GIVE: confirm hand-over; closes the rest of the queue
create or replace function public.complete_claim(p_claim_id uuid)
returns public.claims
language plpgsql security definer set search_path = public as $$
declare
  v_uid   uuid := public._require_uid();
  v_claim public.claims;
  v_item  public.items;
  v_row   public.claims;
begin
  select * into v_claim from public.claims where id = p_claim_id;
  if not found then raise exception 'INVALID_TRANSITION'; end if;
  select * into v_item from public.items where id = v_claim.item_id for update;
  if v_item.giver_id <> v_uid then raise exception 'NOT_ITEM_OWNER'; end if;
  if v_claim.status <> 'approved' or v_item.status <> 'reserved' then raise exception 'INVALID_TRANSITION'; end if;

  update public.claims set status = 'completed' where id = p_claim_id returning * into v_row;
  update public.claims set status = 'closed'
    where item_id = v_item.id and id <> p_claim_id and status in ('pending_approval', 'approved');
  update public.items set status = 'given' where id = v_item.id;
  return v_row;
end $$;

-- GIVE: reopen after a late cancellation
create or replace function public.reopen_item(p_item_id uuid)
returns public.items
language plpgsql security definer set search_path = public as $$
declare
  v_uid  uuid := public._require_uid();
  v_item public.items;
begin
  select * into v_item from public.items where id = p_item_id for update;
  if not found or v_item.giver_id <> v_uid then raise exception 'NOT_ITEM_OWNER'; end if;
  if v_item.status <> 'awaiting_giver_decision' then raise exception 'INVALID_TRANSITION'; end if;
  update public.items set status = 'available' where id = p_item_id returning * into v_item;
  return v_item;
end $$;

-- GIVE: withdraw a post; closes all active requests
create or replace function public.withdraw_item(p_item_id uuid)
returns public.items
language plpgsql security definer set search_path = public as $$
declare
  v_uid  uuid := public._require_uid();
  v_item public.items;
begin
  select * into v_item from public.items where id = p_item_id for update;
  if not found or v_item.giver_id <> v_uid then raise exception 'NOT_ITEM_OWNER'; end if;
  if v_item.status in ('given', 'withdrawn') then raise exception 'INVALID_TRANSITION'; end if;
  update public.claims set status = 'closed'
    where item_id = p_item_id and status in ('pending_approval', 'approved');
  update public.items set status = 'withdrawn' where id = p_item_id returning * into v_item;
  return v_item;
end $$;

revoke execute on function public.submit_claim(uuid, text), public.cancel_claim(uuid),
  public.approve_claim(uuid, timestamptz), public.reject_claim(uuid), public.complete_claim(uuid),
  public.reopen_item(uuid), public.withdraw_item(uuid) from public, anon;
grant execute on function public.submit_claim(uuid, text), public.cancel_claim(uuid),
  public.approve_claim(uuid, timestamptz), public.reject_claim(uuid), public.complete_claim(uuid),
  public.reopen_item(uuid), public.withdraw_item(uuid) to authenticated;

-- ---------- storage: item photos (public read, owner-folder write) ----------
insert into storage.buckets (id, name, public) values ('item-photos', 'item-photos', true)
  on conflict (id) do nothing;

create policy item_photos_read on storage.objects for select to anon, authenticated
  using (bucket_id = 'item-photos');
create policy item_photos_write on storage.objects for insert to authenticated
  with check (bucket_id = 'item-photos' and (storage.foldername(name))[1] = auth.uid()::text);
create policy item_photos_delete on storage.objects for delete to authenticated
  using (bucket_id = 'item-photos' and (storage.foldername(name))[1] = auth.uid()::text);
