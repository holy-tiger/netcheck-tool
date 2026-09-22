import ipaddress
import json
import os
from datetime import datetime, timezone
from pathlib import Path

from fastapi import APIRouter, File, HTTPException, Request, UploadFile


router = APIRouter(prefix="/api/files", tags=["files"])


def _utc_now() -> datetime:
    return datetime.now(timezone.utc)


def resolve_source_ip(request: Request) -> str:
    forwarded_for = request.headers.get("X-Forwarded-For", "")
    candidates = []
    if forwarded_for:
        candidates.append(forwarded_for.split(",", 1)[0].strip())
    real_ip = request.headers.get("X-Real-IP")
    if real_ip:
        candidates.append(real_ip.strip())
    if request.client:
        candidates.append(request.client.host)

    for candidate in candidates:
        try:
            return str(ipaddress.ip_address(candidate))
        except ValueError:
            continue
    raise HTTPException(status_code=400, detail="Unable to determine source IP")


def request_metadata(request: Request) -> dict[str, str | None]:
    return {
        "HTTP_CLIENT_IP": request.headers.get("Client-IP"),
        "HTTP_TRUE_CLIENT_IP": request.headers.get("True-Client-IP"),
        "HTTP_X_FORWARDED_FOR": request.headers.get("X-Forwarded-For"),
        "REMOTE_ADDR": request.client.host if request.client else None,
    }


def enrich_content(
    extension: str,
    content: bytes,
    metadata: dict[str, str | None],
) -> bytes:
    if extension.lower() == ".json":
        try:
            payload = json.loads(content.decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError) as exc:
            raise HTTPException(
                status_code=400,
                detail="JSON upload must contain a UTF-8 object",
            ) from exc
        if not isinstance(payload, dict):
            raise HTTPException(
                status_code=400,
                detail="JSON upload must contain a UTF-8 object",
            )
        payload.update(metadata)
        return json.dumps(
            payload,
            ensure_ascii=False,
            separators=(",", ":"),
        ).encode("utf-8")

    prefix = "".join(
        f"{key}={json.dumps(value, ensure_ascii=False)}\n"
        for key, value in metadata.items()
    ) + "\n"
    return prefix.encode("utf-8") + content


@router.post("/upload")
async def upload_file(request: Request, file: UploadFile = File(...)):
    source_ip = resolve_source_ip(request)

    extension = Path(file.filename or "").suffix
    uploaded_at = _utc_now()
    timestamp = uploaded_at.strftime("%Y%m%dT%H%M%S%fZ")
    filename = f"{source_ip.replace(':', '-')}_{timestamp}{extension}"
    target_dir = Path(os.environ.get("DOH_REPORT_DIR", "/data/doh_report"))
    target_dir.mkdir(parents=True, exist_ok=True)
    try:
        contents = await file.read()
        stored_contents = enrich_content(
            extension,
            contents,
            request_metadata(request),
        )
        target_dir.joinpath(filename).write_bytes(stored_contents)
    finally:
        await file.close()
    return {
        "status": "success",
        "filename": filename,
        "size": len(contents),
        "source_ip": source_ip,
        "uploaded_at": uploaded_at.isoformat(),
    }
