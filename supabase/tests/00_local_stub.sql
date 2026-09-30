-- SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
-- Copyright (C) 2026 Dina Yol (DinaSimple) — Free to Take, https://freetotake.app
-- Minimal stand-in for Supabase's auth/storage schemas so migrations + tests run
-- on plain PostgreSQL (CI / local). NOT applied to the real Supabase project.
do $$ begin
  if not exists (select 1 from pg_roles where rolname = 'anon') then create role anon nologin; end if;
  if not exists (select 1 from pg_roles where rolname = 'authenticated') then create role authenticated nologin; end if;
end $$;

create schema if not exists auth;
create table if not exists auth.users (
  id uuid primary key,
  email text,
  raw_user_meta_data jsonb default '{}'::jsonb
);
create or replace function auth.uid() returns uuid language sql stable as $$
  select nullif(current_setting('request.jwt.claim.sub', true), '')::uuid
$$;
grant usage on schema auth to anon, authenticated;
grant execute on function auth.uid() to anon, authenticated;

create schema if not exists storage;
create table if not exists storage.buckets (id text primary key, name text, public boolean);
create table if not exists storage.objects (id bigserial primary key, bucket_id text, name text);
alter table storage.objects enable row level security;
create or replace function storage.foldername(name text) returns text[] language sql immutable as $$
  select (string_to_array(name, '/'))[1:array_length(string_to_array(name, '/'), 1) - 1]
$$;

-- Supabase grants table privileges to API roles by default
grant usage on schema public to anon, authenticated;
alter default privileges in schema public grant all on tables to anon, authenticated;
alter default privileges in schema public grant all on functions to anon, authenticated;

-- v1.8.1: stand-ins for service_role, Vault, pg_cron and pg_net (live project has the real ones)
do $$ begin
  if not exists (select 1 from pg_roles where rolname = 'service_role') then create role service_role nologin; end if;
end $$;
create schema if not exists vault;
create table if not exists vault.secrets (id uuid primary key default gen_random_uuid(), name text unique, secret text);
create or replace view vault.decrypted_secrets as select id, name, secret as decrypted_secret from vault.secrets;
create or replace function vault.create_secret(new_secret text, new_name text) returns uuid language sql as
  $$ insert into vault.secrets (name, secret) values (new_name, new_secret) returning id $$;
create schema if not exists cron;
create table if not exists cron.jobs (name text primary key, schedule text, command text);
create or replace function cron.schedule(job_name text, schedule text, command text) returns bigint language sql as
  $$ insert into cron.jobs values (job_name, schedule, command) on conflict (name) do update set schedule = excluded.schedule, command = excluded.command returning 1::bigint $$;
create schema if not exists net;

-- v1.12: auth.users columns used by the auth-email function RPCs
alter table auth.users add column if not exists encrypted_password text;
alter table auth.users add column if not exists is_anonymous boolean default false;
