# Smart Farmer Procurement System

Farmers often wait all day at procurement centres without knowing the schedule or what happened to their crop and payment. This app fixes that:

- **Slot booking.** Farmers register with their email address (a one-time password is emailed to them), say how they want to be paid (bank account or UPI, stored encrypted) and book a time slot at a procurement centre. The confirmation email carries the token's QR code.
- **Live queue.** Counter staff check farmers in and call them to counters. Farmers see their position and waiting time live, and a TV screen at the centre shows who is being served.
- **Email alerts.** Farmers get an email at key moments (booked, reminder, turn near, weighed, paid), in their chosen language (English, Tamil or Hindi). Every update also appears on their dashboard.
- **Tracking.** Every booking has a timeline, from booked through weighed to paid, with payment through Razorpay (test mode).
- **Admin dashboard.** Today's farmers, queue lengths, average wait, crop procured and payments, with charts for the last 7 days.
- **Three staff levels.** A super admin adds centres and centre admins and sets crop rates; each centre admin sees only their own centre and adds its counter staff; staff work the operator desk.

Theme: Smart Automation · College mini project

## Tech stack

| Layer | Technology |
|---|---|
| Frontend | HTML, CSS, JavaScript (served by Spring Boot), light and dark theme, English / Tamil / Hindi |
| Backend | Java 21, Spring Boot 4.1, Maven, Spring Data JPA, Flyway |
| Database | PostgreSQL |
| Payments | Razorpay (test mode) |
| QR codes | qrcodejs in the browser, ZXing on the server (email) |
| Email | Gmail SMTP (on the PC and on the server); optional Brevo email API |
| Hosting | AWS EC2 free tier (Mumbai): Java 21 + PostgreSQL + Nginx on one server, no Docker |

## Run it locally (IntelliJ IDEA)

1. **Create the database.** In pgAdmin or psql, logged in as `postgres`:
   ```sql
   CREATE DATABASE farmer_procurement;
   ```
   The app connects with user `postgres` / password `postgres` by default. Flyway creates all tables on the first start.
2. **Open the project.** In IntelliJ, choose File → Open and select this folder. IntelliJ detects `pom.xml` and imports it as a Maven project.
3. **Set the JDK.** In File → Project Structure → Project, set the SDK to **JDK 21**.
4. **Run.** Open `src/main/java/com/cropcoders/procurement/ProcurementApplication.java` and click the green ▶ next to `main`.
5. **Open the app.** Go to <http://localhost:8080>.

Useful URLs:
- <http://localhost:8080/api/ping>: health check
- <http://localhost:8080/api/centres>: procurement centres (JSON)
- <http://localhost:8080/api/crops>: crops and rates (JSON)
- <http://localhost:8080/farmer/login.html>: farmer login (English / Tamil / Hindi)
- <http://localhost:8080/staff/login.html>: staff, centre admin and super admin login (English)
- <http://localhost:8080/display.html?centre=1>: TV queue display
- <http://localhost:8080/actuator/health>: health of the app and database

### Logging in

