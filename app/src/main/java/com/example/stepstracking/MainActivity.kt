package com.example.stepstracking

import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.stepstracking.databinding.ActivityMainBinding

/**
 * 主活动：用于跟踪用户的步数
 *
 * 功能说明：
 * 1. 使用设备的步数传感器(Step Counter Sensor)记录用户步数
 * 2. 在界面上实时显示用户的总步数
 * 3. 提供"重置"按钮允许用户重置步数计数器
 * 4. 提供"Health"按钮导航至健康步数活动(显示从Health应用获取的步数)
 *
 * 所需权限：
 * - android.permission.ACTIVITY_RECOGNITION (活动识别权限)
 * - android.permission.FOREGROUND_SERVICE (前台服务权限)
 * - android.permission.BODY_SENSORS (身体传感器权限)
 */
class MainActivity : AppCompatActivity(), SensorEventListener {
	// 使用ViewModel管理步数数据
	private val mainViewModel: MainViewModel by viewModels()

	// 视图绑定对象
	private lateinit var binding: ActivityMainBinding

	// 传感器相关对象
	private lateinit var sensorManager: SensorManager
	private var stepCounterSensor: Sensor? = null

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		// 初始化视图绑定
		binding = ActivityMainBinding.inflate(layoutInflater)
		setContentView(binding.root)

		// 初始化sensorManager，即使没有权限，也不影响
		sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager

		// 检查并请求活动识别权限
		checkAndRequestPermission()

		// 观察总步数变化并更新UI
		observeStepsCount()

		// 设置按钮点击监听器
		setupClickListeners()
	}

	/**
	 * 检查并请求活动识别权限
	 */
	private fun checkAndRequestPermission() {
		val activityRecognitionPermission = "android.permission.ACTIVITY_RECOGNITION"

		// 定义权限请求回调
		val requestPermissionLauncher = registerForActivityResult(
			ActivityResultContracts.RequestPermission()
		) { isGranted ->
			if (isGranted) {
				// 如果权限授予，初始化传感器
				Toast.makeText(this, "权限已授予", Toast.LENGTH_SHORT).show()
				initializeSensorManager()
			} else {
				// 如果权限被拒绝，提示用户
				Toast.makeText(this, "权限被拒绝", Toast.LENGTH_SHORT).show()
			}
		}

		// 检查权限状态并适当处理
		if (ContextCompat.checkSelfPermission(
				this,
				activityRecognitionPermission
			) != PackageManager.PERMISSION_GRANTED
		) {
			// 如果没有权限，请求权限
			requestPermissionLauncher.launch(activityRecognitionPermission)
		} else {
			// 如果已经有权限，直接初始化传感器
			initializeSensorManager()
		}
	}

	/**
	 * 观察ViewModel中的步数数据并更新UI
	 */
	private fun observeStepsCount() {
		mainViewModel.totalSteps.observe(this) { steps ->
			// 更新步数UI
			binding.tvStepsCounter.text = steps.toInt().toString()
		}
	}

	/**
	 * 设置按钮点击监听器
	 */
	private fun setupClickListeners() {
		// 重置按钮：重置步数计数器
		binding.ctaReset.setOnClickListener {
			mainViewModel.resetInitialSteps()
		}

		// 健康按钮：导航到健康步数活动
		binding.ctaHealth.setOnClickListener {
			startActivity(Intent(this, HealthStepsActivity::class.java))
		}
	}

	/**
	 * 初始化传感器管理器和步数传感器
	 * 在此方法中确保初始化了sensorManager和stepCounterSensor
	 */
	private fun initializeSensorManager() {
		// 获取传感器管理器服务
		sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
		// 获取步数计数传感器
		stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

		// 检查设备是否有步数计数传感器
		if (stepCounterSensor == null) {
			Toast.makeText(this, "设备不支持步数计数传感器", Toast.LENGTH_SHORT).show()
		}
	}

	override fun onResume() {
		super.onResume()
		// 恢复活动时，注册步数传感器监听器
		// 确保传感器存在后再注册
		stepCounterSensor?.let {
			sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
		}
	}

	override fun onPause() {
		super.onPause()
		// 暂停活动时，注销传感器监听器以节省电池
		// 在此我们已经确保了sensorManager已经初始化，直接注销监听器
		sensorManager.unregisterListener(this)
	}

	/**
	 * 当传感器数值变化时调用
	 * 用于接收和处理步数传感器的数据
	 */
	override fun onSensorChanged(event: SensorEvent?) {
		// 只处理步数传感器的数据
		if (event != null && event.sensor != null && event.sensor.type == Sensor.TYPE_STEP_COUNTER) {
			// 更新ViewModel中的步数计数器
			mainViewModel.updateStepsCounter(event.values[0])
		}
	}

	/**
	 * 当传感器精度变化时调用
	 * 在当前实现中不需要处理传感器精度变化
	 */
	override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
		// 此实现中不需要处理传感器精度变化
	}
}
