# EC2 Deployment

This backend currently runs on AWS EC2 behind Nginx.

## Architecture

Vercel React frontend -> EC2 Nginx -> Spring Boot backend -> EC2 Docker MySQL.

The current database is not RDS. It is a MySQL container running on the EC2
instance. FastAPI AI services, HTTPS, and RDS migration are future integration
steps.

## Directories

- Repository: `/home/ec2-user/app/backend`
- Runtime jar: `/opt/aipm/backend/app.jar`
- Runtime logs: `/opt/aipm/backend/logs`
- Backend environment: `/etc/aipm/backend.env`
- MySQL environment: `/etc/aipm/mysql.env`
- Backups: `/home/ec2-user/backups`

## Environment

`/etc/aipm/backend.env` must be owned by root and mode `600`.

Required values:

```dotenv
DB_URL=jdbc:mysql://127.0.0.1:3306/aipm
DB_USERNAME=aipm_user
DB_PASSWORD=change_me
SPRING_PROFILES_ACTIVE=prod
SERVER_PORT=8080
SERVER_ADDRESS=127.0.0.1
CORS_ALLOWED_ORIGINS=http://localhost:5173,https://your-frontend.vercel.app
CORS_ALLOW_CREDENTIALS=false
```

`DB_URL=jdbc:mysql://127.0.0.1:3306/aipm` is the current EC2 Docker MySQL
connection string. Change it only when the project intentionally moves to RDS or
another shared AWS MySQL endpoint.

Do not commit real secrets, IP addresses, SSH keys, tokens, or production
passwords.

## MySQL

The current MySQL database runs as a Docker container on the EC2 instance. It
uses the external Docker volume `aipm-mysql-data`.

Check status:

```bash
sudo docker ps
sudo docker inspect aipm-mysql --format '{{.State.Status}} {{.HostConfig.RestartPolicy.Name}}'
```

If moving to Compose, keep the same volume name and confirm a DB dump exists
before replacing the container.

## Backend Build

On the EC2 instance:

```bash
cd ~/app/backend
GRADLE_OPTS="-Dorg.gradle.jvmargs=-Xmx512m" ./gradlew clean build --no-daemon --max-workers=1
```

For emergency deployment only, tests can be skipped temporarily:

```bash
GRADLE_OPTS="-Dorg.gradle.jvmargs=-Xmx512m" ./gradlew clean bootJar -x test --no-daemon --max-workers=1
```

Skipping tests should be treated as temporary and recorded with the reason.

## Deploy

Manual deployment should use the integration branch:

```bash
cd ~/app/backend
scripts/deploy-backend.sh --branch dev
```

Use `--pull` only when the branch should be updated from its configured remote.

GitHub Actions artifact mode skips Git and Gradle work on EC2. The workflow
uploads both files below to `/tmp`:

- the verified executable jar: `/tmp/aipm-backend-COMMIT_SHA.jar`
- the deploy script from the same commit: `/tmp/deploy-backend-COMMIT_SHA.sh`

The remote command runs the uploaded `/tmp/deploy-backend-COMMIT_SHA.sh`, not any
older copy that may already exist in `/home/ec2-user/app/backend/scripts`.

```bash
/tmp/deploy-backend-COMMIT_SHA.sh --artifact /tmp/aipm-backend-COMMIT_SHA.jar --revision COMMIT_SHA
```

The script backs up the current `/opt/aipm/backend/app.jar`, stages the new jar
as `app.jar.new`, replaces `app.jar`, restarts `aipm-backend`, and verifies
`/actuator/health`. On failure it restores the previous jar and restarts the
service again. Successful deployments write the deployed commit SHA to
`/opt/aipm/backend/REVISION`.

Runtime ownership policy:

- `/opt/aipm/backend`: `root:root`, mode `755`
- `/opt/aipm/backend/app.jar`, `REVISION`, and jar backups: `root:root`, mode `644`
- `/opt/aipm/backend/logs`: `ec2-user:ec2-user`

## GitHub Actions

Two workflows are expected:

- `.github/workflows/backend-ci.yml`: runs on pull requests targeting `dev` and
  manual dispatch. It sets up Java 21, uses the Gradle cache, and runs
  `./gradlew clean test bootJar --no-daemon --max-workers=1`.
- `.github/workflows/backend-dev-deploy.yml`: runs only on pushes to `dev` and
  manual dispatch from the `dev` branch. It does not auto-deploy `main`.

The deploy workflow builds on GitHub-hosted runners, uploads the jar and the
same commit's deploy script to EC2 with SCP, applies `chmod 700` to the uploaded
script, verifies non-interactive sudo with `sudo -n true`, then runs the uploaded
script over SSH. Runtime DB credentials stay on EC2 in `/etc/aipm/backend.env`;
do not put DB passwords in GitHub Secrets.

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

## Future Work

- Add a real JWT or session authentication design.
- Register the backend API URL in Vercel environment variables.
- Register GitHub Actions secrets for the dev deployment workflow.
- Move MySQL to RDS after schema and migration policy are stable.
- Connect FastAPI AI server endpoints.
