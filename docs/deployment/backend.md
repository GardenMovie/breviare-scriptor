# Deployment — Backend (Railway)

The Brevia backend is a Spring Boot (Java 21) application deployed as a containerized service on Railway. The framework decision is recorded in [adr/0002-backend-framework.md](../adr/0002-backend-framework.md).

---

## Runtime

**Java 21** (LTS). Use virtual threads (`spring.threads.virtual.enabled=true`) to keep the redirect hot path non-blocking without explicit async plumbing.

---

## Build

The project uses **Gradle** (Kotlin DSL).

```
./gradlew bootJar
```

This produces a single executable JAR at `build/libs/brevia.jar`.

---

## Railway Configuration

**Railway detection:** Railway can detect Spring Boot via Buildpacks (no Dockerfile required if `gradlew` is present). Alternatively, provide a `Dockerfile` for more control:

```dockerfile
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY build/libs/brevia.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Build command:** `./gradlew bootJar`

**Start command:** `java -jar build/libs/brevia.jar` (or handled by the Dockerfile `ENTRYPOINT`)

**Port:** Railway injects a `PORT` environment variable. Configure Spring Boot to use it:

```
server.port=${PORT:8080}
```

**Health check endpoint:** `GET /health` — returns `200 OK`. Configure in Railway's service settings (check every 30 seconds; restart after 3 consecutive failures).

**Always-on:** The backend must not sleep between requests. Railway's Hobby and Pro plans keep services running continuously. The JVM warm-up cost is paid once at container start, not per request.

**Replicas:** Start with 1 replica. Scale horizontally if the redirect hot path becomes the bottleneck — the app is stateless (no in-process session state).

**Deploy on push:** Railway auto-deploys when the connected git branch (`main`) receives a push. Zero-downtime deploys are achieved via Railway's rolling restart.

---

## Database Connection

Spring Boot uses **HikariCP** for connection pooling by default. Configure via environment variables or `application.properties`:

```
spring.datasource.url=${DATABASE_URL}
spring.datasource.hikari.minimum-idle=2
spring.datasource.hikari.maximum-pool-size=10
spring.datasource.hikari.connection-timeout=5000
spring.datasource.hikari.idle-timeout=30000
```

`DATABASE_URL` must be a JDBC URL:
```
jdbc:postgresql://host:5432/dbname?sslmode=require
```

If the raw value is a standard `postgresql://` URL, convert it to JDBC format in the application startup or use a Spring Boot datasource URL parser.

---

## Migrations

Database migrations are managed with **Flyway**, which runs automatically on application startup before the app accepts traffic.

Migration scripts live in `src/main/resources/db/migration/` and follow the naming convention `V{version}__{description}.sql` (e.g. `V1__create_users.sql`).

Flyway configuration:
```
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration
spring.flyway.baseline-on-migrate=false
```

Breaking schema changes require a multi-step deploy — see [deployment/database.md](./database.md).

---

## Authentication

Spring Security handles JWT validation. The auth flow:

- **Access tokens:** Short-lived JWTs signed with `JWT_SECRET`. Validated on every protected request via a `OncePerRequestFilter`.
- **Refresh tokens:** Longer-lived JWTs set as an httpOnly, Secure, SameSite=Strict cookie (`brevia_refresh`). Stored hash is tracked server-side for invalidation on logout.
- **CORS:** Configured in Spring Security's `CorsConfigurationSource` using the `CORS_ORIGINS` environment variable.

---

## Running Migrations on Deploy

Flyway runs automatically at startup. No separate migration step is needed. The sequence is:

1. Railway builds and starts the new container.
2. Spring Boot initialises → Flyway runs pending migrations.
3. The application starts accepting traffic.

If a migration fails, the application fails to start and Railway keeps the previous version running.

---

## Bundling the GeoIP Database

The MaxMind GeoLite2 country database is a binary file bundled into the build artifact.

- Add the database to `src/main/resources/` (it is not sensitive) or download it during the Gradle build step using the MaxMind license key.
- Set `GEOIP_DB_PATH` to the file's location; or load it from the classpath directly.
- Update the database periodically (MaxMind releases updates monthly).

---

## Private Networking

If the database is also on Railway, use the private network URL for `DATABASE_URL` to avoid public egress and reduce latency. Private URLs are available only within the same Railway project.

---

## Environment Variables

See [deployment/overview.md](./overview.md) for the full manifest. Spring Boot reads all variables from the environment automatically when bound to `@Value` or `@ConfigurationProperties` beans.
