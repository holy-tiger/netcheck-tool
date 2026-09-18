from typing import Any
from pydantic import BaseModel, Field

# 下发给客户端的 JSON (编码前)
class AppCommandSchema(BaseModel):
    report_url: str      # 必填，上报地址
    timeout_ms: int = 20000      # 必填，默认 20000
    tasks: list[dict[str, Any]]    # [{"type": "ping", "targets": ["x"]}]

# 客户端上报的 JSON
class ReportPayloadSchema(BaseModel):
    tracking_id: str
    device_info: dict[str, Any]    # {"os": "...", "model": "..."}
    network_env: dict[str, Any]    # {"type": "WiFi", "local_ip": "..."}
    results: list[dict[str, Any]]  # [{"task": "ping|x", "status": "success", "raw_log": "..."}]

class GenerateCommandRequest(BaseModel):
    report_url: str | None = None
    timeout_ms: int = 20000
    targets: list[str] = Field(default_factory=list)
    ping_enabled: bool = True
    dns_enabled: bool = True
