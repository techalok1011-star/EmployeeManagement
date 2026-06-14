# Employee Management System - Architecture & Design

## Table of Contents
1. [System Overview](#system-overview)
2. [High-Level Architecture](#high-level-architecture)
3. [Key Components](#key-components)
4. [Data Model](#data-model)
5. [User Roles & Permissions](#user-roles--permissions)
6. [Core Workflows](#core-workflows)
7. [Security Architecture](#security-architecture)
8. [Database Schema](#database-schema)
9. [API/Request Flow](#apirequest-flow)
10. [Key Features](#key-features)

---

## System Overview

The **Employee Management System** is a web-based application designed to manage **payment entry records** for employees with two distinct user roles: **Admin** and **Employee**. 

**Purpose:**
- Employees create and manage daily payment entries (cash/cheque/bank transfers)
- Admins oversee all entries, manage employee accounts, and maintain full audit trails
- System maintains complete transaction history and tracks who edited what and when

**Technology Stack:**
- **Framework:** Spring Boot 3.2.0 (Java 17)
- **Web:** Spring Web (Thymeleaf templates)
- **Security:** Spring Security with role-based access control
- **Database:** H2 (in-memory for demo, easily swappable)
- **Architecture:** MVC (Model-View-Controller) Pattern

---

## High-Level Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│                     WEB BROWSER (User Interface)                  │
│                       Thymeleaf Templates                         │
└────────────────────────┬─────────────────────────────────────────┘
                         │ HTTP/HTTPS
                         ▼
┌──────────────────────────────────────────────────────────────────┐
│                    SPRING BOOT APPLICATION                        │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │ 📡 CONTROLLERS (Request Handlers)                          │  │
│  │ ├─ AuthController (Login/Logout)                          │  │
│  │ ├─ EmployeeController (Employee Dashboard & Entries)      │  │
│  │ └─ AdminController (Admin Dashboard & Management)         │  │
│  └────────────────────────────────────────────────────────────┘  │
│                         │                                         │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │ 🔐 SECURITY LAYER                                          │  │
│  │ ├─ Spring Security Filter Chain                            │  │
│  │ ├─ Role-based Access Control (@PreAuthorize)              │  │
│  │ ├─ CustomUserDetailsService (User Authentication)         │  │
│  │ └─ BCrypt Password Encryption                             │  │
│  └────────────────────────────────────────────────────────────┘  │
│                         │                                         │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │ 💼 BUSINESS LOGIC (Services)                               │  │
│  │ ├─ PaymentEntryService (CRUD & Operations)                │  │
│  │ └─ UserService (User Management)                          │  │
│  └────────────────────────────────────────────────────────────┘  │
│                         │                                         │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │ 📊 DATA ACCESS LAYER (Repositories)                        │  │
│  │ ├─ UserRepository                                          │  │
│  │ ├─ PaymentEntryRepository                                 │  │
│  │ └─ TransactionLogRepository                               │  │
│  └────────────────────────────────────────────────────────────┘  │
└──────────────────────────┬───────────────────────────────────────┘
                           │ JDBC/SQL
                           ▼
                    ┌─────────────────┐
                    │   H2 Database   │
                    │   (In-Memory)   │
                    └─────────────────┘
```

---

## Key Components

### 1. **Presentation Layer** (Thymeleaf Templates)

| Component | Location | Purpose |
|-----------|----------|---------|
| **login.html** | `src/main/resources/templates/` | Authentication page for both roles |
| **admin/dashboard.html** | `src/main/resources/templates/admin/` | Admin overview with all employees & recent entries |
| **employee/dashboard.html** | `src/main/resources/templates/employee/` | Employee daily summary & quick add entry form |
| **admin/employees.html** | `src/main/resources/templates/admin/` | Manage employees (add, toggle status, reset password) |
| **admin/entries.html** | `src/main/resources/templates/admin/` | View/edit all entries |
| **employee/entries.html** | `src/main/resources/templates/employee/` | View own entries (grouped by day) |
| **admin/history.html** | `src/main/resources/templates/admin/` | Full audit log (all transactions) |
| **employee/history.html** | `src/main/resources/templates/employee/` | Personal transaction history |

### 2. **Controller Layer** (Request Handlers)

#### **AuthController**
- Handles login/logout pages
- Routes unauthenticated users to `/login`
- Redirects root path `/` to login

#### **EmployeeController** (`/employee/**`)
- **@PreAuthorize("hasRole('EMPLOYEE')")** — Only employees can access
- **Dashboard** (`GET /employee/dashboard`) — Shows today's entries & quick-add form
- **Create Entry** (`POST /employee/entries/add`) — Add payment entry (locked to today only)
- **View Entries** (`GET /employee/entries`) — See all entries grouped by day
- **Edit Entry** (`GET/POST /employee/entries/{id}/edit`) — Edit today's entries only
- **Delete Entry** (`POST /employee/entries/{id}/delete`) — Remove entry
- **Transaction History** (`GET /employee/history`) — Audit log for own entries

#### **AdminController** (`/admin/**`)
- **@PreAuthorize("hasRole('ADMIN')")** — Only admins can access
- **Dashboard** (`GET /admin/dashboard`) — Overview of all employees & entries
- **Employee Management:**
  - List all employees and create new ones
  - View individual employee profile & entries
  - Toggle employee active/inactive status
  - Reset employee passwords
- **Entry Management:**
  - View all entries across all employees
  - Edit any entry (any date, remarks mandatory)
  - Delete entries
- **Transaction History** (`GET /admin/history`) — Complete audit log

### 3. **Service Layer** (Business Logic)

#### **PaymentEntryService**
Core service managing all payment entry operations:

| Operation | Method | Role Access | Key Rules |
|-----------|--------|-------------|-----------|
| Create Entry | `createEntry()` | Employees | Date locked to TODAY only |
| Get Employee Entries | `getEntriesForEmployee()` | Employees | Own entries only |
| Get Today's Entries | `getTodayEntriesForEmployee()` | Employees | Quick dashboard access |
| Get Daily Summary | `getDailySummaryForEmployee()` | Employees | Today's total + count |
| Get Grouped Entries | `getEntriesGroupedByDayForEmployee()` | Employees | Entries grouped by date (newest first) |
| Get All Entries | `getAllEntries()` | Admins | Company-wide view |
| Update Entry (Employee) | `updateEntryByEmployee()` | Employees | Today's entries only, remarks mandatory |
| Update Entry (Admin) | `updateEntry()` | Admins | Any date, any entry, remarks mandatory |
| Delete Entry (Employee) | `deleteEntryByEmployee()` | Employees | Own entries only |
| Delete Entry (Admin) | `deleteEntry()` | Admins | Any entry |
| Get Transaction Logs | `getTransactionLogsForEmployee()` | Employees | Own action history |
| Get All Transaction Logs | `getAllTransactionLogs()` | Admins | Company-wide audit |

**Auto-Audit Features:**
- Every CREATE/UPDATE/DELETE is logged with:
  - Who performed the action
  - What changed (detailed diff for updates)
  - Timestamp
  - Entry details (party name, amount, mode, etc.)

#### **UserService**
Manages user & employee administration:
- Create new employees
- Retrieve users by ID or username
- Toggle employee active/inactive status
- Reset employee passwords
- Get all employees

### 4. **Repository Layer** (Data Access)

| Repository | Key Methods |
|-----------|------------|
| **UserRepository** | `findByUsername()`, `findById()`, `findAll()` |
| **PaymentEntryRepository** | `findByEmployee()`, `findByEmployeeAndEntryDate()`, custom aggregate queries (sum, count) |
| **TransactionLogRepository** | `findByEmployeeUsername()`, `findAll()` |

---

## Data Model

### **User Entity** (users table)
Represents system users (Admins and Employees).

```
┌─────────────────────────────────────────┐
│            USER (users)                  │
├─────────────────────────────────────────┤
│ id (PK)              [Long, AUTO]        │
│ username (UNIQUE)    [String]            │
│ password             [String, hashed]    │
│ fullName             [String]            │
│ email                [String]            │
│ role                 [ADMIN | EMPLOYEE]  │
│ active               [Boolean, default=true] │
│ createdAt            [LocalDateTime]     │
│ paymentEntries (1→M) [List<PaymentEntry>]│
└─────────────────────────────────────────┘
```

**Relationships:**
- One User can have Many PaymentEntries (1 → ∞)
- Admins manage entries; Employees create entries

---

### **PaymentEntry Entity** (payment_entries table)
Represents a single payment transaction.

```
┌──────────────────────────────────────────────┐
│        PAYMENT ENTRY (payment_entries)       │
├──────────────────────────────────────────────┤
│ id (PK)              [Long, AUTO]            │
│ partyName            [String]                │
│ amount               [BigDecimal]            │
│ modeOfPayment        [CASH|CHEQUE|UPI|...]  │
│ entryDate            [LocalDate]             │
│ remarks              [String, optional]     │
│ employee_id (FK)     [User.id]              │
│ createdAt            [LocalDateTime]        │
│ updatedAt            [LocalDateTime]        │
│ edited               [Boolean]              │
│ editedBy             [ADMIN|EMPLOYEE|null]  │
│ editedAt             [LocalDateTime]        │
└──────────────────────────────────────────────┘
```

**Payment Modes Supported:**
- CASH, CHEQUE, BANK_TRANSFER, UPI, NEFT, RTGS, DD (Demand Draft)

**Audit Fields:**
- `edited` — tracks if entry was modified after creation
- `editedBy` — who made the last edit
- `editedAt` — when was it last edited

---

### **TransactionLog Entity** (transaction_logs table)
Complete audit trail of all operations.

```
┌─────────────────────────────────────────────┐
│      TRANSACTION LOG (transaction_logs)      │
├─────────────────────────────────────────────┤
│ id (PK)              [Long, AUTO]           │
│ action               [CREATE|UPDATE|DELETE] │
│ entryId              [Long, nullable]       │
│ employeeName         [String]               │
│ employeeUsername     [String]               │
│ partyName            [String]               │
│ amount               [BigDecimal]           │
│ modeOfPayment        [String]               │
│ entryDate            [LocalDate]            │
│ remarks              [String]               │
│ performedBy          [String, username]     │
│ notes                [String (1000 chars)]  │
│ performedAt          [LocalDateTime]        │
└─────────────────────────────────────────────┘
```

**Notes Field Examples:**
- For CREATE: `"Entry created"`
- For UPDATE: `"Party: 'Old' → 'New'; Amount: ₹100 → ₹200"`
- For DELETE: `"Entry deleted"`

---

## User Roles & Permissions

### **ADMIN Role**
| Feature | Capability |
|---------|-----------|
| Dashboard | View all employees, recent entries, summary statistics |
| Employee Mgmt | Create, activate/deactivate, reset passwords, view profiles |
| Entry Mgmt | View ALL entries, edit ANY entry (any date), delete ANY entry |
| Constraints | Remarks mandatory when editing; all changes logged |
| History | Full company-wide audit log with search |

### **EMPLOYEE Role**
| Feature | Capability |
|---------|-----------|
| Dashboard | View today's summary, add new entry |
| Entry Creation | Add NEW entries ONLY for TODAY's date |
| Entry View | See own entries grouped by day |
| Entry Edit | Edit ONLY TODAY's entries; past entries locked |
| Entry Delete | Delete ONLY OWN entries |
| Constraints | Cannot change entry date; remarks mandatory when editing |
| History | View own transactions & who edited them |

---

## Core Workflows

### **Workflow 1: Employee Creates Payment Entry**

```
Employee Opens App
       ↓
Login with credentials
       ↓
Spring Security validates → CustomUserDetailsService
       ↓
Redirected to /employee/dashboard (role detected)
       ↓
Dashboard loads:
  • Get today's summary (PaymentEntryService.getDailySummaryForEmployee)
  • Get today's entries (PaymentEntryService.getTodayEntriesForEmployee)
  • Display form for new entry
       ↓
Employee fills form (Party, Amount, Mode, Remarks)
       ↓
Form submitted to POST /employee/entries/add
       ↓
EmployeeController validates input
  • Date forcibly set to TODAY (cannot change)
  • Amount must be positive
  • Party name required
       ↓
PaymentEntryService.createEntry():
  • Find employee from authenticated user
  • Create PaymentEntry entity
  • Save to database
  • Log action to TransactionLog → "Entry created"
       ↓
Success message shown
Employee redirected to dashboard
```

**Key Point:** Entry date is **always locked to TODAY** at creation. Employees cannot create backdated entries.

---

### **Workflow 2: Admin Edits Employee's Entry**

```
Admin navigates to /admin/entries
       ↓
Gets ALL entries from PaymentEntryService.getAllEntries()
       ↓
Admin clicks Edit on an entry
       ↓
GET /admin/entries/{id}/edit
  • Fetch entry details
  • Show form (party, amount, mode, date, remarks)
       ↓
Admin modifies entry (ANY date allowed, including past entries)
       ↓
POST /admin/entries/{id}/edit with new data
       ↓
PaymentEntryService.updateEntry():
  • Verify remarks not empty (mandatory)
  • Compute diff (what changed)
  • Update entry: partyName, amount, mode, date, remarks
  • Set: edited=true, editedBy="ADMIN", editedAt=NOW
  • Save to database
  • Log to TransactionLog with detailed diff
       ↓
Success message
Admin redirected to entries list
```

**Key Difference from Employee Edit:**
- ✅ Admin can change date (past/future)
- ✅ Admin can edit ANY entry
- ✅ Remarks tracked for accountability

**Employee Edit (same entry):**
- ❌ Cannot change date (forced to today)
- ❌ Cannot edit past entries (only today's)
- ❌ Remarks mandatory

---

### **Workflow 3: Employee Views Audit History**

```
Employee clicks "Transaction History"
       ↓
GET /employee/history
       ↓
PaymentEntryService.getTransactionLogsForEmployee(username)
       ↓
Query returns all logs where employeeUsername = logged-in user
       ↓
Display:
  • Timestamp (dd MMM yyyy, hh:mm:ss a)
  • Action (CREATE / UPDATE / DELETE)
  • What changed (diff)
  • Who did it (admin or self)
  • Entry details (party, amount, mode, date)
       ↓
Employee can see full history of:
  - Own entries created
  - Admin edits made to own entries
  - Own deletions
```

---

## Security Architecture

### **Authentication Flow**

```
┌─────────────────────────────────────────────────────┐
│     1. User visits /login                           │
│        (AuthController.loginPage)                   │
└─────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────┐
│     2. User submits credentials (POST /login)       │
│        Spring Security intercepts                   │
└─────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────┐
│     3. DaoAuthenticationProvider verifies:          │
│        • CustomUserDetailsService finds user        │
│        • BCryptPasswordEncoder tests password       │
└─────────────────────────────────────────────────────┘
                        ↓
                    ✅ PASS?
                    /    \
                   /      \
                 YES       NO
                 /         \
                ↓           ↓
          ┌──────────┐   ┌──────────────┐
          │ Create   │   │ Reject +     │
          │Session   │   │ Redirect to  │
          │ + Auth   │   │ /login?error │
          └──────────┘   └──────────────┘
                ↓
┌─────────────────────────────────────────────────────┐
│     4. Read user's role (ADMIN or EMPLOYEE)         │
│        SuccessHandler determines redirect:         │
│        • ADMIN → /admin/dashboard                  │
│        • EMPLOYEE → /employee/dashboard            │
└─────────────────────────────────────────────────────┘
```

### **Authorization Rules** (SecurityConfig.java)

```
Public Access (Permitted):
  ├─ /css/** (stylesheets)
  ├─ /js/** (javascripts)
  ├─ /images/** (images)
  ├─ /login (login page)
  ├─ /logout (logout endpoint)
  └─ /h2-console/** (database console, dev-only)

Protected by Role:
  ├─ /admin/** → @PreAuthorize("hasRole('ADMIN')")
  ├─ /employee/** → @PreAuthorize("hasRole('EMPLOYEE')")
  └─ All others → @AuthenticatedUser required

Password Security:
  └─ BCryptPasswordEncoder (salt + hash, ~10 ms per check)
```

### **Row-Level Security** (Business Logic)

Beyond Spring Security roles, the service layer enforces business rules:

```
Employee Operations:
  ├─ Can only VIEW own entries (checked by username)
  ├─ Can only EDIT entries where entryDate == TODAY
  ├─ Can only EDIT entries they created (ownership check)
  ├─ Cannot DELETE past entries
  └─ All changes logged with "performedBy" = username

Admin Operations:
  ├─ Can VIEW/EDIT/DELETE any entry (no ownership check)
  ├─ Can change dates to past/future (unrestricted)
  ├─ Can manage all employees (create, activate, reset password)
  └─ Full access to audit logs
```

### **Validation & Error Handling**

```
Input Validation (Java Bean Validation):
  ├─ @NotNull, @NotBlank on required fields
  ├─ @DecimalMin on amounts
  └─ Result handled by BindingResult

Business Rule Validation:
  ├─ Employee entry date enforcement
  ├─ Remarks-mandatory-on-edit rule
  ├─ Ownership verification
  └─ Past-entry lock for employees

Error Responses:
  ├─ Access denied → 403 (Spring Security)
  ├─ Business rule violation → Flash message + redirect
  ├─ Not found (entry) → RuntimeException → Flash message
  └─ UI displays error messages via Thymeleaf
```

---

## Database Schema

### **DDL (Automatically Created by Spring)**

```sql
CREATE TABLE users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,  -- 'ADMIN' or 'EMPLOYEE'
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE payment_entries (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    party_name VARCHAR(255) NOT NULL,
    amount NUMERIC(15,2) NOT NULL,
    mode_of_payment VARCHAR(20) NOT NULL,
    entry_date DATE NOT NULL,
    remarks VARCHAR(500),
    employee_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    edited BOOLEAN DEFAULT FALSE,
    edited_by VARCHAR(20),  -- 'ADMIN' or 'EMPLOYEE'
    edited_at TIMESTAMP,
    FOREIGN KEY (employee_id) REFERENCES users(id)
);

CREATE TABLE transaction_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    action VARCHAR(20) NOT NULL,  -- 'CREATE', 'UPDATE', or 'DELETE'
    entry_id BIGINT,
    employee_name VARCHAR(255) NOT NULL,
    employee_username VARCHAR(255) NOT NULL,
    party_name VARCHAR(255) NOT NULL,
    amount NUMERIC(15,2),
    mode_of_payment VARCHAR(50),
    entry_date DATE,
    remarks VARCHAR(500),
    performed_by VARCHAR(255) NOT NULL,  -- who made the change
    notes VARCHAR(1000),  -- what changed (diff)
    performed_at TIMESTAMP NOT NULL
);
```

### **Sample Data (DataSeeder.java)**

On startup, system creates:

```
ADMIN:
  username: admin
  password: admin123 (BCrypt hashed)
  role: ADMIN

EMPLOYEES:
  1. rahul.sharma / emp123
  2. priya.patel / emp123
  3. amit.kumar / emp123
```

---

## API/Request Flow

### **Request Flow Diagram**

```
CLIENT (Browser)
       ↓
       │ HTTP Request (GET/POST with credentials)
       ↓
SPRING SECURITY FILTER CHAIN
       ├─ Check if path is public (CSS, login, logout, h2-console)
       ├─ If public → Allow through
       ├─ If protected → Check session/token
       │   └─ No session → Redirect to /login
       │   └─ Has session → Continue
       └─ Verify role (@PreAuthorize)
           └─ Wrong role → 403 Forbidden
           └─ Correct role → Continue
       ↓
CONTROLLER
       ├─ Extract @PathVariable, @RequestParam, @ModelAttribute
       ├─ Get Authentication object (current user)
       ├─ Call SERVICE methods
       └─ Populate Model with data
       ↓
SERVICE LAYER
       ├─ Apply business logic
       ├─ Fetch/save from REPOSITORIES
       ├─ Enforce business rules
       ├─ Log to TransactionLog if CRUD
       └─ Return result
       ↓
REPOSITORY LAYER
       ├─ Send SQL queries to database
       ├─ Map results to JPA entities
       └─ Return to service
       ↓
RESPONSE BUILDER
       ├─ Redirect (for mutations)
       ├─ Or render Thymeleaf template (for views)
       └─ Return HTML/redirect to client
       ↓
CLIENT (Browser)
       └─ Display page or refresh
```

### **Example: Create Entry Request**

```
POST /employee/entries/add

Request Data:
  partyName: "XYZ Supply Co"
  amount: 5000.00
  modeOfPayment: "BANK_TRANSFER"
  remarks: "Invoice #12345"

Flow:
  1. EmployeeController.addEntry(request, bindingResult, auth, ...)
  2. Validate request (@Valid)
  3. Force date = TODAY (ignore user input)
  4. Get username from Authentication: auth.getName()
  5. Call PaymentEntryService.createEntry(request, username)
  6. Service:
     - Finds User from DB
     - Creates PaymentEntry entity
     - Saves to DB
     - Logs "CREATE" action to TransactionLog
  7. Return success message
  8. Redirect to /employee/dashboard
  9. Browser shows entry in today's list
```

---

## Key Features

### **1. Multi-Level Access Control**
- **Spring Security** handles authentication & authorization
- **@PreAuthorize** annotations enforce role checks
- **Business logic** adds row-level security (ownership checks)

### **2. Immutable Audit Trail**
- Every CREATE/UPDATE/DELETE operation logged
- Includes who did it, when, and what changed
- TransactionLog table is append-only (immutable history)

### **3. Employee Constraints**
```
Entry Creation:  ✓ Today only, cannot backdate
Entry Editing:   ✓ Today's entries only, past locked
Entry Deletion:  ✓ Own entries only
Remarks:         ✓ Mandatory on edit
```

### **4. Admin Flexibility**
```
Entry Creation:  ✓ Any date, any employee, any amount
Entry Editing:   ✓ Any date (past/future), any entry
Entry Deletion:  ✓ Any entry
Employee Mgmt:   ✓ Create, suspend, reset password
Full Audit:      ✓ See all changes by all users
```

### **5. Data Grouping & Aggregation**
- Entries grouped by date (newest first)
- Daily totals (count + sum amount)
- Employee summaries on dashboard
- Full history searchable in transaction log

### **6. Easy Extensibility**
```
To add a new payment mode:
  → PaymentEntry.ModeOfPayment enum (add line)
  → Automatically available in dropdowns

To add a new user field:
  → User entity (add column)
  → Migration (auto-handled by Spring)

To add a new role:
  → User.Role enum
  → SecurityConfig @PreAuthorize rules
  → Controllers with new @PreAuthorize annotation
```

---

## Deployment & Running

### **Start the Application**
```bash
mvn spring-boot:run
```

Application runs on **http://localhost:8080**

### **Database Console** (H2 in-memory)
Navigate to **http://localhost:8080/h2-console**
- JDBC URL: `jdbc:h2:mem:empdb`
- Username: `SA`
- Password: (blank)

### **Login Credentials**
```
Admin:
  Username: admin
  Password: admin123

Employee (any):
  Username: rahul.sharma / priya.patel / amit.kumar
  Password: emp123
```

---

## Summary

The Employee Management System is a **role-based, audit-rich application** built on Spring Boot that manages payment entries with strict business rules for employees and flexible admin capabilities. The architecture is clean, secure, and built to scale with:

- Clear separation of concerns (Controller → Service → Repository)
- Comprehensive audit logging
- Role-based access control with business rule enforcement
- Data integrity through immutable transaction logs
- Easy to extend and maintain

Each layer has clear responsibilities, making the codebase maintainable and testable.

