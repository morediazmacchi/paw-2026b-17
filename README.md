## Getting Started

### Prerequisites
- Java (JDK) installed
- Maven (`mvn`) installed — or use `./mvnw` if the project includes the wrapper
- Docker (optional, if not using a local PostgreSQL install)
- PostgreSQL 16 (local or via Docker)

### 1. Database Setup

**Option A — Local PostgreSQL install:**
```bash
sudo -u postgres psql -c "CREATE USER paw WITH PASSWORD 'paw';"
sudo -u postgres psql -c "CREATE DATABASE paw2026b OWNER paw;"
```

**Option B — Docker:**
```bash
docker run --name paw-db -e POSTGRES_USER=paw -e POSTGRES_PASSWORD=paw \
  -e POSTGRES_DB=paw2026b -p 5432:5432 -d postgres:16
```
> Note: only one Postgres instance can bind port 5432 at a time. If you have a local Postgres service running, stop it first (`sudo systemctl stop postgresql`) before starting the Docker container, or remap the container's port (e.g. `-p 5433:5432`).

### 2. Build the project

From the project root, install all modules (`model`, `persistence`, `services`, etc.) into your local Maven repo:
```bash
./mvnw clean install -DskipTests
```
Run this **whenever you change code in `model`, `persistence`, or `services`** — the webapp module resolves them from your local repo, not live from source.

### 3. Run the webapp (Jetty)

The Maven wrapper (`mvnw`) lives in the project root only, so run Jetty **from the root** selecting the `webapp` module:
```bash
./mvnw -pl webapp jetty:run
```
This starts the app on **http://localhost:8080/** (no context path).

- Stop the server with `Ctrl+C`.
- Jetty rescans for changes every 10 seconds, but only for the `webapp` module. If you changed `model`, `persistence` or `services`, stop Jetty, re-run step 2 and start it again.
- If port 8080 is busy, stop whatever is using it before starting Jetty.

### 4. Database initialization and migrations

On every boot `WebConfig` runs, in this order, against PostgreSQL:
1. `schema.sql` — must be idempotent (`CREATE TABLE IF NOT EXISTS`, `ADD COLUMN IF NOT EXISTS`).
2. `postgres-migrations.sql` — Postgres-only migrations (e.g. replacing the `registration.status` CHECK constraint). It is not loaded by the tests, because the in-memory HSQLDB doesn't support `DROP CONSTRAINT IF EXISTS`.
3. `data.sql` — seed data.

No manual migration step is needed: an existing database is upgraded automatically when the app starts. The connection settings (`DB_URL`, user and password) are constants in `WebConfig`; the defaults match the `paw` / `paw2026b` setup from step 1.

### 5. Emails

Mail settings live in `services/src/main/resources/mail.properties` (SMTP credentials and `app.base-url`).
`app.base-url` must be the absolute URL of the app (`http://localhost:8080` locally), since it is used to build the links inside emails (registration reviews, private-event invitations, etc.).

### 6. Tests

```bash
./mvnw test
```
Persistence tests use an in-memory HSQLDB in PostgreSQL-compatibility mode, so they don't need a running Postgres.
