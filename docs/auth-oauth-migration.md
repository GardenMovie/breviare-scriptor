# Auth: Current State & Google OAuth Plan

## Current state (as of `restore-auth` branch)

Auth is **broken for all new users** — there is currently no way to register or log in. There are **no existing users in the database**, so this is a clean implementation, not a migration.

- `POST /api/v1/auth/register` and `POST /api/v1/auth/login` are stubbed to always return `501 NOT_IMPLEMENTED` (`AuthService.java`). They accept a body (including a `password` field) but throw before using it.
- The `users` table and `User` entity have **no password column** — it was removed already (`V1__init.sql`, `User.java`). `UserDetailsServiceImpl` loads a user with a `null` password and no authorities.
- `POST /api/v1/auth/refresh` and `POST /api/v1/auth/logout` work today — refresh reads the `breviare_refresh` cookie, validates the JWT, and issues a new access token. There's just nothing upstream that ever sets that cookie for a new session.
- `JwtAuthFilter` and `JwtService` are provider-agnostic: they only know how to mint/validate Breviare's own JWTs from a `UUID` subject. This part doesn't need to change for OAuth.
- No OAuth client code or dependencies exist yet (`pom.xml` has no Google OAuth libraries).
- `SecurityConfig` permits all of `/api/v1/auth/**`, so the broken endpoints aren't blocked by anything else — they're just dead ends.

This matches the git history: password auth was intentionally removed (`Some comments and removed password based auth, need to implement google and or github sso`), then auth was temporarily fully removed and reverted, leaving the stub in place.

### Dead code to remove during implementation
- `RegisterRequest` / `LoginRequest` (still validate a `password` field nobody reads)
- `AuthService.register` / `AuthService.login`
- `AuthController.register` / `AuthController.login`
- The commented-out `user.getPasswordHash()` line in `UserDetailsServiceImpl`

## Goal

Replace password-based register/login with **Google OAuth** sign-in as the only way to create an account or log in. Keep the existing JWT access/refresh-cookie mechanism for session handling after sign-in — only the *credential verification* step changes.

Since there are no existing users, every account is created via Google sign-in; there's no legacy-account migration or linking problem to solve.

## Proposed flow

1. Frontend uses Google Identity Services (or the standard authorization-code flow) to get an ID token directly.
2. Frontend sends the ID token to a new backend endpoint:
   - `POST /api/v1/auth/google` — body: `{ idToken }`
3. Backend verifies the ID token's signature/audience using Google's public keys (e.g. `google-api-client` or manual JWKS verification) — no extra round trip needed since the ID token already carries verified claims (`sub`, `email`).
4. Backend looks up a user by `(provider, provider_user_id)`. If none exists, creates a new `User` row from the token's `email`/`sub`, with `username` left unset.
5. Backend issues Breviare's own access token + refresh cookie exactly as today (`AuthResult`, `setRefreshCookie`) — downstream of this point, nothing changes.
6. If the user has no `username` yet, frontend prompts for one (this is also their vanity link slug). Frontend calls a username-availability check, then submits the chosen username to claim it.

## Required changes

### Database
Add `provider` and `provider_user_id` columns directly to `users` (no separate identities table needed — there's exactly one provider per user for now):

```sql
ALTER TABLE users
    ADD COLUMN provider         TEXT NOT NULL DEFAULT 'google',
    ADD COLUMN provider_user_id TEXT NOT NULL;

ALTER TABLE users
    ADD CONSTRAINT users_provider_identity_unique UNIQUE (provider, provider_user_id);
```

If a future need arises to let one user link multiple providers, this can be split into a separate table later — not a concern now with zero existing rows.

`users.username` (already `CITEXT NOT NULL UNIQUE`) must become **nullable** — a freshly created OAuth user has no username until they complete the post-auth username-claim step. The existing `username_changed_at` / `username_change_count_this_month` columns are unaffected and continue to govern subsequent changes once a username is set.

### Backend
- Add Google client config (`breviare.oauth.google.client-id`) to `application.yml`, sourced from an env var like the existing `JWT_SECRET` pattern.
- New `GoogleAuthService` responsible for verifying the ID token and resolving/creating a `User`.
- New `/api/v1/auth/google` endpoint in `AuthController` (or a new `OAuthController`), replacing `/register` and `/login`.
- Remove `register`/`login` stubs and their request DTOs as listed above.
- `SecurityConfig` already permits `/api/v1/auth/**`, so the new endpoint needs no further config changes.
- Add a Maven dependency for Google ID token verification (e.g. `com.google.api-client:google-api-client`) or implement JWKS verification manually with the existing `io.jsonwebtoken` (jjwt) dependency already in use for Breviare's own JWTs.
- New username-claim endpoints, gated behind authentication (the access token issued in step 5 above):
  - `GET /api/v1/users/username-availability?username=foo` — checks the `CITEXT` unique constraint, returns whether it's taken.
  - `POST /api/v1/users/username` — body: `{ username }`. Sets the authenticated user's username if available; reuses the existing once-a-month change-limit logic already on `User` (`usernameChangedAt`, `usernameChangeCountThisMonth`) for any *future* change, but the very first claim (when `username` is currently null) should not consume that monthly allowance.
- Any endpoint that depends on having a vanity link (e.g. setting `vanityDestination`) should require `username` to be non-null first, since the username **is** the vanity link slug.

### Frontend
- Replace the email/password form with a "Sign in with Google" button.
- Wire up Google Identity Services to obtain an ID token client-side.
- Send the ID token to `POST /api/v1/auth/google`; handle the same `AuthResponse` shape as before (`{ user, accessToken }` + refresh cookie set by the server).
- If the returned `user.username` is null, show a "claim your username" step immediately after sign-in (this is also the user's vanity link slug). Debounce input against `GET /api/v1/users/username-availability` and block submission until available; on submit, call `POST /api/v1/users/username` then proceed into the app.

## Open questions
- Future: if GitHub (or another provider) is added later, the unique constraint on `(provider, provider_user_id)` already supports it without further schema changes — only a second `provider` value and service implementation would be needed.
