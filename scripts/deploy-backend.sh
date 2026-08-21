#!/usr/bin/env bash
set -euo pipefail

RUNTIME_ROOT="${RUNTIME_ROOT:-/opt/aipm/backend}"
APP_DIR="$RUNTIME_ROOT/app"
APP_JAR="$APP_DIR/aipm-backend.jar"
BACKUP_ROOT="$RUNTIME_ROOT/backups"
SCRIPTS_DIR="$RUNTIME_ROOT/scripts"
REVISION_FILE="$RUNTIME_ROOT/REVISION"
SERVICE_NAME="${SERVICE_NAME:-aipm-backend}"
UNIT_FILE="/etc/systemd/system/${SERVICE_NAME}.service"
ENV_FILE="/etc/aipm/backend.env"
HEALTH_LIVENESS_URL="${HEALTH_LIVENESS_URL:-http://127.0.0.1:8080/actuator/health/liveness}"
HEALTH_READINESS_URL="${HEALTH_READINESS_URL:-http://127.0.0.1:8080/actuator/health/readiness}"
HEALTH_OVERALL_URL="${HEALTH_OVERALL_URL:-http://127.0.0.1:8080/actuator/health}"
HEALTH_RETRIES="${HEALTH_RETRIES:-30}"
HEALTH_INTERVAL_SECONDS="${HEALTH_INTERVAL_SECONDS:-2}"
SUDO=(sudo -n)

ARTIFACT_PATH=""
REVISION=""
HEALTH_SCRIPT=""
SYSTEMD_UNIT_SOURCE=""
ROLLBACK_DIR=""
UNIT_ROLLBACK=""
UNIT_CHANGED=0
FILES_REPLACED=0
SERVICE_RESTART_ATTEMPTED=0
DEPLOYMENT_MUTATED=0
DEPLOYMENT_SUCCEEDED=0
ROLLBACK_ATTEMPTED=0
ROLLBACK_SUCCEEDED=0
APP_NEW="$APP_DIR/.aipm-backend.jar.new.$$"
APP_ROLLBACK="$APP_DIR/.aipm-backend.jar.rollback.$$"
REVISION_NEW="$RUNTIME_ROOT/.REVISION.new.$$"
REVISION_ROLLBACK="$RUNTIME_ROOT/.REVISION.rollback.$$"
DEPLOY_SCRIPT_NEW="$SCRIPTS_DIR/.deploy.sh.new.$$"
HEALTH_SCRIPT_NEW="$SCRIPTS_DIR/.health-check.sh.new.$$"
UNIT_NEW="/etc/systemd/system/.${SERVICE_NAME}.service.new.$$"

usage() {
  cat <<'USAGE'
Usage:
  deploy-backend.sh \
    --artifact /tmp/aipm-backend-COMMIT_SHA.jar \
    --revision COMMIT_SHA \
    --health-script /tmp/aipm-backend-health-COMMIT_SHA.sh \
    --systemd-unit /tmp/aipm-backend-service-COMMIT_SHA.service

Deploys a pre-built Spring Boot jar without pulling Git history or building on
EC2. The current jar, REVISION, and systemd unit are retained only for the
duration of this deployment. A failed restart or health check restores them in
the same execution; a successful deployment removes the temporary rollback
material.
USAGE
}

log() {
  printf '[backend-deploy] %s\n' "$*"
}

die() {
  printf '[backend-deploy] ERROR: %s\n' "$*" >&2
  exit 1
}

require_value() {
  local option="$1"
  local value="${2:-}"
  [[ -n "$value" ]] || die "$option requires a value"
}

require_positive_integer() {
  local name="$1"
  local value="$2"
  [[ "$value" =~ ^[1-9][0-9]*$ ]] || die "$name must be a positive integer"
}

cleanup_uploaded_artifact() {
  if [[ "$ARTIFACT_PATH" =~ ^/tmp/aipm-backend-[0-9A-Fa-f]{7,64}\.jar$ ]] \
      && [[ -f "$ARTIFACT_PATH" ]] \
      && [[ ! -L "$ARTIFACT_PATH" ]]; then
    rm -f -- "$ARTIFACT_PATH"
  fi
}

