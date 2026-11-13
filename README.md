# Smart Farmer Procurement System

Farmers often wait all day at procurement centres without knowing the schedule or what happened to their crop and payment. This app fixes that:

- **Slot booking.** Farmers register with their email address (a one-time password is emailed to them), say how they want to be paid (bank account or UPI, stored encrypted) and book a time slot at a procurement centre. The confirmation email carries the token's QR code.
- **Live queue.** Counter staff check farmers in and call them to counters. Farmers see their position and waiting time live, and a TV screen at the centre shows who is being served.
- **Email alerts.** Farmers get an email at key moments (booked, reminder, turn near, weighed, paid), in their chosen language (English, Tamil or Hindi). Every update also appears on their dashboard.
- **Tracking.** Every booking has a timeline, from booked through weighed to paid, with payment through Razorpay (test mode).
- **Admin dashboard.** Today's farmers, queue lengths, average wait, crop procured and payments, with charts for the last 7 days, plus how many farmers have registered and how many brought their crop to a centre.
- **Visit reviews.** After the crop is weighed (accepted or rejected), the farmer rates the centre and the app with 1 to 5 stars, taps what should improve (waiting time, staff, water and shade, weighing, payment; booking, live queue, emails, language, OTP login) and can add a comment. The admins see the average stars, the most chosen improvements and the newest reviews.
- **Three staff levels.** A super admin adds centres and centre admins and sets crop rates; each centre admin sees only their own centre and adds its counter staff; staff work the operator desk.

