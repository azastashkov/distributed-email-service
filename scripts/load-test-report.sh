#!/usr/bin/env bash
# Pulls a snapshot of load-test metrics from Prometheus and prints a human report.
# Run this while load-client is active or just after it finishes.

set -e
export NO_PROXY=localhost,127.0.0.1

PROM=${PROM:-http://localhost:29090}

q() {
  local query="$1"
  curl -fsS --data-urlencode "query=$query" "$PROM/api/v1/query"
}

scalar() {
  q "$1" | python3 -c "import sys,json;d=json.load(sys.stdin);v=d['data']['result'];print(float(v[0]['value'][1]) if v else 0)"
}

echo "===== Load Test Report ====="
echo

echo "--- Per-op rate (ops/sec, 30s window) ---"
q 'sum by (op,status) (rate(loadclient_op_duration_seconds_count[30s]))' \
  | python3 -c "
import sys,json
d=json.load(sys.stdin)
for r in sorted(d['data']['result'], key=lambda x:(x['metric'].get('op',''), x['metric'].get('status',''))):
  print(f\"  {r['metric'].get('op','?'):14s} {r['metric'].get('status','?'):3s} {float(r['value'][1]):.2f}/s\")
"
echo

echo "--- Per-op p95 latency (ms, 30s window) ---"
q 'histogram_quantile(0.95, sum by (le,op) (rate(loadclient_op_duration_seconds_bucket[30s])))*1000' \
  | python3 -c "
import sys,json
d=json.load(sys.stdin)
for r in sorted(d['data']['result'], key=lambda x:x['metric'].get('op','')):
  v=r['value'][1]
  if v=='NaN': continue
  print(f\"  {r['metric'].get('op','?'):14s} {float(v):.1f} ms\")
"
echo

echo "--- Total errors ---"
TOTAL_ERR=$(scalar 'sum(loadclient_op_errors_total)')
echo "  $TOTAL_ERR"
echo

echo "--- Web RPS per instance (LB balance) ---"
q 'sum by (instance) (rate(http_server_requests_seconds_count{service="web"}[30s]))' \
  | python3 -c "
import sys,json
d=json.load(sys.stdin)
for r in sorted(d['data']['result'], key=lambda x:x['metric'].get('instance','')):
  print(f\"  {r['metric'].get('instance','?')}: {float(r['value'][1]):.2f}/s\")
"
echo

echo "--- Web 5xx rate ---"
RATE=$(scalar 'sum(rate(http_server_requests_seconds_count{service="web",status=~"5.."}[30s]))')
echo "  $RATE per second"
echo

echo "--- WebSocket sessions per instance ---"
q 'ws_sessions_active' \
  | python3 -c "
import sys,json
d=json.load(sys.stdin)
for r in d['data']['result']:
  print(f\"  {r['metric'].get('instance','?')}: {r['value'][1]} sessions\")
"
echo

echo "--- WS messages sent/sec ---"
echo "  total: $(scalar 'sum(rate(ws_messages_sent_total[30s]))')/s"
echo

echo "--- WS cross-instance forwards/sec ---"
echo "  total: $(scalar 'sum(rate(ws_cross_instance_forwards_total[30s]))')/s"
echo

echo "--- WS delivery lag (ms) ---"
echo "  p50: $(scalar 'histogram_quantile(0.50, sum by (le) (rate(loadclient_ws_delivery_lag_seconds_bucket[30s])))*1000') ms"
echo "  p95: $(scalar 'histogram_quantile(0.95, sum by (le) (rate(loadclient_ws_delivery_lag_seconds_bucket[30s])))*1000') ms"
echo "  p99: $(scalar 'histogram_quantile(0.99, sum by (le) (rate(loadclient_ws_delivery_lag_seconds_bucket[30s])))*1000') ms"
echo

echo "--- Cassandra ring ---"
docker exec cassandra-1 nodetool status | tail -6
echo

echo "===== End of Report ====="