cleanup_staging_files() {
  "${SUDO[@]}" rm -f -- \
    "$APP_NEW" \
    "$APP_ROLLBACK" \
    "$REVISION_NEW" \
    "$REVISION_ROLLBACK" \
    "$DEPLOY_SCRIPT_NEW" \
    "$HEALTH_SCRIPT_NEW" \
    "$UNIT_NEW" 2>/dev/null || true
}

remove_rollback_dir() {
  if [[ -z "$ROLLBACK_DIR" ]]; then
    return 0
  fi

  if [[ "$ROLLBACK_DIR" != "$BACKUP_ROOT/.rollback-${REVISION}-$$" ]] \
      || "${SUDO[@]}" test -L "$ROLLBACK_DIR"; then
    log "Refusing to remove unexpected rollback path: $ROLLBACK_DIR" >&2
    return 1
  fi

  "${SUDO[@]}" rm -rf -- "$ROLLBACK_DIR"
  ROLLBACK_DIR=""
}

remove_unit_rollback() {
  if [[ -z "$UNIT_ROLLBACK" ]]; then
    return 0
  fi

  if [[ "$UNIT_ROLLBACK" != "/etc/systemd/system/.${SERVICE_NAME}.service.rollback.$$" ]] \
      || "${SUDO[@]}" test -L "$UNIT_ROLLBACK"; then
    log "Refusing to remove unexpected unit rollback path: $UNIT_ROLLBACK" >&2
    return 1
  fi

  "${SUDO[@]}" rm -f -- "$UNIT_ROLLBACK"
  UNIT_ROLLBACK=""
}

discard_rollback_material() {
  remove_rollback_dir \
    && remove_unit_rollback
}

run_health_check() {
  local label="$1"
  local url="$2"

  HEALTH_URL="$url" \
  HEALTH_RETRIES="$HEALTH_RETRIES" \
  HEALTH_INTERVAL_SECONDS="$HEALTH_INTERVAL_SECONDS" \
    bash "$HEALTH_SCRIPT" --label "$label"
}

run_health_checks() {
  local phase="$1"

  run_health_check "$phase liveness" "$HEALTH_LIVENESS_URL" \
    && run_health_check "$phase readiness" "$HEALTH_READINESS_URL" \
    && run_health_check "$phase overall" "$HEALTH_OVERALL_URL"
}

set_root_file_permissions() {
  local path="$1"
  "${SUDO[@]}" chown root:root "$path" \
    && "${SUDO[@]}" chmod 644 "$path"
}

prepare_runtime_directories() {
  log "Preparing runtime directories"
  "${SUDO[@]}" install -d -o root -g root -m 755 \
    "$RUNTIME_ROOT" \
    "$APP_DIR" \
    "$BACKUP_ROOT" \
    "$SCRIPTS_DIR"
}

validate_runtime_environment() {
  local required_key

  log "Validating the protected runtime environment file"
  "${SUDO[@]}" test -f "$ENV_FILE" \
    && [[ "$("${SUDO[@]}" stat -c '%U:%G' "$ENV_FILE")" == "root:root" ]] \
    && [[ "$("${SUDO[@]}" stat -c '%a' "$ENV_FILE")" == "600" ]] \
    || return 1

  for required_key in \
    DB_URL \
    DB_USERNAME \
    DB_PASSWORD \
    AI_SERVER_BASE_URL \
    PLANNING_AGENT_BASE_URL; do
    "${SUDO[@]}" grep -Eq "^${required_key}=.+$" "$ENV_FILE" || {
      log "Required environment key is missing: $required_key" >&2
      return 1
    }
  done
}

