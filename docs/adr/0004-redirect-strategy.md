# 0004 — Redirect Strategy (302 vs 301)

**Status:** Accepted
**Date:** 2026-06-04

## Context

When a user visits a Brevia short link, the backend must issue an HTTP redirect to the destination URL. Two status codes are applicable: `301 Moved Permanently` and `302 Found` (temporary redirect). The choice has significant implications for analytics and future flexibility.

## Options Considered

### Option A — 301 Moved Permanently

A 301 tells the browser and any intermediate caches that the resource has moved permanently to the new location. The browser stores this mapping and, on subsequent visits to the short URL, navigates directly to the destination without contacting the backend.

- **Pro:** Reduced load on the backend after the first visit — cached redirects require no server round-trip
- **Pro:** Better for SEO in the rare case where a short link is indexed (passes link equity to destination)
- **Con:** Analytics break after the first visit. Repeat visitors bypass the server entirely, making click counts, timestamps, referrer data, and geo data impossible to collect
- **Con:** Once a browser caches a 301, changing the destination URL has no effect for that browser until the cache is manually cleared or expires — vanity link destination changes would be silently ignored
- **Con:** Permanent caching conflicts with Brevia's link expiry model; expired links would still redirect for users who previously visited

### Option B — 302 Found (Temporary)

A 302 tells the browser the resource is temporarily at the given location. Browsers do not cache 302 responses (unless explicit cache headers are set), so every visit contacts the backend.

- **Pro:** Every click passes through the backend, enabling complete analytics (click count, timestamp, referrer, user-agent, geo)
- **Pro:** Destination changes (including vanity link target changes) take effect immediately for all users
- **Pro:** Expiry works correctly — the backend can return 410 Gone for expired links even if the user visited before
- **Con:** Every click incurs a backend round-trip; no caching benefit
- **Con:** Marginally higher latency compared to a cached 301 (network round-trip to Railway on every visit)

## Decision

**Use 302 Found for all redirects.**

Analytics are a core feature of Brevia. The 301 caching behavior would make click tracking fundamentally unreliable after the first visit, which eliminates the primary value proposition for authenticated users. The latency trade-off is acceptable given that the redirect path is the hot path and is optimized for single-query lookup.

Additionally, the mutable nature of vanity link destinations makes 301 semantics incorrect by definition — the destination is not permanently fixed.

## Consequences

- The backend's redirect endpoint must set appropriate `Cache-Control: no-store` or equivalent headers to prevent any intermediary from caching the response
- Every click to a short link generates a backend request; Railway's compute allocation should account for click volume, not just link creation volume
- SEO: short links will not pass link equity to destinations. This is acceptable — Brevia is a utility, not an SEO product
- Future option: authenticated users could opt into 301 for a specific link if they explicitly disable analytics and want maximum performance. This is not in scope for v1.
