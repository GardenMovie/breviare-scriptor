# 0006 — Google-Only Authentication

**Status:** Proposed
**Date:** 2026-06-07

## Context

Breviare currently authenticates users with email + password (`AuthService`, `AuthController`, `LoginRequest`/`RegisterRequest`, `User.passwordHash`, `JwtService` for access/refresh tokens). This ADR proposes replacing that flow entirely with "Sign in with Google" — no password-based registration or login.

## Options Considered

### Option A — Keep email/password, add Google as an additional provider

- **Pro:** No migration needed for existing accounts
- **Pro:** Users who don't want a Google account can still sign up
- **Con:** Doubles the auth surface area to maintain (password reset, email verification, credential storage, brute-force protection)
- **Con:** Account-linking edge cases (same email via both methods) add complexity

### Option B — Replace email/password with Google Sign-In only ✓ Proposed

- **Pro:** Eliminates password storage, hashing, reset flows, and credential-stuffing exposure entirely
- **Pro:** Simpler `User` model and a single, well-understood verification path (Google ID token)
- **Pro:** Delegates identity verification (including email ownership) to Google
- **Con:** Users without a Google account cannot sign up
- **Con:** Requires a one-time migration/decision for any existing password-based accounts
- **Con:** Adds an external dependency (Google's OAuth/identity infrastructure) as a hard requirement for login

## Decision

**Adopt Option B: replace email/password authentication with Google Sign-In as the sole login method.**

### What changes

- `LoginRequest` / `RegisterRequest` are replaced by a single `GoogleAuthRequest(String idToken)`.
- `/api/v1/auth/register` and `/api/v1/auth/login` collapse into a single endpoint, e.g. `POST /api/v1/auth/google`, which:
  1. Verifies the Google ID token's signature, issuer (`accounts.google.com`), audience (Breviare's OAuth client ID), and expiry (via `GoogleIdTokenVerifier` or JWKS lookup against `https://www.googleapis.com/oauth2/v3/certs`)
  2. Extracts `sub` (Google user ID), `email`, `email_verified`, `name`, `picture`
  3. Looks up `User` by `googleId`; creates a new user on first sign-in
  4. Issues Breviare's own access/refresh tokens via the existing `JwtService`, unchanged
- `User` gains a `googleId` field (unique) and drops `passwordHash`.
- `UserRepository` gains `findByGoogleId` / `existsByGoogleId`.
- `PasswordEncoder` dependency is removed from `AuthService`.
- A new config value, e.g. `breviare.google.client-id`, is added for audience verification.

### What stays the same

- `JwtService`, `JwtAuthFilter`, `AuthResult`, `AuthResponse`, `RefreshResponse`
- The `/refresh` and `/logout` endpoints and the HttpOnly refresh-token cookie strategy
- Access/refresh token issuance and validation

### Open question — usernames

Google does not provide a username. Either:
- Drop `username` from `User` / `AuthResponse` entirely, or
- Auto-generate one (e.g. derived from the email local-part plus a uniqueness suffix) and allow the user to change it later

This must be resolved before implementation.

## Consequences

- No password storage, hashing, reset, or brute-force-protection code is needed anywhere in the system.
- Sign-up is gated on having a Google account — this may exclude some users.
- The frontend must integrate Google's Sign-In SDK (or One Tap) to obtain an `id_token` and send it to the new endpoint, replacing the existing email/password forms.
- Any existing email/password accounts (if Breviare has live users at the time of this change) need a migration plan — e.g. requiring them to re-register via Google and link by email, or a one-time data migration.
