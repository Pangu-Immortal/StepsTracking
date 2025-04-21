package com.example.stepstracking

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 步数数据仓库
 *
 * 单例类，用于提供所有组件共享的步数数据
 */
class StepsRepository private constructor(private val context: Context) {

    companion object {
        private const val TAG = "StepsRepository"
        private const val GOAL_STEPS = 6000
        private const val PREFS_NAME = "StepsTrackingPrefs"
        private const val MAX_DAYS_TO_STORE = 7

        @Volatile
        private var INSTANCE: StepsRepository? = null

        fun getInstance(context: Context): StepsRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: StepsRepository(context.applicationContext).also { INSTANCE = it }
            }
        }

        private fun getSharedPreferences(context: Context): SharedPreferences {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
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

    // 日期格式化工具
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

    // 周步数数据
    private val _weeklySteps = MutableLiveData<Map<String, Int>>(emptyMap())
    val weeklySteps: LiveData<Map<String, Int>> = _weeklySteps

    init {
        // 应用启动时加载当天步数数据
        loadTodaySteps()
        // 加载周数据
        loadWeeklySteps()
        // 计算平均步数
        calculateAverageSteps()
        // 检查日期变更，必要时进行重置
        checkDateChange()
    }

    /**
     * 加载当天步数数据
     */
    private fun loadTodaySteps() {
        val todayKey = getTodayKey()
        val prefs = getSharedPreferences(context)
        val savedSteps = prefs.getInt(todayKey, 0)
        _todaySteps.postValue(savedSteps)
        _calories.postValue(savedSteps * 0.04f)
        Log.d(TAG, "加载当天步数: $savedSteps")
    }

    /**
     * 获取当前日期的存储键
     */
    private fun getTodayKey(): String {
        return "steps_${LocalDate.now().format(dateFormatter)}"
    }

    /**
     * 获取指定日期的存储键
     */
    private fun getKeyForDate(date: LocalDate): String {
        return "steps_${date.format(dateFormatter)}"
    }

    /**
     * 检查日期变更，必要时进行重置
     */
    private fun checkDateChange() {
        val prefs = getSharedPreferences(context)
        val lastDateKey = "last_date"
        val lastDateStr = prefs.getString(lastDateKey, "")
        val today = LocalDate.now().format(dateFormatter)

        if (lastDateStr != null && lastDateStr.isNotEmpty() && lastDateStr != today) {
            // 日期已变更，保存昨天的数据并重置今天的数据
            sensorSteps = 0
            healthConnectSteps = 0
            _todaySteps.postValue(0)
            _calories.postValue(0f)

            // 保存新的日期
            prefs.edit().putString(lastDateKey, today).apply()

            Log.d(TAG, "日期变更，步数已重置")
        } else if (lastDateStr.isNullOrEmpty()) {
            // 首次运行，保存今天的日期
            prefs.edit().putString(lastDateKey, today).apply()
        }
    }

    /**
     * 更新Health Connect步数
     */
    fun updateHealthConnectSteps(steps: Int) {
        healthConnectSteps = steps

        // 确保最终值是两者中的最大值
        val finalSteps = maxOf(sensorSteps, healthConnectSteps)
        if (finalSteps != _todaySteps.value) {
            _todaySteps.postValue(finalSteps)
            _calories.postValue(finalSteps * 0.04f)
            saveStepsToPrefs(finalSteps)
        }
    }

    /**
     * 提供步数传感器数据更新（从StepsTrackingService）
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

            // 保存步数到SharedPreferences
            saveStepsToPrefs(finalSteps)

            // 更新周数据
            loadWeeklySteps()
            // 重新计算平均值
            calculateAverageSteps()

            Log.d(TAG, "传感器步数更新: 传感器步数=$sensorSteps, Health Connect步数=$healthConnectSteps, 最终步数=$finalSteps")
        }
    }

    /**
     * 保存步数到SharedPreferences
     */
    private fun saveStepsToPrefs(steps: Int) {
        val prefs = getSharedPreferences(context)
        val todayKey = getTodayKey()
        prefs.edit().putInt(todayKey, steps).apply()
        Log.d(TAG, "步数已保存: $steps")
    }

    /**
     * 加载一周的步数数据
     */
    private fun loadWeeklySteps() {
        val prefs = getSharedPreferences(context)
        val weeklySteps = mutableMapOf<String, Int>()

        // 获取当前日期
        val today = LocalDate.now()

        // 遍历最近7天
        for (i in 0 until MAX_DAYS_TO_STORE) {
            val date = today.minusDays(i.toLong())
            val key = getKeyForDate(date)
            val steps = prefs.getInt(key, 0)
            weeklySteps[date.toString()] = steps
        }

        _weeklySteps.postValue(weeklySteps)
    }

    /**
     * 获取一周的步数数据
     */
    fun getWeeklySteps(): Map<String, Int> {
        return _weeklySteps.value ?: emptyMap()
    }

    /**
     * 计算平均步数
     */
    private fun calculateAverageSteps() {
        val weekData = _weeklySteps.value ?: return
        if (weekData.isEmpty()) return

        val totalSteps = weekData.values.sum()
        val averageSteps = totalSteps / weekData.size
        _averageSteps.postValue(averageSteps)
    }

    /**
     * 重置数据
     */
    fun resetSteps() {
        sensorSteps = 0
        healthConnectSteps = 0
        _todaySteps.postValue(0)
        _calories.postValue(0f)

        // 清除当天步数存储
        val prefs = getSharedPreferences(context)
        val todayKey = getTodayKey()
        prefs.edit().remove(todayKey).apply()

        // 更新周数据和平均值
        loadWeeklySteps()
        calculateAverageSteps()

        Log.d(TAG, "步数数据已重置")
    }

    /**
     * 是否应该重置步数（每日0点）
     */
    fun shouldResetSteps(): Boolean {
        val prefs = getSharedPreferences(context)
        val lastResetKey = "last_reset_date"
        val lastResetDate = prefs.getString(lastResetKey, "")
        val today = LocalDate.now().format(dateFormatter)

        return lastResetDate != today
    }

    /**
     * 标记已重置步数
     */
    fun markStepsReset() {
        val prefs = getSharedPreferences(context)
        val lastResetKey = "last_reset_date"
        val today = LocalDate.now().format(dateFormatter)
        prefs.edit().putString(lastResetKey, today).apply()
    }
}