**Live site:** https://smart-farmer-procurement-cysn.onrender.com  
(Render's free plan: after 15 minutes without visitors the server sleeps, so the first page then takes about a minute to open.)

Staff logins: [staff-accounts.xlsx](staff-accounts.xlsx) lists every admin and staff account, its starting password and its login page (`/staff/login.html`). Change a starting password after the first login.

Theme: Smart Automation · College mini project

## Tech stack

| Layer | Technology |
|---|---|
| Frontend | HTML, CSS, JavaScript (served by Spring Boot), light and dark theme, English / Tamil / Hindi |
| Backend | Java 21, Spring Boot 4.1, Maven, Spring Data JPA, Flyway |
| Database | PostgreSQL |
| Payments | Razorpay (test mode) |
| QR codes | qrcodejs in the browser, ZXing on the server (email) |
| Email | Gmail: SMTP on the PC, a Google Apps Script web app on Render (its free plan blocks SMTP); optional Brevo email API |
| Hosting | Render free plan (Docker) + Neon PostgreSQL, live at https://smart-farmer-procurement-cysn.onrender.com; AWS EC2 is described as an alternative |

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

A new email address goes to the registration form: name, mobile number (for the centre to call), address, district, last 4 digits of Aadhaar (stored encrypted), and language. That language is then used for the farmer's pages and emails.

**Staff, centre admins and the super admin** all log in on one page, `/staff/login.html`, with a username and password. The server finds the role and the browser opens the right screen.

| Role | Belongs to | Can do | Created by |
|---|---|---|---|
| Super admin | All centres | Adds centres and centre admins, sets crop rates, loads demo data, sees every centre | The app at start-up, from `SUPERADMIN_USERNAME` and `SUPERADMIN_PASSWORD` in `.env` |
| Centre admin | One centre | Dashboard, payments and settings of that centre; adds its staff | Super admin, under "Admins & staff" |
| Staff | One centre | Operator desk: check-in, call next, weighment | Centre admin (or super admin) |

Each browser tab keeps its own staff login (in `sessionStorage`), so an admin and a staff member can be logged in side by side in two tabs. Farmer logins are shared by all tabs and last 7 days (`localStorage`).

`V5__roles_and_staff.sql` creates one admin (`admin.tnj`, `admin.tvr`, `admin.ngt`) and one staff account (`staff.tnj`, `staff.tvr`, `staff.ngt`) per centre with random starting passwords. The passwords are in `staff-accounts.xlsx` in the project folder, which is git-ignored. Accounts are switched off rather than deleted, because weighments and payments point to them.

**Payment details.** At registration the farmer chooses how to be paid: a **bank account** (account holder, account number typed twice, IFSC code, bank name) or a **UPI ID**. The form and the server check the same rules (account number 9 to 18 digits, IFSC like `SBIN0001234`, UPI like `name@okaxis`). The browser only ever gets the last 4 digits of the account number back. The account number, the UPI ID and the last 4 digits of Aadhaar are **encrypted in the database** (AES-256-GCM, `EncryptedText`) with the key `PAYOUT_ENCRYPTION_KEY` from `.env`, so a copy of the database alone does not reveal them. Farmers can change the details on their dashboard, and the admin's payments page shows where each farmer wants to be paid.

OTP rules:
- valid for 5 minutes, 3 wrong tries
- a new OTP only after 60 seconds, at most 5 per hour per email address
- at most 200 a day for the whole site, all addresses together (`OTP_DAILY_LIMIT`), so a script trying made-up addresses cannot use up the day's Gmail quota. After that the server answers 429 `OTP_DAILY_LIMIT` until midnight.
- on the live server, Nginx also counts login requests per IP address: sending an OTP 6 a minute (plus a burst of 12), checking an OTP or a staff password 20 a minute together (plus a burst of 20). Over that, Nginx itself answers 429 `RATE_LIMITED` (see `deploy/README.md`, section 9).

### Booking a slot

After logging in, a farmer clicks **Book a slot** and goes through 4 steps:
1. **Centre.** Centres in the farmer's own district are listed first.
2. **Day.** Today plus the next 6 days. A bar shows how busy each day is.
3. **Time.** Green, amber or red shows how crowded each slot is, and the least crowded time is marked *Suggested*.
4. **Crop and quantity.** An approximate amount is shown (quantity × rate).

The farmer then gets a **token** (e.g. `TNJ-001`) and a **QR code** to show at the centre. They can cancel until the slot starts, and the place goes back to the slot.

How it works (for the viva):
- **Slot generation.** `SlotGenerationJob` creates slots at startup and every morning at 7:00 for the next 7 days. The insert uses `ON CONFLICT DO NOTHING`, so running it twice doesn't create duplicates. Places per slot = counters × slot minutes ÷ average service minutes.
- **No overbooking.** A place is taken with a single SQL statement: `UPDATE slots SET booked = booked + 1 WHERE id = ? AND booked < capacity`. If it changes 0 rows, the slot is full. In a test (`EndToEndFlowTest`), 6 farmers booked the last 5 places at the same moment: exactly 5 succeeded and 1 was told the slot is full.
- **Cancel only once.** Cancelling locks the booking row first (`SELECT … FOR UPDATE`) and loads it after. A second cancel at the same moment (double click, two tabs) or a check-in at the counter waits, then sees the new status, so the place goes back to the slot only once. In a test, two cancels at the same moment: one succeeded and the other was told the booking can no longer be cancelled.
- **One booking per day.** A partial unique index (`uq_active_booking`) stops a farmer from holding two active bookings on the same day.
- **Tokens.** `token_counters` is updated with `INSERT … ON CONFLICT DO UPDATE … RETURNING`, so two bookings can never get the same number.
- **Status history.** Every change is saved in `booking_events` and shown to the farmer as a timeline.

### Live queue

| Screen | URL | Who |
|---|---|---|
| Operator desk | `/operator/desk.html` | Counter staff: check in by token or QR code (typed, USB scanner or camera), **Call next** per counter, **Call again**, **Not present** |
| TV display | `/display.html?centre=1` | Centre TV: one large row per counter with the token it is serving, the next tokens in order with their waiting time (as many as fit, then "+ N more"), the day's figures, labels in English with Tamil and Hindi below, optional chime and spoken announcement, dark by default with a light/dark button (or open `/display.html?centre=1&theme=light`). The sound and theme buttons show when the mouse moves. |
| Farmer phone | `/farmer/booking.html?id=…` | Farmer: live position and waiting time, then "Please go to Counter 2 now" (the phone vibrates) |

How it works (for the viva):
- **Check-in** sets `queue_priority`. A farmer who arrives on time or early is placed by their slot start time. A farmer who arrives after slot end + 15 minutes is placed by their arrival time, which puts them at the back of the line.
- **Call next** picks the first farmer with `SELECT … ORDER BY queue_priority, checked_in_at LIMIT 1 FOR UPDATE SKIP LOCKED`, so two counters pressing the button at the same moment always get different farmers.
- **One farmer per counter.** A partial unique index (`uq_counter_serving`, on centre, day and counter where the status is `CALLED`) stops two clicks for the *same* counter (two tabs, two staff) from both calling a farmer. The second one is told the counter is busy.
- **One change at a time per farmer.** Check-in (by token or QR code), **Call again**, **Not present** and the weighment lock the booking row first (`SELECT id … FOR UPDATE`, in `BookingRepository`) and load the booking after. Two actions on the same farmer at the same moment, such as two check-ins, skip and weighment, or a check-in while the farmer cancels, run one after the other, and the second one sees what the first saved. The order matters: a booking already loaded in the transaction would not be read again, so the lock would not help. The no-show job locks with `FOR UPDATE SKIP LOCKED`, so it leaves out a booking the desk is changing right now and looks at it again on its next run. The desk itself sends one check-in at a time.
- **Only changed columns are saved.** `Booking` has `@DynamicUpdate`, so saving a booking writes only the columns that changed. Without it every column is written back, and changing one field could undo a status another request saved in the meantime (e.g. turn a just-cancelled booking back into *Booked*).
- **Each email only once.** The "reminder sent" and "turn is near sent" flags are set with a conditional update, for example `UPDATE bookings SET reminder_sent = true WHERE id = ? AND reminder_sent = false AND status = 'BOOKED'`. The email goes out only when that changed exactly 1 row. So two counters, or two runs of the reminder job, never send the same email twice, and a farmer who cancelled a moment ago gets no reminder. Only that one column is written, so other changes are kept.
- **ETA** is calculated by `EtaCalculator` and unit tested. The average service time is the real average of the last farmers served today, or the centre's setting until at least 3 have been served. The wait for position *p* is `ceil(max(0, p − free counters) / counters) × average service time`.
- **Live updates** use Server-Sent Events. Each screen keeps one connection open (`/api/queue/{centreId}/stream`), and after every change is committed, `QueueSseHub` pushes the new queue to all screens of that centre. After a dropped connection the browser reconnects by itself. But when the server answers with an error instead (e.g. 502 from Nginx while the app restarts), the browser gives up for good, so `js/live.js` opens a new connection itself: after 2 seconds, then 4, 8, 16, and at most every 30 seconds until it works. The operator desk then reloads its lists, because changes made during the outage were never streamed to it.
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
5. **One payment at a time.** Creating the order and verifying it both lock the payment row first (`SELECT … FOR UPDATE`). A double click or two admins paying the same farmer wait for each other, so a payment never gets two Razorpay orders and is never marked paid (or emailed about) twice.
6. **If the result never arrives.** The connection can drop (or the tab be closed) right after paying, so step 4 never reaches our server and the payment still shows *Pending*. So:
   - The browser saves Checkout's result in `localStorage` before sending it, and sends it again the next time the payments page opens. Once the server has given its final answer, the saved result is removed.
   - When the admin clicks **Pay** again, the order already exists, so the server first asks Razorpay with the secret key, server to server (`GET /orders/{id}/payments`, `RazorpayGateway.findSuccessfulPayment`), whether it was already paid. If it was, the server marks the payment Paid with that `pay_…` reference and answers `alreadyPaid`. The page then shows a notice with the reference instead of opening Checkout, so the farmer is never paid twice. If Razorpay cannot be asked, Checkout simply opens as usual.
   - Calls to Razorpay have a time limit (5 seconds to connect, 10 seconds for the answer), because they run while the payment row is locked and the database pool has only 5 connections.
7. **Store amounts safely.** Amounts are stored in **paise** as whole numbers, so there are no rounding errors.
8. **Real life.** Money would flow from the government to the farmer (DBT / RazorpayX Payouts). In the demo, the admin paying through Checkout stands in for that step.

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
| Crop accepted / not accepted | Staff save the weighment (weight and amount, or the reason), with a request to review the visit |
| Payment done | Admin's Razorpay payment is verified |
| Missed slot | Not checked in by slot end + 2 hours (booking becomes *No show*) |

"Checked in" and "Go to counter N" are shown in the app only, because the farmer is already at the centre. All of these appear under **Notifications** on the farmer dashboard, with a ✉ mark when an email was sent.

How it works (for the viva):
- **Texts in 3 languages.** Email texts live in `messages.properties` (English), `messages_ta.properties` and `messages_hi.properties`, and are picked by the farmer's saved language. Every text is HTML-escaped before it goes into the email.
- **Sent after commit, in the background.** A notification is saved in the same transaction as the booking change. The email is sent by `EmailDispatcher` only **after** that transaction commits (`@TransactionalEventListener(AFTER_COMMIT)`), on a separate thread (`@Async`), so the operator's click is not slowed down by Gmail and no email goes out for a change that failed. The row is then marked `SENT` (with Gmail's message id) or `FAILED`.
- **Free quota.** At most `EMAIL_DAILY_LIMIT` (default 250) notification emails per day (Gmail allows 500, Brevo 300). After that, notifications are shown in the app only (`SKIPPED`). OTP emails are not counted here, so farmers can still log in when the notification quota is used up. They have their own limit for the whole site, `OTP_DAILY_LIMIT` (default 200), so together they stay under Gmail's 500 a day.
- **Scheduled jobs.** `AutomationJob` runs every 15 minutes from 7 am to 10 pm and also at startup (so nothing is missed after a restart). Nothing runs at night, so the free database can sleep too.
- **Pluggable sender.** `EmailSender` is an interface; `ConsoleEmailSender` and `SmtpEmailSender` are chosen by `EMAIL_PROVIDER`. `BrevoEmailSender` (HTTPS API) is there for hosts that block SMTP ports.
- **Fast OTP.** The OTP email is also sent in the background, so "Send OTP" answers in about 0.2 seconds and the farmer can type the code as soon as it arrives.
- **One kept-open Gmail connection.** Connecting to Gmail (TCP + TLS + login) takes about 2-3 seconds from here, so `SmtpEmailSender` opens the connection once at startup and reuses it for every email, like a connection pool with one connection. An email then leaves in about 2 seconds instead of 5-8. If Gmail has closed the connection, the sender reconnects and tries once more.

