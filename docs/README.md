# Brevia

A fast, analytics-aware URL shortener. Create short links instantly — no account required. Sign up to track clicks, manage your links, and get a permanent vanity link at `brevia.sh/<username>`.

---

## Features

- **Anonymous link creation** — shorten any URL without an account
- **Base52 short codes** — 6-character all-letter codes displayed as `aBc-DeF` for readability
- **Analytics** — per-link click counts, referrers, countries, and time-series (authenticated users only)
- **Link expiry** — links expire after 30 days of inactivity; authenticated users can set custom expiry
- **Vanity links** — every account gets `brevia.sh/<username>` as a permanent personal redirect
- **302 redirects** — every click passes through the server so analytics are never lost to browser caching

---

## Tech Stack

| Layer | Technology | Host |
|---|---|---|
| Frontend | Next.js or Vite (React) — [ADR](./docs/adr/0001-frontend-framework.md) | Vercel |
| Backend | Spring Boot (Java) — [ADR](./docs/adr/0002-backend-framework.md) | Railway |
| Database | PostgreSQL — [ADR](./docs/adr/0003-database-host.md) | Neon |

---

## Documentation

| Document | Description |
|---|---|
| [Architecture Overview](./docs/architecture/overview.md) | Three-tier structure, routing, auth, CORS |
| [Data Flow](./docs/architecture/data-flow.md) | Runtime flows for link creation, redirects, analytics, expiry |
| [Database Schema](./docs/architecture/database-schema.md) | All tables, columns, indexes |
| [Link Creation](./docs/features/link-creation.md) | Anonymous vs. authenticated creation, URL validation, code generation |
| [Link Expiry](./docs/features/link-expiry.md) | Inactivity and absolute TTL rules |
| [Analytics](./docs/features/analytics.md) | What is tracked and why 302 matters |
| [User Accounts](./docs/features/user-accounts.md) | Registration, username rules, change policies |
| [Vanity Links](./docs/features/vanity-links.md) | Personal redirect links, destination changes, routing priority |
| [API Reference](./docs/api/README.md) | REST API overview, auth, error codes |
| [Deployment Guide](./docs/deployment/overview.md) | Environments, service map, environment variables |
| [ADR Index](./docs/adr/README.md) | Architectural decisions and open questions |

---

## Quick Start

See [docs/deployment/overview.md](./docs/deployment/overview.md) for environment setup and [CONTRIBUTING.md](./CONTRIBUTING.md) for local development instructions.

---

## Contributing

See [CONTRIBUTING.md](./CONTRIBUTING.md).

---

## License

TBD
