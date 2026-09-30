-- SPDX-License-Identifier: AGPL-3.0-only
-- Copyright (C) 2026 Dina Elokhova (DinaSimple) — Free to Take, https://freetotake.app
-- v1.11 — My Profile (spec 0.14): avatar, notification preferences, server-side notification events

-- avatars: public read, own folder write (<uid>/avatar.jpg)
insert into storage.buckets (id, name, public) values ('avatars', 'avatars', true) on conflict (id) do nothing;
create policy avatars_read on storage.objects for select to anon, authenticated using (bucket_id = 'avatars');
create policy avatars_write on storage.objects for insert to authenticated
  with check (bucket_id = 'avatars' and (storage.foldername(name))[1] = auth.uid()::text);
create policy avatars_update on storage.objects for update to authenticated
  using (bucket_id = 'avatars' and (storage.foldername(name))[1] = auth.uid()::text);
create policy avatars_delete on storage.objects for delete to authenticated
  using (bucket_id = 'avatars' and (storage.foldername(name))[1] = auth.uid()::text);

-- notification preferences + coarse home city (for "new giveaways nearby"; city only, never an address)
alter table public.profiles
  add column notify_claim_approved boolean not null default true,
  add column notify_new_request    boolean not null default true,
  add column notify_nearby         boolean not null default true,
  add column home_city             text check (char_length(home_city) <= 80);
grant update (public_name, avatar_url, notify_claim_approved, notify_new_request, notify_nearby, home_city) on public.profiles to authenticated;

alter table public.notifications drop constraint notifications_kind_check;
alter table public.notifications add constraint notifications_kind_check
  check (kind in ('taker_left_queue', 'pickup_cancelled_reopened', 'pickup_cancelled_republish_needed', 'publication_removed',
                  'claim_approved', 'new_request', 'new_giveaway_nearby'));

-- events → notifications (respecting each recipient's preferences)
create or replace function public.notify_on_claim() returns trigger
language plpgsql security definer set search_path = public as $$
declare v_giver uuid;
begin
  if tg_op = 'INSERT' and new.status = 'pending_approval' then
    select giver_id into v_giver from public.items where id = new.item_id;
    insert into public.notifications (user_id, kind, item_id, claim_id)
    select v_giver, 'new_request', new.item_id, new.id
    from public.profiles p where p.id = v_giver and p.notify_new_request;
  elsif tg_op = 'UPDATE' and new.status = 'approved' and old.status is distinct from 'approved' then
    insert into public.notifications (user_id, kind, item_id, claim_id)
    select new.taker_id, 'claim_approved', new.item_id, new.id
    from public.profiles p where p.id = new.taker_id and p.notify_claim_approved;
  end if;
  return new;
end $$;
revoke execute on function public.notify_on_claim() from public, anon, authenticated;
create trigger claims_notify after insert or update of status on public.claims
  for each row execute function public.notify_on_claim();

create or replace function public.notify_nearby_on_publish() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  if new.status = 'available' and new.area_city is not null then
    insert into public.notifications (user_id, kind, item_id)
    select p.id, 'new_giveaway_nearby', new.id
    from public.profiles p
    where p.notify_nearby and p.id <> new.giver_id and lower(p.home_city) = lower(new.area_city)
    limit 500;
  end if;
  return new;
end $$;
revoke execute on function public.notify_nearby_on_publish() from public, anon, authenticated;
create trigger items_notify_nearby after insert on public.items
  for each row execute function public.notify_nearby_on_publish();

-- the user's own notifications with the listing title (device shows them while the app runs; FCM later)
create or replace view public.my_notifications with (security_invoker = true) as
  select n.id, n.kind, n.item_id, n.body, n.created_at, n.read_at, i.title as item_title
  from public.notifications n left join public.items i on i.id = n.item_id
  where n.user_id = auth.uid();
grant select on public.my_notifications to authenticated;
