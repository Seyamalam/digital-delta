#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_dir="$(cd "${script_dir}/.." && pwd)"
state_dir="${repo_dir}/.demo-state"
node_pid=""
dashboard_pid=""
observer_pid=""

cleanup() {
  if [[ -n "${observer_pid}" ]]; then
    kill "${observer_pid}" 2>/dev/null || true
    wait "${observer_pid}" 2>/dev/null || true
  fi
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

for port in 7070 7071 3000; do
  if lsof -nP -iTCP:"${port}" -sTCP:LISTEN >/dev/null 2>&1; then
    echo "Port ${port} is already in use. Stop the previous demo process first." >&2
    exit 1
  fi
done
mkdir -p "${state_dir}"

node "${repo_dir}/scripts/observer-local.mjs" setup
(
  cd "${repo_dir}/services/headquarters-archive"
  pnpm exec wrangler d1 migrations apply digital-delta-hq --local --persist-to "${state_dir}/observer"
)
echo "[demo] start Hono observer with local D1 (no commercial internet)"
(
  cd "${repo_dir}/services/headquarters-archive"
  exec pnpm exec wrangler dev --local --ip 127.0.0.1 --port 7071 --persist-to "${state_dir}/observer"
) &
observer_pid="$!"
echo "[demo] start Go gRPC mesh harness"
(
  cd "${repo_dir}/services/node"
  exec go run ./cmd/delta-node \
    --data "${state_dir}/mesh.db"
) &
node_pid="$!"

echo "[demo] start offline projector dashboard"
(
  cd "${repo_dir}/apps/command"
  exec pnpm dev --hostname 127.0.0.1 --port 3000
) &
dashboard_pid="$!"

for port in 7070 7071 3000; do
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

echo "[demo] publish allowlisted, visibly simulated observations to Hono"
node "${repo_dir}/scripts/observer-local.mjs" seed

echo ""
echo "Digital Delta is ready at http://127.0.0.1:3000/"
echo "Commercial internet is not required. Press Ctrl-C to stop the local services."
wait "${node_pid}" "${observer_pid}" "${dashboard_pid}"
