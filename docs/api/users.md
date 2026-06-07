# API — Users & Authentication

Endpoints for account registration, login, profile management, and authentication.

---

## Register

```
POST /api/v1/auth/register
```

Creates a new user account.

**Request body:**

| Field | Type | Required | Description |
|---|---|---|---|
| `email` | string | Yes | Must be a valid email; normalized to lowercase |
| `password` | string | Yes | Minimum 8 characters |
| `username` | string | Yes | 3–32 characters; `a-z A-Z 0-9 - _`; not reserved |

**Response `201 Created`:**

```json
{
  "data": {
    "user": {
      "id": "uuid",
      "email": "user@example.com",
      "username": "alice",
      "created_at": "2026-06-04T12:00:00Z"
    },
    "access_token": "eyJ..."
  }
}
```

The refresh token is set as an httpOnly cookie (`brevia_refresh`).

**Errors:**
- `400 VALIDATION_ERROR` — missing fields, invalid email format, password too short, invalid username characters or length
- `409 CONFLICT` — email or username already taken

---

## Login

```
POST /api/v1/auth/login
```

Authenticates an existing user and issues tokens.

**Request body:**

| Field | Type | Required |
|---|---|---|
| `email` | string | Yes |
| `password` | string | Yes |

**Response `200 OK`:**

```json
{
  "data": {
    "user": {
      "id": "uuid",
      "email": "user@example.com",
      "username": "alice"
    },
    "access_token": "eyJ..."
  }
}
```

Refresh token set as httpOnly cookie.

**Errors:**
- `400 VALIDATION_ERROR` — missing fields
- `401 UNAUTHORIZED` — invalid email or password (message does not distinguish which)
- `429 RATE_LIMITED` — too many failed login attempts from this IP

---

## Refresh Access Token

```
POST /api/v1/auth/refresh
```

Issues a new access token using the refresh token cookie. No request body required — the refresh token is read from the cookie.

**Response `200 OK`:**

```json
{
  "data": {
    "access_token": "eyJ..."
  }
}
```

**Errors:**
- `401 UNAUTHORIZED` — no refresh token cookie, or it is expired or invalid

---

## Logout

```
POST /api/v1/auth/logout
```

Invalidates the refresh token and clears the cookie. Authentication required.

**Response `204 No Content`**

---

## Get Profile

```
GET /api/v1/users/me
```

Returns the authenticated user's profile. Authentication required.

**Response `200 OK`:**

```json
{
  "data": {
    "id": "uuid",
    "email": "user@example.com",
    "username": "alice",
    "vanity_destination": "https://alice.example.com",
    "username_changed_at": "2026-05-01T10:00:00Z",
    "username_changes_remaining_this_month": 0,
    "vanity_destination_changed_at": "2026-06-01T09:00:00Z",
    "vanity_destination_changes_remaining_this_month": 3,
    "created_at": "2025-01-15T08:00:00Z"
  }
}
```

---

## Update Profile

```
PATCH /api/v1/users/me
```

Updates the authenticated user's profile. Authentication required.

**Request body (all fields optional; at least one required):**

| Field | Type | Description |
|---|---|---|
| `username` | string | New username; subject to username rules and the once-per-month limit |
| `vanity_destination` | string or `null` | New vanity redirect destination; subject to 5x-per-month limit. Pass `null` to clear |

**Response `200 OK`:** Updated profile object (same shape as GET /users/me).

**Errors:**
- `400 VALIDATION_ERROR` — no fields provided, invalid username, invalid URL for vanity_destination
- `409 CONFLICT` — username already taken
- `429 RATE_LIMITED` — username change limit or vanity destination change limit exceeded; response body includes `resets_at` timestamp

---

## List Owned Links

```
GET /api/v1/users/me/links
```

Returns paginated list of links owned by the authenticated user.

**Query parameters:**

| Parameter | Default | Description |
|---|---|---|
| `cursor` | — | Pagination cursor from previous response |
| `limit` | 20 | Page size (max 100) |
| `include_expired` | `false` | Whether to include expired links |

**Response `200 OK`:**

```json
{
  "data": [
    {
      "code": "aBcDeF",
      "display_code": "aBc-DeF",
      "short_url": "https://breviare.sh/aBc-DeF",
      "destination": "https://example.com",
      "click_count": 42,
      "is_expired": false,
      "created_at": "2026-06-01T10:00:00Z",
      "last_clicked_at": "2026-06-04T08:00:00Z"
    }
  ],
  "pagination": {
    "cursor": "opaque_cursor",
    "has_more": true
  }
}
```

---

## Delete Account

```
DELETE /api/v1/users/me
```

Permanently deletes the authenticated user's account. Authentication required.

This action is irreversible. Owned links become anonymous; analytics data is retained until the links themselves expire and are cleaned up.

**Response `204 No Content`**
