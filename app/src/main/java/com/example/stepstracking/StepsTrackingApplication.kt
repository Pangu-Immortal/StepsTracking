package com.example.stepstracking

import android.app.Application

class StepsTrackingApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // 提前初始化仓库
        StepsRepository.getInstance(this)
    }
}