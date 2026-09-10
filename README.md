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

### 3. Run the webapp

From the `webapp/` directory:
```bash
./mvnw jetty:run
```
This starts the app on **http://localhost:8080/**.

Stop the server with `Ctrl+C`. 