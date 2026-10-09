package dev.victoys.down

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 对 gopeed 本地 REST 接口的极简封装。
 * 接口路径取自 gopeed 源码 pkg/api/service.go：
 *   POST   /api/v1/tasks              创建任务
 *   GET    /api/v1/tasks              任务列表
 *   GET    /api/v1/tasks/{id}/status  任务实时进度
 *   POST   /api/v1/tasks/{id}/pause|continue
 *   DELETE /api/v1/tasks/{id}?force=true
 */
object GopeedClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

    fun isReady(): Boolean = GopeedService.port > 0

    private fun baseUrl(): String = "http://127.0.0.1:${GopeedService.port}"

    /** gopeed 统一响应：{code, msg, data} */
    private fun call(request: Request): JSONObject? {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string().orEmpty()
            if (body.isEmpty()) return null
            return JSONObject(body)
        }
    }

    /**
     * 创建下载任务。http/https、magnet、.torrent、ed2k 等链接由 gopeed 自动识别协议。
     * @return 任务 ID
     */
    fun createTask(url: String): String {
        if (!isReady()) error(GopeedService.lastError ?: "下载核心尚未启动")

        val payload = JSONObject().apply {
            put("req", JSONObject().apply { put("url", url) })
            put("opts", JSONObject().apply {
                put("path", GopeedService.DOWNLOAD_DIR)
                put("asDefaultPath", true)
            })
        }.toString()

        val request = Request.Builder()
            .url("${baseUrl()}/api/v1/tasks")
            .post(payload.toRequestBody(JSON_TYPE))
            .build()

        val obj = call(request) ?: error("核心无响应")
        val code = obj.optInt("code", -1)
        if (code != 0) error(obj.optString("msg", "创建任务失败"))
        return obj.optString("data")
    }

    /** 任务列表，元素为 gopeed 的 Task JSON 对象 */
    fun tasks(): List<JSONObject> {
        if (!isReady()) return emptyList()

        val request = Request.Builder()
            .url("${baseUrl()}/api/v1/tasks")
            .get()
            .build()

        val obj = call(request) ?: return emptyList()
        if (obj.optInt("code", -1) != 0) return emptyList()
        val array: JSONArray = obj.optJSONArray("data") ?: return emptyList()

        val result = ArrayList<JSONObject>(array.length())
        for (i in 0 until array.length()) {
            result.add(array.optJSONObject(i) ?: continue)
        }
        return result
    }

    /**
     * 单个任务的实时进度：{status, used, speed, downloaded, total, ...}
     */
    fun taskStatus(id: String): JSONObject? {
        if (!isReady()) return null
        val request = Request.Builder()
            .url("${baseUrl()}/api/v1/tasks/$id/status")
            .get()
            .build()
        val obj = call(request) ?: return null
        if (obj.optInt("code", -1) != 0) return null
        return obj.optJSONObject("data")
    }

    fun pause(id: String) = post("/api/v1/tasks/$id/pause")

    fun resume(id: String) = post("/api/v1/tasks/$id/continue")

    private fun post(path: String) {
        if (!isReady()) return
        val request = Request.Builder()
            .url("${baseUrl()}$path")
            .post("".toRequestBody(JSON_TYPE))
            .build()
        runCatching { call(request) }
    }
}
