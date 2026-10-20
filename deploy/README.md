# Deploying (M8): AWS EC2, no Docker

The whole site runs on one free-tier EC2 server in Mumbai:

```
Internet ──HTTPS──> Nginx ──> Java 21 (the app, a systemd service) ──> PostgreSQL
                                   └──> Gmail SMTP (emails), Razorpay test API (payments)
```

Everything is installed by one script, `setup-ec2.sh` (in this folder), which you run in AWS's browser terminal.
Nothing needs to be installed on your PC. Time: about 25 minutes.

**Cost.** Accounts created before 15 July 2025 get 750 hours a month of `t2.micro`/`t3.micro` free for 12 months.
Newer accounts get the credit-based free plan (up to $200 of credit for 6 months). This setup uses one micro
server and 20 GB of disk, which fits either way. Step 6 sets an alert so nothing is charged by surprise.

## 1. Launch the server

1. Open the AWS console and choose the region **Asia Pacific (Mumbai) `ap-south-1`** (top right).
2. **EC2 → Launch instance**:
   - Name: `smart-farmer`
   - Image: **Ubuntu Server 24.04 LTS** (Free tier eligible)
   - Instance type: **t3.micro** (or t2.micro), marked Free tier eligible
   - Key pair: *Proceed without a key pair* (you will use the browser terminal)
   - Network settings: tick **Allow SSH**, **Allow HTTPS** and **Allow HTTP** from the internet
   - Storage: **20 GiB gp3**
3. **Launch instance**, wait until the state is *Running*.

## 2. Run the setup script

1. Select the instance → **Connect** → **EC2 Instance Connect** → **Connect**. A terminal opens in the browser.
2. Paste this (put your own email at the end; it is only used for the HTTPS certificate):

```bash
curl -fsSL https://raw.githubusercontent.com/Balavigneshcse/Smart_Farmer_Procurement_System/main/deploy/setup-ec2.sh -o setup.sh && sudo bash setup.sh you@gmail.com
```

It takes about 8 minutes (most of it is the first Maven build). At the end it prints the site address,
`https://<your-ip-with-dashes>.sslip.io`. sslip.io is a free name service that points that name at your
server's IP, so the HTTPS certificate works without buying a domain.

## 3. Fill in the settings

```bash
sudo nano /opt/sfps/.env
```

The database password, `JWT_SECRET` and `PAYOUT_ENCRYPTION_KEY` are already generated. Leave them as they are:
on the server (any `APP_URL` other than `http://localhost` or `http://127.0.0.1`) the app refuses to start when
`JWT_SECRET` or `PAYOUT_ENCRYPTION_KEY` is missing or still the built-in `local-dev-...` development value. Fill in:

| Setting | Value |
|---|---|
| `SUPERADMIN_USERNAME`, `SUPERADMIN_PASSWORD` | the super admin login for the live site (use a strong password) |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | the same Gmail address and App Password as in your PC's `.env` (AWS allows Gmail's port 587) |
| `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET` | your Razorpay **test** keys |
| `OTP_DAILY_LIMIT` (optional) | the maximum number of OTP emails a day for the whole site (default `200`), so a flood of requests cannot use up Gmail's daily sending limit. To change it, add a line such as `OTP_DAILY_LIMIT=300` (a server set up with the newest script already has the line, commented out: remove the `#`) |

Save with Ctrl+O, Enter, Ctrl+X. Then:

```bash
sudo systemctl restart sfps
sudo journalctl -u sfps -f      # wait for "Started ProcurementApplication", then Ctrl+C
```

Never change `PAYOUT_ENCRYPTION_KEY` later: farmers' saved bank details could not be read any more.

## 4. First login

1. Open the site address and log in at `/staff/login.html` with the super admin.
2. **Admins & staff**: the migration `V5__roles_and_staff.sql` also creates one admin and one staff account per
   centre on the live database: `admin.tnj`, `staff.tnj`, `admin.tvr`, `staff.tvr`, `admin.ngt`, `staff.ngt`.
   Their usernames are public in the repository, so do this right after the first start: open each one and set a
   **new password**, and untick **Can log in** for the ones you will not use. Add more accounts here if needed.
3. For a demo: **Dashboard → Load demo day**.

## 5. Smoke test

| Check | Expected |
|---|---|
| `https://…sslip.io/actuator/health` | `{"status":"UP"}` |
| Home page | Centres and rates load; language and theme buttons work; padlock in the address bar |
| Farmer login with a real email | OTP email arrives |
| Register with bank or UPI details, book a slot | Confirmation email with the QR code |
| Staff login, check in the token (typing or camera scan), call next, weighment | The farmer's booking page shows the live queue |
| Admin → Payments → Pay with UPI `success@razorpay` | Status Paid, "payment credited" email |
| `/display.html?centre=1` | TV board updates live |
| The next day: `sudo ls -lh /var/backups/sfps` | A `sfps-<date>.dump` file from the night (section 8) |

## 6. Avoid surprise charges

**Billing → Budgets → Create budget → Zero spend budget**, with your email. AWS emails you if anything
starts to cost money.

Keep the server **running**. If it is stopped and started, AWS gives it a new IP address and the site
address changes; run the setup script again (the command from step 2) to get the new address and certificate.
Data and secrets are kept, and `APP_URL` in `.env` is moved to the new sslip.io address so that email links and
QR pictures keep working (an address on your own domain is left alone).

## 7. Updating the live site

After pushing new code to GitHub, open the EC2 terminal and run:

