# Employee Management System - Visual Flow & Interaction Guide

This document provides simple, visual explanations of how the system works and how different users interact with it.

---

## Quick System Overview

### What is this system for?

```
┌─────────────────────────────────────────────────────────┐
│ EMPLOYEES                                               │
│ ├─ Create payment entries daily (cash, cheque, etc.)   │
│ ├─ Record: who paid, how much, via which mode         │
│ ├─ Can edit only TODAY's entries                        │
│ └─ Cannot see other employees' entries                 │
└─────────────────────────────────────────────────────────┘

                         SYSTEM TRACKS ALL


┌─────────────────────────────────────────────────────────┐
│ ADMINS                                                  │
│ ├─ See all employees' entries (past & present)         │
│ ├─ Can edit/delete any entry (any date)                │
│ ├─ Manage employee accounts (create, activate)         │
│ ├─ View complete audit trail (who did what)            │
│ └─ Reset passwords & supervise                         │
└─────────────────────────────────────────────────────────┘
```

---

## User Login & Role-Based Redirection

```
                        USER OPENS APP
                              │
                              ↓
                     ┌─────────────────┐
                     │   Login Page    │
                     │ Enter username  │
                     │ Enter password  │
                     └─────────────────┘
                              │
                              ↓ (SUBMIT)
                     ┌─────────────────┐
                     │ System Checks:  │
                     │ 1. User exists? │
                     │ 2. Password ok? │
                     │ 3. Account ok?  │
                     └─────────────────┘
                              │
                ┌─────────────┴─────────────┐
                │                           │
           ✅ VALID                    ❌ INVALID
                │                           │
                ↓                           ↓
        ┌───────────────┐           ┌──────────────┐
        │Read User Role │           │Show Error:   │
        └───────────────┘           │Invalid creds │
                │                   └──────────────┘
        ┌───────┴───────┐
        │               │
    [ADMIN]        [EMPLOYEE]
        │               │
        ↓               ↓
  /admin/         /employee/
  dashboard       dashboard
```

---

## EMPLOYEE Dashboard & Workflow

```
EMPLOYEE LOGS IN
       │
       ↓
   DASHBOARD
   ┌─────────────────────────────────────────┐
   │ 📊 TODAY'S SUMMARY                      │
   │ ├─ Total entries today: 3               │
   │ ├─ Total amount: ₹15,000                │
   │ └─ Time logged in: 10:30 AM             │
   │                                         │
   │ 🔴 QUICK ADD ENTRY FORM                 │
   │ ├─ Party Name: [textbox]                │
   │ ├─ Amount: [textbox]                    │
   │ ├─ Payment Mode: [dropdown]             │
   │ ├─ Remarks: [textbox]                   │
   │ └─ [ADD ENTRY button]                   │
   │                                         │
   │ 📝 TODAY'S ENTRIES (Quick View)          │
   │ └─ Entry 1: ABC Corp, ₹5000, CASH       │
   │ └─ Entry 2: XYZ Ltd, ₹7000, CHEQUE      │
   │ └─ Entry 3: PQR Inc, ₹3000, UPI         │
   └─────────────────────────────────────────┘

EMPLOYEE CAN:
   • Click "ALL ENTRIES" → See all past entries grouped by date
   • Click "EDIT" on today's entry → Modify details
   • Click "DELETE" → Remove today's entry
   • Click "HISTORY" → See audit log (who changed what)
   • Click "LOGOUT" → Leave system

EMPLOYEE CANNOT:
   ✗ Edit yesterday's entries (locked)
   ✗ Add backdated entries
   ✗ See other employees' entries
   ✗ Delete past entries
   ✗ View audit logs of other employees
```

### Employee Viewing Entries by Day

