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
Last full re-verification against source: 2026-07-18.

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

### Tables (7, schema `public`)

| Table | Key columns | Notes |
|---|---|---|
| `users` | id, username(unique), password(BCrypt), full_name, email, role, active, created_at | `role` CHECK constraint: `ADMIN,EMPLOYEE,ACCOUNTANT,MANAGER` (MANAGER added 2026-07-18) |
| `parties` | id, name, gst, **combined(unique)**, **trailing_number**, total_amount, phone, whatsapp_opt_in | `total_amount` = sum from Excel import, not live-recomputed. `trailing_number` (added 2026-07-12): the party's ledger code from the source Tally system, e.g. `78` for `CHANDRJEET B. M (JAIGAHA) 78` - nullable, most of the original 85 parties don't have one |
| `invoices` | id, invoice_number(unique), invoice_date, party_name, amount, description, delivery_mode, transport_number, **sales_vch_no**, **bags**, **rate_per_bag**, **created_by** | `delivery_mode` CHECK: `TRUCK,SELF_PICKUP,TROLLEY` (only `TRUCK` used in current data). `sales_vch_no` (added 2026-07-12): Tally Sales Register voucher number, only populated for imported rows (`invoice_number` prefix `TALLY-S-<vchNo>` for those). `bags`/`rate_per_bag` (added 2026-07-15, nullable - the 1,355 Tally-imported rows have neither): on the Add Invoice form, admins/accountants/managers now enter number-of-bags + rate/bag instead of a raw amount; `amount` is computed server-side (`InvoiceService.createInvoice()`, `amount = ratePerBag × bags`) and is **not** independently editable - the form's Amount field is a disabled, JS-updated display only, never submitted. `created_by` (added 2026-07-18, nullable varchar(50) - null for pre-existing/imported rows): username of whoever added the invoice (admin/accountant/manager) - powers `InvoiceService.getInvoicesCreatedBy()`/`getInvoicesCreatedByOnDate()`, which is how a Manager's dashboard scopes to "their" invoices (there's still no FK, just a plain string) |
| `payment_entries` | id, party_name, amount, mode_of_payment, entry_date, remarks, edited, edited_by, edited_at, employee_id(FK→users), **receipt_vch_no** | `mode_of_payment` CHECK: `CASH,CHEQUE,BANK_TRANSFER,UPI,NEFT,RTGS,DD`. Only real FK in the whole schema is `employee_id`. `receipt_vch_no` (added 2026-07-12): Tally Receipt Register voucher number for imported rows - **not unique**, Tally itself ran two parallel voucher-number series that collide with each other |
| `transaction_logs` | id, action, entry_id(no FK, just a Long), employee_name, employee_username, party_name, amount, mode_of_payment, entry_date, remarks, performed_by, notes, performed_at | Audit trail, denormalized snapshot per event, not linked by FK |
| `notification_logs` | id, party_name, phone, outstanding_amount, status, error_message, triggered_by, sent_at | `status` CHECK: `SENT,FAILED,DRY_RUN`. `triggered_by` = `"SCHEDULER"` or `"ADMIN:<user>"`/`"ACCOUNTANT:<user>"` |
| `notification_settings` | id(always 1, singleton row), daily_reminder_enabled, updated_by, updated_at | Controls whether the 5pm scheduler actually fires |

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
  now built** (added 2026-07-18, see its own section above) - a GitHub
  Actions workflow pings `GET /health` every ~10 min but only 06:00-22:00
  IST, so the instance is *deliberately* still allowed to sleep overnight.
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
  date-range filtering, history, **invoices** (`/admin/invoices` - also
  supports `from`/`to` date-range filtering on the Invoice List since
  2026-07-12, independent of the lifetime stats bar and the notification log
  section which stay unfiltered), **ledger** (`/admin/ledger?partyName=`,
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

## MANAGER role (added 2026-07-18)

A fourth role alongside ADMIN/EMPLOYEE/ACCOUNTANT, scoped to **only** adding
and viewing invoices - mirrors the EMPLOYEE dashboard's "add today's stuff
from your phone" UX but for invoices instead of payments.

- New `ManagerController` (`/manager/**`, `@PreAuthorize("hasRole('MANAGER')")`,
  class-level - unlike `AdminController` there's no need for per-method
  overrides since every method in this controller is manager-only anyway):
  `GET /manager/dashboard` (add-invoice form + "Today's Invoices" list,
  scoped via `InvoiceService.getInvoicesCreatedByOnDate(username, today)`),
  `POST /manager/invoices/add`, `GET /manager/invoices` (all-time, this
  manager's invoices only, via `getInvoicesCreatedBy(username)`).
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

## Keep-alive (added 2026-07-18) - the "Still open" note below about this is now stale, kept for history

- **New `GET /health`** (`AuthController`, `permitAll` in `SecurityConfig`)
  - deliberately touches neither DB nor session, just proves the JVM/
  servlet container is up.
- **`.github/workflows/keep-alive.yml`**: pings `/health` every ~10 minutes
  but **only during 00:30-16:30 UTC (06:00-22:00 IST)** - three separate
  `cron:` entries to hit that exact half-hour-aligned window (cron can't
  express "every 10 min from :30 to :30" as one expression). Outside that
  window there's simply no scheduled entry, so the free Render instance is
  **allowed to idle-sleep overnight by design** (explicit user choice, not
  an oversight) - it wakes on the first 6 AM ping or an earlier real
  visitor. Also has `workflow_dispatch: {}` for manual triggering/testing
  from the GitHub Actions tab.
- Render's free-tier ~750 instance-hours/month easily covers a ~16h/day
  active window, so this doesn't risk exceeding the free plan.

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
  built since the Tally import (`be798cf`) through the PWA/responsive work
  (`a65a4b2` as of 2026-07-15) - caching, compression/lazy-loading, Docker/
  Render deploy config, bags×rate auto-calc, PWA - read commit messages
  there for anything this skill doesn't cover yet, rather than assuming the
  feature set is frozen at what's written above.

## Still open / not yet done (updated 2026-07-18)

- PWA/responsive treatment not yet applied to the 16 admin-facing
  templates - only login + the 4 employee pages (and now `manager/*`, see
  MANAGER role section) have it.
- `SINGH BUILDNG MATERIAL (LALGANG)` (DB id 59) party-matching ambiguity
  from the Tally import - still unresolved, see "Tally import" section
  above.
- `InvoiceService.createInvoice()`'s check-then-act race on
  `invoice_number` doesn't catch the resulting DB constraint-violation
  exception the way `ExcelPartyService.ensureExists()` does - see
  "Concurrency" section above. Explicitly left as-is by user decision.
