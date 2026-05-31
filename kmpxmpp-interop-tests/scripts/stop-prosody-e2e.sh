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

${COMPOSE_CMD} -p "${PROJECT_NAME}" -f "${COMPOSE_FILE}" down -v
