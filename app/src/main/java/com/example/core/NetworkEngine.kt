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
}
