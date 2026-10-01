package com.example.ui.expert

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.AppLanguage
import com.example.core.NetworkDiagnosticAnalyzer
import com.example.core.NetworkEngine
import com.example.core.TaskExecutionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ExpertViewModel(application: Application) : AndroidViewModel(application) {

    private val _configState = MutableStateFlow(ExpertConfigState())
    val configState: StateFlow<ExpertConfigState> = _configState.asStateFlow()

    private val _executionState = MutableStateFlow(ExpertExecutionState())
    val executionState: StateFlow<ExpertExecutionState> = _executionState.asStateFlow()

    fun updateTargetHosts(hosts: String) {
        _configState.value = _configState.value.copy(targetHosts = hosts)
    }

    fun addTargetHost(host: String) {
        val current = _configState.value.targetHosts.trim()
        val hosts = current.split(Regex("[\n,]+")).map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
        if (!hosts.contains(host)) {
            hosts.add(host)
            _configState.value = _configState.value.copy(targetHosts = hosts.joinToString(", "))
        }
    }

    fun setPingEnabled(enabled: Boolean) {
        _configState.value = _configState.value.copy(pingEnabled = enabled)
    }

    fun setDnsEnabled(enabled: Boolean) {
        _configState.value = _configState.value.copy(dnsEnabled = enabled)
    }

    fun setHttpEnabled(enabled: Boolean) {
        _configState.value = _configState.value.copy(httpEnabled = enabled)
    }

    fun updateHttpUrls(urls: String) {
        _configState.value = _configState.value.copy(httpUrls = urls)
    }

    fun addHttpUrl(url: String) {
        val current = _configState.value.httpUrls.trim()
        val list = current.split(Regex("[\n,]+")).map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
        if (!list.contains(url)) {
            list.add(url)
            _configState.value = _configState.value.copy(httpUrls = list.joinToString("\n"))
        }
    }

    fun setTcpEnabled(enabled: Boolean) {
        _configState.value = _configState.value.copy(tcpEnabled = enabled)
    }

    fun setDohEnabled(enabled: Boolean) {
        _configState.value = _configState.value.copy(dohEnabled = enabled)
    }

    fun updateDohServers(servers: String) {
        _configState.value = _configState.value.copy(dohServers = servers)
    }

    fun addDohServer(server: String) {
        val current = _configState.value.dohServers.trim()
        val servers = current.split(Regex("[\n,]+")).map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
        if (!servers.contains(server)) {
            servers.add(server)
            _configState.value = _configState.value.copy(dohServers = servers.joinToString("\n"))
        }
    }

    fun updateDohDomains(domains: String) {
        _configState.value = _configState.value.copy(dohDomains = domains)
    }

    fun setProxyEnabled(enabled: Boolean) {
        _configState.value = _configState.value.copy(proxyEnabled = enabled)
    }

    fun updateProxyUrls(urls: String) {
        _configState.value = _configState.value.copy(proxyUrls = urls)
    }

    fun updateProxyDnsServer(dns: String) {
        _configState.value = _configState.value.copy(proxyDnsServer = dns)
    }

    fun setProxyDnsMode(mode: String) {
        _configState.value = _configState.value.copy(proxyDnsMode = mode)
    }

    fun updateProxyDohServer(url: String) {
        _configState.value = _configState.value.copy(proxyDohServer = url)
    }

    fun setProxyModeNative(enabled: Boolean) {
        _configState.value = _configState.value.copy(proxyModeNative = enabled)
    }

    fun setProxyModeWebview(enabled: Boolean) {
        _configState.value = _configState.value.copy(proxyModeWebview = enabled)
    }

    fun setSpeedEnabled(enabled: Boolean) {
        _configState.value = _configState.value.copy(speedEnabled = enabled)
    }

    fun updateSpeedUrl(url: String) {
        _configState.value = _configState.value.copy(speedUrl = url)
    }

    fun updateTimeoutMs(timeout: Long) {
        _configState.value = _configState.value.copy(timeoutMs = timeout.coerceAtLeast(1000L))
    }

    fun resetExecution() {
        _executionState.value = ExpertExecutionState()
    }

    fun startLocalDiagnosis(language: AppLanguage) {
        if (_executionState.value.isRunning) return

        val cfg = _configState.value
        val targets = cfg.targetHosts.split(Regex("[\n,]+")).map { it.trim() }.filter { it.isNotEmpty() }
        val httpCustomUrls = cfg.httpUrls.split(Regex("[\n,]+")).map { it.trim() }.filter { it.isNotEmpty() }
        val dohDomains = cfg.dohDomains.split(Regex("[\n,]+")).map { it.trim() }.filter { it.isNotEmpty() }
        val dohServers = cfg.dohServers.split(Regex("[\n,]+")).map { it.trim() }.filter { it.isNotEmpty() }
        val proxyUrls = cfg.proxyUrls.split(Regex("[\n,]+")).map { it.trim() }.filter { it.isNotEmpty() }

        val hasStandardTargets = targets.isNotEmpty() && (cfg.pingEnabled || cfg.dnsEnabled || cfg.tcpEnabled || cfg.speedEnabled)
        val hasHttp = cfg.httpEnabled && (httpCustomUrls.isNotEmpty() || targets.isNotEmpty())
        val hasDoh = cfg.dohEnabled && dohServers.isNotEmpty() && (dohDomains.isNotEmpty() || targets.isNotEmpty())
        val hasProxy = cfg.proxyEnabled && proxyUrls.isNotEmpty()

        if (!hasStandardTargets && !hasHttp && !hasDoh && !hasProxy) {
            _executionState.value = _executionState.value.copy(
                errorMessage = "请至少选择一项探测选项并填写有效的目标主机或网页地址！"
            )
            return
        }

        _executionState.value = ExpertExecutionState(
            isRunning = true,
            progress = 0.05f,
            currentStep = "正在准备本地诊断任务...",
            results = emptyList(),
            analysisReport = null,
            isCompleted = false,
            errorMessage = null
        )

        viewModelScope.launch(Dispatchers.IO) {
            val resultsList = mutableListOf<TaskExecutionResult>()
            val timeoutMs = cfg.timeoutMs

            try {
                // 构建待测试队列描述
                val executionPlan = mutableListOf<suspend () -> Unit>()

                // 1. Ping
                if (cfg.pingEnabled && targets.isNotEmpty()) {
                    for (t in targets) {
                        val host = extractHost(t)
                        if (host.isNotEmpty()) {
                            executionPlan.add {
                                _executionState.value = _executionState.value.copy(currentStep = "Ping 探测: $host")
                                val res = NetworkEngine.executePing(host, timeoutMs)
                                resultsList.add(res)
                            }
                        }
                    }
                }

                // 2. DNS
                if (cfg.dnsEnabled && targets.isNotEmpty()) {
                    for (t in targets) {
                        val host = extractHost(t)
                        if (host.isNotEmpty()) {
                            executionPlan.add {
                                _executionState.value = _executionState.value.copy(currentStep = "DNS 解析: $host")
                                val res = NetworkEngine.executeDns(host)
                                resultsList.add(res)
                            }
                        }
                    }
                }

                // 3. HTTP 网页连通
                if (cfg.httpEnabled) {
                    val finalHttpTargets = if (httpCustomUrls.isNotEmpty()) {
                        httpCustomUrls
                    } else {
                        targets.map { t ->
                            if (!t.startsWith("http://", ignoreCase = true) && !t.startsWith("https://", ignoreCase = true)) {
                                "https://$t"
                            } else {
                                t
                            }
                        }
                    }
                    for (url in finalHttpTargets) {
                        val normalizedUrl = if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
                            "https://$url"
                        } else {
                            url
                        }
                        executionPlan.add {
                            _executionState.value = _executionState.value.copy(currentStep = "HTTP 网页连通: $normalizedUrl")
                            val res = NetworkEngine.executeHttp(normalizedUrl, timeoutMs)
                            resultsList.add(res)
                        }
                    }
                }

                // 4. TCP
                if (cfg.tcpEnabled && targets.isNotEmpty()) {
                    for (t in targets) {
                        val tcpTarget = extractTcpTarget(t)
                        executionPlan.add {
                            _executionState.value = _executionState.value.copy(currentStep = "TCP 端口探测: $tcpTarget")
                            val res = NetworkEngine.executeTcp(tcpTarget, timeoutMs)
                            resultsList.add(res)
                        }
                    }
                }

                // 5. DoH
                if (cfg.dohEnabled) {
                    val finalDomains = if (dohDomains.isNotEmpty()) dohDomains else targets
                    val finalServers = if (dohServers.isNotEmpty()) dohServers else listOf("https://cloudflare-dns.com/dns-query")
                    for (dom in finalDomains) {
                        for (srv in finalServers) {
                            executionPlan.add {
                                _executionState.value = _executionState.value.copy(currentStep = "DoH 安全解析: $dom")
                                val res = NetworkEngine.executeDoh(srv, dom, timeoutMs)
                                resultsList.add(res)
                            }
                        }
                    }
                }

                // 6. 内置代理测试
                if (cfg.proxyEnabled && proxyUrls.isNotEmpty()) {
                    val modes = mutableListOf<String>()
                    if (cfg.proxyModeNative) modes.add("native")
                    if (cfg.proxyModeWebview) modes.add("webview")
                    if (modes.isEmpty()) modes.add("native")

                    val proxyDnsList = when (cfg.proxyDnsMode) {
                        "TRADITIONAL" -> {
                            if (cfg.proxyDnsServer.isNotBlank()) listOf(cfg.proxyDnsServer.trim()) else listOf("8.8.8.8")
                        }
                        else -> emptyList()
                    }

                    val proxyDohList = when (cfg.proxyDnsMode) {
                        "DOH" -> {
                            if (cfg.proxyDohServer.isNotBlank()) listOf(cfg.proxyDohServer.trim()) else listOf("https://cloudflare-dns.com/dns-query")
                        }
                        else -> emptyList()
                    }

                    executionPlan.add {
                        _executionState.value = _executionState.value.copy(currentStep = "内置代理链路测试...")
                        val proxyResults = NetworkEngine.executeProxyTest(
                            context = getApplication(),
                            urls = proxyUrls,
                            modes = modes,
                            dohServers = proxyDohList,
                            dnsServers = proxyDnsList,
                            hostsMapping = emptyMap(),
                            requestedPort = 0,
                            timeoutMs = timeoutMs
                        )
                        resultsList.addAll(proxyResults)
                    }
                }

                // 7. 网速测速
                if (cfg.speedEnabled) {
                    val speedTarget = cfg.speedUrl.ifBlank { "https://speed.cloudflare.com/__down?bytes=5000000" }
                    executionPlan.add {
                        _executionState.value = _executionState.value.copy(currentStep = "下载测速探测...")
                        val res = NetworkEngine.executeSpeedTest(speedTarget, timeoutMs)
                        resultsList.add(res)
                    }
                }

                val totalSteps = executionPlan.size
                if (totalSteps == 0) {
                    _executionState.value = _executionState.value.copy(
                        isRunning = false,
                        errorMessage = "没有可执行的诊断探测任务"
                    )
                    return@launch
                }

                // 逐步执行并更新进度
                for ((idx, stepAction) in executionPlan.withIndex()) {
                    val progress = 0.1f + 0.85f * ((idx + 1).toFloat() / totalSteps)
                    _executionState.value = _executionState.value.copy(
                        progress = progress.coerceAtMost(0.95f)
                    )
                    stepAction.invoke()
                }

                _executionState.value = _executionState.value.copy(
                    progress = 0.98f,
                    currentStep = "正在综合计算智能诊断评估报告..."
                )

                // 智能分析 (完全本地运算)
                val analysis = NetworkDiagnosticAnalyzer.analyze(resultsList, language)

                _executionState.value = ExpertExecutionState(
                    isRunning = false,
                    progress = 1.0f,
                    currentStep = "本地诊断执行完毕",
                    results = resultsList,
                    analysisReport = analysis,
                    isCompleted = true,
                    errorMessage = null
                )

            } catch (e: Exception) {
                _executionState.value = _executionState.value.copy(
                    isRunning = false,
                    errorMessage = "执行过程异常: ${e.message ?: "未知异常"}"
                )
            }
        }
    }

    private fun extractHost(input: String): String {
        var s = input.trim()
        if (s.startsWith("http://", ignoreCase = true)) s = s.substring(7)
        if (s.startsWith("https://", ignoreCase = true)) s = s.substring(8)
        val slashIdx = s.indexOf('/')
        if (slashIdx != -1) s = s.substring(0, slashIdx)
        val colonIdx = s.lastIndexOf(':')
        if (colonIdx != -1 && !s.contains("]")) s = s.substring(0, colonIdx)
        return s
    }

    private fun extractTcpTarget(input: String): String {
        var s = input.trim()
        val isHttps = s.startsWith("https://", ignoreCase = true)
        if (s.startsWith("http://", ignoreCase = true)) s = s.substring(7)
        if (s.startsWith("https://", ignoreCase = true)) s = s.substring(8)
        val slashIdx = s.indexOf('/')
        if (slashIdx != -1) s = s.substring(0, slashIdx)
        return if (!s.contains(":")) {
            if (isHttps) "$s:443" else "$s:80"
        } else {
            s
        }
    }
}
