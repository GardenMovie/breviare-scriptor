# Vanity Links

Every registered user gets a personal vanity link: `breviare.sh/<username>`. When someone visits this URL, they are redirected to whatever destination the user has configured.

Vanity links are distinct from regular short links — they are tied to an account, use the username as the path segment, and are never subject to expiry.

---

## What a Vanity Link Is

- **Path:** `breviare.sh/<username>` — the username is the path segment, exactly as registered (though matched case-insensitively)
- **Destination:** A URL configured by the account owner; this is stored in `users.vanity_destination`
- **Redirect:** `302 Found` with `Cache-Control: no-store`, identical to regular short links
- **Permanence:** The path persists as long as the account exists; it cannot be deleted independently of the account

A vanity link that has no destination configured (i.e., `vanity_destination` is NULL) returns `404 Not Found`. Users must actively set a destination for their vanity link to function.

---

## Setting and Changing the Destination

Users configure their vanity destination via `PATCH /api/v1/users/me` by updating the `vanity_destination` field. The destination must be a valid `http` or `https` URL.

### Change Limit

Users may change their vanity destination **up to 5 times per calendar month**.

- The limit resets at 00:00 UTC on the first day of each month.
- The counter is stored in `users.vanity_destination_change_count_this_month` and validated against `users.vanity_destination_changed_at` to determine whether the count belongs to the current month.
- Attempting a 6th change in the same calendar month returns `429 Too Many Requests` with a message indicating when the limit resets.

This limit applies to destination changes only. The username (which also affects the vanity path) has a separate change limit of once per calendar month — see [features/user-accounts.md](./user-accounts.md).

---

## Username Changes and Vanity Links

Changing a username changes the vanity link path. This is covered in full in [features/user-accounts.md](./user-accounts.md), but the key implications for vanity links are:

- **Old path invalidated immediately:** `breviare.sh/<old_username>` returns `404` after a username change.
- **New path active immediately:** `breviare.sh/<new_username>` resolves to the same `vanity_destination` as before.
- **No redirect from old to new:** There is no automatic redirect from the old path to the new path. External links to the old vanity URL will break.
- **Destination is preserved:** The `vanity_destination` value carries over to the new username; the user does not need to re-set it.

The username change counts against the username change limit (once per calendar month), not the vanity destination change limit (5 per calendar month).

---

## Routing Priority

The `/:slug` route on the backend handles both short codes and usernames. Resolution order:

1. **Short code check first:** The backend checks whether the slug matches a 6-character Base52 short code in the `links` table.
2. **Username check second:** If no short code match, the backend checks whether the slug matches a username in the `users` table (case-insensitive).

This ordering means a short code that happens to match a username exactly will shadow the vanity link for that username. In practice:
- Usernames can be any length and include digits and hyphens — most usernames won't be exactly 6 all-letter characters.
- The short code lookup is case-sensitive; the username lookup is case-insensitive. A generated code like `aBcDeF` would shadow a username `abcdef` only if `aBcDeF` (exact case) matches a short code.
- Reserved words (including common short patterns) are blocked at username registration to reduce conflict surface.

If this shadowing behavior becomes a problem at scale, a stricter namespace partition can be introduced (e.g. prefix all short codes with a non-letter character), but this is not planned for v1.

---

## Vanity Links vs. Regular Short Links

| Property | Regular Short Link | Vanity Link |
|---|---|---|
| Path | `breviare.sh/aBc-DeF` (generated) | `breviare.sh/<username>` (fixed by username) |
| Destination | Set at creation; updateable | Set by user; updateable up to 5x/month |
| Expiry | After inactivity or absolute TTL | Never (while account exists) |
| Analytics | Yes (for authenticated owners) | Not in v1 |
| Ownership | Optional (anonymous possible) | Always tied to an account |
| Creation | Explicit `POST /api/v1/links` | Automatic on registration |
| Deletion | Via `DELETE /api/v1/links/:code` | Only via account deletion |

---

## Account Deletion Effect

When a user deletes their account:
- `users.vanity_destination` is removed with the user row.
- `breviare.sh/<username>` immediately returns `404`.
- The username is freed and can be registered by a new user, who would then own that vanity path.
