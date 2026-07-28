# Operations

## Health Checks

Backend direct:

```bash
curl -i http://127.0.0.1:8080/actuator/health/liveness
curl -i http://127.0.0.1:8080/actuator/health/readiness
curl -i http://127.0.0.1:8080/actuator/health
```

Through Nginx:

```bash
curl -i http://127.0.0.1/actuator/health
```

From outside EC2:

```bash
curl -i http://<EC2_ELASTIC_IP>/actuator/health
```

Current deployed revision:

```bash
sudo cat /opt/aipm/backend/REVISION
```

Run the repository-managed direct health check:

```bash
sudo /opt/aipm/backend/scripts/health-check.sh
```

## Service Commands

```bash
sudo systemctl start aipm-backend
sudo systemctl stop aipm-backend
sudo systemctl restart aipm-backend
systemctl status aipm-backend --no-pager
journalctl -u aipm-backend -n 100 --no-pager
```

## Deployment Rollback

Each deployment temporarily retains the current jar and REVISION:

```text
/opt/aipm/backend/backups/.rollback-<SHA>-<PID>/
├── aipm-backend.jar
└── REVISION
```

The rollback directory exists only while one deployment is running. A restart
or health failure restores the previous jar, REVISION, and changed systemd unit
in the same workflow execution. Successful deployment removes the temporary
rollback material, so successful releases do not accumulate in `backups/`.

Legacy flat `app.jar.*.bak` files are not deleted automatically during the
layout transition and are not used by the new workflow.

## GitHub Actions

Pull requests targeting `dev` run backend CI only. The CI workflow builds with
Java 21 and runs:

```bash
GRADLE_OPTS="-Dorg.gradle.jvmargs=-Xmx512m" ./gradlew clean test bootJar --no-daemon --max-workers=1
```

Pushes to `dev` run the dev deployment workflow. `main` is not automatically
deployed. The workflow uploads the built jar plus the current commit's
`scripts/deploy-backend.sh`, `scripts/health-check.sh`, and
`deploy/systemd/aipm-backend.service` to `/tmp` on EC2. It applies restrictive
permissions, verifies non-interactive sudo with `sudo -n true`, and runs the
temporary deploy script. It does not depend on an EC2 Git clone.

On the first deployment after the runtime-layout migration, the deploy script
validates and temporarily retains the active unit before loading the repository
template. It then deploys the jar and revision, restarts the service, and checks
liveness, readiness, and overall health. A failed first deployment restores the
pre-layout unit together with the prior jar and revision.

DB credentials remain on EC2 in `/etc/aipm/backend.env`; they are not GitHub
Secrets. The production database is RDS MySQL.

Required repository secrets:

- `EC2_HOST`
- `EC2_USER`
- `EC2_SSH_KEY`
- `EC2_KNOWN_HOSTS`

Register `EC2_KNOWN_HOSTS` only after verifying the EC2 host key fingerprint from
a trusted environment. Do not use `StrictHostKeyChecking=no` for deployment.

If GitHub Actions fails, check in this order:

1. The CI build log for compile or test errors.
2. The artifact selection step for unexpected jar names.
3. SSH known-host verification and key permissions.
4. The deploy script output for restart, health check, or rollback messages.
5. EC2 service logs with `journalctl -u aipm-backend -n 100 --no-pager`.

For a later manual rollback, revert the faulty change through a reviewed PR to
`dev`. The dev deployment workflow then builds and deploys that Git revision.
Do not restore old flat backup files directly.

Runtime ownership should remain:

- `/opt/aipm/backend`: `root:root`, mode `755`
- `/opt/aipm/backend/app`, `backups`, and `scripts`: `root:root`, mode `755`
- runtime jar and `REVISION`: `root:root`, mode `644`
- transient rollback directory: `root:root`, mode `700`
- runtime scripts: `root:root`, mode `755`

Logs are read from journald; no runtime `logs` directory is required.

## Troubleshooting

- If the backend fails to start, inspect `journalctl -u aipm-backend -n 100 --no-pager`.
- If Gradle is slow on EC2, keep `--max-workers=1` and `GRADLE_OPTS="-Dorg.gradle.jvmargs=-Xmx512m"`.
- If health returns 401 or 403, confirm `/actuator/health` is exposed and the active profile is `prod`.
- If Nginx returns 502, verify Spring Boot is listening on `127.0.0.1:8080`.
