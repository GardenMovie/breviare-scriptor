# Data Flow

This document describes how data moves through Brevia at runtime for each major operation. For the static structure of the system (tiers, deployment targets, responsibilities), see [overview.md](./overview.md). For the database tables referenced here, see [database-schema.md](./database-schema.md).

---

## Link Creation Flow

Two paths exist depending on whether the creator is authenticated.

### Anonymous Creation

1. User submits a destination URL via the frontend form (no auth token present).
2. Frontend sends `POST /api/v1/links` with the destination URL in the request body.
3. Backend validates the URL (scheme must be `http` or `https`; host must be resolvable; not a Brevia domain to prevent redirect loops).
4. Backend checks the anonymous rate limit for the caller's IP address.
5. Backend generates a 6-character Base52 code and checks for uniqueness in `links.code`. Retries up to 10 times on collision.
6. Backend inserts a row into `links` with `owner_id = NULL`, `inactivity_ttl_days = 30` (default), `absolute_expires_at = NULL`.
7. Backend returns the short code and display URL (`aBc-DeF` format) to the frontend.
8. No analytics record is written at creation time.

### Authenticated Creation

Steps 1–5 are identical, but with an `Authorization: Bearer <token>` header present.

3a. Backend validates the auth token and resolves `owner_id` from the token claims.
6. Backend inserts a row into `links` with `owner_id` set to the authenticated user's ID. If the user supplied a custom `inactivity_ttl_days` or `absolute_expires_at`, those values are stored; otherwise defaults apply.
7. Backend returns the short code, display URL, and expiry information.

---

## Redirect Flow

This is the hot path — it executes on every click.

1. User visits `brevia.sh/aBc-DeF` (or `brevia.sh/abc-def` — case is preserved as-is).
2. The request reaches the backend's catch-all route handler for `/:code`.
3. Backend strips the display dash if present, normalizing to a 6-character code (`aBcDeF`).
4. Backend queries `links` by `code` (index lookup).

**If the code does not exist:**
→ Return `404 Not Found`.

**If the code exists but `is_expired = true`:**
→ Return `410 Gone`.

**If the code exists and is not expired:**
5. Backend evaluates expiry conditions:
   - If `now() - last_clicked_at > inactivity_ttl_days * interval '1 day'` → mark expired, return `410 Gone`.
   - If `absolute_expires_at IS NOT NULL AND now() > absolute_expires_at` → mark expired, return `410 Gone`.
6. Backend issues `302 Found` with `Location: <destination>` and `Cache-Control: no-store`.
7. **After the response is sent** (async, non-blocking):
   - Increment `links.click_count`.
   - Update `links.last_clicked_at` to `now()`.
   - Insert a row into `analytics_events` with referrer, user-agent, hashed IP, and country code.

The analytics write happens after the 302 is dispatched. This keeps the redirect latency to a single read query; the write is fire-and-forget from the client's perspective.

---

## Vanity Link Resolution Flow

Vanity links (`brevia.sh/<username>`) share the same `/:slug` route as short codes. The backend must distinguish between them.

1. User visits `brevia.sh/john`.
2. Backend receives the slug `john`.
3. Backend first checks whether `john` matches a short code in `links.code` (6 characters, Base52). Since usernames can be any length and use a different character set, a 6-character all-letter slug could theoretically match both.

**Resolution priority:** Short codes are checked first. If a 6-character Base52 slug matches an active short code, it is treated as a short code. Usernames are checked second.

This means the namespace is not strictly partitioned — a username that happens to be exactly 6 Base52 characters could be shadowed by a short code with the same string. This is mitigated by:
- Usernames are matched case-insensitively (`citext`); short codes are case-sensitive. A username `abcdef` and a short code `abcdef` (exact case match) would collide, but `aBcDeF` (a short code) would not shadow `abcdef` (a username) under case-insensitive lookup.
- In practice, the probability of a randomly generated 6-char code colliding with a registered username is low.

4. If no short code matches, backend queries `users.username` (case-insensitive) for the slug.

**If no username matches:**
→ Return `404 Not Found`.

**If username matches but `vanity_destination` is NULL:**
→ Return `404 Not Found` (user has not set a vanity destination yet).

**If username matches and `vanity_destination` is set:**
5. Backend issues `302 Found` with `Location: <vanity_destination>` and `Cache-Control: no-store`.
6. No analytics event is recorded for vanity link clicks in v1 (this may change).

---

## Analytics Write Path

Analytics events are written asynchronously after each redirect. The write path:

1. After dispatching the `302` response, the backend queues an analytics write (in-process async task or a lightweight queue).
2. The backend:
   - Extracts `Referer` header (nullable).
   - Extracts `User-Agent` header (nullable).
   - Hashes the client IP with SHA-256 + a server-side secret salt.
   - Performs a geo lookup on the raw IP using a local GeoIP database, extracting the 2-letter country code.
   - Discards the raw IP.
3. Inserts into `analytics_events`.
4. Updates `links.last_clicked_at` and increments `links.click_count` (single UPDATE statement).

The geo lookup uses a bundled local database (MaxMind GeoLite2 or equivalent) to avoid adding an external API call to the redirect path.

---

## Expiry Evaluation Strategy

Expiry is evaluated in two ways:

**Lazy (on read):** Every redirect request checks the expiry conditions before issuing the 302. If the link is found to be expired at lookup time, `is_expired` is set to true in the same transaction and a 410 is returned. This ensures expired links are never successfully redirected, even if the background sweep has not run yet.

**Eager (background sweep):** A periodic job scans for links where:
- `is_expired = false` AND `now() - last_clicked_at > inactivity_ttl_days * interval '1 day'`
- OR `is_expired = false` AND `absolute_expires_at IS NOT NULL` AND `now() > absolute_expires_at`

It marks matching rows as `is_expired = true`. This prevents the lazy check from doing index lookups on rows that are long-expired and no longer receiving traffic.

Sweep frequency: TBD (hourly is a reasonable starting point).

---

## Username Change Flow

1. Authenticated user sends `PATCH /api/v1/users/me` with a new `username`.
2. Backend checks `users.username_change_count_this_month`. If ≥ 1 (and the previous change was in the current calendar month), return `429 Too Many Requests`.
3. Backend validates the new username (length, characters, not reserved, not taken).
4. Backend updates `users.username` to the new value, sets `username_changed_at = now()`, increments `username_change_count_this_month`.
5. The old username path (`brevia.sh/<old_username>`) immediately becomes invalid — it will return `404` on the next request because no `users.username` row matches it anymore.
6. The new username path (`brevia.sh/<new_username>`) becomes active immediately.

No redirect is issued from old username to new username. Users who bookmarked the old vanity link must be informed (via the UI) that the old path is no longer valid.
