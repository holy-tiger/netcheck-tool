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
}
