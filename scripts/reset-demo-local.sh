#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_dir="$(cd "${script_dir}/.." && pwd)"
state_dir="${repo_dir}/.demo-state"

for port in 7070 7071; do
  if lsof -nP -iTCP:"${port}" -sTCP:LISTEN >/dev/null 2>&1; then
    echo "Stop the local demo before resetting; port ${port} is still in use." >&2
    exit 1
  fi
done

if [[ -d "${state_dir}" ]]; then
  backup_root="${repo_dir}/artifacts/demo-state-backups"
  backup_dir="${backup_root}/$(date '+%Y%m%d-%H%M%S')"
  mkdir -p "${backup_root}"
  mv "${state_dir}" "${backup_dir}"
  echo "Previous demo state moved to ${backup_dir}"
fi
mkdir -p "${state_dir}"
echo "Demo state reset to an empty store. The next seed is fair-pass-01."
