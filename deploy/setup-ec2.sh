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
#   4. downloads the code from GitHub and builds the app
#   5. writes /opt/sfps/.env with generated secrets (you add Gmail + Razorpay + super admin)
#   6. runs the app as a service that starts again by itself after a crash or reboot
#   7. puts Nginx in front with HTTPS on https://<your-ip>.sslip.io
# Running it again is safe: existing secrets and data are kept.
# =====================================================================
set -euo pipefail

CERT_EMAIL="${1:-}"
REPO="https://github.com/Balavigneshcse/Smart_Farmer_Procurement_System.git"
APP_DIR=/opt/sfps
CODE_DIR=$APP_DIR/code
ENV_FILE=$APP_DIR/.env

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
    certbot python3-certbot-nginx curl openssl

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

echo "== 5. Code and build"
if [ -d $CODE_DIR/.git ]; then git -C $CODE_DIR pull -q; else git clone -q $REPO $CODE_DIR; fi
(cd $CODE_DIR && mvn -q -B -DskipTests package)
cp $CODE_DIR/target/smart-farmer-procurement-*.jar $APP_DIR/app.jar

PUBLIC_IP=$(curl -s https://checkip.amazonaws.com | tr -d '[:space:]')
DOMAIN="${PUBLIC_IP//./-}.sslip.io"

echo "== 6. Settings ($ENV_FILE)"
if [ ! -f $ENV_FILE ]; then
    cat > $ENV_FILE <<ENV
# Settings of the live site. Edit with: sudo nano $ENV_FILE   then: sudo systemctl restart sfps

# --- Database (made by the setup script) ---
DB_URL=jdbc:postgresql://localhost:5432/farmer_procurement
DB_USERNAME=sfps
DB_PASSWORD=$DB_PASSWORD

# --- Secrets (made by the setup script). Never change PAYOUT_ENCRYPTION_KEY: saved bank details would become unreadable. ---
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

# --- Address of the site (links and QR pictures in emails) ---
APP_URL=https://$DOMAIN

# The app only listens inside the server; Nginx serves it to the internet with HTTPS.
server.address=127.0.0.1
ENV
fi
chown -R sfps:sfps $APP_DIR
chmod 600 $ENV_FILE

echo "== 7. Service"
cp $CODE_DIR/deploy/sfps.service /etc/systemd/system/sfps.service
systemctl daemon-reload
systemctl enable -q sfps
systemctl restart sfps

echo "== 8. Nginx + HTTPS for $DOMAIN"
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
