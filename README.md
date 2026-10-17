# MyBank — Full-Stack Digital Banking Platform

A complete digital banking web application built to demonstrate full-stack
development skills: user accounts with two-factor authentication, wallet
funding via verified payment webhooks, transfers and bill payments with
transaction security, a double-entry ledger, downloadable and digitally
signed statements, spending analytics, and in-app notifications.

> **Course constraint:** the backend is pure Java — no Spring, no JUnit.
> HTTP routing, authentication, rate limiting, validation, and even the
> concurrency test suite are built from first principles on the JDK.

---

## Live Deployment

The full stack is deployed on free tiers and auto-deploys from GitHub:

| Piece | Platform | URL |
|---|---|---|
| **Frontend** (React) | Vercel | https://my-bank-bice.vercel.app |
| **Backend API** (Java) | Render | https://mybank-backend-jqa4.onrender.com |
| **Database** | Neon (managed PostgreSQL) | (private — TLS connection) |

**Health check:** https://mybank-backend-jqa4.onrender.com/api/health

**Demo users (seeded once per database):** Ada Tester (VERIFIED, ₦5,000.00)
and Tunde Tester (PENDING, ₦0.00). Both use password `Password123` and PIN
`1234`. Use the email addresses defined in `backend/src/main/java/bank/Seed.java`;
the `ada@test.com` and `tunde@test.com` values in the Postman collection are
placeholders and do not match the seeded accounts. User IDs are database
specific; check the seed output and set the Postman `receiverId` accordingly.
In demo mode (`OTP_CHANNEL=console`) OTP codes print to the backend logs.

**Free-tier note:** the backend sleeps after ~15 minutes idle; the first
request takes ~50 seconds to wake it. Hit the health endpoint before demoing.

---

## Tech Stack

| Layer | Technology |
|---|---|
| **Frontend** | React 18, Vite, React Router v6, React Icons, hand-rolled SVG charts, CSS-variable design system (light/dark) |
| **Backend** | Java 17, JDK built-in HttpServer (no Spring), Maven, HikariCP connection pool, Jackson, JJWT, jBCrypt |
| **Database** | PostgreSQL — money as BIGINT kobo, double-entry ledger, constraint-enforced statuses, append-only audit log |
| **Payments** | Mock provider by default; partial real Paystack adapter for account/bank operations and external transfers, subject to account activation and further production work |
| **Email** | Brevo for OTP delivery (console fallback in demo mode) |

**Dependency principle:** libraries are used only where hand-rolling would
be reckless (cryptography, hashing, connection pooling, JSON parsing,
database protocol). Application logic — routing, auth flow, rate limiting,
validation, charts, the test suite — is implemented from first principles.

---

## Architecture

```
React frontend (Vercel)
        │  HTTPS + JSON, Bearer JWT, Idempotency-Key header
        ▼
Middleware  ── every request passes ONE gate:
        rate limit → CORS/security headers → JWT verification
        → dispatch → exception-to-status mapping → audit log row
        ▼
Router ──► Route handlers (auth, transfers, bills, queries,
        notifications, webhooks, KYC)
        ▼
Service layer (LedgerService, OtpService, PinService, JwtService)
        ▼
PostgreSQL (Neon) — 12 tables, constraint-enforced
```

**Key architectural decisions:**

- **One gate, one gateway.** Every request passes a single Middleware
  checkpoint; every frontend call flows through one `api.js` wrapper that
  attaches tokens and silently refreshes them. The same "one place to be
  correct" philosophy at both layers.
- **Money as integer kobo.** No floating point anywhere in the money path —
  ₦1.00 is stored as 100, and arithmetic is exact.
- **The ledger is the truth.** Every movement writes matched debit/credit
  entries; wallet balances are a cached view. A scheduled watchdog
  reconciles every wallet against its ledger every 5 minutes and raises an
  alarm on mismatch.
- **Status changes are atomic claims.** Using an OTP, rotating a refresh
  token, and reversing a payment all use conditional UPDATE statements —
  the status flip itself is the lock, so concurrent requests can never
  double-consume or double-refund. Verified by a test suite that races
  real threads against each claim.
