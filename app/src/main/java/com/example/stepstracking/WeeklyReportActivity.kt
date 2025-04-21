// app/src/main/java/com/example/stepstracking/WeeklyReportActivity.kt
package com.example.stepstracking

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.stepstracking.databinding.ActivityWeeklyReportBinding
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class WeeklyReportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWeeklyReportBinding
    private lateinit var stepsRepository: StepsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWeeklyReportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        stepsRepository = StepsRepository.getInstance(this)

        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        displayWeeklyReport()
    }

    private fun displayWeeklyReport() {
        val weeklyData = stepsRepository.getWeeklySteps()
        if (weeklyData.isEmpty()) {
            binding.tvNoData.visibility = android.view.View.VISIBLE
            binding.cardWeeklyReport.visibility = android.view.View.GONE
            return
        }

        binding.tvNoData.visibility = android.view.View.GONE
        binding.cardWeeklyReport.visibility = android.view.View.VISIBLE

        // 获取日期格式化器
        val inputFormatter = DateTimeFormatter.ISO_LOCAL_DATE
        val outputFormatter = DateTimeFormatter.ofPattern("MM月dd日", Locale.CHINA)

        // 设置今天的数据
        val today = LocalDate.now().toString()
        val todaySteps = weeklyData[today] ?: 0
        binding.tvTodaySteps.text = "$todaySteps"
        binding.progressToday.progress = (todaySteps * 100 / 6000).coerceAtMost(100)

        // 设置昨天的数据
        val yesterday = LocalDate.now().minusDays(1).toString()
        val yesterdaySteps = weeklyData[yesterday] ?: 0
        binding.tvYesterdaySteps.text = "$yesterdaySteps"
        binding.progressYesterday.progress = (yesterdaySteps * 100 / 6000).coerceAtMost(100)

        // 设置过去七天的数据
        val totalSteps = weeklyData.values.sum()
        val avgSteps = if (weeklyData.isNotEmpty()) totalSteps / weeklyData.size else 0
        binding.tvTotalSteps.text = "$totalSteps"
        binding.tvAvgSteps.text = "$avgSteps"

        // 设置日期范围
        val oldestDate = LocalDate.now().minusDays(6)
        val formattedOldestDate = oldestDate.format(outputFormatter)
        val formattedToday = LocalDate.now().format(outputFormatter)
        binding.tvDateRange.text = "$formattedOldestDate - $formattedToday"

        // 设置每日详情
        setupDailyDetails(weeklyData, inputFormatter, outputFormatter)
    }

    private fun setupDailyDetails(
        weeklyData: Map<String, Int>,
        inputFormatter: DateTimeFormatter,
        outputFormatter: DateTimeFormatter
    ) {
        // 清空之前的视图
        binding.containerDailyDetails.removeAllViews()

        // 按日期排序（从最近到最远）
        val sortedData = weeklyData.entries
            .sortedByDescending { it.key }
            .take(7) // 只取最近7天

        for ((dateStr, steps) in sortedData) {
            // 解析日期
            val date = LocalDate.parse(dateStr, inputFormatter)
            val formattedDate = date.format(outputFormatter)

            // 创建日期项视图
            val dailyItemView = layoutInflater.inflate(
                R.layout.item_daily_steps,
                binding.containerDailyDetails,
                false
            )

            // 设置日期和步数
            val tvDate = dailyItemView.findViewById<android.widget.TextView>(R.id.tv_date)
            val tvSteps = dailyItemView.findViewById<android.widget.TextView>(R.id.tv_steps)
            val progressBar = dailyItemView.findViewById<android.widget.ProgressBar>(R.id.progress_steps)

            tvDate.text = formattedDate
            tvSteps.text = "$steps 步"
            progressBar.progress = (steps * 100 / 6000).coerceAtMost(100)

            // 添加到容器
            binding.containerDailyDetails.addView(dailyItemView)
        }
    }
}