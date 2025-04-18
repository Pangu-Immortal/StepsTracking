package com.example.stepstracking

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.lifecycle.lifecycleScope
import com.example.stepstracking.databinding.ActivityHealthBinding
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class HealthStepsActivity : AppCompatActivity() {
	private val TAG = "HealthStepsActivity"
	private lateinit var binding: ActivityHealthBinding
	private val healthPermissions = setOf(
		HealthPermission.getReadPermission(StepsRecord::class),
		HealthPermission.getWritePermission(StepsRecord::class)
	)
	private val healthConnectSettingsLauncher = registerForActivityResult(
		ActivityResultContracts.StartActivityForResult()
	) {
		checkPermissions()
	}
	private var fetchJob: Job? = null
	private val exceptionHandler = CoroutineExceptionHandler { _, exception ->
		Log.e(TAG, "协程异常: ${exception.message}")
		lifecycleScope.launch(Dispatchers.Main) {
			binding.progressLoading.visibility = View.GONE
			binding.tvError.visibility = View.VISIBLE
			binding.tvError.text = "出错了: ${exception.localizedMessage}"
			binding.btnRetry.visibility = View.VISIBLE
		}
	}
	private var isRequestingPermissions = false
	private lateinit var stepsRepository: StepsRepository

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		binding = ActivityHealthBinding.inflate(layoutInflater)
		setContentView(binding.root)

		setupInitialUI()
		binding.toolbar.setNavigationOnClickListener { finish() }
		binding.btnRetry.setOnClickListener { checkHealthConnectAvailability() }
		binding.btnRequestPermissions.setOnClickListener { requestHealthConnectPermissions() }
		checkHealthConnectAvailability()
		stepsRepository = StepsRepository.getInstance(this)
		stepsRepository.todaySteps.observe(this) { steps ->
			binding.tvSteps.text = "$steps"
			val progress = if (steps <= 10000) (steps / 10000.0 * 100).toInt() else 100
			binding.progressCircular.progress = progress
			binding.progressLoading.visibility = View.GONE
		}
	}

	private fun setupInitialUI() {
		binding.tvSteps.text = "准备获取步数数据..."
		binding.progressLoading.visibility = View.VISIBLE
		binding.tvError.visibility = View.GONE
		binding.btnRetry.visibility = View.GONE
		binding.cardPermissions.visibility = View.GONE
		binding.tvStepsLabel.visibility = View.GONE
		val dateFormatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日")
		binding.tvDate.text = LocalDate.now().format(dateFormatter)
	}

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

	private fun checkPermissions() {
		Log.d(TAG, "检查Health Connect权限")

		lifecycleScope.launch {
			try {
				val client = HealthConnectClient.getOrCreate(this@HealthStepsActivity)
				val granted = client.permissionController.getGrantedPermissions()

				if (granted.containsAll(healthPermissions)) {
					Log.d(TAG, "已有所需健康数据权限")
					binding.cardPermissions.visibility = View.GONE
					startFetchingSteps()
				} else {
					Log.d(TAG, "需要请求健康数据权限")
					showRequestPermissionsUI()
				}
			} catch (e: Exception) {
				Log.e(TAG, "检查权限失败: ${e.message}")
				showGenericErrorUI("无法连接健康数据服务")
			}
		}
	}

	private fun showRequestPermissionsUI() {
		binding.progressLoading.visibility = View.GONE
		binding.tvError.visibility = View.GONE
		binding.btnRetry.visibility = View.GONE
		binding.cardPermissions.visibility = View.VISIBLE
		binding.tvPermissionTitle.text = "需要健康数据权限"
		binding.tvPermissionMessage.text = "此应用需要访问您的健康数据才能显示步数信息。请点击下方按钮授予权限。"
		binding.btnRequestPermissions.text = "授予权限"
	}

	private fun showPermissionDeniedUI() {
		binding.progressLoading.visibility = View.GONE
		binding.tvError.visibility = View.GONE
		binding.btnRetry.visibility = View.GONE
		binding.cardPermissions.visibility = View.VISIBLE
		binding.tvPermissionTitle.text = "权限被拒绝"
		binding.tvPermissionMessage.text = "需要健康数据权限才能显示步数信息。请前往Health Connect应用手动授予权限。"
		binding.btnRequestPermissions.text = "打开设置"

		AlertDialog.Builder(this)
			.setTitle("权限请求")
			.setMessage("需要健康数据权限才能显示步数信息。您可以在Health Connect应用中手动授予此权限。")
			.setPositiveButton("前往设置") { _, _ -> openHealthConnectSettings() }
			.setNegativeButton("取消") { dialog, _ -> dialog.dismiss() }
			.create()
			.show()
	}

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

	private fun showGenericErrorUI(message: String) {
		binding.progressLoading.visibility = View.GONE
		binding.tvError.visibility = View.VISIBLE
		binding.tvError.text = message
		binding.btnRetry.visibility = View.VISIBLE
		binding.cardPermissions.visibility = View.GONE
	}

	private fun openHealthConnectSettings() {
		try {
			Log.d(TAG, "打开Health Connect设置页面")
			val settingsIntent = Intent().apply {
				action = "androidx.health.ACTION_HEALTH_CONNECT_SETTINGS"
			}
			healthConnectSettingsLauncher.launch(settingsIntent)
		} catch (e: Exception) {
			Log.e(TAG, "打开Health Connect设置失败: ${e.message}")

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

	private fun requestHealthConnectPermissions() {
		Log.d(TAG, "请求Health Connect权限")
		isRequestingPermissions = true
		binding.progressLoading.visibility = View.VISIBLE
		openHealthConnectSettings()
	}

	private fun promptUserToInstallHealthConnect() {
		val playStoreIntent = Intent(Intent.ACTION_VIEW).apply {
			data = Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata")
			setPackage("com.android.vending")
		}

		if (playStoreIntent.resolveActivity(packageManager) != null) {
			startActivity(playStoreIntent)
		} else {
			val browserIntent = Intent(Intent.ACTION_VIEW).apply {
				data = Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata")
			}
			startActivity(browserIntent)
		}
	}

	private fun startFetchingSteps() {
		Log.d(TAG, "开始获取步数数据")
		binding.tvSteps.text = "正在获取步数数据..."
		binding.progressLoading.visibility = View.VISIBLE
		binding.tvError.visibility = View.GONE
		binding.btnRetry.visibility = View.GONE

		fetchJob?.cancel()
		stepsRepository.refreshStepsData()
	}

	override fun onResume() {
		super.onResume()
		checkHealthConnectAvailability()
	}

	override fun onDestroy() {
		super.onDestroy()
		fetchJob?.cancel()
	}
}