```bash
sudo bash /opt/sfps/code/deploy/update.sh
```

What it does:

1. Pulls and builds the new code. If the build fails, it stops and the old version simply keeps running.
2. Saves a copy of the database as `/var/backups/sfps/sfps-before-update-<date-time>.dump`, because the new
   version's database changes (Flyway migrations) run when it starts.
3. Keeps the running version as `/opt/sfps/app.jar.prev`, puts the new one in place and restarts the app.
4. Waits up to 3 minutes for `http://127.0.0.1:8080/actuator/health` to say `UP` (a t3.micro starts in about a minute).
   If it never does, it puts `app.jar.prev` back, restarts, prints the `journalctl -u sfps` command that shows
   why the new version failed, and ends with an error.

Going back to the previous version is safe because the migrations are written so that the previous version
still works on the changed database (for example, V9 keeps the old columns).

**A server set up before the rate limits and backups were added:** paste the command from step 2 once more.
It is safe (secrets and data are kept) and installs the new Nginx settings, the nightly backup job and the
newest code. Like `update.sh`, it first saves a copy of the database and keeps the running version as
`app.jar.prev`, but it does not wait for the health check: open `/actuator/health` afterwards (step 5) and, if
the new version does not start, go back by hand (last line of *Useful commands*).
The older `update.sh` on such a server stops at `git pull` with "detected dubious ownership",
because the code folder belongs to the `sfps` user; the new scripts allow it.

## 8. Backups

Every night at 3 a.m. India time (21:30 UTC, the server's clock) a copy of the database is saved as
`/var/backups/sfps/sfps-<YYYY-MM-DD>.dump` (`pg_dump` custom format, compressed). The last 7 nightly copies are
kept; older ones are deleted, and so are the `sfps-before-update-...` copies from `update.sh` after about a
week. The job is `/etc/cron.d/sfps-backup`. Only the `postgres` user can open the folder, because the copies
hold farmers' details.

To put a copy back (this replaces everything in the live database with that copy):

```bash
sudo ls -lh /var/backups/sfps                  # pick a copy
sudo systemctl stop sfps                       # nobody may write while the copy goes back
sudo -u postgres dropdb farmer_procurement     # delete the database ...
sudo -u postgres createdb -O sfps farmer_procurement   # ... make it again, empty, owned by sfps
sudo -u postgres pg_restore -d farmer_procurement /var/backups/sfps/sfps-2026-10-20.dump
sudo systemctl start sfps
```

The database is deleted and made again first so that the whole database goes back to the copy, including
the tables that a newer version added. (`pg_restore --clean` would only replace the tables that are in the copy:
a table added by a later migration would stay, and that migration would fail with "already exists" the next
time it runs.) If you go back to a `sfps-before-update-...` copy because of a bad update, start the previous
version instead: in place of the last line, run the go-back command (last line of *Useful commands*), or the
new version changes the database again when it starts.

**Keep a copy off the server too.** These copies are on the same disk as the database, so they are lost with
the server (instance deleted, disk broken). Turn on EBS snapshots, which AWS stores separately:
**EC2 → Elastic Block Store → Lifecycle Manager → Create lifecycle policy → EBS snapshot policy**, target
resource type *Instance* with the tag `Name` = `smart-farmer`, every 24 hours, keep 7 (let it create its
default role). Snapshots are charged for the space they use, so watch the budget from step 6. Copying a
dump to your PC now and then also works.

## 9. Login rate limits

Nginx counts login requests per IP address (`nginx-sfps.conf`):

| Request | Allowed per IP address |
|---|---|
| `/api/auth/otp/send` (send an OTP email) | 6 a minute, plus a burst of 12 at once |
| `/api/auth/otp/verify` and `/api/auth/login` (check an OTP or a staff password) | 20 a minute together, plus a burst of 20 |

Over the limit, Nginx itself answers `{"status":429,"code":"RATE_LIMITED","message":"Too many attempts. Please wait a minute and try again."}`
(the app's own 429 answers, such as `OTP_COOLDOWN` or `LOGIN_LOCKED`, pass through unchanged).
The limits are generous on purpose: many mobile users in India share one public IP address (carrier-grade
NAT), so a strict limit would block honest farmers of the same area. They only stop floods; the finer limits
are in the app: one OTP a minute and 5 an hour per email, `OTP_DAILY_LIMIT` for the whole site, and staff
logins locked per username and IP address after 5 wrong passwords (`app.staff-login` in `application.yml`).

The app knows the visitor's real IP address because Nginx sends it in `X-Forwarded-For`, and the app trusts
that header only from a proxy on the same server or network (`server.forward-headers-strategy: native`).
Every location in `nginx-sfps.conf` sends these headers; copy them into any location you add, or every
visitor looks like `127.0.0.1` and one person's wrong passwords would lock that account for everybody.

## Useful commands

| What | Command |
|---|---|
| Is the app running? | `sudo systemctl status sfps` |
| Live log | `sudo journalctl -u sfps -f` |
| Restart after changing settings | `sudo systemctl restart sfps` |
| Database console | `sudo -u postgres psql farmer_procurement` |
| Renew HTTPS (automatic, to test) | `sudo certbot renew --dry-run` |
| Database copies | `sudo ls -lh /var/backups/sfps` |
| Save a copy now | `sudo -u postgres pg_dump -Fc -f /var/backups/sfps/sfps-manual.dump farmer_procurement` |
| Go back to the previous version by hand | `sudo cp /opt/sfps/app.jar.prev /opt/sfps/app.jar && sudo systemctl restart sfps` |
