package com.example.stepstracking

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 步数跟踪服务
 *
 * 提供常驻通知栏显示当前步数情况
 * 定期从Health Connect读取步数数据并更新通知
 */
class StepsTrackingService : LifecycleService() {

    companion object {
        private const val TAG = "StepsTrackingService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "steps_tracking_channel"
        private const val CHANNEL_NAME = "步数跟踪"
        private const val UPDATE_INTERVAL = 5000L // 5秒更新一次

        // 启动服务的便捷方法
        fun startService(context: Context) {
            val intent = Intent(context, StepsTrackingService::class.java)
            // 对于所有版本都使用普通 startService
            context.startService(intent)
            // 不再区分版本使用 startForegroundService，因为可能导致兼容性问题
        }

        // 停止服务的便捷方法
        fun stopService(context: Context) {
            val intent = Intent(context, StepsTrackingService::class.java)
            context.stopService(intent)
        }
    }

    private lateinit var notificationManager: NotificationManager
    private var currentSteps = 0
    private var goalSteps = 6000 // 默认目标步数

    @SuppressLint("ForegroundServiceType")
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "服务创建")

        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()

        // 创建初始通知
        val notification = createNotification(0, goalSteps)
        startForeground(NOTIFICATION_ID, notification)

        // 开始定期更新步数
        startStepsUpdates()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "显示步数跟踪信息"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(steps: Int, goal: Int): android.app.Notification {
        // 创建点击通知时打开的Intent
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        // 计算进度百分比
        val progress = ((steps.toFloat() / goal) * 100).toInt().coerceIn(0, 100)

        // 计算卡路里（简单估算：每1000步约消耗40千卡）
        val calories = (steps * 0.04).toFloat()

        // 构建通知
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // 请替换为您的步数图标
            .setContentTitle("远足沙发")
            .setContentText("还剩 ${goal - steps} 步")
            .setProgress(100, progress, false)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("${steps}/6000 步\n每日平均值：${steps}\n\n卡路里：${String.format("%.1f", calories)}"))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun startStepsUpdates() {
        lifecycleScope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    updateStepsFromHealthConnect()
                } catch (e: Exception) {
                    Log.e(TAG, "更新步数失败", e)
                }
                delay(UPDATE_INTERVAL)
            }
        }
    }

    private suspend fun updateStepsFromHealthConnect() {
        try {
            val client = HealthConnectClient.getOrCreate(this)

            // 获取今天的时间范围
            val today = java.time.LocalDate.now()
            val startTime = today.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant()
            val endTime = LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant()

            // 请求今天的步数记录
            val request = ReadRecordsRequest(
                recordType = StepsRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )

            val response = client.readRecords(request)

            // 计算总步数
            val totalSteps = response.records.sumOf { it.count.toInt() }

            if (totalSteps != currentSteps) {
                currentSteps = totalSteps

                // 更新通知
                withContext(Dispatchers.Main) {
                    val notification = createNotification(currentSteps, goalSteps)
                    notificationManager.notify(NOTIFICATION_ID, notification)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "从Health Connect读取步数失败", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "服务销毁")
    }
}