**QR code in the confirmation email.** The booking confirmation email carries the same QR code as the booking page, so the farmer can show it at the desk without opening the app. `QrCode` draws it with ZXing and it is sent inside the email (an inline picture), so mail apps that block web pictures still show it.

**Big amounts in test mode.** Razorpay test accounts refuse large payments (UPI allows at most ₹1,00,000), but a real procurement is often several lakh rupees. So when the amount is above ₹50,000 (`app.razorpay.test-charge-limit-paise`), Razorpay is asked for a ₹50,000 stand-in charge and the farmer's full amount is recorded as paid. The payments page says so before Checkout opens.

### Admin pages

| Page | What the admin does there (a centre admin sees only their own centre) |
|---|---|
| **Dashboard** (`/admin/dashboard.html`) | Today at a glance: farmers booked, waiting, served, average wait, crop procured, payments pending. A live table per centre (refreshes every 30 seconds) with a link to each TV screen. Charts of farmers and quintals per day for the last 7 days, and the crops bought this week. Farmers registered (site-wide, and new this week) and farmers who brought their crop (all time), and the farmers' reviews: average stars for the centre and the app, how often each improvement was chosen, the 10 newest reviews, and each centre's average stars in the centre table. |
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
- **Live pages come back by themselves.** If the server could not be reached (e.g. during an update), `js/live.js` reopens the connection with growing waits (2, 4, 8, 16, then every 30 seconds), so a TV at the centre does not need a manual reload after a restart. It never holds more than one connection, and it stops trying while the tab is hidden.
- **Cheap animations.** Only `transform` and `opacity` are animated (the pulsing "live" dot, the queue number, the "go to counter" alert), which the graphics card handles without redrawing the page. The header is solid (no blur effect).
- **Small files.** Fonts are cut down to the characters used, icons are one sprite file, photos are small WebP files, and the server compresses HTML, CSS, JS and JSON.
- **Load in parallel.** Farmer pages ask for their data at the same time as the language file, the admin charts library loads in the background (numbers first, charts a moment later), and the operator desk only downloads the camera QR scanner when "Scan QR" is pressed.

