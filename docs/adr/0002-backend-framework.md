# 0002 — Backend Framework

**Status:** Accepted
**Date:** 2026-06-04
**Updated:** 2026-06-06

## Context

Breviare's backend handles link creation, redirect resolution, analytics recording, user authentication, and vanity link management. It will be deployed as a containerized service on Railway. The backend's performance-critical path is redirect resolution — a lookup + 302 response that fires on every click. Everything else (link creation, analytics queries, account management) is relatively low-frequency.

## Options Considered

### Option A — Express.js (Node.js)

- **Pro:** Same language as the frontend; full-stack TypeScript is possible, enabling shared types
- **Pro:** Massive ecosystem — mature libraries for JWT, rate limiting, Postgres
- **Pro:** Non-blocking I/O suits the I/O-bound redirect hot path
- **Con:** No built-in request validation; requires additional libraries (zod, express-validator)
- **Con:** Middleware composition is manual; easy to misconfigure auth or rate limiting

### Option B — FastAPI (Python)

- **Pro:** Automatic request validation and serialization via Pydantic
- **Pro:** Auto-generated OpenAPI/Swagger docs
- **Pro:** Async support (ASGI) provides comparable performance for I/O-bound workloads
- **Con:** Different language from the frontend; shared types require a code-generation step
- **Con:** Python packaging and async pitfalls add onboarding friction

### Option C — Spring Boot (Java) ✓ Chosen

Spring Boot is an opinionated, production-ready Java framework with a mature ecosystem and strong built-in support for REST APIs, security, data access, and scheduling.

- **Pro:** Battle-tested at scale; strong support for all required concerns (auth, validation, connection pooling, scheduling)
- **Pro:** Spring Security provides a well-understood, configurable auth layer for JWT and cookie-based sessions
- **Pro:** Spring Data JPA / JDBC provides type-safe, connection-pooled database access with Flyway for migrations
- **Pro:** Built-in support for async processing (virtual threads via Java 21 or `@Async`) suits the analytics write path
- **Pro:** Excellent Railway support via Docker or Buildpacks; well-understood containerization story
- **Pro:** Strongly typed end-to-end; compile-time safety for request/response models (records, Jackson)
- **Con:** More boilerplate than Express or FastAPI for simple CRUD; annotation-heavy configuration
- **Con:** JVM startup time is higher than Node.js or Python — mitigated by keeping the container always-on (no serverless)
- **Con:** Different language from the frontend; no shared types without a code-generation step

## Decision

**Spring Boot (Java 21)** is the chosen backend framework.

The always-on deployment model on Railway eliminates the JVM startup concern. The redirect hot path is I/O-bound (single DB lookup), and the JVM handles concurrent I/O well. Spring Boot's strong conventions for security, validation, and data access reduce the risk of misconfiguration compared to composing the same concerns from smaller Node.js or Python libraries.

## Consequences

- The backend is a Spring Boot application targeting Java 21.
- Database migrations are managed with **Flyway** (co-located in the Spring Boot project; runs on startup before the application accepts traffic).
- Database access uses **Spring Data JDBC** (preferred over JPA for this schema's simplicity) with **HikariCP** connection pooling (Spring Boot default).
- Authentication uses **Spring Security** with JWT access tokens (short-lived) and refresh tokens stored in httpOnly cookies. A token blocklist or rotation scheme handles logout invalidation.
- Request validation uses **Jakarta Bean Validation** (`@Valid`, `@NotNull`, etc.) with custom validators where needed.
- The project is built with **Gradle** (Kotlin DSL).
- Railway deployment uses a `Dockerfile` or Buildpacks with the `./gradlew bootJar` build step and `java -jar app.jar` start command.
- The frontend calls the backend via REST; no shared type generation is required in v1 (the API contract is documented in `docs/api/`).
