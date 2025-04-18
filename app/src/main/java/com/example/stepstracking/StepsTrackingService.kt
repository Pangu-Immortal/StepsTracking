package com.example.stepstracking

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StepsTrackingService : Service(), SensorEventListener {
    companion object {
        private const val TAG = "StepsTrackingService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "steps_tracking_channel"
        private const val CHANNEL_NAME = "步数跟踪"
        private const val UPDATE_INTERVAL = 3 * 1000L
        fun startService(context: Context) {
            val intent = Intent(context, StepsTrackingService::class.java)
            context.startService(intent)
        }
        fun stopService(context: Context) {
            val intent = Intent(context, StepsTrackingService::class.java)
            context.stopService(intent)
        }
    }

    private lateinit var notificationManager: NotificationManager
    private val handler = Handler(Looper.getMainLooper())
    private var isUpdateTaskActive = false
    private lateinit var sensorManager: SensorManager
    private var stepCounterSensor: Sensor? = null
    private var initialSteps: Float = 0f
    private var currentSteps: Float = 0f
    private var isFirstSensorReading = true
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private val updateRunnable = object : Runnable {
        override fun run() {
            Log.d(TAG, "定时更新步数...")
            serviceScope.launch {
                try {
                    updateNotificationFromRepository()
                } catch (e: Exception) {
                    Log.e(TAG, "更新步数失败: ${e.message}", e)
                }
            }
            handler.postDelayed(this, UPDATE_INTERVAL)
            isUpdateTaskActive = true
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "服务创建")
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        initializeSensorManager()
        val stepsRepository = StepsRepository.getInstance(this)
        val initialSteps = stepsRepository.todaySteps.value ?: 0
        val goalSteps = stepsRepository.goalSteps.value ?: 6000
        val notification = createNotification(initialSteps, goalSteps)
        startForeground(NOTIFICATION_ID, notification)
        handler.postDelayed(updateRunnable, UPDATE_INTERVAL)
        isUpdateTaskActive = true
    }

    private fun initializeSensorManager() {
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
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
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(steps: Int, goal: Int): Notification {
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
        val progress = ((steps.toFloat() / goal) * 100).toInt().coerceIn(0, 100)
        val calories = (steps * 0.04).toFloat()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_directions)
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

    private suspend fun updateNotificationFromRepository() {
        Log.d(TAG, "从仓库获取步数数据并更新通知")
        val stepsRepository = StepsRepository.getInstance(this)
        val currentSteps = stepsRepository.todaySteps.value ?: 0
        val goalSteps = stepsRepository.goalSteps.value ?: 6000
        Log.d(TAG, "仓库中的当前步数: $currentSteps")
        withContext(Dispatchers.Main) {
            val notification = createNotification(currentSteps, goalSteps)
            notificationManager.notify(NOTIFICATION_ID, notification)
            Log.d(TAG, "通知已更新: 步数=$currentSteps")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "服务onStartCommand被调用")
        val isCallbackActive = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            handler.hasCallbacks(updateRunnable)
        } else {
            isUpdateTaskActive
        }
        if (!isCallbackActive) {
            handler.postDelayed(updateRunnable, UPDATE_INTERVAL)
            isUpdateTaskActive = true
        }
        if (stepCounterSensor != null && !isListenerRegistered()) {
            sensorManager.registerListener(this, stepCounterSensor, SensorManager.SENSOR_DELAY_UI)
            Log.d(TAG, "重新注册步数传感器监听器")
        }
        return START_STICKY
    }

    private fun isListenerRegistered(): Boolean {
        return try {
            sensorManager.getSensorList(Sensor.TYPE_ALL).isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "服务销毁")
        handler.removeCallbacks(updateRunnable)
        isUpdateTaskActive = false
        serviceScope.cancel()
        sensorManager.unregisterListener(this)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_STEP_COUNTER) return
        val steps = event.values[0]
        Log.d(TAG, "传感器步数更新: $steps")
        if (isFirstSensorReading) {
            initialSteps = steps
            isFirstSensorReading = false
            Log.d(TAG, "初始步数设置为: $initialSteps")
        }
        currentSteps = steps - initialSteps
        if (currentSteps < 0) currentSteps = 0f
        val stepsRepository = StepsRepository.getInstance(this)
        stepsRepository.updateSensorSteps(currentSteps.toInt())
        Log.d(TAG, "更新后的步数: ${currentSteps.toInt()}")
        serviceScope.launch {
            updateNotificationFromRepository()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}