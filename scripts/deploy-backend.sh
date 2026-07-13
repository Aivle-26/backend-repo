#!/usr/bin/env bash
set -euo pipefail

APP_DIR="${APP_DIR:-$HOME/app/backend}"
DEPLOY_DIR="${DEPLOY_DIR:-/opt/aipm/backend}"
SERVICE_NAME="${SERVICE_NAME:-aipm-backend}"
HEALTH_URL="${HEALTH_URL:-http://127.0.0.1:8080/actuator/health}"
GRADLE_OPTS="${GRADLE_OPTS:--Dorg.gradle.jvmargs=-Xmx512m}"
BUILD_ARGS=(clean bootJar --no-daemon --max-workers=1)
DO_PULL=false
SKIP_TESTS=false
TARGET_BRANCH=""

usage() {
  cat <<USAGE
Usage: scripts/deploy-backend.sh [--pull] [--skip-tests] [--branch BRANCH]

Builds the backend, atomically replaces /opt/aipm/backend/app.jar, restarts
systemd, and verifies the actuator health endpoint. Secrets are read from
/etc/aipm/backend.env by systemd and are not printed by this script.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --pull)
      DO_PULL=true
      shift
      ;;
    --skip-tests)
      SKIP_TESTS=true
      shift
      ;;
    --branch)
      TARGET_BRANCH="${2:-}"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

cd "$APP_DIR"

if [[ -n "$TARGET_BRANCH" ]]; then
  git switch "$TARGET_BRANCH"
fi

if [[ "$DO_PULL" == true ]]; then
  git pull --ff-only
fi

if [[ "$SKIP_TESTS" == true ]]; then
  BUILD_ARGS+=(-x test)
fi

java_major="$(java -version 2>&1 | awk -F'[\".]' '/version/ {print $2; exit}')"
if [[ "$java_major" != "21" ]]; then
  echo "Java 21 is required; detected major version: ${java_major:-unknown}" >&2
  exit 1
fi

export GRADLE_OPTS
./gradlew "${BUILD_ARGS[@]}"

jar_path="$(find build/libs -maxdepth 1 -type f -name '*.jar' ! -name '*plain.jar' | sort | tail -n 1)"
if [[ -z "$jar_path" ]]; then
  echo "No executable jar found in build/libs" >&2
  exit 1
fi

sudo mkdir -p "$DEPLOY_DIR/logs"
sudo chown -R ec2-user:ec2-user "$DEPLOY_DIR"

tmp_jar="$DEPLOY_DIR/app.jar.new"
backup_jar="$DEPLOY_DIR/app.jar.$(date +%Y%m%d_%H%M%S).bak"
cp "$jar_path" "$tmp_jar"

if [[ -f "$DEPLOY_DIR/app.jar" ]]; then
  cp "$DEPLOY_DIR/app.jar" "$backup_jar"
fi

mv "$tmp_jar" "$DEPLOY_DIR/app.jar"

if ! sudo systemctl restart "$SERVICE_NAME"; then
  echo "Failed to restart $SERVICE_NAME" >&2
  sudo journalctl -u "$SERVICE_NAME" -n 100 --no-pager >&2 || true
  if [[ -f "$backup_jar" ]]; then
    cp "$backup_jar" "$DEPLOY_DIR/app.jar"
    sudo systemctl restart "$SERVICE_NAME" || true
  fi
  exit 1
fi

for attempt in {1..30}; do
  if curl -fsS "$HEALTH_URL" >/dev/null; then
    echo "Deployment succeeded: $HEALTH_URL"
    exit 0
  fi
  sleep 2
done

echo "Health check failed: $HEALTH_URL" >&2
sudo journalctl -u "$SERVICE_NAME" -n 100 --no-pager >&2 || true
if [[ -f "$backup_jar" ]]; then
  cp "$backup_jar" "$DEPLOY_DIR/app.jar"
  sudo systemctl restart "$SERVICE_NAME" || true
fi
exit 1
