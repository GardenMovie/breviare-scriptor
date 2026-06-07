# API — Redirects

The redirect endpoint is the core of Breviare. It resolves a path slug to a destination URL and issues an HTTP redirect.

This is not a traditional REST endpoint — it does not serve JSON and does not require authentication. It is the public-facing route that end users hit when they click a Breviare link.

See [adr/0004-redirect-strategy.md](../adr/0004-redirect-strategy.md) for the rationale behind using 302.

---

## Resolve a Slug

```
GET /:slug
```

Resolves either a short code or a username to its destination URL and redirects the client.

**Path parameter:** `:slug` — either a 6-character Base52 short code (with or without the display dash) or a username

The display dash is accepted and stripped before lookup: `aBc-DeF` is treated as `aBcDeF`.

### Resolution Order

1. If the slug (stripped of any dash) is 6 characters and matches a short code in `links.code` → short code resolution
2. Otherwise, check if the slug matches a `users.username` (case-insensitive) → vanity resolution

---

### Short Code Resolution

**Response `302 Found`:**

```
HTTP/1.1 302 Found
Location: https://destination.example.com/path
Cache-Control: no-store
```

No body is returned. The client follows the `Location` header immediately.

After the response is dispatched, the backend asynchronously:
- Records a click event in `analytics_events`
- Updates `links.last_clicked_at` and increments `links.click_count`

---

### Vanity Link Resolution

**Response `302 Found`:**

```
HTTP/1.1 302 Found
Location: https://user-configured-destination.com
Cache-Control: no-store
```

No analytics event is recorded for vanity link clicks in v1.

---

## Error Responses

| Condition | Status | Notes |
|---|---|---|
| Slug does not match any short code or username | `404 Not Found` | Plain HTML or JSON depending on `Accept` header |
| Matched short code is expired | `410 Gone` | Link existed but has expired; see [features/link-expiry.md](../features/link-expiry.md) |
| Matched username exists but `vanity_destination` is NULL | `404 Not Found` | User has not set a destination |

### 404 vs 410 Distinction

`404 Not Found` means the slug never existed (or was never configured).

`410 Gone` means the slug previously resolved successfully but the link has since expired. This distinction helps clients (e.g., web crawlers or monitoring tools) differentiate between a bad link and an expired one.

---

## Cache Headers

All redirect responses include:

```
Cache-Control: no-store
```

This prevents browsers, CDNs, and intermediate proxies from caching the redirect. Every visit to a Breviare link must reach the backend so that analytics can be recorded and expiry can be evaluated. See [adr/0004-redirect-strategy.md](../adr/0004-redirect-strategy.md).

---

## Health Check

```
GET /health
```

Returns `200 OK` with a minimal payload. Used by Railway for container health monitoring. Not part of the `/api/v1/` namespace.

```json
{ "status": "ok" }
```