restore_systemd_unit() {
  if [[ "$UNIT_CHANGED" -ne 1 ]]; then
    return 0
  fi

  if [[ -z "$UNIT_ROLLBACK" ]] || ! "${SUDO[@]}" test -f "$UNIT_ROLLBACK"; then
    log "No systemd unit backup is available for restoration" >&2
    return 1
  fi

  log "Restoring the previous systemd unit"
  if "${SUDO[@]}" install -o root -g root -m 644 "$UNIT_ROLLBACK" "$UNIT_NEW" \
      && "${SUDO[@]}" mv "$UNIT_NEW" "$UNIT_FILE" \
      && "${SUDO[@]}" systemctl daemon-reload; then
    UNIT_CHANGED=0
    if [[ "$FILES_REPLACED" -eq 0 ]]; then
      DEPLOYMENT_MUTATED=0
    fi
    return 0
  fi

  return 1
}

validate_systemd_unit() {
  log "Validating the repository-managed systemd unit"

  "${SUDO[@]}" systemd-analyze verify "$SYSTEMD_UNIT_SOURCE" \
    && "${SUDO[@]}" grep -Fxq 'User=ec2-user' "$SYSTEMD_UNIT_SOURCE" \
    && "${SUDO[@]}" grep -Fxq 'WorkingDirectory=/opt/aipm/backend' "$SYSTEMD_UNIT_SOURCE" \
    && "${SUDO[@]}" grep -Fxq "EnvironmentFile=$ENV_FILE" "$SYSTEMD_UNIT_SOURCE" \
    && "${SUDO[@]}" grep -Fxq \
      'ExecStart=/usr/bin/java -Xms128m -Xmx384m -jar /opt/aipm/backend/app/aipm-backend.jar' \
      "$SYSTEMD_UNIT_SOURCE" \
    && ! "${SUDO[@]}" grep -Eq '^(After|Wants|Requires)=.*docker' "$SYSTEMD_UNIT_SOURCE" \
    && ! "${SUDO[@]}" grep -Eq '^(After|Wants|Requires)=.*mysql' "$SYSTEMD_UNIT_SOURCE"
}

prepare_systemd_unit() {
  validate_systemd_unit || return 1
  "${SUDO[@]}" install -o root -g root -m 644 "$SYSTEMD_UNIT_SOURCE" "$UNIT_NEW" || return 1

  if "${SUDO[@]}" cmp --silent "$UNIT_NEW" "$UNIT_FILE"; then
    log "Systemd unit already matches the repository template"
    "${SUDO[@]}" rm -f -- "$UNIT_NEW"
    "${SUDO[@]}" systemctl daemon-reload
    return
  fi

  UNIT_ROLLBACK="/etc/systemd/system/.${SERVICE_NAME}.service.rollback.$$"
  if "${SUDO[@]}" test -e "$UNIT_ROLLBACK"; then
    log "Temporary unit rollback path already exists" >&2
    return 1
  fi

  log "Retaining the current systemd unit for this deployment"
  "${SUDO[@]}" install -o root -g root -m 600 "$UNIT_FILE" "$UNIT_ROLLBACK" || return 1

  log "Installing the repository-managed systemd unit"
  if ! "${SUDO[@]}" mv "$UNIT_NEW" "$UNIT_FILE" \
      || ! "${SUDO[@]}" systemctl daemon-reload; then
    UNIT_CHANGED=1
    DEPLOYMENT_MUTATED=1
    restore_systemd_unit || true
    return 1
  fi
  UNIT_CHANGED=1
  DEPLOYMENT_MUTATED=1

  unit_exec_start="$("${SUDO[@]}" systemctl show "$SERVICE_NAME" --property=ExecStart --value)"
  unit_working_directory="$("${SUDO[@]}" systemctl show "$SERVICE_NAME" --property=WorkingDirectory --value)"
  if [[ "$unit_exec_start" != *"$APP_JAR"* ]] \
      || [[ "$unit_working_directory" != "$RUNTIME_ROOT" ]]; then
    log "Loaded systemd unit does not match the target runtime layout" >&2
    restore_systemd_unit || true
    return 1
  fi

  log "Systemd unit migration is ready; the current process remains running until deployment restart"
}

