package com.example.stepstracking

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.health.connect.client.HealthConnectClient

object HealthConnectUtils {

    // 检查Health Connect是否可用
    fun isHealthConnectAvailable(context: Context): Boolean {
        val availabilityStatus = HealthConnectClient.sdkStatus(context, "com.google.android.apps.healthdata")
        return availabilityStatus == HealthConnectClient.SDK_AVAILABLE
    }

    // 检查Health Connect是否需要更新
    fun isHealthConnectUpdateRequired(context: Context): Boolean {
        val availabilityStatus = HealthConnectClient.sdkStatus(context, "com.google.android.apps.healthdata")
        return availabilityStatus == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED
    }

    // 检查Health Connect是否已安装
    fun isHealthConnectInstalled(context: Context): Boolean {
        val intent = Intent("androidx.health.ACTION_HEALTH_CONNECT_SHOW")
        val resolveInfo = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo != null
    }

    // 获取Health Connect客户端
    fun getHealthConnectClient(context: Context): HealthConnectClient? {
        return if (isHealthConnectAvailable(context)) {
            HealthConnectClient.getOrCreate(context)
        } else null
    }
}