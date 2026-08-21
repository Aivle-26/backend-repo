# EC2 Deployment

This backend runs on AWS EC2 behind Nginx. GitHub is the source of truth; the
EC2 host does not build or edit application source during deployment.

## Architecture

Vercel React frontend -> EC2 Nginx -> Spring Boot backend -> AWS RDS MySQL.

The Spring Boot process calls the local FastAPI service through loopback. Nginx,
the AI service runtime, RDS data, and S3 data are managed independently from
this backend runtime layout.

## Directories

- Runtime jar: `/opt/aipm/backend/app/aipm-backend.jar`
- Transient rollback workspace: `/opt/aipm/backend/backups/.rollback-<SHA>-<PID>/`
- Runtime deploy script: `/opt/aipm/backend/scripts/deploy.sh`
- Direct health script: `/opt/aipm/backend/scripts/health-check.sh`
- Deployed revision: `/opt/aipm/backend/REVISION`
- Backend environment: `/etc/aipm/backend.env`

`/home/ec2-user/app/backend` may remain temporarily during migration, but it is
not part of the deployment path.

## Environment

`/etc/aipm/backend.env` must be owned by root and mode `600`.

Required values:

```dotenv
DB_URL=jdbc:mysql://<RDS_ENDPOINT>:3306/aipm
DB_USERNAME=aipm_user
DB_PASSWORD=change_me
SPRING_PROFILES_ACTIVE=prod
SERVER_PORT=8080
SERVER_ADDRESS=127.0.0.1
CORS_ALLOWED_ORIGINS=http://localhost:5173,https://your-frontend.vercel.app
CORS_ALLOW_CREDENTIALS=false
AI_SERVER_BASE_URL=http://127.0.0.1:8090
PLANNING_AGENT_BASE_URL=http://127.0.0.1:8090
```

`AI_SERVER_BASE_URL` supplies the document-extraction and risk-analysis clients.
`PLANNING_AGENT_BASE_URL` supplies the planning extraction and WBS clients. Both
must be set in production so no caller falls back to port `8000`.

Do not commit real secrets, IP addresses, SSH keys, tokens, or production
passwords.

## Database

The production datasource is RDS MySQL. Database credentials remain only in
`/etc/aipm/backend.env`. Backend deployment must not change RDS schema or data.
Use the actuator health check and a read-only connectivity check for deployment
verification.

## Backend Build

Build and test on a developer machine or GitHub Actions:

```bash
GRADLE_OPTS="-Dorg.gradle.jvmargs=-Xmx512m" \
  ./gradlew clean test bootJar --no-daemon --max-workers=1
```

EC2 receives only the verified executable jar and deployment scripts. It does
not run Gradle or pull a Git checkout.

## Deploy

The GitHub Actions workflow uploads these files from one commit to `/tmp`:

- the verified executable jar: `/tmp/aipm-backend-COMMIT_SHA.jar`
- the deploy script from the same commit: `/tmp/deploy-backend-COMMIT_SHA.sh`
- the health script from the same commit:
  `/tmp/aipm-backend-health-COMMIT_SHA.sh`
- the systemd unit from the same commit:
  `/tmp/aipm-backend-service-COMMIT_SHA.service`

The remote command runs the uploaded script, not a script from an EC2 Git clone:

```bash
/tmp/deploy-backend-COMMIT_SHA.sh \
  --artifact /tmp/aipm-backend-COMMIT_SHA.jar \
  --revision COMMIT_SHA \
  --health-script /tmp/aipm-backend-health-COMMIT_SHA.sh \
  --systemd-unit /tmp/aipm-backend-service-COMMIT_SHA.service
```

The script retains the current jar and REVISION in a hidden rollback directory
for the duration of one deployment. It stages replacements in the target
filesystem, restarts `aipm-backend`, and checks liveness, readiness, and overall
health in that order. On failure it restores the jar, REVISION, and any changed
systemd unit in the same execution, restarts again when needed, and verifies all
three rollback health endpoints. On success it removes the temporary rollback
directory and unit copy. Successful releases are not retained as deployment
backups.

