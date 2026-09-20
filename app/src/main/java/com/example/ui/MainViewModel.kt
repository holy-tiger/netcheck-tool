package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.core.AppLanguage
import com.example.core.Base64Decoder
import com.example.core.NetworkEngine
import com.example.core.SystemUtils
import com.example.data.AppDatabase
import com.example.data.DiagnosticHistoryEntity
import com.example.network.ReportApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * AI 约束：
 * 状态类定义如下：
 * sealed class AppState {
 *     object Idle : AppState()
 *     data class Running(val trackingId: String, val progress: Float) : AppState()
 *     object Success : AppState()
 *     data class Failed(val reportJsonStr: String) : AppState()
 * }
 */
sealed class AppState {
    object Idle : AppState()
    data class Running(val trackingId: String, val progress: Float) : AppState()
    object Success : AppState()
    data class Failed(val reportJsonStr: String) : AppState()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val reportDao = db.reportDao()

    private val _appState = MutableStateFlow<AppState>(AppState.Idle)
    val appState: StateFlow<AppState> = _appState.asStateFlow()

    private val _currentTrackingId = MutableStateFlow<String>("")
    val currentTrackingId: StateFlow<String> = _currentTrackingId.asStateFlow()

    private val _statusMessage = MutableStateFlow<String>("")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _lastGeneratedJson = MutableStateFlow<String>("")
    val lastGeneratedJson: StateFlow<String> = _lastGeneratedJson.asStateFlow()

    private val _currentLanguage = MutableStateFlow(AppLanguage.getSavedLanguage(application))
    val currentLanguage: StateFlow<AppLanguage> = _currentLanguage.asStateFlow()

    fun setLanguage(language: AppLanguage) {
        AppLanguage.saveLanguage(getApplication(), language)
        _currentLanguage.value = language
    }

    private fun getLocalizedContext(context: Context = getApplication()): Context {
        return AppLanguage.createLocalizedContext(context, _currentLanguage.value)
    }

    // Room Database recent 5 diagnostic reports
    val recentHistory: StateFlow<List<DiagnosticHistoryEntity>> = reportDao.getRecentReports()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun resetToIdle() {
        _appState.value = AppState.Idle
        _statusMessage.value = ""
    }