### Look and feel

The design rules:
- **One accent colour (green).** Amber, red and blue only mean a status and always come with a label and an icon.
- **Light and dark theme.** The button next to the language switch changes it; until someone chooses, the device setting is used (`js/theme.js`). All colours are CSS variables at the top of `css/app.css`. The TV display is dark until someone chooses light (button in its footer, or `?theme=light` in the address for a TV without a mouse), and it remembers its choice separately, so a laptop that uses the light theme for the admin pages still shows the TV board in dark.
- **Fonts.** Anek (one family for English and Tamil) and Geist Mono for tokens and the OTP, self-hosted in `static/fonts/` and cut down to the characters used (about 110 KB together). Hindi uses the device's own font, because the matching Hindi font alone is over 200 KB.
- **Icons.** Phosphor icons (MIT) in one file, `img/icons.svg`.
- **Photos.** Four photos from Unsplash (free licence) as small WebP files in `img/photos/`: vivek vk (home page, mirrored), Foto Murthy (paddy seedlings), Heber Davis (tractor, farmer login), Declan Sun (paddy, staff login).
- **The token is a ticket.** The token card has notches and a tear line like a paper token.
- **Loading and asking.** Grey skeleton shapes show while data loads, and a styled dialog replaces the browser's confirm box (`js/ui.js`).
- **Motion.** Only `transform` and `opacity` are animated, and everything stops when the device asks for reduced motion.

