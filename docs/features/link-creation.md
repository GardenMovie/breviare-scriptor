# Link Creation

Creating a short link is the core action in Brevia. Both anonymous users and authenticated users can create links; the difference lies in ownership, analytics, and expiry options.

---

## URL Validation

Before a short code is generated, the destination URL is validated:

- Scheme must be `http` or `https`. Other schemes (`ftp://`, `javascript:`, etc.) are rejected.
- The URL must parse as a valid absolute URL (host is required).
- The host must not be a Brevia domain (e.g. `brevia.sh`) — self-referential redirects are rejected to prevent loops.
- Maximum destination URL length: 2048 characters.

The backend does **not** perform DNS resolution or HTTP reachability checks at creation time. A link to a non-existent or offline host is created and will simply fail to load for the end user.

---

## Short Code Generation

Short codes use the Base52 encoding scheme described in [adr/0005-encoding-scheme.md](../adr/0005-encoding-scheme.md).

**Algorithm:**
1. Randomly select 6 characters from the Base52 alphabet (`a-z`, `A-Z`).
2. Query the `links` table for an existing row with `code = <generated>`.
3. If no row exists, the code is available — proceed to insertion.
4. If a row exists (collision), regenerate and retry.
5. After 10 failed attempts, return `503 Service Unavailable`.

At low fill levels (under 1% of the key space), collision probability per attempt is approximately 1 in 1.97 billion. Retries beyond the first are extremely rare in practice.

---

## Anonymous Creation

Any visitor can create a short link without an account.

**Request:** `POST /api/v1/links`

Required fields:
- `destination` — the URL to shorten

The backend assigns:
- `owner_id = NULL`
- `inactivity_ttl_days = 30` (hardcoded default; not configurable for anonymous links)
- `absolute_expires_at = NULL`

**Rate limiting:** Anonymous link creation is rate limited by IP address. Requests that exceed the limit receive `429 Too Many Requests`.

**Response:** The short code, display URL, and expiry information. See [api/links.md](../api/links.md) for the full response shape.

Anonymous links have no associated analytics dashboard. Click events are still recorded in `analytics_events` for internal purposes, but anonymous creators cannot retrieve them.

---

## Authenticated Creation

Authenticated users create links by including a valid `Authorization: Bearer <token>` header. The backend resolves the `owner_id` from the token.

**Request:** `POST /api/v1/links`

Optional fields (in addition to `destination`):
- `inactivity_ttl_days` — override the inactivity expiry window (minimum: 1, maximum: TBD)
- `absolute_expires_at` — set a hard expiry timestamp (must be in the future)

If neither optional field is provided, the same defaults as anonymous creation apply.

**Rate limiting:** Higher limits than anonymous creation, keyed by user ID rather than IP.

**Ownership:** The link is associated with the authenticated user's account. The user can:
- View the link in their dashboard
- Access analytics for the link (see [features/analytics.md](./analytics.md))
- Delete the link
- Update the destination or expiry settings

---

## Expiry at Creation Time

See [features/link-expiry.md](./link-expiry.md) for the full expiry specification. In brief:

- All links default to inactivity-based expiry: if the link receives no clicks for `inactivity_ttl_days` consecutive days, it expires.
- Authenticated users can additionally set an absolute expiry date; if set, whichever condition fires first causes expiry.
- The `last_clicked_at` field is initialized to `created_at` at creation time — the inactivity clock starts from the moment the link is created, not from the first click.

---

## Response

A successful creation returns the short code, the full short URL, and expiry metadata. The short URL is formatted with the display dash (e.g. `https://brevia.sh/aBc-DeF`). The stored code (without the dash) is also included for use in subsequent API calls.

See [api/links.md](../api/links.md) for the full response schema.
