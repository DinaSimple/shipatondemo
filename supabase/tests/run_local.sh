#!/usr/bin/env bash
# Runs migrations + all rule tests (v*_test.sql, in order) against a throwaway local PostgreSQL DB.
# Usage: PGHOST=... PGPORT=... PGUSER=postgres supabase/tests/run_local.sh
set -euo pipefail
cd "$(dirname "$0")/../.."
DB="ftt_test_$$"
createdb "$DB"
trap 'dropdb --if-exists "$DB"' EXIT
psql -q -v ON_ERROR_STOP=1 -d "$DB" -f supabase/tests/00_local_stub.sql
for f in supabase/migrations/*.sql; do psql -q -v ON_ERROR_STOP=1 -d "$DB" -f "$f"; done
for t in supabase/tests/v*_test.sql; do psql -q -o /dev/null -v ON_ERROR_STOP=1 -d "$DB" -f "$t"; done