- **Provider seam.** Account provisioning, bank lookup/resolution, and
  external bank transfers use the `Provider` interface; configuration selects
  the mock or partial real Paystack adapter. Bill payments currently use a
  separate test adapter. Selecting `PROVIDER=real` does not make every
  payment feature live; see [`PAYSTACK-LIVE-MONEY.md`](PAYSTACK-LIVE-MONEY.md).
- **Auto-seed, exactly once.** A `seed_marker` claim table (inserted in the
  same transaction as the seed) guarantees demo data is created exactly
  once per database — the server binds its port first, seeds after, and
  later boots skip silently.

---

## Features

| Feature | How it works |
|---|---|
| **Registration + email OTP** | Server-side validation, BCrypt-hashed password, 6-digit code stored as SHA-256 hash + secret pepper, 10-min expiry, single-use atomic claim, rate-limited |
| **Two-step login** | Password + one-time code. Issues a 15-min signed access token and a 30-day hashed refresh token that rotates on every use — reuse of an old token is detected (grace window for concurrent tabs; aged reuse is treated as theft: all sessions killed) |
| **Transaction PIN** | 4–6 digit, bcrypt-hashed, required for every payment; SQL-side attempt counter with 15-min lockout after 5 wrong tries |
| **KYC → account number** | Demo KYC currently marks the signed-in user verified; account issuance runs in a background worker through the mock or partial Paystack adapter. This is not a real identity-verification workflow. |
| **Wallet funding** | Paystack webhooks verified with HMAC-SHA512 over raw bytes, deduplicated via a UNIQUE event inbox — forged or replayed deposits cannot credit twice |
| **Transfers** | Internal (instant) and external (pending → provider verdict by webhook or watchdog chase, automatic reversal on failure). Every payment carries a client-minted idempotency key; the server refuses duplicates before consuming credentials |
| **Bill payments** | Demo flows for airtime, data, electricity, TV, and cable use a test adapter; rejected requests auto-reverse. No live biller integration is selected by `PROVIDER`. |
| **History & statements** | Server-side filtering with parameterized SQL and calendar-validated dates; paginated; CSV statements with derived opening/closing balances, **signed with the user's personal Ed25519 key** — tamper-evident and verifiable via an API endpoint |
| **Insights** | Hand-rolled SVG charts: balance trend, in-vs-out bars, cash-flow waterfall of real observed balances, spending donut grouped by a schema-enforced category column |
| **Notifications** | Written in the same database transaction as the money event they describe; unread badge, day-grouped list, per-user read endpoints |
| **Privacy** | Eye toggle (persisted, cross-tab synced), auto-blur on tab focus loss, PrintScreen clipboard guard, traceability watermark on data pages |
| **Dark/light theme** | Full palette as CSS variables under a `data-theme` attribute; contrast verified against WCAG 2.1 AA by an included script |

---

## Getting Started

### Prerequisites

| Tool | Version check |
|---|---|
| JDK 17 | `java -version` |
| Maven | `mvn -version` |
| Node.js (LTS) + npm | `node -v` |
| PostgreSQL 15+ | running locally |

### 1. Database setup

```sql
CREATE DATABASE banktest;
CREATE USER bankapp WITH PASSWORD 'bankpass';
GRANT ALL PRIVILEGES ON DATABASE banktest TO bankapp;
-- connect to banktest, then:
GRANT ALL ON SCHEMA public TO bankapp;
```

Run the schema (creates 12 tables, constraints, indexes — the final
structure including transaction categories, statement-signing keys, and
the seed marker):

```bash
psql -U bankapp -d banktest -f backend/src/main/resources/schema.sql
```

If a superuser created the tables, grant the app user its table rights:

```sql
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO bankapp;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO bankapp;
REVOKE UPDATE, DELETE ON audit_logs FROM bankapp;  -- audit log: insert-only for the app
```

### 2. Backend configuration

```bash
cd backend
```

Copy the template to `.env`, then fill in your database credentials and
secrets. The comments in `backend/.env.example` explain each variable and
which settings are optional. Keep real secrets out of version control
(`.gitignore` excludes `.env` files).

```powershell
Copy-Item .env.example .env
```

