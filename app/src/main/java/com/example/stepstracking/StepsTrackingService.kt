// app/src/main/java/com/example/stepstracking/StepsTrackingService.kt
package com.example.stepstracking

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Calendar
import java.util.concurrent.TimeUnit

class StepsTrackingService : Service(), SensorEventListener {

    companion object {
        private const val TAG = "StepsTrackingService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "steps_tracking_channel"
        private const val CHANNEL_NAME = "步数跟踪"
        private const val UPDATE_INTERVAL = 3 * 1000L // 3秒
        private const val CHECK_MIDNIGHT_INTERVAL = 60 * 1000L // 1分钟检查一次是否需要重置

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
    private val handler = Handler(Looper.getMainLooper())
    private var isUpdateTaskActive = false

    // 传感器相关
    private lateinit var sensorManager: SensorManager
    private var stepCounterSensor: Sensor? = null
    private var initialSteps: Int = 0
    private var currentSteps: Int = 0
    private var isFirstSensorReading = true

    // 创建协程作用域
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    // 定期更新任务
    private val updateRunnable = object : Runnable {
        override fun run() {
            Log.d(TAG, "定时更新步数...")

            // 在协程作用域中启动更新任务
            serviceScope.launch {
                try {
                    updateNotificationFromRepository()
                } catch (e: Exception) {
                    Log.e(TAG, "更新步数失败: ${e.message}", e)
                }
            }

            // 检查是否需要重置步数
            checkDailyReset()

            // 安排下一次更新
            handler.postDelayed(this, UPDATE_INTERVAL)
            isUpdateTaskActive = true
        }
    }

    // 检查午夜重置任务
    private val midnightCheckRunnable = object : Runnable {
        override fun run() {
            checkDailyReset()

            // 安排下一次检查
            handler.postDelayed(this, CHECK_MIDNIGHT_INTERVAL)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "服务创建")

        // 初始化通知管理器
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()

        // 初始化传感器管理器
        initializeSensorManager()

        // 创建初始通知
        val stepsRepository = StepsRepository.getInstance(this)
        val initialSteps = stepsRepository.todaySteps.value ?:
        0
        val goalSteps = stepsRepository.goalSteps.value ?: 6000
        val notification = createNotification(initialSteps, goalSteps)
        startForeground(NOTIFICATION_ID, notification)

        // 启动定期更新
        handler.postDelayed(updateRunnable, UPDATE_INTERVAL)
        isUpdateTaskActive = true

        // 启动午夜检查
        handler.postDelayed(midnightCheckRunnable, CHECK_MIDNIGHT_INTERVAL)
    }

    private fun initializeSensorManager() {
        // 初始化传感器管理器
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager

        // 访问步数计数器传感器
        stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

        // 注册步数计数器传感器的监听器
        stepCounterSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            Log.d(TAG, "步数传感器注册成功")
        } ?: run {
            Log.e(TAG, "设备不支持步数传感器")
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
                enableLights(true)
                lightColor = Color.GREEN
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    @SuppressLint("RemoteViewLayout")
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

        // 尝试加载自定义通知布局
        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setContentTitle("步数跟踪")
            .setContentText("今日步数: $steps 步 | 卡路里: ${String.format("%.1f", calories)}")
            .setProgress(100, progress, false)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        // 尝试加载自定义视图
        try {
            val collapsedView = RemoteViews(packageName, R.layout.notification_collapsed)
            collapsedView.setTextViewText(R.id.tv_notification_steps, "$steps")
            collapsedView.setTextViewText(R.id.tv_notification_calories, String.format("%.1f", calories))
            collapsedView.setProgressBar(R.id.progress_notification, 100, progress, false)
            notificationBuilder.setCustomContentView(collapsedView)

            // 只有在确认折叠视图能正确加载时才尝试加载展开视图
            val expandedView = RemoteViews(packageName, R.layout.notification_expanded)
            expandedView.setTextViewText(R.id.tv_notification_steps_expanded, "$steps")
            expandedView.setTextViewText(R.id.tv_notification_goal, "$goal")
            expandedView.setTextViewText(R.id.tv_notification_calories_expanded, String.format("%.1f", calories))
            expandedView.setProgressBar(R.id.progress_notification_expanded, 100, progress, false)
            notificationBuilder.setCustomBigContentView(expandedView)
        } catch (e: Exception) {
            // 如果自定义视图加载失败，使用基本通知
            Log.e(TAG, "自定义通知视图加载失败: ${e.message}")
        }

        return notificationBuilder.build()
    }

    /**
     * 检查是否需要日常重置
     */
    private fun checkDailyReset() {
        val stepsRepository = StepsRepository.getInstance(this)

        // 检查日期是否变更
        if (stepsRepository.shouldResetSteps()) {
            Log.d(TAG, "执行每日步数重置")
            isFirstSensorReading = true
            stepsRepository.resetSteps()
            stepsRepository.markStepsReset()
        }
    }

    /**
     * 直接从仓库获取最新步数并更新通知
     */
    private suspend fun updateNotificationFromRepository() {
        Log.d(TAG, "从仓库获取步数数据并更新通知")
        val stepsRepository = StepsRepository.getInstance(this)

        // 获取当前数据
        val currentSteps = stepsRepository.todaySteps.value ?: 0
        val goalSteps = stepsRepository.goalSteps.value ?: 6000

        Log.d(TAG, "仓库中的当前步数: $currentSteps")

        // 在主线程更新通知
        withContext(Dispatchers.Main) {
            val notification = createNotification(currentSteps, goalSteps)
            notificationManager.notify(NOTIFICATION_ID, notification)
            Log.d(TAG, "通知已更新: 步数=$currentSteps")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "服务onStartCommand被调用")

        // 检查定时任务是否在运行
        val isCallbackActive = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            handler.hasCallbacks(updateRunnable)
        } else {
            isUpdateTaskActive
        }

        // 如果定时任务不在运行，重新启动它
        if (!isCallbackActive) {
            handler.postDelayed(updateRunnable, UPDATE_INTERVAL)
            isUpdateTaskActive = true
        }

        // 确保传感器监听器已注册
        if (stepCounterSensor != null && !isListenerRegistered()) {
            sensorManager.registerListener(this, stepCounterSensor, SensorManager.SENSOR_DELAY_UI)
            Log.d(TAG, "重新注册步数传感器监听器")
        }

        return START_STICKY
    }

