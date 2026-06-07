package com.diana.auditinsightbackendspringboot.Services;

import java.util.Map;
import java.util.Set;

public final class EvidenceFolderValidator {

    private EvidenceFolderValidator() {}

    private static final Map<String, Set<String>> ALLOWED = Map.of(
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

    public static boolean isValid(String folder, String subfolder) {
        Set<String> subfolders = ALLOWED.get(folder);
        return subfolders != null && subfolders.contains(subfolder);
    }

    public static Map<String, Set<String>> getAllowed() {
        return ALLOWED;
    }
}