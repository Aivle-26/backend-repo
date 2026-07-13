# EC2 Deployment

This backend currently runs on AWS EC2 behind Nginx.

## Architecture

Vercel React frontend -> EC2 Nginx -> Spring Boot backend -> Docker MySQL.

FastAPI AI services, GitHub Actions deployment, HTTPS, and RDS migration are future integration steps.

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

Do not commit real secrets.

## MySQL

The current MySQL container uses the external Docker volume `aipm-mysql-data`.

Check status:

```bash
sudo docker ps
sudo docker inspect aipm-mysql --format '{{.State.Status}} {{.HostConfig.RestartPolicy.Name}}'
```

If moving to Compose, keep the same volume name and confirm a DB dump exists before replacing the container.

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

```bash
cd ~/app/backend
scripts/deploy-backend.sh --branch infra/ec2-deployment-hardening
```

Use `--pull` only when the branch should be updated from its configured remote.

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

Amazon Linux 2023 installs a default port 80 server block in `/etc/nginx/nginx.conf`.
Use the `default.d` location include unless that stock server block is intentionally replaced.

```bash
sudo dnf install -y nginx
sudo cp deploy/nginx/server-tokens.conf /etc/nginx/conf.d/server-tokens.conf
sudo cp deploy/nginx/aipm-default.d.conf /etc/nginx/default.d/aipm.conf
sudo nginx -t
sudo systemctl enable nginx
sudo systemctl restart nginx
```

`deploy/nginx/aipm.conf` is a full server-block alternative for hosts where the stock default server block has been removed.

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
- Add GitHub Actions secrets and automated deployment.
- Move MySQL to RDS after schema and migration policy are stable.
- Connect FastAPI AI server endpoints.
