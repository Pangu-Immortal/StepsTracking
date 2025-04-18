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

class MainActivity : AppCompatActivity() {
    private val TAG = "MainActivity"
    private val mainViewModel: MainViewModel by viewModels()
    private lateinit var binding: ActivityMainBinding
    private lateinit var requestRecognitionPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var stepsRepository: StepsRepository
    private val stepsGoal = 6000

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or
                android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

        requestRecognitionPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                StepsTrackingService.startService(this)
            } else {
                Toast.makeText(this, "需要步数权限才能跟踪步数", Toast.LENGTH_SHORT).show()
            }
        }

        checkAndRequestPermission()
        stepsRepository = StepsRepository.getInstance(this)
        stepsRepository.todaySteps.observe(this) { steps ->
            updateStepsUI(steps)
        }
        stepsRepository.calories.observe(this) { calories ->
            val decimalFormat = DecimalFormat("0.0")
            binding.tvCaloriesCount.text = decimalFormat.format(calories)
        }

        binding.ctaReset.setOnClickListener {
            stepsRepository.resetSteps()
        }

        binding.ctaHealth.setOnClickListener {
            startActivity(Intent(this, HealthStepsActivity::class.java))
        }
    }

    private fun updateStepsUI(steps: Int) {
        binding.tvStepsCounter.text = steps.toString()
        binding.tvStepsCount.text = steps.toString()
        val calories = steps * 0.04f
        val decimalFormat = DecimalFormat("0.0")
        binding.tvCaloriesCount.text = decimalFormat.format(calories)
        val progressPercentage = (steps.toFloat() / stepsGoal * 100).toInt().coerceIn(0, 100)
        binding.progressSteps.progress = progressPercentage
        binding.progressBarBottom.progress = progressPercentage
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
            StepsTrackingService.startService(this)
        }
    }
}