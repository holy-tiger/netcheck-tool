# DoH Report File Upload Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a bounded multipart upload API that stores safely named DoH report files in `/data/doh_report` and persists that directory on the Docker host.

**Architecture:** A focused `backend/api/files.py` router owns source-IP resolution, filename generation, streaming, and cleanup. FastAPI registers that router, while Compose adds a nested bind mount under the existing `/data` named volume. Tests exercise the HTTP contract through `TestClient` with an isolated temporary upload directory.

**Tech Stack:** Python 3.11, FastAPI, Starlette `TestClient`, `python-multipart`, Python `unittest`, Docker Compose 3.3

---

## File Map

- Create `backend/api/files.py`: upload route, IP resolution, filename generation, bounded streaming, and cleanup.
- Create `backend/tests/__init__.py` and `backend/tests/test_file_upload.py`: HTTP and helper tests.
- Create `backend/requirements-dev.txt`: backend test dependencies.
- Modify `backend/main.py` and `backend/requirements.txt`: register the route and multipart parser.
- Modify `docker-compose.yml`: bind the required host directory.
- Modify `docs/docker-guide.md`: document setup, verification, and proxy-header trust.

### Task 1: Add the Multipart Endpoint Happy Path

**Files:**
- Create: `backend/tests/__init__.py`
- Create: `backend/tests/test_file_upload.py`
- Create: `backend/requirements-dev.txt`
- Create: `backend/api/files.py`
- Modify: `backend/requirements.txt`
- Modify: `backend/main.py:1-34`

- [ ] **Step 1: Prepare test dependencies**

Create an empty `backend/tests/__init__.py` and create `backend/requirements-dev.txt`:

```text
-r requirements.txt
httpx>=0.24.0
```

Run:

```bash
python3 -m venv /tmp/netcheck-backend-venv
/tmp/netcheck-backend-venv/bin/pip install -r backend/requirements-dev.txt
```

Expected: dependencies install successfully.

- [ ] **Step 2: Write the first failing HTTP test**

Create `backend/tests/test_file_upload.py`:

```python
import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from fastapi.testclient import TestClient

from backend.main import app


class FileUploadApiTest(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp_dir.cleanup)
        self.env_patch = patch.dict(os.environ, {"DOH_REPORT_DIR": self.temp_dir.name})
        self.env_patch.start()
        self.addCleanup(self.env_patch.stop)
        self.client = TestClient(app, raise_server_exceptions=False)
        self.addCleanup(self.client.close)

    def test_upload_saves_content_under_generated_name(self):
        response = self.client.post(
            "/api/files/upload",
            files={"file": ("report.JSON", b'{"ok":true}', "application/json")},
            headers={
                "X-Forwarded-For": "198.51.100.24, 10.0.0.4",
                "X-Real-IP": "192.0.2.99",
            },
        )

        self.assertEqual(200, response.status_code)
        body = response.json()
        self.assertEqual("success", body["status"])
        self.assertEqual("198.51.100.24", body["source_ip"])
        self.assertEqual(11, body["size"])
        self.assertRegex(body["filename"], r"^198\.51\.100\.24_\d{8}T\d{12}Z\.JSON$")
        self.assertRegex(body["uploaded_at"], r"^\d{4}-\d{2}-\d{2}T")
        self.assertEqual(
            b'{"ok":true}',
            Path(self.temp_dir.name, body["filename"]).read_bytes(),
        )

    def test_empty_supported_file_is_accepted(self):
        response = self.client.post(
            "/api/files/upload",
            files={"file": ("empty.txt", b"", "text/plain")},
            headers={"X-Forwarded-For": "198.51.100.25"},
        )
        self.assertEqual(200, response.status_code)
        self.assertEqual(0, response.json()["size"])

    def test_missing_file_is_rejected_by_request_validation(self):
        response = self.client.post(
            "/api/files/upload",
            headers={"X-Forwarded-For": "198.51.100.25"},
        )
        self.assertEqual(422, response.status_code)
```

- [ ] **Step 3: Verify the test fails for the missing route**

Run:

```bash
/tmp/netcheck-backend-venv/bin/python -m unittest backend.tests.test_file_upload -v
```

Expected: all three tests FAIL because the response is HTTP 404 rather than
the expected 200 or 422.

- [ ] **Step 4: Implement the minimal route**

Append to `backend/requirements.txt`:

```text
python-multipart>=0.0.6
```

Re-run `/tmp/netcheck-backend-venv/bin/pip install -r backend/requirements-dev.txt`.

