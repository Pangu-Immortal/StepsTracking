package com.example.stepstracking

import android.app.Application
import android.content.Context.MODE_PRIVATE
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.core.content.edit

/**
 * 步数计数器ViewModel
 *
 * 主要功能：
 * 1. 跟踪并管理用户的步数数据
 * 2. 提供步数的实时LiveData供UI观察和显示
 * 3. 处理步数重置逻辑
 * 4. 使用SharedPreferences持久化存储初始步数
 * 5. 同步数据到步数仓库以保持全应用数据一致性
 */
// 存储键值常量
private const val PREFS_NAME = "StepsTrackingPrefs"
private const val KEY_INITIAL_STEPS = "KEY_INITIAL_STEPS"

class MainViewModel(application: Application) : AndroidViewModel(application) {

	// 获取SharedPreferences实例用于数据持久化
	private val sharedPreferences = application.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

	// 获取步数仓库实例用于数据共享
	private val stepsRepository = StepsRepository.getInstance(application)

	// 公开的总步数LiveData，用于UI观察
	private val _totalSteps = MutableLiveData<Float>(0f)
	val totalSteps: LiveData<Float> = _totalSteps

	// 初始步数（设备启动后的基准值）
	private val initialSteps = MutableLiveData<Float>(
		sharedPreferences.getFloat(KEY_INITIAL_STEPS, 0f)
	)

	/**
	 * 更新步数计数器
	 *
	 * @param newSteps 从传感器获取的当前累计步数
	 *
	 * 说明：
	 * - 首次读取时，我们将传感器值保存为初始步数
	 * - 每次更新时，计算相对于初始值的差值作为用户行走的步数
	 * - 同步到中央仓库以保持应用内数据一致性
	 */
	fun updateStepsCounter(newSteps: Float) {
		// 首次读取（设备启动后第一次获取步数）
		if (initialSteps.value == 0f) {
			saveInitialSteps(newSteps)
			initialSteps.value = newSteps
		}

		// 计算相对步数（当前步数 - 初始步数）
		val relativeSteps = newSteps - (initialSteps.value ?: 0f)

		// 更新LiveData（使用postValue以支持在非主线程调用）
		_totalSteps.postValue(relativeSteps)

		// 同步到步数仓库
		stepsRepository.updateSensorSteps(relativeSteps.toInt())
	}

	/**
	 * 将初始步数保存到SharedPreferences
	 *
	 * @param steps 要保存的初始步数值
	 */
	private fun saveInitialSteps(steps: Float) {
		sharedPreferences.edit {
			putFloat(KEY_INITIAL_STEPS, steps)
		}
	}

	/**
	 * 重置步数计数器
	 *
	 * 执行以下操作：
	 * 1. 从SharedPreferences中移除保存的初始步数
	 * 2. 重置内存中的初始步数值为0
	 * 3. 重置显示的总步数为0
	 * 4. 重置仓库中的步数数据
	 */
	fun resetInitialSteps() {
		// 清除存储的初始步数
		sharedPreferences.edit {
			remove(KEY_INITIAL_STEPS)
		}

		// 重置内存中的值
		initialSteps.value = 0f
		_totalSteps.value = 0f

		// 同步重置仓库数据
		stepsRepository.resetSteps()
	}
}