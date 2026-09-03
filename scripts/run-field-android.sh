#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_dir="$(cd "${script_dir}/.." && pwd)"
android_dir="${repo_dir}/apps/field-android"
java_runtime="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"
android_cli="${ANDROID_CLI:-/Users/seyam/.local/bin/android}"

(
  cd "${android_dir}"
  env JAVA_HOME="${java_runtime}" ./gradlew assembleDebug
  "${android_cli}" run \
    --apks=app/build/outputs/apk/debug/app-debug.apk \
    --activity=com.example.digitaldelta.MainActivity \
    "${@:1}"
)
