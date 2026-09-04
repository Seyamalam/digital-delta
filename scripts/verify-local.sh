#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_dir="$(cd "${script_dir}/.." && pwd)"
android_dir="${repo_dir}/apps/field-android"
model_dir="${repo_dir}/models/route-decay"
map_dir="${repo_dir}/apps/command/public/maps"
android_map_dir="${android_dir}/app/src/main/assets/maps"
java_runtime="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"

echo "[proto] lint shared wire contract"
(cd "${repo_dir}/packages/proto" && buf lint)

echo "[localization] verify Bangla and English resource parity and bundled font"
if ! diff -u \
  <(rg -o '<string name="[^"]+"' "${android_dir}/app/src/main/res/values/strings.xml" | sort) \
  <(rg -o '<string name="[^"]+"' "${android_dir}/app/src/main/res/values-bn/strings.xml" | sort); then
  echo "Bangla and English string resources must contain exactly the same keys." >&2
  exit 1
fi
(
  cd "${android_dir}/app/src/main/res"
  printf '%s  %s\n' \
    '6300c5370cd688b0641343de4c786de6d412bb6c578d129dae75e93a0322dcab' \
    'font/noto_sans_bengali_regular.ttf' | shasum -a 256 -c -
)

if [[ -f "${map_dir}/sylhet.pmtiles" ]]; then
  echo "[map] verify reviewed offline Sylhet archive"
  (cd "${map_dir}" && shasum -a 256 -c SHA256SUMS)
fi

if [[ -f "${android_map_dir}/sylhet_osm_basemap.geojson" ]]; then
  echo "[map] verify Android offline geographic extract"
  (cd "${android_map_dir}" && shasum -a 256 -c SHA256SUMS)
  archive_sha="$(shasum -a 256 "${map_dir}/sylhet.pmtiles" | cut -d ' ' -f 1)"
  embedded_archive_sha="$(jq -r '.metadata.archive_sha256' "${android_map_dir}/sylhet_osm_basemap.geojson")"
  if [[ "${archive_sha}" != "${embedded_archive_sha}" ]]; then
    echo "Android map provenance does not match the reviewed PMTiles archive." >&2
    exit 1
  fi
fi

echo "[scenario] compile simulated chaos fixture"
python3 -m py_compile "${repo_dir}/packages/scenario/chaos_server.py"

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

debug_apk="${android_dir}/app/build/outputs/apk/debug/app-debug.apk"
if ! unzip -Z1 "${debug_apk}" | rg -q '^assets/mlkit_barcode_models/.+\.tflite$'; then
  echo "Bundled ML Kit barcode model is missing from the debug APK; QR scanning must work offline." >&2
  exit 1
fi
echo "[android] bundled offline barcode model present in APK"

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
  echo "[command] tests, typecheck, and Next.js production build"
  (cd "${repo_dir}/apps/command" && pnpm test --run && pnpm typecheck && pnpm build)
fi

if [[ -f "${repo_dir}/services/headquarters-archive/package.json" ]]; then
  echo "[archive] tests, typecheck, and Cloudflare deployment dry run"
  (
    cd "${repo_dir}/services/headquarters-archive"
    pnpm test
    pnpm typecheck
    pnpm exec wrangler deploy --dry-run
  )
fi

echo "Local verification passed."
