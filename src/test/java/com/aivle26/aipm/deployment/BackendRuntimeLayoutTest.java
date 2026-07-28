package com.aivle26.aipm.deployment;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class BackendRuntimeLayoutTest {

    @Test
    void systemdUnitUsesStandardRuntimeWithoutLocalServiceDependencies() throws IOException {
        String unit = read("deploy/systemd/aipm-backend.service");

        assertThat(unit)
                .contains("Wants=network-online.target")
                .contains("After=network-online.target")
                .contains("WorkingDirectory=/opt/aipm/backend")
                .contains("EnvironmentFile=/etc/aipm/backend.env")
                .contains("-jar /opt/aipm/backend/app/aipm-backend.jar")
                .doesNotContain("docker.service")
                .doesNotContain("mysql.service")
                .doesNotContain("/etc/aipm/mysql.env");
    }

    @Test
    void devWorkflowBuildsOnRunnerAndUploadsOneDeploymentUnit() throws IOException {
        String workflow = read(".github/workflows/backend-dev-deploy.yml");

        assertThat(workflow)
                .contains("branches:\n      - dev")
                .contains("github.ref == 'refs/heads/dev'")
                .contains("./gradlew clean test bootJar --no-daemon --max-workers=1")
                .contains("scripts/deploy-backend.sh")
                .contains("scripts/health-check.sh")
                .contains("deploy/systemd/aipm-backend.service")
                .contains("StrictHostKeyChecking=yes")
                .doesNotContain("StrictHostKeyChecking=no")
                .doesNotContain("git pull")
                .doesNotContain("/home/ec2-user/app/backend");
    }

    @Test
    void deployScriptUsesTransientRollbackAndThreeStageHealthVerification() throws IOException {
        String script = read("scripts/deploy-backend.sh");

        assertThat(script)
                .contains("APP_JAR=\"$APP_DIR/aipm-backend.jar\"")
                .contains("ROLLBACK_DIR=\"$BACKUP_ROOT/.rollback-${REVISION}-$$\"")
                .contains("discard_rollback_material")
                .contains("run_health_check \"$phase liveness\"")
                .contains("run_health_check \"$phase readiness\"")
                .contains("run_health_check \"$phase overall\"")
                .contains("restore_systemd_unit")
                .contains("validate_runtime_environment")
                .contains("DB_URL")
                .contains("AI_SERVER_BASE_URL")
                .contains("systemctl daemon-reload")
                .contains("systemctl restart \"$SERVICE_NAME\"")
                .doesNotContain("BACKUP_KEEP_COUNT")
                .doesNotContain("prune_old_backups")
                .doesNotContain("/opt/aipm/backend/app.jar")
                .doesNotContain("/etc/aipm/mysql.env")
                .doesNotContain("app.jar.*.bak")
                .doesNotContain("git pull")
                .doesNotContain("./gradlew");
    }

    @Test
    void healthScriptRequiresHttpSuccessAndUpStatus() throws IOException {
        String script = read("scripts/health-check.sh");

        assertThat(script)
                .contains("--fail")
                .contains("--connect-timeout")
                .contains("--max-time")
                .contains("\"status\"[[:space:]]*:[[:space:]]*\"UP\"")
                .contains("HEALTH_RETRIES")
                .contains("HEALTH_INTERVAL_SECONDS");
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(Path.of(relativePath)).replace("\r\n", "\n");
    }
}
