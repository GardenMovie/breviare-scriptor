# 0001 — Frontend Framework

**Status:** Proposed
**Date:** 2026-06-04

## Context

Breviare needs a frontend that handles link creation, user authentication, account dashboards, and analytics views. The frontend will be deployed to Vercel. Two options are under consideration: Next.js and Vite (React).

The core question is whether server-side rendering provides meaningful value for this application's use cases, or whether a pure client-side app is simpler and sufficient.

## Options Considered

### Option A — Next.js

Next.js is a React framework with built-in support for server-side rendering (SSR), static generation, API routes, and file-based routing. Vercel is its original creator and provides first-class deployment support.

- **Pro:** Deep Vercel integration — zero-config deployments, automatic preview environments, edge middleware
- **Pro:** API routes allow lightweight backend logic to live in the same repo (useful for auth callbacks, redirects)
- **Pro:** SSR improves first-load performance for the public-facing creation page
- **Pro:** App Router (Next 13+) gives fine-grained control over server vs. client components
- **Con:** More opinionated and heavier than a pure Vite setup; adds framework-specific patterns to learn
- **Con:** SSR adds complexity to state management and data fetching patterns
- **Con:** Overkill if the entire app is behind authentication anyway (dashboard pages don't benefit from SSR)

### Option B — Vite (React)

Vite is a build tool and dev server. Combined with React Router it produces a single-page application (SPA). Vite deploys cleanly to Vercel as a static site.

- **Pro:** Minimal — just React and the libraries you choose; no framework magic
- **Pro:** Faster dev server startup and hot module replacement than Next.js
- **Pro:** Simpler mental model for a team already comfortable with React SPAs
- **Con:** No SSR; all rendering is client-side, meaning slightly slower first-meaningful-paint
- **Con:** No built-in API routes; all backend logic lives in the Railway service
- **Con:** Requires explicit configuration for Vercel (rewrites for SPA routing)

## Decision

[Not yet decided.]

Key factors that will drive the decision:
- Whether the team prefers a mono-repo with co-located API routes (favors Next.js) or a strict frontend/backend split (favors Vite)
- Whether the marketing/creation landing page needs fast SSR-boosted load times
- Team familiarity with Next.js App Router patterns

## Consequences

If **Next.js** is chosen:
- API routes can handle auth token exchange and lightweight proxying, reducing Railway cold-start exposure for low-traffic endpoints
- Deployment and preview environments are essentially automatic on Vercel
- The codebase will have Next.js-specific conventions (file-based routing, server components) that new contributors must learn

If **Vite** is chosen:
- All API calls go directly to Railway; CORS must be explicitly configured
- The frontend codebase is framework-agnostic React — easier to migrate build tools later
- Vercel deployment requires a `vercel.json` with a catch-all rewrite for SPA routing
