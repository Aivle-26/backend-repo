# Operations

## Health Checks

Backend direct:

```bash
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

## Service Commands

```bash
sudo systemctl start aipm-backend
sudo systemctl stop aipm-backend
sudo systemctl restart aipm-backend
systemctl status aipm-backend --no-pager
journalctl -u aipm-backend -n 100 --no-pager
```

## Backup

Before deployment or MySQL changes:

```bash
BACKUP_DATE=$(date +%Y%m%d_%H%M%S)
mkdir -p ~/backups/$BACKUP_DATE
cd ~/app/backend
git status > ~/backups/$BACKUP_DATE/git-status.txt
git diff > ~/backups/$BACKUP_DATE/git-diff.patch
git ls-files > ~/backups/$BACKUP_DATE/git-files.txt
cp -a src/main/resources ~/backups/$BACKUP_DATE/resources-backup
```

Database dump:

Do not print secret values. First confirm only the variable names in
`/etc/aipm/mysql.env`:

```bash
sudo awk -F= '/^[A-Za-z_][A-Za-z0-9_]*=/ {print $1}' /etc/aipm/mysql.env | sort
```

Expected variable names:

```text
MYSQL_DATABASE
MYSQL_PASSWORD
MYSQL_ROOT_PASSWORD
MYSQL_USER
```

Then run the dump in a subshell that sources the file without echoing values:

```bash
sudo bash -c '
  set -euo pipefail
  set -a
  . /etc/aipm/mysql.env
  set +a
  docker exec -e MYSQL_PWD="$MYSQL_PASSWORD" aipm-mysql \
    mysqldump -u"$MYSQL_USER" \
      --single-transaction \
      --routines \
      --triggers \
      --no-tablespaces \
      "$MYSQL_DATABASE"
' > ~/backups/$BACKUP_DATE/aipm.sql
```

Check backup and disk usage:

```bash
ls -lh ~/backups/$BACKUP_DATE/aipm.sql
df -h
du -sh ~/backups /opt/aipm/backend
```

## Restore Notes

Do not remove `aipm-mysql-data`. Restore into a verified MySQL container only after confirming the target volume and dump.

## GitHub Actions

Pull requests targeting `dev` run backend CI only. The CI workflow builds with
Java 21 and runs:

```bash
GRADLE_OPTS="-Dorg.gradle.jvmargs=-Xmx512m" ./gradlew clean test bootJar --no-daemon --max-workers=1
```

Pushes to `dev` run the dev deployment workflow. `main` is not automatically
deployed. The workflow uploads both the built jar and the current commit's
`scripts/deploy-backend.sh` to `/tmp` on EC2. It applies `chmod 700` to the
uploaded script, verifies non-interactive sudo with `sudo -n true`, and runs that
temporary script. It does not rely on an older script already present in
`/home/ec2-user/app/backend/scripts`. DB credentials remain on EC2 in
`/etc/aipm/backend.env`; they are not GitHub Secrets.

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

Manual rollback uses the most recent jar backup:

```bash
ls -lt /opt/aipm/backend/app.jar.*.bak
sudo cp /opt/aipm/backend/app.jar.YYYYMMDD_HHMMSS.bak /opt/aipm/backend/app.jar.rollback
sudo chown ec2-user:ec2-user /opt/aipm/backend/app.jar.rollback
sudo chmod 664 /opt/aipm/backend/app.jar.rollback
sudo mv /opt/aipm/backend/app.jar.rollback /opt/aipm/backend/app.jar
ls -lh /opt/aipm/backend/app.jar
sudo systemctl restart aipm-backend
curl -i http://127.0.0.1:8080/actuator/health
curl -i http://127.0.0.1/actuator/health
curl -i http://<EC2_ELASTIC_IP>/actuator/health
```

## Troubleshooting

- If the backend fails to start, inspect `journalctl -u aipm-backend -n 100 --no-pager`.
- If Gradle is slow on EC2, keep `--max-workers=1` and `GRADLE_OPTS="-Dorg.gradle.jvmargs=-Xmx512m"`.
- If health returns 401 or 403, confirm `/actuator/health` is exposed and the active profile is `prod`.
- If Nginx returns 502, verify Spring Boot is listening on `127.0.0.1:8080`.