```
ALL ENTRIES View
   ├─ Friday, 22 May 2026
   │  ├─ Entry: ABC Corp, ₹5000 [EDIT] [DELETE]
   │  ├─ Entry: XYZ Ltd, ₹7000 [EDIT] [DELETE]
   │  └─ Day Total: ₹12,000 (2 entries)
   │
   ├─ Thursday, 21 May 2026
   │  ├─ Entry: PQR Inc, ₹3000 [EDIT - DISABLED] [DELETE - DISABLED]
   │  └─ Day Total: ₹3,000 (1 entry)
   │
   └─ Wednesday, 20 May 2026
      ├─ Entry: ABC Corp, ₹2000 [EDIT - DISABLED] [DELETE - DISABLED]
      ├─ Entry: XYZ Ltd, ₹4000 [EDIT - DISABLED] [DELETE - DISABLED]
      └─ Day Total: ₹6,000 (2 entries)

RULE ENFORCEMENT:
   • Only TODAY's entries have EDIT/DELETE enabled
   • Past entries are READ-ONLY
   • All entries show: who created, when, what changed
```

---

## ADMIN Dashboard & Workflow

```
ADMIN LOGS IN
       │
       ↓
   DASHBOARD
   ┌──────────────────────────────────────────────┐
   │ 👥 ACTIVE EMPLOYEES: 3                       │
   │ ├─ rahul.sharma (Email: rahul@...)           │
   │ ├─ priya.patel (Email: priya@...)            │
   │ └─ amit.kumar (Email: amit@...)              │
   │                                              │
   │ 📊 RECENT ENTRIES (Across All Employees)    │
   │ ├─ rahul.sharma: ABC Corp, ₹5000 (CASH)     │
   │ ├─ priya.patel: XYZ Ltd, ₹7000 (CHEQUE)    │
   │ ├─ amit.kumar: PQR Inc, ₹3000 (BANK)       │
   │ └─ ... (showing latest first)                │
   │                                              │
   │ 🔐 ADMIN CONTROLS NAVBAR                     │
   │ ├─ [DASHBOARD] [EMPLOYEES] [ENTRIES]        │
   │ ├─ [HISTORY] [SETTINGS] [LOGOUT]            │
   └──────────────────────────────────────────────┘

ADMIN CAN:
   • Navigate to EMPLOYEES section
   • Navigate to ENTRIES section
   • View complete HISTORY (all changes, all users)
   • Create new employee accounts
   • Activate/Deactivate employees
   • Reset employee passwords
   • Edit any entry (including past entries)
   • Delete any entry
   • View full audit trail
```

### Admin Employee Management

```
EMPLOYEES PAGE
   ┌────────────────────────────────────────────────┐
   │ LIST OF EMPLOYEES                              │
   │                                                │
   │ 1. rahul.sharma                                │
   │    Email: rahul@company.com                    │
   │    Status: ✅ ACTIVE (since 01 Jan 2026)      │
   │    [VIEW PROFILE] [TOGGLE-STATUS] [RESET]    │
   │                                                │
   │ 2. priya.patel                                 │
   │    Email: priya@company.com                    │
   │    Status: ✅ ACTIVE (since 15 Jan 2026)      │
   │    [VIEW PROFILE] [TOGGLE-STATUS] [RESET]    │
   │                                                │
   │ 3. amit.kumar                                  │
   │    Email: amit@company.com                     │
   │    Status: ✅ ACTIVE (since 10 Feb 2026)      │
   │    [VIEW PROFILE] [TOGGLE-STATUS] [RESET]    │
   │                                                │
   │ ┌─────────────────────────┐                   │
   │ │ NEW EMPLOYEE FORM       │                   │
   │ ├─────────────────────────┤                   │
   │ │ Username: [textbox]     │                   │
   │ │ Email: [textbox]        │                   │
   │ │ Full Name: [textbox]    │                   │
   │ │ Password: [textbox]     │                   │
   │ │ [CREATE] [RESET-FORM]  │                   │
   │ └─────────────────────────┘                   │
   └────────────────────────────────────────────────┘

ADMIN ACTIONS:
   • [VIEW PROFILE] → See all entries, payments stats
   • [TOGGLE-STATUS] → Deactivate/Reactivate
   • [RESET] → Set new password for employee
   • [CREATE] → Add new employee account
```

### Admin Viewing an Employee's Profile

