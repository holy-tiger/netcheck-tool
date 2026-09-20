import base64
import json
from fastapi import APIRouter, Request, HTTPException
from ..database import get_db_connection
from ..models import AppCommandSchema, GenerateCommandRequest

router = APIRouter(prefix="/api/admin", tags=["admin"])

@router.post("/generate")
async def generate_command(req: GenerateCommandRequest, request: Request):
    targets = [t.strip() for t in req.targets if t.strip()]
    if not targets:
        raise HTTPException(status_code=400, detail="At least one target host is required")

    # If report_url not specified, build full default report URL from request
    report_url = req.report_url
    if not report_url:
        base_url = str(request.base_url).rstrip("/")
        report_url = f"{base_url}/api/reports"

    tasks = []
    if req.ping_enabled:
        tasks.append({"type": "ping", "targets": targets})
    if req.dns_enabled:
        tasks.append({"type": "dns", "targets": targets})
    if req.http_enabled:
        tasks.append({"type": "http", "targets": targets})
    if req.tcp_enabled:
        tasks.append({"type": "tcp", "targets": targets})
    if req.speed_enabled:
        speed_target = req.speed_url or "https://speed.cloudflare.com/__down?bytes=5000000"
        tasks.append({"type": "speed", "targets": [speed_target]})

    if not tasks:
        tasks.append({"type": "ping", "targets": targets})

    command_obj = AppCommandSchema(
        report_url=report_url,
        timeout_ms=req.timeout_ms or 20000,
        tasks=tasks
    )

    command_json = command_obj.model_dump_json()
    base64_str = base64.b64encode(command_json.encode("utf-8")).decode("utf-8")

    # Update generate_history in SQLite using exact required SQL
    db = await get_db_connection()
    try:
        for host in targets:
            await db.execute(
                """
                INSERT INTO generate_history (target_host, use_count, last_used_at)
                VALUES (?, 1, CURRENT_TIMESTAMP)
                ON CONFLICT(target_host) DO UPDATE SET
                    last_used_at = CURRENT_TIMESTAMP,
                    use_count = use_count + 1
                """,
                (host,)
            )
        await db.commit()
    finally:
        await db.close()

    return {
        "status": "success",
        "base64_command": base64_str,
        "command_json": command_obj.model_dump()
    }

@router.get("/history")
async def get_generate_history(limit: int = 20):
    db = await get_db_connection()
    try:
        cursor = await db.execute(
            """
            SELECT id, target_host, use_count, last_used_at
            FROM generate_history
            ORDER BY last_used_at DESC
            LIMIT ?
            """,
            (limit,)
        )
        rows = await cursor.fetchall()
        return [
            {
                "id": r["id"],
                "target_host": r["target_host"],
                "use_count": r["use_count"],
                "last_used_at": r["last_used_at"]
            }
            for r in rows
        ]
    finally:
        await db.close()
