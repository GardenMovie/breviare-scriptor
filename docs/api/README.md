# API Reference

The Breviare REST API is served by the backend (Railway) and versioned under `/api/v1/`.

---

## Base URL

```
https://api.breviare.sh/api/v1
```

(The exact domain is TBD and will be confirmed in [deployment/overview.md](../deployment/overview.md).)

---

## Authentication

Protected endpoints require a Bearer token in the `Authorization` header:

```
Authorization: Bearer <access_token>
```

Access tokens are short-lived JWTs obtained via `POST /api/v1/auth/login`. When a token expires, use the refresh token (stored in an httpOnly cookie) to obtain a new one.

Endpoints that do not require authentication still accept the header — if a valid token is present, ownership-scoped behavior is activated (e.g., link creation assigns the link to the authenticated user).

---

## Response Envelope

All API responses follow a consistent JSON envelope.

**Success:**
```json
{
  "data": { ... }
}
```

**Error:**
```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Human-readable description",
    "details": { ... }
  }
}
```

`details` is optional and present only when additional context is useful (e.g., field-level validation errors).

---

## Common Error Codes

| HTTP Status | `error.code` | Meaning |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Request body or parameters failed validation |
| 401 | `UNAUTHORIZED` | No token provided or token is invalid/expired |
| 403 | `FORBIDDEN` | Authenticated but not authorized for this resource |
| 404 | `NOT_FOUND` | Resource does not exist |
| 409 | `CONFLICT` | Unique constraint violation (e.g., username already taken) |
| 410 | `GONE` | Resource existed but has expired |
| 429 | `RATE_LIMITED` | Too many requests; see `Retry-After` header |
| 503 | `SERVICE_UNAVAILABLE` | Transient error (e.g., short code generation exhausted retries) |

---

## Rate Limiting Headers

Rate-limited endpoints include these headers in every response:

| Header | Description |
|---|---|
| `X-RateLimit-Limit` | Maximum requests allowed in the window |
| `X-RateLimit-Remaining` | Requests remaining in the current window |
| `X-RateLimit-Reset` | Unix timestamp when the window resets |
| `Retry-After` | Present on `429` responses; seconds to wait before retrying |

---

## Pagination

List endpoints that may return many results use cursor-based pagination:

```json
{
  "data": [ ... ],
  "pagination": {
    "cursor": "opaque_cursor_string",
    "has_more": true
  }
}
```

Pass `cursor=<value>` as a query parameter to fetch the next page. `has_more: false` indicates the last page.

---

## Endpoint Index

| Resource | File |
|---|---|
| Links (create, read, update, delete) | [links.md](./links.md) |
| Users and authentication | [users.md](./users.md) |
| Analytics | [analytics.md](./analytics.md) |
| Redirects (the `/:slug` route) | [redirects.md](./redirects.md) |