```
EMPLOYEE DETAIL PAGE (for rahul.sharma)
   ┌────────────────────────────────────────────────┐
   │ 👤 rahul.sharma                                │
   │    Name: Rahul Sharma                          │
   │    Email: rahul@company.com                    │
   │    Status: ✅ ACTIVE                           │
   │    Created: 01 Jan 2026                        │
   │    [TOGGLE STATUS] [RESET PASSWORD]           │
   │                                                │
   │ 📊 STATS                                       │
   │    Total Entries: 127                          │
   │    This Month: 45                              │
   │    This Week: 12                               │
   │    This Day: 3                                 │
   │                                                │
   │ 📝 ALL ENTRIES (from all dates)               │
   │    [Edit Entry / Delete Entry available]      │
   │                                                │
   │    Fri, 22 May 2026:                          │
   │    ├─ ABC Corp, ₹5000, CASH                   │
   │    │  Created: 10:30 AM | Last Edit: 10:45 AM │
   │    │  (Admin edited on 22 May 10:50 AM)       │
   │    │  [EDIT] [DELETE]                         │
   │    └─ XYZ Ltd, ₹7000, CHEQUE                  │
   │       Created: 11:00 AM | Not edited          │
   │       [EDIT] [DELETE]                         │
   │                                                │
   │    Thu, 21 May 2026:                          │
   │    └─ PQR Inc, ₹3000, BANK                    │
   │       Created: 09:30 AM | Not edited          │
   │       [EDIT] [DELETE]                         │
   └────────────────────────────────────────────────┘
```

### Admin Editing an Entry

```
ADMIN CLICKS [EDIT] ON ENTRY
       │
       ↓
   EDIT FORM (ANY DATE ALLOWED)
   ┌───────────────────────────────────┐
   │ EDIT ENTRY                        │
   │                                   │
   │ Entry ID: 42                      │
   │ Created By: rahul.sharma          │
   │ Created At: 22 May, 10:30 AM     │
   │ Last Edited By: ADMIN (you)      │
   │ Last Edited At: 22 May, 10:50 AM │
   │                                   │
   ├─────────────────────────────────  │
   │ Party Name: [XYZ Ltd]             │
   │ Amount: [7000.00]                 │
   │ Mode: [CHEQUE ▼]                  │
   │ Entry Date: [22-05-2026]          │
   │ Remarks: [Paid as per invoice]   │
   │                                   │
   │ [UPDATE] [CANCEL]                 │
   └───────────────────────────────────┘

   Admin can:
   ✓ Change party name
   ✓ Change amount
   ✓ Change payment mode
   ✓ Change entry DATE (past or future!)
   ✓ Add/modify remarks (MANDATORY)

   System will:
   1. Compare old vs new values
   2. Create diff:
      "Amount: ₹7000 → ₹7500; Remarks: added new comment"
   3. Log to TransactionLog:
      - Action: UPDATE
      - PerformedBy: admin (current user)
      - Notes: (the diff above)
      - Timestamp: NOW
   4. Mark entry as: edited=true, editedBy="ADMIN"
   5. Redirect to entries list with success message
```

---

## ALL ENTRIES (Admin View)

```
ENTRIES PAGE (Admin view)
   ┌──────────────────────────────────────────────────┐
   │ ALL PAYMENT ENTRIES (Across All Employees)      │
   │                                                  │
   │ Fri, 22 May 2026:                              │
   │ ├─ rahul.sharma: ABC Corp, ₹5000, CASH         │
   │ │  Created: 10:30 | Edited by ADMIN (10:50)    │
   │ │  [EDIT] [DELETE]                             │
   │ ├─ priya.patel: XYZ Ltd, ₹7000, CHEQUE         │
   │ │  Created: 11:00 | Not edited                 │
   │ │  [EDIT] [DELETE]                             │
   │ └─ amit.kumar: PQR Inc, ₹3000, BANK            │
   │    Created: 11:30 | Not edited                 │
   │    [EDIT] [DELETE]                             │
   │                                                 │
   │ Thu, 21 May 2026:                              │
   │ ├─ rahul.sharma: ABC Corp, ₹2000, CASH         │
   │ │  Created: 09:30 | Not edited                 │
   │ │  [EDIT] [DELETE]                             │
   │ └─ priya.patel: ABC Corp, ₹1500, UPI           │
   │    Created: 15:45 | Edited by EMPLOYEE (16:00) │
   │    [EDIT] [DELETE]                             │
   │                                                 │
   │ [Previous dates...]                            │
   └──────────────────────────────────────────────────┘

KEY INFO FOR ADMIN:
   ✓ Who created entry (employee name)
   ✓ When it was created
   ✓ If/when it was edited
   ✓ Who edited it (ADMIN or EMPLOYEE)
   ✓ Quick [EDIT] and [DELETE] buttons
   ✓ All dates in past are editable by admin
```

