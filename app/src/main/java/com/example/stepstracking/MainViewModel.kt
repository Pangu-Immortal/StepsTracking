package com.example.stepstracking

import android.app.Application
import android.content.Context.MODE_PRIVATE
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData


/**
 * View model to handle the steps counter logic.
 * The total steps taken are displayed on the screen.
 * The user can reset the steps counter by clicking on the "Reset" button.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {
	private val sharedPreferences = application.getSharedPreferences("myPrefs", MODE_PRIVATE)

	private val _totalSteps = MutableLiveData<Float>(0f)
	val totalSteps = _totalSteps as LiveData<Float>

	private val initialSteps = MutableLiveData<Float>(sharedPreferences.getFloat(KEY_INITIAL_STEPS, 0f))

	fun updateStepsCounter(newSteps: Float) {
		// The first reading is the total steps since the device boot
		if (initialSteps.value == 0f) {
			// Save the total steps
			saveInitialSteps(newSteps)
			initialSteps.value = newSteps
		}

		val steps = newSteps - (initialSteps.value ?: 0f)

		// Calculate the steps taken since the listener started
		_totalSteps.postValue(steps)
	}

	private fun saveInitialSteps(totalSteps: Float) = run { sharedPreferences.edit().putFloat(KEY_INITIAL_STEPS, totalSteps).apply() }

	fun resetInitialSteps() {
		sharedPreferences.edit().remove(KEY_INITIAL_STEPS).apply()
		initialSteps.value = 0f
		_totalSteps.value = 0f
	}

	companion object {
		private const val KEY_INITIAL_STEPS = "KEY_INITIAL_STEPS"
	}
}