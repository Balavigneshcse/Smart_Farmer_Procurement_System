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

### Configuration

Local defaults are in `src/main/resources/application.yml`. To override them, set environment variables in IntelliJ (Run → Edit Configurations → Environment variables):

| Variable | Default | Purpose |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/farmer_procurement` | Database URL |
| `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` | `postgres` / `postgres` (from `application.yml`) | Database login |
| `PORT` | `8080` | HTTP port |

Secret keys (Razorpay, textbee, JWT) are added in later milestones. They are always set as environment variables and never committed.

## Project structure

```
src/main/java/com/cropcoders/procurement/
├── ProcurementApplication.java   entry point (forces Asia/Kolkata time zone)
├── controller/                   REST endpoints
├── service/                      business logic
├── repository/                   Spring Data JPA repositories
├── entity/                       JPA entities (tables)
└── dto/                          request/response objects
src/main/resources/
├── application.yml               configuration
├── db/migration/                 Flyway SQL migrations (V1 schema, V2 sample data)
└── static/                       HTML pages, css/, js/, i18n/ (en, ta, hi)
```

See [PLAN.md](PLAN.md) for the full design and milestone checklist.
