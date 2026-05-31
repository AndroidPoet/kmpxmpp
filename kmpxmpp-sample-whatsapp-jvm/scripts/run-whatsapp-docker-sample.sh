#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
START_SCRIPT="${ROOT_DIR}/kmpxmpp-interop-tests/scripts/start-prosody-e2e.sh"
STOP_SCRIPT="${ROOT_DIR}/kmpxmpp-interop-tests/scripts/stop-prosody-e2e.sh"
PROJECT_NAME="kmpxmpp_sample_whatsapp_e2e"

cleanup() {
  KMPXMPP_PROSODY_PROJECT_NAME="${PROJECT_NAME}" bash "${STOP_SCRIPT}" || true
}
trap cleanup EXIT

KMPXMPP_PROSODY_PROJECT_NAME="${PROJECT_NAME}" bash "${START_SCRIPT}"

cd "${ROOT_DIR}"
./gradlew :kmpxmpp-sample-whatsapp-jvm:run --no-daemon
