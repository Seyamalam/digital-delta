#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_dir="$(cd "${script_dir}/.." && pwd)"
state_dir="${repo_dir}/.demo-state"
node_pid=""
dashboard_pid=""

cleanup() {
  if [[ -n "${dashboard_pid}" ]]; then
    kill "${dashboard_pid}" 2>/dev/null || true
    wait "${dashboard_pid}" 2>/dev/null || true
  fi
  if [[ -n "${node_pid}" ]]; then
    kill "${node_pid}" 2>/dev/null || true
    wait "${node_pid}" 2>/dev/null || true
  fi
}
trap cleanup EXIT INT TERM

for port in 7070 7071 5173; do
  if lsof -nP -iTCP:"${port}" -sTCP:LISTEN >/dev/null 2>&1; then
    echo "Port ${port} is already in use. Stop the previous demo process first." >&2
    exit 1
  fi
done
mkdir -p "${state_dir}"

echo "[demo] start Go gRPC node and local SSE observer"
(
  cd "${repo_dir}/services/node"
  exec go run ./cmd/delta-node \
    --data "${state_dir}/mesh.db" \
    --observer-data "${state_dir}/observer.db"
) &
node_pid="$!"

echo "[demo] start offline projector dashboard"
(
  cd "${repo_dir}/apps/command"
  exec pnpm dev --host 127.0.0.1
) &
dashboard_pid="$!"

for port in 7070 7071 5173; do
  ready=false
  for _ in $(seq 1 150); do
    if nc -z 127.0.0.1 "${port}"; then
      ready=true
      break
    fi
    sleep 0.1
  done
  if [[ "${ready}" != true ]]; then
    echo "Local demo service on port ${port} did not become ready." >&2
    exit 1
  fi
done

echo "[demo] publish deterministic, visibly simulated Protobuf drill"
(
  cd "${repo_dir}/services/node"
  go run ./cmd/delta-drill --seed fair-pass-01 --interval 120ms
)

echo ""
echo "Digital Delta is ready at http://127.0.0.1:5173/"
echo "Commercial internet is not required. Press Ctrl-C to stop the local services."
wait "${node_pid}" "${dashboard_pid}"
