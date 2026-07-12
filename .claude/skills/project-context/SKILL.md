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
are just `User` rows with `role=EMPLOYEE` (or `ACCOUNTANT`).

**Do not trust `ARCHITECTURE.md` / `USER_GUIDE.md` at the repo root** - they
describe an early, much simpler version (Users/PaymentEntry/TransactionLog
only) and were never updated as Party/Invoice/WhatsApp/export/analytics
features were added. Verify against `src/main/java/com/empmgmt/` instead.
Last full re-verification against source: 2026-07-12.

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

## Running it / connecting

- Maven, Java 17, Spring Boot 3.2.0. `mvn spring-boot:run` or run
  `EmployeeManagementApplication` from the IDE. Serves on `localhost:8080`.
- **Postgres** `empdb` on `localhost:5432`, user `postgres`. Password is a
  plaintext local-dev credential in `src/main/resources/application.properties`
  (`spring.datasource.password`) - read it from that file rather than assuming
  it's unchanged, then connect via:
  `"C:\Program Files\PostgreSQL\13\bin\psql.exe" -h localhost -U postgres -d empdb`
  (no GUI psql/pgAdmin on this machine - always use this CLI path).
- `spring.jpa.hibernate.ddl-auto=update` - schema auto-migrates new
  tables/columns on boot, but **never** rewrites existing CHECK constraints,
  indexes, or drops columns. See the enum gotcha below.
- **Two Postgres installs on this machine**: `C:\Program Files\PostgreSQL\13`
  (matches the running server - use this `psql.exe` for all queries/DDL) and
  `C:\Program Files\PostgreSQL\18` (only needed for `pg_dump.exe`/`pg_restore.exe`,
  since v13's dump tools refuse to run against a v18 server - "server version
  mismatch" - but v13's `psql.exe` talks to the v18 server fine for normal
  SQL). Don't assume `psql`/`pg_dump` under the same version folder both work.
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

### Tables (7, schema `public`)

| Table | Key columns | Notes |
|---|---|---|
| `users` | id, username(unique), password(BCrypt), full_name, email, role, active, created_at | `role` CHECK constraint: `ADMIN,EMPLOYEE,ACCOUNTANT` |
| `parties` | id, name, gst, **combined(unique)**, **trailing_number**, total_amount, phone, whatsapp_opt_in | `total_amount` = sum from Excel import, not live-recomputed. `trailing_number` (added 2026-07-12): the party's ledger code from the source Tally system, e.g. `78` for `CHANDRJEET B. M (JAIGAHA) 78` - nullable, most of the original 85 parties don't have one |
| `invoices` | id, invoice_number(unique), invoice_date, party_name, amount, description, delivery_mode, transport_number, **sales_vch_no** | `delivery_mode` CHECK: `TRUCK,SELF_PICKUP,TROLLEY` (only `TRUCK` used in current data). `sales_vch_no` (added 2026-07-12): Tally Sales Register voucher number, only populated for imported rows (`invoice_number` prefix `TALLY-S-<vchNo>` for those) |
| `payment_entries` | id, party_name, amount, mode_of_payment, entry_date, remarks, edited, edited_by, edited_at, employee_id(FK→users), **receipt_vch_no** | `mode_of_payment` CHECK: `CASH,CHEQUE,BANK_TRANSFER,UPI,NEFT,RTGS,DD`. Only real FK in the whole schema is `employee_id`. `receipt_vch_no` (added 2026-07-12): Tally Receipt Register voucher number for imported rows - **not unique**, Tally itself ran two parallel voucher-number series that collide with each other |
| `transaction_logs` | id, action, entry_id(no FK, just a Long), employee_name, employee_username, party_name, amount, mode_of_payment, entry_date, remarks, performed_by, notes, performed_at | Audit trail, denormalized snapshot per event, not linked by FK |
| `notification_logs` | id, party_name, phone, outstanding_amount, status, error_message, triggered_by, sent_at | `status` CHECK: `SENT,FAILED,DRY_RUN`. `triggered_by` = `"SCHEDULER"` or `"ADMIN:<user>"`/`"ACCOUNTANT:<user>"` |
| `notification_settings` | id(always 1, singleton row), daily_reminder_enabled, updated_by, updated_at | Controls whether the 5pm scheduler actually fires |

### Entities/enums (`entity/` package)

