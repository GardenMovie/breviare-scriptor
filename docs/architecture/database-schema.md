# Database Schema

Breviare uses a single PostgreSQL database. This document is the canonical reference for every table, column, constraint, and index.

For the encoding rationale behind short codes, see [adr/0005-encoding-scheme.md](../adr/0005-encoding-scheme.md).

---

## Tables

### `users`

Stores registered accounts.

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | `uuid` | PK, default `gen_random_uuid()` | Internal identifier; never exposed in URLs |
| `email` | `text` | NOT NULL, UNIQUE | Lowercase-normalized on write |
| `password_hash` | `text` | NOT NULL | bcrypt or argon2 hash; plaintext never stored |
| `username` | `citext` | NOT NULL, UNIQUE | Case-insensitive text; see username rules in [features/user-accounts.md](../features/user-accounts.md) |
| `username_changed_at` | `timestamptz` | NULLABLE | Timestamp of most recent username change; NULL if never changed |
| `username_change_count_this_month` | `integer` | NOT NULL, default 0 | Resets at the start of each calendar month |
| `vanity_destination` | `text` | NULLABLE | The URL that `breviare.sh/<username>` currently redirects to; NULL if not set |
| `vanity_destination_change_count_this_month` | `integer` | NOT NULL, default 0 | Resets at the start of each calendar month; max 5 |
| `vanity_destination_changed_at` | `timestamptz` | NULLABLE | Timestamp of most recent vanity destination change |
| `created_at` | `timestamptz` | NOT NULL, default `now()` | |
| `updated_at` | `timestamptz` | NOT NULL, default `now()` | Updated by trigger on any row change |

**Indexes:**
- `users_email_idx` on `email` (for login lookup)
- `users_username_idx` on `username` (for vanity link resolution; already covered by UNIQUE constraint but explicit for clarity)

**Notes:**
- `citext` (case-insensitive text) is used for `username` so that `Alice` and `alice` are treated as the same username. The `citext` extension must be enabled on the database.
- The monthly counters (`*_count_this_month`) are reset by the backend at the start of each calendar month, either lazily on first use or via a scheduled job. The `*_changed_at` timestamp is used to detect whether the counter belongs to the current month.

---

### `links`

Stores every short link — both anonymous and user-owned.

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | `uuid` | PK, default `gen_random_uuid()` | |
| `code` | `char(6)` | NOT NULL, UNIQUE | 6-character Base52 code, stored without the display dash |
| `destination` | `text` | NOT NULL | The full destination URL |
| `owner_id` | `uuid` | NULLABLE, FK → `users.id` ON DELETE SET NULL | NULL for anonymous links |
| `created_at` | `timestamptz` | NOT NULL, default `now()` | |
| `last_clicked_at` | `timestamptz` | NOT NULL, default `now()` | Updated on every redirect; used for inactivity expiry |
| `inactivity_ttl_days` | `integer` | NOT NULL, default 30 | Number of days of no clicks before expiry |
| `absolute_expires_at` | `timestamptz` | NULLABLE | Hard expiry timestamp; NULL means no absolute TTL. Only settable by authenticated users |
| `click_count` | `bigint` | NOT NULL, default 0 | Denormalized total click counter; incremented on each redirect |
| `is_expired` | `boolean` | NOT NULL, default false | Soft-delete flag set when expiry is detected; expired links return 410 |
| `expired_at` | `timestamptz` | NULLABLE | When the link was marked expired |

**Indexes:**
- `links_code_idx` on `code` (primary lookup path; covered by UNIQUE but explicit)
- `links_owner_id_idx` on `owner_id` (for listing all links owned by a user)
- `links_last_clicked_at_idx` on `last_clicked_at` WHERE `is_expired = false` (for expiry sweep queries)
- `links_absolute_expires_at_idx` on `absolute_expires_at` WHERE `absolute_expires_at IS NOT NULL AND is_expired = false` (for absolute TTL sweep)

**Notes:**
- Expiry is evaluated lazily on each redirect request (check `last_clicked_at + inactivity_ttl_days` and `absolute_expires_at` at lookup time) and/or eagerly by a background sweep job. If either condition is met, `is_expired` is set to true and the request receives a 410.
- `click_count` is a denormalized counter. It may drift slightly from `COUNT(analytics_events)` under high concurrency but is used for display purposes where approximate counts are acceptable. The authoritative count is derivable from `analytics_events`.
- When `owner_id` is set to NULL via `ON DELETE SET NULL` (account deletion), the link becomes anonymous but is not deleted. Expiry rules continue to apply.

---

### `analytics_events`

One row per redirect click. This is the highest-volume table.

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | `bigserial` | PK | Integer PK for insertion performance |
| `link_id` | `uuid` | NOT NULL, FK → `links.id` ON DELETE CASCADE | |
| `clicked_at` | `timestamptz` | NOT NULL, default `now()` | |
| `referrer` | `text` | NULLABLE | Value of the HTTP `Referer` header; NULL if absent |
| `user_agent` | `text` | NULLABLE | Raw User-Agent string |
| `ip_hash` | `char(64)` | NULLABLE | SHA-256 hash of the client IP; raw IP is not stored |
| `country_code` | `char(2)` | NULLABLE | ISO 3166-1 alpha-2 country code derived from IP geo lookup |

**Indexes:**
- `analytics_events_link_id_clicked_at_idx` on `(link_id, clicked_at DESC)` (for per-link analytics queries with date range filters)
- `analytics_events_clicked_at_idx` on `clicked_at` (for time-range aggregate queries)

**Notes:**
- Raw IP addresses are never stored. The IP is hashed (SHA-256 with a server-side secret salt) to allow approximate unique-visitor counting without storing PII.
- `country_code` is derived at request time from the IP address using a server-side geo database (e.g. MaxMind GeoLite2). The raw IP is discarded after geo lookup.
- Retention policy TBD: raw events may be rolled up into daily/weekly aggregate rows after a defined period to control table growth.
- This table is append-only. No updates or deletes occur in the normal request path (cascade deletes happen only when a link is hard-deleted, which is not the normal expiry path).

---

## Reserved Namespace

Usernames must not conflict with application routes. The following slugs are reserved and cannot be registered as usernames:

`api`, `auth`, `login`, `logout`, `register`, `signup`, `dashboard`, `settings`, `admin`, `help`, `about`, `terms`, `privacy`, `status`, `health`, `static`, `assets`, `favicon`

This list is enforced at registration time. See [features/user-accounts.md](../features/user-accounts.md).

---

## Extensions Required

```sql
CREATE EXTENSION IF NOT EXISTS "pgcrypto";  -- for gen_random_uuid()
CREATE EXTENSION IF NOT EXISTS "citext";    -- for case-insensitive username
```

---

## Relationship Diagram

```
users
  │
  ├─── links (owner_id → users.id, nullable)
  │       │
  │       └─── analytics_events (link_id → links.id)
  │
  └─── (vanity_destination stored inline on users row)
```

Vanity link destinations are stored directly on the `users` row rather than in a separate table. This keeps the vanity redirect lookup a single-row read by username. If a history of past vanity destinations is needed in the future, a `vanity_destination_history` table can be added without changing the primary lookup path.
