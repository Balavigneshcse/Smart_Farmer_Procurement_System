# Smart Farmer Procurement System — Build Plan

College mini project · Theme: Smart Automation · Built by you + Claude (no other team members)

**Problem:** Farmers face long waits, no information about procurement schedules, and no visibility into procurement/payment status.
**Solution:** Farmers book time slots, the centre runs a live token queue, email and in-app notifications go out at key moments, and every booking has a tracked timeline from arrival to payment.

---

## 1. Stack

| Layer | Choice |
|---|---|
| Frontend | HTML, CSS, vanilla JS — served by Spring Boot from `src/main/resources/static` (no CORS, one deployable) |
| Languages | Farmer pages: English / தமிழ் / हिन्दी (farmer picks at registration; landing + login have a switch). Operator desk and admin: **English only** |
| Backend | Java 21, Spring Boot 4.1.1, Maven (IntelliJ's bundled Maven), Spring Security + JWT (jjwt 0.13.0) |
| Database | PostgreSQL · Flyway migrations · local = PostgreSQL 18 installed on the PC (**no Docker**), database `farmer_procurement`, user `postgres` / `postgres` · production = Neon |
| Realtime | Server-Sent Events (`SseEmitter` + browser `EventSource`) |
| Payments | Razorpay **test mode** (Checkout + server-side signature verification) |
| Notifications | **Email + in-app** (no SMS). On the PC: **Gmail SMTP** with an App Password (500/day). On Render: **Brevo** HTTPS email API (free 300/day), because Render's free plan blocks SMTP ports 25/465/587. `console` provider prints emails in the log during development |
| Hosting | Render free web service (Docker) + Neon free Postgres |
| IDE | IntelliJ IDEA 2026.2, project SDK = JDK 21 (`C:\Program Files\Java\jdk-21.0.12.1`) |
| Source control | https://github.com/Balavigneshcse/Smart_Farmer_Procurement_System — commit + push after each milestone |

**Decisions made**
- Farmers log in with **email + OTP** (the OTP is emailed). Registration asks: name, email (OTP-verified), mobile number (shown to the operator, not verified), address + district, Aadhaar last 4 digits, preferred language. The crop is asked **at booking**, not at registration.
- A farmer's emails are sent in their registered language.
- Decision 2026-09-23: SMS dropped entirely (first textbee, then the Android SMS gateway). Notifications are email + in-app.
- Docker is used only by Render in the cloud (Render has no native Java runtime). Nothing Docker runs on the PC.

## 2. Deployment architecture

```
Farmer phone ─┐
Operator PC  ─┼── HTTPS ──►  Render (free, Docker)
Admin PC     ─┤              Spring Boot app + static pages
TV display   ─┘                 │ JDBC+SSL      │ HTTPS           │ HTTPS
                                ▼               ▼                 ▼
                          Neon Postgres    Razorpay API     Brevo email API ──► farmer's inbox
                                                            (on the PC: Gmail SMTP instead)

Admin browser ── checkout.js popup ──► Razorpay (test)  → success → backend verifies signature
```

## 3. Free-tier limits and the design rules they force

| Service | Free limit | Design rule |
|---|---|---|
| Render web service | 750 h/month, 512 MB RAM, sleeps after 15 min idle, ~1 min to wake | Open the site 2 min before any demo. JVM tuned for 512 MB. Optional: cron-job.org pings `/api/ping` (no DB hit) only 8 AM–8 PM |
| Render Postgres | Deleted 30 days after creation | **Don't use it** — use Neon |
| Neon Postgres | 0.5 GB storage, 100 CU-hours/month, sleeps after 5 min idle | Scheduled jobs run every 15 min, 7 AM–9 PM IST only, so the DB can sleep. Hikari pool max 5 |
| Gmail SMTP | 500 recipients/day; needs 2-Step Verification + App Password; **blocked on Render free** (SMTP ports) | Used only on the PC. Daily cap in code, then in-app only |
| Brevo | 300 emails/day free, HTTPS API | Used on Render (M8). Same daily cap |
| Razorpay | Test mode is free, no KYC | Test keys only. Test UPI ID `success@razorpay` |

## 4. Who does what

| Claude | You |
|---|---|
| Writes all code: Java, SQL, HTML/CSS/JS, Dockerfile, README | Create accounts: Razorpay, GitHub, Neon, Render, Brevo (at deploy) |
| Builds and runs locally, fixes errors, tests pages in the browser | Create a Gmail App Password and put it in `.env` |
| Git commits with clear messages | Put secret keys into IntelliJ run config / Render env vars (never in code) |
| Writes deploy + demo steps | Run it in IntelliJ, test on your phone, click Deploy on Render, review each milestone |

## 5. Scope

**Must-have (the project is complete when these work)**
1. Farmer registration + OTP login
2. Centres, crops and rates (seeded; admin can edit)
3. Automatic slot generation with capacity; booking gives a token + QR code
4. Operator desk: check-in, **Call next** per counter, live queue list
5. Live TV display + farmer live position/ETA (SSE)
6. Weighment entry (weight, moisture, grade) → automatic amount → accept/reject
7. Razorpay test payment → status PAID
8. Email on key events (daily cap) + in-app notification list
9. Farmer tracking timeline (booked → arrived → at counter → weighed → paid)
10. Automation jobs: reminders, no-show marking, "turn near" alert
11. Admin dashboard: today's farmers, average wait, queue length, quantity procured, payments pending/paid

**Nice-to-have (only after must-haves are deployed)**
- QR scanning with webcam at check-in (`html5-qrcode`)
- Tamil / English toggle
- Charts on the dashboard (Chart.js)
- "Least crowded slot" suggestion across centres

## 6. Milestones (each ends with something you can run and test)

- [x] **M0 — Your setup:** JDK 21 installed, PostgreSQL 18 running, IntelliJ 2026.2 installed.
- [x] **M1 — Skeleton:** `pom.xml`, Flyway `V1__schema.sql` (all tables) + `V2__seed_data.sql` (centres, crops), `application.yml`, Centre/Crop entities, `/api/centres`, `/api/crops`, `/api/ping`, shared CSS, i18n (en/ta/hi), landing page. ✔ App starts in IntelliJ, tables appear in the DB, landing page shows centres and rates.
- [x] **M2 — Auth:** OTP send/verify (console sender), JWT, farmer registration (with language choice), staff username/password login, role-based access. Pages: farmer login/register (3 languages), staff login (English).
- [x] **M3 — Slots + booking:** slot generator (startup + daily), slot grid with busy colours, booking with capacity check, token + QR, my bookings, cancel.
- [x] **M4 — Live queue:** check-in (token / QR text / camera), call next (`FOR UPDATE SKIP LOCKED`), call again, not present (back of the line), SSE hub, TV display page, farmer live position/ETA.
- [x] **M5 — Procurement + payment:** weighment form (moisture limit blocks accept, rejection needs a reason), amount calculation, payments page, Razorpay order + checkout + HMAC verify (own `RazorpayGateway`, no SDK; live keys refused). Keys in git-ignored `.env`.
- [x] **M6 — Email + automation:** farmer login switched to **email + OTP** (Flyway V4: `users.email`, OTP and notifications keyed by email; mobile kept as contact), `EmailSender` with `console` and Gmail `smtp` providers, HTML email templates in en/ta/hi, emails sent in the background after commit, daily cap, in-app notification list on the dashboard, reminder / no-show jobs every 15 min, "turn near" alert. SMS code removed. ✔ Real Gmail send tested (OTP + booking confirmation).
- [x] **M7 — Dashboard + polish:** admin dashboard (today's numbers, live table per centre, Chart.js charts for 7 days, crops, payments), "Centres & crops" settings (crop rates / moisture / new crops, centre counters / hours / service time with automatic slot updates), demo data button (40 `@demo.invalid` farmers, a live day + 6 days of history, removable), new look with the same greens and hand-drawn paddy-field SVGs on every page, checked at 375 px phone width.
- [x] **M8 — Deploy:** (code ready; accounts and the live smoke test: see docs/DEPLOY.md) Dockerfile, push to GitHub, Neon DB, **Brevo email provider** (HTTPS API, since Render blocks SMTP), Render web service + env vars, smoke test on the live URL, demo checklist.

---

## 7. Design details

### Roles
| Role | Login | Can do |
|---|---|---|
| FARMER | Email + OTP | Profile, book/cancel slots, see live status, timeline, payments, notifications |
| OPERATOR | Username + password | For their centre: check-in, call next, weighment, mark no-show |
| ADMIN | Username + password | Centres, crops/rates, slot settings, payments (Razorpay), dashboard |
| Public | none | TV display for a centre (token numbers only — no names/phones) |

### Booking lifecycle
```
BOOKED ──check-in──► CHECKED_IN ──call next──► CALLED ──weighment──► COMPLETED (accepted) / REJECTED
  ├──► CANCELLED  (farmer cancels; slot capacity released)
  └──► NO_SHOW    (auto: slot end + 30 min passed, never checked in)

Payment (only for COMPLETED): PENDING ──► PAID / FAILED
```
Every change writes a `booking_events` row → this powers the farmer's timeline.

### Slots and capacity
- Each centre has `open_time`, `close_time`, `slot_minutes`, `counters` and `avg_service_min`.
- Capacity per slot = `counters × slot_minutes / avg_service_min` (e.g. 4 × 30 / 10 = 12 farmers).
- Slots for the next 7 days are generated at app startup and daily; the generator is idempotent (UNIQUE on centre+date+start).
- Busy colour for the booking grid: < 50% green, 50–85% amber, > 85% red.

### Booking safety
```sql
-- atomic capacity check: 0 rows updated => "slot full"
UPDATE slots SET booked = booked + 1 WHERE id = :slotId AND booked < capacity;

-- one active booking per farmer per day
CREATE UNIQUE INDEX uq_active_booking ON bookings(farmer_id, booking_date)
  WHERE status IN ('BOOKED','CHECKED_IN','CALLED');
```
Token = centre code + daily number, e.g. `TNJ-017`, from an upsert counter:
`INSERT INTO token_counters ... ON CONFLICT (centre_id, day) DO UPDATE SET last_no = token_counters.last_no + 1 RETURNING last_no`.

### Queue logic
- At check-in set `queue_priority`: on time (before slot end + 15 min) → slot start time; late → actual check-in time (joins the back).
- Queue = `CHECKED_IN` bookings of that centre today ordered by `queue_priority, checked_in_at`.
- Call next:
  ```sql
  SELECT * FROM bookings
  WHERE centre_id = ? AND booking_date = CURRENT_DATE AND status = 'CHECKED_IN'
  ORDER BY queue_priority, checked_in_at
  LIMIT 1 FOR UPDATE SKIP LOCKED;
  ```
- ETA = `ceil(max(0, position − free_counters) / counters) × avg_service_min`, where `avg_service_min` = average of (completed_at − called_at) for the last 10 farmers today at that centre (at least 3 needed), falling back to the centre default. See `EtaCalculator` + `EtaCalculatorTest`.
- "Not present": a called farmer who doesn't come goes back to the end of the line (`SKIPPED` timeline event).
- Every queue change broadcasts a snapshot on `/api/queue/{centreId}/stream` (SSE). Heartbeat comment every 20 s; dead emitters removed. The farmer page subscribes to the same public stream and finds its own token in the snapshot.

### Notifications (email + in-app)
| Event | Channel |
|---|---|
| OTP | Email (sent straight away; not stored in `notifications`) |
| Booking confirmed / cancelled (token, day, time, centre, crop) | Email + in-app |
| Reminder (~2 hours before the slot) | Email + in-app |
| Turn near (position ≤ 3, once per visit; again after "not present") | Email + in-app |
| Procured / rejected (weight, amount or reason) | Email + in-app |
| Payment done (amount, reference) | Email + in-app |
| No-show (slot end + 2 h, not checked in) | Email + in-app |
| Checked in (position, ETA), called to counter | In-app only (the farmer is at the centre) |

All messages are stored in `notifications` and shown on the farmer dashboard, in the farmer's language (`messages*.properties`). A notification is saved in the same transaction as the change; its email is sent **after commit, on a background thread** (`EmailDispatcher`, `@Async` + `@TransactionalEventListener`), then marked `SENT` / `FAILED`. A daily cap (`EMAIL_DAILY_LIMIT`, default 250 — below Gmail's 500 and Brevo's 300) protects the free quota: after it is reached, notifications are `SKIPPED` (in-app only). OTP emails are not capped.

Providers (`EMAIL_PROVIDER`): `console` (print to log), `smtp` (Gmail: smtp.gmail.com:587, STARTTLS, App Password), `brevo` (M8: `POST https://api.brevo.com/v3/smtp/email` with an `api-key` header).

### Payment flow (Razorpay test mode)
1. Weighment saved → `payments` row `PENDING`, amount computed on the server (in paise).
2. Admin clicks **Pay** → `POST /api/payments/{id}/order` → backend creates a Razorpay order → returns `order_id` + public `key_id`.
3. Browser opens Razorpay Checkout; pay with test UPI `success@razorpay`.
4. Success handler posts `razorpay_order_id`, `razorpay_payment_id`, `razorpay_signature` to `/api/payments/verify` → HMAC-SHA256 signature check (`RazorpayGateway`) → `PAID` → email + timeline update.
5. Viva note: in reality money goes government → farmer (DBT / RazorpayX Payouts). In the demo, the admin paying via test Checkout represents that disbursement.

### Scheduled jobs (`zone = "Asia/Kolkata"`)
| Job | When | What |
|---|---|---|
| Slot generator | On startup + `0 0 7 * * *` | Ensure slots exist for the next 7 days |
| Reminder (`AutomationJob`) | On startup + `0 */15 7-21 * * *` | BOOKED, slot starts within 120 min, `reminder_sent = false` → email (a booking made less than 2 h before its slot gets no separate reminder) |
| No-show (`AutomationJob`) | On startup + `0 */15 7-21 * * *` | BOOKED and slot end + 120 min passed → NO_SHOW + email (only for slots that ended in the last 24 h) |
| Turn near | Event-driven (after every queue change) | Farmers now at position ≤ 3 with `turn_alert_sent = false` → email |

### Database tables (Flyway)
| Table | Key columns |
|---|---|
| `users` | id, name, email (unique, lower case, farmers), mobile (farmers), username (unique, staff), password_hash (staff), role, centre_id (operators), created_at |
| `farmer_profiles` | user_id (PK/FK), address, district, aadhaar_last4, language (en/ta/hi) |
| `otp_codes` | id, email, code_hash, expires_at, attempts, used |
| `centres` | id, code, name, district, address, open_time, close_time, slot_minutes, counters, avg_service_min, active |
| `crops` | id, name, rate_per_qtl, max_moisture_pct, active |
| `slots` | id, centre_id, slot_date, start_time, end_time, capacity, booked · UNIQUE(centre_id, slot_date, start_time) |
| `token_counters` | centre_id, day, last_no · PK(centre_id, day) |
| `bookings` | id, token, farmer_id, slot_id, centre_id, booking_date, crop_id, est_qty_qtl, status, queue_priority, checked_in_at, called_at, counter_no, completed_at, reminder_sent, turn_alert_sent, created_at |
| `booking_events` | id, booking_id, status, note, created_by, created_at |
| `procurements` | id, booking_id (unique), weight_qtl, moisture_pct, grade, rate_per_qtl, amount, accepted, remarks, recorded_by, recorded_at |
| `payments` | id, procurement_id (unique), amount_paise, status, rzp_order_id, rzp_payment_id, paid_at |
| `notifications` | id, user_id, email, booking_id, event, subject, message, email_status (PENDING/SENT/FAILED/SKIPPED/IN_APP_ONLY), provider_ref, created_at, read_at |

Migrations: `V1__schema.sql`, `V2__seed_data.sql` (crops, 3 centres), `V3__staff_accounts.sql` (admin + operators), `V4__email_instead_of_sms.sql`. Never edit an applied migration — add a new `V5__...`. JPA runs with `ddl-auto: validate`.

### REST API
```
Public     GET  /api/ping
           GET  /api/centres · /api/crops · /api/centres/{id}/slots?date=
           GET  /api/queue/{centreId}          (snapshot)
           GET  /api/queue/{centreId}/stream   (SSE)
Auth       POST /api/auth/otp/send · /api/auth/otp/verify · /api/auth/register · /api/auth/login · GET /api/auth/me
Farmer     GET/PUT /api/farmers/me · PUT /api/farmers/me/language
           POST /api/bookings · GET /api/bookings/my · GET /api/bookings/{id} · POST /api/bookings/{id}/cancel
           GET  /api/notifications/my · POST /api/notifications/read-all
Operator   GET  /api/operator/queue
           POST /api/operator/checkin · /api/operator/call-next
           POST /api/operator/bookings/{id}/recall · /skip · /weighment
Admin      GET  /api/admin/dashboard
           GET/POST /api/admin/crops · PUT /api/admin/crops/{id}
           GET  /api/admin/centres · PUT /api/admin/centres/{id}
           GET/POST/DELETE /api/admin/demo      (demo data status / load / remove)
           GET  /api/admin/payments?status= · POST /api/payments/{id}/order · POST /api/payments/verify
```

### Pages (`src/main/resources/static`)
| Page | Purpose |
|---|---|
| `index.html` | Landing + choose role |
| `farmer/login.html`, `farmer/register.html` | OTP login, profile + language choice (3 languages) |
| `farmer/book.html` | Centre → date → slot grid (busy colours) → crop + quantity → token + QR |
| `farmer/dashboard.html` | Bookings, live position/ETA, timeline, payments, notifications |
| `staff/login.html` | Operator/admin login |
| `operator/desk.html` | Check-in, live queue, **Call next**, weighment form |
| `admin/dashboard.html`, `admin/settings.html`, `admin/payments.html` | Stats + charts + demo data, centres & crops, Razorpay payments |
| `display.html?centre=1` | TV board: now serving per counter + next tokens |
| `css/app.css`, `js/api.js`, `js/auth.js`, `js/admin.js` | Shared styles, fetch wrapper (adds JWT, handles 401), auth helpers, admin page helpers |
| `img/*.svg` | Hand-drawn paddy fields and crop sprigs used as backgrounds |
| `js/i18n.js`, `i18n/en.json`, `ta.json`, `hi.json` | Translations for farmer-facing pages only |

Operator, admin and staff-login pages are English only and don't load `i18n.js`.

CDN scripts: Razorpay `checkout.js`, `qrcodejs`, `html5-qrcode`, Chart.js (admin dashboard).

### Project structure (layered — easy to explain in the viva)
```
Smart Farmer Procurement System/
├── pom.xml · Dockerfile (M8, used only by Render) · .gitignore · README.md · PLAN.md
└── src/main/
    ├── java/com/cropcoders/procurement/
    │   ├── ProcurementApplication.java
    │   ├── config/       AppProperties
    │   ├── security/     SecurityConfig, JwtAuthFilter, JwtService, AuthUser
    │   ├── entity/       User, FarmerProfile, Centre, Crop, Slot, Booking, BookingEvent, Procurement, Payment, Notification, OtpCode
    │   ├── enums/        Role, BookingStatus, BookingEventType, PaymentStatus, NotificationEvent, EmailStatus
    │   ├── repository/   one Spring Data repository per entity + DashboardRepository (SQL)
    │   ├── dto/          request/response records
    │   ├── service/      AuthService, JwtService, OtpService, SlotService, BookingService, QueueService,
    │   │                 ProcurementService, PaymentService, NotificationService, AutomationService,
    │   │                 AdminDashboardService, AdminCatalogueService, DemoDataService
    │   ├── payment/      RazorpayGateway
    │   ├── email/        EmailSender (interface), ConsoleEmailSender, SmtpEmailSender, (M8) BrevoEmailSender,
    │   │                 EmailTemplates, EmailDispatcher (background sending)
    │   ├── realtime/     QueueSseHub
    │   ├── scheduler/    SlotGenerationJob, AutomationJob (reminders + no-shows)
    │   ├── controller/   Auth, Farmer, Catalogue, Booking, Queue, Operator, Payment, Admin, Ping
    │   └── exception/    ApiException, ApiError, GlobalExceptionHandler
    └── resources/
        ├── application.yml
        ├── messages.properties, messages_ta.properties, messages_hi.properties   (email texts)
        ├── db/migration/ V1__schema.sql, V2__seed_data.sql, V3__staff_accounts.sql, V4__email_instead_of_sms.sql
        └── static/       (pages above, css/, js/, i18n/)
```

### Configuration (environment variables)
| Variable | Local (IntelliJ) | Production (Render) |
|---|---|---|
| `DB_URL` | default `jdbc:postgresql://localhost:5432/farmer_procurement` | `jdbc:postgresql://<neon-host>/<db>?sslmode=require` |
| `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` | `postgres` / `postgres` (hard-coded in `application.yml`) | from Neon (overrides the yml) |
| `JWT_SECRET` | dev default | random 32+ characters |
| `RAZORPAY_KEY_ID` / `RAZORPAY_KEY_SECRET` | test keys | test keys |
| `EMAIL_PROVIDER` | `console` or `smtp` | `brevo` |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | your Gmail + App Password | – |
| `BREVO_API_KEY` / `MAIL_FROM` | – | Brevo key + verified sender email |
| `PORT` | — | set by Render |

Secrets live only in the git-ignored `.env` file on the PC (template: `example.env`) and in Render → Environment.

### Render deploy notes
- Web Service → connect GitHub repo → Runtime **Docker** → Instance **Free** → env vars above → health check path `/api/ping`.
- Dockerfile: multi-stage (`maven:3.9-eclipse-temurin-21` build → `eclipse-temurin:21-jre` run) with `-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -Xss512k -XX:TieredStopAtLevel=1`.
- `server.port=${PORT:8080}`; timezone forced to `Asia/Kolkata`.

### Demo script (~5 min)
0. Before the viva: Admin → Dashboard → **Load demo day** (fills every centre with a live queue and a week of history).
1. Admin: dashboard numbers and charts; Centres & crops (change a rate or counters, slots update).
2. Farmer (phone): email OTP login → book slot → token `TNJ-017` + QR + confirmation email.
3. Operator: check in your own booking (demo farmers are already waiting) → TV and phone update live ("Position 6 · ETA 20 min") → "turn near" email when you reach the front.
4. Operator: **Call next** → TV shows `TNJ-017 → Counter 2`.
5. Operator: record 24.5 qtl, moisture 15%, Grade A → amount auto-calculated → email.
6. Admin: Payments → Pay → Razorpay test checkout → farmer timeline shows **Paid** + email.
7. Dashboard: average wait, queue length, farmers served, quantity procured.
