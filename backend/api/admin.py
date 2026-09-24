import base64
import json
from fastapi import APIRouter, Request, HTTPException
from ..database import get_db_connection
from ..models import AppCommandSchema, GenerateCommandRequest

router = APIRouter(prefix="/api/admin", tags=["admin"])

@router.post("/generate")
async def generate_command(req: GenerateCommandRequest, request: Request):
    targets = [t.strip() for t in req.targets if t.strip()]
    doh_domains = [d.strip() for d in req.doh_domains if d.strip()]
    proxy_urls = [u.strip() for u in req.proxy_urls if u.strip()]

    if not targets and not (req.doh_enabled and doh_domains) and not (req.proxy_test_enabled and proxy_urls):
        raise HTTPException(status_code=400, detail="At least one target host, DoH domain, or proxy URL is required")

    # If report_url not specified, build full default report URL from request
    report_url = req.report_url
    if not report_url:
        base_url = str(request.base_url).rstrip("/")
        report_url = f"{base_url}/api/reports"

    tasks = []
    if targets:
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

    # DoH 专项探测任务
    if req.doh_enabled:
        doh_servers = [s.strip() for s in req.doh_servers if s.strip()]
        if not doh_servers:
            doh_servers = ["https://cloudflare-dns.com/dns-query", "https://dns.google/dns-query"]
        if not doh_domains and targets:
            doh_domains = [t for t in targets if not t.replace(".", "").isdigit()]
        if not doh_domains:
            doh_domains = ["google.com", "cloudflare.com"]
        tasks.append({
            "type": "doh",
            "servers": doh_servers,
            "domains": doh_domains
        })

    # 内置代理服务器测试任务
    if req.proxy_test_enabled:
        if not proxy_urls:
            proxy_urls = ["https://example.com"]
        modes = req.proxy_modes if req.proxy_modes else ["native", "webview"]
        doh_servers = [s.strip() for s in req.proxy_doh_servers if s.strip()]
        dns_servers = [s.strip() for s in req.proxy_dns_servers if s.strip()]
        tasks.append({
            "type": "proxy_test",
            "urls": proxy_urls,
            "modes": modes,
            "doh_servers": doh_servers,
            "dns_servers": dns_servers,
            "hosts_mapping": req.proxy_hosts_mapping or {},
            "proxy_port": req.proxy_port or 0
        })

    if not tasks:
        tasks.append({"type": "ping", "targets": targets or ["8.8.8.8"]})

    command_obj = AppCommandSchema(
        report_url=report_url,
        timeout_ms=req.timeout_ms or 20000,
        tasks=tasks
    )

    command_json = command_obj.model_dump_json()
    base64_str = base64.b64encode(command_json.encode("utf-8")).decode("utf-8")

    # Update generate_history in SQLite
    db = await get_db_connection()
    try:
        hosts_to_record = set(targets)
        if req.doh_enabled:
            hosts_to_record.update(doh_domains)
        for host in hosts_to_record:
            if not host:
                continue
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