Create `backend/api/files.py`:

```python
import ipaddress
import os
from datetime import datetime, timezone
from pathlib import Path

from fastapi import APIRouter, File, HTTPException, Request, UploadFile

router = APIRouter(prefix="/api/files", tags=["files"])


def _utc_now() -> datetime:
    return datetime.now(timezone.utc)


@router.post("/upload")
async def upload_file(request: Request, file: UploadFile = File(...)):
    candidate = request.headers.get("X-Forwarded-For", "").split(",", 1)[0].strip()
    try:
        source_ip = str(ipaddress.ip_address(candidate))
    except ValueError as exc:
        raise HTTPException(status_code=400, detail="Unable to determine source IP") from exc
    extension = Path(file.filename or "").suffix
    uploaded_at = _utc_now()
    timestamp = uploaded_at.strftime("%Y%m%dT%H%M%S%fZ")
    filename = f"{source_ip.replace(':', '-')}_{timestamp}{extension}"
    target_dir = Path(os.environ.get("DOH_REPORT_DIR", "/data/doh_report"))
    target_dir.mkdir(parents=True, exist_ok=True)
    contents = await file.read()
    target_dir.joinpath(filename).write_bytes(contents)
    await file.close()
    return {
        "status": "success",
        "filename": filename,
        "size": len(contents),
        "source_ip": source_ip,
        "uploaded_at": uploaded_at.isoformat(),
    }
```

Import `files_router` in `backend/main.py` and register it:

```python
from .api.files import router as files_router

app.include_router(files_router)
```

- [ ] **Step 5: Verify green and commit**

Run the whole `backend.tests.test_file_upload` module. Expected: all three
tests PASS.

```bash
git add backend/api/files.py backend/main.py backend/requirements.txt backend/requirements-dev.txt backend/tests
git commit -m "feat: add DoH report file upload endpoint"
```

### Task 2: Resolve Forwarded, Real, and TCP Source Addresses

**Files:**
- Modify: `backend/tests/test_file_upload.py`
- Modify: `backend/api/files.py`

- [ ] **Step 1: Add failing source-IP tests**

Import `HTTPException`, `Request`, and `resolve_source_ip`, then add:

```python
def make_request(headers=(), client=("203.0.113.8", 43123)):
    scope = {
        "type": "http", "method": "POST", "path": "/api/files/upload",
        "headers": [(key.lower().encode(), value.encode()) for key, value in headers],
        "client": client, "server": ("testserver", 80), "scheme": "http",
        "query_string": b"",
    }
    return Request(scope)


class SourceIpTest(unittest.TestCase):
    def test_invalid_forwarded_for_falls_back_to_real_ip(self):
        request = make_request((("X-Forwarded-For", "invalid"), ("X-Real-IP", "192.0.2.9")))
        self.assertEqual("192.0.2.9", resolve_source_ip(request))

    def test_missing_proxy_headers_fall_back_to_tcp_peer(self):
        self.assertEqual("203.0.113.8", resolve_source_ip(make_request()))

    def test_no_valid_address_is_rejected(self):
        request = make_request((("X-Real-IP", "invalid"),), client=("also-invalid", 1))
        with self.assertRaises(HTTPException) as context:
            resolve_source_ip(request)
        self.assertEqual(400, context.exception.status_code)
```

Add this method to `FileUploadApiTest`:

```python
    def test_ipv6_and_original_path_are_sanitized(self):
        response = self.client.post(
            "/api/files/upload",
            files={"file": ("../../private/report.log", b"dns output", "text/plain")},
            headers={"X-Forwarded-For": "2001:db8::7"},
        )
        self.assertEqual(200, response.status_code)
        body = response.json()
        self.assertRegex(body["filename"], r"^2001-db8--7_\d{8}T\d{12}Z\.log$")
        self.assertNotIn("private", body["filename"])
```

- [ ] **Step 2: Verify red**

Run the test module. Expected: import ERROR because `resolve_source_ip` is absent. Add a temporary stub raising `NotImplementedError`, rerun, and confirm the three resolver tests FAIL for missing fallback behavior.

- [ ] **Step 3: Implement resolution and use it from the route**

```python
def resolve_source_ip(request: Request) -> str:
    forwarded_for = request.headers.get("X-Forwarded-For", "")
    candidates = []
    if forwarded_for:
        candidates.append(forwarded_for.split(",", 1)[0].strip())
    if real_ip := request.headers.get("X-Real-IP"):
        candidates.append(real_ip.strip())
    if request.client:
        candidates.append(request.client.host)
    for candidate in candidates:
        try:
            return str(ipaddress.ip_address(candidate))
        except ValueError:
            continue
    raise HTTPException(status_code=400, detail="Unable to determine source IP")
```

