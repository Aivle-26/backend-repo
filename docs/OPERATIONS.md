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

```bash
sudo docker exec -e MYSQL_PWD="$DB_PASSWORD" aipm-mysql \
  mysqldump -uaipm_user --single-transaction --routines --triggers --no-tablespaces aipm \
  > ~/backups/$BACKUP_DATE/aipm.sql
```

## Restore Notes

Do not remove `aipm-mysql-data`. Restore into a verified MySQL container only after confirming the target volume and dump.

## Troubleshooting

- If the backend fails to start, inspect `journalctl -u aipm-backend -n 100 --no-pager`.
- If Gradle is slow on EC2, keep `--max-workers=1` and `GRADLE_OPTS="-Dorg.gradle.jvmargs=-Xmx512m"`.
- If health returns 401 or 403, confirm `/actuator/health` is exposed and the active profile is `prod`.
- If Nginx returns 502, verify Spring Boot is listening on `127.0.0.1:8080`.
