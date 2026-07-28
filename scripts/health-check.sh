#!/usr/bin/env bash
set -euo pipefail

HEALTH_URL="${HEALTH_URL:-http://127.0.0.1:8080/actuator/health}"
HEALTH_RETRIES="${HEALTH_RETRIES:-30}"
HEALTH_INTERVAL_SECONDS="${HEALTH_INTERVAL_SECONDS:-2}"
HEALTH_CONNECT_TIMEOUT_SECONDS="${HEALTH_CONNECT_TIMEOUT_SECONDS:-2}"
HEALTH_MAX_TIME_SECONDS="${HEALTH_MAX_TIME_SECONDS:-5}"
LABEL="Backend"

usage() {
  cat <<'USAGE'
Usage: health-check.sh [--label LABEL]

Checks the direct Spring Boot actuator endpoint until it returns HTTP success
and a JSON status of UP, or until the configured retry limit is exhausted.
USAGE
}

log() {
  printf '[backend-health] %s\n' "$*"
}

require_positive_integer() {
  local name="$1"
  local value="$2"
  [[ "$value" =~ ^[1-9][0-9]*$ ]] || {
    printf '[backend-health] ERROR: %s must be a positive integer\n' "$name" >&2
    exit 2
  }
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --label)
      [[ -n "${2:-}" ]] || {
        printf '[backend-health] ERROR: --label requires a value\n' >&2
        exit 2
      }
      LABEL="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      usage >&2
      printf '[backend-health] ERROR: unknown argument: %s\n' "$1" >&2
      exit 2
      ;;
  esac
done

require_positive_integer "HEALTH_RETRIES" "$HEALTH_RETRIES"
require_positive_integer "HEALTH_INTERVAL_SECONDS" "$HEALTH_INTERVAL_SECONDS"
require_positive_integer "HEALTH_CONNECT_TIMEOUT_SECONDS" "$HEALTH_CONNECT_TIMEOUT_SECONDS"
require_positive_integer "HEALTH_MAX_TIME_SECONDS" "$HEALTH_MAX_TIME_SECONDS"

for ((attempt = 1; attempt <= HEALTH_RETRIES; attempt++)); do
  response=""
  if response="$(
    curl \
      --fail \
      --silent \
      --show-error \
      --connect-timeout "$HEALTH_CONNECT_TIMEOUT_SECONDS" \
      --max-time "$HEALTH_MAX_TIME_SECONDS" \
      "$HEALTH_URL"
  )" && grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"' <<<"$response"; then
    log "$LABEL health check passed on attempt $attempt/$HEALTH_RETRIES"
    exit 0
  fi

  if ((attempt < HEALTH_RETRIES)); then
    log "$LABEL health check failed on attempt $attempt/$HEALTH_RETRIES; retrying in ${HEALTH_INTERVAL_SECONDS}s"
    sleep "$HEALTH_INTERVAL_SECONDS"
  fi
done

printf '[backend-health] ERROR: %s health check failed after %s attempts\n' \
  "$LABEL" \
  "$HEALTH_RETRIES" >&2
exit 1
