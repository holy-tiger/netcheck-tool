package com.example.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

data class ProxyResolutionRecord(
    val host: String,
    val resolvedIp: String,
    val allIps: List<String>,
    val method: String,
    val durationMs: Long
)

class LocalProxyServer(
    val requestedPort: Int = 0,
    val dohServers: List<String> = emptyList(),
    val dnsServers: List<String> = emptyList(),
    val hostsMapping: Map<String, String> = emptyMap()
) {

    private var serverSocket: ServerSocket? = null
    var actualPort: Int = 0
        private set
    var portHadConflict: Boolean = false
        private set

    private var serverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private val resolutionRecords = ConcurrentHashMap<String, ProxyResolutionRecord>()

    fun start(): Int {
        // 规避端口冲突：优先绑定请求端口，遇冲突或0时自动降级分配操作系统空闲端口
        serverSocket = try {
            if (requestedPort > 0) {
                try {
                    ServerSocket(requestedPort, 50, InetAddress.getByName("127.0.0.1"))
                } catch (e: Exception) {
                    portHadConflict = true
                    ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
                }
            } else {
                ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
            }
        } catch (e: Exception) {
            ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        }

        actualPort = serverSocket!!.localPort

        serverJob = scope.launch {
            while (isActive && serverSocket?.isClosed == false) {
                try {
                    val clientSocket = serverSocket?.accept() ?: break
                    launch {
                        handleClient(clientSocket)
                    }
                } catch (e: Exception) {
                    if (!isActive || serverSocket?.isClosed == true) break
                }
            }
        }

        return actualPort
    }

    fun stop() {
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverJob?.cancel()
    }

    fun getRecords(): List<ProxyResolutionRecord> = resolutionRecords.values.toList()

    suspend fun resolveHost(host: String): ProxyResolutionRecord = withContext(Dispatchers.IO) {
        val cleanHost = host.trim().lowercase()
        val cached = resolutionRecords[cleanHost]
        if (cached != null) return@withContext cached

        val startTime = System.currentTimeMillis()

        // 1. 优先检查自定义 Hosts 域名与 IP 映射
        val mappedIp = hostsMapping[cleanHost] ?: hostsMapping[host]
        if (!mappedIp.isNullOrBlank()) {
            val record = ProxyResolutionRecord(
                host = host,
                resolvedIp = mappedIp.trim(),
                allIps = listOf(mappedIp.trim()),
                method = "Hosts Mapping ($cleanHost -> $mappedIp)",
                durationMs = 0L
            )
            resolutionRecords[cleanHost] = record
            return@withContext record
        }

        // 2. 检查自定义 DoH 服务器 (支持多个 DoH 节点竞速或顺序解析)
        if (dohServers.isNotEmpty()) {
            for (dohUrl in dohServers) {
                val dohStart = System.currentTimeMillis()
                val dohRes = DohEngine.queryDoh(dohUrl, cleanHost, timeoutMs = 5000L)
                val dohElapsed = System.currentTimeMillis() - dohStart
                if (dohRes.isSuccess && dohRes.ips.isNotEmpty()) {
                    val record = ProxyResolutionRecord(
                        host = host,
                        resolvedIp = dohRes.ips.first(),
                        allIps = dohRes.ips,
                        method = "DoH ($dohUrl)",
                        durationMs = dohElapsed
                    )
                    resolutionRecords[cleanHost] = record
                    return@withContext record
                }
            }
        }

        // 3. 检查自定义传统 DNS 服务器 (如 8.8.8.8:53)
        if (dnsServers.isNotEmpty()) {
            for (dnsServer in dnsServers) {
                val dnsStart = System.currentTimeMillis()
                val ips = DohEngine.queryUdpDns(dnsServer, cleanHost, timeoutMs = 4000L)
                val dnsElapsed = System.currentTimeMillis() - dnsStart
                if (ips.isNotEmpty()) {
                    val record = ProxyResolutionRecord(
                        host = host,
                        resolvedIp = ips.first(),
                        allIps = ips,
                        method = "Custom DNS ($dnsServer:53)",
                        durationMs = dnsElapsed
                    )
                    resolutionRecords[cleanHost] = record
                    return@withContext record
                }
            }
        }

        // 4. 回退至系统本地 DNS
        val sysStart = System.currentTimeMillis()
        var resolvedIps = emptyList<String>()
        try {
            val addrs = InetAddress.getAllByName(cleanHost)
            resolvedIps = addrs.mapNotNull { it.hostAddress }
        } catch (_: Exception) {}
        val sysElapsed = System.currentTimeMillis() - sysStart

        val targetIp = resolvedIps.firstOrNull() ?: cleanHost
        val record = ProxyResolutionRecord(
            host = host,
            resolvedIp = targetIp,
            allIps = resolvedIps,
            method = "System DNS",
            durationMs = sysElapsed
        )
        resolutionRecords[cleanHost] = record
        record
    }

    private suspend fun handleClient(clientSocket: Socket) = withContext(Dispatchers.IO) {
        var remoteSocket: Socket? = null
        try {
            clientSocket.soTimeout = 15000
            val input = clientSocket.getInputStream()
            val output = clientSocket.getOutputStream()

            // 读取请求首行 (如: "CONNECT example.com:443 HTTP/1.1" 或 "GET http://example.com/ HTTP/1.1")
            val requestLine = readLine(input) ?: return@withContext
            val parts = requestLine.split(" ")
            if (parts.size < 2) return@withContext

            val method = parts[0].uppercase()
            val rawTarget = parts[1]

            if (method == "CONNECT") {
                // HTTPS Tunnel
                val hostPort = rawTarget.split(":")
                val host = hostPort[0]
                val port = hostPort.getOrNull(1)?.toIntOrNull() ?: 443

                // 解析目标 Host (执行自定义 DoH / DNS / Hosts 映射)
                val resolution = resolveHost(host)
                val targetIp = resolution.resolvedIp

                // 读完客户端 CONNECT 请求剩余 headers 直到空行
                while (true) {
                    val line = readLine(input)
                    if (line.isNullOrEmpty()) break
                }

                // 连接远端实际 IP
                remoteSocket = Socket(targetIp, port)
                remoteSocket.soTimeout = 15000

                // 响应客户端 200 Connection Established
                val response = "HTTP/1.1 200 Connection Established\r\n\r\n"
                output.write(response.toByteArray(Charsets.US_ASCII))
                output.flush()

                // 双向透明转发数据流
                val clientIn = clientSocket.getInputStream()
                val clientOut = clientSocket.getOutputStream()
                val remoteIn = remoteSocket.getInputStream()
                val remoteOut = remoteSocket.getOutputStream()

                val job1 = launch { pipe(clientIn, remoteOut) }
                val job2 = launch { pipe(remoteIn, clientOut) }
                job1.join()
                job2.join()

            } else {
                // HTTP GET/POST Proxy
                var host = ""
                var port = 80
                var path = rawTarget

                if (rawTarget.startsWith("http://", ignoreCase = true)) {
                    val uri = java.net.URI(rawTarget)
                    host = uri.host ?: ""
                    port = if (uri.port > 0) uri.port else 80
                    path = (uri.rawPath ?: "/") + if (uri.rawQuery != null) "?${uri.rawQuery}" else ""
                }

                // 读取剩余请求头获取 Host (如果 URL 里没 host)
                val headers = mutableListOf<String>()
                while (true) {
                    val line = readLine(input) ?: break
                    if (line.isEmpty()) break
                    if (host.isEmpty() && line.startsWith("Host:", ignoreCase = true)) {
                        val hPart = line.substring(5).trim()
                        if (hPart.contains(":")) {
                            host = hPart.split(":")[0]
                            port = hPart.split(":")[1].toIntOrNull() ?: 80
                        } else {
                            host = hPart
                        }
                    }
                    // 替换 Proxy-Connection 为 Connection
                    if (!line.startsWith("Proxy-", ignoreCase = true)) {
                        headers.add(line)
                    }
                }

                if (host.isNotEmpty()) {
                    val resolution = resolveHost(host)
                    val targetIp = resolution.resolvedIp

                    remoteSocket = Socket(targetIp, port)
                    remoteSocket.soTimeout = 15000
                    val remoteOut = remoteSocket.getOutputStream()
                    val remoteIn = remoteSocket.getInputStream()

                    // 重构 HTTP 请求行并转发
                    val newReqLine = "$method $path ${parts.getOrNull(2) ?: "HTTP/1.1"}\r\n"
                    remoteOut.write(newReqLine.toByteArray(Charsets.US_ASCII))
                    for (h in headers) {
                        remoteOut.write("$h\r\n".toByteArray(Charsets.US_ASCII))
                    }
                    remoteOut.write("\r\n".toByteArray(Charsets.US_ASCII))
                    remoteOut.flush()

                    // 响应直接回写给客户端
                    pipe(remoteIn, output)
                }
            }

        } catch (_: Exception) {
        } finally {
            try { clientSocket.close() } catch (_: Exception) {}
            try { remoteSocket?.close() } catch (_: Exception) {}
        }
    }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        var b: Int
        while (input.read().also { b = it } != -1) {
            if (b == '\n'.code) {
                return sb.toString().trimEnd('\r')
            }
            sb.append(b.toChar())
        }
        return if (sb.isNotEmpty()) sb.toString().trimEnd('\r') else null
    }

    private fun pipe(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(8192)
        try {
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                output.write(buffer, 0, read)
                output.flush()
            }
        } catch (_: Exception) {}
    }
}
