# Deployment Overview

This document describes Breviare's deployment topology, environment map, and the full environment variable manifest across all services.

---

## Environments

| Environment | Purpose |
|---|---|
| **Local** | Individual developer machines; all services run locally |
| **Preview** | Automatically created per pull request; frontend on Vercel, backend on Railway (or mocked), database branched (if Neon) |
| **Production** | Live user-facing environment |

---

## Service Map

| Service | Platform | Region |
|---|---|---|
| Frontend | Vercel | Auto (CDN edge) |
| Backend | Railway | TBD (choose to minimize latency to DB host) |
| Database | TBD (Neon / Supabase / Railway) | Same region as backend |

The backend and database should be co-located in the same cloud region to minimize query latency on the redirect hot path.

---

## Domain and Routing

All user traffic enters via the Breviare domain (e.g. `breviare.sh`). Routing splits between Vercel and Railway:

| Path | Served by |
|---|---|
| `/:slug` (redirect hot path) | Railway backend directly |
| `/api/v1/*` | Railway backend (either directly or proxied via Vercel) |
| All other paths | Vercel (frontend) |

**Option 1 (recommended for low latency):** DNS routes `breviare.sh` to Vercel for the frontend. A Vercel rewrite rule forwards `/api/v1/*` requests to Railway's public URL. The `/:slug` route is also forwarded to Railway via rewrite. All traffic has one hop regardless of destination.

**Option 2 (simpler backend setup):** The frontend calls Railway's public URL (`api.breviare.sh`) directly for API calls. CORS must be configured on the backend. The redirect domain (`breviare.sh/:slug`) points to Railway directly or via a separate subdomain.

The chosen routing strategy should be documented here once decided.

---

## Environment Variable Manifest

### Backend (Railway)

| Variable | Secret | Description |
|---|---|---|
| `DATABASE_URL` | Yes | JDBC PostgreSQL connection string (e.g. `jdbc:postgresql://host:5432/db?sslmode=require`) |
| `JWT_SECRET` | Yes | HMAC secret for signing access tokens |
| `JWT_REFRESH_SECRET` | Yes | HMAC secret for signing refresh tokens (can be the same as JWT_SECRET if using RS256) |
| `JWT_ACCESS_TTL` | No | Access token lifetime (e.g. `15m`) |
| `JWT_REFRESH_TTL` | No | Refresh token lifetime (e.g. `30d`) |
| `GEOIP_DB_PATH` | No | Path to the bundled MaxMind GeoLite2 database file |
| `COOKIE_SECRET` | Yes | Secret for signing httpOnly cookies |
| `CORS_ORIGINS` | No | Comma-separated allowed origins (e.g. `https://breviare.sh,https://*.vercel.app`) |
| `RATE_LIMIT_ANON_LINKS_PER_HOUR` | No | Anonymous link creation rate limit (default TBD) |
| `RATE_LIMIT_AUTH_LINKS_PER_HOUR` | No | Authenticated link creation rate limit (default TBD) |
| `RATE_LIMIT_LOGIN_PER_15MIN` | No | Login attempt rate limit (default TBD) |
| `APP_ENV` | No | `production`, `staging`, or `development` |
| `PORT` | No | Port to listen on (Railway sets this; Spring Boot reads via `server.port=${PORT:8080}`) |

### Frontend (Vercel)

| Variable | Secret | Description |
|---|---|---|
| `NEXT_PUBLIC_API_URL` or `VITE_API_URL` | No | Base URL of the backend API (e.g. `https://api.breviare.sh`) |

Build-time variables are prefixed appropriately for the chosen framework (`NEXT_PUBLIC_` for Next.js, `VITE_` for Vite).

---

## Secret Management

- Secrets are never committed to version control.
- In production, secrets are injected via Railway's environment variable UI and Vercel's project settings.
- In local development, secrets are stored in `.env` files that are listed in `.gitignore`.
- Secret rotation: rotate `JWT_SECRET` and `JWT_REFRESH_SECRET` by deploying new values; all existing tokens are immediately invalidated (users must re-login). Plan rotation during low-traffic windows.
