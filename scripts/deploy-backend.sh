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
ARTIFACT_PATH=""
REVISION=""
HEALTH_RETRIES="${HEALTH_RETRIES:-30}"
HEALTH_SLEEP_SECONDS="${HEALTH_SLEEP_SECONDS:-2}"
BACKUP_KEEP_COUNT="${BACKUP_KEEP_COUNT:-10}"
SUDO=(sudo -n)

usage() {
  cat <<USAGE
Usage: scripts/deploy-backend.sh [--pull] [--skip-tests] [--branch BRANCH]
       scripts/deploy-backend.sh --artifact /tmp/aipm-backend-COMMIT.jar [--revision COMMIT]

Manual mode builds the backend from the local Git checkout. Artifact mode deploys
an already-built Spring Boot jar, which is intended for GitHub Actions.

The script atomically replaces /opt/aipm/backend/app.jar, restarts systemd,
verifies the actuator health endpoint, and rolls back to the previous jar on
failure. Secrets are read by systemd from /etc/aipm/backend.env and are not
printed by this script.
USAGE
}

log() {
  printf '[deploy] %s\n' "$*"
}

die() {
  printf '[deploy] ERROR: %s\n' "$*" >&2
  exit 1
}

require_value() {
  local option="$1"
  local value="${2:-}"
  [[ -n "$value" ]] || die "$option requires a value"
}

check_java_21() {
  local java_major
  java_major="$(java -version 2>&1 | awk -F'[\".]' '/version/ {print $2; exit}')"
  if [[ "$java_major" != "21" ]]; then
    die "Java 21 is required; detected major version: ${java_major:-unknown}"
  fi
}

check_sudo_noninteractive() {
  log "Checking non-interactive sudo"
  if ! "${SUDO[@]}" true; then
    die "Passwordless sudo is required for deployment. Configure sudoers or run manually."
  fi
}

cleanup_uploaded_artifact() {
  if [[ -n "$ARTIFACT_PATH" && "$ARTIFACT_PATH" == /tmp/aipm-backend-*.jar && -f "$ARTIFACT_PATH" && ! -L "$ARTIFACT_PATH" ]]; then
    rm -f "$ARTIFACT_PATH"
  fi
}

cleanup_deploy_temps() {
  local paths=()

  [[ -n "${tmp_jar:-}" ]] && paths+=("$tmp_jar")
  [[ -n "${tmp_revision:-}" ]] && paths+=("$tmp_revision")
  [[ -n "${rollback_jar:-}" ]] && paths+=("$rollback_jar")
  [[ -n "${rollback_revision:-}" ]] && paths+=("$rollback_revision")
  [[ -n "${backup_jar_tmp:-}" ]] && paths+=("$backup_jar_tmp")
  [[ -n "${backup_revision_tmp:-}" ]] && paths+=("$backup_revision_tmp")

  if [[ "${#paths[@]}" -gt 0 ]]; then
    "${SUDO[@]}" rm -f "${paths[@]}" 2>/dev/null || true
  fi
}

cleanup_on_exit() {
  cleanup_deploy_temps
  cleanup_uploaded_artifact
}

wait_for_health() {
  local label="$1"
  local attempt

  for attempt in $(seq 1 "$HEALTH_RETRIES"); do
    if curl --fail --silent --show-error "$HEALTH_URL" >/dev/null; then
      log "$label health check succeeded: $HEALTH_URL"
      return 0
    fi

    log "$label health check attempt $attempt/$HEALTH_RETRIES failed; retrying in ${HEALTH_SLEEP_SECONDS}s"
    sleep "$HEALTH_SLEEP_SECONDS"
  done

  log "$label health check failed after $HEALTH_RETRIES attempts" >&2
  return 1
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
      require_value "$1" "${2:-}"
      TARGET_BRANCH="${2:-}"
      shift 2
      ;;
    --artifact)
      require_value "$1" "${2:-}"
      ARTIFACT_PATH="${2:-}"
      shift 2
      ;;
    --revision)
      require_value "$1" "${2:-}"
      REVISION="${2:-}"
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

