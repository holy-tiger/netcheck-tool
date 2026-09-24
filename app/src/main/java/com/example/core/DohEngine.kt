package com.example.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.util.Locale

data class DohQueryResult(
    val serverUrl: String,
    val domain: String,
    val ips: List<String>,
    val durationMs: Long,
    val httpStatus: Int,
    val protocol: String,
    val rawLog: String,
    val isSuccess: Boolean
)

object DohEngine {

    /**
     * 构建标准 RFC 1035 / RFC 8484 二进制 DNS 查询数据包 (Type A)
     */
    fun buildDnsQueryPacket(domain: String, queryType: Int = 1): ByteArray {
        val cleanDomain = domain.trim().trimEnd('.')
        val parts = cleanDomain.split('.')
        val out = ByteArrayOutputStream()
        val dos = DataOutputStream(out)

        // Header (12 bytes)
        dos.writeShort(0x1a2b) // ID
        dos.writeShort(0x0100) // Flags: Standard query, RD=1
        dos.writeShort(1)      // QDCOUNT: 1 question
        dos.writeShort(0)      // ANCOUNT: 0
        dos.writeShort(0)      // NSCOUNT: 0
        dos.writeShort(0)      // ARCOUNT: 0

        // Question: QNAME
        for (part in parts) {
            val bytes = part.toByteArray(Charsets.US_ASCII)
            dos.writeByte(bytes.size)
            dos.write(bytes)
        }
        dos.writeByte(0) // Null byte terminates QNAME

        // QTYPE (1 = A, 28 = AAAA)
        dos.writeShort(queryType)
        // QCLASS (1 = IN)
        dos.writeShort(1)

        dos.flush()
        return out.toByteArray()
    }

    /**
     * 解析 DNS 二进制应答数据包中的 IP 地址
     */
    fun parseDnsResponseIps(response: ByteArray): List<String> {
        val ips = mutableListOf<String>()
        if (response.size < 12) return ips

        try {
            val dis = DataInputStream(ByteArrayInputStream(response))
            val id = dis.readUnsignedShort()
            val flags = dis.readUnsignedShort()
            val qdcount = dis.readUnsignedShort()
            val ancount = dis.readUnsignedShort()
            val nscount = dis.readUnsignedShort()
            val arcount = dis.readUnsignedShort()

            var offset = 12
            // Skip questions
            for (i in 0 until qdcount) {
                while (offset < response.size) {
                    val len = response[offset].toInt() and 0xFF
                    if (len == 0) {
                        offset += 1
                        break
                    }
                    offset += 1 + len
                }
                offset += 4 // QTYPE (2) + QCLASS (2)
            }

            // Parse Answers
            for (i in 0 until ancount) {
                if (offset >= response.size) break
                val firstByte = response[offset].toInt() and 0xFF
                if (firstByte and 0xC0 == 0xC0) {
                    offset += 2 // Pointer is 2 bytes
                } else {
                    while (offset < response.size) {
                        val len = response[offset].toInt() and 0xFF
                        if (len == 0) {
                            offset += 1
                            break
                        }
                        offset += 1 + len
                    }
                }
                if (offset + 10 > response.size) break
                val type = ((response[offset].toInt() and 0xFF) shl 8) or (response[offset + 1].toInt() and 0xFF)
                offset += 2
                val clazz = ((response[offset].toInt() and 0xFF) shl 8) or (response[offset + 1].toInt() and 0xFF)
                offset += 2
                val ttl = ((response[offset].toInt() and 0xFF).toLong() shl 24) or
                        ((response[offset + 1].toInt() and 0xFF).toLong() shl 16) or
                        ((response[offset + 2].toInt() and 0xFF).toLong() shl 8) or
                        (response[offset + 3].toInt() and 0xFF).toLong()
                offset += 4
                val rdlength = ((response[offset].toInt() and 0xFF) shl 8) or (response[offset + 1].toInt() and 0xFF)
                offset += 2

                if (type == 1 && rdlength == 4 && offset + 4 <= response.size) {
                    val ip = "${response[offset].toInt() and 0xFF}.${response[offset + 1].toInt() and 0xFF}.${response[offset + 2].toInt() and 0xFF}.${response[offset + 3].toInt() and 0xFF}"
                    if (!ips.contains(ip)) ips.add(ip)
                } else if (type == 28 && rdlength == 16 && offset + 16 <= response.size) {
                    try {
                        val bytes = response.copyOfRange(offset, offset + 16)
                        val inet = InetAddress.getByAddress(bytes)
                        val hostAddr = inet.hostAddress
                        if (hostAddr != null && !ips.contains(hostAddr)) ips.add(hostAddr)
                    } catch (_: Exception) {}
                }
                offset += rdlength
            }
        } catch (_: Exception) {}

        return ips
    }

