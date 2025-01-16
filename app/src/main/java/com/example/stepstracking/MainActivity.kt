package com.example.stepstracking

import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.stepstracking.databinding.ActivityMainBinding

/**
 * Main Activity to track the steps taken by the user. The logic is based on the Step Counter Sensor.
 * The total steps taken are displayed on the screen.
 * The user can reset the steps counter by clicking on the "Reset" button.
 * The user can also navigate to the Health Steps Activity by clicking on the "Health" button.
 * The Health Steps Activity displays the steps taken from the Health app.
 *
 * The manifest contains the following permissions:
 * <uses-permission android:name="android.permission.ACTIVITY_RECOGNITION" />
 * 	<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
 * 	<uses-permission android:name="android.permission.BODY_SENSORS" />
 */
class MainActivity : AppCompatActivity(), SensorEventListener {
	private val mainViewModel: MainViewModel by viewModels()
	private lateinit var binding: ActivityMainBinding

	private lateinit var requestRecognitionPermissionLauncher: ActivityResultLauncher<String>

	private lateinit var sensorManager: SensorManager
	private var stepCounterSensor: Sensor? = null

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		binding = ActivityMainBinding.inflate(layoutInflater)
		setContentView(binding.root)

		// Initialize the launcher
		requestRecognitionPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
			if (isGranted) {
				// Permission granted
				Toast.makeText(this, "Permission granted", Toast.LENGTH_SHORT).show()
				initialiseSensorManager()
			} else {
				// Permission denied
				Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
			}
		}

		// Check and request permission
		val activityRecognitionPermission = "android.permission.ACTIVITY_RECOGNITION"
		if (ContextCompat.checkSelfPermission(this, activityRecognitionPermission) != PackageManager.PERMISSION_GRANTED) {
			requestRecognitionPermissionLauncher.launch(activityRecognitionPermission)
		} else {
			initialiseSensorManager()
		}

		// Observe the total steps
		mainViewModel.totalSteps.observe(this) { steps ->
			binding.tvStepsCounter.text = steps.toInt().toString()
		}

		binding.ctaReset.setOnClickListener {
			mainViewModel.resetInitialSteps()
		}

		binding.ctaHealth.setOnClickListener {
			startActivity(Intent(this, HealthStepsActivity::class.java))
		}
	}


	private fun initialiseSensorManager() {
		// Initialize the SensorManager
		sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager

		// Access the Step Counter Sensor
		stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
	}

	override fun onResume() {
		super.onResume()
		// Register the listener for the step counter sensor
		stepCounterSensor?.let {
			sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
		}
	}

	override fun onPause() {
		super.onPause()
		// Unregister the sensor listener to save battery
		sensorManager.unregisterListener(this)
	}

	override fun onSensorChanged(event: SensorEvent?) {
		if (event?.sensor?.type != Sensor.TYPE_STEP_COUNTER) return
		mainViewModel.updateStepsCounter(event.values[0])
	}

	override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
		// Not needed in this implementation
	}
}