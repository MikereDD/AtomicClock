package com.typezero.atomicclock.widget

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Periodically refreshes the widget snapshot while the app is closed.
 *
 * The widget clock itself is a TextClock, so background work is only needed for
 * network-backed data (NTP + weather/location), not for advancing the displayed
 * time. v0.6 deliberately keeps this cadence conservative for battery life.
 */
class WidgetRefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        WidgetUpdater.refreshFromBackground(
            context = applicationContext,
            reason = inputData.getString(KEY_REASON) ?: REASON_PERIODIC,
        )
        Result.success()
    } catch (_: Throwable) {
        Result.retry()
    }

    companion object {
        const val KEY_REASON = "refresh_reason"
        const val REASON_PERIODIC = "periodic"
        const val REASON_WIDGET = "widget"
        const val REASON_BOOT = "boot"
    }
}

/** Schedules/cancels battery-conscious widget background work. */
object WidgetWork {
    private const val NAME = "atomic_widget_refresh"
    private const val REFRESH_NOW_NAME = "atomic_widget_refresh_now"

    /**
     * Keep one periodic worker. WorkManager is allowed to batch this with other
     * maintenance, and battery-not-low prevents nonessential network/location
     * work when power is already constrained.
     */
    fun ensureScheduled(context: Context) {
        val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(
            60,
            TimeUnit.MINUTES,
        ).setConstraints(periodicConstraints())
            .setInputData(reasonData(WidgetRefreshWorker.REASON_PERIODIC))
            .build()

        // UPDATE upgrades an existing v0.5.x 15-minute schedule in place.
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    /** Queue an immediate one-off refresh after an explicit lifecycle event. */
    fun refreshNow(context: Context, reason: String = WidgetRefreshWorker.REASON_WIDGET) {
        val request = OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
            .setConstraints(immediateConstraints())
            .setInputData(reasonData(reason))
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            REFRESH_NOW_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(NAME)
        WorkManager.getInstance(context).cancelUniqueWork(REFRESH_NOW_NAME)
    }

    private fun periodicConstraints(): Constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .setRequiresBatteryNotLow(true)
        .build()

    private fun immediateConstraints(): Constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    private fun reasonData(reason: String): Data = Data.Builder()
        .putString(WidgetRefreshWorker.KEY_REASON, reason)
        .build()
}
