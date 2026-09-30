-- SPDX-License-Identifier: AGPL-3.0-only
-- Copyright (C) 2026 Dina Elokhova (DinaSimple) — Free to Take, https://freetotake.app
-- v1.5 categories
\set ON_ERROR_STOP on
insert into auth.users (id, email) values ('00000000-0000-0000-0000-0000000000c5', 'g5@x.io');
-- R-1.5-20 only the fixed category keys are accepted
insert into public.items (giver_id, title, category) values ('00000000-0000-0000-0000-0000000000c5', 'Book', 'books');
select public.t_expect_error($q$insert into public.items (giver_id, title, category) values ('00000000-0000-0000-0000-0000000000c5', 'X', 'cars')$q$, 'items_category_chk');
\echo 'V1.5 SQL TESTS PASSED'