### Page scripts and browser security

- **One script file per page.** Each page's own code is in `js/pages/` (e.g. `admin-payments.js` for `/admin/payments.html`), next to the shared files in `js/` (`api.js`, `auth.js`, `live.js`, `ui.js`, ...). The HTML pages have no inline `<script>` blocks, no `onclick="..."` attributes and no `javascript:` links, because the security header below does not allow them.
- **Content-Security-Policy.** `SecurityConfig` sends this header with every response. It tells the browser to run scripts only from our own server, cdnjs, jsDelivr and Razorpay, to talk only to our API and Razorpay, to load no plugins, and to let no other site show our pages inside a frame (clickjacking). So even if someone slipped a script into a page (XSS), the browser would not run it. Styles allow `'unsafe-inline'`, because our scripts set `style="..."` on elements. `EndToEndFlowTest` checks the exact header.
- **Subresource Integrity.** The libraries from CDNs (Chart.js and qrcodejs from cdnjs, the html5-qrcode camera scanner from jsDelivr) are loaded with an `integrity="sha384-…"` hash of that exact version. If a CDN ever served a different file, the browser would refuse to run it. Razorpay's `checkout.js` has no hash, because Razorpay only offers one unversioned URL and updates the file.
- **Escaping.** Text from the server is escaped before it goes into HTML (`esc()` in `admin.js`, `farmer.js` and the page scripts), quotes included, so a name typed by someone cannot close an attribute and add its own.
- **Ended logins.** When a login runs out while a page is open, `api.js` goes to the login page before the next request instead of sending it. Only a 401 with code `UNAUTHORIZED` (the server refused our token) logs the browser out; a wrong password (`BAD_CREDENTIALS`) or an expired registration (`REGISTRATION_EXPIRED`) does not.

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

Local defaults are in `src/main/resources/application.yml`. Override them in the git-ignored `.env` file in the project folder (`example.env` is a template with the main keys), or as environment variables:

| Variable | Default | Purpose |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/farmer_procurement` | Database URL |
| `DB_USERNAME` / `DB_PASSWORD` | `postgres` / `postgres` | Database login |
| `PORT` | `8080` | HTTP port |
| `JWT_SECRET` | a local development value (`local-dev-...`) | Signs login tokens (and QR picture links). **Required on a server:** when `APP_URL` is not `http://localhost` or `http://127.0.0.1`, the app refuses to start with the development value (`SecretGuard`). Use 32+ random characters |
| `PAYOUT_ENCRYPTION_KEY` | a local development value (`local-dev-...`) | Encrypts farmers' account numbers and UPI IDs. **Required on a server**, the same way as `JWT_SECRET`. 32+ random characters; **never change it later** |
| `SUPERADMIN_USERNAME` / `SUPERADMIN_PASSWORD` | – | The super admin account, created at start-up if there is none |
| `RAZORPAY_KEY_ID` / `RAZORPAY_KEY_SECRET` | – | Razorpay **test** keys (`rzp_test_...`); live keys are refused |
| `EMAIL_PROVIDER` | `console` | `console` prints emails in the log; `smtp` sends them with Gmail; `brevo` uses the Brevo API |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | – | Gmail address and App Password (for `smtp`) |
| `EMAIL_DAILY_LIMIT` | `250` | Notification emails per day before switching to in-app only (OTP emails are not counted) |
| `OTP_DAILY_LIMIT` | `200` | OTP emails per day for the whole site, all addresses together (`app.otp.max-per-day`) |
| `BREVO_API_KEY` / `MAIL_FROM` | – | Only for `brevo` |
| `APP_URL` | `http://localhost:8080` | Website address used for links inside emails. Anything other than `http://localhost` or `http://127.0.0.1` counts as a server, so `JWT_SECRET` and `PAYOUT_ENCRYPTION_KEY` must then be set |

Secrets (JWT secret, encryption key, Razorpay keys, Gmail App Password) live in the git-ignored `.env` file on the PC, or in `/opt/sfps/.env` on the server. They are never committed. The built-in development values are in the public code (anyone could sign their own login token with them, even as super admin), which is why the app refuses them on a server.

