package com.diana.auditinsightbackendspringboot.Services;

import com.diana.auditinsightbackendspringboot.Enum.OrganisationType;

import java.util.Map;
import java.util.Set;

public final class EvidenceFolderValidator {

    private EvidenceFolderValidator() {}

    private static final Map<String, Set<String>> PRIVATE_ALLOWED = Map.of(
            "Financial Reporting",
                Set.of("General Ledgers", "Trial Balances", "Financial Statements"),
            "Banking and Cash",
                Set.of("Bank Statements", "Bank Reconciliations", "Payment Confirmations"),
            "Sales Evidence",
                Set.of("Sales Invoices", "Receipts", "Credit Notes", "Sales Orders"),
            "Purchases and Procurement",
                Set.of("Purchase Orders", "Supplier Invoices", "Goods Received Notes", "Supplier Contracts"),
            "Payroll and HR",
                Set.of("Payroll Registers", "Employment Contracts", "Timesheets"),
            "Tax and Compliance",
                Set.of("VAT Returns", "PAYE Filings", "Tax Clearance Certificates"),
            "Inventory and Assets",
                Set.of("Stock Count Sheets", "Asset Registers", "Depreciation Schedules"),
            "Legal and Governance",
                Set.of("Board Minutes", "Company Registration", "Contracts"),
            "IT and System Evidence",
                Set.of("Access Logs", "Audit Trail Exports", "Backup Reports"),
            "Other Supporting Documents",
                Set.of("Emails", "Screenshots", "Miscellaneous")
    );

    private static final Map<String, Set<String>> NGO_ALLOWED = Map.ofEntries(
            Map.entry("Financial Reporting",
                Set.of("General Ledgers", "Trial Balances", "Financial Statements",
                        "Project Financial Reports", "Donor Financial Reports")),
            Map.entry("Budget Management",
                Set.of("Approved Annual Budget", "Project Budgets", "Grant Budgets",
                        "Budget Revisions", "Budget vs Actual Reports", "Budget Approval Minutes")),
            Map.entry("Banking and Cash",
                Set.of("Bank Statements", "Bank Reconciliations", "Payment Confirmations",
                        "Cashbooks", "Cash Count Sheets", "Petty Cash Vouchers")),
            Map.entry("Payment Evidence",
                Set.of("Payment Vouchers", "Signed Payment Requests", "Electronic Transfer Confirmations",
                        "Cheque Copies", "Mobile Money Confirmations", "Payment Approval Forms")),
            Map.entry("Grants and Donor Agreements",
                Set.of("Grant Agreements", "Funding Agreements", "Donor Contracts",
                        "Grant Amendments", "Donor Correspondence")),
            Map.entry("Donor Compliance",
                Set.of("Donor Guidelines", "Reporting Requirements", "Compliance Checklists",
                        "Donor Approvals", "Waivers", "Donor Monitoring Reports")),
            Map.entry("Project Documentation",
                Set.of("Project Proposals", "Work Plans", "Activity Reports",
                        "Project Completion Reports", "Monitoring Reports")),
            Map.entry("Project Activities",
                Set.of("Training Reports", "Workshop Reports", "Workshop Agendas",
                        "Workshop Attendance Lists", "Signed Attendance Sheets", "Meeting Minutes",
                        "Evaluation Forms", "Photographs")),
            Map.entry("Beneficiary Documentation",
                Set.of("Beneficiary Lists", "Beneficiary Registration Forms", "Beneficiary IDs",
                        "Distribution Lists", "Acknowledgement Receipts", "Consent Forms")),
            Map.entry("Procurement",
                Set.of("Purchase Requisitions", "Purchase Orders", "Supplier Quotations",
                        "Bid Evaluation Reports", "Supplier Invoices", "Goods Received Notes",
                        "Supplier Contracts")),
            Map.entry("Payroll and HR",
                Set.of("Payroll Registers", "Employment Contracts", "Timesheets",
                        "Leave Records", "Staff Lists", "Performance Contracts")),
            Map.entry("Travel",
                Set.of("Travel Authorizations", "Travel Expense Claims", "Flight Tickets", "Hotel Invoices")),
            Map.entry("Vehicles",
                Set.of("Vehicle Logbooks", "Fuel Records", "Vehicle Maintenance Records",
                        "Vehicle Insurance", "Vehicle Allocation Records")),
            Map.entry("Fixed Assets",
                Set.of("Asset Register", "Asset Tags", "Purchase Documents", "Asset Transfer Forms",
                        "Asset Disposal Forms", "Physical Verification Reports", "Maintenance Records",
                        "Depreciation Schedules")),
            Map.entry("Inventory",
                Set.of("Inventory Registers", "Stock Count Sheets")),
            Map.entry("Compliance and Tax",
                Set.of("VAT Documents", "PAYE Filings", "RSSB Contributions",
                        "Tax Clearance Certificates", "NGO Registration Certificates")),
            Map.entry("Legal and Governance",
                Set.of("Board Minutes", "Management Meeting Minutes", "Policies",
                        "Memorandums of Understanding", "Contracts", "Registration Documents")),
            Map.entry("Audit Evidence",
                Set.of("Audit Requests", "Management Responses", "Audit Reports",
                        "Management Letters", "Corrective Action Plans")),
            Map.entry("IT and System Evidence",
                Set.of("Access Logs", "Audit Trail Exports", "Backup Reports")),
            Map.entry("Other Supporting Documents",
                Set.of("Emails", "Approval Letters", "Miscellaneous"))
    );

    private static final Map<OrganisationType, Map<String, Set<String>>> BY_ORGANISATION_TYPE = Map.of(
            OrganisationType.PRIVATE, PRIVATE_ALLOWED,
            OrganisationType.NGO, NGO_ALLOWED
    );

    public static boolean isValid(OrganisationType organisationType, String folder, String subfolder) {
        Map<String, Set<String>> allowed = BY_ORGANISATION_TYPE.get(organisationType);
        if (allowed == null) {
            return false;
        }
        Set<String> subfolders = allowed.get(folder);
        return subfolders != null && subfolders.contains(subfolder);
    }

    public static Map<String, Set<String>> getAllowed(OrganisationType organisationType) {
        return BY_ORGANISATION_TYPE.get(organisationType);
    }
}