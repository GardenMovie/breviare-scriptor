# Architecture Overview

Brevia is a three-tier web application: a React frontend, a REST API backend, and a PostgreSQL database. This document describes the responsibilities of each tier, the deployment targets, and the cross-cutting concerns that span all three.

For runtime data flows, see [data-flow.md](./data-flow.md). For the database schema, see [database-schema.md](./database-schema.md).

---

## Tiers

```
┌─────────────────────────────────────────────────────┐
│  Browser                                            │
│  React SPA (Next.js or Vite)                        │
│  Deployed on Vercel                                 │
└──────────────────────┬──────────────────────────────┘
                       │  HTTPS  (REST API)
                       ▼
┌─────────────────────────────────────────────────────┐
│  Backend                                            │
│  Spring Boot (Java 21)                              │
│  Deployed on Railway                                │
└──────────────────────┬──────────────────────────────┘
                       │  TLS  (Postgres wire protocol)
                       ▼
┌─────────────────────────────────────────────────────┐
│  Database                                           │
│  PostgreSQL                                         │
│  Hosted on Neon / Supabase / Railway (TBD)          │
└─────────────────────────────────────────────────────┘
```

Tech stack decisions are tracked in:
- [adr/0001-frontend-framework.md](../adr/0001-frontend-framework.md) (frontend: undecided)
- [adr/0002-backend-framework.md](../adr/0002-backend-framework.md) (backend: Spring Boot — accepted)
- [adr/0003-database-host.md](../adr/0003-database-host.md) (database host: undecided)

---

## Tier Responsibilities

### Frontend (Vercel)

- Renders the link-creation form for anonymous and authenticated users
- Handles user registration, login, and session state (stores and sends auth token)
- Displays the analytics dashboard for authenticated users
- Displays account settings (username management, vanity link destination)
- Does **not** perform short-link redirects — redirect resolution is the backend's responsibility

The frontend communicates with the backend exclusively via the REST API. It holds no business logic; all validation, rate limiting, and data persistence happen on the backend.

### Backend (Railway)

- Resolves short links and issues `302` redirects (the hot path)
- Creates and expires short links
- Manages user accounts (registration, authentication, username changes)
- Records analytics events for each redirect
- Enforces all business rules: rate limits, expiry policies, username change limits, vanity destination change limits
- Exposes the REST API at `/api/v1/...`
- Exposes the redirect endpoint at `/:code` and `/:username`

The backend is a long-running process (not serverless). This keeps connection pooling simple and avoids cold-start latency on the redirect hot path.

### Database (PostgreSQL)

- Stores all persistent state: users, links, analytics events
- Enforces uniqueness constraints on short codes and usernames
- Is accessed only by the backend; the frontend has no direct database access

---

## Request Routing

All traffic enters through the Brevia domain (e.g. `brevia.sh`). Two categories of routes exist:

| Path pattern | Handler | Notes |
|---|---|---|
| `/api/v1/*` | Backend REST API | Proxied from Vercel to Railway, or direct to Railway depending on frontend choice |
| `/:slug` | Backend redirect handler | The slug is either a short code or a username |
| All other paths | Frontend (Vercel) | Dashboard, account pages, 404 page |

If the frontend is **Next.js**, API routes at `/api/*` can be co-located in the Next.js app and proxy to Railway, or the frontend can call Railway's public URL directly.

If the frontend is **Vite (SPA)**, all `/api/v1/*` calls go directly to Railway's public URL, and CORS must be configured to allow the Vercel origin.

The redirect routes (`/:slug`) are always served by the Railway backend. This is the performance-critical path and must not pass through Vercel's serverless infrastructure.

---

## Authentication

- Authentication uses short-lived JWTs (access tokens) and longer-lived refresh tokens (or an httpOnly cookie session — to be decided; see [features/user-accounts.md](../features/user-accounts.md)).
- The backend issues tokens on login and validates them on every protected request.
- The frontend stores the access token in memory (not localStorage) to reduce XSS risk. The refresh token is stored in an httpOnly cookie.
- All protected API endpoints require `Authorization: Bearer <access_token>`.

---

## CORS

The backend must allow cross-origin requests from the Vercel deployment origin.

- In production: the `Access-Control-Allow-Origin` header is set to the production Vercel domain only.
- In staging: preview deployment origins must be allowlisted or a wildcard pattern used for the Vercel preview subdomain (`*.vercel.app`).
- The redirect endpoint (`/:slug`) does not serve JSON and does not require CORS headers.

---

## Rate Limiting

Rate limiting is enforced at the backend layer, keyed by IP address for anonymous requests and by user ID for authenticated requests.

| Operation | Limit |
|---|---|
| Anonymous link creation | TBD (e.g. 10 links / hour / IP) |
| Authenticated link creation | TBD (e.g. 100 links / hour / user) |
| Login attempts | TBD (e.g. 5 attempts / 15 min / IP) |
| Redirect resolution | No rate limit (public read) |

Exact limits are TBD and should be tuned after observing production traffic patterns.

---

## Cross-Cutting Concerns

**Observability:** The backend should emit structured logs (JSON) with at minimum: request method, path, status code, duration, and user ID (if authenticated). Railway collects stdout logs automatically.

**Health check:** The backend exposes `GET /health` returning `200 OK` with a minimal payload. Railway uses this for container health monitoring.

**Migrations:** Schema migrations are versioned and applied before new backend versions are deployed. The migration tool is TBD; see [deployment/database.md](../deployment/database.md).

**Secrets:** All secrets (database URL, JWT signing key, GeoIP license key, etc.) are injected as environment variables. No secrets in code or version control. See [deployment/overview.md](../deployment/overview.md) for the full environment variable manifest.
