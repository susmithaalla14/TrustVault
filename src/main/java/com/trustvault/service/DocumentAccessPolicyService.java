package com.trustvault.service;

import com.trustvault.model.*;
import com.trustvault.repository.DocumentPermissionRepository;
import com.trustvault.repository.DocumentRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Authoritative document access control service.
 * Enforces server-side authorization checks on EVERY operation to prevent IDOR attacks,
 * privilege escalations, and unauthorized data access.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentAccessPolicyService {

    private final DocumentRepository documentRepository;
    private final DocumentPermissionRepository permissionRepository;
    private final AuditLogService auditLogService;

    /**
     * Checks if a user has READ permission on a document.
     * Allowed if:
     * 1. User is an ADMIN
     * 2. User is the document OWNER
     * 3. User has an active READ or EDIT grant on the document
     */
    @Transactional(readOnly = true)
    public boolean canRead(Document document, User user) {
        if (document == null || user == null) return false;
        if (user.getRole() == Role.ADMIN) return true;
        if (document.getOwner().getId().equals(user.getId())) return true;

        Optional<DocumentPermission> permission = permissionRepository.findByDocumentAndGrantedUser(document, user);
        return permission.isPresent(); // Both READ and EDIT grants allow reading
    }

    /**
     * Checks if a user has EDIT permission on a document.
     * Allowed if:
     * 1. User is an ADMIN
     * 2. User is the document OWNER
     * 3. User has an explicit EDIT grant on the document
     */
    @Transactional(readOnly = true)
    public boolean canEdit(Document document, User user) {
        if (document == null || user == null) return false;
        if (user.getRole() == Role.ADMIN) return true;
        if (document.getOwner().getId().equals(user.getId())) return true;

        Optional<DocumentPermission> permission = permissionRepository.findByDocumentAndGrantedUser(document, user);
        return permission.isPresent() && permission.get().getPermissionType() == PermissionType.EDIT;
    }

    /**
     * Checks if a user can DELETE a document.
     * Strictly restricted to:
     * 1. User is an ADMIN
     * 2. User is the document OWNER
     * (Shared users, even with EDIT permission, CANNOT delete documents).
     */
    @Transactional(readOnly = true)
    public boolean canDelete(Document document, User user) {
        if (document == null || user == null) return false;
        if (user.getRole() == Role.ADMIN) return true;
        return document.getOwner().getId().equals(user.getId());
    }

    /**
     * Checks if a user can SHARE or manage permissions on a document.
     * Strictly restricted to:
     * 1. User is an ADMIN
     * 2. User is the document OWNER
     */
    @Transactional(readOnly = true)
    public boolean canShare(Document document, User user) {
        if (document == null || user == null) return false;
        if (user.getRole() == Role.ADMIN) return true;
        return document.getOwner().getId().equals(user.getId());
    }

    /**
     * Authoritative guard: verifies read access or throws AccessDeniedException with audit logging.
     */
    @Transactional(readOnly = true)
    public void verifyRead(Document document, User user, HttpServletRequest request) {
        if (!canRead(document, user)) {
            String username = user != null ? user.getUsername() : "ANONYMOUS";
            Long docId = document != null ? document.getId() : null;
            auditLogService.logAccessDenied(username, "DOCUMENT_READ", "DOCUMENT", docId,
                    "User lacks ownership or sharing grant", request);
            log.warn("IDOR / UNAUTHORIZED READ ATTEMPT: User '{}' attempted to read document ID '{}'", username, docId);
            throw new AccessDeniedException("Access denied: You are not authorized to view or download this document.");
        }
    }

    /**
     * Authoritative guard: verifies edit access or throws AccessDeniedException with audit logging.
     */
    @Transactional(readOnly = true)
    public void verifyEdit(Document document, User user, HttpServletRequest request) {
        if (!canEdit(document, user)) {
            String username = user != null ? user.getUsername() : "ANONYMOUS";
            Long docId = document != null ? document.getId() : null;
            auditLogService.logAccessDenied(username, "DOCUMENT_EDIT", "DOCUMENT", docId,
                    "User lacks EDIT authority", request);
            log.warn("UNAUTHORIZED EDIT ATTEMPT: User '{}' attempted to edit document ID '{}'", username, docId);
            throw new AccessDeniedException("Access denied: You do not have edit permissions for this document.");
        }
    }

    /**
     * Authoritative guard: verifies delete access or throws AccessDeniedException with audit logging.
     */
    @Transactional(readOnly = true)
    public void verifyDelete(Document document, User user, HttpServletRequest request) {
        if (!canDelete(document, user)) {
            String username = user != null ? user.getUsername() : "ANONYMOUS";
            Long docId = document != null ? document.getId() : null;
            auditLogService.logAccessDenied(username, "DOCUMENT_DELETE", "DOCUMENT", docId,
                    "Only the document owner or admin can delete this document", request);
            log.warn("UNAUTHORIZED DELETE ATTEMPT: User '{}' attempted to delete document ID '{}'", username, docId);
            throw new AccessDeniedException("Access denied: Only the document owner can delete this document.");
        }
    }

    /**
     * Authoritative guard: verifies share access or throws AccessDeniedException with audit logging.
     */
    @Transactional(readOnly = true)
    public void verifyShare(Document document, User user, HttpServletRequest request) {
        if (!canShare(document, user)) {
            String username = user != null ? user.getUsername() : "ANONYMOUS";
            Long docId = document != null ? document.getId() : null;
            auditLogService.logAccessDenied(username, "PERMISSION_GRANT", "DOCUMENT", docId,
                    "Only the document owner or admin can grant permissions", request);
            log.warn("UNAUTHORIZED SHARE ATTEMPT: User '{}' attempted to share document ID '{}'", username, docId);
            throw new AccessDeniedException("Access denied: Only the document owner can manage permissions for this document.");
        }
    }
}