**Farmers** log in with their email address and an OTP that is emailed to them. With `EMAIL_PROVIDER=smtp` in `.env` the email really goes out through Gmail (see *Emails and automation* below). With `EMAIL_PROVIDER=console` (the default when `.env` doesn't set it), nothing is sent and the email is printed in the IntelliJ **Run** console instead:

```
======== Email to ravi@example.com ========
Subject: 123456 is your login OTP
...
```

A new email address goes to the registration form: name, mobile number (for the centre to call), address, district, last 4 digits of Aadhaar, and language. That language is then used for the farmer's pages and emails.

**Staff, centre admins and the super admin** all log in on one page, `/staff/login.html`, with a username and password. The server finds the role and the browser opens the right screen.

| Role | Belongs to | Can do | Created by |
|---|---|---|---|
| Super admin | All centres | Adds centres and centre admins, sets crop rates, loads demo data, sees every centre | The app at start-up, from `SUPERADMIN_USERNAME` and `SUPERADMIN_PASSWORD` in `.env` |
| Centre admin | One centre | Dashboard, payments and settings of that centre; adds its staff | Super admin, under "Admins & staff" |
| Staff | One centre | Operator desk: check-in, call next, weighment | Centre admin (or super admin) |

Each browser tab keeps its own staff login (in `sessionStorage`), so an admin and a staff member can be logged in side by side in two tabs. Farmer logins are shared by all tabs and last 7 days (`localStorage`).

`V5__roles_and_staff.sql` creates one admin (`admin.tnj`, `admin.tvr`, `admin.ngt`) and one staff account (`staff.tnj`, `staff.tvr`, `staff.ngt`) per centre with random starting passwords. The passwords are in `staff-accounts.xlsx` in the project folder, which is git-ignored. Accounts are switched off rather than deleted, because weighments and payments point to them.

**Payment details.** At registration the farmer chooses how to be paid: a **bank account** (account holder, account number typed twice, IFSC code, bank name) or a **UPI ID**. The form and the server check the same rules (account number 9 to 18 digits, IFSC like `SBIN0001234`, UPI like `name@okaxis`). The browser only ever gets the last 4 digits of the account number back. The account number and the UPI ID are **encrypted in the database** (AES-256-GCM, `EncryptedText`) with the key `PAYOUT_ENCRYPTION_KEY` from `.env`, so a copy of the database alone does not reveal them. Farmers can change the details on their dashboard, and the admin's payments page shows where each farmer wants to be paid.

OTP rules: valid for 5 minutes, 3 wrong tries, a new OTP only after 60 seconds, at most 5 per hour per email address.

### Booking a slot

After logging in, a farmer clicks **Book a slot** and goes through 4 steps:
1. **Centre.** Centres in the farmer's own district are listed first.
2. **Day.** Today plus the next 6 days. A bar shows how busy each day is.
3. **Time.** Green, amber or red shows how crowded each slot is, and the least crowded time is marked *Suggested*.
4. **Crop and quantity.** An approximate amount is shown (quantity × rate).

The farmer then gets a **token** (e.g. `TNJ-001`) and a **QR code** to show at the centre. They can cancel until the slot starts, and the place goes back to the slot.

How it works (for the viva):
- **Slot generation.** `SlotGenerationJob` creates slots at startup and every morning at 7:00 for the next 7 days. The insert uses `ON CONFLICT DO NOTHING`, so running it twice doesn't create duplicates. Places per slot = counters × slot minutes ÷ average service minutes.
- **No overbooking.** A place is taken with a single SQL statement: `UPDATE slots SET booked = booked + 1 WHERE id = ? AND booked < capacity`. If it changes 0 rows, the slot is full. In a test, 6 farmers booked the last 5 places at the same moment: exactly 5 succeeded and 1 was told the slot is full.
- **One booking per day.** A partial unique index (`uq_active_booking`) stops a farmer from holding two active bookings on the same day.
- **Tokens.** `token_counters` is updated with `INSERT … ON CONFLICT DO UPDATE … RETURNING`, so two bookings can never get the same number.
- **Status history.** Every change is saved in `booking_events` and shown to the farmer as a timeline.

### Live queue

| Screen | URL | Who |
|---|---|---|
| Operator desk | `/operator/desk.html` | Counter staff: check in by token or QR code (typed, USB scanner or camera), **Call next** per counter, **Call again**, **Not present** |
| TV display | `/display.html?centre=1` | Centre TV: now serving per counter, next tokens with waiting time, labels in 3 languages, optional chime and spoken announcement |
| Farmer phone | `/farmer/booking.html?id=…` | Farmer: live position and waiting time, then "Please go to Counter 2 now" (the phone vibrates) |

How it works (for the viva):
- **Check-in** sets `queue_priority`. A farmer who arrives on time or early is placed by their slot start time. A farmer who arrives after slot end + 15 minutes is placed by their arrival time, which puts them at the back of the line.
- **Call next** picks the first farmer with `SELECT … ORDER BY queue_priority, checked_in_at LIMIT 1 FOR UPDATE SKIP LOCKED`, so two counters pressing the button at the same moment always get different farmers.
- **ETA** is calculated by `EtaCalculator` and unit tested. The average service time is the real average of the last farmers served today, or the centre's setting until at least 3 have been served. The wait for position *p* is `ceil(max(0, p − free counters) / counters) × average service time`.
- **Live updates** use Server-Sent Events. Each screen keeps one connection open (`/api/queue/{centreId}/stream`), and after every change is committed, `QueueSseHub` pushes the new queue to all screens of that centre. The browser reconnects by itself if the connection drops.
- **Privacy.** The public queue contains token numbers only, never names or phone numbers.

### Weighment and payment

**Counter staff.** On a busy counter, click **Record weighment**, then enter:
- weight (quintals)
- moisture (%)
- grade (A/B/C)
- decision: Accept or Reject

If the moisture is above the crop's limit (e.g. 17% for paddy), **Accept is blocked**. A rejection needs a reason.

- **Accepted:** amount = weight × the crop's rate, the booking becomes *Procured*, and a **pending payment** is created.
- **Rejected:** no payment. The farmer sees the reason.

**Admin.** Go to **Admin → Payments**, find a pending payment and click **Pay**. Razorpay Checkout opens in **test mode**: use UPI ID `success@razorpay`, or a test card from Razorpay's documentation. After payment the row shows **Paid** with the Razorpay reference (`pay_…`), and the farmer's page shows it too.

**Setup.** Copy `example.env` to `.env` in the project folder and put your Razorpay **test** keys in it. `.env` is git-ignored and is read automatically at startup:

```
RAZORPAY_KEY_ID=rzp_test_...
RAZORPAY_KEY_SECRET=...
```

The startup log shows `Razorpay: test mode, key rzp_test_…` when the keys are found. Live keys (`rzp_live_…`) are refused, so this project can never move real money.

How it works (for the viva):
1. **Create the order.** The server calls the Razorpay Orders API. The amount always comes from the database, never from the browser.
2. **Pay.** The browser opens Razorpay Checkout for that order.
3. **Receive the result.** Checkout returns `razorpay_order_id`, `razorpay_payment_id` and `razorpay_signature` to the browser.
4. **Verify.** The server recomputes `HMAC-SHA256(order_id + "|" + payment_id, key_secret)` and compares it with the signature, then marks the payment Paid. Only Razorpay and our server know the secret, so a forged "success" is rejected. `RazorpayGatewayTest` checks this against a value computed with `openssl`.
5. **Store amounts safely.** Amounts are stored in **paise** as whole numbers, so there are no rounding errors.
6. **Real life.** Money would flow from the government to the farmer (DBT / RazorpayX Payouts). In the demo, the admin paying through Checkout stands in for that step.

### Emails and automation

**Setup (Gmail).** Turn on 2-Step Verification for the Gmail account, create an **App Password** (Google Account → Security → App passwords), and put it in `.env`:

```
EMAIL_PROVIDER=smtp
MAIL_USERNAME=yourname@gmail.com
MAIL_PASSWORD=the16letterapppassword
```

Use `EMAIL_PROVIDER=console` to print emails in the log instead (handy for testing with made-up addresses).

| Email | When |
|---|---|
| Login OTP | Farmer asks for an OTP |
| Booking confirmed / cancelled | Farmer books or cancels |
| Reminder | About 2 hours before the slot (checked every 15 minutes, 7 am to 10 pm) |
| Your turn is near | Farmer reaches position 3 or better in the queue |
| Crop accepted / not accepted | Staff save the weighment (weight and amount, or the reason) |
| Payment done | Admin's Razorpay payment is verified |
| Missed slot | Not checked in by slot end + 2 hours (booking becomes *No show*) |

"Checked in" and "Go to counter N" are shown in the app only, because the farmer is already at the centre. All of these appear under **Notifications** on the farmer dashboard, with a ✉ mark when an email was sent.

How it works (for the viva):
- **Texts in 3 languages.** Email texts live in `messages.properties` (English), `messages_ta.properties` and `messages_hi.properties`, and are picked by the farmer's saved language. Every text is HTML-escaped before it goes into the email.
- **Sent after commit, in the background.** A notification is saved in the same transaction as the booking change. The email is sent by `EmailDispatcher` only **after** that transaction commits (`@TransactionalEventListener(AFTER_COMMIT)`), on a separate thread (`@Async`), so the operator's click is not slowed down by Gmail and no email goes out for a change that failed. The row is then marked `SENT` (with Gmail's message id) or `FAILED`.
- **Free quota.** At most `EMAIL_DAILY_LIMIT` (default 250) notification emails per day (Gmail allows 500, Brevo 300). After that, notifications are shown in the app only (`SKIPPED`). OTP emails are never capped, so farmers can always log in.
- **Scheduled jobs.** `AutomationJob` runs every 15 minutes from 7 am to 10 pm and also at startup (so nothing is missed after a restart). Nothing runs at night, so the free database can sleep too.
- **Pluggable sender.** `EmailSender` is an interface; `ConsoleEmailSender` and `SmtpEmailSender` are chosen by `EMAIL_PROVIDER`. `BrevoEmailSender` (HTTPS API) is there for hosts that block SMTP ports.
- **Fast OTP.** The OTP email is also sent in the background, so "Send OTP" answers in about 0.2 seconds and the farmer can type the code as soon as it arrives.
- **One kept-open Gmail connection.** Connecting to Gmail (TCP + TLS + login) takes about 2-3 seconds from here, so `SmtpEmailSender` opens the connection once at startup and reuses it for every email, like a connection pool with one connection. An email then leaves in about 2 seconds instead of 5-8. If Gmail has closed the connection, the sender reconnects and tries once more.

