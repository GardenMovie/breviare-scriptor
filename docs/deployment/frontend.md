# Deployment — Frontend (Vercel)

The Breviare frontend is deployed to Vercel. This document covers configuration for both the Next.js and Vite paths. The framework choice is tracked in [adr/0001-frontend-framework.md](../adr/0001-frontend-framework.md).

---

## Next.js Configuration

**Framework preset:** Next.js (Vercel auto-detects this from `package.json`)

**Build command:** `next build` (default)

**Output directory:** `.next` (default)

**Node version:** 20.x or later (set in Vercel project settings or via `.node-version` file)

**Environment variables required at build time:**
- None for Next.js (server-side env vars are available at runtime, not needed at build)

**Environment variables required at runtime:**
- `NEXT_PUBLIC_API_URL` — the backend API base URL, used in client components

**Routing:**
- Next.js file-based routing handles all frontend pages.
- API proxying to Railway can be done via Next.js `rewrites` in `next.config.js`:
  ```
  /api/v1/* → <RAILWAY_BACKEND_URL>/api/v1/*
  /:slug → <RAILWAY_BACKEND_URL>/:slug
  ```
  This keeps all traffic under `breviare.sh` and avoids CORS complexity.

**Preview deployments:**
- Vercel automatically creates a preview deployment for every pull request.
- Preview deployments use a separate `NEXT_PUBLIC_API_URL` pointing to the staging backend.
- If using Neon, database branching can provide a per-PR isolated database for the staging backend.

---

## Vite (SPA) Configuration

**Framework preset:** Vite (select "Other" in Vercel or use `vite` framework setting)

**Build command:** `vite build`

**Output directory:** `dist`

**Node version:** 20.x or later

**Environment variables required at build time:**
- `VITE_API_URL` — the backend API base URL (Vite inlines `VITE_` prefixed variables at build time)

**SPA routing:**
A `vercel.json` rewrite is required to handle client-side routing. Without it, direct navigation to any route other than `/` returns a 404:

```json
{
  "rewrites": [
    { "source": "/(.*)", "destination": "/index.html" }
  ]
}
```

With Vite, API calls go directly to Railway's public URL. CORS must be configured on the backend to allow the Vercel origin.

**Preview deployments:**
- Same as Next.js: each PR gets a preview deployment.
- `VITE_API_URL` for preview deployments must point to the staging backend.
- Because environment variables are inlined at build time, Vercel rebuilds the app with preview-specific values automatically.

---

## Shared Vercel Settings

**Production branch:** `main`

**Preview branches:** All other branches (automatic)

**Build & deploy settings:**
- Auto-deploy on push: enabled
- Cancel in-progress builds on new push: enabled (default)

**Custom domain:** Set `breviare.sh` to point to Vercel in DNS settings once the domain is acquired.
