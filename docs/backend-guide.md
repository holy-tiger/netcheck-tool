# Backend Installation and Deployment Guide

## 1. Backend Options

This repository provides two backend implementations:

- **FastAPI**: `backend/main.py`, the recommended implementation. It provides
  validation, asynchronous SQLite access, API documentation, and the web
  dashboard.
- **Node.js**: `server.js`, a standalone alternative with the same main API
  routes. It requires Node.js 22.5 or newer because it uses `node:sqlite`.

## 2. Local FastAPI Installation

From the repository root:

```bash
cd /home/ubuntu/workspace/github/netcheck-tool
python3 -m venv .venv
source .venv/bin/activate
pip install -r backend/requirements.txt
```

Start the service:

```bash
python -m uvicorn backend.main:app --host 0.0.0.0 --port 3000
```

The equivalent entry point is `python -m backend.main`. Once running, open:

- `http://localhost:3000/` — dashboard
- `http://localhost:3000/health` — health check
- `http://localhost:3000/docs` — Swagger API documentation

The SQLite schema is initialized automatically. The default database is
`diagnostic.db` in the working directory. Set `SQLITE_DB_PATH` to use another
location:

```bash
SQLITE_DB_PATH=/var/lib/netcheck/diagnostic.db \
  python -m uvicorn backend.main:app --host 0.0.0.0 --port 3000
```

## 3. API Verification

Check service health:

```bash
curl http://localhost:3000/health
```

Generate a diagnostic command:

```bash
curl -X POST http://localhost:3000/api/admin/generate \
  -H 'Content-Type: application/json' \
  -d '{"targets":["google.com","8.8.8.8"],"ping_enabled":true,"dns_enabled":true,"http_enabled":true,"tcp_enabled":true}'
```

List or retrieve reports:

```bash
curl http://localhost:3000/api/reports
curl http://localhost:3000/api/reports/<tracking_id>
```

## 4. Production Deployment

Use a process manager such as systemd. Example unit file:

```ini
[Unit]
Description=NetCheck FastAPI Backend
After=network.target

[Service]
WorkingDirectory=/opt/netcheck-tool
ExecStart=/opt/netcheck-tool/.venv/bin/uvicorn backend.main:app --host 0.0.0.0 --port 3000
Environment=SQLITE_DB_PATH=/var/lib/netcheck/diagnostic.db
Restart=always

[Install]
WantedBy=multi-user.target
```

Create the database directory and enable the service:

```bash
sudo mkdir -p /var/lib/netcheck
sudo systemctl daemon-reload
sudo systemctl enable --now netcheck
sudo systemctl status netcheck
```

Put Nginx or Caddy in front of the service and enable HTTPS. The current API
allows all CORS origins and has no authentication, so it should not be exposed
directly to the public internet without additional protection.

## 5. Android Client Configuration

The Android quick-diagnosis flow currently contains a hard-coded Cloud Run
`report_url` in `MainViewModel.kt`. When using a self-hosted backend, update
that URL to the deployed `/api/reports` endpoint, or generate commands through
`/api/admin/generate` and pass the returned Base64 command to the client.

## 6. Docker Deployment

For a one-command Python 3.11 deployment with persistent SQLite storage, run:

```bash
docker-compose up -d --build
```

The included Compose file supports legacy `docker-compose` v1 clients and
publishes the service on port 13000 by default. See the complete startup,
update, backup, and troubleshooting instructions in
[`docker-guide.md`](docker-guide.md).
