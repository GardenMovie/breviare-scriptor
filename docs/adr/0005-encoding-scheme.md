# 0005 — Short Code Encoding Scheme

**Status:** Accepted
**Date:** 2026-06-04

## Context

Every short link needs a unique identifier — a short code that appears in the URL. Key requirements:

- Short enough to be memorable and easy to type
- Large enough key space to avoid frequent collisions
- URL-safe characters only
- Visually unambiguous — no characters that look alike
- Human-friendly display format

## Options Considered

### Option A — Base62 (a-z, A-Z, 0-9), 6 characters

Base62 uses all alphanumeric characters. 62^6 = ~56.8 billion combinations.

- **Pro:** Larger key space than Base52
- **Con:** Includes visually ambiguous pairs: `0` vs `O`, `1` vs `l`, `1` vs `I` — a user hand-typing a link could easily mistake one for the other
- **Con:** Mixed digits and letters look less clean in a display context

### Option B — Base52 (a-z, A-Z), 6 characters

Base52 uses only letters, excluding all digits. 52^6 = ~19.7 billion combinations.

- **Pro:** No ambiguous characters — every character in the alphabet is visually distinct from every other
- **Pro:** All-letter codes read more naturally and are easier to spell aloud
- **Pro:** 19.7 billion is an ample key space; at 10 million links created per day it would take over 5 years to exhaust
- **Con:** Smaller key space than Base62; if Breviare grows to extreme scale, code length must eventually increase from 6 to 7 characters
- **Con:** Case-sensitive — `ABcDef` and `abcdef` are different codes. URLs are technically case-sensitive in the path component, so this is correct behavior, but users must be careful when hand-transcribing

### Option C — Base36 (a-z, 0-9, lowercase only), 7 characters

Base36 lowercase-only eliminates case sensitivity entirely. 36^7 = ~78.4 billion combinations.

- **Pro:** Case-insensitive — `abc123` and `ABC123` resolve to the same link
- **Pro:** Larger key space at 7 characters than Base52 at 6
- **Con:** Includes digits; reintroduces `0`/`o` and `1`/`l` ambiguity
- **Con:** 7-character codes are longer and less visually clean

## Decision

**Use Base52 (a-z, A-Z), 6 characters, displayed as `XXX-XXX`.**

The combination of no-ambiguous-characters and a clean all-letter appearance makes Base52 the best fit for a utility where users may occasionally read or share links verbally. The key space of ~19.7 billion is sufficient for the foreseeable future.

**Display format:** The 6-character code is stored and matched without a separator. On display (in the web UI, API responses, and emails), a dash is inserted after the third character — e.g. stored as `aBcDeF`, displayed as `aBc-DeF`. The dash is stripped before lookup. This makes codes easier to read and remember while keeping the storage and URL format simple.

**Alphabet:** `abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ` (52 characters, index 0–51).

**Generation:** Random selection of 6 characters from the alphabet. On collision (the generated code already exists in the database), regenerate and retry. At low fill levels, collision probability per attempt is negligible; a maximum retry limit (e.g. 10 attempts) is enforced, after which the request fails with a 503.

## Consequences

- Short URLs are case-sensitive in the path: `breviare.sh/aBc-DeF` and `breviare.sh/abc-def` are different links. The backend must preserve case in lookups.
- The display dash must be stripped before any database lookup; the database stores the 6-character code without the dash.
- Vanity links (`breviare.sh/<username>`) use the username as the path segment directly and are not subject to the Base52 encoding scheme. See [features/vanity-links.md](../features/vanity-links.md).
- If the key space becomes a concern in the future, the code length can be increased from 6 to 7 characters (52^7 ≈ 1 trillion combinations) without changing the alphabet or display format convention.