if [[ -n "$ARTIFACT_PATH" && ( "$DO_PULL" == true || "$SKIP_TESTS" == true || -n "$TARGET_BRANCH" ) ]]; then
  die "--artifact cannot be combined with --pull, --skip-tests, or --branch"
fi

check_java_21

if [[ -n "$ARTIFACT_PATH" ]]; then
  log "Deploying pre-built artifact"
  trap cleanup_on_exit EXIT

  if [[ ! -f "$ARTIFACT_PATH" ]]; then
    die "Artifact does not exist or is not a regular file: $ARTIFACT_PATH"
  fi

  jar_path="$ARTIFACT_PATH"
else
  log "Building artifact from local checkout"
  cd "$APP_DIR"

  if [[ -n "$TARGET_BRANCH" ]]; then
    log "Switching to branch: $TARGET_BRANCH"
    git switch "$TARGET_BRANCH"
  fi

  if [[ "$DO_PULL" == true ]]; then
    log "Pulling latest changes with fast-forward only"
    git pull --ff-only
  fi

  if [[ "$SKIP_TESTS" == true ]]; then
    BUILD_ARGS+=(-x test)
  fi

  export GRADLE_OPTS
  ./gradlew "${BUILD_ARGS[@]}"

  mapfile -t jars < <(find build/libs -maxdepth 1 -type f -name '*.jar' ! -name '*plain.jar' ! -name '*sources.jar' ! -name '*javadoc.jar' ! -name '*test*.jar' ! -name '*tests*.jar' | sort)
  if [[ "${#jars[@]}" -ne 1 ]]; then
    printf '%s\n' "${jars[@]}" >&2
    die "Expected exactly one executable jar in build/libs, found ${#jars[@]}"
  fi

  jar_path="${jars[0]}"

  if [[ -z "$REVISION" ]]; then
    REVISION="$(git rev-parse HEAD 2>/dev/null || true)"
  fi
fi

check_sudo_noninteractive

log "Preparing deployment directory: $DEPLOY_DIR"
"${SUDO[@]}" install -d -o root -g root -m 755 "$DEPLOY_DIR"
"${SUDO[@]}" install -d -o ec2-user -g ec2-user -m 755 "$DEPLOY_DIR/logs"

timestamp="$(date +%Y%m%d_%H%M%S)"
tmp_jar="$DEPLOY_DIR/app.jar.new"
tmp_revision="$DEPLOY_DIR/REVISION.new"
rollback_jar="$DEPLOY_DIR/app.jar.rollback"
rollback_revision="$DEPLOY_DIR/REVISION.rollback"
revision_file="$DEPLOY_DIR/REVISION"
backup_jar="$DEPLOY_DIR/app.jar.$timestamp.bak"
backup_revision="$DEPLOY_DIR/REVISION.$timestamp.bak"
backup_jar_tmp="$backup_jar.new"
backup_revision_tmp="$backup_revision.new"
trap cleanup_on_exit EXIT

set_root_file_permissions() {
  local path="$1"
  "${SUDO[@]}" chown root:root "$path"
  "${SUDO[@]}" chmod 644 "$path"
}

prune_old_backups() {
  local old_backups=()

  mapfile -t old_backups < <("${SUDO[@]}" find "$DEPLOY_DIR" -maxdepth 1 -type f -name 'app.jar.*.bak' -printf '%T@ %p\n' | sort -rn | awk -v keep="$BACKUP_KEEP_COUNT" 'NR > keep {print $2}')
  if [[ "${#old_backups[@]}" -gt 0 ]]; then
    log "Pruning old app.jar backups; keeping latest $BACKUP_KEEP_COUNT"
    "${SUDO[@]}" rm -f "${old_backups[@]}"
  fi
}

