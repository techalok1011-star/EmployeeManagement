---
name: project-context
description: Full onboarding context for the EmployeeManagement (branded "PayTrack") Spring Boot project - entity/schema map, services, endpoints, DB connection, current data state, and known gotchas. Use at the start of any session working in this repo, or whenever asked about parties, invoices, payments, outstanding balances, WhatsApp reminders, or employee/user management here.
user-invocable: true
---

# EmployeeManagement ("PayTrack") — Project Context

Payment-collection / outstanding-balance tracking system for a business with
sales parties, invoices, and payment entries logged by employees. The repo/
package is named `EmployeeManagement` but the app brands itself **"PayTrack"**
in generated PDFs/UI text - there is no separate "Employee" entity, employees
are just `User` rows with `role` = `EMPLOYEE`, `ACCOUNTANT`, `MANAGER`, or
`ADMIN` (`MANAGER` added 2026-07-18, see its own section below).

**Do not trust `ARCHITECTURE.md` / `USER_GUIDE.md` at the repo root** - they
describe an early, much simpler version (Users/PaymentEntry/TransactionLog
only) and were never updated as Party/Invoice/WhatsApp/export/analytics
features were added. Verify against `src/main/java/com/empmgmt/` instead.
Last full re-verification against source: 2026-07-29.

**The app is now deployed and live** at
`https://employeemanagement-q6h3.onrender.com` (Render, free tier, Docker
runtime) backed by a Neon serverless Postgres (region `us-east-1`) - see the
"Deployment" section below before assuming "the database" means only the
local one.

