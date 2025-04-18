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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.Observer
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 步数跟踪服务
 *
 * 提供常驻通知栏显示当前步数情况
 * 定期从Health Connect读取步数数据并更新通知
 */
class StepsTrackingService : Service() {

    companion object {
        private const val TAG = "StepsTrackingService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "steps_tracking_channel"
        private const val CHANNEL_NAME = "步数跟踪"
        private const val UPDATE_INTERVAL = 5000L // 5秒更新一次

        // 启动服务的便捷方法
        fun startService(context: Context) {
            val intent = Intent(context, StepsTrackingService::class.java)
            context.startService(intent)
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
    private var updateJob: Job? = null
    private lateinit var stepsRepository: StepsRepository
    private var stepsObserver: Observer<Int>? = null

    @SuppressLint("ForegroundServiceType")
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "服务创建")

        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()

        // 初始化仓库
        stepsRepository = StepsRepository.getInstance(this)

        // 观察仓库数据变化
        observeRepositoryData()

        // 创建初始通知
        val notification = createNotification(0, goalSteps)
        startForeground(NOTIFICATION_ID, notification)

        // 开始定期更新步数
        startStepsUpdates()
    }

    // 观察仓库数据变化
    private fun observeRepositoryData() {
        // 创建观察者并保存引用
        stepsObserver = Observer<Int> { steps -> // 更新通知
            currentSteps = steps
            val notification = createNotification(currentSteps, goalSteps)
            notificationManager.notify(NOTIFICATION_ID, notification)
            Log.d(TAG, "通知已更新，新步数: $steps")
        }

        // 获取仓库中的LiveData并添加观察者
        stepsObserver?.let {
            stepsRepository.todaySteps.observeForever(it)
        }
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
            .setSmallIcon(android.R.drawable.ic_menu_directions) // 使用系统内置图标作为临时替代
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
        // 取消之前的任务
        updateJob?.cancel()

        // 创建新的更新任务
        updateJob = kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            while (true) {
                try {
                    Log.d(TAG, "尝试更新步数数据...")
                    updateStepsFromHealthConnect()
                } catch (e: Exception) {
                    Log.e(TAG, "更新步数失败: ${e.message}", e)
                }
                delay(UPDATE_INTERVAL)
            }
        }
    }

    private fun updateStepsFromHealthConnect() {
        try {
            stepsRepository.refreshStepsData()
            Log.d(TAG, "通过仓库更新步数数据")
        } catch (e: Exception) {
            Log.e(TAG, "刷新步数数据失败: ${e.message}")
            throw e
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "服务onStartCommand被调用")

        // 如果更新任务未启动，则启动它
        if (updateJob == null || updateJob?.isActive == false) {
            Log.d(TAG, "更新任务未运行，重新启动...")
            startStepsUpdates()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "服务销毁")

        // 安全移除观察者
        stepsObserver?.let {
            stepsRepository.todaySteps.removeObserver(it)
        }
        stepsObserver = null

        // 取消更新任务
        updateJob?.cancel()
        updateJob = null
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}