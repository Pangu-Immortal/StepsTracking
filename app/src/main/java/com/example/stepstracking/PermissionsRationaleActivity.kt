package com.example.stepstracking

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.stepstracking.databinding.ActivityPermissionsRationaleBinding

/**
 * 权限解释活动
 *
 * 用途：
 * 1. 向用户解释应用为何需要Health Connect权限
 * 2. 当用户在Android 13+上点击隐私权政策链接查询权限时显示
 * 3. 满足Health Connect API的权限请求最佳实践
 *
 * 技术说明：
 * - 响应androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE意图
 * - 提供清晰的权限使用说明和隐私保障信息
 */
class PermissionsRationaleActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPermissionsRationaleBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPermissionsRationaleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 设置关闭按钮
        setupCloseButton()

        // 设置权限解释内容
        setupRationaleContent()
    }

    private fun setupCloseButton() {
        binding.btnClose.setOnClickListener {
            finish()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun setupRationaleContent() {
        // 在此处设置解释性文本，说明应用为何需要Health Connect权限
        binding.tvRationaleTitle.text = "为什么我们需要访问您的健康数据"

        binding.tvRationaleContent.text = """
            步数追踪应用需要访问您的步数数据以提供以下功能：
            
            1. 显示您的每日步数统计
            2. 从Google Fit或其他健康应用同步步数数据
            3. 提供准确的健康活动分析
            
            我们非常重视您的隐私。您的健康数据仅用于上述目的，不会与第三方共享。
            您可以随时在设置中撤销这些权限。
        """.trimIndent()
    }
}