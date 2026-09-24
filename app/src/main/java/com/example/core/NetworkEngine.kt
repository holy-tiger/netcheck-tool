package com.example.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

data class TaskExecutionResult(
    val task: String,
    val status: String, // "success" or "failed"
    val raw_log: String,
    val durationMs: Long
)

object NetworkEngine {

    /**
     * AI 约束：Ping：使用 Runtime.getRuntime().exec("ping -c 4 -w 10 $target")。
     * 必须捕获进程的 InputStream 和 ErrorStream 并拼接为 raw_log。
     */
    suspend fun executePing(target: String, timeoutMs: Long = 20000L): TaskExecutionResult =
        withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            val cleanTarget = target.trim()
            val command = "ping -c 4 -w 10 $cleanTarget"
            val sbLog = java.lang.StringBuilder()
            sbLog.append("=== Ping Test: ").append(cleanTarget).append(" ===\n")
            sbLog.append("$ ").append(command).append("\n\n")

            var exitCode = -1
            try {
                val process = Runtime.getRuntime().exec(command)

                // Read standard output
                val stdoutReader = BufferedReader(InputStreamReader(process.inputStream))
                val stderrReader = BufferedReader(InputStreamReader(process.errorStream))

                var line: String?
                while (stdoutReader.readLine().also { line = it } != null) {
                    sbLog.append(line).append("\n")
                }
                while (stderrReader.readLine().also { line = it } != null) {
                    sbLog.append("[ERR] ").append(line).append("\n")
                }

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
                    if (!finished) {
                        process.destroyForcibly()
                        sbLog.append("\n[Process Timed Out after ").append(timeoutMs).append(" ms]\n")
                    } else {
                        exitCode = process.exitValue()
                    }
                } else {
                    exitCode = process.waitFor()
                }

