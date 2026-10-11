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

## Tech Stack

| Layer | Technology |
|---|---|
| **Frontend** | React 18, Vite, React Router v6, React Icons, hand-rolled SVG charts, CSS-variable design system (light/dark) |
| **Backend** | Java 17, JDK built-in HttpServer (no Spring), Maven, HikariCP connection pool, Jackson, JJWT, jBCrypt |
| **Database** | PostgreSQL — money as BIGINT kobo, double-entry ledger, constraint-enforced statuses, append-only audit log |
| **Payments** | Provider seam — mock adapter (default) / real Paystack adapter (awaiting DVA enablement) |
| **Email** | Brevo for OTP delivery (console fallback in demo mode) |

**Dependency principle:** libraries are used only where hand-rolling would
be reckless (cryptography, hashing, connection pooling, JSON parsing,
database protocol). Application logic — routing, auth flow, rate limiting,
validation, charts, the test suite — is implemented from first principles.

---

## Architecture

```
React frontend (localhost:5173)
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
PostgreSQL — 11 tables, constraint-enforced
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
- **Provider seam.** All external payment operations go through a
  `Provider` interface; a factory selects the mock or real Paystack
  adapter from configuration. Enabling live bank rails changes one config
  value, not code.

---

## Features

| Feature | How it works |
|---|---|
| **Registration + email OTP** | Server-side validation, BCrypt-hashed password, 6-digit code stored as SHA-256 hash + secret pepper, 10-min expiry, single-use atomic claim, rate-limited |
| **Two-step login** | Password + one-time code. Issues a 15-min signed access token and a 30-day hashed refresh token that rotates on every use — reuse of an old token is detected (grace window for concurrent tabs; aged reuse is treated as theft: all sessions killed) |
| **Transaction PIN** | 4–6 digit, bcrypt-hashed, required for every payment; SQL-side attempt counter with 15-min lockout after 5 wrong tries |
| **KYC → account number** | Verification unlocks money movement and issues a personal account number (background worker; mock in demo, real Paystack adapter ready behind the same interface) |
| **Wallet funding** | Paystack webhooks verified with HMAC-SHA512 over raw bytes, deduplicated via a UNIQUE event inbox — forged or replayed deposits cannot credit twice |
| **Transfers** | Internal (instant) and external (pending → provider verdict by webhook or watchdog chase, automatic reversal on failure). Every payment carries a client-minted idempotency key; the server refuses duplicates before consuming credentials |
| **Bill payments** | Airtime, data, electricity, TV, cable — same security ceremony; rejected payments auto-reverse |
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

### 1. Create the `.gitignore` FIRST (before any commits)

The repo should carry this file — if it's missing after cloning, create
`.gitignore` in the root:

```gitignore
# Secrets — NEVER commit
backend/.env
.env

# Build output
backend/target/
frontend/dist/

# Dependencies (regenerable via npm install / mvn compile)
frontend/node_modules/

