#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_dir="$(cd "${script_dir}/.." && pwd)"
android_dir="${repo_dir}/apps/field-android"
java_runtime="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"

echo "[proto] lint shared wire contract"
(cd "${repo_dir}/packages/proto" && buf lint)

if rg -n '(^|[^A-Za-z])json([^A-Za-z]|$)' \
  "${repo_dir}/apps/field-android/app/src/main/java/com/example/digitaldelta/domain/mesh" \
  "${repo_dir}/services/node/internal/mesh" >/dev/null; then
  echo "JSON reference found in a mesh package; mesh transport must remain Protobuf-only." >&2
  exit 1
fi

echo "[android] unit tests and debug build"
(
  cd "${android_dir}"
  env JAVA_HOME="${java_runtime}" ./gradlew test assembleDebug
)

if [[ "${1:-}" == "--connected" ]]; then
  echo "[android] connected Compose journey tests"
  (
    cd "${android_dir}"
    env JAVA_HOME="${java_runtime}" ./gradlew connectedDebugAndroidTest
  )
fi

if [[ -f "${repo_dir}/services/node/go.mod" ]]; then
  echo "[go] race tests, vet, and node build"
  (
    cd "${repo_dir}/services/node"
    go test -race ./...
    go vet ./...
    go build ./cmd/delta-node
  )
fi

if [[ -f "${repo_dir}/apps/command/package.json" ]]; then
  echo "[command] tests and build"
  (cd "${repo_dir}/apps/command" && pnpm test --run && pnpm build)
fi

echo "Local verification passed."