| Variable | Purpose |
|---|---|
| `DB_URL`, `DB_USER`, `DB_PASS` | PostgreSQL JDBC URL and database credentials |
| `JWT_SECRET` | Random signing secret for JWT access tokens; at least 32 characters |
| `OTP_PEPPER` | Separate secret mixed into OTP and refresh-token hashes |
| `PAYSTACK_SECRET_KEY` | Verifies Paystack webhook signatures; required even with the mock provider |
| `TRANSFER_CAP_KOBO`, `FEE_KOBO` | Maximum transfer amount and external transfer fee, both in kobo |
| `CORS_ORIGIN` | Exact browser frontend origin allowed by the API |
| `OTP_CHANNEL` | `email` sends OTPs through Brevo; `console` prints OTPs to backend logs |
| `BREVO_API_KEY`, `BREVO_SENDER_EMAIL` | Brevo credentials, required only when `OTP_CHANNEL=email` |
| `PROVIDER` | `mock` (default) or `real` to select the payment-provider adapter |

The backend requires `DB_URL`, `DB_USER`, `DB_PASS`, `JWT_SECRET`,
`OTP_PEPPER`, `PAYSTACK_SECRET_KEY`, `TRANSFER_CAP_KOBO`, `FEE_KOBO`,
`CORS_ORIGIN`, and `OTP_CHANNEL`. `PAYSTACK_SECRET_KEY` is required even in
mock mode because the webhook endpoint uses it to validate signatures.
`BREVO_API_KEY` and `BREVO_SENDER_EMAIL` are required only with
`OTP_CHANNEL=email`. `PROVIDER` is optional and defaults to `mock`. `PORT`
is read from the process environment (not `.env`) and defaults to `8080`.
Fill in every blank required value and use test-only credentials for local
development, never production secrets. Boot is **fail-fast**: missing
required configuration or a short JWT secret stops startup.

### 3. Run the backend

```bash
mvn clean compile
mvn exec:java
```

**Auto-seed:** on first boot the server seeds Ada Tester (VERIFIED, ₦5,000)
and Tunde Tester (PENDING, ₦0), both with password `Password123` and PIN
`1234`. Their email addresses are defined in `Seed.java`; the user IDs are
printed when seeding succeeds. A `seed_marker` guarantees seeding happens
once per database, crash-safe; later boots print `Seed marker found —
skipping.` In console mode, OTP codes print to this terminal.

The backend is also **port-aware** (`PORT` env var, default 8080) for
cloud deployment.

### 4. Run the frontend

```bash
cd ../frontend
npm install
npm run dev        # http://localhost:5173
```

Vite proxies `/api` and `/webhooks` to `localhost:8080` in development. For
deployed frontends, set the `VITE_API_URL` environment variable to the
backend URL at build time.

### 5. Tests

```bash
cd backend
mvn exec:java -Dexec.args="test"
```

Pure-Java suite (no JUnit, per course constraint) racing real threads
against the money-path claims: reversal exactly-once, refresh replay
classification (benign vs theft), OTP single-use. **6/6 passing.**

---

## Postman Collection

The API collection is `postman/MyBank.postman_collection.json`. Import it
into Postman, then follow the setup, ordered workflows, and drill notes in
[`postman/README.md`](postman/README.md). The guide covers environment
configuration, OTPs and automatically saved tokens, payment idempotency,
webhook signatures, statements, cross-user checks, and troubleshooting.

---

## API Overview

27 routes under `/api` plus the Paystack webhook route (all JSON,
JWT-protected except public auth steps, health, and webhook):

| Group | Endpoints |
|---|---|
| Health | `GET /api/health` |
| Auth | register, verify-otp, login, verify-login-otp, refresh, logout, set-pin, change-password |
| KYC | submit (marks the signed-in user verified and starts account-number issuance) |
| OTP | request (purpose-tagged: transfer / beneficiary) |
| Transfers | internal, external |
| Bills | pay |
| Banks | list, resolve account name |
| Beneficiaries | list, add (OTP-gated), resolve MyBank account |
| Queries | dashboard, transactions (filters + pagination), statements (signed CSV), analytics, statement verification |
| Notifications | list, mark-all-read, read-one (user-scoped) |
| Webhooks | `POST /webhooks/paystack` (HMAC-SHA512 verified, event-inbox deduplicated; outside `/api`) |

