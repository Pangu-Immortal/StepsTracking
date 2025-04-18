package com.example.stepstracking

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.lifecycle.lifecycleScope
import com.example.stepstracking.databinding.ActivityHealthBinding
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 健康步数活动
 *
 * 主要功能：
 * 1. 从HealthConnect读取步数数据并在界面上显示
 * 2. 支持HealthConnect API
 * 3. 提供完整的错误处理和用户交互
 * 4. 使用ViewBinding和ConstraintLayout实现现代UI
 *
 * 适配说明：
 * - 支持Android 7-35的所有版本
 * - 使用最新的Health Connect客户端API
 * - 针对不同场景提供具体错误提示
 * - 针对Android 35的权限请求机制特别适配
 */
class HealthStepsActivity : AppCompatActivity() {
	private val TAG = "HealthStepsActivity"

	// 使用ViewBinding
	private lateinit var binding: ActivityHealthBinding

	// 健康权限集合
	private val healthPermissions = setOf(
		HealthPermission.getReadPermission(StepsRecord::class),
		HealthPermission.getWritePermission(StepsRecord::class)
	)

	// Health Connect设置启动器
	private val healthConnectSettingsLauncher = registerForActivityResult(
		ActivityResultContracts.StartActivityForResult()
	) {
		// 从Health Connect设置返回后，重新检查权限
		checkPermissions()
	}

	// 用于数据获取的协程Job，便于在生命周期结束时取消
	private var fetchJob: Job? = null

	// 错误处理器
	private val exceptionHandler = CoroutineExceptionHandler { _, exception ->
		Log.e(TAG, "协程异常: ${exception.message}")
		lifecycleScope.launch(Dispatchers.Main) {
			binding.progressLoading.visibility = View.GONE
			binding.tvError.visibility = View.VISIBLE
			binding.tvError.text = "出错了: ${exception.localizedMessage}"
			binding.btnRetry.visibility = View.VISIBLE
		}
	}

	// 是否正在请求权限标志
	private var isRequestingPermissions = false

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		// 初始化ViewBinding
		binding = ActivityHealthBinding.inflate(layoutInflater)
		setContentView(binding.root)

		// 设置初始UI状态
		setupInitialUI()

		// 设置返回按钮
		binding.toolbar.setNavigationOnClickListener {
			finish()
		}

		// 设置重试按钮
		binding.btnRetry.setOnClickListener {
			checkHealthConnectAvailability()
		}

		// 设置权限请求按钮
		binding.btnRequestPermissions.setOnClickListener {
			requestHealthConnectPermissions()
		}