**Major data event, 2026-07-12**: the original demo/seed `invoices` (446
rows) and `payment_entries` (15 rows) were deliberately deleted in full and
replaced with a bulk import from a separate business's Tally Sales/Receipt
register (Shiv Shakti Cement Supply Agency - see
[[shivshakti-register-reconciliation]] memory and the
[[register-reconciliation]] skill for how that source data was built). 156
new `Party` rows were added and 18 existing parties were merged/matched to
Tally party names via fuzzy + transaction-level reconciliation. See "Current
data snapshot" below for resulting counts, and the Tally Import section
further down for the durable facts about this dataset (column conventions,
what's real vs. placeholder, etc).

**Major data event, 2026-07-28**: a **second, larger** historical backfill
for the same ShivShakti business - 3 full fiscal years (FY2022-23 through
FY2024-25, predating the 2026-07-12 import's FY2025-26 start) - was added to
**local `empdb` only**. 385 new parties, 3,377 invoices, 16,134 payments.
See the "ShivShakti historical import (2026-07-28)" section below for the
full details (party-matching approach, invoice-number scheme, what's still
out of sync with Neon). **Also note**: between the two imports, a smaller,
undocumented partial **FY2026-27** import (Apr-Jul 2026, 534 invoices/1,911
payments, `TALLY-S-FY2026-27-<vchNo>`) had already been applied to *both*
local and Neon before this session started - discovered while building the
2026-07-28 import's invoice-numbering scheme, not previously captured here.

**Major data event, 2026-07-30/31**: a **second local database, `empdb2`**,
was created alongside `empdb` (which remains completely untouched) and now
holds a from-scratch, PDF-verified FY2026-27 (1-Apr-26 to 28-Jul-26) ledger
for the same ShivShakti business - 252 parties, 755 invoices, 1,991
payment_entries, containing *only* this ledger's data (none of `empdb`'s
FY22-26 history was carried over). **The running local app is currently
pointed at `empdb2`, not `empdb`** (`spring.datasource.url` was switched -
see "Running it / connecting" below) - don't assume "local" means `empdb`
without checking the datasource URL first. Full details - including the
party-outstanding data-entry error and the party-ledger Dr/Cr display bug
found and fixed along the way - in the "FY2026-27 ledger + `empdb2`"
section below.

## Running it / connecting

- Maven, Java 17, Spring Boot 3.2.0. `mvn spring-boot:run` or run
  `EmployeeManagementApplication` from the IDE. Serves on `localhost:8080`.
- **Postgres, two local databases now** on `localhost:5432`, user `postgres`.
  Password is a plaintext local-dev credential in
  `src/main/resources/application.properties` (`spring.datasource.password`)
  - read it from that file rather than assuming it's unchanged. Connect via:
  `"C:\Program Files\PostgreSQL\13\bin\psql.exe" -h localhost -U postgres -d empdb`
  or `-d empdb2` (no GUI psql/pgAdmin on this machine - always use this CLI path).
  - `empdb` - the original, full-history database (FY22-23 through FY26-27,
    everything described in "Current data snapshot" below). Untouched since
    2026-07-30/31's `empdb2` work - always verify this assumption still holds
    (`SELECT count(*) FROM invoices` etc.) rather than trusting it blindly.
  - `empdb2` - created 2026-07-30/31, schema-cloned from `empdb` (`pg_dump`/
    restore) but with `parties`/`invoices`/`payment_entries`/`transaction_logs`/
    `audit_logs`/`notification_logs`/`admin_notifications`/`payment_receipts`
    wiped and rebuilt from *only* the FY2026-27 ledger data (`users`/
    `notification_settings`/`login_sessions` were left as cloned from `empdb`).
    See the "FY2026-27 ledger + `empdb2`" section below for the full story.
  - **`spring.datasource.url` currently points at `empdb2`** (switched
    2026-07-31) - check this value before assuming which DB a running local
    instance is actually using; it's easy to flip back to `empdb` by editing
    that one line + restarting.
- `spring.jpa.hibernate.ddl-auto=update` - schema auto-migrates new
  tables/columns on boot, but **never** rewrites existing CHECK constraints,
  indexes, or drops columns. See the enum gotcha below.
- **Two Postgres installs on this machine, roles now REVERSED from earlier
  notes**: the local server itself is now **v18** (confirmed 2026-07-28 -
  `pg_dump.exe` under `C:\Program Files\PostgreSQL\13` fails with `server
  version: 18.4 ... aborting because of server version mismatch`). Use
  `C:\Program Files\PostgreSQL\18\bin\pg_dump.exe`/`pg_restore.exe` for any
  local backup/restore. `psql.exe` under either the `13` or `18` folder still
  connects fine for plain queries/DDL (psql is lenient about talking to a
  newer server; pg_dump/pg_restore are not). Re-verify which version the
  server is on before assuming either of these - it may have been upgraded
  again since.
- To restart the app after a code change: find the PID on port 8080
  (`Get-NetTCPConnection -LocalPort 8080 -State Listen`), `Stop-Process -Force`
  it, then `nohup mvn -q spring-boot:run &` and poll
  `curl -sf http://localhost:8080/login` until it responds (don't `sleep`
  blindly - first boot after a clean compile is ~15-20s). The background-task
  "completed" notification some harnesses emit for the backgrounded `mvn`
  process is **not** a signal the server died - it fires immediately because
  the shell wrapper detaches; verify with `Get-NetTCPConnection`/`curl`, not
  the notification.
- Real secrets (WhatsApp access token, DataSeeder's initial admin/employee
  passwords) live in `src/main/resources/application-local.properties`,
  which is gitignored and not tracked - confirmed clean as of 2026-07-12
  (an earlier session found a live WhatsApp token briefly sitting directly in
  the tracked `application.properties`; that's been fixed by externalizing to
  the local file). Still worth a glance before any commit that touches
  `application.properties`, in case that regresses.
- **`seed.admin-password`/`seed.employee-password` only apply once, to an
  empty `users` table.** Since real data (including real users) was migrated
  in, `DataSeeder` never runs again - these values are stale/irrelevant for
  telling anyone's actual current password. Known-working local credentials:
  `admin`/`admin123` (unchanged since seed). Real employees' passwords are
  **not recoverable** - they're BCrypt hashes in the DB, one-way. If a user
  needs access, either ask them (if self-set) or use the existing
  `POST /admin/employees/{id}/reset-password` action on `/admin/employees`
  to set a new one - don't try to guess or derive the old one.
- **Template-only edits need a rebuild, not just a restart.**
  `spring.thymeleaf.cache=false` only disables Thymeleaf's own template
  cache, not the underlying classpath resource copy - editing a `.html`
  under `src/main/resources/templates` and just restarting the already-
  running jar/process serves the **stale** copy from `target/classes`.
  Always run `mvn -q compile` (which re-triggers `process-resources`) before
  restarting, for template-only changes.
- **Node.js path gotcha (Windows, recurring)**: passing a Bash-tool-style
  mount path (`/c/Users/...`) to `node -e "..."` resolves wrong (becomes
  `C:\c\Users\...` or gets mangled against cwd) - Node on Windows needs the
  native form (`C:\\Users\\...` inside a script, or a Windows-style path
  argument). Safest fix: write the script to a `.js` file with the Write
  tool and run `node script.js`, rather than inlining `-e` with a bash-style
  path.

## Data model

No real foreign keys between the financial tables - everything is joined by
matching a **string** party identifier. This is the single most important
thing to understand before touching invoices/payments/parties:

- **`Party.combined`** (unique, varchar 700) is the canonical party key -
  built as `name + '_' + gst` when GST is known, or bare `name` otherwise.
- **`Invoice.partyName`** and **`PaymentEntry.partyName`** are free-text
  columns expected to match some `Party.combined` value, but there is
  **no FK enforcing this**. `InvoiceService.getPartyOutstandingSummary()`
  unions party keys seen across both tables and left-joins to `Party` for
  phone/opt-in - a typo'd or hand-entered `partyName` that doesn't match any
  `Party.combined` silently becomes an "unlinked" outstanding entry (no
  phone, no WhatsApp reminder possible). Confirmed live examples in the DB:
  `payment_entries` rows for "Tata Industries Ltd", "Reliance Corp",
  "Infosys Solutions", "Wipro Technologies" have **no** matching row in
  `parties` at all (likely demo/seed data, but the same silent-mismatch
  failure mode applies to real typos).
- When adding parties/invoices/payments programmatically or via SQL, always
  match the exact `combined` string format, or better, go through
  `ExcelPartyService.ensureExists(combined)` / the existing controllers
  rather than hand-crafting the string.

### Tables (11, schema `public`)

| Table | Key columns | Notes |
|---|---|---|
| `users` | id, username(unique), password(BCrypt), full_name, email, role, active, created_at | `role` CHECK constraint: `ADMIN,EMPLOYEE,ACCOUNTANT,MANAGER` (MANAGER added 2026-07-18) |
| `parties` | id, name, gst, **combined(unique)**, **trailing_number**, total_amount, phone, whatsapp_opt_in | `total_amount` = sum from Excel import, not live-recomputed. `trailing_number` (added 2026-07-12): the party's ledger code from the source Tally system, e.g. `78` for `CHANDRJEET B. M (JAIGAHA) 78` - nullable, most of the original 85 parties don't have one |
| `invoices` | id, invoice_number(unique), invoice_date, party_name, amount, description, delivery_mode, transport_number, **sales_vch_no**, **bags**, **rate_per_bag**, **created_by** | `delivery_mode` CHECK: `TRUCK,SELF_PICKUP,TROLLEY` (only `TRUCK` used in current data). `sales_vch_no` (added 2026-07-12): Tally Sales Register voucher number, only populated for imported rows (`invoice_number` prefix `TALLY-S-<vchNo>` for those). `bags`/`rate_per_bag` (added 2026-07-15, nullable - the 1,355 Tally-imported rows have neither): on the Add Invoice form, admins/accountants/managers now enter number-of-bags + rate/bag instead of a raw amount; `amount` is computed server-side (`InvoiceService.createInvoice()`, `amount = ratePerBag × bags`) and is **not** independently editable - the form's Amount field is a disabled, JS-updated display only, never submitted. `created_by` (added 2026-07-18, nullable varchar(50) - null for pre-existing/imported rows): username of whoever added the invoice (admin/accountant/manager) - powers `InvoiceService.getInvoicesCreatedBy()`/`getInvoicesCreatedByOnDate()`, which is how a Manager's dashboard scopes to "their" invoices (there's still no FK, just a plain string) |
| `payment_entries` | id, party_name, amount, mode_of_payment, entry_date, remarks, edited, edited_by, edited_at, employee_id(FK→users), **receipt_vch_no** | `mode_of_payment` CHECK: `CASH,CHEQUE,BANK_TRANSFER,UPI,NEFT,RTGS,DD`. Only real FK in the whole schema is `employee_id`. `receipt_vch_no` (added 2026-07-12): Tally Receipt Register voucher number for imported rows - **not unique**, Tally itself ran two parallel voucher-number series that collide with each other |
| `transaction_logs` | id, action, entry_id(no FK, just a Long), employee_name, employee_username, party_name, amount, mode_of_payment, entry_date, remarks, performed_by, notes, performed_at | Audit trail, denormalized snapshot per event, not linked by FK |
| `notification_logs` | id, party_name, phone, outstanding_amount, status, error_message, triggered_by, sent_at | `status` CHECK: `SENT,FAILED,DRY_RUN`. `triggered_by` = `"SCHEDULER"` or `"ADMIN:<user>"`/`"ACCOUNTANT:<user>"` |
| `audit_logs` | id, action, entity_type, entity_id, party_name, amount, description, performed_by, performed_by_role, performed_at | Added 2026-07-18. Generic audit trail for Invoice/Party create-edit-delete (`transaction_logs` above still covers PaymentEntry actions on its own, unchanged). **Deliberately no CHECK constraint** on `action`/`entity_type` (plain Strings, no `@Enumerated`) - this table logs many action verbs and will keep growing, and a CHECK constraint would need a manual `ALTER TABLE` every time (see the enum gotcha below). |
| `notification_settings` | id(always 1, singleton row), daily_reminder_enabled, updated_by, updated_at | Controls whether the 5pm scheduler actually fires |
| `payment_receipts` | id, **payment_entry_id**(FK→payment_entries, unique), photo_data(**bytea**), content_type, latitude, longitude, captured_at | Added 2026-07-18. One row per entry that has a photographed receipt - deliberately a separate table, not columns on `payment_entries`, since that table is read on every cached ledger/summary query and a bytea column there would bloat all of them. `photo_data` **must** stay mapped via `@JdbcTypeCode(SqlTypes.VARBINARY)` + `columnDefinition="bytea"` - plain `@Lob` on a `byte[]` with Hibernate+Postgres silently maps to `oid` (large object) instead, which isn't auto-cleaned on row delete and doesn't play well with pooled/serverless Postgres connections (caught and fixed during initial implementation, before any real data existed). |
| `admin_notifications` | id, type, message, party_name, amount, triggered_by, triggered_by_role, source_type, source_id, is_read, created_at, read_at | Added 2026-07-18. `type` CHECK: `COLLECTION_ADDED,INVOICE_ADDED` - same enum-CHECK-constraint gotcha as below applies to any future new type. Powers the admin activity feed/bell (see its own section). |
| `login_sessions` | id, username, full_name, role, login_at, logout_at | Added 2026-07-28. One row per login; `logout_at` stays null for still-open sessions (or ones where the app restarted before a real logout - no cleanup job for these). Populated entirely from `SecurityConfig`'s auth success/logout handlers, not any controller. Powers the ADMIN-only `/admin/login-history` report - see its own section below. |

### Entities/enums (`entity/` package)

- `User.Role`: `ADMIN, EMPLOYEE, ACCOUNTANT, MANAGER`
- `Invoice.DeliveryMode`: `TRUCK("Truck"), SELF_PICKUP("Self Pickup"), TROLLEY("Trolley")`
- `PaymentEntry.ModeOfPayment`: `CASH, CHEQUE, BANK_TRANSFER, UPI, NEFT, RTGS, DD`
- `NotificationLog.Status`: `SENT, FAILED, DRY_RUN`
- `User` --1:N--> `PaymentEntry` (real JPA relationship, `employee_id` FK)
- Everything else joins via `combined`/`party_name` string matching (see above)
- `Party.trailingNumber`, `Invoice.salesVchNo`, `PaymentEntry.receiptVchNo`
  (added 2026-07-12): plain nullable String fields, no special annotations
  needed beyond `@Column` since the underlying columns already existed
  (added via manual `ALTER TABLE` before the entity fields were added -
  `ddl-auto=update` validated against them cleanly on next boot, no schema
  drift). Surfaced in DTOs: `PartyOutstandingDTO.trailingNumber`,
  `InvoiceDTO.Response.salesVchNo`, `PartyLedgerDTO.Entry.salesVchNo`/
  `.receiptVchNo`, `PartyLedgerDTO.Response.trailingNumber`,
  `PaymentEntryDTO.Response.receiptVchNo` - and in templates:
  `admin/parties.html` (Trailing No. column), `admin/invoices.html`
  (Sales Vch No. column + Trailing No. column in the outstanding table),
  `admin/party-ledger.html` and `admin/full-ledger.html` (both voucher
  columns), `admin/entries.html` (Receipt Vch No. column).

## Known gotcha: enum + Postgres CHECK constraints

Adding a new value to a `@Enumerated(EnumType.STRING)` enum (e.g. a past
`User.Role.ACCOUNTANT` addition) does **not** get picked up by
`ddl-auto=update` - Postgres's auto-generated CHECK constraint
(`users_role_check` etc.) is static SQL baked in at table-creation time and
needs a manual fix:

```sql
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check;
ALTER TABLE users ADD CONSTRAINT users_role_check CHECK (role IN ('ADMIN','EMPLOYEE','ACCOUNTANT'));
```

Same applies to `payment_entries_mode_of_payment_check`,
`invoices_delivery_mode_check` (if it exists), `notification_logs_status_check`
- any table with an `@Enumerated(STRING)` column, whenever its Java enum
gains a new constant.

## Services (`service/` package)

- **`ExcelPartyService`** - imports party master data from Excel (Apache POI)
  on startup (`party.import.on-startup`, default file
  `Party_wise_Sales_Summary.xlsx`) or via re-upload
  (`/api/parties/upload-import`). Auto-detects header row, GSTIN column,
  amount column by text heuristics; accumulates per-party totals across
  sheets. `ensureExists(combined)` lazily creates a `Party` row when a new
  name is typed into a form. `cleanupInvalidEntries()` strips junk rows
  (bare numbers, stray header text). **`party.import.on-startup` is currently
  `false`** (flipped 2026-07-31, was `true` before) - it kept re-adding
  parties from that old seed Excel file into `empdb2` on every restart (3
  parties it found missing there, since `empdb2` doesn't have `empdb`'s
  original GST-registered party set - see "FY2026-27 ledger + `empdb2`"
  below). This is a global property, not scoped to either database - if
  the app is ever pointed back at `empdb`, that file still won't re-sync
  there either unless this is flipped back to `true`.
- **`InvoiceService`** - CRUD + the core financial logic:
  `getPartyOutstandingSummary()` (invoiced − paid per party, the central
  number everywhere - only returns parties with ≥1 invoice or payment, so
  parties with zero transactions never appear), `getPartyLedger()`
  (chronological statement of account w/ running balance, **newest first**,
  single party), `getInvoicesByDateRange(from, to)` (added 2026-07-12,
  either bound nullable), `getAllPartyLedgers()` (added 2026-07-12 - every
  party's statement in one call, **oldest first** per party unlike
  `getPartyLedger()`, built from 2 bulk queries grouped in memory rather
  than N+1 per-party queries - powers `/admin/full-ledger`),
  `getAgingReport()` (0-30/31-60/61-90/90+ buckets via FIFO invoice-vs-payment
  allocation), `getPartyPaymentBehavior()` (labels: Paid Up/Regular/New/Slow/
  Very Slow/Chronic Late/Credit/Clear), `getExecutiveSummary()` (dashboard:
  totals, top 10 defaulters, 6-month trend).
- **`WhatsAppService`** - sends reminders via Meta WhatsApp Business Cloud
  API. Mode-gated: `LOG_ONLY` (simulated, code default) vs `LIVE` (real
  send). **`application.properties` currently sets `whatsapp.mode=LIVE`**,
  overriding the safe default - worth double-checking this is intentional
  before any test run that iterates real parties. Note: the template call
  currently sends no parameters (the parameter-substitution block is
  commented out), so live sends likely don't actually inject
  party/amount/date into the template text despite what the class Javadoc
  implies - verify against an actual Meta response before relying on
  personalized reminder content.
- **`OutstandingNotificationService`** - orchestrates reminders:
  `sendDailyReminders()`, `resendFailed()` (retries only recent `FAILED`
  sends within 30 days), `getStats()`, `getCollectionsWorklist()`,
  `isDailyReminderEnabled()`/`toggleDailyReminder()` (singleton settings row).
- **`PaymentEntryService`** - employee CRUD with rules (employees: only
  today's own entries; admins: any entry, remarks mandatory on edit); every
  mutation writes a `TransactionLog`.
- **`ExportService`** - Excel (POI)/PDF (OpenPDF)/CSV export of entries and
  audit logs.
- **`UserService`** - employee/accountant account management (blocks
  creating ADMIN via this path), BCrypt password handling.

## Performance: caching + compression (added 2026-07-15)

- **`config/CacheConfig.java`** - Caffeine in-memory cache (single-instance
  app, no Redis needed), 5-minute TTL / 500-entry cap, six named caches:
  `partyOutstanding`, `allPartyLedgers`, `partyLedger`, `agingReport`,
  `paymentBehavior`, `invoiceStats` (constants `PARTY_OUTSTANDING` etc. in
  that class - always reference the constant, not a string literal).
- `InvoiceService`'s read-heavy financial summary methods
  (`getPartyOutstandingSummary`, `getInvoicePageStats`, `getPartyLedger`,
  `getAllPartyLedgers`, `getAgingReport`, `getPartyPaymentBehavior`) are all
  `@Cacheable`. **Every** mutation in `InvoiceService` and
  `PaymentEntryService` (create/update/delete, both admin and employee
  paths) carries `@CacheEvict(cacheNames = {...all six...}, allEntries =
  true)` - this is the actual staleness guard, the 5-minute TTL is just a
  backstop. **If you add a new invoice/payment mutation method, it needs
  this same `@CacheEvict` or the outstanding figures will silently go
  stale** until the TTL expires.
- `server.compression.enabled=true` (gzip) in `application.properties`,
  plus a lazy-loading restructure of `/admin/full-ledger`: the main page
  renders only party names/totals, and each party's transaction table is
  fetched on demand via a Thymeleaf fragment
  (`AdminController.fullLedgerPartyDetail()` →
  `admin/full-ledger :: partyTable`, `toggleEntries()` JS in the template).
  This was the fix for the page taking 12-19s / 8.2MB on Render's free
  0.1-vCPU tier - now ~289KB initial load. **If `/admin/full-ledger` (or
  any other page) gets heavy again, this fragment-on-demand pattern is the
  established fix, not just "add more caching."**

## Deployment (Render + Neon, added 2026-07-15)

- **Live URL**: `https://employeemanagement-q6h3.onrender.com`. Render free
  web service (`render.yaml` blueprint, `runtime: docker`, 0.1 vCPU) backed
  by Neon serverless Postgres (`us-east-1`, requires `sslmode=require` and
  an SNI-capable client - use `psql` v14+ if connecting manually, not the
  older v13 client tools that live alongside v18 on this machine).
  Migrated real data (not a fresh seed) - same Tally-imported invoices/
  payments/parties as local, kept in sync only by manually re-running any
  local DB change against Neon too (no automatic sync).
- **Deploy = `git push origin main`.** Render auto-redeploys on push to
  `main`; poll `https://employeemanagement-q6h3.onrender.com/login` (or
  another public route) after a push, first cold build can take a few
  minutes. Since the DB is Neon (shared, real), never run *any* write
  against it (even a test row) without asking the user first for that
  specific action, even if a similar write was already approved earlier in
  the same session - this was a hard guardrail block earlier and is the
  durable norm now. When a test write to Neon is approved, verify then
  clean it up (`DELETE ...`) immediately, same discipline as local testing.
- **`WHATSAPP_MODE=LOG_ONLY` on Render** (`render.yaml`) - deliberately
  different from local's `LIVE` in `application.properties`, so the
  deployed instance never sends real WhatsApp messages to real parties by
  accident.
- **Cold start**: free Render instances sleep after ~15 min idle; first
  request after that is slow (the container has to boot). **Keep-alive is
  NOT a GitHub Actions workflow** (one was added 2026-07-18, then removed
  2026-07-19, commit `544e940` - GH Actions' schedule trigger doesn't
  reliably honor sub-hourly cron, actual gaps averaged ~2 hours instead of
  10 minutes, so it wasn't actually working). Replaced by an **external
  cron-job.org ping** hitting `GET /health` every ~10 min, 6 AM-10 PM IST -
  this lives entirely outside the repo (no file here to check/edit), so
  verify/adjust it by logging into cron-job.org directly, not by looking
  for a workflow file. The instance is still *deliberately* allowed to
  sleep overnight/outside that window.
- **Known deployment gotchas already hit and fixed** (useful if a future
  redeploy breaks the same way):
  - Render can misdetect the runtime as Node if the service was created
    before `Dockerfile`/`render.yaml` were pushed - fix is deleting and
    recreating the service via **New → Blueprint** (not "Web Service"), so
    it reads `render.yaml` and sets `runtime: docker` from the start.
  - "Dockerfile not found" even with `dockerfilePath`/`dockerContext` set
    correctly in `render.yaml` - check the Render service's **Settings →
    Build → Root Directory** is blank, not `src` or anything else.
  - `SPRING_DATASOURCE_URL` on Render must be prefixed `jdbc:postgresql://`
    - pasting Neon's raw connection string (`postgresql://...`, no `jdbc:`)
    causes `Driver org.postgresql.Driver claims to not accept jdbcUrl`.
  - **A manual "Rollback" from the Render dashboard appears to pause
    auto-deploy** (discovered 2026-07-28/29) - after rolling back to an
    earlier deploy to unblock production during an incident, a subsequent
    `git push origin main` did **not** trigger a new auto-deploy; the
    dashboard kept showing the rolled-back commit as live even though GitHub
    had newer commits. Fix was a manual **"Deploy latest commit"** click from
    the Render dashboard. **After any manual rollback, don't assume the next
    `git push` will auto-deploy** - check the dashboard and manually trigger
    if needed.
- Secrets (Neon connection string, WhatsApp token, seed passwords) live
  only as Render env vars (`sync: false` in `render.yaml`) - never hardcode
  them into this skill, the repo, or any committed file.

## Scheduled jobs

Exactly one: `NotificationScheduler.runDailyReminders()`, cron
`0 0 17 * * *` (5pm daily), gated on the `notification_settings` singleton
row (admin-toggleable at `/admin/notifications`).

## Endpoints (grouped by controller)

- **`AuthController`**: `/login`, `/` (redirect), `/health` (permitAll,
  keep-alive target - see its section above).
- **`EmployeeController`** (`/employee/**`, role EMPLOYEE): dashboard, add/
  edit/delete own today-only entries, list, history.
- **`ManagerController`** (`/manager/**`, role MANAGER - added 2026-07-18):
  dashboard (add invoice + today's invoices), all-invoices list. See its
  own section above.
- **`AdminController`** (`/admin/**`, role ADMIN; several sub-paths widened
  to ADMIN+ACCOUNTANT): dashboard, employees CRUD, entries CRUD+export
  (excel/pdf/audit-csv) - `/admin/entries` supports `from`/`to`/`employeeId`
  date-range filtering, history, **login-history** (`/admin/login-history`,
  added 2026-07-28, ADMIN-only - see its own section below), **invoices**
  (`/admin/invoices` - also supports `from`/`to` date-range filtering on the
  Invoice List since 2026-07-12, independent of the lifetime stats bar and
  the notification log section which stay unfiltered; `GET`/`POST
  /admin/invoices/{id}/edit` added 2026-07-28, ADMIN+ACCOUNTANT, any
  invoice - see "Invoice edit capability" section below), **ledger**
  (`/admin/ledger?partyName=`,
  single party, newest-first - has an "➕ Add Payment" tab, see the
  ACCOUNTANT role section above), **full-ledger** (`/admin/full-ledger`, added
  2026-07-12 - every party's statement on one page, oldest-first per party,
  mirrors the ShivShakti Excel workbook's "Party Ledger" sheet layout, has a
  client-side search box filtering visible party sections), **aging**
  (`/admin/aging`), **parties CRUD** (`/admin/parties*`, including
  phone/opt-in toggle), **notifications** (`/admin/notifications`,
  send/toggle-schedule/resend-failed), **employee-collections**
  (`/admin/employee-collections` - stays aggregate-only per-employee
  totals; drilling into one employee's individual entries is a "View
  Entries" link to the existing filtered `/admin/entries?employeeId=X`
  page rather than an inline expandable/dropdown row - chosen because it
  reuses the existing entries page's search/export instead of duplicating
  that UI), executive dashboard, collections worklist + one-off remind,
  payment-behavior analytics.
- **`PartyController`** (`@RestController /api/parties`): search
  (`GET /api/parties`, any authenticated role), structured suggest
  (`GET /api/parties/suggest`), import/cleanup/upload-import (ADMIN/
  ACCOUNTANT only). Note: CSRF is explicitly disabled for `/api/parties/import`
  and `/api/parties/cleanup` but **not** for `/api/parties/upload-import` -
  check this is intentional if that upload endpoint ever gets called from a
  non-browser client.

Views are server-rendered Thymeleaf under `src/main/resources/templates/`
(`admin/*.html`, `employee/*.html`), one template per controller method
above, no separate frontend/SPA.

## ACCOUNTANT role gotcha: admin/* templates aren't uniformly role-aware (2026-07-17)

`SecurityConfig`'s URL matcher (`/admin/**` → `hasAnyRole('ADMIN','ACCOUNTANT')`) only
gates *access*, not what the page *shows*. Individual `admin/*.html` templates each
decide their own sidebar/nav visibility via `sec:authorize`, and that decision was
made independently per template - some (`invoices.html`, `party-ledger.html`,
`employee-collections.html`) were built role-aware from the start (dynamic
`${adminName}`, `sec:authorize="hasRole('ADMIN')"` on admin-only nav links, a
separate `🧮 Accountant` badge). Others (`entries.html`, as of before this fix) were
built when the page was still ADMIN-only and hardcoded `"Administrator"` / `"👑 Admin"`
with **no** gating on any nav link at all - so once a controller method's
`@PreAuthorize` was widened to let ACCOUNTANT in, the accountant would see themselves
mislabeled as Admin with the full admin nav (Dashboard/Executive/Employees/Audit Log)
exposed. **When widening any `admin/*` endpoint to ACCOUNTANT, check that specific
template's sidebar for hardcoded identity/nav - don't assume the URL-level
`hasAnyRole` guarantees a correct-looking page.** Also relatedly:
`AdminController`'s class-level `@PreAuthorize("hasRole('ADMIN')")` means every method
defaults to ADMIN-only unless it carries its own `@PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")`
override - `GET /admin/entries` was missing this override entirely until fixed (the
`employee-collections.html` → "View Entries" link pointed accountants straight into
a 403).

Also as of this date, ACCOUNTANT (not just ADMIN) can add a payment entry directly
from a party's ledger: `/admin/ledger?partyName=...` now has an "➕ Add Payment" tab
(alongside the existing "📒 Statement of Account" tab) for recording a collection
that's missing from the statement - e.g. a discrepancy caught while reviewing it.
Backed by `PaymentEntryService.createEntryByStaff()` / `POST /admin/ledger/add-payment` -
deliberately *not* routed through the employee day-entry flow: unlike
`createEntry()` (employee path, forced to today's date), this allows **any** date,
and remarks are mandatory (mirrors the mandatory-remarks rule on admin edits) so the
reason for the out-of-band entry is on record. The `employee_id` FK on the resulting
row points at whichever admin/accountant added it (same convention as the Tally
import's placeholder `employee_id=admin` rows), so `employeeName`/`employeeUsername`
in `PaymentEntryDTO.Response` is how you can tell an entry was staff-added rather
than logged by the employee it might be filed under a party for.

## MANAGER role (added 2026-07-18, edit/delete added 2026-07-28)

A fourth role alongside ADMIN/EMPLOYEE/ACCOUNTANT. Originally scoped to only
adding/viewing invoices (mirrors the EMPLOYEE dashboard's "add today's stuff
from your phone" UX); as of 2026-07-28 can also add/edit/delete **collections**
(payment entries, added 2026-07-26 - see git log) and now edit/delete their
**own** invoices too (any date, not just today - see "Invoice edit
capability" section below for the shared service method and the
Admin/Accountant side of the same feature).

- New `ManagerController` (`/manager/**`, `@PreAuthorize("hasRole('MANAGER')")`,
  class-level - unlike `AdminController` there's no need for per-method
  overrides since every method in this controller is manager-only anyway):
  `GET /manager/dashboard` (add-invoice form + "Today's Invoices" list,
  scoped via `InvoiceService.getInvoicesCreatedByOnDate(username, today)`),
  `POST /manager/invoices/add`, `GET /manager/invoices` (all-time, this
  manager's invoices only, via `getInvoicesCreatedBy(username)`),
  `GET`/`POST /manager/invoices/{id}/edit` and `POST /manager/invoices/{id}/delete`
  (added 2026-07-28 - ownership enforced by comparing `invoice.createdBy` to
  `auth.getName()` **in the controller**, not the service layer, since
  Admin/Accountant's edit of the same shared `InvoiceService.updateInvoice()`
  method is deliberately unrestricted to any invoice).
- **Dashboard invoice tables gained a 👁 View / ✏️ Edit / 🗑 Delete actions
  column** (2026-07-28) - View opens a small client-side modal (no server
  round-trip, populated from `data-*` attributes already rendered per row)
  showing Bags/Rate/Amount/Description/Transport No., since the compact
  Today's/Month invoice tables never had room for those columns (unlike
  `manager/invoices.html`, which shows them inline).
- **Own PWA manifest**: `static/manager-manifest.json` (`start_url:
  /manager/dashboard`, separate from `static/manifest.json`'s
  `/employee/dashboard`) - **necessary**, not cosmetic: if a Manager
  installed the shared employee manifest to their home screen, tapping the
  icon would hit `/employee/dashboard`, and since `/employee/**` requires
  `hasRole('EMPLOYEE')` specifically (not `hasAnyRole`), a Manager would get
  a 403 on their own home-screen shortcut. `SecurityConfig`'s `permitAll()`
  list needs `/manager-manifest.json` alongside `/manifest.json` for the
  same reason `/manifest.json` needs to be there (see PWA section below).
- `SecurityConfig`: `/manager/**` → `hasRole('MANAGER')`; login
  `successHandler` has a `ROLE_MANAGER` branch → `/manager/dashboard` (added
  **before** the final `else` which still falls through to
  `/employee/dashboard` - a MANAGER user would otherwise silently redirect
  into a role they don't have and immediately 403).
- `UserService.getAllManagers()` (`findByRole(MANAGER)`) powers a "Managers"
  table on `admin/employees.html` (same enable/disable/reset-password
  actions as the existing Accountants table) and a `MANAGER` option in that
  page's role `<select>`. `UserService.createEmployee()` needed **no** code
  change - it only explicitly blocks creating `ADMIN`, so `MANAGER` passed
  straight through once the enum + form option existed.
- **Invoice auto-numbering** (`InvoiceService.getNextInvoiceNumber()`):
  suggests `INV-<year>-<seq>` (zero-padded to 3 digits, sequence resets each
  year, derived from the max existing suffix under that year's prefix) and
  pre-fills the Invoice Number field on both `admin/invoices.html` and
  `manager/dashboard.html` - field stays a normal editable text input, not
  locked. Two people grabbing the same suggestion at the same instant just
  hits `createInvoice()`'s existing `existsByInvoiceNumber` check (see
  concurrency note below) rather than silently colliding.
- Verified end-to-end (login redirect, 403 on `/admin/**` and
  `/employee/**`, add-invoice, `created_by` correctly stamped, manifest
  `start_url`) via a temporary test `MANAGER` account, deleted after.

## Party autocomplete: name/trailing-number search, no GST (fixed 2026-07-18)

- **Server-side matching** (`ExcelPartyService.search()` /
  `.searchStructured()`, backing `GET /api/parties` and
  `GET /api/parties/suggest`) used to match only against `Party.combined`
  (`name + '_' + gst`) via `PartyRepository.findTop50ByCombinedContainingIgnoreCase`
  - which meant a GST substring could accidentally surface a party, and
  `trailingNumber` wasn't searchable at all despite being displayed
  elsewhere in the app. Now uses a dedicated
  `findTop50ByNameContainingIgnoreCaseOrTrailingNumberContainingIgnoreCase`
  query - matches name OR trailing number, **never** GST.
  `PartySuggestionDTO` gained a `trailingNumber` field so the client can
  show why a result matched.
- **Two pages do their own client-side re-filter** on top of the server
  result rather than trusting server matching directly:
  `admin/invoices.html` and `manager/dashboard.html` both prefetch the
  *entire* party list once (`fetch('/api/parties/suggest?q=...')`) into a
  JS array, then filter it locally on every keystroke (for instant, no-
  round-trip suggestions). Their own filter predicate had the identical
  GST-leak bug (`p.combined.includes(q)` re-introduces GST since `combined`
  contains it) and had to be fixed the same way - don't assume fixing the
  server query alone covers these two pages.
- **Silent 20-result cap bit us twice in the same investigation**: that
  prefetch fetch omitted `&limit=`, which silently defaults to 20
  server-side (`PartyController.suggest`'s `@RequestParam(defaultValue =
  "20")`) - so only the first 20 of 244 parties were ever cached, and
  anything typed only searched within those 20. Fixed by adding
  `&limit=5000` to both prefetch calls. **Any future "prefetch everything
  once" pattern against `/api/parties/suggest` needs an explicit large
  `limit`** - the endpoint's default is tuned for real per-keystroke
  searches (where 20 results is plenty), not bulk fetches.
- The other three suggestion UIs (`employee/dashboard.html`,
  `employee/edit-entry.html`, `admin/edit-entry.html`) are purely
  server-driven (re-fetch on every keystroke, no client cache) - they only
  needed their dropdown's secondary line switched from showing `gst` to
  showing `trailingNumber`, no matching-logic changes.
- Party count at time of this fix: 244 (`SELECT COUNT(*) FROM parties`) -
  re-query if this matters, it grows via `ExcelPartyService.ensureExists()`
  whenever a new party name is typed into any form.

## Party ledger: Add Invoice tab + PDF/CSV export (added 2026-07-18)

`admin/party-ledger.html` (`GET /admin/ledger?partyName=`) now has a third tab
alongside "Statement of Account" / "Add Payment":

- **🧾 Add Invoice tab** - mirrors `admin/invoices.html`'s Add Invoice form
  (bags × rate auto-calc via `recalcLedgerInvoiceAmount()`, same field set)
  but party is fixed/disabled (hidden `partyName` pre-filled from
  `ledger.partyName`, matching the existing Add Payment tab's pattern).
  Posts to `POST /admin/ledger/add-invoice` (mirrors `addLedgerPayment` -
  redirects back to this same ledger page with a flash message either way,
  not a full page re-render on validation error). Publishes the same
  `InvoiceCreatedEvent` as every other invoice-creation path (single
  `InvoiceService.createInvoice()` call), so Observer-pattern notifications
  and self-notify suppression apply here too - confirmed via direct
  DB check (curl-based, not browser UI - see gotcha below), no notification
  row created when ADMIN itself adds the invoice this way.
- **Export (PDF/CSV), date-range filterable** - two date inputs + Export
  PDF/Export CSV buttons on the Statement of Account tab
  (`exportLedger(format)` JS builds `GET /admin/ledger/export?partyName=&
  format=&from=&to=`). Filtering happens by taking the *already-fully-computed*
  `ledger.entries` (running balance correct over the party's whole history)
  and filtering that list to `[from, to]` inclusive - **never** recompute the
  running balance from zero within just the filtered window, or the Balance
  column would be wrong. `ExportService.exportPartyLedgerToPdf()`/
  `exportPartyLedgerToCsv()` follow the exact column set shown on-screen
  (Date/Type/Reference/Sales Vch/Receipt Vch/Description/Debit/Credit/Balance).

**Two real bugs caught while building this (both fixed before commit):**
- A `<script>` block using Thymeleaf inline JS (`[[${ledger.partyName}]]`)
  **must** have `th:inline="javascript"` on the `<script>` tag itself, or
  Thymeleaf skips proper JS-string quoting and emits the bare unescaped value
  (`encodeURIComponent(HCL Ltd)` - a `SyntaxError`, not just wrong data). Any
  future inline-JS-with-Thymeleaf-expression script tag needs this attribute.
- Binding `<input type="date" th:field="*{invoiceDate}">` directly to a
  `LocalDate` renders via Spring's default locale-based formatter
  (`18/07/26`), which is **not** valid HTML5 date-input format (needs strict
  ISO `yyyy-MM-dd`) - the browser silently ignores it and shows the field
  empty. This is exactly why `employee/dashboard.html`'s date field already
  uses a disabled display + separate hidden ISO-formatted input instead of
  binding `th:field` straight to a date input - same fix applied here
  (`th:value="${#temporals.format(newInvoice.invoiceDate,'yyyy-MM-dd')}"`
  instead of `th:field`). **Any new `<input type="date">` bound to a
  pre-filled `LocalDate` needs this pattern, not bare `th:field`** - only
  matters when the field is pre-filled server-side; a blank date field posted
  by the user (like `admin/invoices.html`'s main Add Invoice form, which
  never pre-fills) doesn't hit this.

**Testing gotcha discovered this session**: browser-automation form
submission for this app has been unreliable (clicks silently not
registering, stale cached page loads producing phantom console errors from
before a fix). A curl-based flow - fetch page → extract `_csrf` (**use the
first match only**, the login page and others emit the same hidden CSRF
input more than once on the page) → POST with cookie jar → verify via direct
DB query - was faster and more reliable for this kind of write-flow
verification than driving the real browser end-to-end.

## Receipt photo + geo-tagging (added 2026-07-18)

Employees can attach a photo of the paper receipt they hand a party after
collecting money, geo-tagged at capture time. Deliberately scoped to the
**employee day-entry flow only** (`employee/dashboard.html` → `POST
/employee/entries/add`) - it's the literal "gave a receipt after an in-person
collection" moment. The admin/accountant "Add Payment from Ledger" backfill
path (`createEntryByStaff`) does **not** get photo capture (it's a
reconciliation entry, not an in-person collection) but still triggers
notifications (see below).

- **Optional, not mandatory** - a photo/geo problem never blocks saving the
  entry. `EmployeeController.addEntry()` calls
  `PaymentEntryService.attachReceipt()` in a try/catch **after** `createEntry()`
  already succeeded; failures are logged and swallowed.
- **Client-side compression before upload** (`employee/dashboard.html`,
  vanilla JS, no library): the file input (`accept="image/*"
  capture="environment"` - opens the phone's rear camera directly on mobile)
  triggers a canvas-based downscale to max 1600px + JPEG quality 0.7 before
  the `<input type="file">`'s `FileList` is replaced via `DataTransfer`. Keeps
  `payment_receipts.photo_data` rows small and uploads fast on poor field
  connectivity - a multi-MB raw camera photo becomes ~15-20KB.
- **Geo-tag**: `navigator.geolocation.getCurrentPosition()` fires on file
  selection, populates hidden `latitude`/`longitude` fields. Permission
  denied/unavailable → fields stay blank, entry still saves (see optional
  above).
- **Viewing**: `GET /admin/entries/{id}/receipt` (ADMIN + ACCOUNTANT) streams
  the bytes with stored `content_type`. `admin/entries.html` shows a 📷 icon
  next to the Receipt Vch No. column for rows that have one, linking straight
  to that endpoint (opens in a new tab, no lightbox). Whether a row has a
  receipt is resolved via `PaymentEntryService.withReceiptFlags()` - a single
  **batched** query (`PaymentReceiptRepository.findPaymentEntryIdsWithReceipt`)
  called once per list render, not per-row - avoid reintroducing N+1 here if
  this page's query methods change. Party ledger / full-ledger receipt display
  is a deliberate fast-follow, not built yet.

## Admin notification system - Observer pattern via Spring events (added 2026-07-18)

Admin gets an in-app notification when EMPLOYEE/ACCOUNTANT logs a collection,
or MANAGER/ACCOUNTANT adds an invoice. **In-app only** (bell + activity feed),
no WhatsApp/email push - that was an explicit scope decision.

- **Spring's `ApplicationEventPublisher`/`@TransactionalEventListener` *is*
  the Observer pattern** here - no hand-rolled `Observable` interface.
  `PaymentEntryService.createEntry()`/`createEntryByStaff()` and
  `InvoiceService.createInvoice()` (the single method shared by
  Admin/Accountant/Manager invoice creation) each publish a
  `PaymentEntryCreatedEvent`/`InvoiceCreatedEvent` (`event/` package, plain
  POJOs carrying the entity + actor username) right after `.save()`. The
  publishers know nothing about who's listening or why - true Observer
  decoupling.
- **`AdminNotificationListener`** (`service/` package) is the one subscriber:
  looks up the actor's role via `UserRepository`, **skips creating a
  notification if the actor is ADMIN** (no self-notifications - this is where
  the actual "who should be notified" policy lives, not in the publishers).
  Otherwise builds a human-readable message and saves an `AdminNotification`
  row.
- **`@TransactionalEventListener(phase = AFTER_COMMIT)` alone was not
  enough** - a real bug hit during initial build: by the time an
  AFTER_COMMIT callback runs, the *original* transaction's resources are
  still bound to the thread but already committed, so a plain
  `repository.save()` inside the listener silently joined that dead
  transaction and never actually persisted (no exception, the log line even
  said "created" - only a direct DB query revealed nothing was there). Fix:
  both listener methods carry `@Transactional(propagation =
  Propagation.REQUIRES_NEW)` so each gets its own fresh, independently-
  committing transaction. **Do not remove this annotation** as a "simplification."
- **Endpoints**: `GET /admin/notifications/unread-count` (tiny JSON
  `{"count":N}`, ADMIN-only) polled every ~45s by the bell;
  `GET /admin/activity` renders the full feed (newest-first) and marks
  everything read as a side effect of visiting - simplest v1 read-state
  model, no per-row mark-as-read UI.
- **Bell rollout**: same bell+badge snippet (`.notif-bell`/`.notif-badge` CSS,
  a small poll script) copy-pasted into the topbar of **all 18**
  `admin/*.html` templates, mirroring the established convention from the
  PWA/mobile sidebar rollout ("copy the exact block from one of these rather
  than reinventing it"). If adding a new admin page, copy this pattern too.
- Accountants do **not** see the bell/activity feed (`/admin/activity` and
  the unread-count endpoint are `hasRole('ADMIN')` only, not the usual
  `hasAnyRole('ADMIN','ACCOUNTANT')` widening) - matches the original request
  ("admin should be notified"), not extended to accountants.

## Unified Audit Log (added 2026-07-18)

`admin/history.html` (`GET /admin/history`, ADMIN-only) used to show only
`transaction_logs` (PaymentEntry create/update/delete). It now shows a merged,
chronologically-sorted feed of that **plus** Invoice and Party
create/update/delete, driven by `AuditLogService.getUnifiedFeed()` which
combines `TransactionLogRepository` + the new `AuditLogRepository` into one
`List<AuditFeedDTO.Response>` sorted by the raw `performedAt` timestamp (not
the formatted display string - string-sorting dates would be wrong).

- **Logged**: invoice create (`InvoiceService.createInvoice()` - covers all
  three call sites: admin/invoices.html, the ledger's Add Invoice tab, and
  Manager's invoice form, since they all funnel through this one method) and
  delete (`InvoiceService.deleteInvoice()`, which now takes a `performedBy`
  param - **signature changed**, only one call site, `AdminController.deleteInvoice`,
  already updated to pass `auth.getName()`); party create/update/delete
  (`AdminController.addParty`/`editParty`/`updatePartyPhone`/`deleteParty` -
  these have **no service layer**, they call `partyRepository` directly in
  the controller, so the audit calls live right there via a small
  `logPartyAudit()` helper, not in a `PartyService` that doesn't exist).
- **Not logged** (deliberately out of scope): the bulk `ExcelPartyService
  .cleanupInvalidEntries()` path (`POST /api/parties/cleanup`, no
  `@PreAuthorize` at all currently - worth a look if this matters) and the
  Excel-import party creation path (`ExcelPartyService.ensureExists()`) -
  that fires constantly as a side effect of typing any new party name into
  any form, logging it would be pure noise, not a deliberate audit-worthy action.
- **Role is captured at the time of the action** (`AuditLog.performedByRole`,
  looked up once via `UserRepository`/`UserService.getUserByUsername()` at
  the moment of logging) - correct even if the user's role changes later.
  Legacy `transaction_logs` rows have no role captured historically, so the
  merged feed shows a role badge only for the new Invoice/Party rows; existing
  PaymentEntry rows just show the username, same as before.
- **Template**: added a Category column (💰 Payment Entry / 🧾 Invoice /
  🏢 Party) and null-guards on Employee/Amount/Mode/Entry Date (those only
  apply to some categories) - the existing search box, CREATE/UPDATE/DELETE
  filter buttons, and summary-pill counts needed **zero JS changes**, since
  `AuditLog.action` deliberately reuses the exact same `CREATE`/`UPDATE`/`DELETE`
  vocabulary as `TransactionLog.action`.
- **`AuditLog.action`/`entity_type` are plain Strings, not `@Enumerated`** -
  deliberate, to sidestep the enum/CHECK-constraint gotcha below for a table
  that's expected to grow with more logged action types over time.

## Invoice edit capability (added 2026-07-28)

Before this date, invoices could be created and (Admin/Accountant only)
deleted, but **never edited by anyone** - `InvoiceService` had no update
method at all. Added `InvoiceService.updateInvoice(id, request, performedBy)`,
mirroring `createInvoice()`'s validation (re-checks `invoice_number`
uniqueness, excluding self; recomputes `amount = ratePerBag × bags`; same
`@CacheEvict` set) and `logInvoiceAudit()` convention.

- **Admin/Accountant**: `GET`/`POST /admin/invoices/{id}/edit`
  (`@PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")`, matching the existing
  delete endpoint) - can edit **any** invoice, no ownership check. New
  `admin/edit-invoice.html` template (mirrors `admin/edit-entry.html`'s
  shell), Edit button added next to the existing 🗑 on `admin/invoices.html`.
- **Manager**: same shared service method, but scoped to invoices they
  personally created (`invoice.createdBy == auth.getName()`), **any date**
  (a deliberate, more permissive choice than the "today only" rule payment-
  entry edits use) - see the MANAGER role section above for the endpoints.
- **Every edit writes an `audit_logs` `UPDATE` row** via the existing
  `logInvoiceAudit()` helper - shows up automatically on `/admin/history`
  with **zero template changes needed there**, since that page already
  handles arbitrary `CREATE`/`UPDATE`/`DELETE` actions generically.
- **Real bug hit and fixed during this build**: `InvoiceDTO.Response.deliveryMode`
  holds the enum's **display name** ("Truck"), not the constant name
  ("TRUCK") - `toResponse()` maps it via `.getDisplayName()`. Pre-filling the
  edit form with `Invoice.DeliveryMode.valueOf(invoice.getDeliveryMode())`
  throws `IllegalArgumentException: No enum constant ...Truck` (500 error).
  Fixed with a small `findDeliveryModeByDisplayName()` helper (duplicated in
  both `AdminController` and `ManagerController` - matches each other, no
  shared util class for it). **Any future code that needs the raw enum from
  an `InvoiceDTO.Response` must go through display-name matching, not
  `valueOf()`.**
- Verified end-to-end (create/edit/delete, ownership-check rejection, audit
  log entries with correct `performed_by_role`) via a temporary test
  `MANAGER` account and test invoice, both deleted after - reuse this
  pattern for any future invoice-edit-flow verification.

## Indian amount formatting + critical Thymeleaf th:data-* gotcha (added 2026-07-29)

All amounts across the app now render with Indian digit grouping
(`12,50,000.00` lakh/crore style) instead of Western triads
(`1,250,000.00`). Thymeleaf's `#numbers.formatDecimal(...)` cannot do this
regardless of locale - it always groups in fixed triads. Even
`NumberFormat.getInstance(new Locale("en","IN"))` doesn't help: the JDK's
core `java.text` formatters only support one uniform grouping interval,
never the mixed 3-then-2s pattern (ICU4J's `DecimalFormat` can, but that's
a dependency this app doesn't otherwise need). Fix: a small dependency-free
`com.empmgmt.util.AmountFormat` that groups digits manually, used across 21
templates (81 call sites) via SpringEL's `T(com.empmgmt.util.AmountFormat).format(x)`.

**Production incident, 2026-07-29**: this broke the manager dashboard
for any manager with real invoice data, in a way that was very hard to
diagnose remotely (see `git log` around `d83a5ba`/`fbd9bf0` for the full
debugging trail - useful reading if something similar happens again).
**The durable lesson: Thymeleaf treats `th:data-*` custom-attribute
expressions as "restricted"** - `T(...)` static-class access (and `new`
object instantiation) throws
`org.attoparser.ParseException: Instantiation of new objects and access to
static classes or parameters is forbidden in this context`, but **only**
inside that restricted context - ordinary `th:text`/`th:value` are fine.
A blanket regex replacement of every `#numbers.formatDecimal(...)` call
(including two `th:data-amount="..."` attributes on
`manager/dashboard.html` feeding the invoice View-modal) converted a safe
expression into a forbidden one there specifically.

- **Why it was so hard to catch**: the restriction only fires once
  Thymeleaf actually *evaluates* the expression against a real row - an
  empty `th:each` never trips it. Every local test that day happened to hit
  empty or near-empty tables (or tested Collections, which has no such
  attribute at all), so it passed repeatedly, then broke immediately and
  deterministically for a real manager account with actual invoices.
- **Why the symptom looked like a JS bug, not a template error**: Thymeleaf
  throws *mid-render*, after already streaming part of the response. The
  connection closes right there - everything before that point in the
  document (all the visible UI) renders completely normally, while the
  entire trailing portion of the page, including every inline `<script>`
  block, silently never arrives. The browser reports `readyState:
  "complete"` regardless of the truncation, so the console just showed
  `ReferenceError: switchTab is not defined` with no obvious page-load
  failure - `document.scripts` was the tell (only the external `theme.js`
  tag present, the big inline script entirely missing; full response length
  roughly half of what it should have been).
- **Diagnostic path that actually worked** (worth reusing verbatim next
  time something like this happens): a hard refresh and a fresh Render
  redeploy of the identical commit ruled out caching/stale-deploy theories;
  `git show <commit>:path/to/file` (bypassing the local Windows checkout,
  which has CRLF-conversion warnings on every commit) confirmed the exact
  pushed bytes were syntactically clean; logging into the *actual*
  production instance via `claude-in-chrome` (the user had to log in
  themselves in the watched tab - no valid prod credentials were available
  otherwise) and running `document.scripts` there was the first hard
  evidence of a missing script block, not a JS error; reproducing locally
  needed **bulk-inserting real-scale data** (10 invoices via a
  `generate_series` SQL insert) for a test manager, since 0-1 row tests
  never triggered it - that's when the server log finally showed the real
  `attoparser.ParseException` with an exact line/column.
- **Fix**: made `AmountFormat` a Spring bean (`@Component("amountFormat")`)
  with an instance method `formatInstance(Number)`, and switched the two
  affected `th:data-amount` attributes to
  `${@amountFormat.formatInstance(inv.amount)}` (a bean reference, not
  subject to the restriction) instead of `T(...)`. The other 79 `T(...)`
  call sites are all in normal `th:text` contexts and are fine as-is - no
  need to convert those.
- **How to apply going forward**: any new `th:data-*` attribute that needs
  computed/formatted values must use a bean reference (`${@beanName.method(...)}`)
  or a plain Thymeleaf built-in (`#numbers`, `#temporals`, etc.), never
  `T(...)` or `new SomeClass()`. If a future global find/replace touches
  amount-formatting expressions again, grep for `th:data-` first and treat
  those occurrences separately.

## Login/logout session tracking (added 2026-07-28)

Net-new feature - nothing tracked login/logout timing before this. New
`login_sessions` table (see Tables list above), `LoginSessionService`
(`recordLogin(username, latitude, longitude)` / `recordLogout(username)` /
`getRecentSessions()` returning `List<Map<String,Object>>` with a computed
`durationText` and `active` flag - not a formal DTO class, kept simple since
it's read-only and single-purpose).

**Geo-tagged logins (added 2026-07-31)**: `login_sessions` gained nullable
`latitude`/`longitude` columns (`BigDecimal`, precision 10 scale 7 - same
convention as `PaymentReceipt`). `login.html` requests
`navigator.geolocation.getCurrentPosition()` **on page load** (not on
submit, unlike the receipt-photo geo-tag which fires on file-select) so the
permission prompt has time to resolve before the user finishes typing
credentials - two hidden `latitude`/`longitude` inputs get populated if/when
it resolves, blank otherwise. Optional, same as every other geo-tag in this
app - denied/unavailable never blocks login. `SecurityConfig`'s
`successHandler` reads `req.getParameter("latitude"/"longitude")` via a
small `parseCoordinate()` helper (blank/malformed → `null`, never throws)
and passes them into `recordLogin()`. `admin/login-history.html` shows a
"📍 View" map link (`google.com/maps?q=lat,lng`, same pattern as the receipt
photo's location link on `admin/entries.html`) when both are present, "—"
otherwise. Verified via curl (login POST with `latitude`/`longitude` fields,
then a direct DB check) - test row deleted after.

- **Wired into `SecurityConfig`**, not any controller: the existing
  role-based-redirect `successHandler` lambda now also calls
  `loginSessionService.recordLogin(auth.getName())` right before
  `res.sendRedirect(...)`; `.logout(...)` switched from a plain
  `logoutSuccessUrl` string to a `logoutSuccessHandler` lambda that calls
  `loginSessionService.recordLogout(authentication.getName())` before
  redirecting - the `Authentication` parameter passed into a
  `LogoutSuccessHandler` is still populated (captured before context-clearing)
  even though `.clearAuthentication(true)` runs as part of the same logout
  filter chain, so this works correctly.
- **New `GET /admin/login-history`** (`AdminController`, ADMIN-only - no
  `@PreAuthorize` override needed since the class default is already
  `hasRole('ADMIN')`, matching the bell/activity-feed precedent of not
  extending this kind of thing to ACCOUNTANT) → `admin/login-history.html`,
  showing user/role/login time/logout time/duration/live "🟢 Active" badge,
  newest-first, last 200 sessions. Nav link (`🕐 Login History`, right after
  `📜 Audit Log`) added to **all 19 other** `admin/*.html` templates via a
  scripted `sed` pass matching both the plain and
  `sec:authorize="hasRole('ADMIN')"` variants of the existing Audit Log link
  - if adding a 20th admin template later, copy this link too.
- Sessions where the app restarted before a real logout stay permanently
  `logout_at IS NULL` (shown as "🟢 Active" even though the user is long
  gone) - no cleanup/expiry job exists for this yet.

## Concurrency: what's actually protected vs. not (as of 2026-07-18)

Came up as a direct question, worth keeping the answer somewhere durable
rather than re-deriving it:

- Standard Spring Boot/Tomcat thread-per-request handling, all
  controllers/services are stateless singleton beans - concurrent users
  never cross-contaminate. Nothing custom needed or done here.
- Two "check-then-act" races exist: `ExcelPartyService.ensureExists()`
  (check `Party.combined` doesn't exist, then insert) and
  `InvoiceService.createInvoice()` (check `invoice_number` doesn't exist,
  then insert). Both are backed by real DB unique constraints
  (`parties.combined`, `invoices.invoice_number`), so **a race can never
  actually create duplicate data** even if two requests pass the check in
  the same instant - the DB is the real safety net, not the check.
- `ensureExists()` already catches the resulting constraint-violation
  exception gracefully (logs at debug, swallows it - see its `try/catch`).
  `createInvoice()` does **not** - a genuine simultaneous collision (e.g.
  two people submitting the exact same auto-suggested invoice number at
  the same instant) would propagate a raw Postgres constraint-violation
  message into the flash `errorMsg` instead of the clean "already exists"
  message the normal pre-check path produces. No data corruption, just an
  ugly message in a rare edge case - left as-is by explicit user decision,
  not fixed.
- No `@Version` / optimistic locking on any entity - two people editing
  the *same row* at the *same instant* is last-write-wins with no conflict
  warning. Accepted as reasonable for this app's small-team, low-
  concurrency usage rather than a real risk; revisit if the user base grows.

## Keep-alive (added 2026-07-18, GitHub Actions version removed 2026-07-19) - now external

- **New `GET /health`** (`AuthController`, `permitAll` in `SecurityConfig`)
  - deliberately touches neither DB nor session, just proves the JVM/
  servlet container is up.
- **The original `.github/workflows/keep-alive.yml` was deleted in
  `544e940` (2026-07-19)** - GitHub Actions' `schedule:` trigger doesn't
  reliably honor sub-hourly cron; actual gaps between runs averaged ~2
  hours instead of the configured 10 minutes, so it wasn't preventing
  sleep during the day at all. **Do not re-add a GH Actions workflow
  expecting sub-hourly reliability** - this was already tried and
  confirmed not to work. A once-daily GH Actions job (see
  `morning-wakeup.yml` below) is a different reliability profile and is
  fine - it only needs to land sometime in the early morning, not on a
  precise 10-minute cadence.
- **`.github/workflows/morning-wakeup.yml` (added 2026-07-31)**: the real
  bug behind "the site won't wake up on its own overnight, only works once
  someone opens it manually" - the user confirmed cron-job.org's actual
  failed-check log reads **"Failed (output too large)"** for the morning
  wake-up attempt: cron-job.org's monitor has a response-size cap, and
  Render's cold-boot interstitial page (served while the real app isn't up
  yet) is bigger than that cap - so cron-job.org marks it a failure, while
  a real browser (no such cap) just reads through it and gets the real app
  once it's ready. `/health` itself is unaffected - it always returns a
  plain 2-byte `"OK"`, this is purely a cron-job.org-side limitation on the
  intermediate Render page. A plain `curl` (used by this workflow) has no
  response-size limit, so it isn't affected the same way.
  - **A single ping is not enough** - repo owner caught this directly: this
    job fires once (~04:40 IST, `10 23 * * *` UTC), comfortably before
    cron-job.org's window opens at ~05:30 IST (`*/10 0-16 * * *` UTC - i.e.
    hour 0 UTC = 5:30 IST is the *first* ping of the day). If it only
    pinged once and then went quiet, the instance would fall back asleep
    after Render's ~15-min idle threshold, and cron-job.org's first real
    ping ~40-50 min later would hit the identical cold-boot/output-too-large
    problem again. Fixed by having the job **ping once (patient retry to
    survive the cold boot), then keep nudging `/health` every 8 minutes**
    (comfortably under the 15-min sleep threshold) **for ~72 more minutes**
    inside the same job run, bridging the gap until cron-job.org's own
    10-min cadence has clearly taken over.
  - **Deliberately doesn't lean on GitHub's schedule-trigger precision for
    the bridging part** - only the initial `schedule:` firing time is
    approximate (some drift is fine, it just needs to land in the early
    morning); the repeated pings happen via `sleep` inside one already-
    running job, which isn't subject to the same scheduling imprecision
    that got the original every-10-minutes GH Actions workflow removed
    (`544e940`, see above).
  - **Confirmed the repo is public** (`techalok1011-star/EmployeeManagement`)
    before choosing an ~80-minute-per-day job runtime - GitHub Actions
    minutes are unlimited/free for public repos, so this doesn't eat into
    any minutes budget. If the repo is ever made private, revisit this -
    private repos only get a limited free minutes allowance per month.
  - **Root-cause note, unrelated bug found the same session**:
    `NotificationScheduler.runDailyReminders()`'s `@Scheduled(cron = "0 0 17
    * * *")` had no explicit `zone`, so on Render's UTC-default container it
    fired at 22:30 IST instead of 5 PM IST - fixed by adding `zone =
    "Asia/Kolkata"`. This is a *separate* issue (the reminder job silently
    not running on schedule) from the site failing to wake up overnight
    (this workflow's job) - don't conflate the two if either resurfaces.
- **Replaced by an external cron-job.org job** pinging `/health` every ~10
  min, 6 AM-10 PM IST - configured entirely on cron-job.org's own
  dashboard, nothing in this repo to inspect/edit. Outside that window
  there's no ping, so the free Render instance is **allowed to idle-sleep
  overnight by design** (explicit user choice, not an oversight).
- Render's free-tier ~750 instance-hours/month easily covers a ~16h/day
  active window, so this doesn't risk exceeding the free plan.
- **Root-cause bug found and fixed 2026-07-31**: `NotificationScheduler
  .runDailyReminders()`'s `@Scheduled(cron = "0 0 17 * * *")` had no
  explicit `zone` - on Render's container (`eclipse-temurin` base image,
  no `TZ` env var set anywhere) this resolved in UTC, so "17:00" actually
  fired at **22:30 IST**, half an hour after the cron-job.org keep-alive
  window closes (10 PM IST) - right when the free-tier instance is falling
  asleep, so the daily WhatsApp reminder job silently failed to run most
  days unless someone happened to have the site open around then. Fixed
  by adding `zone = "Asia/Kolkata"` to the `@Scheduled` annotation so it
  now genuinely fires at 5 PM IST, safely inside the keep-alive window.
  **Any future `@Scheduled` cron added to this app needs an explicit
  `zone = "Asia/Kolkata"`** - the container's default timezone is UTC, not
  IST, and nothing here sets `TZ` globally.

## Current data snapshot (point-in-time, end of day 2026-07-28 - re-query live, don't trust these numbers as still current)

**Local `empdb` only** - see the ShivShakti historical import section below
for why local and Neon now diverge on more than just organic usage.

- `parties`: 664 rows (241 as of 2026-07-12 + 385 from the 2026-07-28
  historical import, minus the ~10 that fuzzy-merged into existing rows
  instead of creating duplicates - net +423). 403 of these currently share a
  `trailing_number` with at least one other party (across 157 distinct
  codes) - expected, not a bug, since this business reused Tally ledger
  codes for different real parties across fiscal years; flagged to the user
  as a `PayTrack_Shared_Trailing_Numbers.xlsx` review file, not yet acted on.
- `invoices`: 5,266 rows, ₹48,00,34,775.44 total, spanning 2022-04-01 to
  2026-07-19 (was 2025-04-01 onward before the historical import).
- `payment_entries`: 23,337 rows, ₹45,94,59,706.00 total, spanning
  2022-04-01 to 2026-07-24.
- `users`: 6 (unchanged in count from 2026-07-12, though the specific
  accountant/employee usernames may have changed) - `admin` (ADMIN), 4
  EMPLOYEE, 1 ACCOUNTANT. **No MANAGER user currently exists** - every
  manager-role test in this project has used a temporary account created via
  `/admin/employees/add` and deleted after, since real managers' passwords
  aren't recoverable (see BCrypt note above).
- `login_sessions`: 6 rows (feature added 2026-07-28, so this only reflects
  activity since then, mostly from this session's own admin logins/logouts
  plus test-account sessions that were cleaned up).
- **Neon is behind on all of the above** - last directly queried
  2026-07-28 at 227 parties / 1,896 invoices / 7,211 payments (already
  diverged from local's pre-historical-import baseline via real production
  usage, e.g. real invoices/payments Neon has that local didn't). The
  FY2026-27 partial import (534 invoices/1,911 payments) *is* on Neon. The
  2026-07-28 FY22-23/23-24/24-25 historical import is **not**. `login_sessions`
  table does not exist on Neon yet either until the next deploy (schema
  auto-migrates via `ddl-auto=update` on next boot, no manual step needed).

## Tally import (2026-07-12) - durable facts

- Source: `sale register.xls` / `reciept reigiter.xls` (Tally exports for
  Shiv Shakti Cement Supply Agency), reconciled into
  `ShivShakti_FY2526_Outstanding_and_Trend.xlsx` first - see
  [[register-reconciliation]] skill for the full parsing/cleanup pipeline.
- Party matching against the pre-existing 85 GST-registered `parties` was
  done two ways: (1) frequency-weighted name-token overlap (16 matches), and
  (2) **transaction-level reconciliation** - same date + near-identical
  amount + corresponding party name - which caught 2 more real matches the
  token pass missed (`SINGH B M (MILKHIPUR)62`→`M/S SINGH BUILDING MATERIAL`,
  9 collisions; `RAJESH & SONS 270`→`M/S Rajesh & Sons,Bealy`, 5 collisions)
  and one genuine data-loss bug: 32 Sales Register rows were initially
  skipped as "already represented" by an existing invoice, but since the
  existing invoices were then deleted entirely (per explicit instruction),
  those 32 had to be backfilled afterward or their value would have vanished.
  **If more Tally data is imported later, re-run reconciliation rather than
  assuming today's 18-party match list is exhaustive** - `SINGH BUILDNG
  MATERIAL (LALGANG)` (DB id 59) showed weaker, ambiguous overlap with a
  *second* Tally party (`SINGH B M (LALGANJ) 226 ARUN`) beyond the one it
  was matched to (`MOIZEM ALI (LALGANG)`) - left unresolved, flagged to the
  user, not merged.
- Full backup of the deleted original `invoices`/`payment_entries`/
  `transaction_logs` (pre-deletion state) was taken via `pg_dump` before any
  deletion - ask the user where that dump file ended up if it's ever needed
  (it was written to a session scratchpad, not the repo).
- `party.name`/`combined` for the 156 newly-inserted parties is the raw Tally
  ledger text (e.g. `CHANDRJEET B. M (JAIGAHA) 78`) - much less formal than
  the original 85 parties' `M/S ... BUILDING MATERIAL_GSTIN` style. Don't
  "clean up" these names to match the other style without checking with the
  user first - the trailing number is the important, deliberately-preserved
  part.

## ShivShakti historical import (2026-07-28) - durable facts

Second, larger historical backfill, covering FY2022-23/FY2023-24/FY2024-25
(the 3 fiscal years immediately **before** the 2026-07-12 import's
FY2025-26 start). Source workbook:
`Shiv Shakti Cement Supply Agency_FY2022-23_to_FY2024-25_Outstanding_and_Trend.xlsx`
(`C:\Users\ay036\Downloads\`, built via [[register-reconciliation]] from
`sale regiter.xlsx` on 2026-07-27) - has 3 years × 4 sheets each (Outstanding/
Monthly Trend/Party Ledger/No Party Code) plus one cross-year "Code
Collisions" sheet.

- **Party identity = exact ledger text, never the trailing code** - the
  workbook's own Party Ledger sheets already resolve the "same code, two
  different real parties" collision problem (documented on its own "Code
  Collisions" sheet) by keeping each distinct full-text name as its own
  ledger block per year. The import parser just followed that: grouped
  transactions by the literal header-row text found in each year's Party
  Ledger sheet, never by `trailing_number`. Two parties sharing a code is
  harmless in the schema (`trailing_number` isn't unique, only `combined`
  is) - confirmed working correctly post-import for a real 3-way collision
  (code `162`: `AGENCY SUPLYY  B M (MADURI) 162` / `Jialala Yadav B. M (
  Muzzfrpur) 162 Myank` / `CHANDRABHUSANN (MADURI) 162`, all landed as 3
  separate `Party` rows with correct per-party transaction totals).
- **Party names carry real double-spacing/case drift the Outstanding/Code-
  Collisions sheets don't preserve** - e.g. the actual Party Ledger header is
  `AGENCY SUPLYY  B M (MADURI) 162` (double space before "B M"), while other
  sheets in the same workbook render it single-spaced. Don't assume text
  copied from one sheet exactly matches what's in the Party Ledger sheet -
  verify against the Party Ledger itself before writing match/merge logic.
- Of 398 distinct historical party names, only **12** fuzzy-matched (case/
  punctuation only) an existing DB party and were merged into the existing
  `combined` string per explicit user decision; the other 386 (→385 after
  in-batch dedup) were new - this business's Tally naming drifts heavily
  year to year, confirmed independently by the Code Collisions sheet.
- **Invoice-number scheme**: `TALLY-S-FY<year>-<vchNo>` (e.g.
  `TALLY-S-FY2022-23-714`) - this exact year-qualified pattern was
  discovered **already in use** for the FY2026-27 partial import
  (`TALLY-S-FY2026-27-<n>`) before this session touched anything, so the
  historical import matched it rather than inventing a new one. Description/
  remarks text also matches that existing convention exactly (`"Imported
  from ShivShakti Sales/Receipt Register (Tally) - FY<year>..."`).
- **New parties' `total_amount`** = sum of Sales (debit) only from whatever
  was being imported at that moment, same convention as the original
  2026-07-12 import - see the `total_amount` gotcha in the `parties` table
  row above; this field is never live-recomputed afterward.
- All sums verified byte-exact against the workbook's own per-year Outstanding
  + Monthly Trend grand totals, both before and after the DB write - see
  [[register-reconciliation]] skill's verification discipline.
- Full `pg_dump` backup (local **and** a separate Neon backup, taken right
  before writing to each) in the session scratchpad, not the repo - ask
  where these ended up if a rollback is ever needed for this specific change.
- **Local was imported first; Neon is NOT yet done.** The plan was to
  re-run the identical import against Neon (using its live connection
  string, pulled from the Render dashboard - not stored anywhere in this
  repo/skill) rather than a dump/restore of local→Neon, since Neon already
  had real production activity local didn't (confirmed: Neon had *more*
  recent invoices/payments than local's pre-import baseline at the time).
  **The one attempt made died partway through** - `Connection terminated
  unexpectedly` after the 385 parties + 3,377 invoices were sent but before
  the 16,134 payments - but the whole batch was one transaction, so it
  **rolled back cleanly with zero partial state**, re-verified via direct
  query immediately after (227 parties / 1,896 invoices / 7,211 payments,
  unchanged). A retry was proposed (batching multi-row inserts instead of
  ~20k individual round-trip statements, since Neon's pooled connection
  can't survive that long a single connection) but the session moved on to
  other work before it happened - **Neon still does not have this
  historical import as of end of session 2026-07-28.** Don't assume it's
  done; re-check party/invoice/payment counts against Neon before trusting
  any "is Neon in sync" assumption.

## FY2026-27 ledger + `empdb2` (2026-07-30/31) - durable facts

Two source PDFs for this same ShivShakti business, both in
`C:\Users\ay036\Downloads\`: `LEDGER ALL PARTY 2627.pdf` (309-page Tally
"Ledger Account" export, 1-Apr-26 to 28-Jul-26 - the real source) and
`PARTY DETAILS.pdf` (turned out to just be Tally's own alphabetical index of
the ledger PDF's page numbers, not a separate master-data file - useful only
as a cross-check on party count, not a data source itself).

### Parsing the ledger PDF

- No Python on this machine (per its own recurring note elsewhere in this
  skill) - used `pdf-parse` v2 (`PDFParse` class + `getText()`, **not** the
  v1 `pdf(buffer)` function-call API some docs/examples still show) in a
  scratch Node project, plus `exceljs` for writing the workbook.
- 309 page-blocks in the PDF, but only **252 are real parties** - the rest
  are the business's own internal ledgers that happen to print in the same
  "Ledger Account" report format and must be excluded by name:
  `Sales Account`, `Profit & Loss A/c` (both literally have `kindLine =
  "Ledger Account"`, indistinguishable from a real party except by name), and
  `Cash`/`Cash Book` (35 pages alone, distinguishable by kind line - the
  business's entire cash receipts book, multi-page).
- **Multi-page party ledgers** (10 of the 252) continue onto a following PDF
  page whose header line is subtly different from a fresh party's - it reads
  `SHIV SHAKTI CEMENT SUPPLY AGENCY` / `"<PartyName> Ledger Account : <period>
  \tPage N"` (a continuation-page artifact of the PDF export) instead of the
  normal `<PartyName>` / `"Ledger Account"` two-line header. First line after
  that header is `Brought Forward <credit>\t<debit>` instead of `Opening
  Balance`. Any future re-parse of a Tally "Ledger Account" (not Register)
  export needs to handle this continuation format, not just single-page
  blocks.
- Within a party block, **"To" = Debit column, "By" = Credit column** for
  every row type uniformly (Opening Balance, Sales, Receipt, and the
  Closing-Balance plug row alike) - this holds even though the plug row's
  prefix looks "backwards" at first glance. Don't try to read Dr/Cr meaning
  from the plug row's own prefix; compute the running balance yourself from
  real transaction rows (`opening + Σdebit − Σcredit`) and use the printed
  Closing Balance line only as a numeric cross-check, not as the source of
  truth for sign.
- Zero-balance parties (opening exactly cancels transactions) get **no**
  printed Closing Balance line at all (Tally only prints the plug when
  needed to balance the columns) - don't treat a missing closing line as a
  parse failure, treat it as balance = 0.
- Verified byte-exact: 541 Sales transactions summing ₹4,69,76,062.40, 1,970
  Receipt transactions summing ₹4,58,43,821.00, 235 parties with a nonzero
  1-Apr-26 opening balance (214 Dr summing ₹4,70,45,039.22, 21 Cr summing
  ₹11,82,254.91), grand net outstanding ₹4,69,95,025.71 across all 252
  parties - re-derive these from `parties.json` (see "Files" below) rather
  than trusting this snapshot if the workbook or DB is ever rebuilt.

### `Party_Outstanding_FY2026-27_Apr-Jul.xlsx` workbook

In `C:\Users\ay036\Downloads\` - built across two sessions. Three sheets:

1. **Party Outstanding** - one row per party (Sl. No./Party/Ledger Code/
   Opening/Closing/Balance Type/Outstanding), built in an earlier session
   from this same ledger PDF, sorted by Outstanding descending.
2. **Party Ledger** (added 2026-07-30) - full transaction detail per party,
   mirrors the [[register-reconciliation]] skill's styling conventions
   (navy header, Sales rows red/Receipt rows green, party subtotal/grand
   total rows) but with a single Vch No. + Type column pair rather than
   separate Sales/Receipt Vch No. columns, since this source's own "Vch
   No." column already covers both under one `Vch Type`.
3. **Missing Trailing Numbers** (added 2026-07-30) - the 46 parties whose
   Ledger Code came back blank, cross-checked against local `empdb`'s
   `trailing_number` column.

**Two real data problems found and fixed in the Party Outstanding sheet**
while cross-verifying the new Party Ledger sheet against it:
- **`BRIJENDRA YADAV PADRI`** had Outstanding = ₹15,61,87,678 in the
  original sheet (by far the largest party, which should itself have been a
  red flag) - the ledger PDF itself shows a real closing balance of only
  ₹18,500. Root cause not identified (predates this session's Excel), just
  corrected. This one error alone accounted for the entire gap between the
  sheet's original grand total and the newly-computed one - a useful
  sanity-check technique if a similar mismatch ever shows up again: diff the
  two grand totals and see if it equals exactly one party's error.
- **`USMANI BM (MUBARKHPUR) 404`** vs **`USMANI B M (MUBARKHPUR) 404`** -
  same party (identical outstanding amount), just a missing space; the
  ledger PDF and its index both use the spaced form. Renamed to match.
- Both fixes triggered a full re-sort (Sl. No. renumbered 1-252) and
  footer-totals recompute (`Total Outstanding Receivable (Dr)`, `Total
  Advance Held (Cr)`, `Net Outstanding`).

**Trailing-number recovery**: of the 46 parties with a blank Ledger Code,
checking exact name matches against local `empdb.parties.trailing_number`
found only 1. Re-checking for the **"name + trailing digits appended"**
pattern (a real, recurring convention in this business's data - the same
party sometimes gets a DB row named e.g. `"BRIJENDRA YADAV PADRI 228"`
instead of storing `228` in the `trailing_number` column) found **12 more**
this way, for 13 total recovered and written back into the Party Outstanding
sheet. The other 33 either exist in `empdb` with no trailing number recorded
anywhere, or don't exist in `empdb` at all - a plain exact/normalized-name
match on `parties.name` will systematically miss this embedded-code pattern;
always also check for it explicitly.

### `admin/party-ledger.html` / `admin/full-ledger.html` Dr/Cr display bug (fixed)

Found while cross-checking a live party (`R.K TRADERS (SIDHARI) 351`,
Total Paid > Total Invoiced) against the Excel: both templates computed
their "Outstanding" stat tile as `ledger.outstanding.abs()` - **unconditionally
discarding the sign** - and distinguished Dr from Cr only by a subtle color
change (`red` vs the *same* neutral color used for the unrelated "Total
Invoiced" tile). A party the business owes money **to** looked visually
identical to a party that owes the business money, with no way to tell them
apart short of reading the raw ledger rows. Fixed in both templates: label
switches to "Advance Held" and a green **Cr** suffix appears whenever
`outstanding < 0`; positive balances unchanged. `admin/invoices.html` and
`admin/payment-behavior.html` already had a separate "Credit" badge column
next to their outstanding amount, so they didn't have this bug.

### `empdb2` - a from-scratch local database seeded from this ledger

Built after the user explicitly wanted this data usable by the project but
**without modifying `empdb` at all**. Final approach (after an initial
false start - see below): `CREATE DATABASE empdb2`, `pg_dump`/restore
`empdb`'s **schema** into it (so table structure/columns match exactly),
then `TRUNCATE` the financial tables and rebuild them from *only* the 252
ledger parties - **not** a merge/reconcile against `empdb`'s existing party
history, despite that being the original plan. `users`/`notification_settings`/
`login_sessions` were left as cloned from `empdb` so the DB is usable by the
app immediately (needs an admin login to exist).

- **False start, worth remembering if this pattern comes up again**: the
  first approach (per an earlier, since-superseded plan) fully cloned
  `empdb`'s *data* too, then tried to reconcile the 252 ledger parties
  against `empdb`'s ~668 existing ones by name - this surfaced real,
  pre-existing duplication inside `empdb` itself (the same real party
  sitting under 2-3 differently-spelled `parties` rows, e.g. `SURAJ B. M
  (DEWAIT) 358` / `Suraj B. M ( Dewait) 358` / `M/S SURAJ B/M (RAM BUJH
  DRIVER)` all trailing-numbered 358, holding separate disconnected
  history) with no single obviously-correct canonical name to reconcile to.
  The user then clarified they didn't want `empdb`'s transaction history in
  `empdb2` at all - simplifying things considerably, since the whole
  reconciliation problem became moot. **If `empdb`'s duplicate-party
  problem is ever tackled directly (as its own task, not via this ledger
  import), the flagged list from that abandoned reconciliation pass is a
  useful starting point** - though it wasn't saved anywhere durable, it
  would need re-running.
- **Synthetic opening-balance rows**: since `empdb2` has no pre-2026-04-01
  history at all, every one of the 235 parties with a nonzero ledger opening
  balance needed a synthetic entry dated `2026-04-01` to avoid silently
  losing that balance - a Dr opening became an `invoices` row
  (`invoice_number = 'TALLY-S-FY2026-27-OPEN-<party-slug>'`), a Cr opening
  became a `payment_entries` row (no `receipt_vch_no`, identified instead by
  `remarks LIKE 'Opening balance%'`). 214 Dr + 21 Cr = 235, matching exactly.
- **Real transactions** use the established convention from the earlier
  Tally imports: `invoice_number = 'TALLY-S-FY2026-27-' || vchNo`,
  `sales_vch_no`/`receipt_vch_no` = the ledger's own Vch No., `delivery_mode
  = 'TRUCK'`, `mode_of_payment = 'CASH'` (only mode present in this source),
  `employee_id = 1` (the `admin` user), description/remarks text copied
  **verbatim** from the existing partial-FY2026-27-import rows already in
  `empdb` (`"Imported from ShivShakti Sales Register (Tally) - FY2026-27"` /
  `"...Receipt Register... - payment mode not specified, defaulted to CASH"`)
  rather than reinvented, so any future full-text search for this import
  convention works across both.
- **Final `empdb2` counts**: 252 parties, 755 invoices (541 real + 214
  synthetic), 1,991 payment_entries (1,970 real + 21 synthetic). Confirmed
  0 duplicate `invoice_number`s, 0 orphan `party_name`s, and per-party
  outstanding recomputed from the raw DB rows matched the PDF-verified
  ledger closing balance **exact to the paisa for all 252 parties** (an
  independent validation pass, not just the build script's self-report -
  worth repeating this pattern, a self-reported "done" from an import
  script should not be taken as verification on its own).
- `empdb` itself confirmed unchanged throughout (row counts, sums, and date
  ranges for every table matched the pre-existing baseline exactly; no
  table's on-disk mtime moved except from an unrelated read-only `pg_dump`
  checkpoint flush).
- **Files** (session scratchpad, not the repo - ephemeral, don't assume
  these paths still exist in a future session): the parsed/verified
  `parties.json` (ground truth for all of the above), the applied
  `rebuild_fy2627.sql`, and an **abandoned, never-run script
  `generate_import.js`** from the false-start approach above that contains
  unqualified `DELETE FROM invoices`/`DELETE FROM payment_entries`
  statements with no database guard - confirmed never pointed at `empdb`,
  but flagged as worth deleting outright if it's still sitting there,
  rather than left around as a footgun.
- **`spring.datasource.url` was switched to point at `empdb2`** after this
  build (see "Running it / connecting" above) - the running local app now
  serves from `empdb2`, not `empdb`.

## PWA + mobile responsiveness (added 2026-07-15)

- Goal: employees install the site to their phone home screen (like a
  native app) and use it without a cramped fixed-sidebar layout. Chose PWA
  over a native Android app (no Android SDK/emulator available to build or
  verify one; PWA reuses the existing server-rendered app as-is).
- **New static assets**: `static/manifest.json` (name "PayTrack",
  `start_url: /employee/dashboard`, standalone display), `static/sw.js`
  (deliberately minimal service worker - caches only the icons + manifest,
  **never** HTML/data pages, to avoid ever serving stale financial figures
  offline), `static/icons/icon-192.png` / `icon-512.png` /
  `icon-512-maskable.png` (generated via a `pngjs`-based Node script, not
  checked in as source - just the PNG output).
- **`SecurityConfig.java`**'s `permitAll()` matcher list must include
  `/manifest.json`, `/sw.js`, `/icons/**` - forgetting this makes Spring
  Security 302-redirect them to `/login`, silently breaking installability
  even though the files exist and are otherwise correct. Already fixed;
  watch for this regressing if the matcher list is ever refactored.
- **Off-canvas responsive sidebar** pattern (CSS `transform:
  translateX(-100%)` + `.open` class + a `.sidebar-overlay` click-to-close
  div + a `.menu-toggle` hamburger button + `toggleSidebar()` JS,
  `@media(max-width:768px)`), plus the manifest `<link>`/theme-color
  `<meta>`/apple-touch-icon `<link>` and SW-registration script - all
  copy-pasted identically across the templates listed below. If adding
  this to a new template, copy the exact block from one of these rather
  than reinventing it.
- **Currently applied to**: `login.html` and the 4 employee-facing
  templates (`employee/dashboard.html`, `employee/entries.html`,
  `employee/edit-entry.html`, `employee/history.html`) - these are what
  employees actually use day-to-day. **Not yet applied** to any of the 16
  admin-facing templates (`admin/*.html`) - deliberately deferred, only do
  this if asked, since admins were assumed to be on desktop.
- Verified by creating a temporary test `EMPLOYEE` user + one test payment
  entry through the running app (via `/admin/employees/add` and the
  employee entry form), logging in as that user, curling all 4 pages
  post-login to confirm HTTP 200 with no Thymeleaf/exception markers, then
  deleting the test user/entry/log rows afterward - necessary because real
  employees' passwords are unknown/unrecoverable (see above), so there was
  no other way to actually exercise an authenticated employee session.
  Reuse this same create-test-employee-then-delete approach for any future
  employee-only-page verification.

### Flex/grid `min-width:auto` gotcha - a recurring class of mobile bug (2026-07-26, extended 2026-07-28)

Flex and grid items both default to `min-width:auto`, meaning they refuse to
shrink below their **content's intrinsic min-width** unless told otherwise
with an explicit `min-width:0`. This has now bitten the manager pages twice:

1. **2026-07-26**: `.stat-card` inside `.stats-grid` (large ₹ totals could
   spill past the card edge) - fixed with `min-width:0; max-width:100%;
   overflow:hidden` on `.stat-card` + `overflow-wrap:break-word` on the
   value text.
2. **2026-07-28**: much bigger version of the same bug - `.main` (a flex
   child of `body{display:flex}`) and `.card` (a grid child of
   `.two-col{display:grid}`) both lacked `min-width:0`. Adding a wider
   Actions column to the dashboard's invoice tables (see "Invoice edit
   capability" above) pushed a descendant `<table>`'s intrinsic width high
   enough that `.main` refused to shrink at all on mobile - the **entire
   page**, not just that one table, rendered wider than the viewport and
   required horizontal scrolling to see anything. Fixed by adding
   `min-width:0` to `.main` (in `manager/dashboard.html`, `invoices.html`,
   `entries.html`, `edit-entry.html`, `edit-invoice.html`) and to
   `.two-col > *` (in `dashboard.html`, the only file with that class).

**How to apply**: any time a new wide/complex element (a table with an
explicit `min-width`, a long unbreakable string, a fixed-width control) gets
added inside a flex or grid container on a mobile-treated page, check
whether every ancestor in that flex/grid chain has `min-width:0` - if the
*whole page* spills out rather than just the one element scrolling within
its own wrapper, this is almost certainly why.

**Also separately fixed 2026-07-28**: `display:flex` was put directly on a
few `<td>` elements (for an Edit/Delete/View actions cell) - mixing flex
display on table cells is its own known cross-browser landmine (inconsistent
width-calculation behavior, particularly on iOS Safari). Moved the flex
layout onto an inner wrapper `<div>` inside the cell instead, in
`manager/dashboard.html`, `manager/invoices.html`, and `admin/invoices.html`
- keep table cells as plain `display:table-cell` and wrap any flex/grid
layout you need inside them in a child element.

**Browser-based mobile-viewport testing gotcha (2026-07-28)**: in this
environment, `mcp__claude-in-chrome__resize_window` reports success but does
**not** actually change `window.innerWidth` (confirmed stuck at the full
desktop resolution regardless of requested size), so `@media` queries never
activate and screenshots/computed-styles at the "resized" width are
misleading. **Workaround that works**: create a same-origin `<iframe>` via
`javascript_tool`, set its `style.width`/`height` to the target mobile size,
point `src` at the page under test, await `onload`, then inspect
`iframe.contentDocument`/`contentWindow` - media queries correctly evaluate
against the iframe's own dimensions regardless of the outer window. Also
useful: compare `iframe.contentDocument.body.scrollWidth` against the
iframe's own width to directly detect real horizontal-overflow bugs like the
one above.

## Where to look next

- Root-level ad hoc scripts `cleanup_parties.ps1` / `test_cleanup.ps1` hit
  the running app's REST endpoints directly - useful examples of calling
  `/api/parties/*` from outside the UI.
- `scripts/inspect_excel.py` - a dev helper for inspecting the party-import
  Excel file structure (Python - check an interpreter is actually available
  before assuming this runs; it was not on the machine this skill was built
  on, see the [[register-reconciliation]] skill's Node-only workaround for
  the same constraint).
- `git log --oneline` on `main` is a reliable, dense summary of everything
  built since the Tally import (`be798cf`) - caching, compression/lazy-
  loading, Docker/Render deploy config, bags×rate auto-calc, PWA, fiscal-
  year ledger partitioning, manager collections, invoice edit/login-history,
  Indian amount formatting, and the `th:data-*`/`T(...)` production
  incident and fix (`fbd9bf0` as of 2026-07-29) - read commit messages there
  for anything this skill doesn't cover yet, rather than assuming the
  feature set is frozen at what's written above.

## Still open / not yet done (updated 2026-07-29)

- **Verify `fbd9bf0` (the th:data-*/T(...) truncation fix) is actually live
  on Render** before assuming production is healthy - the deploy pipeline
  was in an unusual state that session (manual rollback appeared to pause
  auto-deploy, needing a manual "Deploy latest commit" click; see the
  Deployment section's rollback note above). Confirm via the Render
  dashboard's Events tab showing this commit as the current live deploy,
  not just that a push happened.
- **Neon does not have the 2026-07-28 ShivShakti historical import** (FY22-23
  through FY24-25) - local only. See that section above before assuming the
  two databases are in sync on party/invoice/payment counts.
- **403 parties currently share a `trailing_number` with another party**
  (157 distinct codes) - a review workbook (`PayTrack_Shared_Trailing_Numbers.xlsx`)
  was generated and handed to the user 2026-07-28 but no merge/cleanup
  decisions have been made yet. If asked to act on this, don't assume every
  row is a duplicate - see the historical-import section above for why
  code reuse across years is expected, not automatically wrong.
- PWA/responsive treatment not yet applied to the 18 admin-facing
  templates (now 20 with `edit-invoice.html`/`login-history.html` added
  2026-07-28, both also un-treated) - only login + the 4 employee pages +
  `manager/*` have it.
- `SINGH BUILDNG MATERIAL (LALGANG)` (DB id 59) party-matching ambiguity
  from the 2026-07-12 Tally import - still unresolved, see "Tally import"
  section above.
- `InvoiceService.createInvoice()`'s check-then-act race on
  `invoice_number` doesn't catch the resulting DB constraint-violation
  exception the way `ExcelPartyService.ensureExists()` does - see
  "Concurrency" section above. Explicitly left as-is by user decision.
  `updateInvoice()` (added 2026-07-28) has the identical gap for the same
  reason - not fixed either.
- Receipt photo display on `admin/party-ledger.html`/`admin/full-ledger.html`
  - only `admin/entries.html` shows the 📷 icon today (see receipt section
    above), deliberately scoped smaller for the first pass.
- Photo capture only wired into the employee day-entry form, not the
  admin/accountant "Add Payment from Ledger" backfill flow - see receipt
  section above for why.
- Notification bell/activity feed is ADMIN-only, not extended to ACCOUNTANT,
  per the original request wording. Login History (added 2026-07-28) follows
  the same ADMIN-only precedent.
- `login_sessions` rows never get a `logout_at` if the app/server restarts
  before a real logout - no expiry/cleanup job exists, so very old "🟢
  Active" badges on `/admin/login-history` may just be stale, not real.
- **`empdb2` exists only locally** - no Neon equivalent, not deployed
  anywhere, not referenced by `render.yaml`/any env var. If asked to deploy
  this FY2026-27 data or make it visible on the live Render app, that's new
  work, not something already done.
- **The local app currently points at `empdb2`, not `empdb`** (see "Running
  it / connecting" above) - `empdb`'s full FY22-27 history is not visible in
  the running local instance right now unless `spring.datasource.url` is
  switched back.
- `party.import.on-startup` was set to `false` globally (2026-07-31, see the
  `ExcelPartyService` note above) - if the app is ever pointed back at
  `empdb`, the startup Excel-seed sync that used to run every boot won't
  fire anymore unless this is flipped back to `true`.
- **`empdb`'s own duplicate-party problem, surfaced but not fixed**: an
  abandoned reconciliation pass during the `empdb2` build (see that section
  above) found real parties sitting under 2-3 differently-spelled `parties`
  rows in `empdb` with disconnected history (e.g. the `SURAJ B. M (DEWAIT)
  358` example) - on top of the already-known, separately-flagged "403
  parties share a `trailing_number`" issue above. Neither has been merged or
  cleaned up.
- The abandoned `generate_import.js` script (unqualified `DELETE FROM
  invoices`/`payment_entries`, see the `empdb2` section above) may still be
  sitting in the session scratchpad - worth confirming it's gone before
  trusting any future scratchpad script that looks similar.
