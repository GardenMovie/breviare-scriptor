# Architecture Decision Records

This directory contains ADRs (Architecture Decision Records) for Brevia. Each ADR documents a significant technical or architectural decision: the context that prompted it, the options considered, and the chosen outcome.

## Status Values

| Status | Meaning |
|---|---|
| **Proposed** | Decision is under consideration; no choice made yet |
| **Accepted** | Decision has been made and is in effect |
| **Superseded** | A later ADR replaced this one (link to successor) |
| **Deprecated** | Decision no longer applies but is kept for history |

## Template

```
# NNNN — Title

**Status:** Proposed | Accepted | Superseded by [NNNN](./NNNN-title.md)
**Date:** YYYY-MM-DD

## Context

Why does this decision need to be made? What forces or constraints are at play?

## Options Considered

### Option A — Name
- Pro: ...
- Con: ...

### Option B — Name
- Pro: ...
- Con: ...

## Decision

[State the chosen option and the primary reason.]

## Consequences

What becomes easier or harder as a result of this decision?
```

## Index

| # | Title | Status |
|---|---|---|
| [0001](./0001-frontend-framework.md) | Frontend Framework | Proposed |
| [0002](./0002-backend-framework.md) | Backend Framework | Accepted |
| [0003](./0003-database-host.md) | Database Host | Proposed |
| [0004](./0004-redirect-strategy.md) | Redirect Strategy (302 vs 301) | Accepted |
| [0005](./0005-encoding-scheme.md) | Short Code Encoding Scheme | Accepted |