		// 检查Health Connect可用性
		checkHealthConnectAvailability()
	}

	/**
	 * 设置初始UI状态
	 */
	private fun setupInitialUI() {
		binding.tvSteps.text = "准备获取步数数据..."
		binding.progressLoading.visibility = View.VISIBLE
		binding.tvError.visibility = View.GONE
		binding.btnRetry.visibility = View.GONE
		binding.cardPermissions.visibility = View.GONE
		binding.tvStepsLabel.visibility = View.GONE

		// 设置日期
		val dateFormatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日")
		binding.tvDate.text = LocalDate.now().format(dateFormatter)
	}

	/**
	 * 检查Health Connect是否可用
	 */
	private fun checkHealthConnectAvailability() {
		Log.d(TAG, "检查Health Connect可用性")
		binding.progressLoading.visibility = View.VISIBLE
		binding.tvError.visibility = View.GONE
		binding.btnRetry.visibility = View.GONE
		binding.cardPermissions.visibility = View.GONE

		lifecycleScope.launch {
			try {
				val availability = withContext(Dispatchers.IO) {
					HealthConnectClient.sdkStatus(applicationContext, "com.google.android.apps.healthdata")
				}

				when (availability) {
					HealthConnectClient.SDK_AVAILABLE -> {
						Log.d(TAG, "Health Connect可用")
						checkPermissions()
					}
					HealthConnectClient.SDK_UNAVAILABLE -> {
						Log.e(TAG, "Health Connect不可用")
						showInstallHealthConnectDialog()
					}
					HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
						Log.e(TAG, "Health Connect需要更新")
						showUpdateHealthConnectDialog()
					}
					else -> {
						Log.e(TAG, "未知Health Connect状态: $availability")
						showGenericErrorUI("Health Connect不可用")
					}
				}
			} catch (e: Exception) {
				Log.e(TAG, "检查Health Connect可用性失败: ${e.message}")
				showGenericErrorUI("无法检查Health Connect可用性")
			}
		}
	}

	/**
	 * 检查应用是否有所需的健康数据权限
	 */
	private fun checkPermissions() {
		Log.d(TAG, "检查Health Connect权限")

		lifecycleScope.launch {
			try {
				val client = HealthConnectClient.getOrCreate(this@HealthStepsActivity)
				val granted = client.permissionController.getGrantedPermissions()

				if (granted.containsAll(healthPermissions)) {
					// 已有所需权限，开始获取步数
					Log.d(TAG, "已有所需健康数据权限")
					binding.cardPermissions.visibility = View.GONE
					startFetchingSteps()
				} else {
					// 需要请求权限
					Log.d(TAG, "需要请求健康数据权限")
					showRequestPermissionsUI()
				}
			} catch (e: Exception) {
				Log.e(TAG, "检查权限失败: ${e.message}")
				showGenericErrorUI("无法连接健康数据服务")
			}
		}
	}

	/**
	 * 显示请求权限的UI
	 */
	private fun showRequestPermissionsUI() {
		binding.progressLoading.visibility = View.GONE
		binding.tvError.visibility = View.GONE
		binding.btnRetry.visibility = View.GONE
		binding.cardPermissions.visibility = View.VISIBLE
		binding.tvPermissionTitle.text = "需要健康数据权限"
		binding.tvPermissionMessage.text = "此应用需要访问您的健康数据才能显示步数信息。请点击下方按钮授予权限。"
		binding.btnRequestPermissions.text = "授予权限"
	}

	/**
	 * 显示权限被拒绝的UI
	 */
	private fun showPermissionDeniedUI() {
		binding.progressLoading.visibility = View.GONE
		binding.tvError.visibility = View.GONE
		binding.btnRetry.visibility = View.GONE
		binding.cardPermissions.visibility = View.VISIBLE
		binding.tvPermissionTitle.text = "权限被拒绝"
		binding.tvPermissionMessage.text = "需要健康数据权限才能显示步数信息。请前往Health Connect应用手动授予权限。"
		binding.btnRequestPermissions.text = "打开设置"

		// 显示对话框提醒用户
		AlertDialog.Builder(this)
			.setTitle("权限请求")
			.setMessage("需要健康数据权限才能显示步数信息。您可以在Health Connect应用中手动授予此权限。")
			.setPositiveButton("前往设置") { _, _ -> openHealthConnectSettings() }
			.setNegativeButton("取消") { dialog, _ -> dialog.dismiss() }
			.create()
			.show()
	}

	/**
	 * 显示安装Health Connect的对话框
	 */
	private fun showInstallHealthConnectDialog() {
		binding.progressLoading.visibility = View.GONE

		AlertDialog.Builder(this)
			.setTitle("需要安装Health Connect")
			.setMessage("此功能需要安装Google的Health Connect应用。是否前往应用商店安装？")
			.setPositiveButton("安装") { _, _ ->
				promptUserToInstallHealthConnect()
			}
			.setNegativeButton("取消") { dialog, _ ->
				dialog.dismiss()
				showGenericErrorUI("需要安装Health Connect才能使用此功能")
			}
			.create()
			.show()
	}

	/**
	 * 显示更新Health Connect的对话框
	 */
	private fun showUpdateHealthConnectDialog() {
		binding.progressLoading.visibility = View.GONE

		AlertDialog.Builder(this)
			.setTitle("需要更新Health Connect")
			.setMessage("此功能需要更新Google的Health Connect应用到最新版本。是否前往应用商店更新？")
			.setPositiveButton("更新") { _, _ ->
				promptUserToInstallHealthConnect()
			}
			.setNegativeButton("取消") { dialog, _ ->
				dialog.dismiss()
				showGenericErrorUI("需要更新Health Connect才能使用此功能")
			}
			.create()
			.show()
	}

	/**
	 * 显示通用错误UI
	 */
	private fun showGenericErrorUI(message: String) {
		binding.progressLoading.visibility = View.GONE
		binding.tvError.visibility = View.VISIBLE
		binding.tvError.text = message
		binding.btnRetry.visibility = View.VISIBLE
		binding.cardPermissions.visibility = View.GONE
	}

	/**
	 * 打开Health Connect设置页面
	 * 这是适配Android 35的关键部分 - 不再使用createRequestPermissionResultContract
	 * 而是直接打开Health Connect设置让用户手动授权
	 */
	private fun openHealthConnectSettings() {
		try {
			Log.d(TAG, "打开Health Connect设置页面")
			val settingsIntent = Intent().apply {
				action = "androidx.health.ACTION_HEALTH_CONNECT_SETTINGS"
			}
			healthConnectSettingsLauncher.launch(settingsIntent)
		} catch (e: Exception) {
			Log.e(TAG, "打开Health Connect设置失败: ${e.message}")

			// 尝试打开应用详情页
			try {
				val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
					data = Uri.parse("package:com.google.android.apps.healthdata")
				}
				startActivity(intent)
			} catch (e2: Exception) {
				Log.e(TAG, "打开应用详情页失败: ${e2.message}")
				Toast.makeText(
					this,
					"无法打开Health Connect设置，请手动打开并授予权限",
					Toast.LENGTH_LONG
				).show()
			}
		}
	}

	/**
	 * 请求Health Connect权限
	 * 针对Android 35的适配版本 - 直接打开Health Connect设置页面
	 */
	private fun requestHealthConnectPermissions() {
		Log.d(TAG, "请求Health Connect权限")
		isRequestingPermissions = true
		binding.progressLoading.visibility = View.VISIBLE

		// 打开Health Connect设置页面
		openHealthConnectSettings()
	}

	/**
	 * 打开Play商店安装Health Connect
	 */
	private fun promptUserToInstallHealthConnect() {
		val playStoreIntent = Intent(Intent.ACTION_VIEW).apply {
			data = Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata")
			setPackage("com.android.vending") // 确保在Google Play商店打开
		}

		if (playStoreIntent.resolveActivity(packageManager) != null) {
			startActivity(playStoreIntent)
		} else {
			// 如果Play商店不可用，使用浏览器打开
			val browserIntent = Intent(Intent.ACTION_VIEW).apply {
				data = Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata")
			}
			startActivity(browserIntent)
		}
	}

	/**
	 * 开始获取步数数据
	 */
	private fun startFetchingSteps() {
		Log.d(TAG, "开始获取步数数据")
		binding.tvSteps.text = "正在获取步数数据..."
		binding.progressLoading.visibility = View.VISIBLE
		binding.tvError.visibility = View.GONE
		binding.btnRetry.visibility = View.GONE

		// 取消之前的任务
		fetchJob?.cancel()

		// 启动新的获取任务
		fetchJob = lifecycleScope.launch(exceptionHandler) {
			while (isActive) {
				readStepData()
				// 每5秒刷新一次数据
				delay(5000)
			}
		}
	}

	/**
	 * 从Health Connect读取步数数据
	 */
	private fun readStepData() {
		lifecycleScope.launch(Dispatchers.IO + exceptionHandler) {
			try {
				val client = HealthConnectClient.getOrCreate(this@HealthStepsActivity)

				// 获取今天的时间范围
				val today = LocalDate.now()
				val startTime = today.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant()
				val endTime = LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant()

				// 请求今天的步数记录
				val request = ReadRecordsRequest(
					recordType = StepsRecord::class,
					timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
				)

				val response = client.readRecords(request)

				// 计算总步数
				val totalSteps = response.records.sumOf { it.count }

				// 在主线程更新UI
				withContext(Dispatchers.Main) {
					binding.tvSteps.text = "$totalSteps"
					binding.progressLoading.visibility = View.GONE
					binding.tvStepsLabel.visibility = View.VISIBLE

					// 更新进度环
					val progress = if (totalSteps <= 10000) (totalSteps / 10000.0 * 100).toInt() else 100
					binding.progressCircular.progress = progress
				}

				Log.d(TAG, "成功获取今日步数: $totalSteps")
			} catch (e: Exception) {
				Log.e(TAG, "读取步数数据失败: ${e.message}")
				withContext(Dispatchers.Main) {
					binding.progressLoading.visibility = View.GONE
					binding.tvError.visibility = View.VISIBLE
					binding.tvError.text = "读取步数数据失败: ${e.localizedMessage}"
					binding.btnRetry.visibility = View.VISIBLE
				}
			}
		}
	}

	override fun onResume() {
		super.onResume()

		// 如果不是正在请求权限，检查Health Connect可用性
		if (!isRequestingPermissions) {
			Log.d(TAG, "onResume: 重新检查Health Connect可用性")
			checkHealthConnectAvailability()
		} else {
			// 如果是从权限请求页面返回，检查权限
			isRequestingPermissions = false
			checkPermissions()
		}
	}

	override fun onDestroy() {
		super.onDestroy()

		// 取消所有正在进行的协程任务
		fetchJob?.cancel()
	}
}