---

## Transaction History / Audit Log

### Employee History View

```
EMPLOYEE HISTORY PAGE
   ┌─────────────────────────────────────────────────┐
   │ MY TRANSACTION HISTORY                          │
   │ (Only my entries and changes to my entries)    │
   │                                                 │
   │ 22 May 2026, 10:30:45 AM                       │
   │ ├─ ACTION: CREATE                              │
   │ ├─ Entry: Party(ABC Corp) | Amt(₹5000)        │
   │ ├─ Mode: CASH                                 │
   │ ├─ Performed By: rahul.sharma (me)            │
   │ └─ Note: Entry created                        │
   │                                                 │
   │ 22 May 2026, 10:45:30 AM                       │
   │ ├─ ACTION: UPDATE                              │
   │ ├─ Entry: Party(ABC Corp) | Amt(₹5000)        │
   │ ├─ Performed By: admin                        │
   │ └─ Changes: Amount: ₹5000 → ₹5500            │
   │            Remarks: added new comment         │
   │                                                 │
   │ 21 May 2026, 09:30:15 AM                       │
   │ ├─ ACTION: CREATE                              │
   │ ├─ Entry: Party(ABC Corp) | Amt(₹2000)        │
   │ ├─ Mode: CASH                                 │
   │ ├─ Performed By: rahul.sharma (me)            │
   │ └─ Note: Entry created                        │
   │                                                 │
   │ [Older entries...]                             │
   └─────────────────────────────────────────────────┘

WHAT EMPLOYEE SEES:
   • Only their own entries
   • All changes (by self or admin)
   • Who made each change
   • Exact timestamp
   • What was changed (diff)
```

### Admin History View

```
ADMIN HISTORY PAGE (Full Audit Log)
   ┌──────────────────────────────────────────────────┐
   │ COMPLETE TRANSACTION HISTORY                     │
   │ (All entries, all employees, all changes)       │
   │                                                  │
   │ 22 May 2026, 10:50:45 AM                        │
   │ ├─ ACTION: UPDATE                               │
   │ ├─ Entry: Party(ABC Corp) | Amt(₹5500) CASH    │
   │ ├─ Employee: rahul.sharma                       │
   │ ├─ Performed By: admin (you)                    │
   │ └─ Changes: Amount: ₹5000 → ₹5500             │
   │            Remarks: Invoice #999 verified      │
   │                                                  │
   │ 22 May 2026, 10:45:20 AM                        │
   │ ├─ ACTION: CREATE                               │
   │ ├─ Entry: Party(PQR Inc) | Amt(₹3000) UPI      │
   │ ├─ Employee: amit.kumar                        │
   │ ├─ Performed By: amit.kumar                    │
   │ └─ Note: Entry created                         │
   │                                                  │
   │ 22 May 2026, 10:30:15 AM                        │
   │ ├─ ACTION: CREATE                               │
   │ ├─ Entry: Party(XYZ Ltd) | Amt(₹7000) CHEQUE  │
   │ ├─ Employee: priya.patel                       │
   │ ├─ Performed By: priya.patel                   │
   │ └─ Note: Entry created                         │
   │                                                  │
   │ 21 May 2026, 15:00:30 AM                        │
   │ ├─ ACTION: DELETE                               │
   │ ├─ Entry: Party(OLD Corp) | Amt(₹1000)        │
   │ ├─ Employee: priya.patel                       │
   │ ├─ Performed By: admin (you)                   │
   │ └─ Note: Entry deleted (duplicate)             │
   │                                                  │
   │ [Older entries...]                              │
   └──────────────────────────────────────────────────┘

WHAT ADMIN SEES:
   ✓ ALL entries from ALL employees
   ✓ ALL operations (CREATE, UPDATE, DELETE)
   ✓ Timestamp of every action
   ✓ Who performed the action
   ✓ Full details of what changed (diff)
   ✓ IMMUTABLE log (can't be deleted/edited)
```

---

