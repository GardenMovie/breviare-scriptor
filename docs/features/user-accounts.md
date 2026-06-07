# User Accounts

Breviare supports optional user registration. An account is not required to create a short link, but it unlocks analytics, link management, and a personal vanity link.

---

## Registration

Users register with an email address and password.

**Required fields:**
- `email` — must be a valid email format; normalized to lowercase; must be unique
- `password` — minimum 8 characters; no maximum enforced (hashed, so length is not a storage concern)
- `username` — chosen at registration; subject to the rules below

**On registration:**
- Password is hashed using bcrypt (or argon2id — to be decided) before storage; plaintext is never persisted
- A confirmation email may be sent (TBD for v1)
- The user is issued an access token and refresh token

---

## Username Rules

Usernames form the path segment for the user's vanity link (`breviare.sh/<username>`), so they must be URL-safe and visually unambiguous.

**Allowed characters:** `a-z`, `A-Z`, `0-9`, `-`, `_`

**Length:** 3–32 characters

**Case:** Stored and matched case-insensitively (using `citext`). `Alice` and `alice` cannot both be registered. The stored value preserves the original casing chosen at registration.

**Reserved words:** The following usernames cannot be registered because they conflict with application routes:

`api`, `auth`, `login`, `logout`, `register`, `signup`, `dashboard`, `settings`, `admin`, `help`, `about`, `terms`, `privacy`, `status`, `health`, `static`, `assets`, `favicon`

This list is enforced at registration and at every username change. Adding a new reserved word to this list does not affect existing accounts — only new registrations and changes are validated against it.

---

## Username Change Policy

Users may change their username **once per calendar month**.

- The limit resets at 00:00 UTC on the first day of each month.
- The counter is stored in `users.username_change_count_this_month` and the timestamp in `users.username_changed_at`. If `username_changed_at` is in a previous calendar month, the counter is treated as 0 for the current month.
- Attempting a second change in the same calendar month returns `429 Too Many Requests` with a message indicating when the limit resets.

**Effect of a username change:**
- The old vanity path (`breviare.sh/<old_username>`) immediately stops resolving. It returns `404 Not Found`.
- The new vanity path (`breviare.sh/<new_username>`) activates immediately.
- Short links owned by the user are unaffected — they resolve by short code, not by username.
- Any external sites or bookmarks pointing to the old vanity URL will break. The user is warned about this in the UI before confirming the change.

---

## Authentication

- **Login:** `POST /api/v1/auth/login` with `email` and `password`. Returns an access token (short-lived JWT) and sets a refresh token in an httpOnly cookie.
- **Token refresh:** The frontend uses the refresh token cookie to obtain a new access token when the current one expires.
- **Logout:** Invalidates the refresh token server-side (stored in a token blocklist or via a rotation scheme — to be decided).
- **Session strategy TBD:** JWT with refresh tokens is the baseline. An alternative is server-side sessions with a session store in Redis or the database. The choice affects scalability and logout correctness.

Access tokens are short-lived (e.g. 15 minutes). Refresh tokens are longer-lived (e.g. 30 days) and stored in an httpOnly, Secure, SameSite=Strict cookie to mitigate XSS theft.

---

## Account Deletion

`DELETE /api/v1/users/me` — requires authentication.

**Effects:**
- The `users` row is deleted.
- Short links owned by the user have `owner_id` set to NULL (via `ON DELETE SET NULL` on the foreign key). The links continue to exist as anonymous links and remain subject to normal expiry rules.
- `analytics_events` rows are retained (they are linked to `links`, not directly to `users`). Once the link itself is eventually expired and cleaned up, the events are cascade-deleted.
- The vanity path (`breviare.sh/<username>`) immediately stops resolving.
- The username is freed and can be registered by another user.

A soft-delete option (deactivate rather than delete) is out of scope for v1.

---

## Profile

`GET /api/v1/users/me` returns:
- `id`, `email`, `username`
- `username_changed_at`, `username_changes_remaining_this_month` (derived)
- `vanity_destination`, `vanity_destination_changes_remaining_this_month` (derived)
- `created_at`

Password is never included in profile responses.
