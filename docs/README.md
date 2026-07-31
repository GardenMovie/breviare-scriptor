# Breviare

A URL shortener backend. Create short links anonymously, or sign in with Google to track click
analytics, manage your links, and claim a vanity link at `https://breviare-iter.vercel.app/u/<username>`.

This repo is the **backend** for Breviare (Spring Boot / Java), you can find the frontend [here](https://github.com/GardenMovie/breviare-iter)

---

## Features

- **Anonymous link creation**: shorten any URL without an account
- **Base52 short codes**: 6-character all-letter codes (ambiguous characters like `0/O/1/l/I`
  excluded), displayed as `aBc-DeF` for readability
- **Google OAuth sign-in**: no password auth; accounts are created via Google ID token verification
- **Link expiry**: links expire after 30 days of inactivity or an optional absolute TTL;
- **Vanity links**: signed-in users claim a `username`, which doubles as their personal redirect
  slug (`https://breviare-iter.vercel.app/u/<username>`)
- **Analytics**: lifetime click count per link, plus a 7-day daily click breakdown
  (`clicksLast7Days`) backed by a nightly rollup job.
- **302 redirects with `Cache-Control: no-store`**: every click hits the server so analytics
  aren't silently lost to browser caching
- **Link safety checks on creation**: scheme allowlist (http/https only) → local blocklist
  (synced nightly from a public DNS blocklist) → optional Google Safe Browsing check (fails open
  if the API is unavailable)
- **Rate limiting**: mutating endpoints are limited per-IP (Bucket4j)

---

## Tech Stack

| Layer | Technology |
|---|---|
| Backend | Spring Boot 3.3 / Java 21 |
| Database | PostgreSQL, migrations via Flyway |
| Auth | Google OAuth (ID token verification) + JWT access/refresh tokens |
| Rate limiting | Bucket4j |

Deployed via GitHub Actions to a single EC2 instance, running as a systemd service
(`breviare.service`).

---

## API Overview

Base path: `/api/v1`. Full route list lives in the controllers under `src/main/java/com/breviare/`.

| Endpoint | Notes |
|---|---|
| `POST /api/v1/links` | Create a short link. Works anonymously or authenticated. |
| `GET /{slug}` | Redirect to the link's destination. |
| `GET /u/{username}` | Redirect to a user's vanity destination. |
| `GET /api/health` | Health check, used by the deploy pipeline. |
| `GET /api/v1/links/{code}` / `PATCH` / `DELETE` | Read, update, or delete a link (owner-only where applicable). |
| `GET /api/v1/links/{code}/analytics` | Not yet implemented — handler exists but is disabled. |
| `POST /api/v1/auth/google` | Exchange a Google ID token for a Breviare session (access token + refresh cookie). |
| `POST /api/v1/auth/refresh` / `POST /api/v1/auth/logout` | Session management via the `breviare_refresh` httpOnly cookie. |
| `GET /api/v1/users/me` / `PATCH` / `DELETE` | Manage the current user's account. |
| `GET /api/v1/users/me/links` | List the current user's links, with 7-day click stats. |
| `GET /api/v1/users/username-availability` | Check whether a username can be claimed. |
| `POST /api/v1/users/username` | Claim or change the current user's username. |

---

## Local Development

```bash
cp .env.example .env   # then set JWT_SECRET (openssl rand -hex 32) and GOOGLE_CLIENT_ID
docker run -d --name breviare-pg -e POSTGRES_USER=breviare -e POSTGRES_PASSWORD=breviare \
  -e POSTGRES_DB=breviare -p 5433:5432 postgres:16
./run-local.sh
```

`run-local.sh` loads `.env`, derives `JAVA_HOME` if unset, sets `TZ=UTC` (the rollup/expiry jobs
do UTC-sensitive date arithmetic), and fails fast with a clear message if `.env` is missing.

See `.env.example` for the full list of environment variables and what each one does.