## Important Business Rules & Constraints

### Employee Cannot...

```
❌ Create entries for PAST dates
   ├─ System forces date = TODAY
   └─ Reason: Prevent backdating of entries

❌ Edit entries from YESTERDAY or older
   ├─ Only TODAY's entries show EDIT button
   ├─ Older entries are LOCKED
   └─ Reason: Prevent tampering with historical data

❌ Delete entries from PAST dates
   ├─ Only TODAY's entries can be deleted
   └─ Reason: Maintain audit trail

❌ See other employees' entries
   ├─ Dashboard shows only own entries
   ├─ Entries page shows only own entries
   ├─ History shows only own transactions
   └─ Reason: Privacy & data segregation

❌ Skip the remarks field when editing
   ├─ Remarks are MANDATORY on edit
   ├─ System won't save unless remarks provided
   └─ Reason: Track WHY changes are made
```

### Admin Can Do Anything...

```
✅ View ALL entries across ALL employees
   └─ Dashboard shows all recent entries

✅ Edit ANY entry at ANY time
   ├─ Can change: party name, amount, mode, date, remarks
   ├─ Date can be past OR future
   └─ Remarks mandatory for audit

✅ Delete ANY entry
   ├─ But it's logged (not truly deleted from history)
   └─ TransactionLog shows: DELETE action by admin

✅ Manage employee accounts
   ├─ Create new employees
   ├─ Activate/Deactivate
   ├─ Reset passwords
   └─ View full profiles

✅ View complete audit trail
   ├─ Who did what, when, why
   ├─ All transactions from all employees
   └─ Full historical record

✅ Enforce business rules
   ├─ Can override date constraints
   ├─ Can add/modify remarks
   └─ All actions are logged
```

---

## Payment Modes Supported

```
Payment Methods Available:

1. CASH ........................ Direct cash payment
2. CHEQUE ...................... Cheque dated & number
3. BANK_TRANSFER ............... Online bank transfer
4. UPI ......................... Unified Payments Interface
5. NEFT ........................ National Electronic Funds Transfer
6. RTGS ........................ Real Time Gross Settlement
7. DD (DEMAND DRAFT) ........... Bank demand draft

SELECTION:
   ├─ Available in dropdown on entry form
   ├─ Employee selects at creation
   ├─ Admin can change when editing
   └─ Stored in TransactionLog for audit
```

---

## Entry Lifecycle

```
ENTRY CREATED BY EMPLOYEE
        │
        ├─ Date: AUTO-SET to TODAY
        ├─ CreatedAt: TIMESTAMP (NOW)
        ├─ UpdatedAt: TIMESTAMP (NOW)
        ├─ Edited: FALSE
        ├─ EditedBy: NULL
        └─ EditedAt: NULL
        │
        ↓ (Log: CREATE action)
        │
ENTRY VISIBLE IN:
        ├─ Employee Dashboard (today's section)
        ├─ Employee All Entries (today's section)
        ├─ Admin Dashboard (recent entries)
        └─ Admin All Entries
        │
        ├─ TODAY: Employee can EDIT/DELETE
        │
        ├─ TOMORROW: Entry becomes LOCKED for employee
        │         (viewed only, no edit/delete)
        │
        ├─ ADMIN CAN EDIT/DELETE (anytime)
        │         (even 1 year later)
        │
        ↓ (Log: UPDATE action, if modified)
        │
ADMIN EDITS ENTRY:
        ├─ UpdatedAt: TIMESTAMP (NOW)
        ├─ Edited: TRUE
        ├─ EditedBy: ADMIN
        ├─ EditedAt: TIMESTAMP (NOW)
        └─ Log: UPDATE action with diff
        │
        ↓ (Log: DELETE action, if deleted)
        │
ENTRY DELETED (by anyone):
        ├─ Entry removed from database
        ├─ BUT logged in TransactionLog
        └─ Log: DELETE action with details
        │
        ↓
HISTORY VIEW:
        └─ Shows full lifecycle: CREATE → UPDATEs → DELETE
           with who did what and when
```

---

## Data Consistency & Integrity

### ACID Compliance

