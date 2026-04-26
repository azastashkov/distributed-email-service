#!/usr/bin/env bash
# End-to-end smoke test against the running docker compose stack.
# Exits non-zero on first failure so CI can use it.

set -euo pipefail

BASE="${BASE:-http://localhost:28080}"
export NO_PROXY=localhost,127.0.0.1
export no_proxy=localhost,127.0.0.1

curl_json() { curl -fsS "$@"; }

echo "=== Signup Alice ==="
ALICE_EMAIL="alice-$(date +%s)@test.local"
ALICE=$(curl_json -X POST "$BASE/api/v1/auth/signup" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$ALICE_EMAIL\",\"password\":\"password123\",\"displayName\":\"Alice\"}")
ALICE_TOKEN=$(echo "$ALICE" | python3 -c "import sys,json;print(json.load(sys.stdin)['token'])")
echo "ok"

echo "=== Folders (expect 4 system folders) ==="
N=$(curl_json "$BASE/api/v1/folders" -H "Authorization: Bearer $ALICE_TOKEN" | python3 -c "import sys,json;print(len(json.load(sys.stdin)))")
[ "$N" = "4" ] || { echo "expected 4 folders, got $N"; exit 1; }
echo "ok"

echo "=== Signup Bob ==="
BOB_EMAIL="bob-$(date +%s)@test.local"
BOB=$(curl_json -X POST "$BASE/api/v1/auth/signup" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$BOB_EMAIL\",\"password\":\"password123\",\"displayName\":\"Bob\"}")
BOB_TOKEN=$(echo "$BOB" | python3 -c "import sys,json;print(json.load(sys.stdin)['token'])")
echo "ok"

echo "=== Alice → Bob email ==="
DRAFT=$(curl_json -X POST "$BASE/api/v1/emails/draft" \
  -H "Authorization: Bearer $ALICE_TOKEN" -H "Content-Type: application/json" \
  -d "{\"to\":[\"$BOB_EMAIL\"],\"cc\":[],\"bcc\":[],\"subject\":\"hi\",\"body\":\"the quick brown fox jumps over\",\"attachmentNames\":[]}")
EMAIL_ID=$(echo "$DRAFT" | python3 -c "import sys,json;print(json.load(sys.stdin)['emailId'])")
curl_json -X POST "$BASE/api/v1/emails/$EMAIL_ID/send" -H "Authorization: Bearer $ALICE_TOKEN" >/dev/null
echo "ok"

echo "=== Bob inbox (expect 1) ==="
INBOX=$(curl_json "$BASE/api/v1/folders" -H "Authorization: Bearer $BOB_TOKEN" \
  | python3 -c "import sys,json;print([f['folderId'] for f in json.load(sys.stdin) if f['name']=='INBOX'][0])")
ITEMS=$(curl_json "$BASE/api/v1/folders/$INBOX/emails?limit=5" -H "Authorization: Bearer $BOB_TOKEN" \
  | python3 -c "import sys,json;print(len(json.load(sys.stdin)['items']))")
[ "$ITEMS" = "1" ] || { echo "expected 1 inbox email, got $ITEMS"; exit 1; }
echo "ok"

echo "=== Bob unread (expect 1) ==="
sleep 1
UNREAD=$(curl_json "$BASE/api/v1/emails?status=unread&limit=5" -H "Authorization: Bearer $BOB_TOKEN" \
  | python3 -c "import sys,json;print(len(json.load(sys.stdin)['items']))")
[ "$UNREAD" = "1" ] || { echo "expected 1 unread, got $UNREAD"; exit 1; }
echo "ok"

echo "=== Search ==="
sleep 2
HITS=$(curl_json "$BASE/api/v1/search?q=brown&limit=5" -H "Authorization: Bearer $BOB_TOKEN" \
  | python3 -c "import sys,json;print(len(json.load(sys.stdin)['hits']))")
[ "$HITS" -ge 1 ] || { echo "search returned 0 hits"; exit 1; }
echo "ok"

echo "ALL OK"
