# TrustVault — AI Interaction & Engineering Log (PROMPTS.md)

This log documents genuine dated AI-assisted engineering interactions for the TrustVault Secure Document Management application (Snovasys Fresher Hiring Challenge 2026). In accordance with the challenge evaluation criteria, every entry captures the actual prompt, actual output, the real technical problem encountered, the correction made, and the associated Git commit.

---

## Entry 1: Architecture Decision, Repository Inspection & Scaffolding
* **Date**: 2026-10-23T22:48:55+05:30
* **Topic**: Architecture Selection, Repository Inspection & Maven Tooling Setup

### 1. Actual Prompt
> "Start by inspecting my GitHub repository and identifying whether it is currently Flask/Python or Java/Spring Boot. Then report: What is already implemented, What is missing from the challenge requirements, Whether migration to Java is needed, How Supabase Storage and the application database should be connected, What is required to deploy on Render, The single safest next step."

### 2. Actual AI Output
* Confirmed the local workspace was completely empty (no files or `.git` directory).
* Queried `https://github.com/susmithaalla14/TrustVault` (returned HTTP 404 / 0 public repositories on GitHub API).
* Discovered local environment had Java 25 LTS installed (`java version "25.0.1"`), but standalone `mvn` was not present in the Windows system `PATH`.
* Advised against an unnecessary Python migration if no codebase existed yet, and recommended **Scenario A**: greenfield scaffolding directly in Java 21 LTS + Spring Boot 3/4 + Spring Security + JPA + Thymeleaf using the standalone Maven Wrapper (`mvnw`).

### 3. Real Problem
* The developer machine lacked a global Maven installation on the Windows `PATH`. Running standard `mvn` commands directly failed with `CommandNotFoundException`.
* Initial attempt to generate a legacy Spring Boot 3.4.1 starter returned a version compatibility error from the Initializr endpoint (`"Invalid Spring Boot version '3.4.1', Spring Boot compatibility range is >=4.0.0"`).

### 4. Real Correction
* Scaffolding was requested with compatible Spring Boot configuration targeting Java 21 LTS.
* Maven Wrapper (`mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties`) was configured with `distributionType=only-script` to dynamically provision Maven 3.10.0 without manual PATH editing.
* Verified end-to-end compilation with `.\mvnw.cmd compile` which succeeded in 37 seconds.
* Added `h2` database in test scope to `pom.xml` to enable fast, self-contained automated security testing alongside production PostgreSQL.

### 5. Related Commit
* **Commit**: `42ffe03` (`feat: initialize Spring Boot project structure with Maven wrapper and documentation`)

---

## Entry 2: Relational Domain Modeling & Tamper-Resistant Audit Log Schema
* **Date**: 2026-10-23T23:14:00+05:30
* **Topic**: Domain Entity Modeling, RBAC Roles, and Audit Trail Persistence

### 1. Actual Prompt
> "Implement core domain entities, repositories, and database schema for TrustVault conforming to Snovasys requirements: Role (ADMIN, OWNER, VIEWER), User, Document, DocumentPermission, and AuditLog."

### 2. Actual AI Output
* Created entity classes in `com.trustvault.model`:
  * `Role`: enum defining `ADMIN`, `OWNER`, and `VIEWER`.
  * `User`: with BCrypt password hashing, uniqueness indexes on username and email.
  * `Document`: mapping original filename, MIME type, size, category, and private Supabase storage UUID key.
  * `DocumentPermission`: granular `READ` and `EDIT` sharing grants with unique composite constraint.
  * `AuditLog`: append-only entity with `@Column(updatable = false)` on timestamp.
* Created Spring Data JPA repositories with custom JPQL queries for search and storage usage metrics.

### 3. Real Problem
* The project required both live PostgreSQL in production and zero-dependency local/test execution.
* If the JDBC driver was hardcoded in `application.properties` to `org.postgresql.Driver` without an active database container, local development tests would crash.

### 4. Real Correction
* Updated `pom.xml` to make `com.h2database:h2` available in runtime scope.
* Removed hardcoded `spring.datasource.driver-class-name` from `application.properties`, allowing Spring Boot's datasource autoconfiguration to dynamically resolve `org.postgresql.Driver` for live PostgreSQL URLs and `org.h2.Driver` for in-memory fallbacks.

### 5. Related Commit
* **Commit**: `2d9b30e` (`feat: add domain entities and repositories for users, documents, permissions, and audit logs`)

---

## Entry 3: Private Supabase Storage & Compensating Consistency Strategy
* **Date**: 2026-10-23T23:16:30+05:30
* **Topic**: Supabase Storage REST Integration and Storage-Database Consistency

### 1. Actual Prompt
> "Use my existing Supabase project and integrate Storage through a supported Java HTTP client. Use configuration variables: SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY, SUPABASE_STORAGE_BUCKET. Create or verify private bucket trustvault-documents. Use a safe consistency strategy if Storage succeeds but the database transaction fails."

