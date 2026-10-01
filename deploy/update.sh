#!/usr/bin/env bash
# Puts the newest code from GitHub on the live site:   sudo bash /opt/sfps/code/deploy/update.sh
# Database changes (Flyway migrations) run by themselves when the app starts.
set -euo pipefail
APP_DIR=/opt/sfps
git -C $APP_DIR/code pull -q
(cd $APP_DIR/code && mvn -q -B -DskipTests package)
cp $APP_DIR/code/target/smart-farmer-procurement-*.jar $APP_DIR/app.jar
chown sfps:sfps $APP_DIR/app.jar
cp $APP_DIR/code/deploy/sfps.service /etc/systemd/system/sfps.service
systemctl daemon-reload
systemctl restart sfps
echo "Updated. Follow the start-up with: sudo journalctl -u sfps -f"
