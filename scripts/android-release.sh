#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

VERSION_NAME="$(
    sed -nE 's/^[[:space:]]*versionName[[:space:]]*=[[:space:]]*"([^"]+)".*/\1/p' app/build.gradle.kts | head -n 1
)"

if [[ -z "$VERSION_NAME" ]]; then
    echo "Could not read versionName from app/build.gradle.kts" >&2
    exit 1
fi

./gradlew assembleRelease

gh release create "v${VERSION_NAME}" \
    app/build/outputs/apk/release/app-release.apk \
    --repo WangChengYeh/Yoga \
    --title "v${VERSION_NAME}" \
    --notes "Android release v${VERSION_NAME}"