## Deploying

**Render + Neon (free, the current setup).** The app runs on Render from the `Dockerfile` (Java 21, sized for the 512 MB free container), the database is a free Neon PostgreSQL, and emails go from a Gmail account through a Google Apps Script, because Render's free plan blocks the SMTP ports. `render.yaml` is the Render Blueprint: Render generates `JWT_SECRET`, `PAYOUT_ENCRYPTION_KEY` and `GOOGLE_SCRIPT_SECRET`, and asks once for `DATABASE_URL`, `SUPERADMIN_USERNAME`/`SUPERADMIN_PASSWORD`, the Razorpay test keys and `GOOGLE_SCRIPT_URL`. Every push to `main` deploys again.
- **Database.** Neon gives one `DATABASE_URL` (`postgresql://...?sslmode=require`); `DatabaseUrl` turns it into the JDBC settings at start-up and upgrades `require` to `verify-full`, so the database's certificate is checked against Java's trusted authorities, not only encrypted. Use Neon's address with connection pooling **off** (Flyway needs a direct connection).
- **Email.** `deploy/google-apps-script/Code.gs` is the script (deploy it as a web app, *Execute as: Me*, *Who has access: Anyone*). It sends nothing unless the request carries the secret that is in its `SECRET` script property, and the booking QR travels as an inline picture. Gmail allows about 100 such emails a day, so the Blueprint sets `EMAIL_DAILY_LIMIT=50` and `OTP_DAILY_LIMIT=40`.
- **Login rate limits.** There is no Nginx on Render, so `RateLimitFilter` applies the same per-IP limits in the app (OTP emails 6 a minute with a burst of 12; OTP checks and staff logins 20 a minute with a burst of 20; 429 `RATE_LIMITED`). Requests from the machine itself (local development, tests) are not limited.
- **The visitor's real IP.** `GET /api/ping` (also the health check) shows `yourIp`: after a deploy it must be your own public address, not Render's. Otherwise the per-IP limits and the staff login lock would count all visitors together.
- **Free plan.** The app sleeps after 15 minutes without visitors and takes about a minute to wake up; slots, reminders and missed bookings are also handled at start-up, so nothing is lost while it sleeps. Neon keeps only 6 hours of history, so take a `pg_dump` now and then.
- `PAYOUT_ENCRYPTION_KEY` must never change: if the Render service is ever created again, copy the old value into the new one, or the saved bank details cannot be read.

