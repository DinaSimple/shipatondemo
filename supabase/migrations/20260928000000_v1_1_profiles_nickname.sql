-- SPDX-License-Identifier: AGPL-3.0-only
-- Copyright (C) 2026 Dina Yol (DinaSimple) — Free to Take, https://freetotake.app
-- v1.1 — public name vs service-generated nickname (General Requirements)
-- Others see public_name when set, otherwise nickname. Email is never exposed.

alter table public.profiles rename column display_name to public_name;
alter table public.profiles alter column public_name drop not null;
alter table public.profiles alter column public_name drop default;
update public.profiles set public_name = null where public_name = '';
alter table public.profiles add constraint public_name_len check (public_name is null or char_length(public_name) between 1 and 40);

create or replace function public.generate_nickname() returns text
language plpgsql volatile set search_path = public as $$
declare
  adj  text[] := array['Sunny','Brave','Kind','Swift','Happy','Calm','Lucky','Bright','Gentle','Jolly','Clever','Cozy'];
  noun text[] := array['Otter','Fox','Panda','Robin','Koala','Lynx','Heron','Badger','Finch','Dolphin','Hedgehog','Owl'];
  cand text;
begin
  loop
    cand := adj[1 + floor(random() * array_length(adj, 1))::int]
         || noun[1 + floor(random() * array_length(noun, 1))::int]
         || lpad(floor(random() * 10000)::int::text, 4, '0');
    exit when not exists (select 1 from public.profiles where nickname = cand);
  end loop;
  return cand;
end $$;
revoke execute on function public.generate_nickname() from public, anon, authenticated;

alter table public.profiles add column nickname text;
update public.profiles set nickname = public.generate_nickname() where nickname is null;
alter table public.profiles alter column nickname set not null;
alter table public.profiles alter column nickname set default public.generate_nickname();
create unique index profiles_nickname_key on public.profiles (nickname);

-- nickname is service-owned: users may update only public_name / avatar_url
revoke update on public.profiles from anon, authenticated;
grant update (public_name, avatar_url) on public.profiles to authenticated;

create or replace function public.handle_new_user() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  insert into public.profiles (id, public_name)
  values (new.id, nullif(btrim(new.raw_user_meta_data ->> 'public_name'), ''))
  on conflict (id) do nothing;
  return new;
end $$;
revoke execute on function public.handle_new_user() from public, anon, authenticated;
