package dev.victoys.down

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import libgopeed.Libgopeed

/**
 * 前台服务：在这里启动 gopeed 的 Go 下载核心。
 * 核心启动后监听 127.0.0.1 的随机端口，App 通过 [GopeedClient] 与之通信。
 */
class GopeedService : Service() {

    companion object {
        private const val TAG = "GopeedService"
        private const val CHANNEL_ID = "gopeed_download"
        private const val NOTIFY_ID = 1001

        /** 默认下载目录 */
        const val DOWNLOAD_DIR = "/storage/emulated/0/Download"

        /** Go 核心 REST 端口，0 表示尚未启动 */
        @Volatile
        var port: Int = 0
            private set

        /** 核心启动失败的原因 */
        @Volatile
        var lastError: String? = null
            private set

        fun start(context: Context) {
            val intent = Intent(context, GopeedService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFY_ID, buildNotification())

        Thread {
            try {
                val p = Libgopeed.start(filesDir.absolutePath, DOWNLOAD_DIR)
                port = p.toInt()
                Log.i(TAG, "gopeed core started, port=$port")
            } catch (t: Throwable) {
                lastError = t.message ?: t.toString()
                Log.e(TAG, "failed to start gopeed core", t)
            }
        }.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        try {
            Libgopeed.stop()
        } catch (t: Throwable) {
            Log.w(TAG, "stop core failed", t)
        }
        port = 0
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "下载服务",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "保持下载核心在后台运行" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }.setContentTitle("下载服务运行中")
            .setContentText("Gopeed 下载核心正在后台工作")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .build()
    }
}
