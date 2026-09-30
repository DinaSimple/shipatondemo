-- SPDX-License-Identifier: AGPL-3.0-only
-- Copyright (C) 2026 Dina Elokhova (DinaSimple) — Free to Take, https://freetotake.app
-- v1.5 — fixed catalog categories (spec 0.7). Creator picks one per giveaway; stored as key.
alter table public.items add constraint items_category_chk
  check (category is null or category in ('kids_toys', 'furniture', 'food', 'books', 'clothes', 'random'));
create index items_created_idx on public.items (created_at desc) where status in ('available', 'reserved');
