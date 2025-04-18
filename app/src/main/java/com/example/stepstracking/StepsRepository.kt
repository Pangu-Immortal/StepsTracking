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

    private val _todaySteps = MutableLiveData<Int>(0)
    val todaySteps: LiveData<Int> = _todaySteps

    private val _goalSteps = MutableLiveData<Int>(GOAL_STEPS)
    val goalSteps: LiveData<Int> = _goalSteps

    private val _averageSteps = MutableLiveData<Int>(0)
    val averageSteps: LiveData<Int> = _averageSteps

    private val _calories = MutableLiveData<Float>(0f)
    val calories: LiveData<Float> = _calories

    private var sensorSteps = 0
    private var healthConnectSteps = 0

    fun getSensorSteps(): Int {
        return sensorSteps
    }

    fun updateHealthConnectSteps(steps: Int) {
        healthConnectSteps = steps
        val finalSteps = maxOf(sensorSteps, healthConnectSteps)
        if (finalSteps != _todaySteps.value) {
            _todaySteps.postValue(finalSteps)
            _calories.postValue(finalSteps * 0.04f)
        }
    }

    fun refreshStepsData() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = HealthConnectClient.getOrCreate(context)
                val today = LocalDate.now()
                val startTime = today.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant()
                val endTime = LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant()
                val request = ReadRecordsRequest(
                    recordType = StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
                val response = client.readRecords(request)
                val totalSteps = response.records.sumOf { it.count.toInt() }
                healthConnectSteps = totalSteps
                val finalSteps = maxOf(sensorSteps, healthConnectSteps)
                _todaySteps.postValue(finalSteps)
                _calories.postValue(finalSteps * 0.04f)
                Log.d(TAG, "步数数据已刷新: 传感器步数=$sensorSteps, Health Connect步数=$healthConnectSteps, 最终步数=$finalSteps")
            } catch (e: Exception) {
                Log.e(TAG, "刷新步数数据失败: ${e.message}", e)
            }
        }
    }

    fun setGoalSteps(goal: Int) {
        _goalSteps.postValue(goal)
    }

    fun updateSensorSteps(steps: Int) {
        sensorSteps = steps
        val finalSteps = maxOf(sensorSteps, healthConnectSteps)
        if (finalSteps != todaySteps.value) {
            _todaySteps.postValue(finalSteps)
            _calories.postValue(finalSteps * 0.04f)
            _averageSteps.postValue(finalSteps)
            Log.d(TAG, "传感器步数更新: 传感器步数=$sensorSteps, Health Connect步数=$healthConnectSteps, 最终步数=$finalSteps")
        }
    }

    fun resetSteps() {
        sensorSteps = 0
        healthConnectSteps = 0
        _todaySteps.postValue(0)
        _calories.postValue(0f)
        _averageSteps.postValue(0)
    }
}