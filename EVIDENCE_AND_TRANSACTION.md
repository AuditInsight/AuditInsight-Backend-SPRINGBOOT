You are building a module for AuditInsight, an audit-readiness and
financial evidence management platform.

Your task is to implement three interconnected modules:
1. Transaction Management
2. Evidence Management
3. Review Queue

---

## CONTEXT

The platform has three user roles inside an Organisation:
- Client (business owner) — owns and manages the organisation
- Member (accountant, financer, etc.) — handles day-to-day financial records
- Auditor — reviews financial records and flags issues

---

## PERMISSIONS (strictly enforce these)

| Action                        | Client | Member | Auditor |
|-------------------------------|--------|--------|---------|
| Create transaction            |  YES   |  YES   |   NO    |
| Upload evidence               |  YES   |  YES   |   NO    |
| View transactions             |  YES   |  YES   |  YES    |
| View evidence                 |  YES   |  YES   |  YES    |
| Flag issues (Review Queue)    |   NO   |   NO   |  YES    |
| Resolve issues (Review Queue) |  YES   |  YES   |   NO    |
| Generate reports              |  YES   |  YES   |  YES    |

Any action outside these permissions must return a 403 Forbidden error
with a clear message explaining why access is denied.

---

## DATA MODELS

### Transaction
{
id                  // unique, auto-generated e.g. TXN-0001
organisation_id     // reference to the organisation
name                // e.g. "Supplier Payment - Office Supplies"
date                // transaction date
amount              // numeric value
type                // "income" | "expense"
payment_method      // "bank" | "mobile_money" | "cash"
status              // "pending" | "completed"
evidence_status     // "missing" | "partial" | "complete"
// auto-calculated based on linked evidence
created_by          // user ID of the member or client who created it
created_at          // timestamp
}

### Evidence
{
id                  // unique, auto-generated
organisation_id     // reference to the organisation
transaction_id      // REQUIRED — links evidence to a specific transaction
document_name       // e.g. "Supplier Invoice - ABC Ltd"
folder              // one of the predefined audit folders (see below)
subfolder           // subcategory within the folder
file_upload         // stored as a cloudinaryURL
file_type           // e.g. "pdf", "xlsx", "jpg"
notes               // optional additional context
uploaded_by         // user ID of uploader
uploaded_at         // timestamp
}

### ReviewQueue
{
id                  // unique, auto-generated
organisation_id     // reference to the organisation
transaction_id      // the transaction being flagged
issue_type          // "missing_evidence" | "compliance_issue"
// | "risk_flag" | "verification_problem"
description         // detailed explanation of the issue
status              // "open" | "resolved" | "escalated"
flagged_by          // "system" (auto) or auditor user ID (manual)
resolved_by         // user ID of client or member who resolved it
created_at          // timestamp
resolved_at         // timestamp (null until resolved)
}

---

## BUSINESS LOGIC TO IMPLEMENT

### 1. Transaction evidence_status (auto-calculated)
- "missing"  → transaction exists but has zero evidence linked
- "partial"  → transaction has at least one evidence but not all
  required document types
- "complete" → all required evidence types are present

### 2. Review Queue — Automatic Flagging (System)
Trigger automatically when:
- A transaction is created but has NO evidence linked after 24 hours
- A transaction status is "completed" but evidence_status is still "missing"
  Create a ReviewQueue entry with:
    - flagged_by: "system"
    - issue_type: "missing_evidence"
    - description: "Transaction [TXN-ID] has no supporting evidence linked."

### 3. Review Queue — Manual Flagging (Auditor only)
- Auditor can flag any transaction with a custom issue_type and description
- Only users with role "auditor" can create manual flags
- System must reject flag attempts from "client" or "member" roles

### 4. Resolving Issues (Client and Member only)
- Client or Member can mark a ReviewQueue item as "resolved"
- They must provide a resolution note
- resolved_by and resolved_at must be recorded
- Auditor cannot resolve issues

### 5. Evidence Folder Structure
When uploading evidence, the folder must be one of:
   Folders                   SubFolders
- Financial Reporting → [General Ledgers, Trial Balances, Financial Statements]
- Banking and Cash → [Bank Statements, Bank Reconciliations, Payment Confirmations]
- Sales Evidence → [Sales Invoices, Receipts, Credit Notes, Sales Orders]
- Purchases and Procurement → [Purchase Orders, Supplier Invoices,
  Goods Received Notes, Supplier Contracts]
- Payroll and HR → [Payroll Registers, Employment Contracts, Timesheets]
- Tax and Compliance → [VAT Returns, PAYE Filings, Tax Clearance Certificates]
- Inventory and Assets → [Stock Count Sheets, Asset Registers,
  Depreciation Schedules]
- Legal and Governance → [Board Minutes, Company Registration, Contracts]
- IT and System Evidence → [Access Logs, Audit Trail Exports, Backup Reports]
- Other Supporting Documents → [Emails, Screenshots, Miscellaneous]

Reject uploads that use folders or subfolders not in this list.

---

## API ENDPOINTS TO BUILD

### Transactions
POST   /api/transactions          → Create transaction (Client, Member only)
GET    /api/transactions          → List all transactions in organisation
GET    /api/transactions/:id      → Get single transaction with linked evidence
PATCH  /api/transactions/:id      → Update transaction status

### Evidence
POST   /api/evidence              → Upload evidence + link to transaction
GET    /api/evidence              → List all evidence in organisation
GET    /api/evidence/:id          → Get single evidence record
GET    /api/evidence/transaction/:transaction_id → Get all evidence for a transaction

### Review Queue
POST   /api/review-queue          → Manually flag an issue (Auditor only)
GET    /api/review-queue          → List all review queue items
GET    /api/review-queue/:id      → Get single review queue item
PATCH  /api/review-queue/:id/resolve → Resolve an issue (Client, Member only)

---

## ADDITIONAL REQUIREMENTS

1. Every API request must include the authenticated user's ID and role.
   Use middleware to check permissions before processing any request.

2. All errors /success must return structured responses:
   {
   "status": "done/failed",
   "message": "Permission denied. Auditors cannot create transactions.",
   "code": 403
   }

3. When evidence is uploaded and linked to a transaction:
    - Automatically recalculate the transaction's evidence_status
    - If evidence_status changes to "complete", automatically resolve
      any open "missing_evidence" items in the Review Queue for that transaction

4. Generate a transaction reference ID in the format TXN-XXXX
   (e.g. TXN-0001, TXN-0042) random generated per organisation.

5. All database queries must be scoped to the organisation_id
   to ensure data isolation between organisations.

---

## WHAT TO BUILD

Please implement the following, clean and well-commented:
1. Data models / database schema
2. API routes and controllers
3. Permission middleware
4. Business logic (evidence_status calculation, auto-flagging)
5. Basic validation for all inputs

Use best practices, separation of concerns, and make the code
easy to extend in future phases.