```
ATOMICITY:
   • Each operation is all-or-nothing
   • If database save fails, logs are not created
   • If log save fails, entry save is rolled back

CONSISTENCY:
   • Foreign keys enforced (payment entry → employee)
   • No orphaned records
   • Amounts always decimal (15,2 precision)
   • Roles always ADMIN or EMPLOYEE

ISOLATION:
   • Each transaction is isolated
   • Concurrent edits don't interfere
   • Database handles locking

DURABILITY:
   • Data persists after commit
   • H2 keeps in memory during session
   • In production, use PostgreSQL/MySQL for durability
```

### Data Validation

```
AT FORM SUBMISSION:
   ├─ Party name: NOT EMPTY
   ├─ Amount: POSITIVE NUMBER > 0
   ├─ Payment mode: MUST BE VALID ENUM
   ├─ Entry date: Must exist, format validation
   ├─ Remarks (on edit): NOT EMPTY or blank
   └─ All errors shown in form with messages

AT BUSINESS LOGIC LAYER:
   ├─ Employee can only edit own entries
   ├─ Employee can only edit today's entries
   ├─ Employee cannot backdate entries
   ├─ Remarks mandatory on update
   ├─ User must exist before entry creation
   └─ All errors throw RuntimeException/AccessDeniedException

AUTOMATIC TIMESTAMPS:
   ├─ @PrePersist on User: createdAt = NOW
   ├─ @PrePersist on PaymentEntry: createdAt, updatedAt = NOW
   ├─ @PreUpdate on PaymentEntry: updatedAt = NOW
   ├─ @PrePersist on TransactionLog: performedAt = NOW
   └─ Developers can't forget to set these
```

---

## Extensibility & Future Features

### Easy to Add

```
1. NEW PAYMENT MODE:
   └─ Edit PaymentEntry.ModeOfPayment enum
      ✓ Automatically appears in all dropdowns
      ✓ Backward compatible with existing entries

2. NEW USER FIELD (e.g., phone number):
   └─ Add @Column private String phone to User
      ✓ Spring auto-creates column
      ✓ DTOs updated, forms updated
      ✓ Audit logs automatically capture

3. NEW ROLE (e.g., MANAGER, ACCOUNTANT):
   └─ Add to User.Role enum
      ✓ Create Controller with @PreAuthorize("hasRole('MANAGER')")
      ✓ Add SecurityConfig rules
      ✓ Instant authorization

4. NEW REPORT (e.g., monthly summary):
   └─ Add method to PaymentEntryService
      ✓ Query existing repositories
      ✓ Build aggregation
      ✓ Return to controller
      ✓ Display in Thymeleaf template

5. NEW FIELD TRACKING (e.g., approval status):
   └─ Add to PaymentEntry entity
      ✓ Add to WorkflowService
      ✓ Update TransactionLog notes
      ✓ Audit automatically logs changes
```

---

## Summary Table: Who Can Do What?

| Feature | Employee | Admin |
|---------|----------|-------|
| **View Own Dashboard** | ✓ | ✘ |
| **View Admin Dashboard** | ✘ | ✓ |
| **Create Entry** | ✓ (Today only) | Can via Employee UI |
| **View Own Entries** | ✓ | ✘ |
| **View All Entries** | ✘ | ✓ |
| **Edit Today's Entry** | ✓ | ✓ (Any date) |
| **Edit Past Entry** | ✘ | ✓ |
| **Delete Today's Entry** | ✓ | ✓ (Any date) |
| **Delete Past Entry** | ✘ | ✓ |
| **Create Employee** | ✘ | ✓ |
| **Toggle Employee Status** | ✘ | ✓ |
| **Reset Password** | ✘ | ✓ |
| **View Own History** | ✓ | ✘ |
| **View All History** | ✘ | ✓ |
| **See Change Details** | ✓ (Own entries) | ✓ (All entries) |

---

## Contact & Support References

```
For more information, see:
   ├─ ARCHITECTURE.md (Technical details)
   ├─ Source code in src/main/java/com/empmgmt/
   │  ├─ entity/ (Data models)
   │  ├─ service/ (Business logic)
   │  ├─ controller/ (Request handlers)
   │  ├─ repository/ (Data access)
   │  └─ config/ (Security & setup)
   ├─ Database console: http://localhost:8080/h2-console
   └─ Running app: http://localhost:8080
```

