package com.jestk.browser.engine.adblock

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.jestk.browser.engine.AdBlockerEngine
import java.util.concurrent.TimeUnit

/** Refreshes the filter lists and swaps the engines; see [FilterListController.refresh]. */
class FilterListUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ok = FilterListServices.controller(applicationContext).refresh()
        return if (ok || runAttemptCount >= MAX_ATTEMPTS) Result.success() else Result.retry()
    }

    companion object {
        private const val MAX_ATTEMPTS = 3
    }
}

/** Process-wide access to the single [FilterListController] (tests inject their own). */
internal object FilterListServices {
    @Volatile
    var override: FilterListController? = null

    @Volatile
    private var instance: FilterListController? = null

    fun controller(context: Context): FilterListController =
        override ?: instance ?: synchronized(this) {
            instance ?: FilterListController.create(context) { AdBlockerEngine.engineStore }
                .also { instance = it }
        }

    /** App start: lets the engine load the downloaded lists and schedules the periodic refresh. */
    fun install(context: Context) {
        controller(context).attach(AdBlockerEngine.engineStore)
        FilterListScheduler.schedulePeriodic(context)
    }
}

/** WorkManager entry points: the daily refresh and the settings "Update now" button. */
internal object FilterListScheduler {
    private const val PERIODIC = "adblock-filter-lists-periodic"
    private const val NOW = "adblock-filter-lists-now"
    private const val PERIOD_HOURS = 24L

    /** About daily, unmetered network, battery not low. Keeps an already scheduled job. */
    fun schedulePeriodic(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .setRequiresBatteryNotLow(true)
            .build()
        val request = PeriodicWorkRequestBuilder<FilterListUpdateWorker>(PERIOD_HOURS, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** One refresh now on any network (settings button, first Strict download). */
    fun updateNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<FilterListUpdateWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, request)
    }
}