Before replacing the jar, the script confirms that the prepared target jar
exists, validates the uploaded systemd unit, retains the active unit temporarily
when it differs, and reloads systemd. The first deployment after the layout
migration therefore changes the unit and jar in one controlled operation. Later
deployments skip unit replacement when the template is unchanged while still
running `daemon-reload`.

Legacy flat `app.jar.*.bak` files and pre-migration backup directories are not
used or created by this workflow. They are handled only by a separately approved
cleanup operation.

Runtime ownership policy:

- `/opt/aipm/backend`: `root:root`, mode `755`
- `app`, `backups`, and `scripts`: `root:root`, mode `755`
- the runtime jar and `REVISION`: `root:root`, mode `644`
- transient rollback directory: `root:root`, mode `700`
- runtime scripts: `root:root`, mode `755`

Logging is handled by journald. The deploy script does not create a `logs`
directory.

## GitHub Actions

Two workflows are expected:

- `.github/workflows/backend-ci.yml`: runs on pull requests targeting `dev` and
  manual dispatch. It sets up Java 21, uses the Gradle cache, and runs
  `./gradlew clean test bootJar --no-daemon --max-workers=1`.
- `.github/workflows/backend-dev-deploy.yml`: runs only on pushes to `dev` and
  manual dispatch from the `dev` branch. It does not auto-deploy `main`.

The deploy workflow builds on GitHub-hosted runners, uploads the jar, both
scripts, and the systemd unit with SCP, applies restrictive permissions,
verifies non-interactive sudo with `sudo -n true`, and runs the temporary deploy
script. Runtime DB credentials stay on EC2 in `/etc/aipm/backend.env`; do not
put DB passwords in GitHub Secrets.

Required GitHub Secrets:

- `EC2_HOST`
- `EC2_USER`
- `EC2_SSH_KEY`
- `EC2_KNOWN_HOSTS`

`EC2_KNOWN_HOSTS` must contain the EC2 host key entry after verifying the host
key fingerprint from a trusted environment. Do not use
`StrictHostKeyChecking=no`.

After a successful deploy, verify:

```bash
curl -i http://127.0.0.1:8080/actuator/health/liveness
curl -i http://127.0.0.1:8080/actuator/health/readiness
curl -i http://127.0.0.1:8080/actuator/health
curl -i http://127.0.0.1/actuator/health
curl -i http://<EC2_ELASTIC_IP>/actuator/health
```

## systemd

Install the service template:

```bash
sudo cp deploy/systemd/aipm-backend.service /etc/systemd/system/aipm-backend.service
sudo systemctl daemon-reload
sudo systemctl enable aipm-backend
sudo systemctl restart aipm-backend
```

Check status and logs:

```bash
systemctl status aipm-backend --no-pager
journalctl -u aipm-backend -n 100 --no-pager
```

## Nginx

Amazon Linux 2023 installs a default port 80 server block in
`/etc/nginx/nginx.conf`. Use the `default.d` location include unless that stock
server block is intentionally replaced.

```bash
sudo dnf install -y nginx
sudo cp deploy/nginx/server-tokens.conf /etc/nginx/conf.d/server-tokens.conf
sudo cp deploy/nginx/aipm-default.d.conf /etc/nginx/default.d/aipm.conf
sudo nginx -t
sudo systemctl enable nginx
sudo systemctl restart nginx
```

`deploy/nginx/aipm.conf` is a full server-block alternative for hosts where the
stock default server block has been removed.

Health check through Nginx:

```bash
curl -i http://127.0.0.1/actuator/health
```

## AWS Security Group

Recommended inbound rules:

- SSH `22`: current operator IP only
- HTTP `80`: required test range
- HTTPS `443`: after domain and certificate setup
- MySQL `3306`: do not open
- Spring Boot `8080`: do not open

The unit intentionally has no Docker or local MySQL dependency. Its working
directory is `/opt/aipm/backend`, and it runs
`/opt/aipm/backend/app/aipm-backend.jar`. It retains Java 21, the `ec2-user`
runtime user, `/etc/aipm/backend.env`, automatic startup, restart-on-failure,
and journald logging.
