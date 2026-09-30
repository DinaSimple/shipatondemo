# Security

This is the **public demo copy** of Free to Take. It contains **no secrets**:

- no API keys, tokens, passwords, signing keys or `local.properties` / `keystore.properties`;
- the production Supabase project reference is replaced with `YOUR_PROJECT_REF`;
- the image-captcha answers are removed from `supabase/migrations/20261012000000_v1_13_image_captcha.sql`;
- the git history starts fresh (no earlier commits are included).

All keys are read at build time from `local.properties` (see `local.properties.example`), which is git-ignored.
Server-side secrets live only in the Supabase Vault / Edge Function environment.

Found a vulnerability? Please report it privately via https://freetotake.app instead of opening a public issue.
