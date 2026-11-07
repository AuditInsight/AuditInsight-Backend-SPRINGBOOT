package com.diana.auditinsightbackendspringboot.Enum;

/**
 * Where a paid subscription period came from: a verified pawaPay payment, a Super Admin complimentary grant, or
 * created before this distinction existed.
 */
public enum SubscriptionSource { PAYMENT, ADMIN_COMPLIMENTARY, LEGACY }
