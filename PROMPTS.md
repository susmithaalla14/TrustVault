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

* **Related Commit**: `42ffe03` (`feat: initialize Spring Boot project structure with Maven wrapper and documentation`)
