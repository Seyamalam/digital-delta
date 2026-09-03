#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_dir="$(cd "${script_dir}/.." && pwd)"
android_dir="${repo_dir}/apps/field-android"
model_dir="${repo_dir}/models/route-decay"
java_runtime="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"

echo "[proto] lint shared wire contract"
(cd "${repo_dir}/packages/proto" && buf lint)

if rg -n '(^|[^A-Za-z])json([^A-Za-z]|$)' \
  "${repo_dir}/apps/field-android/app/src/main/java/com/example/digitaldelta/domain/mesh" \
  "${repo_dir}/services/node/internal/mesh" >/dev/null; then
  echo "JSON reference found in a mesh package; mesh transport must remain Protobuf-only." >&2
  exit 1
fi

if [[ -f "${model_dir}/pyproject.toml" ]]; then
  echo "[model] reproduce synthetic dataset, metrics, and ONNX export"
  command -v uv >/dev/null || {
    echo "uv is required to verify the route-decay model." >&2
    exit 1
  }
  model_tmp="$(mktemp -d)"
  cleanup_model_tmp() {
    rm -rf -- "${model_tmp}"
  }
  trap cleanup_model_tmp EXIT
  (
    cd "${model_dir}"
    uv run --frozen train.py --output-dir "${model_tmp}"
  )
  for artifact in route_risk_v1.onnx metrics.json model_config.json synthetic_route_risk.csv; do
    cmp "${model_dir}/artifacts/${artifact}" "${model_tmp}/${artifact}"
  done
  cmp "${model_dir}/artifacts/route_risk_v1.onnx" \
    "${android_dir}/app/src/main/assets/route_risk_v1.onnx"
  cmp "${model_dir}/artifacts/model_config.json" \
    "${android_dir}/app/src/main/assets/route_risk_v1_config.json"
  cleanup_model_tmp
  trap - EXIT
fi

echo "[android] unit tests, debug build, and minified release build"
(
  cd "${android_dir}"
  env JAVA_HOME="${java_runtime}" ./gradlew test assembleDebug assembleRelease
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
    go build ./...
  )
fi

if [[ -f "${repo_dir}/apps/command/package.json" ]]; then
  echo "[command] tests and build"
  (cd "${repo_dir}/apps/command" && pnpm test --run && pnpm build)
fi

echo "Local verification passed."