---

## Deployment

The production deployment uses three free-tier platforms, all auto-deploying
from GitHub:

| Piece | Platform | Notes |
|---|---|---|
| Frontend | **Vercel** | Env var `VITE_API_URL` = backend URL (baked in at build time) |
| Backend | **Render** | Docker build (`backend/Dockerfile` — multi-stage Maven + JRE 17); port-aware (`PORT` env var); env vars carry all config |
| Database | **Neon** | Managed PostgreSQL over TLS (`sslmode=require`) |

**Configuration rules:**

- Secrets live in platform environment variables, never in the repository
  (`.gitignore` excludes `.env`)
- `CORS_ORIGIN` on the backend must exactly match the deployed frontend URL
- The backend binds `PORT` **before** slow startup work, so platform health
  checks always succeed
- The auto-seed is marker-guarded — it runs exactly once per database,
  ever

**Free-tier trade-off:** the Render backend sleeps after ~15 minutes idle
and takes ~50 seconds to wake. Hit the health endpoint before demoing.

---

## Security Highlights

- Integer-kobo money math — no floating point in the money path
- Double-entry ledger with scheduled reconciliation
- Idempotency keys (client-held across retries) + user-scoped replay
  checks + DB UNIQUE backstops
- Atomic conditional UPDATEs on every money-state transition (race-tested)
- HMAC-SHA512-verified webhooks with exactly-once event inbox
- bcrypt passwords/PINs; hashed + peppered OTPs and refresh tokens
- JWT with version-based instant revocation (kill-switch)
- Rate limiting (per IP, per category), PIN lockout, OTP attempt caps
- Two-layer validation: server = source of truth, client = documented mirror
- Append-only audit log (UPDATE/DELETE revoked at the database level)
- Digitally signed statements (Ed25519, JDK-native) — tamper-evident
- Exact-origin CORS response configured with `CORS_ORIGIN` (not a multi-origin allow-list)

## Known Limitations

- KYC verification is simulated (real flow gated the same way)
- Paystack DVA/Transfers integrations are incomplete for live customer funds
- Live-money integration requirements and current codebase limitations are
  documented in [`PAYSTACK-LIVE-MONEY.md`](PAYSTACK-LIVE-MONEY.md)
- Rate limiter is in-memory — multi-instance deployments need a shared store
- Statement-signing private keys are sealed with a config-derived key;
  production would use a KMS
- Privacy features are deterrents — phone cameras defeat any web technique
- Free-tier hosting: backend sleeps when idle (cold-start delay)

---

## Project Structure

```
├── backend/
│   ├── Dockerfile                # multi-stage Maven build for Render
│   ├── src/main/java/bank/       # flat package, ~30 classes
│   │   ├── Main / Config / Db / Json / Crypto / Validate
│   │   ├── Middleware / Router / JwtService / Audit
│   │   ├── AuthRoutes / OtpService (+ delivery chain) / PinService / KycRoutes
│   │   ├── LedgerService / TransferRoutes / BillRoutes / BankRoutes
│   │   ├── BeneficiaryRoutes / QueryRoutes / NotificationRoutes
│   │   ├── WebhookRoutes / Scheduler / Workers / Seed / MoneyPathTests
│   │   ├── Provider seam (Provider, Factory, Mock, Real adapters)
│   │   └── StatementSigner (Ed25519, keys sealed at rest)
│   ├── src/main/resources/schema.sql
│   └── pom.xml
├── frontend/
│   ├── public/                   # favicon set, SPA redirects
│   ├── src/components/           # Layout, ui kit, Select, charts/, guards
│   ├── src/hooks/                # usePrivacy, useScrollFade
│   ├── src/pages/                # 15 page components
│   ├── src/api.js                # API gateway
│   ├── src/utils/                # format, validation mirrors, theme, motion
│   └── index.css                 # design tokens + full component styles
├── postman/
│   └── MyBank.postman_collection.json   # importable API drill suite
├── PAYSTACK-LIVE-MONEY.md        # DVA/Transfers scope and live-money limitations
└── system.properties             # JDK 17 pin for cloud builds
```

---

## Author

Bakare Abdussalam — NIIT Lagos.