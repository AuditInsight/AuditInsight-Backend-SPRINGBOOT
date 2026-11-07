package com.diana.auditinsightbackendspringboot.Enum;

/**
 * ACTIVE: granted and not revoked (it stops granting access once its end date passes); ENDED: closed after
 * expiry so a new grant can be made; REVOKED: withdrawn by a Super Admin.
 */
public enum TrialGrantStatus { ACTIVE, ENDED, REVOKED }
