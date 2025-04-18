package com.example.stepstracking

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.lifecycle.lifecycleScope
import com.example.stepstracking.databinding.ActivityHealthBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime

class HealthStepsActivity : AppCompatActivity() {
	private lateinit var binding: ActivityHealthBinding
	private var healthConnectClient: HealthConnectClient? = null
	private var fetchingJob: Job? = null

	// 需要的权限
	private val permissions = setOf(
		HealthPermission.getReadPermission(StepsRecord::class),
		HealthPermission.getWritePermission(StepsRecord::class)
	)

	// 启动系统设置的启动器
	private val settingsLauncher = registerForActivityResult(
		ActivityResultContracts.StartActivityForResult()
	) {
		// 从设置返回后，重新检查权限
		checkPermissionsAndStartReading()
	}

	// 启动Health Connect应用的启动器
	private val healthConnectLauncher = registerForActivityResult(
		ActivityResultContracts.StartActivityForResult()
	) {
		// 从Health Connect返回后，重新检查权限
		checkPermissionsAndStartReading()
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		binding = ActivityHealthBinding.inflate(layoutInflater)
		setContentView(binding.root)

		// 检查Health Connect状态
		checkHealthConnectAvailability()
	}

	override fun onResume() {
		super.onResume()
		// 每次回到前台时，重新检查权限状态
		if (healthConnectClient != null) {
			checkPermissionsAndStartReading()
		}
	}

	override fun onPause() {
		super.onPause()
		// 停止获取步数数据
		fetchingJob?.cancel()
		fetchingJob = null
	}

	private fun checkHealthConnectAvailability() {
		// 检查Health Connect SDK状态
		val availabilityStatus = HealthConnectClient.sdkStatus(this, "com.google.android.apps.healthdata")

		when (availabilityStatus) {
			HealthConnectClient.SDK_AVAILABLE -> {
				// Health Connect可用，初始化客户端
				try {
					healthConnectClient = HealthConnectClient.getOrCreate(this)
					checkPermissionsAndStartReading()
				} catch (e: Exception) {
					Log.e("HealthStepsActivity", "初始化Health Connect客户端失败", e)
					Toast.makeText(this, "无法连接Health Connect: ${e.message}", Toast.LENGTH_SHORT).show()
				}
			}

			HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
				// 需要更新Health Connect
				Toast.makeText(this, "需要更新Health Connect应用", Toast.LENGTH_LONG).show()

				try {
					// 打开Play Store更新Health Connect
					val intent = Intent(Intent.ACTION_VIEW).apply {
						data = Uri.parse(
							"https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata"
						)
						setPackage("com.android.vending")
					}
					startActivity(intent)
				} catch (e: Exception) {
					Log.e("HealthStepsActivity", "无法打开Play Store", e)
					Toast.makeText(this, "无法打开Play Store: ${e.message}", Toast.LENGTH_SHORT).show()
				}
			}

			else -> {
				// Health Connect不可用，提示安装
				Toast.makeText(this, "需要安装Health Connect应用", Toast.LENGTH_LONG).show()

				try {
					// 打开Play Store安装Health Connect
					val intent = Intent(Intent.ACTION_VIEW).apply {
						data = Uri.parse(
							"https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata"
						)
					}
					startActivity(intent)
				} catch (e: Exception) {
					Log.e("HealthStepsActivity", "无法打开Play Store", e)
					Toast.makeText(this, "无法打开Play Store: ${e.message}", Toast.LENGTH_SHORT).show()
				}
			}
		}
	}

	private fun checkPermissionsAndStartReading() {
		val client = healthConnectClient ?: return

		lifecycleScope.launch {
			try {
				// 检查已授予的权限
				val grantedPermissions = client.permissionController.getGrantedPermissions()

				if (grantedPermissions.containsAll(permissions)) {
					// 已有所有需要的权限，开始读取步数
					startFetchingSteps()
				} else {
					// 缺少权限，请求权限
					requestHealthConnectPermissions()
				}
			} catch (e: Exception) {
				Log.e("HealthStepsActivity", "检查权限失败", e)
				Toast.makeText(this@HealthStepsActivity, "检查权限失败: ${e.message}", Toast.LENGTH_SHORT).show()
			}
		}
	}

	private fun requestHealthConnectPermissions() {
		try {
			// 尝试打开 Health Connect 权限页面
			val intent = Intent().apply {
				action = "androidx.health.ACTION_HEALTH_CONNECT_SETTINGS"
			}

			// 使用启动器启动活动
			healthConnectLauncher.launch(intent)

			// 显示指导消息
			Toast.makeText(
				this,
				"请在Health Connect应用中授予我们app所需的权限",
				Toast.LENGTH_LONG
			).show()
		} catch (e: Exception) {
			Log.e("HealthStepsActivity", "打开Health Connect设置失败", e)

			try {
				// 备选方案：尝试通过系统设置打开Health Connect应用设置
				val settingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
					data = Uri.parse("package:com.google.android.apps.healthdata")
				}
				settingsLauncher.launch(settingsIntent)

				Toast.makeText(
					this,
					"请在应用设置中授予Health Connect权限",
					Toast.LENGTH_LONG
				).show()
			} catch (e2: Exception) {
				Log.e("HealthStepsActivity", "打开应用设置失败", e2)
				Toast.makeText(
					this,
					"无法自动打开设置。请手动前往设置->应用->Health Connect，授予权限后返回此应用。",
					Toast.LENGTH_LONG
				).show()
			}
		}
	}

	private fun startFetchingSteps() {
		// 如果已有正在运行的任务，先取消
		fetchingJob?.cancel()

		// 创建新任务
		fetchingJob = lifecycleScope.launch {
			while (true) {
				readStepData()
				delay(5000L) // 每5秒读取一次
			}
		}
	}

	private fun readStepData() {
		val client = healthConnectClient ?: return

		lifecycleScope.launch {
			try {
				val response = client.readRecords(
					ReadRecordsRequest(
						recordType = StepsRecord::class,
						timeRangeFilter = TimeRangeFilter.between(
							ZonedDateTime.now().minusDays(1).toInstant(),
							ZonedDateTime.now().toInstant()
						)
					)
				)

				val totalSteps = response.records.sumOf { it.count }

				withContext(Dispatchers.Main) {
					binding.tvSteps.text = "今日步数: $totalSteps"
				}
			} catch (e: Exception) {
				Log.e("HealthStepsActivity", "读取步数数据失败", e)

				withContext(Dispatchers.Main) {
					binding.tvSteps.text = "读取步数失败"
					Toast.makeText(this@HealthStepsActivity, "读取步数数据失败: ${e.message}", Toast.LENGTH_SHORT).show()
				}
			}
		}
	}
}