stage_deployment_files() {
  log "Staging jar, revision, and runtime scripts"
  "${SUDO[@]}" install -o root -g root -m 644 "$ARTIFACT_PATH" "$APP_NEW"
  printf '%s\n' "$REVISION" | "${SUDO[@]}" tee "$REVISION_NEW" >/dev/null
  set_root_file_permissions "$REVISION_NEW"
  "${SUDO[@]}" install -o root -g root -m 755 "${BASH_SOURCE[0]}" "$DEPLOY_SCRIPT_NEW"
  "${SUDO[@]}" install -o root -g root -m 755 "$HEALTH_SCRIPT" "$HEALTH_SCRIPT_NEW"
}

retain_current_deployment() {
  ROLLBACK_DIR="$BACKUP_ROOT/.rollback-${REVISION}-$$"
  [[ ! -e "$ROLLBACK_DIR" ]] || die "Temporary rollback path already exists"

  log "Retaining the current jar and revision for this deployment"
  "${SUDO[@]}" install -d -o root -g root -m 700 "$ROLLBACK_DIR"
  "${SUDO[@]}" install -o root -g root -m 644 \
    "$APP_JAR" \
    "$ROLLBACK_DIR/aipm-backend.jar"

  if "${SUDO[@]}" test -f "$REVISION_FILE"; then
    "${SUDO[@]}" install -o root -g root -m 644 \
      "$REVISION_FILE" \
      "$ROLLBACK_DIR/REVISION"
  fi
}

replace_deployment_files() {
  "${SUDO[@]}" mv "$APP_NEW" "$APP_JAR" || return 1
  set_root_file_permissions "$APP_JAR" || return 1
  FILES_REPLACED=1
  DEPLOYMENT_MUTATED=1

  "${SUDO[@]}" mv "$REVISION_NEW" "$REVISION_FILE" || return 1
  set_root_file_permissions "$REVISION_FILE"
}

install_runtime_scripts() {
  "${SUDO[@]}" mv "$DEPLOY_SCRIPT_NEW" "$SCRIPTS_DIR/deploy.sh" \
    && "${SUDO[@]}" chown root:root "$SCRIPTS_DIR/deploy.sh" \
    && "${SUDO[@]}" chmod 755 "$SCRIPTS_DIR/deploy.sh" \
    && "${SUDO[@]}" mv "$HEALTH_SCRIPT_NEW" "$SCRIPTS_DIR/health-check.sh" \
    && "${SUDO[@]}" chown root:root "$SCRIPTS_DIR/health-check.sh" \
    && "${SUDO[@]}" chmod 755 "$SCRIPTS_DIR/health-check.sh"
}

verify_deployed_runtime() {
  local deployed_revision
  local environment_files
  local unit_after
  local unit_exec_start
  local unit_working_directory

  log "Verifying the deployed runtime contract"
  "${SUDO[@]}" systemctl is-active --quiet "$SERVICE_NAME" \
    && "${SUDO[@]}" systemctl is-enabled --quiet "$SERVICE_NAME" \
    || return 1

  unit_exec_start="$("${SUDO[@]}" systemctl show "$SERVICE_NAME" --property=ExecStart --value)"
  unit_working_directory="$("${SUDO[@]}" systemctl show "$SERVICE_NAME" --property=WorkingDirectory --value)"
  environment_files="$("${SUDO[@]}" systemctl show "$SERVICE_NAME" --property=EnvironmentFiles --value)"
  unit_after="$("${SUDO[@]}" systemctl show "$SERVICE_NAME" --property=After --value)"
  deployed_revision="$("${SUDO[@]}" tr -d '\r\n' <"$REVISION_FILE")"

  [[ "$unit_exec_start" == *"$APP_JAR"* ]] \
    && [[ "$unit_exec_start" != *"$RUNTIME_ROOT/app.jar"* ]] \
    && [[ "$unit_working_directory" == "$RUNTIME_ROOT" ]] \
    && [[ "$environment_files" == *"$ENV_FILE"* ]] \
    && [[ "$unit_after" != *"docker.service"* ]] \
    && [[ "$unit_after" != *"mysql.service"* ]] \
    && [[ "$deployed_revision" == "$REVISION" ]] \
    && "${SUDO[@]}" test -f "$SCRIPTS_DIR/deploy.sh" \
    && "${SUDO[@]}" test -x "$SCRIPTS_DIR/deploy.sh" \
    && "${SUDO[@]}" test -f "$SCRIPTS_DIR/health-check.sh" \
    && "${SUDO[@]}" test -x "$SCRIPTS_DIR/health-check.sh"
}

