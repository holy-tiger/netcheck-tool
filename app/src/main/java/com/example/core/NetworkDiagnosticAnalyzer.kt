package com.example.core

data class AnalysisReport(
    val status: String, // "PASS", "WARN", "FAIL"
    val summary: String,
    val issues: List<String>,
    val solutions: List<String>,
    val formattedReport: String
)

object NetworkDiagnosticAnalyzer {

    fun analyze(
        results: List<TaskExecutionResult>,
        language: AppLanguage = AppLanguage.CHINESE
    ): AnalysisReport {
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
        var totalSpeed = 0
        var failedSpeed = 0
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
                task.startsWith("speed|") || task.startsWith("download|") -> {
                    totalSpeed++
                    if (!isSuccess) failedSpeed++
                }
            }
        }

        // 1. 全局断网检查 (Total Internet failure)
        val isTotalInternetDown = totalPing > 0 && failedPing == totalPing && totalHttp > 0 && failedHttp == totalHttp
        if (isTotalInternetDown) {
            when (language) {
                AppLanguage.CHINESE -> {
                    issues.add("🚨 设备当前完全无法连接互联网 (Ping 与 HTTP 均失败)")
                    solutions.add("请检查 Wi-Fi 连接状态或蜂窝移动网络数据开关，尝试重启设备网络或路由器。")
                }
                AppLanguage.ARABIC -> {
                    issues.add("🚨 الجهاز غير قادر على الوصول إلى الإنترنت تماماً (فشل كل من Ping و HTTP)")
                    solutions.add("يرجى التحقق من اتصال Wi-Fi أو بيانات الجوال؛ حاول إعادة تشغيل شبكة الجهاز أو الموجه.")
                }
                else -> {
                    issues.add("🚨 Device is completely unable to access the Internet (both Ping and HTTP failed)")
                    solutions.add("Check Wi-Fi connection or cellular data switch; try restarting device network or router.")
                }
            }
        }

        // 2. DNS 劫持与污染检查 (仅当同时存在 DNS 与 DoH 结果时进行比对)
        var dnsHijackDetected = false
        for ((domain, sysIps) in systemDnsIps) {
            val secureIps = dohIps[domain]
            if (secureIps != null && secureIps.isNotEmpty() && sysIps.isNotEmpty()) {
                val hasOverlap = sysIps.any { secureIps.contains(it) }
                val hasBogon = sysIps.any { it.startsWith("127.") || it == "0.0.0.0" || it.startsWith("198.18.") }
                if (!hasOverlap || hasBogon) {
                    dnsHijackDetected = true
                    when (language) {
                        AppLanguage.CHINESE -> issues.add("⚠️ 域名 '$domain' 疑似遭遇运营商 DNS 劫持/污染 (系统DNS解析: ${sysIps.joinToString()}, DoH安全解析: ${secureIps.joinToString()})")
                        AppLanguage.ARABIC -> issues.add("⚠️ يبدو أن النطاق '$domain' قد تعرض لاختطاف/تلويث DNS (DNS النظام: ${sysIps.joinToString()}، DoH الآمن: ${secureIps.joinToString()})")
                        else -> issues.add("⚠️ Domain '$domain' appears to be hijacked or poisoned (System DNS: ${sysIps.joinToString()}, DoH: ${secureIps.joinToString()})")
                    }
                }
            }
        }

        // 系统 DNS 失败但 DoH 成功
        if (failedDns > 0 && totalDoh > 0 && failedDoh == 0) {
            dnsHijackDetected = true
            when (language) {
                AppLanguage.CHINESE -> issues.add("⚠️ 系统默认 DNS 解析异常失败，但 DoH 加密解析全部正常")
                AppLanguage.ARABIC -> issues.add("⚠️ فشلت دقة DNS الافتراضية للنظام بينما نجحت دقة DoH المشفرة بالكامل")
                else -> issues.add("⚠️ System default DNS resolution failed, while secure DoH resolution succeeded")
            }
        }

        if (dnsHijackDetected) {
            when (language) {
                AppLanguage.CHINESE -> solutions.add("建议在系统设置中启用「私人 DNS (DoH/DoT)」(例如填入 dns.google 或 1dot1dot1dot1.cloudflare-dns.com)；企业客户端可在应用层集成 DoH 加密解析。")
                AppLanguage.ARABIC -> solutions.add("يوصى بتفعيل 'DNS الخاص (DoH/DoT)' في إعدادات النظام، أو دمج دقة DoH على مستوى التطبيق.")
                else -> solutions.add("It is recommended to enable 'Private DNS (DoH/DoT)' in system settings, or integrate DoH resolution at the application layer.")
            }
        }

        // 3. DoH 服务被阻断检查
        if (totalDoh > 0 && failedDoh == totalDoh && totalHttp > 0 && failedHttp < totalHttp) {
            when (language) {
                AppLanguage.CHINESE -> {
                    issues.add("⚠️ 探测到的 DoH 服务器被防火墙或运营商阻断 (443端口拦截或SNI阻断)")
                    solutions.add("建议切换至国内合规公共 DoH 节点 (如阿里 DNS: https://223.5.5.5/dns-query 或腾讯 DNSPod: https://doh.pub/dns-query)，或启用纯 IP 直连模式。")
                }
                AppLanguage.ARABIC -> {
                    issues.add("⚠️ تم حظر خادم DoH المحدد بواسطة جدار الحماية أو مزود الخدمة")
                    solutions.add("يوصى بالتبديل إلى خوادم DoH عامة متوافقة أو استخدام الاتصال المباشر عبر IP.")
                }
                else -> {
                    issues.add("⚠️ Configured DoH server connection blocked by firewall or ISP (Port 443 or SNI block)")
                    solutions.add("Switch to compliant public DoH nodes or use direct IP connections.")
                }
            }
        }

        // 4. 传输层端口防火墙阻断检查 (Ping 通但 TCP 端口超时)
        if (totalPing > 0 && failedPing == 0 && totalTcp > 0 && failedTcp > 0) {
            when (language) {
                AppLanguage.CHINESE -> {
                    issues.add("⚠️ 网络层 ICMP 连通良好，但目标 TCP 端口连接被拒绝或超时，可能存在防火墙拦截")
                    solutions.add("请联系网络管理员排查局域网防火墙出站策略，或确认云服务器安全组对应端口是否开放。")
                }
                AppLanguage.ARABIC -> {
                    issues.add("⚠️ طبقة ICMP متصلة، ولكن تم رفض اتصال منفذ TCP أو انتهت المهلة")
                    solutions.add("يرجى مراجعة مسؤول الشبكة للتحقق من سياسات جدار الحماية أو قواعد مجموعة الأمان.")
                }
                else -> {
                    issues.add("⚠️ ICMP layer is reachable, but target TCP port connection was refused or timed out")
                    solutions.add("Contact network administrator to inspect firewall outbound policies or verify security group rules.")
                }
            }
        }

        // 5. 代理测试分析 (原生 vs WebView)
        if (totalProxy > 0 && failedProxy == totalProxy) {
            when (language) {
                AppLanguage.CHINESE -> {
                    issues.add("⚠️ 内置代理测试失败：无法通过指定代理或映射建立连接")
                    solutions.add("请确认内置代理端口是否被其他进程占用、目标代理服务器上游服务是否正常运行，或检查 hosts 静态映射配置是否正确。")
                }
                AppLanguage.ARABIC -> {
                    issues.add("⚠️ فشل اختبار الوكيل المدمج: تعذر إنشاء اتصال عبر الوكيل المحدد")
                    solutions.add("تأكد من أن منفذ الوكيل غير مشغول وتحقق من تكوين تعيين hosts الثابت.")
                }
                else -> {
                    issues.add("⚠️ Built-in proxy test failed: unable to establish connection through specified proxy")
                    solutions.add("Ensure the proxy port is not occupied and verify the hosts static mapping configuration.")
                }
            }
        } else if (nativeProxySuccess && webviewProxyFailed) {
            when (language) {
                AppLanguage.CHINESE -> {
                    issues.add("⚠️ 原生 HTTP 代理访问成功，但 Android WebView 页面加载失败")
                    solutions.add("请排查目标网站的 SSL 中间证书链完整性，并检查 AndroidManifest 是否允许明文流量 (usesCleartextTraffic) 或尝试清理 WebView 缓存。")
                }
                AppLanguage.ARABIC -> {
                    issues.add("⚠️ نجح الوصول عبر وكيل HTTP الأصلي، لكن فشل تحميل صفحة WebView")
                    solutions.add("يرجى فحص شهادة SSL والتحقق من أذونات المرور النصي الواضح في Android.")
                }
                else -> {
                    issues.add("⚠️ Native HTTP proxy access succeeded, but Android WebView page load failed")
                    solutions.add("Inspect SSL intermediate certificate chain and ensure cleartext traffic permissions in AndroidManifest.")
                }
            }
        }

        // 6. 针对单一协议失败补充具体问题与排障建议 (避免漏报问题或错报原因)
        if (!isTotalInternetDown) {
            // Ping 局部丢包/失败
            if (failedPing > 0 && issues.none { it.contains("Ping") }) {
                when (language) {
                    AppLanguage.CHINESE -> {
                        issues.add("⚠️ 目标主机 Ping 探测存在超时或丢包 ($failedPing/$totalPing 失败)")
                        solutions.add("检查局域网连接质量，或确认目标主机是否禁用了 ICMP 回显响应。")
                    }
                    AppLanguage.ARABIC -> {
                        issues.add("⚠️ واجه فحص Ping للمضيف الهدف فقداناً للحزم أو انتهاء المهلة ($failedPing/$totalPing فشل)")
                        solutions.add("تحقق من جودة اتصال الشبكة المحلية أو ما إذا كان الخادم يعطل استجابة ICMP.")
                    }
                    else -> {
                        issues.add("⚠️ Target host Ping probe experienced packet loss or timeout ($failedPing/$totalPing failed)")
                        solutions.add("Check local network stability or verify whether the target host disables ICMP echo responses.")
                    }
                }
            }

            // 常规 DNS 局部失败 (且未被判定为 DoH 对比劫持)
            if (failedDns > 0 && !dnsHijackDetected && issues.none { it.contains("DNS") }) {
                when (language) {
                    AppLanguage.CHINESE -> {
                        issues.add("⚠️ 部分域名系统 DNS 解析失败 ($failedDns/$totalDns 失败)")
                        solutions.add("请检查域名拼写是否正确，或尝试更换为公共 DNS (如 223.5.5.5 或 8.8.8.8)。")
                    }
                    AppLanguage.ARABIC -> {
                        issues.add("⚠️ فشل دقة DNS لبعض النطاقات ($failedDns/$totalDns فشل)")
                        solutions.add("تحقق من صحة كتابة النطاق أو جرب التبديل إلى خادم DNS عام (مثل 8.8.8.8 أو 1.1.1.1).")
                    }
                    else -> {
                        issues.add("⚠️ Domain DNS resolution failed ($failedDns/$totalDns failed)")
                        solutions.add("Verify the domain name spelling or try switching to public DNS (e.g., 8.8.8.8 or 1.1.1.1).")
                    }
                }
            }

            // HTTP 网页请求失败
            if (failedHttp > 0 && issues.none { it.contains("HTTP") }) {
                when (language) {
                    AppLanguage.CHINESE -> {
                        issues.add("⚠️ 目标 HTTP/HTTPS 网页或接口请求失败 ($failedHttp/$totalHttp 失败)")
                        solutions.add("请检查目标服务器服务状态、SSL/TLS 证书有效性及网络反向代理配置。")
                    }
                    AppLanguage.ARABIC -> {
                        issues.add("⚠️ فشل طلب ويب أو واجهة برمجة تطبيقات HTTP/HTTPS الهدف ($failedHttp/$totalHttp فشل)")
                        solutions.add("يرجى التحقق من رمز حالة HTTP وصلاحية شهادة SSL/TLS وإعدادات الوكيل العكسي.")
                    }
                    else -> {
                        issues.add("⚠️ Target HTTP/HTTPS web or API request failed ($failedHttp/$totalHttp failed)")
                        solutions.add("Check target HTTP status code, SSL/TLS certificate validity, and reverse proxy settings.")
                    }
                }
            }

            // TCP 端口失败 (未被前述 ICMP对比 覆盖时)
            if (failedTcp > 0 && issues.none { it.contains("TCP") }) {
                when (language) {
                    AppLanguage.CHINESE -> {
                        issues.add("⚠️ 目标 TCP 端口连接失败 ($failedTcp/$totalTcp 失败)")
                        solutions.add("请确认目标端口服务是否正在监听，或确认云服务器安全组策略。")
                    }
                    AppLanguage.ARABIC -> {
                        issues.add("⚠️ فشل اتصال منفذ TCP الهدف ($failedTcp/$totalTcp فشل)")
                        solutions.add("يرجى التأكد من تشغيل الخدمة على المنفذ المطلوب والتحقق من قواعد الأمان.")
                    }
                    else -> {
                        issues.add("⚠️ Target TCP port connection failed ($failedTcp/$totalTcp failed)")
                        solutions.add("Ensure the service is actively listening on the target port and check security group rules.")
                    }
                }
            }
        }

        // 7. 状态定级
        val status = when {
            issues.any { it.startsWith("🚨") } -> "FAIL"
            issues.isNotEmpty() -> "WARN"
            else -> "PASS"
        }

        // 8. 收集实际测试过的项目，生成精准动态摘要 (绝不凭空捏造未测试的技术点)
        val testedItemNames = mutableListOf<String>()
        when (language) {
            AppLanguage.CHINESE -> {
                if (totalPing > 0) testedItemNames.add("Ping 连通性")
                if (totalDns > 0) testedItemNames.add("DNS 域名解析")
                if (totalDoh > 0) testedItemNames.add("DoH 安全解析")
                if (totalTcp > 0) testedItemNames.add("TCP 端口探测")
                if (totalHttp > 0) testedItemNames.add("HTTP 网页服务")
                if (totalProxy > 0) testedItemNames.add("内置代理")
                if (totalSpeed > 0) testedItemNames.add("网速测速")
            }
            AppLanguage.ARABIC -> {
                if (totalPing > 0) testedItemNames.add("اتصال Ping")
                if (totalDns > 0) testedItemNames.add("دقة DNS")
                if (totalDoh > 0) testedItemNames.add("دقة DoH الآمنة")
                if (totalTcp > 0) testedItemNames.add("فحص منفذ TCP")
                if (totalHttp > 0) testedItemNames.add("خدمة HTTP")
                if (totalProxy > 0) testedItemNames.add("الوكيل المدمج")
                if (totalSpeed > 0) testedItemNames.add("اختبار السرعة")
            }
            else -> {
                if (totalPing > 0) testedItemNames.add("Ping connectivity")
                if (totalDns > 0) testedItemNames.add("DNS resolution")
                if (totalDoh > 0) testedItemNames.add("DoH secure resolution")
                if (totalTcp > 0) testedItemNames.add("TCP port probe")
                if (totalHttp > 0) testedItemNames.add("HTTP service")
                if (totalProxy > 0) testedItemNames.add("Built-in proxy")
                if (totalSpeed > 0) testedItemNames.add("Download speed test")
            }
        }

        val summary = when (status) {
            "PASS" -> {
                if (testedItemNames.isEmpty()) {
                    when (language) {
                        AppLanguage.CHINESE -> "未执行任何探测任务。"
                        AppLanguage.ARABIC -> "لم يتم تنفيذ أي مهام فحص."
                        else -> "No diagnostic probe tasks were executed."
                    }
                } else {
                    when (language) {
                        AppLanguage.CHINESE -> "所有测试项目 (${testedItemNames.joinToString("、")}) 全部正常通过，网络链路状态良好。"
                        AppLanguage.ARABIC -> "نجحت جميع الاختبارات المحددة (${testedItemNames.joinToString("، ")}) بالكامل؛ حالة اتصال الشبكة ممتازة."
                        else -> "All tested items (${testedItemNames.joinToString(", ")}) passed successfully; network connectivity is healthy."
                    }
                }
            }
            "WARN" -> {
                when (language) {
                    AppLanguage.CHINESE -> "检测到局部网络异常，已生成具体的排障与修复建议。"
                    AppLanguage.ARABIC -> "تم اكتشاف خلل جزئي في الشبكة؛ تم إنشاء توصيات مستهدفة لاستكشاف الأخطاء وإصلاحها."
                    else -> "Partial network anomalies detected; targeted troubleshooting recommendations have been generated."
                }
            }
            else -> {
                when (language) {
                    AppLanguage.CHINESE -> "网络连接严重受阻，基础链路不可达。"
                    AppLanguage.ARABIC -> "اتصال الشبكة معطل بشدة؛ الروابط الأساسية غير قابلة للوصول."
                    else -> "Network connection is severely obstructed; basic links are unreachable."
                }
            }
        }

        if (solutions.isEmpty()) {
            when (language) {
                AppLanguage.CHINESE -> solutions.add("当前网络各项指标完全正常，无需任何配置调整。")
                AppLanguage.ARABIC -> solutions.add("جميع مقاييس الشبكة تعمل بشكل طبيعي؛ لا حاجة لأي تعديلات.")
                else -> solutions.add("All network metrics are operating normally; no configuration adjustments needed.")
            }
        }

        val sb = StringBuilder()
        when (language) {
            AppLanguage.CHINESE -> {
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
                sb.append("\n--- 💡 推荐解决方案与指引 ---\n")
                solutions.forEachIndexed { idx, sol ->
                    sb.append("  ").append(idx + 1).append(". ").append(sol).append("\n")
                }
            }
            AppLanguage.ARABIC -> {
                sb.append("=========================================\n")
                sb.append(" تقرير تشخيص NetCheck الذكي والحلول المقترحة\n")
                sb.append("=========================================\n\n")
                sb.append("【التقييم العام】: ").append(if (status == "PASS") "✅ ممتاز (NORMAL)" else if (status == "WARN") "⚠️ تحذير (WARNING)" else "❌ خطأ فادح (CRITICAL)").append("\n")
                sb.append("【ملخص النظرة العامة】: ").append(summary).append("\n\n")
                sb.append("--- 🔍 المشكلات المكتشفة (").append(issues.size).append(") ---\n")
                if (issues.isEmpty()) {
                    sb.append("  • لم يتم العثور على أي مشاكل، تم اجتياز جميع الفحوصات\n")
                } else {
                    issues.forEach { sb.append("  • ").append(it).append("\n") }
                }
                sb.append("\n--- 💡 الحلول الموصى بها ---\n")
                solutions.forEachIndexed { idx, sol ->
                    sb.append("  ").append(idx + 1).append(". ").append(sol).append("\n")
                }
            }
            else -> {
                sb.append("=========================================\n")
                sb.append(" NetCheck Smart Diagnostic & Solutions Report\n")
                sb.append("=========================================\n\n")
                sb.append("[Overall Assessment]: ").append(if (status == "PASS") "✅ NORMAL" else if (status == "WARN") "⚠️ WARNING" else "❌ CRITICAL").append("\n")
                sb.append("[Executive Summary]: ").append(summary).append("\n\n")
                sb.append("--- 🔍 Detected Issues (").append(issues.size).append(") ---\n")
                if (issues.isEmpty()) {
                    sb.append("  • No anomalies detected; all protocol probes passed successfully\n")
                } else {
                    issues.forEach { sb.append("  • ").append(it).append("\n") }
                }
                sb.append("\n--- 💡 Recommended Solutions ---\n")
                solutions.forEachIndexed { idx, sol ->
                    sb.append("  ").append(idx + 1).append(". ").append(sol).append("\n")
                }
            }
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