# IDE / OS
.idea/
*.iml
.vscode/
.DS_Store
Thumbs.db
```

Then confirm your secrets were never tracked:

```bash
git ls-files | grep "\.env$"
# empty output = safe. If listed: git rm --cached backend/.env, rotate secrets.
```

### 2. Database setup

```sql
CREATE DATABASE banktest;
CREATE USER bankapp WITH PASSWORD 'bankpass';
GRANT ALL PRIVILEGES ON DATABASE banktest TO bankapp;
-- connect to banktest, then:
GRANT ALL ON SCHEMA public TO bankapp;
```

Run the schema (creates 11 tables, constraints, indexes — the final
structure including transaction categories and statement-signing keys):

```bash
psql -U bankapp -d banktest -f backend/src/main/resources/schema.sql
```

Grant the app user its table rights (tables are created by the superuser):

```sql
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO bankapp;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO bankapp;
REVOKE UPDATE, DELETE ON audit_logs FROM bankapp;  -- audit log: insert-only for the app
```

### 3. Backend configuration

```bash
cd backend
cp .env.example .env    # then fill in the values
```

| Variable | Purpose |
|---|---|
| `DB_URL` / `DB_USER` / `DB_PASS` | PostgreSQL connection |
| `JWT_SECRET` | ≥32 chars — signs access tokens (`openssl rand -hex 32`) |
| `OTP_PEPPER` | different random string — mixed into OTP/refresh hashes |
| `PAYSTACK_SECRET_KEY` | test key — webhook HMAC verification |
| `TRANSFER_CAP_KOBO` / `FEE_KOBO` | per-transfer cap / external payout fee |
| `CORS_ORIGIN` | allowed frontend origin |
| `OTP_CHANNEL` | `email` (Brevo) or anything else (console — demo mode) |
| `BREVO_API_KEY`, `BREVO_SENDER_EMAIL` | required only when OTP_CHANNEL=email |
| `PROVIDER` | `mock` (default) / `real` (Paystack, needs DVA) |

Boot is **fail-fast**: any missing or invalid value stops startup with a
named cause.

### 4. Run the backend

```bash
mvn clean compile
mvn exec:java -Dexec.args="--seed"   # demo users — run once (idempotent)
mvn exec:java                        # server on :8080
```

Demo users: `ada@test.com` / `password` (PIN `1234`, funded, verified) and
`tunde@test.com` / `password` (PIN `1234`, pending KYC). In console mode,
OTP codes print to this terminal.

### 5. Run the frontend

```bash
cd ../frontend
npm install
npm run dev        # http://localhost:5173
```

Vite proxies `/api` to `localhost:8080` in development.

### 6. Tests

```bash
cd backend
mvn exec:java -Dexec.args="test"
```

Pure-Java suite (no JUnit, per course constraint) racing real threads
against the money-path claims: reversal exactly-once, refresh replay
classification (benign vs theft), OTP single-use. **6/6 passing.**

---

## Postman Collection

A ready-made API collection ships with the repo:
**`postman/MyBank.postman_collection.json`** — import it into Postman
(**Import** → select the file) to explore and drill every endpoint with
automatic assertions.

**Setup:**

1. Create an Environment (Environments panel → +) with:
   - `paystackKey` = the same `sk_test_...` key as `backend/.env`
   - `baseUrl` = `http://localhost:8080`
2. **Select the environment** (top-right dropdown)

**Run order:**

1. **Health** — backend + DB reachable
2. **Login** → paste the OTP from the server console → **Verify Login OTP**
   (tokens auto-save for all subsequent requests)
3. **Submit KYC** — verifies the logged-in user (flips status, issues the
   account number); re-run **Dashboard** until the VA appears
4. **Money movement** — Request OTP → Internal/External transfer, Pay Bill
   (resend the same request = idempotent replay drill → "already processed")
5. **Webhooks** — Fund (signed deposit), Replay ("duplicate"), Tampered
   (401) — signatures computed inside Postman from your `paystackKey`
6. **Security drills** — no-token 401, invalid-date 400

The collection contains no secrets — the Paystack key lives only in your
local environment variables.

---

## API Overview

26 routes under `/api` (all JSON, JWT-protected except auth + health):

| Group | Endpoints |
|---|---|
| Auth | register, verify-otp, login, verify-login-otp, refresh, logout, set-pin, change-password |
| KYC | submit (verification + async account-number issuance) |
| OTP | request (purpose-tagged: transfer / beneficiary) |
| Transfers | internal, external |
| Bills | pay |
| Banks | list, resolve account name |
| Beneficiaries | list, add (OTP-gated), resolve MyBank account |
| Queries | dashboard, transactions (filters + pagination), statements (signed CSV), analytics, statement verification |
| Notifications | list, mark-all-read, read-one (user-scoped) |
| Webhooks | `/webhooks/paystack` (HMAC-SHA512 verified, event-inbox deduplicated) |

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

## Known Limitations

- KYC verification is simulated (real flow gated the same way)
- Real Paystack bank rails are written but pending DVA enablement
- Rate limiter is in-memory — multi-instance deployments need a shared store
- Statement-signing private keys are sealed with a config-derived key;
  production would use a KMS
- Privacy features are deterrents — phone cameras defeat any web technique

---

## Project Structure

```
├── backend/
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
│   ├── public/                   # favicon
│   ├── src/components/           # Layout, ui kit, Select, charts/, guards
│   ├── src/hooks/                # usePrivacy, useScrollFade
│   ├── src/pages/                # 13 pages across four user journeys
│   ├── src/utils/                # api.js gateway, validate mirrors, theme, motion
│   └── index.css                 # design tokens + full component styles
└── postman/
    └── MyBank.postman_collection.json   # importable API drill suite
```

---

## Author

Bakare Abdussalam — NIIT Lagos.