# Smart Farmer Procurement System

Farmers often wait all day at procurement centres without knowing the schedule or what happened to their crop and payment. This app fixes that:

- **Slot booking.** Farmers register with their mobile number (OTP) and book a time slot at a procurement centre.
- **Live queue.** Operators check farmers in and call them to counters. Farmers see their position and waiting time live, and a TV screen at the centre shows who is being served.
- **SMS alerts.** Farmers get an SMS at key moments (booking, reminder, turn near, weighed, paid), in their chosen language (English, Tamil or Hindi).
- **Tracking.** Every booking has a timeline, from booked through weighed to paid, with payment through Razorpay (test mode).

Theme: Smart Automation · College mini project

## Tech stack

| Layer | Technology |
|---|---|
| Frontend | HTML, CSS, JavaScript (served by Spring Boot) |
| Backend | Java 21, Spring Boot 4.1, Maven, Spring Data JPA, Flyway |
| Database | PostgreSQL |
| Payments | Razorpay (test mode) |
| SMS | textbee.dev (Android phone as SMS gateway) |
| Hosting | Render (app) + Neon (PostgreSQL), both free tiers |

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
- <http://localhost:8080/staff/login.html>: operator and admin login (English)

### Logging in

**Farmers** log in with their mobile number and an OTP. While `SMS_PROVIDER` is `console` (the default), no real SMS is sent. The OTP is printed in the IntelliJ **Run** console:

```
======== SMS to +91 98XXXXXXXX ========
123456 is your OTP to log in to Smart Farmer Procurement System. ...
```

A new mobile number goes to the registration form: name, address, district, last 4 digits of Aadhaar, and language. That language is then used for the farmer's pages and SMS.

**Staff** demo accounts (created by `V3__staff_accounts.sql`):

| Username | Password | Role |
|---|---|---|
| `admin` | `Admin@123` | Admin |
| `operator.tnj` | `Operator@123` | Operator, Thanjavur |
| `operator.tvr` | `Operator@123` | Operator, Tiruvarur |
| `operator.ngt` | `Operator@123` | Operator, Nagapattinam |

OTP rules: valid for 5 minutes, 3 wrong tries, a new OTP only after 60 seconds, at most 5 per hour per number.

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

### Configuration

Local defaults are in `src/main/resources/application.yml`. To override them, set environment variables in IntelliJ (Run → Edit Configurations → Environment variables):

| Variable | Default | Purpose |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/farmer_procurement` | Database URL |
| `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` | `postgres` / `postgres` (from `application.yml`) | Database login |
| `PORT` | `8080` | HTTP port |
| `JWT_SECRET` | a local development value | Signs login tokens. **Set a random 32+ character value in production** |
| `SMS_PROVIDER` | `console` | `console` prints SMS in the log; `textbee` sends real SMS (M6) |

Secret keys (JWT, Razorpay, textbee) are always set as environment variables and never committed.

## Project structure

```
src/main/java/com/cropcoders/procurement/
├── ProcurementApplication.java   entry point (forces Asia/Kolkata time zone)
├── config/                       AppProperties (the "app:" settings)
├── security/                     SecurityConfig (who can call what), JWT service + filter
├── controller/                   REST endpoints
├── service/                      business logic (OTP, login, farmer profile, ...)
├── repository/                   Spring Data JPA repositories
├── entity/                       JPA entities (tables)
├── enums/                        Role, ...
├── dto/                          request/response objects
├── scheduler/                    timed jobs (slot generation)
├── sms/                          SmsSender interface + console sender (textbee in M6)
└── exception/                    ApiException + GlobalExceptionHandler (JSON errors)
src/main/resources/
├── application.yml               configuration
├── messages*.properties          SMS texts in English, Tamil, Hindi
├── db/migration/                 Flyway: V1 schema, V2 centres + crops, V3 staff accounts
└── static/                       HTML pages, css/, js/, i18n/ (en, ta, hi)
src/test/java/                    unit tests (JwtServiceTest)
```

### How login works (for the viva)

1. **Farmer OTP.** The server makes a random 6-digit code, stores only its BCrypt hash, and sends it by SMS in the farmer's language.
2. **Verify.** The code is checked against the hash. There are 3 attempts, and each wrong try is saved even though the request fails (`noRollbackFor`).
3. **New farmer.** The server returns a 15-minute *registration token* (a JWT marked `purpose=register`). This token can only be used to submit the registration form.
4. **Login token.** A JWT signed with `JWT_SECRET` that holds the user id, role and centre. The browser keeps it in `localStorage` and sends it as `Authorization: Bearer …`.
5. **Access rules.** `JwtAuthFilter` reads the token on every request, and `SecurityConfig` allows `/api/farmers/**` only for FARMER, `/api/operator/**` only for OPERATOR, and `/api/admin/**` only for ADMIN.

See [PLAN.md](PLAN.md) for the full design and milestone checklist.
