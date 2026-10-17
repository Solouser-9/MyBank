# Using the MyBank Postman collection

The collection `MyBank.postman_collection.json` exercises the MyBank HTTP API,
including normal account flows and deliberate replay, tampering, and
authorization checks. Requests include Postman test scripts that check
expected status codes and response fields.

## 1. Choose what you are testing

To use the deployed web app on your PC, open
[https://my-bank-bice.vercel.app](https://my-bank-bice.vercel.app) in your
browser. The website is the Vercel frontend; its API is hosted separately on
Render. Postman sends requests to the API directly, so its `baseUrl` must be
the Render **backend origin**, not the Vercel website URL:

```text
https://mybank-backend-jqa4.onrender.com
```

Do not append `/api` or `/api/health` to `baseUrl`, and do not add a trailing
slash. The collection's Health request adds `/api/health` itself.

If you are testing a local backend instead, start PostgreSQL, configure
`backend/.env`, and run the backend as described in the repository
[README](../README.md#getting-started); use `http://localhost:8080` for
`baseUrl`. The Vercel web app remains the deployed frontend and is independent
of whether you are sending Postman requests to Render or to your local API.

In Postman, choose **Import**, select `postman/MyBank.postman_collection.json`,
then select the imported collection. Run **0 · Setup → Health** first. A
successful response is HTTP 200 with `"status": "ok"`. Render's free service
may take a short while to wake on the first request; wait for Health to
respond before continuing.

## 2. Configure Postman variables

Create and select a Postman Environment (the environment dropdown near the
top-right of the app). Add:

| Variable | Value |
|---|---|
| `baseUrl` | For the deployed site, `https://mybank-backend-jqa4.onrender.com` (Render backend origin); for local API testing, `http://localhost:8080` |
| `paystackKey` | Exactly the same test secret configured as `PAYSTACK_SECRET_KEY` in the backend's Render environment (or local `.env`) |

Select this environment in Postman before sending requests. The browser's
Vercel URL is for viewing and using the web app; it is not the Postman API
base URL. Postman is not subject to browser CORS enforcement. The deployed
website itself does run in a browser, so the backend's `CORS_ORIGIN` must
allow `https://my-bank-bice.vercel.app` for web-app API requests to work.

The collection also defines starter collection variables:

| Variable | Purpose |
|---|---|
| `email` | Account email used by the Ada login request; replace the placeholder with the Ada email configured in `backend/src/main/java/bank/Seed.java` |
| `password` | Seeded password, `Password123` |
| `pin` | Seeded transaction PIN, `1234` |
| `receiverId` | Recipient user ID for internal transfers; set it to Tunde's ID from the seed output/database |
| `newPassword` | The current password-change request uses a literal value (`NewDrill123`); edit that request body directly if you want a different new password |
| `paystackKey` | May be set in the environment above; the pre-request scripts resolve it through Postman's variable scopes |

The current collection's `ada@test.com` and `tunde@test.com` entries are
placeholders; they do not match the seeded users in `Seed.java`. The Tunde
login request has its email in the request body, so edit that body to the
seeded Tunde email before running it. On the deployed service, use the
accounts and IDs in the Render database: the IDs printed by a local seed are
not necessarily the IDs on the deployed database. Seeding runs only once per
database, so do not assume that deleting or changing Postman variables
resets the deployed accounts or balances.

The collection scripts create other collection variables as requests run,
including access/refresh tokens, pending login IDs, the virtual account number,
idempotency keys, and statement/webhook data. Leave the collection's
`Authorization` headers and scripts intact: successful login verification
saves tokens for protected requests. Treat access and refresh tokens as
credentials; do not share or commit exported environments containing them.

## 3. Complete an authentication flow

For the seeded Ada account:

1. Run **1 · Auth → Login (step 1 — password)**. It saves the returned user ID
   as `pendingUserId` and requests a login OTP.
2. Retrieve the matching login OTP from the channel configured on the
   backend. For local `OTP_CHANNEL=console`, find its `[TEST OTP]` line in
   the backend terminal. For the deployed Render API, check the account's
   email if `OTP_CHANNEL=email`; if it is configured for console delivery,
   inspect the Render service logs. Confirm the user ID and `purpose=login`.
3. Replace `REPLACE_ME` in **Verify Login OTP (saves tokens)** with that
   six-digit code, then send the request. The test script saves
   `accessToken`, `refreshToken`, and `userId`; protected requests use the
   access token automatically.
4. Run **Dashboard** to check the account and, after the virtual account has
   been issued, save its account number for webhook tests.

OTP codes expire after 10 minutes, are single-use, and are limited to three
requests per user and purpose in a 15-minute window. A wrong or expired code
returns HTTP 400. If the code expires or is used already, request another
code rather than retrying it. With `OTP_CHANNEL=email`, check the user's
mailbox instead of the backend console.

To test signup instead, run **Register (fresh user)**, then use the generated
`newUserId` and the signup OTP in **Verify OTP (signup)**. The generated email
is saved as `regEmail`; set `email` to that value before logging in as the new
account. Newly registered users need to set a transaction PIN and complete
KYC before money-movement requests. The signup request generates a fresh
email and phone each run.

## 4. Run the collection in order

The numbered folders group the API operations. Send requests manually in the
order below; several steps require copying an OTP, checking asynchronous
account issuance, or reviewing state changes, so running the entire
collection unattended is not appropriate.

| Folder | What it tests and prerequisites |
|---|---|
| **0 · Setup** | Health check. **Reset Idempotency Keys** clears the collection's three generated payment keys; run it before initiating fresh internal, external, or bill payments. |
| **1 · Auth** | Registration/OTP verification, login/OTP verification, refresh-token rotation, PIN setup, password change, and logout. Login before protected calls. |
| **2 · KYC & Account** | KYC submission, dashboard, bank list, and external account-name resolution. KYC is applied to the currently logged-in user. After submitting, poll Dashboard for the asynchronously issued virtual account. |
| **3 · Money Movement** | Request a `transfer` OTP, then paste it into an internal transfer, external transfer, or bill payment. Each payment also needs a valid PIN and a unique `Idempotency-Key`. Amounts are in kobo: `10000` is ₦100. |
| **4 · Beneficiaries** | List and resolve beneficiaries. Adding one requires an OTP requested with purpose `beneficiary`, not a `transfer` OTP. |
| **5 · Queries & Statements** | Analytics, paginated/filtered transactions, statement download, valid statement verification, and a deliberate tampering check. Re-download the statement after the tampering request because its pre-request script changes the saved statement text. |
| **6 · Notifications** | List notifications, mark one read, and mark all read. Run the list request first to populate `lastNotifId`. |
| **7 · Webhooks (Paystack)** | Synthetic HMAC-signed funding webhook, duplicate delivery, and invalid-signature rejection. Run Dashboard first to populate `vaNumber`; `paystackKey` must match the backend key. |
| **8 · User B (Tunde)** | Second-user login and cross-user transfer/idempotency checks. Set Tunde's email and ID correctly, then verify as Tunde before sending requests in this folder. The cross-user key-collision check needs Ada's external-transfer key and Tunde's OTP. |
| **9 · Security Drills** | A protected request without authorization must return 401; an invalid transaction date must return 400 rather than 500. |

The collection tests use expected responses to confirm each drill. For a
payment to succeed, its prerequisite OTP, PIN, KYC status, available balance,
recipient, and idempotency key all need to be valid.

## 5. Understand the important stateful tests

### Idempotency keys and payments

Internal transfers, external transfers, and bill payments use generated
collection keys that are minted only if a key is not already present. Sending
the same request again with the same key is an idempotency/replay test, not a
new payment. Run **Reset Idempotency Keys** before a genuinely new attempt.
This only clears Postman variables; it does not delete payment records or
change backend state.

**Pay Bill (forced-FAIL drill — auto-reversal)** deliberately uses the fixed
key `fail-demo-001`. It is a stateful failure/reversal demonstration and is
not reset by the collection reset request. Do not use it as a normal payment.

External payouts may remain pending while the mock provider/watchdog resolves
them. The forced-failure bill request is intended to demonstrate an automatic
reversal.

### Webhook signature checks

The funding request creates a synthetic `charge.success` payload for ₦500
(`50000` kobo) and computes the Paystack-compatible HMAC-SHA512 signature in
Postman. It does not call Paystack. The API validates the signature using its
`PAYSTACK_SECRET_KEY`, then credits the virtual account once. Replaying the
same reference should return a duplicate result without crediting twice.
The tampered webhook changes the amount but keeps the old signature; expect
HTTP 401.

For local testing, the configured key can be a test-only string as long as
the Postman environment and backend use exactly the same value. Never use or
publish a live production key for these drills.

### Statement signature checks

Run **Download Statement** first; it saves the exact CSV response as
`statementText`. **Verify Statement** sends those exact bytes and should
report a valid signature. **Tamper Statement + Verify** inserts a fake row
into the saved text and should report an invalid signature. Download the
statement again before trying pristine verification another time.

### Cross-user and account-state checks

The collection keeps Ada and Tunde's token variables separately so the
cross-user tests can use both identities. Sign in as the intended account
before KYC or user-specific operations. An internal transfer from Ada to
Tunde can provide funds for the subsequent reverse-direction test, but it
also changes both balances and creates notifications/transactions.

The password-change request is also stateful: it changes the account
password and invalidates refresh-token sessions. Run it only when you intend
to change that user's password, and update the Postman `password` value
before logging in again. Logout revokes the current refresh token; the current
access token remains valid until it expires.

## Troubleshooting

| Result | Check |
|---|---|
| Connection refused / timeout | Confirm the backend is running and `baseUrl` has the right host, port, and no trailing slash. A sleeping hosted backend may need time to wake. |
| 401 on a protected request | Complete login and OTP verification; check that the request uses the current access token and that it has not expired. |
| 400 during OTP verification | Confirm the code's user and purpose, and request a new code if it expired or was already consumed. |
| 409 on a payment | Check whether the idempotency key was already used. Reset the generated key before starting a new payment; a fixed-key drill may not be reusable. |
| Webhook signature failure | Ensure the selected environment's `paystackKey` is identical to backend `PAYSTACK_SECRET_KEY`, and run Dashboard first so `vaNumber` is available. |
| Virtual account missing | KYC/account issuance is asynchronous; poll Dashboard and check backend logs for provider/worker errors. |
| 429 | Wait for the relevant OTP or API rate-limit window to pass before retrying. |
| 500 or database-related errors | Check that the schema was applied to the configured database and that `backend/.env` contains all required settings. |
