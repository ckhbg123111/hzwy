#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="${ENV_FILE:-$SCRIPT_DIR/.env}"

load_env_file() {
  local env_file="$1"
  local line
  local key
  local value

  while IFS= read -r line || [[ -n "$line" ]]; do
    line="${line%$'\r'}"

    if [[ -z "${line//[[:space:]]/}" || "$line" =~ ^[[:space:]]*# ]]; then
      continue
    fi

    if [[ "$line" =~ ^[[:space:]]*([A-Za-z_][A-Za-z0-9_]*)=(.*)$ ]]; then
      key="${BASH_REMATCH[1]}"
      value="${BASH_REMATCH[2]}"

      if [[ -n "${!key+x}" ]]; then
        continue
      fi

      if [[ "$value" =~ ^\".*\"$ || "$value" =~ ^\'.*\'$ ]]; then
        value="${value:1:-1}"
      fi

      printf -v "$key" '%s' "$value"
      export "$key"
      continue
    fi

    echo "Skipping invalid line in ${env_file}: ${line}" >&2
  done < "$env_file"
}

if [[ -f "${ENV_FILE}" ]]; then
  echo "Loading environment variables from ${ENV_FILE}..."
  load_env_file "${ENV_FILE}"
fi

DOCKERFILE_PATH="${DOCKERFILE_PATH:-$SCRIPT_DIR/server/Dockerfile}"
BUILD_CONTEXT="${BUILD_CONTEXT:-$SCRIPT_DIR/server}"
IMAGE_NAME="${IMAGE_NAME:-boardgame-platform-server}"
CONTAINER_NAME="${CONTAINER_NAME:-boardgame-platform-server}"
HOST_PORT="${HOST_PORT:-8079}"
CONTAINER_PORT="${CONTAINER_PORT:-8079}"
RESTART_POLICY="${RESTART_POLICY:-unless-stopped}"

if ! command -v docker >/dev/null 2>&1; then
  echo "docker is required but was not found in PATH." >&2
  exit 1
fi

if [[ ! -f "${DOCKERFILE_PATH}" ]]; then
  echo "Dockerfile was not found at ${DOCKERFILE_PATH}." >&2
  exit 1
fi

if ! docker info >/dev/null 2>&1; then
  echo "docker daemon is not reachable. Please start Docker first." >&2
  exit 1
fi

echo "Building image ${IMAGE_NAME} from ${DOCKERFILE_PATH}..."
docker build -t "${IMAGE_NAME}" -f "${DOCKERFILE_PATH}" "${BUILD_CONTEXT}"

EXISTING_CONTAINER_ID="$(docker ps -aq --filter "name=^/${CONTAINER_NAME}$")"
if [[ -n "${EXISTING_CONTAINER_ID}" ]]; then
  echo "Removing existing container ${CONTAINER_NAME}..."
  docker rm -f "${CONTAINER_NAME}" >/dev/null
fi

RUN_ARGS=(
  -d
  --name "${CONTAINER_NAME}"
  --restart "${RESTART_POLICY}"
  -p "${HOST_PORT}:${CONTAINER_PORT}"
  -e "SERVER_PORT=${CONTAINER_PORT}"
  -e "JAVA_OPTS=${JAVA_OPTS:-}"
)

if [[ -n "${BOARDGAME_OPS_TOKEN:-}" ]]; then
  RUN_ARGS+=(-e "BOARDGAME_OPS_TOKEN=${BOARDGAME_OPS_TOKEN}")
fi

if [[ -n "${SPRING_PROFILES_ACTIVE:-}" ]]; then
  RUN_ARGS+=(-e "SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE}")
fi

echo "Starting container ${CONTAINER_NAME}..."
docker run "${RUN_ARGS[@]}" "${IMAGE_NAME}" >/dev/null

echo "Deploy completed."
echo "Container: ${CONTAINER_NAME}"
echo "Image: ${IMAGE_NAME}"
echo "URL: http://localhost:${HOST_PORT}"
echo "Logs: docker logs -f ${CONTAINER_NAME}"
