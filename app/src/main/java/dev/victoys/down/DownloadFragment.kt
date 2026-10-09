package dev.victoys.down

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment

/** 左页：输入链接 → 下载 */
class DownloadFragment : Fragment(R.layout.fragment_download) {

    private val handler = Handler(Looper.getMainLooper())
    private var polling = false

    private var statusText: TextView? = null
    private var input: EditText? = null

    private val pollTask = object : Runnable {
        override fun run() {
            refresh()
            if (polling) handler.postDelayed(this, 1500)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        input = view.findViewById(R.id.inputUrl)
        statusText = view.findViewById(R.id.textStatus)
        val button = view.findViewById<Button>(R.id.btnDownload)

        button.setOnClickListener {
            val url = input?.text?.toString()?.trim().orEmpty()
            if (url.isEmpty()) {
                Toast.makeText(requireContext(), "请先粘贴下载链接", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            statusText?.text = "正在创建任务…"
            Thread {
                try {
                    val id = GopeedClient.createTask(url)
                    post { statusText?.text = "已添加任务 ${id.take(8)}" }
                } catch (e: Throwable) {
                    post { statusText?.text = "创建失败：${e.message ?: e.toString()}" }
                }
            }.start()
        }
    }

    override fun onResume() {
        super.onResume()
        polling = true
        handler.post(pollTask)
    }

    override fun onPause() {
        polling = false
        handler.removeCallbacks(pollTask)
        super.onPause()
    }

    override fun onDestroyView() {
        polling = false
        handler.removeCallbacks(pollTask)
        statusText = null
        input = null
        super.onDestroyView()
    }

    private fun post(block: () -> Unit) {
        if (isAdded) activity?.runOnUiThread(block)
    }

    private fun refresh() {
        Thread {
            val text = buildStatusText()
            post { statusText?.text = text }
        }.start()
    }

    private fun buildStatusText(): String {
        if (!GopeedService.isReady()) {
            return "正在启动下载核心…\n${GopeedService.lastError ?: ""}".trim()
        }

        val tasks = GopeedClient.tasks()
        if (tasks.isEmpty()) return "暂无任务，粘贴链接即可开始下载"

        var running = 0
        var waiting = 0
        var done = 0
        var failed = 0
        var paused = 0

        for (task in tasks) {
            when (task.optString("status")) {
                "running" -> running++
                "wait", "ready" -> waiting++
                "done" -> done++
                "error" -> failed++
                "pause" -> paused++
            }
        }

        val builder = StringBuilder()
        builder.append("共 ${tasks.size} 个任务")
        builder.append(" · 下载中 $running · 等待 $waiting · 暂停 $paused · 完成 $done · 失败 $failed")

        val active = tasks.filter { it.optString("status") != "done" }.take(3)
        for (task in active) {
            val id = task.optString("id")
            val name = task.optJSONObject("meta")
                ?.optJSONObject("res")
                ?.optString("name")
                ?.takeIf { it.isNotBlank() }
                ?: (task.optString("protocol") + " 任务")

            val status = task.optString("status")
            val st = GopeedClient.taskStatus(id)
            val downloaded = st?.optLong("downloaded") ?: 0L
            val total = st?.optLong("total") ?: 0L
            val speed = st?.optLong("speed") ?: 0L

            val percent = if (total > 0) (downloaded * 100 / total) else 0
            builder.append("\n\n● ").append(name)
                .append("\n  ").append(statusText(status))
            if (total > 0) {
                builder.append(" · ").append(percent).append("%")
                    .append(" · ").append(formatSize(speed)).append("/s")
                    .append("\n  ").append(formatSize(downloaded)).append(" / ").append(formatSize(total))
            }
        }
        return builder.toString()
    }

    private fun statusText(status: String): String = when (status) {
        "running" -> "下载中"
        "pause" -> "已暂停"
        "wait", "ready" -> "排队中"
        "error" -> "出错"
        "done" -> "已完成"
        else -> status
    }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var index = 0
        while (value >= 1024 && index < units.lastIndex) {
            value /= 1024
            index++
        }
        return if (index == 0) "$bytes B" else String.format("%.1f %s", value, units[index])
    }
}
