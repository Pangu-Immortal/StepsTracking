package com.example.stepstracking

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 步数数据仓库
 *
 * 单例类，用于提供所有组件共享的步数数据
 */
class StepsRepository private constructor(private val context: Context) {

    companion object {
        private const val TAG = "StepsRepository"
        private const val GOAL_STEPS = 6000

        @Volatile
        private var INSTANCE: StepsRepository? = null

        fun getInstance(context: Context): StepsRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: StepsRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    // 今日步数数据
    private val _todaySteps = MutableLiveData<Int>(0)
    val todaySteps: LiveData<Int> = _todaySteps

    // 目标步数
    private val _goalSteps = MutableLiveData<Int>(GOAL_STEPS)
    val goalSteps: LiveData<Int> = _goalSteps

    // 平均步数数据
    private val _averageSteps = MutableLiveData<Int>(0)
    val averageSteps: LiveData<Int> = _averageSteps

    // 卡路里数据（简单估算：每1000步约消耗40千卡）
    private val _calories = MutableLiveData<Float>(0f)
    val calories: LiveData<Float> = _calories

    // 记录传感器步数
    private var sensorSteps = 0
    // 记录Health Connect步数
    private var healthConnectSteps = 0

    /**
     * 刷新步数数据
     * 从Health Connect读取最新步数并更新所有LiveData
     */
    fun refreshStepsData() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = HealthConnectClient.getOrCreate(context)

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
                val totalSteps = response.records.sumOf { it.count.toInt() }

                // 更新Health Connect步数
                healthConnectSteps = totalSteps

                // 取两个数据源中的较大值
                val finalSteps = maxOf(sensorSteps, healthConnectSteps)

                // 计算卡路里
                val caloriesValue = finalSteps * 0.04f

                // 更新LiveData
                _todaySteps.postValue(finalSteps)
                _calories.postValue(caloriesValue)
                _averageSteps.postValue(finalSteps) // 简化版，实际可能需要从数据库计算

                Log.d(TAG, "步数数据已刷新: 传感器步数=$sensorSteps, Health Connect步数=$healthConnectSteps, 最终步数=$finalSteps, 卡路里=$caloriesValue")
            } catch (e: Exception) {
                Log.e(TAG, "刷新步数数据失败: ${e.message}", e)

                // 即使刷新失败，也使用当前传感器数据更新
                val finalSteps = sensorSteps
                _todaySteps.postValue(finalSteps)
                _calories.postValue(finalSteps * 0.04f)
            }
        }
    }

    /**
     * 设置目标步数
     */
    fun setGoalSteps(goal: Int) {
        _goalSteps.postValue(goal)
    }

    /**
     * 提供步数传感器数据更新（从MainActivity）
     */
    fun updateSensorSteps(steps: Int) {
        // 记录传感器步数
        sensorSteps = steps

        // 取两个数据源中的较大值
        val finalSteps = maxOf(sensorSteps, healthConnectSteps)

        // 只有当最终步数变化时才更新
        if (finalSteps != todaySteps.value) {
            _todaySteps.postValue(finalSteps)
            _calories.postValue(finalSteps * 0.04f)
            _averageSteps.postValue(finalSteps) // 简化版

            Log.d(TAG, "传感器步数更新: 传感器步数=$sensorSteps, Health Connect步数=$healthConnectSteps, 最终步数=$finalSteps")
        }
    }

    /**
     * 重置数据
     */
    fun resetSteps() {
        sensorSteps = 0
        healthConnectSteps = 0
        _todaySteps.postValue(0)
        _calories.postValue(0f)
        _averageSteps.postValue(0)
    }
}