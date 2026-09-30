// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
// Copyright (C) 2026 Dina Elokhova (DinaSimple) — Free to Take, https://freetotake.app
// Daily photo cleanup (answer Q22): deletes photo files of publications that expired more than 14 days ago.
// Called by pg_cron (job "photo-cleanup-daily") with a shared secret from Vault; verify_jwt is off,
// the x-cleanup-token check below is the authentication. Runs with the service role (auto-injected env).
import { createClient } from "jsr:@supabase/supabase-js@2";

Deno.serve(async (req: Request) => {
  const admin = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);
  const token = req.headers.get("x-cleanup-token") ?? "";
  const { data: ok } = await admin.rpc("cleanup_token_ok", { p_token: token });
  if (ok !== true) return new Response("forbidden", { status: 403 });

  const { data: batch, error } = await admin.rpc("expired_photo_batch", { p_limit: 200 });
  if (error) return new Response(error.message, { status: 500 });

  const cleared: string[] = [];
  let removed = 0;
  for (const row of (batch ?? []) as { item_id: string; photo_paths: string[] }[]) {
    if (row.photo_paths.length > 0) {
      const { error: e } = await admin.storage.from("item-photos").remove(row.photo_paths);
      if (e) continue; // retried on the next run
      removed += row.photo_paths.length;
    }
    cleared.push(row.item_id);
  }
  if (cleared.length > 0) await admin.rpc("mark_photos_deleted", { p_item_ids: cleared });
  return Response.json({ items: cleared.length, photosRemoved: removed });
});
