# TrustVault — Security Architecture & STRIDE Threat Model

TrustVault is a secure document management application built for the **Snovasys Fresher Hiring Challenge 2026**. This document provides an exhaustive STRIDE threat analysis, trust boundary mapping, and mitigation matrix implemented in the application.

---

## 1. System Architecture & Trust Boundaries

```
[ Unauthenticated Internet ]
           │
           ▼ (HTTPS / TLS 1.3)
   [ Render Web Service ]
           │
           ▼
[ Spring Security Filter Chain ]
 ├─ CSRF Token Validator
 ├─ Session Authentication Filter (BCrypt + Cookie HttpOnly/SameSite)
 ├─ Role & Permission Authorization Filters
           │
           ▼ (Authenticated Context)
[ TrustVault Spring Boot Application ]
 ├─ DocumentController / AdminController / UserController
 ├─ Security Guard: DocumentAccessPolicyService (Ownership & RBAC)
 ├─ AuditLogService (Tamper-Resistant Security Event Logger)
 ├─ SupabaseStorageService (Server-Side Private Storage Client)
           │                                 │
           ▼ (JDBC + SSL)                    ▼ (REST API + Service Key)
[ Supabase PostgreSQL ]             [ Supabase Storage: trustvault-documents ]
  (Users, Documents,                   (Private Document Binary Blobs)
   Permissions, Audit Logs)
```

### Trust Zones:
1. **Untrusted Client Zone**: Browser, public network. No secrets, keys, or direct storage URLs are ever entrusted to this zone.
2. **Application Demilitarized Zone (DMZ)**: Render-hosted Spring Boot container. Handles authentication, authorization, input validation, and business logic.
3. **Protected Data Zone**: Supabase managed PostgreSQL database and private Storage bucket. Accessible only via authenticated server-side connections.

---

## 2. STRIDE Threat Analysis Matrix

| STRIDE Category | Threat ID | Specific Threat Scenario | Impact | Severity | Implemented Mitigation & Verification |
|---|---|---|---|---|---|
| **Spoofing** | THR-S01 | Attacker impersonates an existing user or guesses session identifier | Account takeover, unauthorized document access | Critical | Password hashing with **BCrypt (strength 12)**; session fixation protection (`migrateSession`); HTTP-only, secure, `SameSite=Strict` cookies. |
| **Spoofing** | THR-S02 | Rogue user registers with `ADMIN` role or modifies registration payload to self-assign elevated privileges | Privilege Escalation to Admin | Critical | Registration endpoint strictly binds to a DTO with non-privileged fields; newly registered users are hardcoded to `VIEWER` or default `OWNER`. `ADMIN` is provisioned exclusively through secure database initialization. |
| **Tampering** | THR-T01 | Malicious user alters document ID in request (`/documents/download/{id}`) to access another user's document (IDOR) | Data breach, unauthorized reading of sensitive files | Critical | Centralized server-side `DocumentSecurityService.canRead(docId, currentUser)` check before any file retrieval. Database IDs cannot be iterated to bypass authorization. |
| **Tampering** | THR-T02 | Malicious user attempts to upload executable scripts (e.g. `.exe`, `.jsp`, `.sh`, `.php`, SVG with XSS payloads) | Remote Code Execution, Stored XSS | High | Whitelist validation of MIME types and extensions (PDF, DOCX, XLSX, TXT, PNG, JPG). Content-Disposition set to `attachment` with `X-Content-Type-Options: nosniff`. |
| **Tampering** | THR-T03 | Audit log records altered or deleted to cover illicit document access | Loss of forensic integrity | High | Audit logs are append-only. No deletion or update APIs exist in the application. JPA entity has `@Column(updatable = false)`. Admin view is read-only. |
| **Repudiation** | THR-R01 | User deletes or shares a document and denies having performed the action | Inability to attribute security incidents | Medium | Comprehensive audit logging via `AuditLogService`. Records actor ID, username, IP address, timestamp, action type (`UPLOAD`, `DOWNLOAD`, `SHARE_READ`, `SHARE_EDIT`, `REVOKE`, `DELETE`, `LOGIN_FAIL`, `ACCESS_DENIED`), and target document metadata. |
| **Information Disclosure** | THR-I01 | Attacker gains direct access to Supabase Storage objects by guessing predictable paths (e.g., `/documents/1.pdf`) | Unauthorized document retrieval | Critical | Bucket `trustvault-documents` is strictly **PRIVATE**. Files are stored under cryptographic UUID keys (`{ownerId}/{uuid}-{sanitizedFilename}`). Direct public reads are rejected by Supabase Storage policies. |
| **Information Disclosure** | THR-I02 | `SUPABASE_SERVICE_ROLE_KEY` or database credentials leaked in client HTML, JavaScript, logs, or Git commits | Full database/storage compromise | Critical | Credentials read exclusively from environment variables on Render. Omitted from Git repository via `.gitignore`. Never serialized in Thymeleaf models or API responses. |
| **Information Disclosure** | THR-I03 | Sensitive document content cached by browser or intermediate proxies | Leakage on shared workstations | Medium | HTTP Cache-Control headers set to `no-cache, no-store, must-revalidate` on all sensitive document endpoints. |
| **Denial of Service** | THR-D01 | Attacker uploads huge multi-gigabyte files to exhaust disk or memory | Service outage, denial of service | High | Spring Boot `max-file-size` and `max-request-size` enforced at 20MB limit. Pre-upload validation checks size before reading stream into memory. |
| **Denial of Service** | THR-D02 | Repeated brute-force login attempts exhaust database connections | Credential stuffing, denial of service | Medium | Login attempts rate-limited; failed attempts audited; BCrypt calculation introduces computational delay preventing automated bursts. |
| **Elevation of Privilege** | THR-E01 | User with `READ` permission attempts to update metadata or delete a document | Unauthorized document tampering | High | Role-Based and Permission-Based checks (`hasEditAccess`, `isDocumentOwner`). `VIEWER` and `READ` recipients cannot trigger POST/DELETE mutations. |
| **Elevation of Privilege** | THR-E02 | User attempts to access documents after permissions were revoked by owner | Access after revocation | High | Permissions are checked dynamically against the database on **every single request**. Revocation is immediate and invalidates subsequent downloads. |

---

## 3. Storage and Database Consistency Strategy

To prevent orphaned storage objects or phantom database records:
1. **Upload Phase**:
   - Save file to Supabase Storage first.
   - If Storage upload fails, return error immediately; no database record created.
   - Save metadata to PostgreSQL inside a transactional service.
   - If database transaction fails, trigger compensating rollback: automatically delete the uploaded object from Supabase Storage using its key, preventing storage leaks.
2. **Delete Phase**:
   - Verify caller is document owner or administrator.
   - Delete metadata record and permissions in PostgreSQL transaction.
   - Delete object from Supabase Storage bucket.
   - Record `DOCUMENT_DELETE` audit event.
