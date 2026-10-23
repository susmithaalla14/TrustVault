package com.trustvault.model;

/**
 * Granular document sharing permissions:
 * - READ: Allows viewing and downloading the document.
 * - EDIT: Allows updating document metadata/category in addition to reading.
 * Note: Deletion and permission granting are strictly reserved for the document OWNER or ADMIN.
 */
public enum PermissionType {
    READ,
    EDIT
}
