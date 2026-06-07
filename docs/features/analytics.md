# Analytics

Breviare records a click event on every redirect. Authenticated users who own a link can view aggregated analytics for it via the dashboard or the API.

---

## Why 302 Enables Analytics

HTTP `301 Moved Permanently` responses are cached by browsers. After a user's first click, their browser stores the destination URL and navigates there directly on every subsequent visit — the backend is never contacted again. This makes click counting, timestamps, referrer tracking, and geo data impossible for repeat visitors.

HTTP `302 Found` responses are not cached by default. Every click contacts the backend, and every click can be recorded. This is the core reason Breviare uses 302 for all redirects. See [adr/0004-redirect-strategy.md](../adr/0004-redirect-strategy.md).

---

## Data Collected Per Click

The following data is captured for each redirect event and stored in `analytics_events`:

| Field | Source | Notes |
|---|---|---|
| `clicked_at` | Server timestamp | When the request was received |
| `referrer` | HTTP `Referer` header | The page the user clicked the link from; absent if the user navigated directly or the referring page strips the header |
| `user_agent` | HTTP `User-Agent` header | Browser and OS identification string |
| `ip_hash` | Client IP address | SHA-256 hash with a server-side secret salt; raw IP is never stored |
| `country_code` | GeoIP lookup on client IP | ISO 3166-1 alpha-2 country code (e.g. `US`, `DE`); raw IP discarded after lookup |

**What is not stored:** Raw IP addresses, precise location (city, coordinates), device fingerprints beyond user-agent, any cookies or session identifiers.

---

## Privacy Considerations

- IP addresses are hashed before storage using SHA-256 with a per-deployment secret salt. The hash allows approximate unique-visitor counting across clicks from the same IP without being reversible by anyone who gains access to the database.
- Country-level geo is the maximum geographic resolution stored.
- The `Referer` header can contain sensitive information (e.g. a URL with query parameters that include search terms or session tokens). Breviare stores the full referrer value as provided by the browser; users should be aware of this when sharing their analytics data with others.
- A privacy policy and data retention policy should be established before launch (see Retention below).

---

## Aggregated Metrics

The analytics dashboard and API surface the following aggregated views:

| Metric | Description |
|---|---|
| Total clicks | `COUNT(analytics_events)` for the link |
| Unique visitors | Approximate count of distinct `ip_hash` values |
| Clicks over time | Daily click counts bucketed by `clicked_at::date` |
| Top referrers | Top N `referrer` values by count |
| Country breakdown | Click counts grouped by `country_code` |

All aggregations are computed at query time from the `analytics_events` table. Pre-aggregated rollup tables may be introduced later for performance if the events table grows large.

---

## Access Control

- **Authenticated link owners:** Can view full analytics for any link they own via `GET /api/v1/links/:code/analytics` and a summary across all their links via `GET /api/v1/users/me/analytics`.
- **Anonymous links:** Click events are recorded in `analytics_events` for internal monitoring, but there is no API or dashboard surface to retrieve them. Anonymous link creators cannot access analytics.
- **Other authenticated users:** Cannot access analytics for links they do not own. The API returns `403 Forbidden`.
- **Expired links:** Analytics data for expired links remains accessible to the owner. Expiry does not delete the event history.

---

## Analytics on Vanity Links

In v1, vanity link clicks (`breviare.sh/<username>`) do not generate analytics events. Vanity links serve as a personal landing redirect and analytics are out of scope for the initial release.

---

## Retention Policy

TBD. Options to evaluate:
- Keep raw events indefinitely (simple, expensive at scale)
- Roll up events older than N days into daily aggregate rows and delete the raw events (reduces table size, loses referrer/user-agent detail for old events)
- Hard-delete events older than N days (simplest for compliance, loses history)

The retention policy should be defined before launch and disclosed in the privacy policy.