**AWS EC2 (alternative).** The app can also run on one free-tier **AWS EC2** server (Mumbai), without Docker: Java 21 runs the app as a systemd service, PostgreSQL is on the same server, and Nginx serves it over HTTPS (free Let's Encrypt certificate on an `sslip.io` name). Emails go through Gmail SMTP, which AWS allows. `deploy/setup-ec2.sh` installs everything in one go; `deploy/README.md` has the steps and the smoke test, and `deploy/update.sh` puts new code live.

How it works (for the viva):
- **Secrets.** The setup script generates `JWT_SECRET` and `PAYOUT_ENCRYPTION_KEY` into `/opt/sfps/.env`. Without them the app does not start on the server (see *Configuration*).
- **Nightly backups.** A cron job (`/etc/cron.d/sfps-backup`) saves a `pg_dump` copy of the database every night at 3 a.m. India time in `/var/backups/sfps`, and the last 7 nightly copies are kept. Only the `postgres` user can open the folder, because the copies hold farmers' details. `deploy/README.md` (section 8) shows how to put a copy back.
- **Safe updates.** `update.sh` builds the new code (if the build fails, the old version keeps running), saves a copy of the database before the new Flyway migrations run, keeps the running version as `app.jar.prev` and restarts with the new one. It then asks `/actuator/health` every 5 seconds, for up to 3 minutes, until it says `UP`. If it never does, the script puts the previous version back and restarts it. That is safe because the migrations leave the database usable for the previous version (for example, V9 keeps the old login columns).
- **Login rate limits.** Nginx counts login requests per IP address (the numbers are under *Logging in*) and answers 429 `RATE_LIMITED` above them. The limits are generous on purpose, because many mobile users in India share one public IP address; the finer limits are in the app.
- **The visitor's real IP.** Every Nginx location that passes requests to the app sends `X-Forwarded-For`, `X-Real-IP`, `X-Forwarded-Proto` and `Host`. With `server.forward-headers-strategy: native`, Tomcat takes the visitor's address from that header, but only from a proxy on the same machine or a private network. Without it every visitor would look like `127.0.0.1`, and the staff login lock would lock an account for everybody.

Email providers (`EMAIL_PROVIDER`): `console` (print in the log), `smtp` (Gmail), `google-script` (Gmail through a Google Apps Script, used on Render) and `brevo` (Brevo HTTPS API, for hosts that block SMTP). Brevo cannot carry inline pictures, so with it the confirmation email shows the QR from a signed link (`/api/qr/{id}.png?sig=...`, `QrLinkSigner`).

## Project structure

```
src/main/java/com/cropcoders/procurement/
├── ProcurementApplication.java   entry point (forces Asia/Kolkata time zone)
├── config/                       AppProperties (the "app:" settings), SuperAdminSeeder
├── security/                     SecurityConfig (who can call what, Content-Security-Policy), JWT service + filter,
│                                 AccessScope (centre limits), EncryptedText (bank details), QrLinkSigner,
│                                 SecretGuard (no development secrets on a server)
├── controller/                   REST endpoints
├── service/                      business logic (login, LoginAttemptService (wrong staff passwords per IP),
│                                 booking, queue, weighment, payment, notifications, admin dashboard and
│                                 settings, demo data, ...)
├── repository/                   Spring Data JPA repositories + DashboardRepository (SQL for the dashboard)
├── entity/                       JPA entities (tables)
├── enums/                        Role, BookingStatus, NotificationEvent, EmailStatus, ...
├── dto/                          request/response objects
├── scheduler/                    timed jobs (slot generation, reminders + no-shows, forgetting old wrong passwords)
├── realtime/                     live queue updates (Server-Sent Events)
├── payment/                      RazorpayGateway (orders API, signature check, earlier payments of an order)
├── email/                        EmailSender (console / Gmail SMTP / Brevo), EmailTemplates, EmailDispatcher (background
│                                 sending), QrCode (QR picture with ZXing)
└── exception/                    ApiException + GlobalExceptionHandler (JSON errors)
src/main/resources/
├── application.yml               configuration
├── messages*.properties          email texts in English, Tamil, Hindi
├── db/migration/                 Flyway: V1 schema, V2 centres + crops, V3 staff accounts, V4 email instead of SMS,
│                                 V5 roles and staff, V6 farmer payment details, V7 encrypted payment details, V10 encrypted Aadhaar digits,
│                                 V8 one farmer per counter + staff login lock,
│                                 V9 login_attempts (staff login lock per username and IP) + index for the daily OTP limit,
│                                 V11 visit_feedback (one review per weighed booking: stars, improvement codes, comments)
└── static/                       HTML pages, css/, js/ (shared scripts), js/pages/ (one script per page),
                                  i18n/ (en, ta, hi), fonts/, img/ (icons.svg, photos/)
src/test/java/                    unit tests + EndToEndFlowTest (see "Tests")
deploy/                           AWS EC2 guide (README.md), setup script (with nightly backups), update script
                                  (health check + rollback), systemd service, Nginx site (with login rate limits)
```

### How login works (for the viva)

1. **Farmer OTP.** The server makes a random 6-digit code, stores only its BCrypt hash, and emails it in the farmer's language. Email addresses are stored in lower case, so `Ravi@Gmail.com` and `ravi@gmail.com` are the same farmer.
2. **Verify.** The code is checked against the hash. There are 3 attempts, and each wrong try is saved even though the request fails (`noRollbackFor`). The newest OTP row is locked while it is checked (`SELECT … FOR UPDATE`), so guesses sent at the same moment are checked one after the other and every wrong one is counted. In a test, 6 wrong guesses at the same moment used up exactly the 3 tries, and after that even the right code was refused.
3. **New farmer.** The server returns a 15-minute *registration token* (a JWT marked `purpose=register`). This token can only be used to submit the registration form.
4. **Login token.** A JWT signed with `JWT_SECRET` that holds the user id, role and centre. The browser keeps a farmer's token in `localStorage` (all tabs, 7 days) and a staff token in `sessionStorage` (this tab only, 12 hours), and sends it as `Authorization: Bearer …`. A token the server refuses gets 401 with code `UNAUTHORIZED`, and only that code logs the browser out.
5. **Access rules.** `JwtAuthFilter` reads the token on every request and then loads the account from the database (one small query by id). The account must still be switched on, so switching an account off locks it out at once, not when its token runs out. Its role in the database must also be the role in the token, so a token with a made-up role (e.g. a farmer's id with `SUPER_ADMIN`) is refused with 401. The role, name and centre the controllers see come from the database, not from the token. `SecurityConfig` allows `/api/farmers/**` only for FARMER, `/api/operator/**` only for OPERATOR (staff), and `/api/admin/**` for ADMIN and SUPER_ADMIN. Inside the admin APIs, `AccessScope` limits a centre admin to their own centre and keeps centres, crop rates and demo data for the super admin.
6. **Staff passwords** are stored as BCrypt hashes. The super admin's password comes from `.env` at start-up, so it is never in the code or a migration.
7. **Guessing passwords.** Wrong staff passwords are counted per username **and** client IP address, in the `login_attempts` table (V9, `LoginAttemptService`). After 5 wrong passwords in a row from one address, that username is locked for 15 minutes from that address only (`app.staff-login`), even for the right password. A stranger guessing passwords therefore locks out only themselves, and the real staff can still log in at their own computers. (Before V9 the whole account was locked for everybody, and the usernames are easy to guess.)
   - **Unknown usernames** are counted and locked the same way, and their password is checked against a dummy BCrypt hash so the answer takes as long. So the answers never show which usernames exist.
   - **Tries at the same moment** are all counted: each wrong one is a single `INSERT … ON CONFLICT DO UPDATE` that adds 1 in the database. Like the OTP, it is saved even though the request fails (`noRollbackFor`).
   - **Unlocking.** The right password (when not locked) clears the count for that address. An admin setting a new password lifts the lock for every address. `AutomationJob` deletes rows that have not changed for a day and are not locked.
   - **The address** is `request.getRemoteAddr()`. Behind Nginx it is the visitor's real address, taken from `X-Forwarded-For` (`server.forward-headers-strategy: native`, see *Deploying*). On the server Nginx also limits login requests per IP address and answers 429 `RATE_LIMITED`.

## Tests

Run them in IntelliJ (right-click `src/test/java` → Run 'All Tests') or with Maven (`mvn test`). There are 51 tests in 12 classes. The 10 in `EndToEndFlowTest` are skipped without a local PostgreSQL.

| Test | What it checks |
|---|---|
| `EndToEndFlowTest` (10) | The whole app, step by step, on its own throw-away database (`farmer_procurement_e2e`, dropped afterwards; skipped without a local PostgreSQL): super admin, centre admin and staff with their access limits, a switched-off account's token refused, the lock after 5 wrong passwords (only from that IP address: the same user at another address still logs in; an unknown username is locked the same way; a new password lifts it), farmer OTP login, registration with bank details (encrypted), UPI change, booking with the QR email, check-in, call next, weighment, Razorpay order and signature check, paid status, demo day. Six smaller tests, some sending requests from several threads at the same moment: 6 farmers for the last 5 places (exactly 5 get one), two cancels of one booking (the place goes back once), 6 wrong OTP guesses (exactly 3 tries counted), a token with a made-up role (refused), the no-show and reminder jobs (one reminder however often they run), the exact Content-Security-Policy header, the visit review (only after the weighment, only once, only the farmer's own booking, wrong stars or codes refused; the super admin and that centre's admin see it, another centre's admin does not) and demo reviews (removed with the demo data) |
| `JwtServiceTest` (5) | Login and registration tokens, expiry, tampering |
| `SecretGuardTest` (4) | The built-in `local-dev-...` secrets stop the app when `APP_URL` is not localhost (also through `JwtService`), and are fine on the PC |
| `OtpServiceTest` (4) | The daily OTP limit for the whole site (`OTP_DAILY_LIMIT`, counted from midnight, no OTP saved or emailed after it), and that the limit per address is checked first |
| `EtaCalculatorTest` (5) | Waiting-time estimates in the queue |
| `RazorpayGatewayTest` (7) | Payment signature check, test keys only, finding an order's earlier successful payment (`captured` or `authorized`, not `failed` or `created`) |
| `EmailTemplatesTest` (4) | Emails in three languages, HTML escaping |
| `QrCodeTest` (1) | The email's QR picture reads back as the booking's QR text |
| `EncryptedTextTest` (5) | Bank details are unreadable in the database and come back the same; the development key is refused on a server |
| `MoneyFormatTest` (1) | Amounts in emails use Indian grouping (₹6,00,000.00) |

Deployment steps: [deploy/README.md](deploy/README.md).
