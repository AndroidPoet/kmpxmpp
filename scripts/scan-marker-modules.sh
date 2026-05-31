#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

# Marker files that represent module stubs should not reappear.
forbidden_stub_files=(
  "kmpxmpp-bind/src/commonMain/kotlin/io/github/androidpoet/kmpxmpp/bind/ModuleMarker.kt"
  "kmpxmpp-plugin-api/src/commonMain/kotlin/io/github/androidpoet/kmpxmpp/pluginapi/ModuleMarker.kt"
  "kmpxmpp-crypto-store/src/commonMain/kotlin/io/github/androidpoet/kmpxmpp/cryptostore/ModuleMarker.kt"
  "kmpxmpp-testkit/src/commonMain/kotlin/io/github/androidpoet/kmpxmpp/testkit/ModuleMarker.kt"
  "kmpxmpp-bom/src/commonMain/kotlin/io/github/androidpoet/kmpxmpp/bom/ModuleMarker.kt"
  "kmpxmpp-compliance/src/commonMain/kotlin/io/github/androidpoet/kmpxmpp/compliance/ModuleMarker.kt"
)

for file in "${forbidden_stub_files[@]}"; do
  if [[ -f "${file}" ]]; then
    echo "Marker-only module stub reintroduced: ${file}" >&2
    exit 1
  fi
done

echo "Marker module scan passed."
