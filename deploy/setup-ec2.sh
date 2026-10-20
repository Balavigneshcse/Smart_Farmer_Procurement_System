#!/usr/bin/env bash
# =====================================================================
# Smart Farmer Procurement System - first-time setup on an AWS EC2 server
# (Ubuntu 24.04, t3.micro / t2.micro). No Docker: Java runs the app directly.
#
#   sudo bash setup-ec2.sh your.email@gmail.com
#
# The email is only for the free HTTPS certificate (Let's Encrypt expiry notices).
# What it does:
#   1. adds 2 GB of swap (1 GB of memory is tight for building and running Java)
#   2. installs Java 21, Maven, PostgreSQL, Nginx and Certbot
#   3. creates the database and its user with a random password
#   4. saves a copy of the database every night in /var/backups/sfps (the last 7 are kept)
#   5. downloads the code from GitHub and builds the app
#   6. writes /opt/sfps/.env with generated secrets (you add Gmail + Razorpay + super admin)
#   7. runs the app as a service that starts again by itself after a crash or reboot
#   8. puts Nginx in front with HTTPS on https://<your-ip>.sslip.io (with login rate limits)
# Running it again is safe: existing secrets and data are kept. If the server's IP address
# changed, APP_URL in .env is moved to the new sslip.io address (a custom domain is left alone).
# =====================================================================
set -euo pipefail

CERT_EMAIL="${1:-}"
REPO="https://github.com/Balavigneshcse/Smart_Farmer_Procurement_System.git"
APP_DIR=/opt/sfps
CODE_DIR=$APP_DIR/code
ENV_FILE=$APP_DIR/.env
BACKUP_DIR=/var/backups/sfps

if [ "$(id -u)" -ne 0 ]; then echo "Run with sudo: sudo bash setup-ec2.sh you@gmail.com"; exit 1; fi
if [ -z "$CERT_EMAIL" ]; then echo "Give your email: sudo bash setup-ec2.sh you@gmail.com"; exit 1; fi

echo "== 1. Swap"
if ! swapon --show | grep -q /swapfile; then
    fallocate -l 2G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
    echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

echo "== 2. Packages"
apt-get update -q
DEBIAN_FRONTEND=noninteractive apt-get install -y -q openjdk-21-jdk-headless maven git postgresql nginx \
    certbot python3-certbot-nginx curl openssl cron

