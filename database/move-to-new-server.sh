#!/usr/bin/env bash
#
# Move the database off Railway onto our own server.
#
# Dumps the source, restores it onto the target, and — before anything is
# trusted — compares every table's row count between the two. A restore that
# "looked fine" and quietly dropped a table is the failure this guards against.
#
# The source is left untouched throughout. That is deliberate: until the app is
# actually running against the new server, Railway is the rollback, and a
# rollback you have edited is not a rollback.
#
#   export SOURCE_URL='postgresql://postgres:PASS@reseau.proxy.rlwy.net:43951/railway'
#   export TARGET_URL='postgresql://fluxibiz:PASS@newhost:5432/fluxibiz'
#
#   ./database/move-to-new-server.sh preflight   # check both ends, change nothing
#   ./database/move-to-new-server.sh dump        # write a .dump locally
#   ./database/move-to-new-server.sh restore     # load it into the target
#   ./database/move-to-new-server.sh verify      # compare row counts
#   ./database/move-to-new-server.sh repair      # run 004 on the target
#   ./database/move-to-new-server.sh all         # every step, in order

set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
STAMP="$(date +%Y%m%d-%H%M%S)"
DUMP_DIR="${DUMP_DIR:-$HERE/dumps}"
DUMP_FILE="${DUMP_FILE:-$DUMP_DIR/fluxibiz-$STAMP.dump}"

bold() { printf '\033[1m%s\033[0m\n' "$*"; }
warn() { printf '\033[33m%s\033[0m\n' "$*"; }
die()  { printf '\033[31mERROR: %s\033[0m\n' "$*" >&2; exit 1; }
ok()   { printf '\033[32m  ok\033[0m %s\n' "$*"; }

need_env() {
    [ -n "${SOURCE_URL:-}" ] || die "SOURCE_URL is not set"
    [ -n "${TARGET_URL:-}" ] || die "TARGET_URL is not set"
}

# Server major version, e.g. 18
server_major() { psql "$1" -tAc "SHOW server_version_num" | cut -c1-2; }

