# API — Links

Endpoints for creating and managing short links.

Short codes in API paths use the 6-character form **without** the display dash (e.g. `aBcDeF`, not `aBc-DeF`). The display-formatted version (`aBc-DeF`) is included in responses for UI use.

---

## Create a Link

```
POST /api/v1/links
```

Creates a new short link. Authentication is optional; if a valid Bearer token is present, the link is assigned to that user.

**Request body:**

| Field | Type | Required | Description |
|---|---|---|---|
| `destination` | string | Yes | The URL to redirect to |
| `inactivity_ttl_days` | integer | No | Days of inactivity before expiry (authenticated users only; default: 30) |
| `absolute_expires_at` | ISO 8601 datetime | No | Hard expiry timestamp (authenticated users only) |

**Response `201 Created`:**

```json
{
  "data": {
    "code": "aBcDeF",
    "display_code": "aBc-DeF",
    "short_url": "https://breviare.sh/aBc-DeF",
    "destination": "https://example.com/some/long/url",
    "owner_id": "uuid-or-null",
    "created_at": "2026-06-04T12:00:00Z",
    "last_clicked_at": "2026-06-04T12:00:00Z",
    "inactivity_ttl_days": 30,
    "absolute_expires_at": null,
    "click_count": 0
  }
}
```

**Errors:**
- `400 VALIDATION_ERROR` — invalid URL, invalid TTL value, `absolute_expires_at` in the past
- `429 RATE_LIMITED` — anonymous or per-user rate limit exceeded
- `503 SERVICE_UNAVAILABLE` — short code generation failed after max retries

---

## Get Link Metadata

```
GET /api/v1/links/:code
```

Returns metadata for a single link. For links with an owner, authentication as the owner is required.

**Path parameter:** `:code` — 6-character code without dash

**Response `200 OK`:**

Same shape as the creation response, plus:

```json
{
  "data": {
    "code": "aBcDeF",
    "display_code": "aBc-DeF",
    "short_url": "https://breviare.sh/aBc-DeF",
    "destination": "https://example.com/some/long/url",
    "owner_id": "uuid-or-null",
    "created_at": "2026-06-04T12:00:00Z",
    "last_clicked_at": "2026-06-05T08:30:00Z",
    "inactivity_ttl_days": 30,
    "absolute_expires_at": null,
    "click_count": 42,
    "is_expired": false,
    "expired_at": null
  }
}
```

**Errors:**
- `401 UNAUTHORIZED` — link has an owner but no valid token provided
- `403 FORBIDDEN` — authenticated but not the owner
- `404 NOT_FOUND` — code does not exist
- `410 GONE` — link has expired

---

## Update a Link

```
PATCH /api/v1/links/:code
```

Updates the destination URL or expiry settings for an owned link. Authentication as the owner is required.

**Request body (all fields optional):**

| Field | Type | Description |
|---|---|---|
| `destination` | string | New destination URL |
| `inactivity_ttl_days` | integer | New inactivity window |
| `absolute_expires_at` | ISO 8601 datetime or `null` | Update or remove the absolute TTL |

At least one field must be present.

**Response `200 OK`:** Updated link object (same shape as GET response).

**Errors:**
- `400 VALIDATION_ERROR` — no fields provided, invalid URL, invalid TTL
- `401 UNAUTHORIZED` — no valid token
- `403 FORBIDDEN` — not the owner
- `404 NOT_FOUND`
- `410 GONE` — cannot update an expired link

---

## Delete a Link

```
DELETE /api/v1/links/:code
```

Permanently deletes a link. Authentication as the owner is required. Anonymous links cannot be deleted via the API.

**Response `204 No Content`:** Empty body.

**Errors:**
- `401 UNAUTHORIZED`
- `403 FORBIDDEN`
- `404 NOT_FOUND`

Note: deletion removes the `links` row and cascade-deletes all associated `analytics_events`. This operation is irreversible.

---

## List Owned Links

```
GET /api/v1/users/me/links
```

Returns all links owned by the authenticated user, newest first. See [users.md](./users.md).
