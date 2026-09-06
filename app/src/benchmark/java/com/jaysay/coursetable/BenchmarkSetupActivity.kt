package com.jaysay.coursetable

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.jaysay.coursetable.data.model.Course
import com.jaysay.coursetable.data.model.ScheduleViewMode
import com.jaysay.coursetable.data.preferences.AppPreferences
import com.jaysay.coursetable.data.preferences.PreferencesManager
import com.jaysay.coursetable.data.repository.CourseRepository
import com.jaysay.coursetable.data.repository.TableData
import com.jaysay.coursetable.util.TimeUtils
import kotlinx.coroutines.launch

/** Only packaged in .benchmark; fictional fixtures cannot reach release data. */
class BenchmarkSetupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            val count = intent.getIntExtra("count", 42).coerceIn(1, 2000)
            val mode = runCatching { ScheduleViewMode.valueOf(intent.getStringExtra("mode") ?: "WEEK") }.getOrDefault(ScheduleViewMode.WEEK)
            val courses = (0 until count).map { i ->
                Course("bench-$i", "虚构性能课程$i", "", "", 0f, (1..20).toList(),
                    i % 7 + 1, (i / 7 % 6) * 2 + 1, (i / 7 % 6) * 2 + 2,
                    "测试教师", "测试楼A101", "", "", false, "", seriesId = "bench-$i")
            }
            CourseRepository(this@BenchmarkSetupActivity).saveAllTables(listOf(
                TableData("性能测试课表", courses, semesterStart = TimeUtils.currentWeekStartDate(), viewMode = mode)))
            PreferencesManager(this@BenchmarkSetupActivity).save(AppPreferences())
            startActivity(Intent(this@BenchmarkSetupActivity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            finish()
        }
    }
}
