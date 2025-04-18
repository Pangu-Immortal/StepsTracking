package com.example.stepstracking

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime

class HealthStepsActivity : AppCompatActivity() {
	private lateinit var binding: ActivityHealthBinding

	val PERMISSIONS = setOf(
		HealthPermission.getReadPermission(StepsRecord::class),
		HealthPermission.getWritePermission(StepsRecord::class)
	)

	// 使用正确的ActivityResultContracts来处理多个权限请求
	private val requestPermissions =
		registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
			// 检查是否所有权限都已授予
			val allGranted = permissions.entries.all { it.value }
			if (allGranted) {
				startFetchingSteps()
			} else {
				Toast.makeText(this, "Health Connect权限被拒绝", Toast.LENGTH_SHORT).show()
			}
		}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		binding = ActivityHealthBinding.inflate(layoutInflater)
		setContentView(binding.root)

		// 检查并请求权限
		checkHealthConnectAvailability()
	}

	private fun checkHealthConnectAvailability() {
		// 检查Health Connect状态
		val availabilityStatus = HealthConnectClient.sdkStatus(this, "com.google.android.apps.healthdata")

		when (availabilityStatus) {
			HealthConnectClient.SDK_AVAILABLE -> {
				// Health Connect可用，检查权限
				checkHealthConnectPermissions()
			}
			HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
				// 需要更新Health Connect
				Toast.makeText(this, "需要更新Health Connect", Toast.LENGTH_SHORT).show()
				val uriString = "market://details?id=com.google.android.apps.healthdata&url=healthconnect%3A%2F%2Fonboarding"
				startActivity(
					Intent(Intent.ACTION_VIEW).apply {
						setPackage("com.android.vending")
						data = Uri.parse(uriString)
						putExtra("overlay", true)
						putExtra("callerId", packageName)
					}
				)
			}
			else -> {
				// Health Connect不可用，引导用户安装
				promptUserToInstallHealthConnect()
			}
		}
	}

	private fun checkHealthConnectPermissions() {
		try {
			val client = HealthConnectClient.getOrCreate(this)

			lifecycleScope.launch {
				try {
					// 检查Health Connect是否已安装
					val availabilityStatus = HealthConnectClient.sdkStatus(this@HealthStepsActivity, "com.google.android.apps.healthdata")
					if (availabilityStatus == HealthConnectClient.SDK_UNAVAILABLE) {
						promptUserToInstallHealthConnect()
						return@launch
					}

					// 使用新的Health Connect权限请求方式
					// 1. 创建权限请求Intent
					val intent = Intent("androidx.health.ACTION_HEALTH_CONNECT_PERMISSIONS").apply {
						putExtra("androidx.health.EXTRA_PERMISSIONS", PERMISSIONS.toList().toTypedArray())
						putExtra("androidx.health.EXTRA_FROM_PERMISSIONS_REQUEST", true)
					}

					// 2. 启动intent以请求权限
					startActivity(intent)

					// 3. 权限可能在Activity回到前台后发生变化，所以在onResume中检查权限状态
				} catch (e: Exception) {
					e.printStackTrace()
					Toast.makeText(this@HealthStepsActivity, "检查权限失败: ${e.message}", Toast.LENGTH_SHORT).show()
				}
			}
		} catch (e: Exception) {
			e.printStackTrace()
			Toast.makeText(this, "无法连接到Health Connect: ${e.message}", Toast.LENGTH_SHORT).show()
		}
	}

	override fun onResume() {
		super.onResume()
		// 当Activity回到前台时，检查权限状态
		checkPermissionsAndStartFetching()
	}

	private fun checkPermissionsAndStartFetching() {
		lifecycleScope.launch {
			try {
				val client = HealthConnectClient.getOrCreate(this@HealthStepsActivity)
				val grantedPermissions = client.permissionController.getGrantedPermissions()

				if (grantedPermissions.containsAll(PERMISSIONS)) {
					startFetchingSteps()
				}
			} catch (e: Exception) {
				e.printStackTrace()
			}
		}
	}

	private fun promptUserToInstallHealthConnect() {
		val playStoreIntent = Intent(Intent.ACTION_VIEW).apply {
			data = Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata")
		}
		startActivity(playStoreIntent)
	}

	private fun startFetchingSteps() {
		lifecycleScope.launch {
			try {
				readStepData()
				delay(5000L)
			} catch (e: Exception) {
				e.printStackTrace()
			}
		}
	}

	private fun readStepData() {
		try {
			val healthConnectClient = HealthConnectClient.getOrCreate(this)

			lifecycleScope.launch {
				try {
					val response = healthConnectClient.readRecords(
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
					e.printStackTrace()
					withContext(Dispatchers.Main) {
						Toast.makeText(this@HealthStepsActivity, "读取步数数据失败: ${e.message}", Toast.LENGTH_SHORT).show()
					}
				}
			}
		} catch (e: Exception) {
			e.printStackTrace()
			Toast.makeText(this, "无法连接到Health Connect: ${e.message}", Toast.LENGTH_SHORT).show()
		}
	}
}