**QR code in the confirmation email.** The booking confirmation email carries the same QR code as the booking page, so the farmer can show it at the desk without opening the app. `QrCode` draws it with ZXing and it is sent inside the email (an inline picture), so mail apps that block web pictures still show it.

**Big amounts in test mode.** Razorpay test accounts refuse large payments (UPI allows at most ₹1,00,000), but a real procurement is often several lakh rupees. So when the amount is above ₹50,000 (`app.razorpay.test-charge-limit-paise`), Razorpay is asked for a ₹50,000 stand-in charge and the farmer's full amount is recorded as paid. The payments page says so before Checkout opens.

### Admin pages

| Page | What the admin does there (a centre admin sees only their own centre) |
|---|---|
| **Dashboard** (`/admin/dashboard.html`) | Today at a glance: farmers booked, waiting, served, average wait, crop procured, payments pending. A live table per centre (refreshes every 30 seconds) with a link to each TV screen. Charts of farmers and quintals per day for the last 7 days, and the crops bought this week. |
| **Payments** (`/admin/payments.html`) | Pays farmers through Razorpay test mode (see above). |
| **Centres & crops** (`/admin/settings.html`) | Super admin: adds centres, changes crop rates and moisture limits, adds crops (with Tamil and Hindi names), switches crops or centres off. Any admin: changes their centre's counters, opening hours and minutes per farmer. |
| **Admins & staff** (`/admin/staff.html`) | Super admin: adds centre admins and staff for any centre. Centre admin: adds staff for their own centre. Both can rename an account, set a new password or switch it off. |

