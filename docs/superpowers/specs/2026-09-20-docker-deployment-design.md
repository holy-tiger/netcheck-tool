# Docker Deployment Design

## Goal

Provide a one-command deployment for the FastAPI backend that works with the
legacy Python-based `docker-compose` v1 client and documents routine operation
and updates.

## Runtime Design

- Build the service from a root-level `Dockerfile` based on Python 3.11 slim.
- Install `backend/requirements.txt`, copy only application files needed at
  runtime, and run Uvicorn on container port 3000.
- Define one `backend` service in `docker-compose.yml` using Compose file format
  `3.3`, avoiding Compose v2-only features.
- Publish host port 13000 to container port 3000.
- Store SQLite at `/data/diagnostic.db` through the `netcheck_data` named volume.
- Restart unless explicitly stopped and use `/health` for container health.

## Supporting Files and Documentation

A `.dockerignore` will exclude Git data, IDE state, local virtual environments,
Android build output, local databases, and other development-only files from
the image context. `docs/docker-guide.md` will explain prerequisites, initial
startup, status and log inspection, restart/stop/removal, code-and-image
updates, API verification, SQLite backup/restore considerations, and common
troubleshooting. `docs/backend-guide.md` will link readers to the Docker guide.

## Compatibility and Verification

The Compose configuration will use only keys supported by Compose 1.25-era
clients. Verification will include parsing the configuration with whichever of
`docker-compose config` or `docker compose config` is available, building the
image, starting the service when Docker is available, checking `/health`, and
confirming that the database path is backed by the named volume. If a Docker
daemon is unavailable, static configuration checks will be reported instead.
