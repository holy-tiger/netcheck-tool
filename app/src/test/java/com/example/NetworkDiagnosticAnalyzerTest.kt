package com.example

import com.example.core.NetworkDiagnosticAnalyzer
import com.example.core.TaskExecutionResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkDiagnosticAnalyzerTest {

    @Test
    fun testAllTasksSuccessfulYieldsPass() {
        val results = listOf(
            TaskExecutionResult("ping|8.8.8.8", "success", "4 packets transmitted, 4 received, 0% packet loss", 50),
            TaskExecutionResult("dns|google.com", "success", "google.com resolved to 142.250.190.46", 30),
            TaskExecutionResult("doh|google.com (Cloudflare)", "success", "Resolved: 142.250.190.46 in 45ms", 45)
        )
        val analysis = NetworkDiagnosticAnalyzer.analyze(results)
        assertEquals("PASS", analysis.status)
        assertTrue(analysis.issues.isEmpty())
        assertTrue(analysis.solutions.isNotEmpty())
    }

    @Test
    fun testStandardDnsFailedWithDohSuccessProvidesTargetedSolution() {
        val results = listOf(
            TaskExecutionResult("ping|8.8.8.8", "success", "4 packets transmitted, 4 received, 0% packet loss", 40),
            TaskExecutionResult("dns|bad-dns.com", "failed", "UnknownHostException: Unable to resolve host", 120),
            TaskExecutionResult("doh|bad-dns.com (Cloudflare)", "success", "Resolved: 104.21.5.12 in 50ms", 50)
        )
        val analysis = NetworkDiagnosticAnalyzer.analyze(results)
        assertEquals("WARN", analysis.status)
        assertTrue(analysis.issues.any { it.contains("DNS") })
        // Should recommend DoH or switching DNS servers
        assertTrue(analysis.solutions.any { it.contains("DoH") || it.contains("DNS") })
    }

    @Test
    fun testProxyFailureDetectsProxyIssue() {
        val results = listOf(
            TaskExecutionResult("proxy_test|https://internal.test [native]", "failed", "Proxy HTTP 502: Bad Gateway or connection refused", 200)
        )
        val analysis = NetworkDiagnosticAnalyzer.analyze(results)
        assertEquals("WARN", analysis.status)
        assertTrue(analysis.issues.any { it.contains("代理") })
        assertTrue(analysis.solutions.any { it.contains("代理") || it.contains("端口") })
    }

    @Test
    fun testTotalInternetFailureYieldsFail() {
        val results = listOf(
            TaskExecutionResult("ping|8.8.8.8", "failed", "100% packet loss", 2000),
            TaskExecutionResult("http|https://example.com", "failed", "ConnectException: Network unreachable", 1500)
        )
        val analysis = NetworkDiagnosticAnalyzer.analyze(results)
        assertEquals("FAIL", analysis.status)
        assertTrue(analysis.issues.any { it.contains("完全无法连接") })
    }
}
