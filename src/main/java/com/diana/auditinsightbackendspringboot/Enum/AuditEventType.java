package com.diana.auditinsightbackendspringboot.Enum;

/** What happened to a document, as recorded in its audit trail (evidence_audit_events.event_type). */
public enum AuditEventType {
    DOCUMENT_UPLOADED,
    SIGNATURE_REQUESTED,
    SIGNATURE_DECLINED,
    SIGNED,
    VERSION_STORED,
    SIGNED_COPY_ACCESSED,
    ORIGINAL_ACCESSED,
    VERIFICATION
}