# The server's public IP gives the site its sslip.io name. It is looked up before anything else
# changes, so if the lookup fails a live server is left exactly as it was (old version still running).
PUBLIC_IP=$(curl -fsS https://checkip.amazonaws.com | tr -d '[:space:]' || true)
if ! [[ $PUBLIC_IP =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo "!! Could not find the server's public IP address. Run the script again in a minute."; exit 1
fi
DOMAIN="${PUBLIC_IP//./-}.sslip.io"

echo "== 3. App user and folders"
id sfps >/dev/null 2>&1 || useradd --system --home-dir $APP_DIR --shell /usr/sbin/nologin sfps
mkdir -p $APP_DIR

echo "== 4. Database"
if [ ! -f $ENV_FILE ]; then
    DB_PASSWORD=$(openssl rand -hex 16)
    sudo -u postgres psql -v ON_ERROR_STOP=1 -q <<SQL
DO \$\$ BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'sfps') THEN
    CREATE ROLE sfps LOGIN PASSWORD '$DB_PASSWORD';
  ELSE
    ALTER ROLE sfps PASSWORD '$DB_PASSWORD';
  END IF;
END \$\$;
SQL
    sudo -u postgres psql -tAc "SELECT 1 FROM pg_database WHERE datname = 'farmer_procurement'" | grep -q 1 \
        || sudo -u postgres createdb -O sfps farmer_procurement
fi

echo "== 5. Nightly database backup ($BACKUP_DIR)"
# Only the postgres user can read the copies (they hold farmers' details).
install -d -o postgres -g postgres -m 700 $BACKUP_DIR
# A cron.d line is: minute hour day month weekday USER command. cron treats % as a new line,
# so it is written \% here. 21:30 on the server's clock (EC2 uses UTC) is 3 a.m. in India.
# Old copies are deleted only after a new copy was saved (&&). -mmin +9360 means older than
# 6.5 days (9360 minutes): tonight's copy and the 6 before it stay, the one from a week ago goes.
# Whole days (-mtime) would be a coin toss for that one: find rounds ages down to whole days, and
# each night's copy is finished a few seconds earlier or later than the night before.
cat > /etc/cron.d/sfps-backup <<'CRON'
# Made by setup-ec2.sh: a copy of the database every night; the last 7 nightly copies are kept.
30 21 * * * postgres pg_dump -Fc -f /var/backups/sfps/sfps-$(date +\%Y-\%m-\%d).dump farmer_procurement && find /var/backups/sfps -name 'sfps-*.dump' -mmin +9360 -delete
CRON
chmod 644 /etc/cron.d/sfps-backup

echo "== 6. Code and build"
# The code folder belongs to the sfps user. Git refuses to work in a folder owned by someone else,
# even for root, so safe.directory says this one is ours.
if [ -d $CODE_DIR/.git ]; then git -c safe.directory=$CODE_DIR -C $CODE_DIR pull -q; else git clone -q $REPO $CODE_DIR; fi
(cd $CODE_DIR && mvn -q -B -DskipTests clean package)
# Running the script again on a live server also puts new code live, and its database changes
# (Flyway migrations) run when the app restarts below. So, like update.sh, save a copy of the
# database first and keep the running version as app.jar.prev (it can be put back by hand).
if [ -f $APP_DIR/app.jar ]; then
    sudo -u postgres pg_dump -Fc -f $BACKUP_DIR/sfps-before-update-$(date +%Y-%m-%d-%H%M).dump farmer_procurement
    cp $APP_DIR/app.jar $APP_DIR/app.jar.prev
    echo "   saved a copy of the database and kept the running version as $APP_DIR/app.jar.prev"
fi
# Copy next to it, then mv: mv swaps the file in one step, so the running app is never left
# with a half-copied jar (it keeps reading its old file until the restart below).
cp $CODE_DIR/target/smart-farmer-procurement-*.jar $APP_DIR/app.jar.new
mv $APP_DIR/app.jar.new $APP_DIR/app.jar

echo "== 7. Settings ($ENV_FILE)"
# An APP_URL that this script wrote earlier (an sslip.io address).
SSLIP_APP_URL='^APP_URL=https?://[0-9a-z.-]+\.sslip\.io/?[[:space:]]*$'
if [ ! -f $ENV_FILE ]; then
    cat > $ENV_FILE <<ENV
# Settings of the live site. Edit with: sudo nano $ENV_FILE   then: sudo systemctl restart sfps

# --- Database (made by the setup script) ---
DB_URL=jdbc:postgresql://localhost:5432/farmer_procurement
DB_USERNAME=sfps
DB_PASSWORD=$DB_PASSWORD

# --- Secrets (made by the setup script). The app does not start without them on the server. ---
# Never change PAYOUT_ENCRYPTION_KEY: saved bank details would become unreadable.
JWT_SECRET=$(openssl rand -hex 32)
PAYOUT_ENCRYPTION_KEY=$(openssl rand -hex 32)

# --- Fill these in ---
SUPERADMIN_USERNAME=
SUPERADMIN_PASSWORD=

EMAIL_PROVIDER=smtp
MAIL_USERNAME=
MAIL_PASSWORD=

RAZORPAY_KEY_ID=
RAZORPAY_KEY_SECRET=

# --- Optional: OTP emails a day for the whole site (default 200) ---
# OTP_DAILY_LIMIT=200

# --- Address of the site (links and QR pictures in emails) ---
APP_URL=https://$DOMAIN

# The app only listens inside the server; Nginx serves it to the internet with HTTPS.
server.address=127.0.0.1
ENV
elif grep -qE "$SSLIP_APP_URL" $ENV_FILE; then
    # The settings are kept, but a stopped and started server gets a new IP address from AWS.
    # Email links and QR pictures must point to the new sslip.io name, not the old one.
    sed -i -E "s#$SSLIP_APP_URL#APP_URL=https://$DOMAIN#" $ENV_FILE
    echo "   kept the existing settings; APP_URL is https://$DOMAIN"
else
    echo "   kept the existing settings; APP_URL is your own address, left as it is"
fi
chown -R sfps:sfps $APP_DIR
chmod 600 $ENV_FILE

echo "== 8. Service"
cp $CODE_DIR/deploy/sfps.service /etc/systemd/system/sfps.service
systemctl daemon-reload
systemctl enable -q sfps
systemctl restart sfps

echo "== 9. Nginx + HTTPS for $DOMAIN"
sed "s/__DOMAIN__/$DOMAIN/" $CODE_DIR/deploy/nginx-sfps.conf > /etc/nginx/sites-available/sfps
ln -sf /etc/nginx/sites-available/sfps /etc/nginx/sites-enabled/sfps
rm -f /etc/nginx/sites-enabled/default
nginx -t -q && systemctl reload nginx
certbot --nginx -d "$DOMAIN" --non-interactive --agree-tos -m "$CERT_EMAIL" --redirect -q || \
    echo "!! HTTPS certificate failed. Check that ports 80 and 443 are open in the security group, then run the script again."

echo
echo "Done. Next:"
echo "  1. sudo nano $ENV_FILE      (fill in super admin, Gmail and Razorpay)"
echo "  2. sudo systemctl restart sfps"
echo "  3. open https://$DOMAIN     (first start takes about a minute)"
echo "  Logs: sudo journalctl -u sfps -f"
echo "  Database copies: every night in $BACKUP_DIR (the last 7 are kept)"
