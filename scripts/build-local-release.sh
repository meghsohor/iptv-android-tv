#!/usr/bin/env bash
# Builds and signs a release APK locally, without needing CI or a push to main.
# Usage: scripts/build-local-release.sh [output-path]
#
# Requires the release keystore + its passwords, kept OUTSIDE this repo (never commit
# a keystore or its passwords to git) — see ~/Shuvo/Documents/iptv-android-tv-signing/.
# Override their location with MEGHTV_KEYSTORE / MEGHTV_KEYSTORE_CREDS if you ever move them.
set -euo pipefail

ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
JAVA_BIN="/opt/homebrew/opt/openjdk@17/bin"
KEYSTORE="${MEGHTV_KEYSTORE:-$HOME/Shuvo/Documents/iptv-android-tv-signing/release.keystore}"
CREDS_FILE="${MEGHTV_KEYSTORE_CREDS:-$HOME/Shuvo/Documents/iptv-android-tv-signing/passwords.txt}"
KEY_ALIAS="iptvtv-release"

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_ROOT"

if [[ ! -f "$KEYSTORE" ]]; then
  echo "Keystore not found at $KEYSTORE (set MEGHTV_KEYSTORE to override)" >&2
  exit 1
fi
if [[ ! -f "$CREDS_FILE" ]]; then
  echo "Keystore credentials not found at $CREDS_FILE (set MEGHTV_KEYSTORE_CREDS to override)" >&2
  exit 1
fi
# shellcheck disable=SC1090
source "$CREDS_FILE"

BUILD_TOOLS="$(find "$ANDROID_HOME/build-tools" -maxdepth 1 -mindepth 1 -type d | sort -V | tail -1)"
if [[ -z "$BUILD_TOOLS" ]]; then
  echo "No build-tools found under $ANDROID_HOME/build-tools" >&2
  exit 1
fi

VERSION_NAME="$(sed -n 's/.*versionName *= *"\([^"]*\)".*/\1/p' app/build.gradle.kts | head -1)"
OUTPUT="${1:-$HOME/Downloads/MeghTV-v${VERSION_NAME}-local.apk}"

export PATH="$JAVA_BIN:$PATH"

echo "Building release APK (versionName $VERSION_NAME)..."
./gradlew assembleRelease

TMP_ALIGNED="$(mktemp -t meghtv-aligned).apk"
trap 'rm -f "$TMP_ALIGNED"' EXIT

echo "Zipaligning..."
"$BUILD_TOOLS/zipalign" -f -p 4 \
  app/build/outputs/apk/release/app-release-unsigned.apk \
  "$TMP_ALIGNED"

echo "Signing..."
"$BUILD_TOOLS/apksigner" sign \
  --ks "$KEYSTORE" \
  --ks-pass "pass:$STORE_PW" \
  --ks-key-alias "$KEY_ALIAS" \
  --out "$OUTPUT" \
  "$TMP_ALIGNED"

echo "Done: $OUTPUT"
