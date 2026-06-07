# Deployment — Database (PostgreSQL)

Brevia uses PostgreSQL. The host is not yet decided; see [adr/0003-database-host.md](../adr/0003-database-host.md). This document covers the setup that applies regardless of host, with host-specific notes at the end.

---

## Connection

The backend connects to the database via the `DATABASE_URL` environment variable. Spring Boot expects a JDBC URL:

```
jdbc:postgresql://host:5432/dbname?sslmode=require
```

`sslmode=require` is mandatory in all environments above local development.

**Connection pooling:** Spring Boot uses **HikariCP** by default. The backend is a long-running process (not serverless), so the built-in pool is appropriate. PgBouncer or an external pooler is not required unless connection counts become a concern at scale.

Recommended pool settings as a starting point:
- Min connections: 2
- Max connections: 10
- Connection timeout: 5 seconds
- Idle timeout: 30 seconds

---

## Required Extensions

Run once after creating the database:

```sql
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "citext";
```

Most hosted Postgres providers include these extensions by default.

---

## Migrations

All schema changes are applied via versioned migrations. The migration tool is TBD; candidates:

**Migration tool: Flyway** (co-located in the Spring Boot project; runs automatically on startup).

Migration scripts live in `src/main/resources/db/migration/` using the naming convention `V{version}__{description}.sql`.

Other tools considered:

| Tool | Notes |
|---|---|
| Flyway ✓ | Java-native; runs on Spring Boot startup; SQL-based |
| Liquibase | Java-native alternative; XML/YAML/SQL formats; more complex |
| golang-migrate | CLI; language-agnostic; requires a separate run step |

**Migration discipline:**
- Migrations are applied in order and are never modified after being committed.
- Breaking schema changes (dropping columns, changing types) require a multi-step deploy: add the new column → deploy → migrate data → drop the old column → deploy.
- Migrations run before the new backend version is activated on deploy (see [deployment/backend.md](./backend.md)).

---

## SSL

All production connections must use TLS. Set `sslmode=require` (or `sslmode=verify-full` with a CA cert for stricter validation) in the connection string.

Local development may use `sslmode=disable` for simplicity.

---

## Backup

Backup strategy depends on the chosen host:

- **Neon:** Point-in-time restore is available on paid plans; free tier has no PITR.
- **Supabase:** Daily backups on free tier; PITR on Pro plan.
- **Railway Postgres:** Manual backups only on free tier; upgrade for automatic backups.

At minimum, set up a scheduled `pg_dump` to an external storage bucket (e.g. S3 or Backblaze B2) before launch.

---

## Host-Specific Notes

### Neon

- Use the `/pooler` connection string variant for all non-migration connections (enables PgBouncer pooling).
- Autosuspend is enabled on the free tier — the first query after a pause will experience a 1–3 second cold start. Disable autosuspend on the production branch once on a paid plan.
- Enable database branching: create a `main` branch for production and a `dev` branch for development. Each PR can use its own branch for preview environments.

### Supabase

- Use the connection pooler URL (port 6543) for application connections; use the direct URL (port 5432) for migrations only.
- The `citext` extension is available by default.
- Do not use Supabase's built-in auth or REST API (PostgREST) — Brevia has its own auth implementation.

### Railway Postgres

- Use the private network URL (`postgresql://...railway.internal/...`) when the backend and database are in the same Railway project. This avoids public egress costs and reduces latency.
- Backups must be configured manually; Railway does not offer automatic backups on the free tier.
- The database add-on shares the Railway project's compute credit. Monitor usage to avoid unexpected charges.