                sbLog.append("\n[Exit Code: ").append(exitCode).append("]\n")
            } catch (e: Exception) {
                sbLog.append("\n[Exception: ").append(e.javaClass.simpleName).append("]: ")
                    .append(e.message ?: "Unknown error")
            }

            val durationMs = System.currentTimeMillis() - startTime
            val status = if (exitCode == 0) "success" else "failed"

            TaskExecutionResult(
                task = "ping|$cleanTarget",
                status = status,
                raw_log = sbLog.toString(),
                durationMs = durationMs
            )
        }

    /**
     * AI 约束：DNS：使用 java.net.InetAddress.getAllByName(target)。
     * 必须捕获耗时时间（返回前和返回后的时间戳差值），捕获 UnknownHostException。
     */
    suspend fun executeDns(target: String): TaskExecutionResult =
        withContext(Dispatchers.IO) {
            val cleanTarget = target.trim()
            val sbLog = java.lang.StringBuilder()
            sbLog.append("=== DNS Resolution: ").append(cleanTarget).append(" ===\n")

            val beforeTime = System.currentTimeMillis()
            var status = "failed"
            try {
                // Return all resolved IP addresses
                val addresses = InetAddress.getAllByName(cleanTarget)
                val afterTime = System.currentTimeMillis()
                val elapsed = afterTime - beforeTime

                sbLog.append("Resolution Time: ").append(elapsed).append(" ms\n")
                sbLog.append("Resolved ").append(addresses.size).append(" address(es):\n")
                addresses.forEachIndexed { index, addr ->
                    sbLog.append(" [").append(index + 1).append("] ")
                        .append(addr.hostAddress)
                        .append(" (Canonical: ").append(addr.canonicalHostName).append(")\n")
                }
                status = "success"
                TaskExecutionResult(
                    task = "dns|$cleanTarget",
                    status = status,
                    raw_log = sbLog.toString(),
                    durationMs = elapsed
                )
            } catch (e: UnknownHostException) {
                val afterTime = System.currentTimeMillis()
                val elapsed = afterTime - beforeTime
                sbLog.append("Resolution Time: ").append(elapsed).append(" ms\n")
                sbLog.append("[UnknownHostException]: Unable to resolve host '")
                    .append(cleanTarget).append("': ").append(e.message)
                TaskExecutionResult(
                    task = "dns|$cleanTarget",
                    status = "failed",
                    raw_log = sbLog.toString(),
                    durationMs = elapsed
                )
            } catch (e: Exception) {
                val afterTime = System.currentTimeMillis()
                val elapsed = afterTime - beforeTime
                sbLog.append("Resolution Time: ").append(elapsed).append(" ms\n")
                sbLog.append("[Exception]: ").append(e.javaClass.simpleName)
                    .append(" - ").append(e.message)
                TaskExecutionResult(
                    task = "dns|$cleanTarget",
                    status = "failed",
                    raw_log = sbLog.toString(),
                    durationMs = elapsed
                )
            }
        }

    /**
     * HTTP 网页/接口连通性测试 (HTTP Check)
     * 支持 http:// 与 https://，测试状态码、握手与响应延迟、重定向、Header
     */
    suspend fun executeHttp(target: String, timeoutMs: Long = 15000L): TaskExecutionResult =
        withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            var urlString = target.trim()
            if (!urlString.startsWith("http://", ignoreCase = true) && !urlString.startsWith("https://", ignoreCase = true)) {
                urlString = "https://$urlString"
            }
            val sbLog = java.lang.StringBuilder()
            sbLog.append("=== HTTP/Web Connectivity: ").append(urlString).append(" ===\n")

            var status = "failed"
            var connection: java.net.HttpURLConnection? = null
            try {
                val url = java.net.URL(urlString)
                connection = url.openConnection() as java.net.HttpURLConnection
                val timeout = timeoutMs.toInt().coerceIn(1000, 30000)
                connection.connectTimeout = timeout
                connection.readTimeout = timeout
                connection.instanceFollowRedirects = true
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", "NetCheck/1.0 (Android)")
                connection.setRequestProperty("Accept", "*/*")

                val connectStart = System.currentTimeMillis()
                connection.connect()
                val connectTime = System.currentTimeMillis() - connectStart

                val responseCode = connection.responseCode
                val responseMessage = connection.responseMessage ?: ""
                val contentType = connection.contentType ?: "unknown"
                val contentLength = connection.contentLengthLong

                val totalDuration = System.currentTimeMillis() - startTime
                sbLog.append("Target URL: ").append(urlString).append("\n")
                sbLog.append("HTTP Status: ").append(responseCode).append(" ").append(responseMessage).append("\n")
                sbLog.append("Connect Latency: ").append(connectTime).append(" ms\n")
                sbLog.append("Total Roundtrip: ").append(totalDuration).append(" ms\n")
                sbLog.append("Content-Type: ").append(contentType).append("\n")
                if (contentLength >= 0) {
                    sbLog.append("Content-Length: ").append(contentLength).append(" bytes\n")
                }

                // Consider 2xx and 3xx as success
                if (responseCode in 200..399) {
                    status = "success"
                    sbLog.append("[Result]: HTTP request passed successfully\n")
                } else {
                    status = "failed"
                    sbLog.append("[Result]: HTTP returned error code: ").append(responseCode).append("\n")
                }
            } catch (e: java.net.SocketTimeoutException) {
                sbLog.append("[SocketTimeoutException]: Connection timed out after ").append(timeoutMs).append(" ms\n")
            } catch (e: javax.net.ssl.SSLException) {
                sbLog.append("[SSLException]: SSL/TLS handshake failed: ").append(e.message).append("\n")
            } catch (e: Exception) {
                sbLog.append("[Exception: ").append(e.javaClass.simpleName).append("]: ").append(e.message ?: "Unknown error").append("\n")
            } finally {
                connection?.disconnect()
            }

            val elapsed = System.currentTimeMillis() - startTime
            TaskExecutionResult(
                task = "http|$target",
                status = status,
                raw_log = sbLog.toString(),
                durationMs = elapsed
            )
        }

    /**
     * TCP 端口连通性探测 (TCP Port Check)
     * 支持 host:port 格式，测试指定端口是否开放、握手时延
     */
    suspend fun executeTcp(target: String, timeoutMs: Long = 10000L): TaskExecutionResult =
        withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            val cleanTarget = target.trim()
            val sbLog = java.lang.StringBuilder()
            sbLog.append("=== TCP Port Probe: ").append(cleanTarget).append(" ===\n")

            var host = cleanTarget
            var port = 80

            if (cleanTarget.contains(":")) {
                val parts = cleanTarget.split(":")
                host = parts[0].trim()
                port = parts.getOrNull(1)?.toIntOrNull() ?: 80
            } else if (cleanTarget.startsWith("http://", ignoreCase = true)) {
                val uri = java.net.URI(cleanTarget)
                host = uri.host ?: cleanTarget
                port = if (uri.port > 0) uri.port else 80
            } else if (cleanTarget.startsWith("https://", ignoreCase = true)) {
                val uri = java.net.URI(cleanTarget)
                host = uri.host ?: cleanTarget
                port = if (uri.port > 0) uri.port else 443
            }

            var status = "failed"
            var socket: java.net.Socket? = null
            try {
                socket = java.net.Socket()
                val endpoint = java.net.InetSocketAddress(host, port)
                val timeout = timeoutMs.toInt().coerceIn(1000, 20000)

                val connectStart = System.currentTimeMillis()
                socket.connect(endpoint, timeout)
                val elapsed = System.currentTimeMillis() - connectStart

                sbLog.append("Resolved Host: ").append(host).append("\n")
                sbLog.append("Target Port: ").append(port).append("\n")
                sbLog.append("Remote Address: ").append(socket.remoteSocketAddress).append("\n")
                sbLog.append("TCP Handshake Time: ").append(elapsed).append(" ms\n")
                sbLog.append("[Result]: Port is OPEN and reachable\n")
                status = "success"
            } catch (e: java.net.ConnectException) {
                sbLog.append("[ConnectException]: Connection refused on port ").append(port).append(" (").append(e.message).append(")\n")
            } catch (e: java.net.SocketTimeoutException) {
                sbLog.append("[SocketTimeoutException]: TCP connection timed out after ").append(timeoutMs).append(" ms\n")
            } catch (e: java.net.UnknownHostException) {
                sbLog.append("[UnknownHostException]: Unable to resolve hostname '").append(host).append("'\n")
            } catch (e: Exception) {
                sbLog.append("[Exception: ").append(e.javaClass.simpleName).append("]: ").append(e.message ?: "Unknown error").append("\n")
            } finally {
                try { socket?.close() } catch (_: Exception) {}
            }

            val totalElapsed = System.currentTimeMillis() - startTime
            TaskExecutionResult(
                task = "tcp|$cleanTarget",
                status = status,
                raw_log = sbLog.toString(),
                durationMs = totalElapsed
            )
        }

    /**
     * 下行网速测试 (Speed / Download Bandwidth Test)
     * 通过指定的测试下载文件 URL 进行数据拉取流测速，计算下行速率 (Mbps / MB/s)
     */
    suspend fun executeSpeedTest(fileUrl: String, timeoutMs: Long = 30000L): TaskExecutionResult =
        withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            var urlString = fileUrl.trim()
            if (!urlString.startsWith("http://", ignoreCase = true) && !urlString.startsWith("https://", ignoreCase = true)) {
                urlString = "https://$urlString"
            }
            val sbLog = java.lang.StringBuilder()
            sbLog.append("=== Download Speed Test: ").append(urlString).append(" ===\n")

            var status = "failed"
            var connection: java.net.HttpURLConnection? = null
            var inputStream: java.io.InputStream? = null
            var totalBytes: Long = 0
            val effectiveTimeout = timeoutMs.toInt().coerceIn(3000, 60000)

            try {
                val url = java.net.URL(urlString)
                connection = url.openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 10000
                connection.readTimeout = 15000
                connection.instanceFollowRedirects = true
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", "NetCheck-SpeedEngine/1.0 (Android)")
                connection.setRequestProperty("Accept-Encoding", "identity")

                val connectStart = System.currentTimeMillis()
                connection.connect()
                val connectTime = System.currentTimeMillis() - connectStart

                val responseCode = connection.responseCode
                val responseMessage = connection.responseMessage ?: ""
                val declaredContentLength = connection.contentLengthLong

                sbLog.append("Target Download URL: ").append(urlString).append("\n")
                sbLog.append("HTTP Status: ").append(responseCode).append(" ").append(responseMessage).append("\n")
                sbLog.append("Connect Latency: ").append(connectTime).append(" ms\n")
                if (declaredContentLength > 0) {
                    val declaredMb = String.format(java.util.Locale.US, "%.2f MB", declaredContentLength / (1024.0 * 1024.0))
                    sbLog.append("File Content-Length: ").append(declaredContentLength).append(" bytes (").append(declaredMb).append(")\n")
                }

                if (responseCode in 200..299) {
                    inputStream = connection.inputStream
                    val buffer = ByteArray(16384) // 16 KB buffer
                    val streamStart = System.currentTimeMillis()
                    var bytesRead: Int

                    while (true) {
                        if (System.currentTimeMillis() - startTime >= effectiveTimeout) {
                            sbLog.append("[Notice]: Reached test time limit (").append(effectiveTimeout).append(" ms), stopping download stream\n")
                            break
                        }
                        bytesRead = inputStream.read(buffer)
                        if (bytesRead == -1) break
                        totalBytes += bytesRead
                    }

                    val downloadDurationMs = (System.currentTimeMillis() - streamStart).coerceAtLeast(1L)
                    val durationSec = downloadDurationMs / 1000.0
                    val totalMB = totalBytes / (1024.0 * 1024.0)
                    val speedMBs = if (durationSec > 0) totalMB / durationSec else 0.0
                    val speedMbps = speedMBs * 8.0

                    sbLog.append(
                        String.format(
                            java.util.Locale.US,
                            "Downloaded: %.2f MB (%d bytes) in %.2f s\n",
                            totalMB,
                            totalBytes,
                            durationSec
                        )
                    )
                    sbLog.append(
                        String.format(
                            java.util.Locale.US,
                            "Average Downlink Speed: %.2f Mbps (%.2f MB/s)\n",
                            speedMbps,
                            speedMBs
                        )
                    )

                    if (totalBytes > 0) {
                        status = "success"
                        sbLog.append("[Result]: Download speed test completed successfully\n")
                    } else {
                        status = "failed"
                        sbLog.append("[Result]: No data received from test file URL\n")
                    }
                } else {
                    sbLog.append("[Result]: Speed test failed with HTTP status ").append(responseCode).append("\n")
                }
            } catch (e: java.net.SocketTimeoutException) {
                sbLog.append("[SocketTimeoutException]: Connection timed out during speed test download\n")
            } catch (e: Exception) {
                sbLog.append("[Exception: ").append(e.javaClass.simpleName).append("]: ").append(e.message ?: "Unknown error").append("\n")
            } finally {
                try { inputStream?.close() } catch (_: Exception) {}
                connection?.disconnect()
            }

            val totalElapsed = System.currentTimeMillis() - startTime
            TaskExecutionResult(
                task = "speed|$urlString",
                status = status,
                raw_log = sbLog.toString(),
                durationMs = totalElapsed
            )
        }

    /**
     * DoH (DNS over HTTPS) 域名解析专项检测
     */
    suspend fun executeDoh(
        serverUrl: String,
        domain: String,
        timeoutMs: Long = 15000L
    ): TaskExecutionResult = withContext(Dispatchers.IO) {
        val dohResult = DohEngine.queryDoh(serverUrl, domain, timeoutMs)
        val cleanDomain = domain.trim()
        val cleanServer = serverUrl.trim()
        TaskExecutionResult(
            task = "doh|$cleanServer|$cleanDomain",
            status = if (dohResult.isSuccess) "success" else "failed",
            raw_log = dohResult.rawLog,
            durationMs = dohResult.durationMs
        )
    }

    /**
     * 内置本地代理服务器综合测试 (防端口冲突 / 原生与 WebView 双模)
     */
    suspend fun executeProxyTest(
        context: android.content.Context,
        urls: List<String>,
        modes: List<String>,
        dohServers: List<String> = emptyList(),
        dnsServers: List<String> = emptyList(),
        hostsMapping: Map<String, String> = emptyMap(),
        requestedPort: Int = 0,
        timeoutMs: Long = 20000L
    ): List<TaskExecutionResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<TaskExecutionResult>()

        // 1. 启动内置本地代理服务器 (自动处理端口冲突)
        val proxy = LocalProxyServer(
            requestedPort = requestedPort,
            dohServers = dohServers,
            dnsServers = dnsServers,
            hostsMapping = hostsMapping
        )
        val actualPort = proxy.start()

        try {
            val effectiveUrls = if (urls.isEmpty()) listOf("https://example.com") else urls
            val effectiveModes = if (modes.isEmpty()) listOf("native") else modes

            for (url in effectiveUrls) {
                var cleanUrl = url.trim()
                if (!cleanUrl.startsWith("http://", ignoreCase = true) && !cleanUrl.startsWith("https://", ignoreCase = true)) {
                    cleanUrl = "https://$cleanUrl"
                }

                // 原生 Native 访问模式测试
                if (effectiveModes.contains("native")) {
                    val nativeRes = executeNativeProxyTest(
                        targetUrl = cleanUrl,
                        proxyPort = actualPort,
                        proxy = proxy,
                        timeoutMs = timeoutMs
                    )
                    results.add(nativeRes)
                }

                // WebView 访问模式测试
                if (effectiveModes.contains("webview")) {
                    val webViewRes = executeWebViewProxyTest(
                        context = context,
                        targetUrl = cleanUrl,
                        proxyPort = actualPort,
                        proxy = proxy,
                        timeoutMs = timeoutMs
                    )
                    results.add(webViewRes)
                }
            }
        } finally {
            proxy.stop()
        }

        results
    }

    private suspend fun executeNativeProxyTest(
        targetUrl: String,
        proxyPort: Int,
        proxy: LocalProxyServer,
        timeoutMs: Long
    ): TaskExecutionResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val sbLog = StringBuilder()
        sbLog.append("=== Local Proxy Native Test: ").append(targetUrl).append(" ===\n")
        sbLog.append("Local Proxy Server: 127.0.0.1:").append(proxyPort)
        if (proxy.portHadConflict) {
            sbLog.append(" (⚠️ 请求端口冲突，已自动安全转移至空闲端口)\n")
        } else {
            sbLog.append(" (动态端口无冲突保障)\n")
        }

        var status = "failed"
        val proxyObj = java.net.Proxy(java.net.Proxy.Type.HTTP, java.net.InetSocketAddress("127.0.0.1", proxyPort))

        try {
            val okHttpClient = okhttp3.OkHttpClient.Builder()
                .proxy(proxyObj)
                .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .followRedirects(true)
                .build()

            val request = okhttp3.Request.Builder()
                .url(targetUrl)
                .header("User-Agent", "NetCheck-ProxyTester/1.0 (Android Native)")
                .build()

            val callStart = System.currentTimeMillis()
            val response = okHttpClient.newCall(request).execute()
            val roundtripTime = System.currentTimeMillis() - callStart

            val code = response.code
            val message = response.message
            val contentType = response.header("Content-Type") ?: "unknown"
            val bodyString = response.body?.string() ?: ""
            val snippet = if (bodyString.length > 250) bodyString.substring(0, 250) + "..." else bodyString

            // 提取代理记录中的解析 IP 与解析耗时
            val uri = java.net.URI(targetUrl)
            val host = uri.host ?: ""
            val records = proxy.getRecords()
            val matchedRecord = records.find { it.host.equals(host, ignoreCase = true) }

            sbLog.append("Target Host: ").append(host).append("\n")
            if (matchedRecord != null) {
                sbLog.append("Resolution Method: ").append(matchedRecord.method).append("\n")
                sbLog.append("Resolved IP: ").append(matchedRecord.resolvedIp).append("\n")
                if (matchedRecord.allIps.size > 1) {
                    sbLog.append("All Resolved IPs: ").append(matchedRecord.allIps.joinToString(", ")).append("\n")
                }
                sbLog.append("DNS Resolution Latency: ").append(matchedRecord.durationMs).append(" ms\n")
            } else {
                sbLog.append("Resolved IP: (Proxied via 127.0.0.1:").append(proxyPort).append(")\n")
            }

            sbLog.append("HTTP Response: ").append(code).append(" ").append(message).append("\n")
            sbLog.append("Roundtrip Latency: ").append(roundtripTime).append(" ms\n")
            sbLog.append("Content-Type: ").append(contentType).append("\n")
            sbLog.append("Response Length: ").append(bodyString.length).append(" chars\n")
            sbLog.append("Content Snippet: \n").append(snippet.trim()).append("\n")

            if (code in 200..399) {
                status = "success"
                sbLog.append("[Result]: Native Proxy Request Succeeded\n")
            } else {
                sbLog.append("[Result]: Native Proxy returned HTTP error ").append(code).append("\n")
            }
        } catch (e: Exception) {
            sbLog.append("[Exception: ").append(e.javaClass.simpleName).append("]: ").append(e.message ?: "Unknown error").append("\n")
        }

        val totalDuration = System.currentTimeMillis() - startTime
        TaskExecutionResult(
            task = "proxy_test|native|$targetUrl",
            status = status,
            raw_log = sbLog.toString(),
            durationMs = totalDuration
        )
    }

    private suspend fun executeWebViewProxyTest(
        context: android.content.Context,
        targetUrl: String,
        proxyPort: Int,
        proxy: LocalProxyServer,
        timeoutMs: Long
    ): TaskExecutionResult {
        val startTime = System.currentTimeMillis()
        val sbLog = StringBuilder()
        sbLog.append("=== Local Proxy WebView Test: ").append(targetUrl).append(" ===\n")
        sbLog.append("Local Proxy Server: 127.0.0.1:").append(proxyPort).append("\n")

        var status = "failed"
        var pageTitle = ""
        var httpCode = 200

        try {
            // 在主线程调度 WebView 执行
            withContext(Dispatchers.Main) {
                val deferred = kotlinx.coroutines.CompletableDeferred<Boolean>()
                var webView: android.webkit.WebView? = null
                try {
                    webView = android.webkit.WebView(context)
                    webView.settings.javaScriptEnabled = true
                    webView.settings.domStorageEnabled = true

                    val proxyObj = java.net.Proxy(java.net.Proxy.Type.HTTP, java.net.InetSocketAddress("127.0.0.1", proxyPort))
                    val interceptClient = okhttp3.OkHttpClient.Builder()
                        .proxy(proxyObj)
                        .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                        .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                        .followRedirects(true)
                        .build()

                    webView.webViewClient = object : android.webkit.WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: android.webkit.WebView?,
                            request: android.webkit.WebResourceRequest?
                        ): android.webkit.WebResourceResponse? {
                            val reqUrl = request?.url?.toString() ?: return null
                            return try {
                                val okReq = okhttp3.Request.Builder()
                                    .url(reqUrl)
                                    .header("User-Agent", "NetCheck-WebViewProxy/1.0")
                                    .build()
                                val resp = interceptClient.newCall(okReq).execute()
                                val contentType = resp.header("Content-Type") ?: "text/html; charset=utf-8"
                                val mimeType = contentType.split(";")[0].trim()
                                val encoding = if (contentType.contains("charset=")) contentType.split("charset=")[1].trim() else "utf-8"
                                val stream = resp.body?.byteStream() ?: java.io.ByteArrayInputStream(ByteArray(0))
                                httpCode = resp.code
                                android.webkit.WebResourceResponse(mimeType, encoding, resp.code, resp.message.ifEmpty { "OK" }, emptyMap(), stream)
                            } catch (e: Exception) {
                                null
                            }
                        }

                        override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            pageTitle = view?.title ?: ""
                            deferred.complete(true)
                        }

                        override fun onReceivedError(
                            view: android.webkit.WebView?,
                            errorCode: Int,
                            description: String?,
                            failingUrl: String?
                        ) {
                            super.onReceivedError(view, errorCode, description, failingUrl)
                            sbLog.append("[WebView Error]: ").append(description).append(" (Code: ").append(errorCode).append(")\n")
                            deferred.complete(false)
                        }
                    }

                    webView.loadUrl(targetUrl)

                    // 限制等待时间
                    kotlinx.coroutines.withTimeoutOrNull(timeoutMs) {
                        deferred.await()
                    } ?: run {
                        sbLog.append("[WebView Timeout]: Page loading timed out after ").append(timeoutMs).append(" ms\n")
                    }
                } finally {
                    try {
                        webView?.stopLoading()
                        webView?.destroy()
                    } catch (_: Throwable) {}
                }
            }

            val uri = java.net.URI(targetUrl)
            val host = uri.host ?: ""
            val records = proxy.getRecords()
            val matchedRecord = records.find { it.host.equals(host, ignoreCase = true) }

            sbLog.append("Target Host: ").append(host).append("\n")
            if (matchedRecord != null) {
                sbLog.append("Resolution Method: ").append(matchedRecord.method).append("\n")
                sbLog.append("Resolved IP: ").append(matchedRecord.resolvedIp).append("\n")
                sbLog.append("DNS Resolution Latency: ").append(matchedRecord.durationMs).append(" ms\n")
            }

            sbLog.append("Rendered Page Title: ").append(pageTitle.ifEmpty { "(No Title)" }).append("\n")
            sbLog.append("HTTP Status: ").append(httpCode).append("\n")

            if (httpCode in 200..399) {
                status = "success"
                sbLog.append("[Result]: WebView Proxy Page Rendered Successfully\n")
            } else {
                sbLog.append("[Result]: WebView Proxy returned HTTP status ").append(httpCode).append("\n")
            }
        } catch (e: Throwable) {
            sbLog.append("[WebView Exception: ").append(e.javaClass.simpleName).append("]: ").append(e.message ?: "Unknown error").append("\n")
        }

        val totalDuration = System.currentTimeMillis() - startTime
        return TaskExecutionResult(
            task = "proxy_test|webview|$targetUrl",
            status = status,
            raw_log = sbLog.toString(),
            durationMs = totalDuration
        )
    }
}
