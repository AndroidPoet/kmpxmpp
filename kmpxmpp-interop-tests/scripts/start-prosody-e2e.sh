#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DOCKER_DIR="${SCRIPT_DIR}/../docker"
COMPOSE_FILE="${DOCKER_DIR}/docker-compose.prosody.yml"
COMPOSE_CMD="docker-compose"
PROJECT_NAME="${KMPXMPP_PROSODY_PROJECT_NAME:-kmpxmpp_prosody_e2e}"

if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
  COMPOSE_CMD="docker compose"
fi

${COMPOSE_CMD} -p "${PROJECT_NAME}" -f "${COMPOSE_FILE}" up -d

for i in {1..30}; do
  if ${COMPOSE_CMD} -p "${PROJECT_NAME}" -f "${COMPOSE_FILE}" ps --status running --services | grep -qx "prosody"; then
    break
  fi
  sleep 1
done

if ! ${COMPOSE_CMD} -p "${PROJECT_NAME}" -f "${COMPOSE_FILE}" ps --status running --services | grep -qx "prosody"; then
  ${COMPOSE_CMD} -p "${PROJECT_NAME}" -f "${COMPOSE_FILE}" logs prosody || true
  echo "Prosody container did not reach running state." >&2
  exit 1
fi

for i in {1..30}; do
  if ${COMPOSE_CMD} -p "${PROJECT_NAME}" -f "${COMPOSE_FILE}" exec -T prosody sh -lc "prosodyctl about >/dev/null 2>&1"; then
    break
  fi
  sleep 1
done

if ! ${COMPOSE_CMD} -p "${PROJECT_NAME}" -f "${COMPOSE_FILE}" exec -T prosody sh -lc "prosodyctl about >/dev/null 2>&1"; then
  ${COMPOSE_CMD} -p "${PROJECT_NAME}" -f "${COMPOSE_FILE}" logs prosody || true
  echo "Prosody service did not become ready." >&2
  exit 1
fi

register_user() {
  local username="$1"
  local domain="$2"
  local password="$3"

  for attempt in {1..20}; do
    if output="$(${COMPOSE_CMD} -p "${PROJECT_NAME}" -f "${COMPOSE_FILE}" exec -T prosody prosodyctl register "${username}" "${domain}" "${password}" 2>&1)"; then
      return 0
    fi

    if echo "${output}" | grep -qi "exists"; then
      return 0
    fi

    if echo "${output}" | grep -qi "not running"; then
      ${COMPOSE_CMD} -p "${PROJECT_NAME}" -f "${COMPOSE_FILE}" up -d >/dev/null 2>&1 || true
    fi

    sleep 1
  done

  echo "Failed to register ${username}@${domain}: ${output}" >&2
  return 1
}

register_user "alice" "localhost" "strong-password"
register_user "bob" "localhost" "strong-password"