ensure_backup_permissions() {
  local backups=()

  mapfile -t backups < <("${SUDO[@]}" find "$DEPLOY_DIR" -maxdepth 1 -type f \( -name 'app.jar.*.bak' -o -name 'REVISION.*.bak' \) -print)
  if [[ "${#backups[@]}" -gt 0 ]]; then
    "${SUDO[@]}" chown root:root "${backups[@]}"
    "${SUDO[@]}" chmod 644 "${backups[@]}"
  fi
}

rollback() {
  local reason="$1"

  log "Rolling back: $reason"
  "${SUDO[@]}" journalctl -u "$SERVICE_NAME" -n 100 --no-pager >&2 || true

  cleanup_deploy_temps

  if "${SUDO[@]}" test -f "$backup_jar"; then
    "${SUDO[@]}" cp "$backup_jar" "$rollback_jar"
    set_root_file_permissions "$rollback_jar"
    "${SUDO[@]}" mv "$rollback_jar" "$DEPLOY_DIR/app.jar"
    set_root_file_permissions "$DEPLOY_DIR/app.jar"

    if "${SUDO[@]}" test -f "$backup_revision"; then
      "${SUDO[@]}" cp "$backup_revision" "$rollback_revision"
      set_root_file_permissions "$rollback_revision"
      "${SUDO[@]}" mv "$rollback_revision" "$revision_file"
      set_root_file_permissions "$revision_file"
    else
      "${SUDO[@]}" rm -f "$revision_file"
    fi

    if "${SUDO[@]}" systemctl restart "$SERVICE_NAME"; then
      if wait_for_health "Rollback"; then
        log "Rollback completed successfully"
      else
        log "Rollback service restarted, but health check failed" >&2
      fi
    else
      log "Rollback restart failed" >&2
    fi
  else
    log "No previous app.jar backup exists; rollback is not possible" >&2
  fi

  exit 1
}

log "Copying artifact to staging path"
cleanup_deploy_temps
"${SUDO[@]}" install -o root -g root -m 644 "$jar_path" "$tmp_jar"

if "${SUDO[@]}" test -f "$DEPLOY_DIR/app.jar"; then
  log "Backing up current app.jar"
  "${SUDO[@]}" cp "$DEPLOY_DIR/app.jar" "$backup_jar_tmp"
  set_root_file_permissions "$backup_jar_tmp"
  "${SUDO[@]}" mv "$backup_jar_tmp" "$backup_jar"
  set_root_file_permissions "$backup_jar"
fi

if "${SUDO[@]}" test -f "$revision_file"; then
  "${SUDO[@]}" cp "$revision_file" "$backup_revision_tmp"
  set_root_file_permissions "$backup_revision_tmp"
  "${SUDO[@]}" mv "$backup_revision_tmp" "$backup_revision"
  set_root_file_permissions "$backup_revision"
fi

if [[ -n "$REVISION" ]]; then
  printf '%s\n' "$REVISION" | "${SUDO[@]}" tee "$tmp_revision" >/dev/null
  set_root_file_permissions "$tmp_revision"
fi

log "Replacing app.jar"
"${SUDO[@]}" mv "$tmp_jar" "$DEPLOY_DIR/app.jar"
set_root_file_permissions "$DEPLOY_DIR/app.jar"

if [[ -n "$REVISION" ]]; then
  "${SUDO[@]}" mv "$tmp_revision" "$revision_file"
  set_root_file_permissions "$revision_file"
else
  log "No revision provided; removing stale REVISION file"
  "${SUDO[@]}" rm -f "$tmp_revision" "$revision_file"
fi

log "Restarting $SERVICE_NAME"
if ! "${SUDO[@]}" systemctl restart "$SERVICE_NAME"; then
  rollback "systemd restart failed"
fi

if ! wait_for_health "Deployment"; then
  rollback "deployment health check failed"
fi

prune_old_backups
ensure_backup_permissions
cleanup_deploy_temps

log "Deployment succeeded"