rollback() {
  local reason="$1"
  local rollback_status=0

  ROLLBACK_ATTEMPTED=1
  log "Rolling back deployment: $reason"

  if [[ "$FILES_REPLACED" -eq 1 ]]; then
    if [[ -z "$ROLLBACK_DIR" ]] \
        || ! "${SUDO[@]}" test -f "$ROLLBACK_DIR/aipm-backend.jar"; then
      log "Rollback is unavailable because no complete jar snapshot exists" >&2
      return 1
    fi

    if ! "${SUDO[@]}" install -o root -g root -m 644 \
        "$ROLLBACK_DIR/aipm-backend.jar" \
        "$APP_ROLLBACK" \
        || ! "${SUDO[@]}" mv "$APP_ROLLBACK" "$APP_JAR" \
        || ! set_root_file_permissions "$APP_JAR"; then
      log "Rollback could not restore the previous jar" >&2
      rollback_status=1
    fi

    if "${SUDO[@]}" test -f "$ROLLBACK_DIR/REVISION"; then
      if ! "${SUDO[@]}" install -o root -g root -m 644 \
          "$ROLLBACK_DIR/REVISION" \
          "$REVISION_ROLLBACK" \
          || ! "${SUDO[@]}" mv "$REVISION_ROLLBACK" "$REVISION_FILE" \
          || ! set_root_file_permissions "$REVISION_FILE"; then
        log "Rollback could not restore the previous revision" >&2
        rollback_status=1
      fi
    elif ! "${SUDO[@]}" rm -f -- "$REVISION_FILE"; then
      log "Rollback could not remove the new revision" >&2
      rollback_status=1
    fi
  fi

  if [[ "$UNIT_CHANGED" -eq 1 ]] && ! restore_systemd_unit; then
    log "Rollback could not restore the previous systemd unit" >&2
    rollback_status=1
  fi

  if [[ "$rollback_status" -ne 0 ]]; then
    log "Rollback restoration failed; preserving rollback material for operator review" >&2
    return 1
  fi

  if [[ "$SERVICE_RESTART_ATTEMPTED" -eq 1 ]]; then
    if "${SUDO[@]}" systemctl restart "$SERVICE_NAME"; then
      if ! run_health_checks "Rollback"; then
        log "Rollback restarted the service, but health verification failed" >&2
        rollback_status=1
      fi
    else
      log "Rollback could not restart $SERVICE_NAME" >&2
      rollback_status=1
    fi
  fi

  if [[ "$rollback_status" -ne 0 ]]; then
    log "Rollback did not return the service to a verified healthy state" >&2
    return 1
  fi

  ROLLBACK_SUCCEEDED=1
  DEPLOYMENT_MUTATED=0
  FILES_REPLACED=0
  discard_rollback_material || {
    log "Rollback succeeded, but temporary rollback cleanup failed" >&2
    return 1
  }
  log "Rollback completed and passed verification"
}

