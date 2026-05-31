#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

required_files=(
  ".github/workflows/build.yml"
  ".github/workflows/docker-e2e.yml"
  ".github/workflows/publish.yml"
  "docs/PRODUCTION_READINESS.md"
  "docs/SECURITY_ROADMAP.md"
  "scripts/scan-marker-modules.sh"
  "scripts/scan-readiness-claims.sh"
  "scripts/verify-production.sh"
)

for file in "${required_files[@]}"; do
  if [[ ! -f "${file}" ]]; then
    echo "Missing required production file: ${file}" >&2
    exit 1
  fi
done

if ! grep -Fq "dependsOn(productionDockerSample)" "${ROOT_DIR}/build.gradle.kts"; then
  echo "Missing productionVerify gate dependency: productionDockerSample." >&2
  exit 1
fi

bash "${ROOT_DIR}/scripts/scan-readiness-claims.sh"
bash "${ROOT_DIR}/scripts/scan-marker-modules.sh"

omemo_claim_level="${KMPXMPP_OMEMO_CLAIM_LEVEL:-partial}"
if [[ "${omemo_claim_level}" != "partial" && "${omemo_claim_level}" != "full" ]]; then
  echo "Invalid KMPXMPP_OMEMO_CLAIM_LEVEL='${omemo_claim_level}'. Use 'partial' or 'full'." >&2
  exit 1
fi

readiness_file="docs/PRODUCTION_READINESS.md"
readme_file="README.md"
required_readiness_phrases=(
  "must **not** be marketed as full audited OMEMO E2EE lifecycle complete yet"
  "Partial OMEMO lifecycle implementation, not full audited E2EE lifecycle"
  "Baseline production claim excludes deprecated/deferred/experimental XEP modules from guaranteed behavior."
  "kmpxmpp-xep-0048-bookmarks"
  "XEP-0402"
  "SASL2 / channel-binding hardening is tracked explicitly in docs/SECURITY_ROADMAP.md."
)
required_readme_phrases=(
  "production-capable baseline chat workflows"
  "full audited OMEMO E2EE lifecycle complete yet"
  "Baseline production claim excludes deprecated/deferred/experimental XEP modules"
)

for phrase in "${required_readiness_phrases[@]}"; do
  if ! grep -Fq "${phrase}" "${readiness_file}"; then
    echo "Missing required readiness wording in ${readiness_file}: ${phrase}" >&2
    exit 1
  fi
done

for phrase in "${required_readme_phrases[@]}"; do
  if ! grep -Fq "${phrase}" "${readme_file}"; then
    echo "Missing required readiness wording in ${readme_file}: ${phrase}" >&2
    exit 1
  fi
done

if [[ "${omemo_claim_level}" == "full" ]]; then
  echo "KMPXMPP_OMEMO_CLAIM_LEVEL=full is not permitted by in-repo checks yet." >&2
  echo "Blocked reason: external cryptography audit evidence and full lifecycle assurance artifacts are not present." >&2
  echo "Use KMPXMPP_OMEMO_CLAIM_LEVEL=partial for current production baseline claim." >&2
  exit 1
fi

if [[ "${KMPXMPP_ENFORCE_PUBLISH_SECRETS:-false}" == "true" ]]; then
  required_env=(
    "ORG_GRADLE_PROJECT_mavenCentralUsername"
    "ORG_GRADLE_PROJECT_mavenCentralPassword"
    "ORG_GRADLE_PROJECT_signingInMemoryKeyId"
    "ORG_GRADLE_PROJECT_signingInMemoryKey"
    "ORG_GRADLE_PROJECT_signingInMemoryKeyPassword"
  )

  for var in "${required_env[@]}"; do
    if [[ -z "${!var:-}" ]]; then
      echo "Missing required publish environment variable: ${var}" >&2
      exit 1
    fi
  done
fi

echo "Release preflight passed."