    /**
     * 发送 UDP 传统 DNS 查询 (用于自定义 DNS 服务器测试，如 8.8.8.8:53)
     */
    suspend fun queryUdpDns(dnsServer: String, domain: String, timeoutMs: Long = 4000L): List<String> =
        withContext(Dispatchers.IO) {
            val ips = mutableListOf<String>()
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket()
                socket.soTimeout = timeoutMs.toInt().coerceIn(1000, 10000)
                val targetAddr = InetAddress.getByName(dnsServer.trim())
                val packetData = buildDnsQueryPacket(domain, queryType = 1)
                val sendPacket = DatagramPacket(packetData, packetData.size, targetAddr, 53)
                socket.send(sendPacket)

                val buffer = ByteArray(2048)
                val receivePacket = DatagramPacket(buffer, buffer.size)
                socket.receive(receivePacket)

                val receivedData = buffer.copyOf(receivePacket.length)
                ips.addAll(parseDnsResponseIps(receivedData))
            } catch (_: Exception) {
            } finally {
                socket?.close()
            }
            ips
        }

    /**
     * 执行 DoH (DNS over HTTPS) 查询
     * 自动支持 RFC 8484 协议二进制 POST 与主流 JSON API 回退
     */
    suspend fun queryDoh(
        serverUrl: String,
        domain: String,
        timeoutMs: Long = 10000L
    ): DohQueryResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val cleanDomain = domain.trim()
        var cleanServerUrl = serverUrl.trim()
        if (!cleanServerUrl.startsWith("http://", ignoreCase = true) && !cleanServerUrl.startsWith("https://", ignoreCase = true)) {
            cleanServerUrl = "https://$cleanServerUrl"
        }

        val sbLog = StringBuilder()
        sbLog.append("=== DoH Resolution: ").append(cleanDomain).append(" ===\n")
        sbLog.append("DoH Server: ").append(cleanServerUrl).append("\n")

        val resolvedIps = mutableListOf<String>()
        var httpCode = -1
        var protocolStr = "HTTP/1.1"

        // 1. 尝试 RFC 8484 wire-format POST
        var wireSuccess = false
        try {
            val queryBytes = buildDnsQueryPacket(cleanDomain, queryType = 1)
            val url = URL(cleanServerUrl)
            val conn = url.openConnection() as HttpURLConnection
            val timeout = timeoutMs.toInt().coerceIn(1000, 20000)
            conn.connectTimeout = timeout
            conn.readTimeout = timeout
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/dns-message")
            conn.setRequestProperty("Accept", "application/dns-message, application/dns-json, application/json")
            conn.setRequestProperty("User-Agent", "NetCheck-DoHEngine/1.0")

            val connectStart = System.currentTimeMillis()
            conn.outputStream.use { os ->
                os.write(queryBytes)
                os.flush()
            }
            httpCode = conn.responseCode
            val connectTime = System.currentTimeMillis() - connectStart

            sbLog.append("Protocol Method: RFC 8484 POST (application/dns-message)\n")
            sbLog.append("HTTP Status: ").append(httpCode).append(" ").append(conn.responseMessage ?: "").append("\n")
            sbLog.append("Handshake Latency: ").append(connectTime).append(" ms\n")

            if (httpCode in 200..299) {
                val contentType = conn.contentType ?: ""
                val responseBytes = conn.inputStream.use { it.readBytes() }
                if (contentType.contains("application/dns-message", ignoreCase = true) || responseBytes.size > 12) {
                    val parsed = parseDnsResponseIps(responseBytes)
                    if (parsed.isNotEmpty()) {
                        resolvedIps.addAll(parsed)
                        wireSuccess = true
                    }
                }

                // 如果响应是 JSON，尝试 JSON 解析
                if (!wireSuccess && (contentType.contains("json", ignoreCase = true) || responseBytes.isNotEmpty())) {
                    val jsonStr = String(responseBytes, Charsets.UTF_8)
                    val jsonIps = parseJsonDnsResponse(jsonStr)
                    if (jsonIps.isNotEmpty()) {
                        resolvedIps.addAll(jsonIps)
                        wireSuccess = true
                    }
                }
            }
            conn.disconnect()
        } catch (e: Exception) {
            sbLog.append("[Wire-format Notice]: ").append(e.javaClass.simpleName).append(" - ").append(e.message).append("\n")
        }

        // 2. 如果 wire-format 未解析成功，尝试主流 DoH JSON API (如 Cloudflare, Google, AliDNS)
        if (!wireSuccess || resolvedIps.isEmpty()) {
            try {
                val separator = if (cleanServerUrl.contains("?")) "&" else "?"
                val jsonUrlStr = "$cleanServerUrl${separator}name=$cleanDomain&type=A"
                val url = URL(jsonUrlStr)
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = timeoutMs.toInt().coerceIn(1000, 15000)
                conn.readTimeout = timeoutMs.toInt().coerceIn(1000, 15000)
                conn.requestMethod = "GET"
                conn.setRequestProperty("Accept", "application/dns-json, application/json")
                conn.setRequestProperty("User-Agent", "NetCheck-DoHEngine/1.0")

                httpCode = conn.responseCode
                sbLog.append("Fallback to JSON API: ").append(jsonUrlStr).append("\n")
                sbLog.append("JSON API Status: ").append(httpCode).append("\n")

                if (httpCode in 200..299) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val jsonIps = parseJsonDnsResponse(body)
                    for (ip in jsonIps) {
                        if (!resolvedIps.contains(ip)) resolvedIps.add(ip)
                    }
                }
                conn.disconnect()
            } catch (e: Exception) {
                sbLog.append("[JSON API Exception]: ").append(e.javaClass.simpleName).append(" - ").append(e.message).append("\n")
            }
        }

        val totalDuration = System.currentTimeMillis() - startTime
        sbLog.append("Total Query Time: ").append(totalDuration).append(" ms\n")
        val isSuccess = resolvedIps.isNotEmpty()

        if (isSuccess) {
            sbLog.append("Successfully Resolved ").append(resolvedIps.size).append(" IP(s):\n")
            resolvedIps.forEachIndexed { idx, ip ->
                sbLog.append(" [").append(idx + 1).append("] ").append(ip).append("\n")
            }
            sbLog.append("[Result]: DoH Query Passed\n")
        } else {
            sbLog.append("[Result]: DoH Query Failed (No IP addresses resolved)\n")
        }

        DohQueryResult(
            serverUrl = cleanServerUrl,
            domain = cleanDomain,
            ips = resolvedIps,
            durationMs = totalDuration,
            httpStatus = httpCode,
            protocol = protocolStr,
            rawLog = sbLog.toString(),
            isSuccess = isSuccess
        )
    }

    private fun parseJsonDnsResponse(jsonStr: String): List<String> {
        val ips = mutableListOf<String>()
        try {
            val json = JSONObject(jsonStr)
            val answer = json.optJSONArray("Answer")
            if (answer != null) {
                for (i in 0 until answer.length()) {
                    val ansObj = answer.optJSONObject(i) ?: continue
                    val type = ansObj.optInt("type", 1)
                    val data = ansObj.optString("data", "").trim()
                    if ((type == 1 || type == 28) && data.isNotEmpty()) {
                        if (!ips.contains(data)) {
                            ips.add(data)
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return ips
    }
}