How it works (for the viva):
- **Numbers from SQL.** `DashboardRepository` lets PostgreSQL do the counting with `COUNT(*) FILTER (WHERE status = …)`, `SUM` and `AVG`, so the dashboard needs a handful of queries instead of loading every booking. `generate_series` makes one row per day, so a day without bookings still shows as 0 in the chart. The charts use Chart.js.
- **Changing a centre safely.** Places per slot = counters × slot minutes ÷ minutes per farmer. After a change, slots that have not started get the new capacity, but never less than what is already booked (`GREATEST(new, booked)`). Empty slots outside new hours are deleted and slots for new hours are created. Farmers who already booked always keep their place. Hours must fall on slot boundaries (e.g. 8:00 or 8:30), and a counter that is serving a farmer cannot be removed.
- **Rates.** A new rate is used for weighments from now on. Every weighment stores the rate it used, so old amounts never change.

### Demo data (for presentations)

On the super admin's dashboard, **Load demo day** adds:
- 40 demo farmers (most speaking Tamil, some Hindi and English),
- a busy day in progress at each centre: farmers already weighed and paid, some at the counters, a waiting line and some still to come,
- the last 6 days of history, so the charts have something to show,
- made-up bank or UPI details for every demo farmer (encrypted like real ones), so the payments page shows where each is paid.

**Remove demo data** deletes all of it again. Real farmers, their bookings and payments are never touched.

How it works (for the viva):
- Demo farmers have email addresses ending in `@demo.invalid`. `.invalid` is a domain that can never exist, so demo farmers never get a real email (notifications are in-app only) and cannot log in.
- The day is built around the current time, so whenever you load it the queue looks live: counter 3 at Thanjavur is left free, so you can press **Call next** straight away.
- The data is written with plain SQL (so past check-in, weighment and payment times can be set), using the same safe "take a place" statement as real bookings and the same token counter, so real bookings made later that day get the next token number.

### Keeping the pages smooth

