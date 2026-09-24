package com.example.core

data class AnalysisReport(
    val status: String, // "PASS", "WARN", "FAIL"
    val summary: String,
    val issues: List<String>,
    val solutions: List<String>,
    val formattedReport: String
)

object NetworkDiagnosticAnalyzer {

    fun analyze(results: List<TaskExecutionResult>): AnalysisReport {
        val issues = mutableListOf<String>()
        val solutions = mutableListOf<String>()

        var totalPing = 0
        var failedPing = 0
        var totalDns = 0
        var failedDns = 0
        var totalDoh = 0
        var failedDoh = 0
        var totalTcp = 0
        var failedTcp = 0
        var totalHttp = 0
        var failedHttp = 0
        var totalProxy = 0
        var failedProxy = 0
        var nativeProxySuccess = false
        var webviewProxyFailed = false

        val systemDnsIps = mutableMapOf<String, MutableList<String>>()
        val dohIps = mutableMapOf<String, MutableList<String>>()

        for (res in results) {
            val task = res.task
            val isSuccess = res.status == "success"

            when {
                task.startsWith("ping|") -> {
                    totalPing++
                    if (!isSuccess) failedPing++
                }
                task.startsWith("dns|") -> {
                    totalDns++
                    val domain = task.removePrefix("dns|").trim()
                    if (!isSuccess) {
                        failedDns++
                    } else {
                        // 提取解析到的 IP
                        val ips = extractIpsFromLog(res.raw_log)
                        systemDnsIps.getOrPut(domain) { mutableListOf() }.addAll(ips)
                    }
                }
                task.startsWith("doh|") -> {
                    totalDoh++
                    val parts = task.split("|")
                    val domain = parts.getOrNull(2) ?: parts.getOrNull(1) ?: ""
                    if (!isSuccess) {
                        failedDoh++
                    } else {
                        val ips = extractIpsFromLog(res.raw_log)
                        if (domain.isNotEmpty()) {
                            dohIps.getOrPut(domain) { mutableListOf() }.addAll(ips)
                        }
                    }
                }
                task.startsWith("tcp|") -> {
                    totalTcp++
                    if (!isSuccess) failedTcp++
                }
                task.startsWith("http|") -> {
                    totalHttp++
                    if (!isSuccess) failedHttp++
                }
                task.startsWith("proxy_test|") || task.startsWith("proxy|") -> {
                    totalProxy++
                    if (task.contains("native")) {
                        if (isSuccess) nativeProxySuccess = true
                    }
                    if (task.contains("webview")) {
                        if (!isSuccess) webviewProxyFailed = true
                    }
                    if (!isSuccess) failedProxy++
                }
            }
        }

        // 1. 全局断网检查
        if (totalPing > 0 && failedPing == totalPing && totalHttp > 0 && failedHttp == totalHttp) {
            issues.add("🚨 设备当前完全无法连接互联网 (Ping 与 HTTP 均失败)")
            solutions.add("请检查 Wi-Fi 连接状态或蜂窝移动网络数据开关，尝试重启设备网络或路由器。")
        }

        // 2. DNS 劫持与污染检查 (对比系统 DNS 与 DoH 解析结果)
        var dnsHijackDetected = false
        for ((domain, sysIps) in systemDnsIps) {
            val secureIps = dohIps[domain]
            if (secureIps != null && secureIps.isNotEmpty() && sysIps.isNotEmpty()) {
                val hasOverlap = sysIps.any { secureIps.contains(it) }
                // 检查系统解析是否返回了保留地址/环回地址 (如 127.0.0.1, 0.0.0.0, 198.18.*)
                val hasBogon = sysIps.any { it.startsWith("127.") || it == "0.0.0.0" || it.startsWith("198.18.") }
                if (!hasOverlap || hasBogon) {
                    dnsHijackDetected = true
                    issues.add("⚠️ 域名 '$domain' 疑似遭遇运营商 DNS 劫持/污染 (系统DNS解析: ${sysIps.joinToString()}, DoH安全解析: ${secureIps.joinToString()})")
                }
            }
        }

        // 系统 DNS 失败但 DoH 成功
        if (failedDns > 0 && totalDoh > 0 && failedDoh == 0) {
            dnsHijackDetected = true
            issues.add("⚠️ 系统默认 DNS 解析异常失败，但 DoH 加密解析全部正常")
        }

        if (dnsHijackDetected) {
            solutions.add("建议在系统设置中启用「私人 DNS (DoH/DoT)」(例如填入 dns.google 或 1dot1dot1dot1.cloudflare-dns.com)；企业客户端可在应用层集成 DoH 加密解析。")
        }

        // 3. DoH 服务被阻断检查
        if (totalDoh > 0 && failedDoh == totalDoh && totalHttp > 0 && failedHttp < totalHttp) {
            issues.add("⚠️ 探测到的 DoH 服务器被防火墙或运营商阻断 (443端口拦截或SNI阻断)")
            solutions.add("建议切换至国内合规公共 DoH 节点 (如阿里 DNS: https://223.5.5.5/dns-query 或腾讯 DNSPod: https://doh.pub/dns-query)，或启用纯 IP 直连模式。")
        }

        // 4. 传输层端口防火墙阻断检查 (Ping 通但 TCP 端口超时)
        if (totalPing > 0 && failedPing == 0 && totalTcp > 0 && failedTcp > 0) {
            issues.add("⚠️ 网络层 ICMP 连通良好，但目标 TCP 端口连接被拒绝或超时，可能存在防火墙拦截")
            solutions.add("请联系网络管理员排查局域网防火墙出站策略，或确认云服务器安全组对应端口是否开放。")
        }

        // 5. 代理测试分析 (原生 vs WebView)
        if (totalProxy > 0 && failedProxy == totalProxy) {
            issues.add("⚠️ 内置代理测试失败：无法通过指定代理或映射建立连接")
            solutions.add("请确认内置代理端口是否被其他进程占用、目标代理服务器上游服务是否正常运行，或检查 hosts 静态映射配置是否正确。")
        } else if (nativeProxySuccess && webviewProxyFailed) {
            issues.add("⚠️ 原生 HTTP 代理访问成功，但 Android WebView 页面加载失败")
            solutions.add("请排查目标网站的 SSL 中间证书链完整性，并检查 AndroidManifest 是否允许明文流量 (usesCleartextTraffic) 或尝试清理 WebView 缓存。")
        }

        // 6. 状态定级
        val status = when {
            issues.any { it.startsWith("🚨") } -> "FAIL"
            issues.isNotEmpty() -> "WARN"
            else -> "PASS"
        }

        val summary = when (status) {
            "PASS" -> "全链路网络健康状态极佳：Ping、系统DNS、DoH安全解析与代理隧道均畅通无阻。"
            "WARN" -> "检测到局部网络异常或潜在解析污染风险，已生成优化与修复建议。"
            else -> "网络连接严重受阻，基础链路不可达。"
        }

        if (solutions.isEmpty()) {
            solutions.add("当前网络各项指标完全正常，无需任何配置调整。")
        }

        val sb = StringBuilder()
        sb.append("=========================================\n")
        sb.append("   NetCheck 智能网络诊断与解决方案报告    \n")
        sb.append("=========================================\n\n")
        sb.append("【综合评定】: ").append(if (status == "PASS") "✅ 优秀 (NORMAL)" else if (status == "WARN") "⚠️ 告警 (WARNING)" else "❌ 严重故障 (CRITICAL)").append("\n")
        sb.append("【总览结论】: ").append(summary).append("\n\n")

        sb.append("--- 🔍 检出问题项 (").append(issues.size).append(") ---\n")
        if (issues.isEmpty()) {
            sb.append("  • 未发现任何异常指标，各协议层探测全部通过\n")
        } else {
            issues.forEach { sb.append("  • ").append(it).append("\n") }
        }
        sb.append("\n")

        sb.append("--- 💡 推荐解决方案与指引 ---\n")
        solutions.forEachIndexed { idx, sol ->
            sb.append("  ").append(idx + 1).append(". ").append(sol).append("\n")
        }

        return AnalysisReport(
            status = status,
            summary = summary,
            issues = issues,
            solutions = solutions,
            formattedReport = sb.toString()
        )
    }

    private fun extractIpsFromLog(log: String): List<String> {
        val ips = mutableListOf<String>()
        val ipRegex = Regex("""\b(?:\d{1,3}\.){3}\d{1,3}\b""")
        for (match in ipRegex.findAll(log)) {
            val ip = match.value
            if (!ip.startsWith("127.0.0.") && ip != "0.0.0.0" && !ip.startsWith("255.")) {
                if (!ips.contains(ip)) ips.add(ip)
            }
        }
        return ips
    }
}