### 2. Actual AI Output
* Created `SupabaseStorageService` using Java 21's native `HttpClient` with zero external library overhead.
* Implemented authenticated endpoints: `POST /storage/v1/object` (upload), `GET /storage/v1/object/authenticated` (download), `DELETE /storage/v1/object` (delete), and `POST /storage/v1/object/sign` (short-lived 60s signed URLs).
* Implemented bucket auto-provisioning with `"public": false` enforcement.
* Built in-memory fallback store when credentials are unconfigured during offline tests.

### 3. Real Problem
* If a document binary is uploaded to Supabase Storage successfully, but the subsequent PostgreSQL database transaction fails (e.g. unique constraint violation, network timeout), an orphaned storage object is left behind.
* Conversely, if the database record is inserted before storage upload, a failed upload leaves a phantom document record that cannot be downloaded.

### 4. Real Correction
* Designed a **Compensating Rollback Strategy** in `DocumentService.uploadDocument()`:
  1. File binary is uploaded to Supabase Storage under a cryptographically generated UUID path.
  2. Database metadata is persisted in a transaction.
  3. If database persistence throws an exception, the `catch` block immediately invokes `storageService.delete(confirmedKey)` to roll back the orphaned binary from Supabase Storage before rethrowing.

### 5. Related Commit
* **Commit**: `3ba1931` (`feat: implement Supabase storage service and application configuration`)

---

## Entry 4: Access Control Policy, IDOR Defense & Audit Log Transaction Propagation Fix
* **Date**: 2026-10-23T23:19:30+05:30
* **Topic**: Authorization Enforcement, IDOR Protection, and Spring Transaction Rollback Fix

### 1. Actual Prompt
> "Implement authorization and access control services: READ versus EDIT permissions, owner-authorized sharing, immediate enforcement of revocation, IDOR prevention, and security audit logging of denied attempts."

### 2. Actual AI Output
* Created `DocumentAccessPolicyService` with authoritative checks:
  * `canRead`: ADMIN, OWNER, or active READ/EDIT grantee.
  * `canEdit`: ADMIN, OWNER, or active EDIT grantee.
  * `canDelete`: strictly ADMIN or OWNER (shared users cannot delete).
  * `canShare`: strictly ADMIN or OWNER.
* Configured Spring Security in `SecurityConfig` with BCrypt (strength 12), session fixation protection, and login/logout audit handlers.

### 3. Real Problem Encountered (Root-Cause Analysis)
* During automated security testing, `testCrossUserUnauthorizedDownloadBlocked` asserted that an unauthorized download attempt by Bob on Alice's document was rejected AND that an `ACCESS_DENIED` audit log was saved in the database.
* The test failed with `Expecting value to be true but was false` for the presence of the audit log!
* **Technical Cause**: `verifyRead` threw `AccessDeniedException` out of a `@Transactional(readOnly = true)` method. When `auditLogService.logAccessDenied()` was invoked internally, it called `this.logEvent()`. In Spring, calling a method on `this` bypasses the Spring AOP proxy. As a result, `@Transactional(propagation = Propagation.REQUIRES_NEW)` was bypassed, running the audit write in the parent transaction. When `AccessDeniedException` was thrown, Spring marked the parent transaction for rollback, wiping the security audit log!

### 4. Real Correction
* Annotated `AuditLogService` at the class level with `@Transactional(propagation = Propagation.REQUIRES_NEW)`.
* Every public audit logging method (`logAccessDenied`, `logEvent`, etc.) now initiates an independent physical transaction that commits immediately, guaranteeing that security access denial attempts are permanently recorded even when the calling business method rolls back with an exception.

### 5. Related Commit
* **Commit**: `07bc4d8` (`test: add automated security test suite and configure independent transaction propagation for audit logging`)

---

## Entry 5: Automated Security Test Suite Verification & MockMvc Redirection Handling
* **Date**: 2026-10-23T23:31:30+05:30
* **Topic**: Automated Security Testing, IDOR Verification, and Privilege Escalation Prevention

### 1. Actual Prompt
> "Create automated JUnit and Spring Security tests for: Unauthenticated access, Unauthorized cross-user document access, Viewer deletion attempts, Unauthorized permission grants, READ vs EDIT enforcement, Access after revocation, Administrator privilege escalation, Unauthorized audit-log access, Invalid file uploads, and consistency."

### 2. Actual AI Output
* Implemented `SecurityAccessControlTests` (7 Spring Security integration tests with MockMvc).
* Implemented `DocumentSecurityPolicyTests` (8 authoritative policy tests).
* Verified tests against multi-user scenarios (`aliceOwner`, `bobViewer`, `charlieThirdParty`).

### 3. Real Problem
* When running `.\mvnw.cmd test`, three MockMvc tests failed with:
  `AssertionError: Redirected URL '/login' does not match the expected URL pattern '**/login'`.
* In Spring Boot 4's MockMvc, `redirectedUrlPattern("**/login")` expects an Ant-style path that does not match root-relative `/login` without a subpath wildcard.

### 4. Real Correction
* Updated test assertions to use `redirectedUrl("/login")` which matches exact root-relative redirects.
* Re-ran `.\mvnw.cmd test`: **16 out of 16 tests passed with 0 failures, 0 errors, and 0 skipped in 24 seconds**.

### 5. Related Commit
* **Commit**: `07bc4d8` (`test: add automated security test suite and configure independent transaction propagation for audit logging`)
