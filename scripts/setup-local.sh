#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_dir="$(cd "${script_dir}/.." && pwd)"
java_runtime="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"

for command_name in go buf pnpm uv shasum; do
  if ! command -v "${command_name}" >/dev/null; then
    echo "Missing required command: ${command_name}" >&2
    exit 1
  fi
done
if [[ ! -x "${java_runtime}/bin/java" ]]; then
  echo "Java 17 runtime not found at ${java_runtime}. Set JAVA_HOME to a JDK 17 installation." >&2
  exit 1
fi

echo "[setup] dashboard dependencies"
(cd "${repo_dir}/apps/command" && pnpm install --frozen-lockfile)

echo "[setup] Go modules"
(cd "${repo_dir}/services/node" && go mod download)

echo "[setup] model environment"
(cd "${repo_dir}/models/route-decay" && uv sync --frozen)

echo "[setup] Android toolchain"
(cd "${repo_dir}/apps/field-android" && env JAVA_HOME="${java_runtime}" ./gradlew help)

echo "Local setup complete. Run: make verify"
