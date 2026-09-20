# Docker Deployment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a one-command, persistent FastAPI deployment compatible with legacy `docker-compose` v1 and document startup and update operations.

**Architecture:** A Python 3.11 image runs Uvicorn on port 3000. Compose file format 3.3 publishes port 13000, stores SQLite in a named volume, performs an HTTP health check, and restarts the service unless explicitly stopped.

**Tech Stack:** Docker, Compose file format 3.3, Python 3.11, FastAPI, Uvicorn, SQLite

---

## File Structure

- Create `Dockerfile`: reproducible FastAPI runtime image.
- Create `.dockerignore`: restrict build context to relevant source files.
- Create `docker-compose.yml`: legacy-compatible service, port, health, and volume configuration.
- Create `docs/docker-guide.md`: deployment and routine operations.
- Modify `docs/backend-guide.md`: link to the Docker-specific guide.

### Task 1: Container Image

**Files:**
- Create: `Dockerfile`
- Create: `.dockerignore`

- [ ] **Step 1: Add a Dockerfile using Python 3.11**

Use `python:3.11-slim`, set `/app` as the working directory, install
`backend/requirements.txt`, copy `backend/`, expose port 3000, and start:

```dockerfile
CMD ["python", "-m", "uvicorn", "backend.main:app", "--host", "0.0.0.0", "--port", "3000"]
```

- [ ] **Step 2: Add build-context exclusions**

Exclude `.git`, `.idea`, `.gradle`, `.venv*`, `build`, `app/build`, local
database files, caches, logs, and Android artifacts in `.dockerignore`.

- [ ] **Step 3: Build the image**

Run: `docker build -t netcheck-backend:test .`

Expected: build succeeds and the image uses Python 3.11.

### Task 2: Legacy-Compatible Compose Service

**Files:**
- Create: `docker-compose.yml`

- [ ] **Step 1: Add Compose file format 3.3 configuration**

Define service `backend` with `build: .`, image name `netcheck-backend:latest`,
`13000:3000` port mapping, `SQLITE_DB_PATH=/data/diagnostic.db`,
`netcheck_data:/data`, `restart: unless-stopped`, and an HTTP health check that
uses Python's standard library so no curl package is required.

- [ ] **Step 2: Parse the Compose configuration**

Run: `docker-compose config`

Expected: exit code 0 and rendered service, volume, port, environment, and
health-check settings. If only Compose v2 is installed, run
`docker compose config` as an additional compatibility check.

- [ ] **Step 3: Start and verify the service**

Run:

```bash
docker-compose up -d --build
docker-compose ps
curl http://127.0.0.1:13000/health
```

Expected: service becomes healthy and the endpoint returns
`{"status":"healthy","service":"netcheck-backend"}`.

### Task 3: Operations Documentation

**Files:**
- Create: `docs/docker-guide.md`
- Modify: `docs/backend-guide.md`

- [ ] **Step 1: Write the Docker operations guide**

Document requirements and these legacy Compose commands:

```bash
docker-compose up -d --build
docker-compose ps
docker-compose logs -f backend
docker-compose restart backend
docker-compose stop
docker-compose down
git pull
docker-compose up -d --build
```

Explain that `down` preserves the named volume while `down -v` deletes the
database. Include health verification, port customization, data inspection,
backup, and common troubleshooting.

- [ ] **Step 2: Link the general backend guide**

Add a Docker deployment section to `docs/backend-guide.md` linking to
`docker-guide.md` and showing the one-command startup.

- [ ] **Step 3: Validate documentation and changes**

Run:

```bash
git diff --check
rg -n "docker-compose up -d --build|13000|netcheck_data" Dockerfile docker-compose.yml docs
```

Expected: no whitespace errors and all deployment concepts are documented.

### Task 4: Final Verification

**Files:**
- Verify: `Dockerfile`
- Verify: `.dockerignore`
- Verify: `docker-compose.yml`
- Verify: `docs/docker-guide.md`
- Verify: `docs/backend-guide.md`

- [ ] **Step 1: Rebuild without stale containers**

Run: `docker-compose up -d --build`

Expected: the backend is recreated successfully without deleting
`netcheck_data`.

- [ ] **Step 2: Check runtime health and persistence configuration**

Run:

```bash
docker-compose ps
curl --fail http://127.0.0.1:13000/health
docker-compose exec -T backend python -c "import os; print(os.environ['SQLITE_DB_PATH'])"
```

Expected: the service is healthy, the HTTP request succeeds, and the printed
path is `/data/diagnostic.db`.
