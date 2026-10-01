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

The database password, `JWT_SECRET` and `PAYOUT_ENCRYPTION_KEY` are already generated. Fill in:

| Setting | Value |
|---|---|
| `SUPERADMIN_USERNAME`, `SUPERADMIN_PASSWORD` | the super admin login for the live site (use a strong password) |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | the same Gmail address and App Password as in your PC's `.env` (AWS allows Gmail's port 587) |
| `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET` | your Razorpay **test** keys |

Save with Ctrl+O, Enter, Ctrl+X. Then:

```bash
sudo systemctl restart sfps
sudo journalctl -u sfps -f      # wait for "Started ProcurementApplication", then Ctrl+C
```

Never change `PAYOUT_ENCRYPTION_KEY` later: farmers' saved bank details could not be read any more.

## 4. First login

1. Open the site address and log in at `/staff/login.html` with the super admin.
2. **Admins & staff**: add an admin and a staff account for each centre (the accounts in
   `staff-accounts.xlsx` exist only in your PC's database).
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

## 6. Avoid surprise charges

**Billing → Budgets → Create budget → Zero spend budget**, with your email. AWS emails you if anything
starts to cost money.

Keep the server **running**. If it is stopped and started, AWS gives it a new IP address and the site
address changes; run the setup script again to get the new address and certificate (data and secrets are kept).

## 7. Updating the live site

After pushing new code to GitHub, open the EC2 terminal and run:

```bash
sudo bash /opt/sfps/code/deploy/update.sh
```

## Useful commands

| What | Command |
|---|---|
| Is the app running? | `sudo systemctl status sfps` |
| Live log | `sudo journalctl -u sfps -f` |
| Restart after changing settings | `sudo systemctl restart sfps` |
| Database console | `sudo -u postgres psql farmer_procurement` |
| Renew HTTPS (automatic, to test) | `sudo certbot renew --dry-run` |