Replace inline parsing with `source_ip = resolve_source_ip(request)`.

- [ ] **Step 4: Verify green and commit**

Run `/tmp/netcheck-backend-venv/bin/python -m unittest backend.tests.test_file_upload -v`. Expected: all tests PASS.

```bash
git add backend/api/files.py backend/tests/test_file_upload.py
git commit -m "feat: derive upload names from report source IP"
```

### Task 3: Enforce Limits and Atomic Cleanup

**Files:**
- Modify: `backend/tests/test_file_upload.py`
- Modify: `backend/api/files.py`

- [ ] **Step 1: Add failing validation and failure-path tests**

Add tests that assert:

```python
    def test_unsupported_extension_is_rejected(self):
        response = self.client.post(
            "/api/files/upload", files={"file": ("report.zip", b"x")},
            headers={"X-Real-IP": "192.0.2.10"},
        )
        self.assertEqual(400, response.status_code)
        self.assertEqual([], list(Path(self.temp_dir.name).iterdir()))

    def test_oversized_file_leaves_no_partial_file(self):
        response = self.client.post(
            "/api/files/upload",
            files={"file": ("large.log", b"x" * (20 * 1024 * 1024 + 1))},
            headers={"X-Real-IP": "192.0.2.11"},
        )
        self.assertEqual(413, response.status_code)
        self.assertEqual([], list(Path(self.temp_dir.name).iterdir()))

    def test_storage_failure_is_sanitized(self):
        blocker = Path(self.temp_dir.name, "blocker")
        blocker.write_text("file", encoding="utf-8")
        with patch.dict(os.environ, {"DOH_REPORT_DIR": str(blocker)}):
            response = self.client.post(
                "/api/files/upload", files={"file": ("report.txt", b"x")},
                headers={"X-Real-IP": "192.0.2.12"},
            )
        self.assertEqual(500, response.status_code)
        self.assertEqual("Unable to store uploaded file", response.json()["detail"])
        self.assertNotIn(str(blocker), response.text)
```

Import `datetime` and `timezone`, then add the collision test:

```python
    def test_timestamp_collision_does_not_overwrite_existing_file(self):
        first = datetime(2026, 9, 22, 8, 15, 30, 123456, tzinfo=timezone.utc)
        second = datetime(2026, 9, 22, 8, 15, 30, 123457, tzinfo=timezone.utc)
        existing = Path(
            self.temp_dir.name,
            "192.0.2.13_20260922T081530123456Z.txt",
        )
        existing.write_bytes(b"existing")

        with patch("backend.api.files._utc_now", side_effect=[first, second]):
            response = self.client.post(
                "/api/files/upload",
                files={"file": ("report.txt", b"new")},
                headers={"X-Real-IP": "192.0.2.13"},
            )

        self.assertEqual(200, response.status_code)
        self.assertEqual(b"existing", existing.read_bytes())
        self.assertEqual(
            "192.0.2.13_20260922T081530123457Z.txt",
            response.json()["filename"],
        )
        self.assertEqual(
            b"new",
            Path(self.temp_dir.name, response.json()["filename"]).read_bytes(),
        )
```

- [ ] **Step 2: Verify red**

Run:

```bash
/tmp/netcheck-backend-venv/bin/python -m unittest backend.tests.test_file_upload -v
```

Expected: unsupported and oversized files are accepted, collisions overwrite,
and storage failure is not sanitized.

- [ ] **Step 3: Implement bounded atomic storage**

Add constants:

```python
ALLOWED_EXTENSIONS = {".json", ".txt", ".log"}
MAX_UPLOAD_BYTES = 20 * 1024 * 1024
CHUNK_SIZE = 1024 * 1024
```

Replace the route body after resolving the IP with this implementation:

```python
    extension = Path(file.filename or "").suffix
    if extension.lower() not in ALLOWED_EXTENSIONS:
        await file.close()
        raise HTTPException(status_code=400, detail="Unsupported file extension")
    target_dir = Path(os.environ.get("DOH_REPORT_DIR", "/data/doh_report"))
    temporary_path = None
    size = 0
    try:
        target_dir.mkdir(parents=True, exist_ok=True)
        descriptor, name = tempfile.mkstemp(prefix=".upload-", dir=target_dir)
        temporary_path = Path(name)
        with os.fdopen(descriptor, "wb") as destination:
            while chunk := await file.read(CHUNK_SIZE):
                size += len(chunk)
                if size > MAX_UPLOAD_BYTES:
                    raise HTTPException(status_code=413, detail="File exceeds 20 MiB limit")
                destination.write(chunk)
        safe_ip = source_ip.replace(":", "-")
        while True:
            uploaded_at = _utc_now()
            filename = f"{safe_ip}_{uploaded_at.strftime('%Y%m%dT%H%M%S%fZ')}{extension}"
            try:
                os.link(temporary_path, target_dir / filename)
                break
            except FileExistsError:
                continue
        return {"status": "success", "filename": filename, "size": size,
                "source_ip": source_ip, "uploaded_at": uploaded_at.isoformat()}
    except HTTPException:
        raise
    except OSError as exc:
        logger.exception("Failed to store uploaded file")
        raise HTTPException(status_code=500, detail="Unable to store uploaded file") from exc
    finally:
        await file.close()
        if temporary_path is not None:
            temporary_path.unlink(missing_ok=True)
```

Import `logging` and `tempfile`, and define `logger = logging.getLogger(__name__)`.

- [ ] **Step 4: Verify green and commit**

Run:

```bash
/tmp/netcheck-backend-venv/bin/python -m unittest discover -s backend/tests -v
```

Expected: all upload tests PASS, including cleanup and collision preservation.

```bash
git add backend/api/files.py backend/tests/test_file_upload.py
git commit -m "feat: validate and safely persist uploaded reports"
```

### Task 4: Persist Uploads in Docker and Document Deployment

**Files:**
- Modify: `docker-compose.yml:11-15`
- Modify: `docs/docker-guide.md:20-40,97-134`

- [ ] **Step 1: Verify the mount is initially absent**

Run `docker-compose config | rg '/data/doh_report'`. Expected: exit 1.

- [ ] **Step 2: Add the bind mount**

```yaml
    volumes:
      - netcheck_data:/data
      - /data/doh_report:/data/doh_report
```

- [ ] **Step 3: Verify Compose**

Run `docker-compose config | rg -C 3 '/data/doh_report'`. Expected: the backend has host source and container target `/data/doh_report`.

- [ ] **Step 4: Document setup and trust boundaries**

Add before initial startup:

```bash
sudo mkdir -p /data/doh_report
sudo chown 10001:10001 /data/doh_report
docker-compose up -d --build
```

Document that UID `10001` is the non-root container user, SQLite remains on `netcheck_data`, and uploaded files persist on the host. Add an example:

```bash
curl --fail -H 'X-Real-IP: 192.0.2.20' -F 'file=@./example.json' \
  http://127.0.0.1:13000/api/files/upload
ls -lh /data/doh_report
```

State the address priority (`X-Forwarded-For`, `X-Real-IP`, TCP peer) and require the trusted proxy to overwrite client forwarding headers and restrict direct backend access.

- [ ] **Step 5: Verify and commit**

```bash
docker-compose config
rg -n 'doh_report|api/files/upload|X-Forwarded-For|X-Real-IP' docs/docker-guide.md docker-compose.yml
git diff --check
git add docker-compose.yml docs/docker-guide.md
git commit -m "docs: persist uploaded DoH reports in Docker"
```

Expected: all commands exit 0.

### Task 5: Final Verification

**Files:** Verify every file listed in the file map.

- [ ] **Step 1: Run all backend tests**

```bash
/tmp/netcheck-backend-venv/bin/pip install -r backend/requirements-dev.txt
/tmp/netcheck-backend-venv/bin/python -m unittest discover -s backend/tests -v
```

Expected: zero failures and zero errors.

- [ ] **Step 2: Validate and build production deployment**

```bash
docker-compose config
docker build -t netcheck-backend:file-upload-test .
```

Expected: both commands exit 0 and the image installs `python-multipart`.

- [ ] **Step 3: Smoke-test the production image**

Verify that the production-only dependencies can import the app and that the
route is registered:

```bash
docker run --rm --entrypoint python netcheck-backend:file-upload-test -c \
  "from backend.main import app; assert any(r.path == '/api/files/upload' for r in app.routes)"
```

Expected: exit 0 under Python 3.11 without needing the test-only `httpx`
dependency in the production image.

- [ ] **Step 4: Audit the result**

```bash
git diff --check HEAD~3..HEAD
git status --short
```

Expected: no whitespace errors. The pre-existing untracked `docs/app_guide.md` remains untouched.
