-- SPDX-License-Identifier: AGPL-3.0-only
-- Copyright (C) 2026 Dina Yol (DinaSimple) — Free to Take, https://freetotake.app
-- v1.0.1 — fixes from Supabase security advisor
-- 1) pin search_path on helper functions
alter function public._require_uid() set search_path = public;
alter function public.touch_updated_at() set search_path = public;

-- 2) sign-up trigger must not be callable via /rest/v1/rpc
revoke execute on function public.handle_new_user() from public, anon, authenticated;

-- Note: the "authenticated can execute SECURITY DEFINER" warnings for
-- submit/cancel/approve/reject/complete_claim, reopen_item, withdraw_item are by design:
-- they are the only write path and re-check ownership + rules inside.
