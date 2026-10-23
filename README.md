# TrustVault — Secure Document Management System

> **Snovasys Fresher Hiring Challenge 2026 Submission**  
> **Candidate**: Susmitha Alla  
> **Repository**: [https://github.com/susmithaalla14/TrustVault](https://github.com/susmithaalla14/TrustVault)

---

## 1. Overview & Architecture

TrustVault is a zero-trust, enterprise-grade secure document management application built on **Java 21 LTS**, **Spring Boot**, **Spring Security**, and **Spring Data JPA**, backed by **Supabase PostgreSQL** for relational metadata and **Supabase Storage** for private object storage.

```
┌─────────────────────────────────────────────────────────────┐
│                       TrustVault Web UI                     │
│            Thymeleaf + CSS (Black & Emerald-Green)          │
└──────────────────────────────┬──────────────────────────────┘
                               │ HTTPS / CSRF Protected
┌──────────────────────────────▼──────────────────────────────┐
│                    Spring Boot 4 / Java 21                  │
│  ├─ Spring Security (BCrypt, Session Fixation, RBAC)        │
│  ├─ DocumentAccessPolicyService (IDOR Defense)              │
│  ├─ SupabaseStorageService (Private Bucket Client)          │
│  └─ AuditLogService (Tamper-Resistant Security Logs)        │
└──────────────┬───────────────────────────────┬──────────────┘
               │ JDBC + SSL                    │ REST + Service Key
┌──────────────▼──────────────┐ ┌──────────────▼──────────────┐
│     Supabase PostgreSQL     │ │   Supabase Storage Bucket   │
│  (Users, Permissions, Logs) │ │    (trustvault-documents)   │
└─────────────────────────────┘ └─────────────────────────────┘
```

---

## 2. Requirements-to-Code Checklist

| Module | Feature | Implementation Path | Status |
|---|---|---|---|
| **Security** | BCrypt Password Hashing | `com.trustvault.security.SecurityConfig` | Planned |
| **Security** | CSRF & Session Fixation Protection | `com.trustvault.security.SecurityConfig` | Planned |
| **Security** | Safe Initial Admin Provisioning | `com.trustvault.service.DataInitializer` | Planned |
| **RBAC** | Roles: `ADMIN`, `OWNER`, `VIEWER` | `com.trustvault.model.Role` | Planned |
| **Documents** | Multipart Upload with MIME/Size Validation | `com.trustvault.controller.DocumentController` | Planned |
| **Documents** | Unguessable UUID Storage Paths | `com.trustvault.service.SupabaseStorageService` | Planned |
| **Documents** | Search by Name, Owner, Category, Date | `com.trustvault.repository.DocumentRepository` | Planned |
| **Documents** | Authorized Streaming Downloads | `com.trustvault.controller.DocumentController` | Planned |
| **Sharing** | Granular READ & EDIT Permissions | `com.trustvault.model.DocumentPermission` | Planned |
| **Sharing** | Immediate Revocation Enforcement | `com.trustvault.service.DocumentAccessPolicyService` | Planned |
| **Sharing** | Strict IDOR Protection | `com.trustvault.service.DocumentAccessPolicyService` | Planned |
| **Audit** | Append-Only Tamper-Resistant Audit Trail | `com.trustvault.service.AuditLogService` | Planned |
| **Audit** | Admin-Only Security Log Viewer | `com.trustvault.controller.AdminController` | Planned |
| **UI** | Responsive Black & Emerald-Green Design | `src/main/resources/templates/`, `static/css/` | Planned |
| **Deployment**| Multi-stage Dockerfile for Render | `Dockerfile`, `render.yaml` | Planned |
| **Testing** | Automated Security Tests (14 scenarios) | `src/test/java/com/trustvault/` | Planned |

---

## 3. Technology Stack

* **Language**: Java 21 LTS
* **Framework**: Spring Boot (Spring WebMvc, Spring Security, Spring Data JPA, Spring Validation)
* **Template Engine**: Thymeleaf + Thymeleaf Extras Spring Security
* **Database**: PostgreSQL (Supabase managed, SSL required) / H2 for isolated automated unit tests
* **Object Storage**: Supabase Storage (`trustvault-documents` private bucket)
* **Build System**: Maven Wrapper (`mvnw` / `mvnw.cmd`)
* **Deployment**: Docker containerization on Render

---

## 4. Local Development Setup

### Prerequisites
* Java 21+ (`java -version`)
* Git (`git --version`)

### Quickstart
1. Clone the repository:
   ```bash
   git clone https://github.com/susmithaalla14/TrustVault.git
   cd TrustVault
   ```
2. Build and verify tests with the Maven Wrapper:
   ```bash
   # Windows
   .\mvnw.cmd clean test
   # Linux / macOS
   ./mvnw clean test
   ```
3. Run locally:
   ```bash
   .\mvnw.cmd spring-boot:run
   ```
   Open `http://localhost:8080` in your browser.

---

## 5. Security & Threat Mitigation Summary

See [THREATS.md](THREATS.md) for full STRIDE threat analysis. Key mitigations:
* **Private Bucket Enforcement**: Files never accessible through unauthenticated public URLs.
* **Server-Side Authorization**: Every download, view, edit, or delete verifies caller identity against document ownership and granted permissions.
* **Zero Secret Leakage**: Service role keys and database passwords kept strictly in server-side environment variables.