    // 检查传感器监听器是否已注册
    private fun isListenerRegistered(): Boolean {
        return try {
            // 这是一个简化的检查，并不完全准确
            // 在实际应用中，可能需要维护一个注册状态标志
            sensorManager.getSensorList(Sensor.TYPE_ALL).isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "服务销毁")

        // 移除定时任务
        handler.removeCallbacks(updateRunnable)
        handler.removeCallbacks(midnightCheckRunnable)
        isUpdateTaskActive = false

        // 取消所有协程
        serviceScope.cancel()

        // 取消传感器监听器
        sensorManager.unregisterListener(this)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_STEP_COUNTER) return

        val steps = event.values[0]
        Log.d(TAG, "传感器步数更新: $steps")

        // 首次读取时，初始化初始步数
        if (isFirstSensorReading) {
            initialSteps = steps.toInt()
            isFirstSensorReading = false
            Log.d(TAG, "初始步数设置为: $initialSteps")
        }

        // 计算相对步数
        currentSteps = (steps - initialSteps).toInt()
        if (currentSteps < 0) currentSteps = 0

        // 更新仓库
        val stepsRepository = StepsRepository.getInstance(this)
        stepsRepository.updateSensorSteps(currentSteps)
        Log.d(TAG, "更新后的步数: ${currentSteps}")

        // 更新通知 (可选，这会增加通知更新频率)
        serviceScope.launch {
            updateNotificationFromRepository()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // 此实现中不需要
    }
}