- `User.Role`: `ADMIN, EMPLOYEE, ACCOUNTANT`
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
  on startup (`party.import.on-startup=true`, default file
  `Party_wise_Sales_Summary.xlsx`) or via re-upload
  (`/api/parties/upload-import`). Auto-detects header row, GSTIN column,
  amount column by text heuristics; accumulates per-party totals across
  sheets. `ensureExists(combined)` lazily creates a `Party` row when a new
  name is typed into a form. `cleanupInvalidEntries()` strips junk rows
  (bare numbers, stray header text).
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

## Scheduled jobs

Exactly one: `NotificationScheduler.runDailyReminders()`, cron
`0 0 17 * * *` (5pm daily), gated on the `notification_settings` singleton
row (admin-toggleable at `/admin/notifications`).

## Endpoints (grouped by controller)

- **`AuthController`**: `/login`, `/` (redirect).
- **`EmployeeController`** (`/employee/**`, role EMPLOYEE): dashboard, add/
  edit/delete own today-only entries, list, history.
- **`AdminController`** (`/admin/**`, role ADMIN; several sub-paths widened
  to ADMIN+ACCOUNTANT): dashboard, employees CRUD, entries CRUD+export
  (excel/pdf/audit-csv) - `/admin/entries` supports `from`/`to`/`employeeId`
  date-range filtering, history, **invoices** (`/admin/invoices` - also
  supports `from`/`to` date-range filtering on the Invoice List since
  2026-07-12, independent of the lifetime stats bar and the notification log
  section which stay unfiltered), **ledger** (`/admin/ledger?partyName=`,
  single party, newest-first), **full-ledger** (`/admin/full-ledger`, added
  2026-07-12 - every party's statement on one page, oldest-first per party,
  mirrors the ShivShakti Excel workbook's "Party Ledger" sheet layout, has a
  client-side search box filtering visible party sections), **aging**
  (`/admin/aging`), **parties CRUD** (`/admin/parties*`, including
  phone/opt-in toggle), **notifications** (`/admin/notifications`,
  send/toggle-schedule/resend-failed), employee-collections, executive
  dashboard, collections worklist + one-off remind, payment-behavior
  analytics.
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

## Current data snapshot (point-in-time, end of day 2026-07-12 - re-query live, don't trust these numbers as still current)

Superseded by the Tally import (see "Major data event" above and "Tally
Import" section below) - the original demo invoices/payments are gone.

- `parties`: 241 rows (85 original + 156 imported). Only 174 of these have
  any invoice or payment at all (all Tally-imported ones); the other 67
  original parties now have zero transactions and won't appear in
  `getPartyOutstandingSummary()` / the outstanding-based pages. Only 2 have
  `whatsapp_opt_in=true` (A.K Enterrises, Lalchand Yadav).
- `invoices`: 1,355 rows, **all** from the Tally Sales Register import
  (`invoice_number` prefix `TALLY-S-<vchNo>`), all `delivery_mode='TRUCK'`.
  Sum: ₹11,41,75,575.54.
- `payment_entries`: 5,292 rows, **all** from the Tally Receipt Register
  import (`receipt_vch_no` set on every row), all `mode_of_payment='CASH'`
  (Tally didn't record actual payment mode - stated as an assumption in each
  row's `remarks`), `employee_id` = `admin` for all of them (placeholder,
  since these aren't real employee-logged entries). Sum: ₹10,91,46,126.00.
  Net outstanding across all parties: ₹50,29,449.54 (verified live against
  `/admin/invoices`'s stat bar and `/admin/full-ledger`).
- `users`: 6 - `admin` (ADMIN), `rahul.sharma`/`priya.patel`/`amit.kumar`/
  `Amardeep` (EMPLOYEE), `satya` (ACCOUNTANT). Unchanged by the import.
- `notification_logs`: 44 rows, all pre-date the import, includes repeated
  `FAILED` sends to "LALCHAND YADAV" worth investigating if WhatsApp
  reminders matter right now.
- `transaction_logs`: 0 rows - the 12 rows that existed were audit entries
  for the now-deleted original `payment_entries` and were cleaned up
  alongside them (they're not FK-linked, so they'd otherwise have become
  orphaned pointers to nonexistent `entry_id`s).

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

## Where to look next

- Root-level ad hoc scripts `cleanup_parties.ps1` / `test_cleanup.ps1` hit
  the running app's REST endpoints directly - useful examples of calling
  `/api/parties/*` from outside the UI.
- `scripts/inspect_excel.py` - a dev helper for inspecting the party-import
  Excel file structure (Python - check an interpreter is actually available
  before assuming this runs; it was not on the machine this skill was built
  on, see the [[register-reconciliation]] skill's Node-only workaround for
  the same constraint).
