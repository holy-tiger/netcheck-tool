package com.example.ui.expert

import com.example.core.AnalysisReport
import com.example.core.TaskExecutionResult

data class ExpertConfigState(
    val targetHosts: String = "8.8.8.8, 1.1.1.1, baidu.com",
    val pingEnabled: Boolean = true,
    val dnsEnabled: Boolean = true,
    val httpEnabled: Boolean = true,
    val httpUrls: String = "",
    val tcpEnabled: Boolean = true,
    val dohEnabled: Boolean = false,
    val dohServers: String = "https://cloudflare-dns.com/dns-query\nhttps://dns.google/dns-query",
    val dohDomains: String = "google.com\ncloudflare.com",
    val proxyEnabled: Boolean = false,
    val proxyUrls: String = "https://example.com",
    val proxyDnsMode: String = "SYSTEM", // "SYSTEM", "TRADITIONAL", "DOH"
    val proxyDnsServer: String = "8.8.8.8",
    val proxyDohServer: String = "https://cloudflare-dns.com/dns-query",
    val proxyModeNative: Boolean = true,
    val proxyModeWebview: Boolean = true,
    val speedEnabled: Boolean = false,
    val speedUrl: String = "",
    val timeoutMs: Long = 15000L
)

data class ExpertExecutionState(
    val isRunning: Boolean = false,
    val progress: Float = 0f,
    val currentStep: String = "",
    val results: List<TaskExecutionResult> = emptyList(),
    val analysisReport: AnalysisReport? = null,
    val isCompleted: Boolean = false,
    val errorMessage: String? = null
)
