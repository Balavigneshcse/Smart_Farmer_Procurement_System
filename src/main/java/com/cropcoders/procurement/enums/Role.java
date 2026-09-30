package com.cropcoders.procurement.enums;

public enum Role {
    FARMER,
    /** Counter staff of one centre (the operator desk). */
    OPERATOR,
    /** Admin of one centre: its dashboard, payments, settings and staff. */
    ADMIN,
    /** Sees every centre; adds centres and centre admins, sets crop rates. */
    SUPER_ADMIN
}