- **Live pages pause in hidden tabs.** A browser keeps at most 6 connections open to one server, and every live page (operator desk, TV, farmer's booking) holds one. `js/live.js` closes the connection while a tab is hidden and reopens it when the tab is shown; the server then sends the current queue at once.
- **Cheap animations.** Only `transform` and `opacity` are animated (the pulsing "live" dot, the queue number, the "go to counter" alert), which the graphics card handles without redrawing the page. The header is solid (no blur effect).
- **Small files.** Fonts are cut down to the characters used, icons are one sprite file, photos are small WebP files, and the server compresses HTML, CSS, JS and JSON.
- **Load in parallel.** Farmer pages ask for their data at the same time as the language file, the admin charts library loads in the background (numbers first, charts a moment later), and the operator desk only downloads the camera QR scanner when "Scan QR" is pressed.

### Look and feel

The design rules:
- **One accent colour (green).** Amber, red and blue only mean a status and always come with a label and an icon.
- **Light and dark theme.** The button next to the language switch changes it; until someone chooses, the device setting is used (`js/theme.js`). All colours are CSS variables at the top of `css/app.css`.
- **Fonts.** Anek (one family for English and Tamil) and Geist Mono for tokens and the OTP, self-hosted in `static/fonts/` and cut down to the characters used (about 110 KB together). Hindi uses the device's own font, because the matching Hindi font alone is over 200 KB.
- **Icons.** Phosphor icons (MIT) in one file, `img/icons.svg`.
- **Photos.** Four photos from Unsplash (free licence) as small WebP files in `img/photos/`: Nandhu Kumar (hero and login), Helena Pfisterer (grain), Declan Sun (paddy).
- **The token is a ticket.** The token card has notches and a tear line like a paper token.
- **Loading and asking.** Grey skeleton shapes show while data loads, and a styled dialog replaces the browser's confirm box (`js/ui.js`).
- **Motion.** Only `transform` and `opacity` are animated, and everything stops when the device asks for reduced motion.

### Testing the queue outside centre hours

Centres close at 16:00 or 17:00, so in the evening there are no slots left for today. To test at night, temporarily keep Thanjavur open late (run in pgAdmin), then restart the app so it creates the extra slots:

```sql
UPDATE centres SET close_time = '23:30' WHERE code = 'TNJ';
```

When you have finished testing, put it back and remove the extra empty slots:

```sql
UPDATE centres SET close_time = '17:00' WHERE code = 'TNJ';
DELETE FROM slots WHERE centre_id = (SELECT id FROM centres WHERE code = 'TNJ') AND start_time >= '17:00' AND booked = 0;
```

### Configuration

Local defaults are in `src/main/resources/application.yml`. Override them in the git-ignored `.env` file in the project folder (`example.env` lists every key), or as environment variables:

| Variable | Default | Purpose |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/farmer_procurement` | Database URL |
| `DB_USERNAME` / `DB_PASSWORD` | `postgres` / `postgres` | Database login |
| `PORT` | `8080` | HTTP port |
| `JWT_SECRET` | a local development value | Signs login tokens (and QR picture links). **Set a random 32+ character value in production** |
| `PAYOUT_ENCRYPTION_KEY` | a local development value | Encrypts farmers' account numbers and UPI IDs. **Random 32+ characters in production; never change it later** |
| `SUPERADMIN_USERNAME` / `SUPERADMIN_PASSWORD` | – | The super admin account, created at start-up if there is none |
| `RAZORPAY_KEY_ID` / `RAZORPAY_KEY_SECRET` | – | Razorpay **test** keys (`rzp_test_...`); live keys are refused |
| `EMAIL_PROVIDER` | `console` | `console` prints emails in the log; `smtp` sends them with Gmail; `brevo` uses the Brevo API |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | – | Gmail address and App Password (for `smtp`) |
| `EMAIL_DAILY_LIMIT` | `250` | Notification emails per day before switching to in-app only |
| `BREVO_API_KEY` / `MAIL_FROM` | – | Only for `brevo` |
| `APP_URL` | `http://localhost:8080` | Website address used for links inside emails |

Secrets (JWT secret, Razorpay keys, Gmail App Password) live in the git-ignored `.env` file on the PC, or in `/opt/sfps/.env` on the server. They are never committed.

## Deploying

The live site runs on one free-tier **AWS EC2** server (Mumbai), without Docker: Java 21 runs the app as a systemd service, PostgreSQL is on the same server, and Nginx serves it over HTTPS (free Let's Encrypt certificate on an `sslip.io` name). Emails go through Gmail SMTP, which AWS allows. `deploy/setup-ec2.sh` installs everything in one go; `deploy/README.md` has the steps and the smoke test, and `deploy/update.sh` puts new code live.

Email providers (`EMAIL_PROVIDER`): `console` (print in the log), `smtp` (Gmail) and `brevo` (Brevo HTTPS API, for hosts that block SMTP). Brevo cannot carry inline pictures, so with it the confirmation email shows the QR from a signed link (`/api/qr/{id}.png?sig=...`, `QrLinkSigner`).

## Project structure

```
src/main/java/com/cropcoders/procurement/
├── ProcurementApplication.java   entry point (forces Asia/Kolkata time zone)
├── config/                       AppProperties (the "app:" settings), SuperAdminSeeder
├── security/                     SecurityConfig (who can call what), JWT service + filter, AccessScope (centre limits),
│                                 EncryptedText (bank details), QrLinkSigner
├── controller/                   REST endpoints
├── service/                      business logic (login, booking, queue, weighment, payment, notifications,
│                                 admin dashboard and settings, demo data, ...)
├── repository/                   Spring Data JPA repositories + DashboardRepository (SQL for the dashboard)
├── entity/                       JPA entities (tables)
├── enums/                        Role, BookingStatus, NotificationEvent, EmailStatus, ...
├── dto/                          request/response objects
├── scheduler/                    timed jobs (slot generation, reminders + no-shows)
├── realtime/                     live queue updates (Server-Sent Events)
├── payment/                      RazorpayGateway (orders API + signature check)
├── email/                        EmailSender (console / Gmail SMTP / Brevo), EmailTemplates, EmailDispatcher (background
│                                 sending), QrCode (QR picture with ZXing)
└── exception/                    ApiException + GlobalExceptionHandler (JSON errors)
src/main/resources/
├── application.yml               configuration
├── messages*.properties          email texts in English, Tamil, Hindi
├── db/migration/                 Flyway: V1 schema, V2 centres + crops, V3 staff accounts, V4 email instead of SMS,
│                                 V5 roles and staff, V6 farmer payment details, V7 encrypted payment details
└── static/                       HTML pages, css/, js/, i18n/ (en, ta, hi), fonts/, img/ (icons.svg, photos/)
src/test/java/                    unit tests + EndToEndFlowTest (see "Tests")
deploy/                           AWS EC2 guide (README.md), setup and update scripts, systemd service, Nginx site
```

### How login works (for the viva)

1. **Farmer OTP.** The server makes a random 6-digit code, stores only its BCrypt hash, and emails it in the farmer's language. Email addresses are stored in lower case, so `Ravi@Gmail.com` and `ravi@gmail.com` are the same farmer.
2. **Verify.** The code is checked against the hash. There are 3 attempts, and each wrong try is saved even though the request fails (`noRollbackFor`).
3. **New farmer.** The server returns a 15-minute *registration token* (a JWT marked `purpose=register`). This token can only be used to submit the registration form.
4. **Login token.** A JWT signed with `JWT_SECRET` that holds the user id, role and centre. The browser keeps a farmer's token in `localStorage` (all tabs, 7 days) and a staff token in `sessionStorage` (this tab only, 12 hours), and sends it as `Authorization: Bearer …`.
5. **Access rules.** `JwtAuthFilter` reads the token on every request. `SecurityConfig` allows `/api/farmers/**` only for FARMER, `/api/operator/**` only for OPERATOR (staff), and `/api/admin/**` for ADMIN and SUPER_ADMIN. Inside the admin APIs, `AccessScope` limits a centre admin to their own centre and keeps centres, crop rates and demo data for the super admin.
6. **Staff passwords** are stored as BCrypt hashes. The super admin's password comes from `.env` at start-up, so it is never in the code or a migration.

## Tests

Run them in IntelliJ (right-click `src/test/java` → Run 'All Tests') or with Maven (`mvn test`).

| Test | What it checks |
|---|---|
| `EndToEndFlowTest` | The whole app, step by step, on its own throw-away database (`farmer_procurement_e2e`, dropped afterwards; skipped without a local PostgreSQL): super admin, centre admin and staff with their access limits, farmer OTP login, registration with bank details (encrypted), UPI change, booking with the QR email, check-in, call next, weighment, Razorpay order and signature check, paid status, demo day |
| `JwtServiceTest` | Login and registration tokens, expiry, tampering |
| `EtaCalculatorTest` | Waiting-time estimates in the queue |
| `RazorpayGatewayTest` | Payment signature check, test keys only |
| `EmailTemplatesTest` | Emails in three languages, HTML escaping |
| `QrCodeTest` | The email's QR picture reads back as the booking's QR text |
| `EncryptedTextTest` | Bank details are unreadable in the database and come back the same |
| `MoneyFormatTest` | Amounts in emails use Indian grouping (₹6,00,000.00) |

Deployment steps: [deploy/README.md](deploy/README.md).
