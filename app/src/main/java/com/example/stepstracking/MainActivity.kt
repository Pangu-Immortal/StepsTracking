package com.example.stepstracking

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.stepstracking.databinding.ActivityMainBinding
import java.text.DecimalFormat

class MainActivity : AppCompatActivity(), SensorEventListener {
    private val TAG = "MainActivity"
    private val mainViewModel: MainViewModel by viewModels()
    private lateinit var binding: ActivityMainBinding

    private lateinit var requestRecognitionPermissionLauncher: ActivityResultLauncher<String>

    private lateinit var sensorManager: SensorManager
    private var stepCounterSensor: Sensor? = null
    private lateinit var stepsRepository: StepsRepository

    // 步数目标
    private val stepsGoal = 6000

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 设置深色状态栏
        window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or
                android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

        // 初始化权限请求启动器
        requestRecognitionPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                // 权限授予
                Toast.makeText(this, "步数权限已授予", Toast.LENGTH_SHORT).show()
                initializeSensorManager()
            } else {
                // 权限拒绝
                Toast.makeText(this, "需要步数权限才能跟踪步数", Toast.LENGTH_SHORT).show()
            }
        }

        // 检查和请求权限
        checkAndRequestPermission()

        // 初始化仓库
        stepsRepository = StepsRepository.getInstance(this)

        // 观察仓库数据
        stepsRepository.todaySteps.observe(this) { steps ->
            Log.d(TAG, "todaySteps: $steps")
            updateStepsUI(steps)
        }

        stepsRepository.calories.observe(this) { calories ->
            val decimalFormat = DecimalFormat("0.0")
            binding.tvCaloriesCount.text = decimalFormat.format(calories)
        }


        // 观察总步数
        mainViewModel.totalSteps.observe(this) { steps ->
            Log.d(TAG, "totalSteps: $steps")
            updateStepsUI(steps.toInt())
        }

        // 设置点击事件
        binding.ctaReset.setOnClickListener {
            mainViewModel.resetInitialSteps()
            // 同时重置仓库数据
            stepsRepository.resetSteps()
        }

        binding.ctaHealth.setOnClickListener {
            startActivity(Intent(this, HealthStepsActivity::class.java))
        }
    }

    private fun updateStepsUI(steps: Int) {
        if (steps > 0) {
            // 同步到仓库
            stepsRepository.updateSensorSteps(mainViewModel.totalSteps.value?.toInt() ?: 0)
        }
        // 更新大数字步数显示
        binding.tvStepsCounter.text = steps.toString()

        // 更新顶部卡片步数
        binding.tvStepsCount.text = steps.toString()

        // 计算卡路里（简单估算：每1000步约消耗40千卡）
        val calories = steps * 0.04f
        val decimalFormat = DecimalFormat("0.0")
        binding.tvCaloriesCount.text = decimalFormat.format(calories)

        // 更新进度条
        val progressPercentage = (steps.toFloat() / stepsGoal * 100).toInt().coerceIn(0, 100)
        binding.progressSteps.progress = progressPercentage
        binding.progressBarBottom.progress = progressPercentage

        // 更新目标和平均步数
        binding.tvStepsGoal.text = "/$stepsGoal 步"
        binding.tvStepsAverage.text = "每日平均值: $steps"
    }

    private fun checkAndRequestPermission() {
        val activityRecognitionPermission = Manifest.permission.ACTIVITY_RECOGNITION
        if (ContextCompat.checkSelfPermission(
                this,
                activityRecognitionPermission
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestRecognitionPermissionLauncher.launch(activityRecognitionPermission)
        } else {
            initializeSensorManager()
        }
    }

    private fun initializeSensorManager() {
        // 初始化传感器管理器
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager

        // 访问步数计数器传感器
        stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    }

    override fun onResume() {
        super.onResume()
        // 注册步数计数器传感器的监听器
        stepCounterSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        // 取消注册传感器监听器以节省电池
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_STEP_COUNTER) return
        val steps = event.values[0]
        mainViewModel.updateStepsCounter(steps)
        // 同步到仓库
        stepsRepository.updateSensorSteps(mainViewModel.totalSteps.value?.toInt() ?: 0)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // 此实现中不需要
    }

    override fun onDestroy() {
        super.onDestroy()
        // 取消传感器监听器
        sensorManager.unregisterListener(this)
    }
}