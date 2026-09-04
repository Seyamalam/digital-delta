#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_dir="$(cd "${script_dir}/.." && pwd)"
node_dir="${repo_dir}/services/node"
connections="${1:-10000}"
load_tmp="$(mktemp -d)"
server_pid=""
monitor_pid=""

cleanup() {
  if [[ -n "${monitor_pid}" ]]; then
    kill "${monitor_pid}" 2>/dev/null || true
    wait "${monitor_pid}" 2>/dev/null || true
  fi
  if [[ -n "${server_pid}" ]]; then
    kill "${server_pid}" 2>/dev/null || true
    wait "${server_pid}" 2>/dev/null || true
  fi
  rm -rf -- "${load_tmp}"
}
trap cleanup EXIT

echo "[load] build isolated server and client"
(
  cd "${node_dir}"
  go build -o "${load_tmp}/delta-node" ./cmd/delta-node
  go build -o "${load_tmp}/delta-load" ./cmd/delta-load
)

"${load_tmp}/delta-node" \
  --listen 127.0.0.1:27070 \
  --data "${load_tmp}/mesh.db" \
  --node load-sink >"${load_tmp}/server.log" 2>&1 &
server_pid="$!"

for _ in $(seq 1 100); do
  if nc -z 127.0.0.1 27070; then
    break
  fi
  sleep 0.1
done
if ! nc -z 127.0.0.1 27070; then
  echo "load-test server did not become ready" >&2
  exit 1
fi

echo 0 >"${load_tmp}/peak-rss-kb"
(
  while kill -0 "${server_pid}" 2>/dev/null; do
    rss="$(ps -o rss= -p "${server_pid}" | tr -d ' ')"
    peak="$(<"${load_tmp}/peak-rss-kb")"
    if [[ -n "${rss}" && "${rss}" -gt "${peak}" ]]; then
      echo "${rss}" >"${load_tmp}/peak-rss-kb"
    fi
    sleep 0.2
  done
) &
monitor_pid="$!"

echo "[load] open ${connections} independent gRPC streams"
"${load_tmp}/delta-load" \
  --target 127.0.0.1:27070 \
  --connections "${connections}" \
  --ramp 10s \
  --hold 5s \
  --timeout 3m

echo "server_peak_rss_kb=$(<"${load_tmp}/peak-rss-kb")"
server_log_errors="$(rg -c 'level=ERROR' "${load_tmp}/server.log" || true)"
echo "server_log_errors=${server_log_errors:-0}"
