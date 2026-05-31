#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

cd "${ROOT_DIR}"

./gradlew productionVerify --no-daemon --stacktrace
bash kmpxmpp-sample-whatsapp-jvm/scripts/run-whatsapp-docker-sample.sh
./gradlew publishToMavenLocal --no-daemon --stacktrace

echo "Production verification passed."