# Every table and its row count, as "table<TAB>count", sorted. Built by asking
# the catalogue for the table list and counting each one for real — reltuples
# is an estimate and would report a clean move for a half-restored table.
counts_for() {
    local url="$1" q
    q=$(psql "$url" -tAc "
        SELECT coalesce(
          string_agg(format('SELECT %L AS t, count(*) AS n FROM %I', tablename, tablename),
                     ' UNION ALL '),
          'SELECT NULL::text AS t, 0::bigint AS n WHERE false')
        FROM pg_tables WHERE schemaname='public'")
    psql "$url" -tAF$'\t' -c "$q ORDER BY 1"
}

cmd_preflight() {
    need_env
    bold "Preflight"

    psql "$SOURCE_URL" -tAc "SELECT 1" >/dev/null 2>&1 || die "cannot reach SOURCE_URL"
    ok "source reachable"
    psql "$TARGET_URL" -tAc "SELECT 1" >/dev/null 2>&1 || die "cannot reach TARGET_URL"
    ok "target reachable"

    local src tgt dumpv
    src=$(server_major "$SOURCE_URL")
    tgt=$(server_major "$TARGET_URL")
    dumpv=$(pg_dump --version | grep -oE '[0-9]+' | head -1)

    echo "  source postgres : $src"
    echo "  target postgres : $tgt"
    echo "  local pg_dump   : $dumpv"

    [ "$tgt" -ge "$src" ] || die "target ($tgt) is older than source ($src); pg_restore will fail"
    ok "target version is compatible"
    [ "$dumpv" -ge "$src" ] || die "local pg_dump ($dumpv) is older than the source server ($src)"
    ok "pg_dump is new enough"

    local existing
    existing=$(psql "$TARGET_URL" -tAc \
        "SELECT count(*) FROM pg_tables WHERE schemaname='public'")
    if [ "$existing" -gt 0 ]; then
        warn "  target already holds $existing tables in public — restore would merge into them."
        warn "  Drop and recreate the target database first if you want a clean load."
    else
        ok "target public schema is empty"
    fi
}

cmd_dump() {
    need_env
    bold "Dump"
    mkdir -p "$DUMP_DIR"
    pg_dump "$SOURCE_URL" -Fc --no-owner --no-privileges -f "$DUMP_FILE"
    ok "$DUMP_FILE ($(du -h "$DUMP_FILE" | cut -f1))"
    echo "$DUMP_FILE" > "$DUMP_DIR/.latest"
}

latest_dump() {
    if [ -n "${DUMP_FILE_OVERRIDE:-}" ]; then echo "$DUMP_FILE_OVERRIDE"; return; fi
    [ -f "$DUMP_DIR/.latest" ] || die "no dump found — run '$0 dump' first"
    cat "$DUMP_DIR/.latest"
}

cmd_restore() {
    need_env
    bold "Restore"
    local f; f="$(latest_dump)"
    [ -f "$f" ] || die "dump file missing: $f"
    echo "  loading $f"
    # Errors are reported rather than fatal: a fresh target throws benign
    # "role does not exist" style noise that must not abort a good restore.
    pg_restore -d "$TARGET_URL" --no-owner --no-privileges -j 4 "$f" 2>&1 \
        | grep -viE 'does not exist|already exists' || true
    ok "restore finished — verify before trusting it"
}

cmd_verify() {
    need_env
    bold "Verify — row counts, source vs target"
    local s t
    s=$(mktemp); t=$(mktemp)
    counts_for "$SOURCE_URL" > "$s"
    counts_for "$TARGET_URL" > "$t"

    local diff_found=0
    printf '  %-32s %10s %10s\n' TABLE SOURCE TARGET
    while IFS=$'\t' read -r name n; do
        [ -n "$name" ] || continue
        local m; m=$(awk -F'\t' -v k="$name" '$1==k{print $2}' "$t")
        if [ -z "$m" ]; then
            printf '  \033[31m%-32s %10s %10s  MISSING\033[0m\n' "$name" "$n" "-"
            diff_found=1
        elif [ "$n" != "$m" ]; then
            printf '  \033[31m%-32s %10s %10s  DIFFERS\033[0m\n' "$name" "$n" "$m"
            diff_found=1
        fi
    done < "$s"

    local st tt
    st=$(wc -l < "$s"); tt=$(wc -l < "$t")
    echo "  tables: source $st, target $tt"
    rm -f "$s" "$t"

    if [ "$diff_found" -eq 0 ] && [ "$st" -eq "$tt" ]; then
        ok "every table matches"
    else
        die "target does not match source — do not cut over"
    fi
}

cmd_repair() {
    need_env
    bold "Repair — orphaned customer references (runs on TARGET only)"
    psql "$TARGET_URL" -v ON_ERROR_STOP=1 -q -f "$HERE/004_repair_orphan_customers.sql"
    ok "004 applied"
    psql "$TARGET_URL" -c "
      SELECT 'orphan orders' AS check, count(*) FROM orders
        WHERE customer_id IS NOT NULL AND customer_id NOT IN (SELECT id FROM customers)
      UNION ALL SELECT 'orphan carts', count(*) FROM carts
        WHERE customer_id NOT IN (SELECT id FROM customers)
      UNION ALL SELECT 'customer FKs present', count(*) FROM pg_constraint
        WHERE conname IN ('fkpxtb8awmi0dk6smoh2vp1litg','fk8ba3sryid5k8a9kidpkvqipyt',
                          'fkojm0jhhrky63bsx7vf59ha7rd','fk107p31licxrcj85qn4ntle3d');"
}

cmd_all() {
    cmd_preflight; echo
    cmd_dump;      echo
    cmd_restore;   echo
    cmd_verify;    echo
    cmd_repair;    echo
    bold "Done. The app is still pointed at the old server."
    cat <<'NEXT'

  Cut over by changing these on the app, and nothing else:

    PGHOST      -> the new host (private/internal address if there is one)
    PGPORT      -> the new port
    PGDATABASE  -> the new database name
    DB_USER     -> the new role
    DB_PASS     -> a NEW password, not the Railway one

  Then redeploy and watch the boot log for:

    Database JDBC URL [jdbc:postgresql://<new host>/...]
    Started IteSbApiApplication

  Run `verify` before pointing the app anywhere. ddl-auto is `update`, so
  Hibernate CREATES whatever it finds missing — an incomplete restore boots
  clean, with empty tables where the data should be, and nothing says so. The
  row-count check above is the only thing that catches it.

  For the same reason, never point the app at the new database before the
  restore finishes: Hibernate would build the whole schema empty, and you would
  then be restoring on top of tables it already made.

  Leave Railway running untouched for a few days.
NEXT
}

case "${1:-}" in
    preflight) cmd_preflight ;;
    dump)      cmd_dump ;;
    restore)   cmd_restore ;;
    verify)    cmd_verify ;;
    repair)    cmd_repair ;;
    all)       cmd_all ;;
    *) sed -n '2,28p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 1 ;;
esac
