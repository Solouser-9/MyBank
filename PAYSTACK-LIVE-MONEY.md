# Preparing MyBank for live Paystack money movement

> **Important:** This codebase is currently a banking demonstration, not a
> production-ready financial service. Setting `PROVIDER=real` alone does not
> make every feature real or safe. The current seed data creates demo users
> and credits Ada with a fictitious opening balance; bill payments are always
> simulated; and external payouts use the Paystack merchant balance. Do not
> point a live Paystack account or real customer funds at this application
> until the production, compliance, security, and reconciliation work in this
> guide has been completed and independently reviewed.

## What a DVA is

A Paystack Dedicated Virtual Account (DVA) is a bank-account number associated
with a Paystack customer. A customer can send a bank transfer to that account;
Paystack notifies the merchant through a signed webhook, and the merchant can
credit the corresponding customer account in its own system.

A DVA is an **incoming collection** method. It does not, by itself, make
internal MyBank transfers or bill payments real, authorize payouts, supply
settlement funds, or make MyBank a licensed bank. Outgoing bank transfers are
a separate Paystack Transfers product and merchant-balance operation.

Paystack availability, account eligibility, required verification, pricing,
settlement, and supported banks can vary by country, business, and account.
Confirm the current requirements in the Paystack Dashboard and with Paystack
Support before building against live mode. Start with Paystack's official
[Dedicated Virtual Accounts documentation](https://paystack.com/docs/payments/dedicated-virtual-accounts/)
and [Transfers documentation](https://paystack.com/docs/transfers/).

## 1. Request DVA access from Paystack

1. Sign in to the Paystack Dashboard for the business that will own and
   operate MyBank. Complete the business/profile verification Paystack
   requests, including settlement and authorized-representative details.
2. In the Dashboard, find the Dedicated Virtual Accounts feature/product.
   If it is not available or enabled, contact Paystack Support from the
   Dashboard and request DVA activation for the business. Ask which
   verification documents, settlement setup, limits, fees, and supported
   banks apply to this specific account.
3. Separately request/confirm access to **Transfers** if MyBank is intended to
   send payouts to external bank accounts. DVA access does not imply transfer
   access. Confirm the source balance, approval/OTP requirements, limits,
   settlement timing, and how failed or reversed transfers are reported.
4. Keep test mode and live mode separate. Test keys and test virtual accounts
   cannot collect or send real funds. Do not use a live secret key in Postman,
   source code, screenshots, a committed file, or a shared `.env`.

Dashboard names and activation steps can change. Follow Paystack's current
instructions for the account rather than assuming that a code change or
creating an API key activates the product.

## 2. What the current code does (and does not do)

`PROVIDER` selects the provider used by KYC account issuance, bank lookup,
account resolution, and external bank transfer initiation:

- `PROVIDER=mock` (the default) selects `MockPaystackAdapter`.
- `PROVIDER=real` selects `RealPaystackAdapter`.

The real adapter already calls Paystack endpoints for customer creation,
dedicated-account lookup/creation, Nigerian bank listing and account
resolution, transfer-recipient creation, transfer initiation, and transfer
verification. Inbound `POST /webhooks/paystack` verifies the raw request
body's HMAC-SHA512 signature with `PAYSTACK_SECRET_KEY`. It handles
`charge.success` wallet credits and transfer success/failure/reversal events,
and records provider events to deduplicate deliveries.

These pieces are only an integration starting point. The following
limitations must be addressed before live use:

| Area | Current behavior | Required before live use |
|---|---|---|
| Seed and user verification | `Main` runs `Seed.run()` on every database's first boot. `Seed` creates demo users and credits Ada's wallet with a fabricated ₦5,000 opening balance. `POST /api/kyc/submit` simply marks any signed-in user `VERIFIED`. | Prevent demo seeding in production. Replace the simulated KYC endpoint with real identity/business verification and approval controls. Use a production database with reviewed migrations and no demo opening credits. |
| DVA provisioning | `RealPaystackAdapter` creates a Paystack customer and DVA asynchronously. It hard-codes `"test-bank"` as `preferred_bank` and does not persist a Paystack customer code for later reuse. | Confirm the live account's supported bank choice with Paystack, remove the test-only bank assumption, and make customer creation idempotent by storing/looking up the Paystack customer code. Handle an existing customer, retries, API error bodies, inactive accounts, and concurrent requests safely. Never generate/fabricate real account numbers. |
| Deposits/webhooks | A verified `charge.success` event is matched by virtual-account number, then its amount and reference are used to credit the wallet. | Verify the actual live DVA event shape and all required transaction fields; validate currency, amount, reference, account ownership/status, and transaction identity. Make event receipt, crediting, and processed status recoverable and atomic/idempotent, and alert/reconcile unprocessed events. Configure and verify the live webhook endpoint. |
| External transfers | The real adapter initiates a Paystack transfer using `source=balance`; MyBank first debits the user's internal wallet. A worker/watchdog checks the transfer. | Enable Transfers with Paystack, ensure the merchant balance is funded/settled, implement every required Paystack authorization step, and reconcile Paystack transfer states before telling a user money was delivered. Add operational controls, limits, approvals, and failure/manual-review paths. |
| Internal transfers | Internal transfers are ledger-only movements between MyBank wallets. | They are real only as liabilities recorded by MyBank; they do not call Paystack. Build and operate this capability only with the legal, safeguarding, reconciliation, support, and regulatory controls required for the business. |
| Bill payments | `BillRoutes` always uses `TestBillPaymentAdapter`; `PROVIDER=real` does not change it. | Integrate an authorized bill-payment provider, verify its products and responses, and implement reconciliation, idempotency, customer receipts, and reversals separately. |
| Operations | Demo-oriented async workers, in-memory rate limiting, and logging are used. | Add durable job/event processing, alerts, access controls, incident handling, backups, monitoring, secure secret rotation, audit retention, and settlement/ledger reconciliation suitable for real funds. |

In particular, do not treat the code's `VERIFIED` flag, dashboard labels,
Postman assertions, or a successful Paystack API response as proof that a
customer is legally verified or that a payout settled.

## 3. Codebase changes to plan

### Configuration and provider selection

1. Keep the existing `backend/.env.example` as the editable template; it is
   not a place for real credentials. In the actual backend runtime secret
   store (for example Render's environment settings), configure:

   ```dotenv
   PROVIDER=real
   PAYSTACK_SECRET_KEY=<server-side live secret supplied by Paystack>
   ```

   Keep `PAYSTACK_SECRET_KEY` server-side only. `sk_live_...` is a secret
   credential; a `pk_live_...` public key is not interchangeable with it.
   The same environment's secret is used both for Paystack API requests and
   validating webhook signatures. Maintain separate values for test and
   production deployments.
2. Keep `DB_URL`, `DB_USER`, `DB_PASS`, `JWT_SECRET`, `OTP_PEPPER`,
   `TRANSFER_CAP_KOBO`, `FEE_KOBO`, `CORS_ORIGIN`, and `OTP_CHANNEL`
   configured. Use a strong, independently generated JWT secret and OTP
   pepper. For the Vercel web app, set `CORS_ORIGIN` to its exact origin,
   currently `https://my-bank-bice.vercel.app`; this is the browser origin,
   **not** the API `baseUrl`. `PORT` is supplied by the hosting platform.
3. Do not put production secrets in the Postman environment, frontend
   `VITE_API_URL`, the Vercel build, or a committed file. Postman can call the
   deployed API without a Paystack key except for locally signing synthetic
   webhook test requests; never use a live secret for those tests.

Relevant implementation: `backend/src/main/java/bank/Config.java`,
`ProviderFactory.java`, `RealPaystackAdapter.java`, and the deployment's
secret configuration. `ProviderFactory` already chooses `real` when
`PROVIDER=real`; simply changing that value is not a complete go-live plan.

### DVA onboarding and persistence

Update `RealPaystackAdapter.createCustomerAndVa`:

- Replace the hard-coded `preferred_bank: "test-bank"` with a validated
  configuration/selection supported by the Paystack account and the selected
  test or live environment. Do not copy a test-mode bank identifier into live
  configuration without verifying it with Paystack.
- Save Paystack's customer code and DVA provider identifiers in the database
  so retries reuse the customer instead of creating duplicate customers.
  Add a schema migration and unique constraints for provider/customer/account
  identities; preserve the existing unique ownership and idempotency rules.
- Handle API timeouts and retry outcomes without accidentally creating
  duplicate customers/accounts. Treat non-2xx responses, malformed responses,
  inactive accounts, and missing account fields as explicit failures with
  safe logs and an operational alert.
- Keep account issuance asynchronous if desired, but expose a meaningful
  pending/failed state to the user and support team rather than silently
  leaving a verified user without an account number.

Relevant files: `RealPaystackAdapter.java`, `Provider.java`,
`KycRoutes.java`, `Scheduler.java`, `schema.sql`, and the dashboard/account
response in `QueryRoutes.java`.

### Genuine KYC and production user provisioning

Before live money operations, remove or gate demo behavior in
`Main.java`/`Seed.java`. A fresh production deployment currently seeds demo
credentials and a wallet balance that did not come from a deposit. Keep seed
data confined to development/test databases, and add an explicit deployment
guard so a production startup cannot run it.

Replace `KycRoutes.submit`'s unconditional `VERIFIED` update with an actual
identity-verification and review workflow appropriate to the service and
Paystack requirements. Verification must be evidence-backed, audited,
reviewable, and reversible where policy requires. Do not promote a user just
because they call an endpoint.

Relevant files: `Main.java`, `Seed.java`, `KycRoutes.java`,
`AuthRoutes.java`, `schema.sql`, plus the frontend verification flow.

### Deposits and webhooks

Configure Paystack's live webhook URL to the publicly reachable API endpoint:

```text
https://<your-backend-host>/webhooks/paystack
```

For the deployed service, use the Render backend hostname (not the Vercel
frontend URL). For local development, use a secure public tunnel and remove
or disable that webhook configuration when finished. Configure separate
webhook URLs for test and live environments if Paystack exposes separate
settings.

Before relying on deposits, update `WebhookRoutes` and its tests to:

- Accept only the Paystack event types the integration needs, and validate
  the exact live payload and account mapping.
- Confirm `currency` is NGN, amount is a positive integer in the expected
  kobo unit, the account is active and owned by the intended user, and a
  unique provider reference has not already been credited.
- Persist the signed event and credit/ledger entry with recoverable,
  exactly-once semantics. Acknowledge safely and alert/retry processing
  failures; do not permanently mark an event complete if crediting fails.
- Test valid signatures, invalid signatures, duplicate delivery, out-of-order
  events, unknown/inactive accounts, malformed fields, and database failure.

The current route authenticates the raw body with HMAC-SHA512 and has an event
inbox, which is a useful foundation. Do not remove raw-body verification or
replace it with trusting a client-supplied status.

Relevant files: `WebhookRoutes.java`, `Middleware.java`, `Scheduler.java`,
`LedgerService.java`, `schema.sql`, and webhook integration tests.

### Outgoing transfers and bills

The existing real transfer adapter creates a recipient then initiates a
Paystack transfer from the merchant's Paystack `balance`. It is not a
withdrawal directly from the end user's bank account. Before live payouts:

- Confirm with Paystack that Transfers are activated and determine how the
  merchant account must be funded and how approvals/transfer OTPs work.
- Implement the required authorization flow; do not assume an API call can
  immediately send a payout.
- Use stable idempotency/reference handling across network retries. Persist
  recipient codes where appropriate and reconcile Paystack's transfer
  response, webhook, and verification endpoint. A timeout is an unknown
  outcome, not proof of failure; do not retry in a way that can pay twice.
- Ensure a failed or reversed external transfer restores the wallet and fee
  exactly once; ensure a successful payout stays debited. Reconcile the
  internal ledger against Paystack settlements and transfers.

`TransferRoutes` and `RealPaystackAdapter` are the external-transfer path.
Internal transfers in `LedgerService` stay internal ledger movements.
`BillRoutes` directly instantiates `TestBillPaymentAdapter`, so build a
separate real biller adapter and select it through configuration before
claiming bill payments use live rails.

## 4. Safe rollout checklist

1. **Obtain activation and policy guidance** from Paystack for DVA and,
   separately, Transfers. Resolve the legal/regulatory, customer-funds
   safeguarding, disclosures, refunds, dispute, and support obligations for
   the business and jurisdiction. Paystack access is not a banking license.
2. **Create isolated environments:** development/test keys and database;
   staging with Paystack test credentials; production with a separate,
   migrated database and only live credentials. Never test against production
   customers or balances.
3. **Remove demo paths:** disable production seeding, fictitious opening
   balances, simulated KYC, mock bills, and test Postman values. Add a
   fail-closed startup check so mock mode or test credentials cannot be
   enabled accidentally in production, and live mode cannot start with
   incomplete verification/webhook configuration.
4. **Finish and review the integration work above.** Add automated contract
   tests with Paystack test mode, webhook signature/replay tests, concurrency
   tests, and failure/timeout tests. Have the financial ledger and security
   changes independently reviewed.
5. **Run staged test-mode journeys:** create a customer/DVA, receive a
   test deposit webhook, verify the balance/ledger/statement, exercise
   duplicate webhooks, account lookup, recipient resolution, payout
   authorization, success/failure/reversal, and reconciliation.
6. **Go live deliberately:** apply reviewed database migrations, provision
   only approved secrets in the backend host, configure the live webhook,
   verify it reaches the API, then enable a small controlled pilot with
   conservative limits and active monitoring. Confirm settlement and ledger
   totals before increasing usage.
7. **Operate the service:** monitor Paystack dashboard balances, webhook
   delivery and unprocessed events, pending transfers, ledger mismatches,
   failed payouts, customer complaints, and settlement reports. Document
   support escalation, key rotation, incident response, rollback, and
   customer-fund reconciliation procedures.

Do not switch the existing public deployment to live keys as an experiment.
The current integration has not been certified for real customer funds, and
the live environment needs a production-safe seed/KYC/ledger and transfer
design before the provider flag is changed.
