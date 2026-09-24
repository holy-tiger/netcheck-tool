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
    http_enabled: bool = True
    tcp_enabled: bool = True
    speed_enabled: bool = False
    speed_url: str | None = None
    # DoH 检测配置
    doh_enabled: bool = False
    doh_servers: list[str] = Field(default_factory=list)
    doh_domains: list[str] = Field(default_factory=list)
    # 内置代理服务器测试配置
    proxy_test_enabled: bool = False
    proxy_urls: list[str] = Field(default_factory=list)
    proxy_modes: list[str] = Field(default_factory=lambda: ["native", "webview"])
    proxy_doh_servers: list[str] = Field(default_factory=list)
    proxy_dns_servers: list[str] = Field(default_factory=list)
    proxy_hosts_mapping: dict[str, str] = Field(default_factory=dict)
    proxy_port: int = 0
