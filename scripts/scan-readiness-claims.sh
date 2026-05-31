#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

scan_files=(
  "README.md"
  "docs/PRODUCTION_READINESS.md"
  "docs/SECURITY_ROADMAP.md"
)

for file in "${scan_files[@]}"; do
  if [[ ! -f "${file}" ]]; then
    echo "Missing readiness scan file: ${file}" >&2
    exit 1
  fi
done

forbidden_phrases=(
  "fully production-ready secure E2EE stack: Yes"
  "is full audited OMEMO E2EE lifecycle complete"
  "is fully audited OMEMO E2EE complete"
  "Signal/Double-Ratchet-equivalent OMEMO lifecycle completeness achieved"
)

for phrase in "${forbidden_phrases[@]}"; do
  for file in "${scan_files[@]}"; do
    if grep -Fqi "${phrase}" "${file}"; then
      echo "Forbidden readiness claim found in ${file}: ${phrase}" >&2
      exit 1
    fi
  done
done

echo "Readiness claim scan passed."
