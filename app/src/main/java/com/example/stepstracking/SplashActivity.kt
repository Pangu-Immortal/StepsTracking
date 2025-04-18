package com.example.stepstracking

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class SplashActivity : AppCompatActivity() {

    private val SPLASH_DURATION = 2000L // 2秒

    // 注册权限请求
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // 检查是否所有权限都已授予
        val allGranted = permissions.entries.all { it.value }

        if (allGranted) {
            // 所有权限已授予，延迟后启动主活动
            delayedStartMainActivity()
        } else {
            // 即使权限未全部授予，也继续启动应用
            // 但可能某些功能无法正常工作
            delayedStartMainActivity()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // 初始化视图动画
        initViewAnimation()

        // 请求需要的权限
        requestRequiredPermissions()
    }

    private fun initViewAnimation() {
        // 获取图标和文本视图
        val iconView = findViewById<ImageView>(R.id.iv_app_icon)
        val titleView = findViewById<TextView>(R.id.tv_app_title)
        val progressBar = findViewById<ProgressBar>(R.id.progress_bar)

        // 创建淡入动画
        val fadeIn = AlphaAnimation(0.0f, 1.0f).apply {
            duration = 1000
            fillAfter = true
        }

        // 应用动画
        iconView.startAnimation(fadeIn)
        titleView.startAnimation(fadeIn)

        // 动画监听器
        fadeIn.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {}

            override fun onAnimationEnd(animation: Animation?) {
                // 显示进度条
                progressBar.visibility = android.view.View.VISIBLE
            }

            override fun onAnimationRepeat(animation: Animation?) {}
        })
    }

    private fun requestRequiredPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        // 添加活动识别权限
        permissionsToRequest.add(Manifest.permission.ACTIVITY_RECOGNITION)

        // 在Android 13+上添加通知权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        // 检查前台服务权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            permissionsToRequest.add(Manifest.permission.FOREGROUND_SERVICE)
        }

        // 如果有权限需要申请，启动权限请求
        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            // 没有权限需要申请，直接延迟启动主活动
            delayedStartMainActivity()
        }
    }

    private fun delayedStartMainActivity() {
        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this@SplashActivity, MainActivity::class.java)
            startActivity(intent)
            finish() // 结束SplashActivity
        }, SPLASH_DURATION)
    }
}