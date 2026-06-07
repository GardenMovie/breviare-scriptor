# Link Expiry

Links in Brevia do not live forever. This document defines how and when links expire, what happens to them after expiry, and which links are exempt.

---

## Expiry Modes

### Inactivity-Based Expiry (Default)

A link expires if it receives no clicks for a consecutive number of days. The clock is measured from `last_clicked_at`, which is initialized to `created_at` when the link is first created.

- **Default window:** 30 days
- **Applies to:** All links (anonymous and authenticated) unless overridden by an authenticated user
- **Behavior:** If `now() - last_clicked_at > inactivity_ttl_days * interval '1 day'`, the link is expired on next access (lazy) or by the background sweep (eager)

Every click resets the inactivity clock — a popular link stays alive indefinitely as long as it continues to receive traffic.

### Absolute TTL (Authenticated Users Only)

Authenticated users can optionally set a hard expiry date and time on a link (`absolute_expires_at`). When this timestamp passes, the link expires regardless of click activity.

- **Default:** NULL (no absolute TTL)
- **Applies to:** Authenticated links only; anonymous links cannot set an absolute TTL
- **Behavior:** If `now() > absolute_expires_at`, the link is expired

### Interaction Between Both Modes

Both modes can be active simultaneously on a single link. Whichever condition fires first causes expiry. For example:
- A link with `inactivity_ttl_days = 30` and `absolute_expires_at = 90 days from now` expires after 30 days of inactivity OR after 90 days absolute, whichever comes first.
- A highly active link that is clicked every day would expire at the 90-day mark regardless of activity.

---

## Expiry Evaluation

Expiry is evaluated in two ways to balance correctness and performance.

### Lazy Evaluation (On Read)

Every redirect request checks both expiry conditions immediately before issuing the `302`. If either condition is met:
1. The backend sets `links.is_expired = true` and `links.expired_at = now()` in the database.
2. The backend returns `410 Gone` instead of issuing a redirect.

This guarantees that no expired link ever successfully redirects, regardless of whether the background sweep has run.

### Eager Evaluation (Background Sweep)

A periodic background job scans `links` for rows where:
- `is_expired = false`
- AND (`now() - last_clicked_at > inactivity_ttl_days * interval '1 day'` OR (`absolute_expires_at IS NOT NULL` AND `now() > absolute_expires_at`))

It marks matching rows as `is_expired = true`. This prevents accumulation of "dead" links that must be lazily checked on every access and keeps the indexes on `last_clicked_at` and `absolute_expires_at` smaller.

Sweep frequency: hourly (TBD; tune based on observed link volume and acceptable expiry lag).

---

## What "Expired" Means

**HTTP response:** Requests to an expired short code return `410 Gone`. This is intentional — `410` signals "this resource existed but is permanently gone," distinguishing it from `404` which means "this resource was never here." See [adr/0004-redirect-strategy.md](../adr/0004-redirect-strategy.md) for redirect strategy context.

**Database state:** Expired links are **soft-deleted** — `is_expired` is set to `true` but the row is not removed. This preserves:
- The short code (so it cannot be accidentally reused for a new link)
- Analytics history (the `analytics_events` rows remain for the link owner)
- Audit trail

Short codes from expired links are not returned to the pool for reuse. The key space (~19.7 billion) is large enough that this is not a practical concern for the foreseeable future.

**Dashboard:** Expired links appear in the authenticated user's link list with an "Expired" badge. Analytics for expired links remain viewable.

---

## Vanity Link Exception

Vanity links — `brevia.sh/<username>` redirects controlled by the user's account — are **never subject to expiry** while the user's account exists. The inactivity and absolute TTL mechanisms apply only to regular short links in the `links` table.

If a user's account is deleted, their vanity path simply stops resolving (returns `404`). No expiry record is created; the path becomes orphaned.

---

## Summary Table

| Link Type | Inactivity Expiry | Absolute TTL | Exempt from Expiry |
|---|---|---|---|
| Anonymous | Yes (30-day default) | No | — |
| Authenticated (default) | Yes (30-day default) | No | — |
| Authenticated (with absolute TTL set) | Yes | Yes | — |
| Vanity link | No | No | While account exists |