on_exit() {
  local exit_code=$?

  trap - EXIT
  set +e

  if [[ "$exit_code" -ne 0 ]] \
      && [[ "$DEPLOYMENT_MUTATED" -eq 1 ]] \
      && [[ "$ROLLBACK_ATTEMPTED" -eq 0 ]]; then
    rollback "unexpected deployment failure" || true
  elif [[ "$DEPLOYMENT_MUTATED" -eq 0 ]] \
      && [[ "$DEPLOYMENT_SUCCEEDED" -eq 0 ]] \
      && [[ "$ROLLBACK_SUCCEEDED" -eq 0 ]]; then
    discard_rollback_material || true
  fi

  cleanup_staging_files
  cleanup_uploaded_artifact
  exit "$exit_code"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
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
    --health-script)
      require_value "$1" "${2:-}"
      HEALTH_SCRIPT="${2:-}"
      shift 2
      ;;
    --systemd-unit)
      require_value "$1" "${2:-}"
      SYSTEMD_UNIT_SOURCE="${2:-}"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      usage >&2
      die "Unknown argument: $1"
      ;;
  esac
done

[[ -n "$ARTIFACT_PATH" ]] || die "--artifact is required"
[[ -n "$REVISION" ]] || die "--revision is required"
[[ -n "$HEALTH_SCRIPT" ]] || die "--health-script is required"
[[ -n "$SYSTEMD_UNIT_SOURCE" ]] || die "--systemd-unit is required"
[[ "$REVISION" =~ ^[0-9A-Fa-f]{7,64}$ ]] || die "--revision must be a Git commit SHA"
[[ -f "$ARTIFACT_PATH" && ! -L "$ARTIFACT_PATH" ]] || die "Artifact must be a regular, non-symlink file"
[[ -f "$HEALTH_SCRIPT" && ! -L "$HEALTH_SCRIPT" ]] || die "Health script must be a regular, non-symlink file"
[[ -f "$SYSTEMD_UNIT_SOURCE" && ! -L "$SYSTEMD_UNIT_SOURCE" ]] || die "Systemd unit must be a regular, non-symlink file"
jar tf "$ARTIFACT_PATH" >/dev/null || die "Artifact is not a readable jar file"
require_positive_integer "HEALTH_RETRIES" "$HEALTH_RETRIES"
require_positive_integer "HEALTH_INTERVAL_SECONDS" "$HEALTH_INTERVAL_SECONDS"

trap on_exit EXIT

log "Checking non-interactive sudo"
"${SUDO[@]}" true || die "Passwordless sudo is required"

prepare_runtime_directories

if ! validate_runtime_environment; then
  die "$ENV_FILE is missing required keys or secure ownership and permissions"
fi

if ! "${SUDO[@]}" test -f "$APP_JAR" || "${SUDO[@]}" test -L "$APP_JAR"; then
  die "Current runtime jar is missing at $APP_JAR; prepare the target layout before deploying"
fi

if ! prepare_systemd_unit; then
  die "Systemd unit validation or migration failed"
fi

unit_exec_start="$("${SUDO[@]}" systemctl show "$SERVICE_NAME" --property=ExecStart --value)"
if [[ "$unit_exec_start" != *"$APP_JAR"* ]]; then
  die "$SERVICE_NAME does not yet use $APP_JAR; migrate systemd before running the new deploy workflow"
fi

stage_deployment_files
retain_current_deployment

log "Replacing the runtime jar and revision"
if ! replace_deployment_files; then
  die "Deployment files could not be replaced"
fi

log "Restarting $SERVICE_NAME"
SERVICE_RESTART_ATTEMPTED=1
if ! "${SUDO[@]}" systemctl restart "$SERVICE_NAME"; then
  die "Deployment failed during service restart"
fi

if ! run_health_checks "Deployment"; then
  die "Deployment failed health verification"
fi

if ! install_runtime_scripts; then
  die "Deployment failed while installing runtime scripts"
fi

if ! verify_deployed_runtime; then
  die "Deployment failed final runtime verification"
fi

DEPLOYMENT_SUCCEEDED=1
DEPLOYMENT_MUTATED=0
if ! discard_rollback_material; then
  die "Deployment succeeded, but temporary rollback cleanup failed"
fi

log "Deployment succeeded at revision $REVISION"
