package com.example.stepstracking

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AppCompatActivity
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController.Companion.createRequestPermissionResultContract
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

/**
 * Activity to read the steps data from HealthConnect and display it on the screen.
 * The user can view the steps taken from the Health app.
 *
 * It was tested on the Google Fit. HealthConnect app acts as a bridge between the app and Google Fit.
 * It can be used with other health apps that support the HealthConnect API.
 *
 * The user can install HealthConnect from the Play Store if it is not available on the device. The prompt will be shown.
 * Google Fit writes data to the HealthConnect in different time intervals. The app reads the data in cycles of 5 seconds just for testing purposes.
 *
 * The manifest contains the following permissions:
 * <uses-permission android:name="android.permission.health.READ_STEPS"/>
 * 	<uses-permission android:name="android.permission.health.WRITE_STEPS"/>
 *
 * 	<queries>
 * 		<!-- Add Health Connect package to queries -->
 * 		<package android:name="com.google.android.apps.healthdata" />
 * 	</queries>
 * */
class HealthStepsActivity : AppCompatActivity() {
	private lateinit var binding: ActivityHealthBinding

	private lateinit var permissionsLauncher: ActivityResultLauncher<Set<String>>

	val PERMISSIONS = setOf(
		HealthPermission.getReadPermission(StepsRecord::class),
		HealthPermission.getWritePermission(StepsRecord::class)
	)

//	private val requiredPermissions = setOf("android.permission.health.READ_STEPS")

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		binding = ActivityHealthBinding.inflate(layoutInflater)
		setContentView(binding.root)

		// Register the launcher
		permissionsLauncher = registerForActivityResult(createRequestPermissionResultContract()) { grantedPermissions ->
			// Check if all required permissions are granted
			if (grantedPermissions.containsAll(PERMISSIONS)) {
				// All required permissions are granted
				startFetchingSteps()
			} else {
				// Permissions were denied
				Toast.makeText(this, "Health Connect permissions denied", Toast.LENGTH_SHORT).show()
			}
		}

		// Request permissions
		requestHealthConnectPermissions()

//		checkMe()
	}


	private fun checkHealthConnectAvailability(): Boolean {
		val intent = Intent("androidx.health.ACTION_REQUEST_PERMISSIONS").apply {
			`package` = "com.google.android.apps.healthdata"
		}
		val resolveInfo = packageManager.resolveActivity(intent, 0)
		return resolveInfo != null
	}

	private fun requestHealthConnectPermissions() {
		if (checkHealthConnectAvailability()) {
			permissionsLauncher.launch(PERMISSIONS)
		} else {
			promptUserToInstallHealthConnect()
		}
	}

	/**
	 * Opens the Play Store to prompt the user to install HealthConnect. Its needed for Android 13 and below
	 * HealthConnect is a Google app that acts as a bridge between the app and Google Fit
	 * */
	private fun promptUserToInstallHealthConnect() {
		val playStoreIntent = Intent(Intent.ACTION_VIEW).apply {
			data = Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata")
		}
		startActivity(playStoreIntent)
	}

	/**
	 * Reads steps data from HealthConnect and updates the UI
	 * It happens in cycles of 5 seconds
	 *
	 * Google Fit was using for testing and it can write data to HealthConnect in different time intervals
	 * */
	private fun startFetchingSteps() {
		lifecycleScope.launch {
			while (true) {
				readStepData()
				delay(5000L) // Wait for 5 seconds before fetching again
			}
		}
	}

	private fun readStepData() {
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
					binding.tvSteps.text = "Steps Today: $totalSteps"
				}
			} catch (e: Exception) {
				e.printStackTrace()
				withContext(Dispatchers.Main) {
					Toast.makeText(this@HealthStepsActivity, "Failed to read step data", Toast.LENGTH_SHORT).show()
				}
			}
		}
	}

	/**
	 * Checks the availability of HealthConnect on the device
	 * works correctly. Can be used this checking variant
	 * */
//	private fun checkMe() {
//		val providerPackageName = "com.google.android.apps.healthdata"
//		val availabilityStatus = HealthConnectClient.sdkStatus(this, providerPackageName)
//		if (availabilityStatus == HealthConnectClient.SDK_UNAVAILABLE) {
//			return // early return as there is no viable integration
//		}
//		if (availabilityStatus == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED) {
//			// Optionally redirect to package installer to find a provider, for example:
//			val uriString = "market://details?id=$providerPackageName&url=healthconnect%3A%2F%2Fonboarding"
//			startActivity(
//				Intent(Intent.ACTION_VIEW).apply {
//					setPackage("com.android.vending")
//					data = Uri.parse(uriString)
//					putExtra("overlay", true)
//					putExtra("callerId", packageName)
//				}
//			)
//			return
//		}
//	}
}