    /**
     * 一键全网体检：自动综合检测 Ping、DNS、HTTP 网页及 TCP 端口连通性
     */
    fun startQuickDiagnosis() {
        val quickJson = JSONObject().apply {
            put("report_url", "https://ais-dev-j3j2ubcrkh3fzla4fgtffc-690261017235.us-east1.run.app/api/reports")
            put("timeout_ms", 15000)
            put("tasks", JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "ping")
                    put("targets", JSONArray().apply {
                        put("8.8.8.8")
                        put("1.1.1.1")
                    })
                })
                put(JSONObject().apply {
                    put("type", "dns")
                    put("targets", JSONArray().apply {
                        put("google.com")
                        put("cloudflare.com")
                    })
                })
                put(JSONObject().apply {
                    put("type", "http")
                    put("targets", JSONArray().apply {
                        put("https://www.google.com")
                    })
                })
                put(JSONObject().apply {
                    put("type", "tcp")
                    put("targets", JSONArray().apply {
                        put("8.8.8.8:53")
                        put("1.1.1.1:443")
                    })
                })
                put(JSONObject().apply {
                    put("type", "speed")
                    put("targets", JSONArray().apply {
                        put("https://speed.cloudflare.com/__down?bytes=5000000")
                    })
                })
            })
        }
        val base64 = android.util.Base64.encodeToString(
            quickJson.toString().toByteArray(Charsets.UTF_8),
            android.util.Base64.NO_WRAP
        )
        startDiagnosis(base64)
    }

    /**
     * AI 约束：所有网络测试必须在 Dispatchers.IO 执行
     */
    fun startDiagnosis(rawBase64: String) {
        if (_appState.value is AppState.Running) return

        val trackingId = SystemUtils.generateTrackingId()
        _currentTrackingId.value = trackingId
        _appState.value = AppState.Running(trackingId = trackingId, progress = 0.05f)
        val lContext = getLocalizedContext()
        _statusMessage.value = lContext.getString(R.string.step_decoding)

        viewModelScope.launch(Dispatchers.IO) {
            var reportJsonString = ""
            var targetReportUrl = ""
            try {
                // 1. Base64 容错解码
                val decodedJsonStr = Base64Decoder.decode(rawBase64)
                val commandJson = JSONObject(decodedJsonStr)

                targetReportUrl = commandJson.optString("report_url", "").trim()
                val timeoutMs = commandJson.optLong("timeout_ms", 20000L)
                val tasksArray = commandJson.optJSONArray("tasks") ?: JSONArray()

                // 2. 收集设备底噪与网络环境
                _appState.value = AppState.Running(trackingId = trackingId, progress = 0.15f)
                _statusMessage.value = lContext.getString(R.string.running_waiting_hint)

                val deviceInfo = SystemUtils.getDeviceInfo()
                val networkEnv = SystemUtils.getNetworkEnv(getApplication())

                // 3. 构建待执行任务队列
                data class TaskItem(val type: String, val target: String)
                val taskQueue = mutableListOf<TaskItem>()

                for (i in 0 until tasksArray.length()) {
                    val taskObj = tasksArray.optJSONObject(i) ?: continue
                    val type = taskObj.optString("type", "ping").lowercase()
                    val targetsArray = taskObj.optJSONArray("targets") ?: JSONArray()
                    for (j in 0 until targetsArray.length()) {
                        val target = targetsArray.optString(j, "").trim()
                        if (target.isNotEmpty()) {
                            taskQueue.add(TaskItem(type, target))
                        }
                    }
                }

                if (taskQueue.isEmpty()) {
                    // Fallback default target if none provided
                    taskQueue.add(TaskItem("ping", "8.8.8.8"))
                    taskQueue.add(TaskItem("dns", "google.com"))
                }

                // 4. 逐项执行网络测试 (全部在 Dispatchers.IO)
                val resultsList = JSONArray()
                val totalTasks = taskQueue.size

                for ((index, item) in taskQueue.withIndex()) {
                    val currentProgress = 0.2f + 0.65f * (index.toFloat() / totalTasks)
                    _appState.value = AppState.Running(trackingId = trackingId, progress = currentProgress)

                    val resultObj = JSONObject()
                    val stepTarget = "${item.target} (${index + 1}/$totalTasks)"
                    when (item.type) {
                        "ping" -> {
                            _statusMessage.value = lContext.getString(R.string.step_ping, stepTarget)
                            val pingRes = NetworkEngine.executePing(item.target, timeoutMs)
                            resultObj.put("task", pingRes.task)
                            resultObj.put("status", pingRes.status)
                            resultObj.put("raw_log", pingRes.raw_log)
                        }
                        "dns" -> {
                            _statusMessage.value = lContext.getString(R.string.step_dns, stepTarget)
                            val dnsRes = NetworkEngine.executeDns(item.target)
                            resultObj.put("task", dnsRes.task)
                            resultObj.put("status", dnsRes.status)
                            resultObj.put("raw_log", dnsRes.raw_log)
                        }
                        "http" -> {
                            _statusMessage.value = lContext.getString(R.string.step_http, stepTarget)
                            val httpRes = NetworkEngine.executeHttp(item.target, timeoutMs)
                            resultObj.put("task", httpRes.task)
                            resultObj.put("status", httpRes.status)
                            resultObj.put("raw_log", httpRes.raw_log)
                        }
                        "tcp" -> {
                            _statusMessage.value = lContext.getString(R.string.step_tcp, stepTarget)
                            val tcpRes = NetworkEngine.executeTcp(item.target, timeoutMs)
                            resultObj.put("task", tcpRes.task)
                            resultObj.put("status", tcpRes.status)
                            resultObj.put("raw_log", tcpRes.raw_log)
                        }
                        "speed", "download" -> {
                            _statusMessage.value = lContext.getString(R.string.step_speed, stepTarget)
                            val speedRes = NetworkEngine.executeSpeedTest(item.target, timeoutMs)
                            resultObj.put("task", speedRes.task)
                            resultObj.put("status", speedRes.status)
                            resultObj.put("raw_log", speedRes.raw_log)
                        }
                        else -> {
                            _statusMessage.value = lContext.getString(R.string.step_ping, stepTarget)
                            val pingRes = NetworkEngine.executePing(item.target, timeoutMs)
                            resultObj.put("task", pingRes.task)
                            resultObj.put("status", pingRes.status)
                            resultObj.put("raw_log", pingRes.raw_log)
                        }
                    }
                    resultsList.put(resultObj)
                }

                // 5. 组装客户端上报的完整 JSON
                val payloadJson = JSONObject()
                payloadJson.put("tracking_id", trackingId)

                val devJson = JSONObject()
                deviceInfo.forEach { (k, v) -> devJson.put(k, v) }
                payloadJson.put("device_info", devJson)

                val netJson = JSONObject()
                networkEnv.forEach { (k, v) -> netJson.put(k, v) }
                payloadJson.put("network_env", netJson)

                payloadJson.put("results", resultsList)

                reportJsonString = payloadJson.toString(2)
                _lastGeneratedJson.value = reportJsonString

                // 6. 上报至 Web 接口
                _appState.value = AppState.Running(trackingId = trackingId, progress = 0.9f)
                _statusMessage.value = lContext.getString(R.string.step_reporting)

                var uploadSuccess = false
                if (targetReportUrl.isNotEmpty()) {
                    // Try original target URL
                    uploadSuccess = tryUpload(targetReportUrl, reportJsonString)

                    // If failed and original was localhost / 127.0.0.1, try Android emulator loopback 10.0.2.2
                    if (!uploadSuccess && (targetReportUrl.contains("localhost") || targetReportUrl.contains("127.0.0.1"))) {
                        val emulatorUrl = targetReportUrl
                            .replace("localhost", "10.0.2.2")
                            .replace("127.0.0.1", "10.0.2.2")
                        uploadSuccess = tryUpload(emulatorUrl, reportJsonString)
                    }
                }

                // 7. 保存到本地 Room 数据库 (保存最近5条历史)
                val statusString = if (uploadSuccess) "SUCCESS" else "FAILED"
                reportDao.insertReport(
                    DiagnosticHistoryEntity(
                        trackingId = trackingId,
                        timestamp = System.currentTimeMillis(),
                        status = statusString,
                        summary = "执行 ${resultsList.length()} 项测试 (${networkEnv["type"]})",
                        reportUrl = targetReportUrl,
                        reportJson = reportJsonString
                    )
                )
                reportDao.trimOldReports()

                // 8. 状态转移
                if (uploadSuccess) {
                    _appState.value = AppState.Success
                    _statusMessage.value = "诊断成功，已自动同步到客服后台！"
                } else {
                    _appState.value = AppState.Failed(reportJsonString)
                    _statusMessage.value = "上报接口连接失败，已生成离线报告！"
                }

            } catch (e: Exception) {
                val errorMsg = e.message ?: "未知异常"
                val fallbackJson = JSONObject().apply {
                    put("tracking_id", trackingId)
                    put("error", errorMsg)
                    put("raw_data", reportJsonString)
                }.toString(2)

                _lastGeneratedJson.value = fallbackJson

                reportDao.insertReport(
                    DiagnosticHistoryEntity(
                        trackingId = trackingId,
                        timestamp = System.currentTimeMillis(),
                        status = "FAILED",
                        summary = "诊断异常中断: $errorMsg",
                        reportUrl = targetReportUrl,
                        reportJson = fallbackJson
                    )
                )
                reportDao.trimOldReports()

                _appState.value = AppState.Failed(fallbackJson)
                _statusMessage.value = "执行中断: $errorMsg"
            }
        }
    }

    private suspend fun tryUpload(url: String, jsonBodyStr: String): Boolean {
        return try {
            val body = ReportApi.createJsonBody(jsonBodyStr)
            val response = ReportApi.get().uploadReport(url = url, body = body)
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 将诊断日志保存到设备的「下载」目录 (Downloads)，无需调起系统分享
     */
    fun saveReportToFile(
        context: Context,
        reportJsonStr: String,
        trackingId: String? = null,
        onComplete: ((Boolean, String) -> Unit)? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val actualId = trackingId?.ifEmpty { null } ?: _currentTrackingId.value.ifEmpty { "UNKNOWN" }
            val fileName = "diagnostic_report_${actualId}.txt"
            var savedPath = ""
            var isSuccess = false

            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    val resolver = context.contentResolver
                    val contentValues = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                        put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                    }
                    val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    if (uri != null) {
                        resolver.openOutputStream(uri)?.use { os ->
                            os.write(reportJsonStr.toByteArray(Charsets.UTF_8))
                        }
                        savedPath = "系统「下载」目录 / $fileName"
                        isSuccess = true
                    }
                }

                if (!isSuccess) {
                    val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                    if (!downloadsDir.exists()) {
                        downloadsDir.mkdirs()
                    }
                    val targetFile = File(downloadsDir, fileName)
                    targetFile.writeText(reportJsonStr, Charsets.UTF_8)
                    savedPath = targetFile.absolutePath
                    isSuccess = true
                }
            } catch (e: Exception) {
                try {
                    val fallbackDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
                    val targetFile = File(fallbackDir, fileName)
                    targetFile.writeText(reportJsonStr, Charsets.UTF_8)
                    savedPath = targetFile.absolutePath
                    isSuccess = true
                } catch (e2: Exception) {
                    savedPath = e2.message ?: "写入文件失败"
                    isSuccess = false
                }
            }

            withContext(Dispatchers.Main) {
                val lContext = getLocalizedContext(context)
                if (isSuccess) {
                    android.widget.Toast.makeText(
                        context,
                        lContext.getString(R.string.toast_saved_to, savedPath),
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                } else {
                    android.widget.Toast.makeText(
                        context,
                        lContext.getString(R.string.toast_save_failed, savedPath),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
                onComplete?.invoke(isSuccess, savedPath)
            }
        }
    }
}
