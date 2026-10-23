package com.trustvault.model;

/**
 * Standard system roles for TrustVault.
 * In accordance with challenge requirements:
 * - ADMIN: Manages users, inspects system statistics, views comprehensive security audit logs.
 * - OWNER: Full control over owned documents, can grant/revoke READ and EDIT access to other users.
 * - VIEWER: Read-only access to documents shared with them. Cannot upload or delete.
 */
public enum Role {
    ADMIN,
    OWNER,
    VIEWER
}
