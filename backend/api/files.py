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
        raise HTTPException(
            status_code=400,
            detail="Unable to determine source IP",
        ) from exc

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
