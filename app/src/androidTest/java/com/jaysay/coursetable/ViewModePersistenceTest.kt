package com.jaysay.coursetable

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jaysay.coursetable.data.model.ScheduleViewMode
import com.jaysay.coursetable.data.repository.CourseRepository
import com.jaysay.coursetable.data.repository.TableData
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ViewModePersistenceTest {
    @Test fun oldFailedDayRequestCannotUndoNewDayRequest() = exercise(false)
    @Test fun successiveFailuresReturnToConfirmedMode() = exercise(true)

    private fun exercise(failLatest: Boolean) = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val directory = File(context.cacheDir, "view-mode-${System.nanoTime()}").apply { mkdirs() }
        val application = object : Application() { override fun getFilesDir(): File = directory }
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        var writes = 0
        var errors = 0
        val store = ViewModelStore()
        val model = withContext(Dispatchers.Main) {
            MainViewModel(application) { tables ->
                writes++
                if (writes == 1) {
                    firstStarted.complete(Unit)
                    releaseFirst.await()
                    error("injected first write failure")
                }
                if (failLatest) error("injected latest write failure")
                CourseRepository(application).saveAllTables(tables)
            }.also { store.put("test", it) }
        }
        try {
            withTimeout(5000) { while (withContext(Dispatchers.Main) { model.state.isLoading }) delay(10) }
            withContext(Dispatchers.Main) { model.setScheduleViewMode(ScheduleViewMode.DAY) { errors++ } }
            firstStarted.await()
            withContext(Dispatchers.Main) {
                assertEquals(ScheduleViewMode.WEEK, model.state.tables[0].viewMode)
                assertEquals(ScheduleViewMode.DAY, model.state.activeTable.viewMode)
                model.setScheduleViewMode(ScheduleViewMode.MONTH) { errors++ }
                model.setScheduleViewMode(ScheduleViewMode.DAY) { errors++ }
            }
            releaseFirst.complete(Unit)
            withTimeout(5000) { while (withContext(Dispatchers.Main) { model.state.pendingViewModes.isNotEmpty() }) delay(10) }
            withContext(Dispatchers.Main) {
                val expected = if (failLatest) ScheduleViewMode.WEEK else ScheduleViewMode.DAY
                assertEquals(expected, model.state.activeTable.viewMode)
                assertEquals(expected, model.state.tables[0].viewMode)
                assertEquals(if (failLatest) 1 else 0, errors)
                assertEquals(2, writes) // The superseded month request never writes.
            }
            if (!failLatest) assertEquals(ScheduleViewMode.DAY, CourseRepository(application).loadAllTables()[0].viewMode)
        } finally {
            withContext(Dispatchers.Main) { store.clear() }
            directory.deleteRecursively()
        }
    }
}
