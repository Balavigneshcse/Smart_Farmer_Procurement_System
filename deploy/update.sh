#!/usr/bin/env bash
# Puts the newest code from GitHub on the live site:   sudo bash /opt/sfps/code/deploy/update.sh
# Database changes (Flyway migrations) run by themselves when the app starts, so the script first
# saves a copy of the database. If the new version does not become healthy, the previous one is put back.
# Going back to the previous jar is safe because the migrations are written so that the previous
# version still works on the changed database (for example V9 keeps the old columns).
set -euo pipefail
APP_DIR=/opt/sfps
CODE_DIR=$APP_DIR/code
BACKUP_DIR=/var/backups/sfps

# Puts a jar in place as app.jar. mv swaps the file in one step, so the running app is never left
# with a half-copied jar (it keeps reading its old file until it restarts).
use_jar() {
    cp "$1" $APP_DIR/app.jar.new
    chown sfps:sfps $APP_DIR/app.jar.new
    mv $APP_DIR/app.jar.new $APP_DIR/app.jar
}

# Asks the app's health page every 5 seconds, for up to 3 minutes (a t3.micro starts in about a minute).
wait_until_up() {
    for _ in $(seq 36); do
        if curl -fsS --max-time 5 http://127.0.0.1:8080/actuator/health 2>/dev/null | grep -q '"UP"'; then
            return 0
        fi
        sleep 5
    done
    return 1
}

# 1. Build. If the pull or the build fails, set -e stops here and the old version keeps running.
# (The code folder belongs to the sfps user; safe.directory lets root use it with Git.)
git -c safe.directory=$CODE_DIR -C $CODE_DIR pull -q
(cd $CODE_DIR && mvn -q -B -DskipTests clean package)

# 2. Copy of the database before the new migrations change it (the nightly job deletes it after about a week).
install -d -o postgres -g postgres -m 700 $BACKUP_DIR
BACKUP=$BACKUP_DIR/sfps-before-update-$(date +%Y-%m-%d-%H%M).dump
sudo -u postgres pg_dump -Fc -f $BACKUP farmer_procurement
echo "Database saved to $BACKUP"

# 3. Keep the running version as app.jar.prev, put the new one in place and restart.
cp $APP_DIR/app.jar $APP_DIR/app.jar.prev
chown sfps:sfps $APP_DIR/app.jar.prev
use_jar $CODE_DIR/target/smart-farmer-procurement-*.jar
cp $CODE_DIR/deploy/sfps.service /etc/systemd/system/sfps.service
systemctl daemon-reload
STARTED=$(date '+%Y-%m-%d %H:%M:%S')
systemctl restart sfps

# 4. Check that the new version really works.
echo "Waiting for the new version to start (up to 3 minutes)..."
if wait_until_up; then
    echo "Updated. The new version is running."
    exit 0
fi

# 5. It never became healthy: put the previous version back.
echo "!! The new version did not start. Putting the previous version back..."
use_jar $APP_DIR/app.jar.prev
systemctl restart sfps
if wait_until_up; then
    echo "!! The previous version is running again."
else
    echo "!! The previous version did not start either. Database copy from before the update: $BACKUP"
    echo "!! (deploy/README.md, section 8 Backups, shows how to put it back)"
fi
echo "!! Read why the new version failed with: sudo journalctl -u sfps --since \"$STARTED\" --no-pager"
exit 1
