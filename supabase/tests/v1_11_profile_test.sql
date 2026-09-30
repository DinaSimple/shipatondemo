-- SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
-- Copyright (C) 2026 Dina Elokhova (DinaSimple) — Free to Take, https://freetotake.app
-- v1.11 profile / notifications
\set ON_ERROR_STOP on
insert into auth.users (id, email) values
  ('00000000-0000-0000-0000-00000000a111', 'g11@x.io'),
  ('00000000-0000-0000-0000-00000000b111', 't11@x.io'),
  ('00000000-0000-0000-0000-00000000c111', 'n11@x.io');

-- R-1.11-20 own profile editable (name, prefs, city); nickname not
begin; set local role authenticated; select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000c111', true);
update public.profiles set public_name = 'Martin', home_city = 'Barcelona', notify_new_request = false where id = auth.uid();
select public.t_expect_error($q$update public.profiles set nickname = 'x' where id = auth.uid()$q$, 'permission denied');
commit;
begin; set local role authenticated; select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000b111', true);
update public.profiles set home_city = 'Madrid', notify_claim_approved = true where id = auth.uid();
commit;

-- R-1.11-21 publishing in Barcelona notifies Barcelona users with the preference on (not the publisher)
begin; set local role authenticated; select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000a111', true);
select public.publish_item('Bike11', '', 'random', 41.4, 2.2, 'P', 'A', 'Barcelona', '08005', current_date + 3, '10:00', '11:00', array['00000000-0000-0000-0000-00000000a111/b.jpg']);
commit;
do $$ begin
  assert (select count(*) from public.notifications n join public.items i on i.id = n.item_id where i.title = 'Bike11' and n.kind = 'new_giveaway_nearby') = 1;
end $$;

-- R-1.11-22 new request → publisher notified; approval → requester notified
begin; set local role authenticated; select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000b111', true);
select public.submit_claim((select id from public.items where title = 'Bike11'), 'hi', current_date + 3, '10:00');
commit;
begin; set local role authenticated; select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000a111', true);
do $$ begin assert (select count(*) from public.my_notifications where kind = 'new_request' and item_title = 'Bike11') = 1; end $$;
select public.approve_claim((select c.id from public.claims c join public.items i on i.id = c.item_id where i.title = 'Bike11'));
commit;
begin; set local role authenticated; select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000b111', true);
do $$ begin assert (select count(*) from public.my_notifications where kind = 'claim_approved') = 1; end $$;
update public.notifications set read_at = now() where user_id = auth.uid();
do $$ begin assert (select count(*) from public.my_notifications where read_at is null) = 0; end $$;
commit;
\echo 'V1.11 SQL TESTS PASSED'
