# 0003 — Database Host

**Status:** Accepted
**Date:** 2026-06-04

## Context

Brevia uses PostgreSQL as its database. The host needs to be chosen. Three options are under active consideration: Neon, Supabase, and Railway's built-in Postgres add-on.

The redirect hot path performs a single-row lookup on every click, so connection latency and pooling behavior are important. The backend is a long-running container on Railway (not serverless), which affects pooling requirements.

## Options Considered

### Option A — Neon

Neon is a serverless Postgres provider with a branching feature (database branches per git branch). It offers a generous free tier.

- **Pro:** Database branching allows per-PR preview environments with isolated data — pairs well with Vercel's preview deployments
- **Pro:** Free tier includes 0.5 GB storage and 190 compute hours/month
- **Pro:** Built-in connection pooling via PgBouncer (accessed via `/pooler` connection string)
- **Pro:** Low latency when co-located in the same region as Railway
- **Con:** Autosuspend on the free tier (pauses after 5 minutes of inactivity) adds cold-start latency to the first query after a pause — relevant for low-traffic staging environments
- **Con:** Branching workflow requires discipline to keep branches clean; unused branches consume storage

### Option B — Supabase

Supabase is a Postgres-based BaaS (backend-as-a-service) with a rich dashboard, auth primitives, and a REST/realtime API layer.

- **Pro:** Excellent dashboard for inspecting data during development
- **Pro:** Built-in auth could potentially be leveraged instead of rolling custom auth (reduces scope)
- **Pro:** Free tier includes 500 MB storage and 2 projects
- **Con:** The additional BaaS layers (auth, realtime, REST API) are not needed and add noise if Brevia is building its own backend
- **Con:** Free tier projects pause after 1 week of inactivity — a significant pain point for hobby/staging environments
- **Con:** Slightly more vendor lock-in than raw Postgres if the Supabase SDK is used for anything beyond DB access

### Option C — Railway Postgres

Railway offers a Postgres add-on that lives in the same Railway project as the backend service.

- **Pro:** Co-location with the backend guarantees the lowest possible internal network latency (private networking, no public egress)
- **Pro:** Single billing and operational surface — everything is in Railway
- **Pro:** No pausing or autosuspend; always-on
- **Con:** No database branching; preview environments share a database or require manual setup
- **Con:** Free tier is limited ($5 credit/month on Hobby plan); Postgres add-on can consume it quickly under any real load
- **Con:** Less flexible export/import tooling compared to Neon or Supabase

## Decision

**Neon (Option A).**

Brevia is building its own backend and auth, so Supabase's BaaS extras add noise rather than value. Railway Postgres is always-on but consumes the free credit quickly and lacks branching. Neon's free tier is the most generous for a hobby project, its PgBouncer pooler is a good fit for the long-running Railway backend, and database branching pairs well with Vercel preview deployments. The autosuspend cold-start is acceptable on staging; production can be kept warm by the redirect hot path itself.

## Consequences

Whichever host is chosen:
- The `DATABASE_URL` environment variable format is standard Postgres connection string — switching hosts requires only an env var change plus any connection pool configuration adjustments
- SSL must be enforced in production regardless of host
- Migration tooling (see [deployment/database.md](../deployment/database.md)) must be compatible with the chosen host's connection model
