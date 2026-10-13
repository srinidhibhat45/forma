#!/usr/bin/env bash
set -euo pipefail
: "${ANDROID_HOME:?Android SDK location is required}"
: "${FORMA_KEYSTORE:?Forma signing keystore is required}"
: "${FORMA_STORE_PASSWORD:?Forma signing password is required}"
: "${FORMA_KEY_PASSWORD:?Forma key password is required}"
forma_input="${1:-android/app/build/outputs/apk/release/app-release-unsigned.apk}"
forma_output="${2:-output/forma-ota.apk}"
mkdir -p "$(dirname "$forma_output")"
"$ANDROID_HOME/build-tools/36.0.0/apksigner" sign \
  --ks "$FORMA_KEYSTORE" --ks-key-alias forma \
  --ks-pass env:FORMA_STORE_PASSWORD --key-pass env:FORMA_KEY_PASSWORD \
  --lineage android/signing/forma.lineage --rotation-min-sdk-version 28 \
  --v1-signing-enabled false --v2-signing-enabled false \
  --v3-signing-enabled true --v4-signing-enabled false \
  --debuggable-apk-permitted false --out "$forma_output" "$forma_input"
"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --verbose "$forma_output"
