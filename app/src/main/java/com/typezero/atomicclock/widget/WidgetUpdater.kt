package com.typezero.atomicclock.widget

import android.content.Context
import android.os.PowerManager
import android.util.Log
import com.typezero.atomicclock.BuildConfig
import com.typezero.atomicclock.data.AtomicSettings
import com.typezero.atomicclock.data.SettingsRepository
import com.typezero.atomicclock.data.TimeSyncRepository
import com.typezero.atomicclock.ntp.SntpResult
import com.typezero.atomicclock.weather.CurrentWeather
import com.typezero.atomicclock.weather.LocationCache
import com.typezero.atomicclock.weather.WeatherRepository
import com.typezero.atomicclock.weather.backgroundLocationStatus
import kotlinx.coroutines.flow.first

/**
 * Single place that assembles a [WidgetSnapshot] and refreshes it from scratch.
 *
 * Key rule: a missing new value never erases a good old one. If time sync or
 * weather fetch fails, the previous snapshot is carried forward.
 */
object WidgetUpdater {
    private const val TAG = "AtomicWidget"
    private const val NTP_STALE_MS = 60 * 60 * 1000L
    private const val WEATHER_STALE_MS = 60 * 60 * 1000L
    private const val LOCATION_STALE_MS = 3 * 60 * 60 * 1000L

    fun buildSnapshot(
        prev: WidgetSnapshot,
        settings: AtomicSettings,
        sync: SntpResult?,
        syncEpoch: Long,
        weather: CurrentWeather?,
    ): WidgetSnapshot = WidgetSnapshot(
        hasSync = sync != null || prev.hasSync,
        driftMs = sync?.clockOffsetMillis ?: prev.driftMs,
        sourceShort = sync?.let { shortSource(it.server) } ?: prev.sourceShort,
        stratum = sync?.stratum ?: prev.stratum,
        lastSyncEpoch = if (sync != null) syncEpoch else prev.lastSyncEpoch,
        hasWeather = weather != null || prev.hasWeather,
        tempC = weather?.temperatureC ?: prev.tempC,
        humidity = weather?.humidity ?: prev.humidity,
        label = weather?.label ?: prev.label,
        iconName = weather?.icon?.name ?: prev.iconName,
        city = weather?.city ?: prev.city,
        lastWeatherEpoch = if (weather != null) System.currentTimeMillis() else prev.lastWeatherEpoch,
        use24 = settings.use24Hour,
        fahrenheit = settings.fahrenheit,
        windMph = settings.windMph,
        bgLevel = settings.widgetBackground.ordinal,
        dialThemeName = settings.dialTheme.name,
    )

    /**
     * Battery-conscious background refresh.
     *
     * - NTP is sampled only when the previous sync is at least an hour old.
     * - Weather is fetched only when at least an hour old.
     * - A live background location is requested at most every three hours, only
     *   when Android has granted background location and Battery Saver is off.
     * - Otherwise weather reuses the last good coordinates.
     * - The widget is redrawn only when its stored snapshot actually changes.
     */
    suspend fun refreshFromBackground(context: Context, reason: String) {
        val now = System.currentTimeMillis()
        val settings = SettingsRepository(context).settings.first()
        val prev = WidgetStore.load(context)
        val powerSave = (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)
            ?.isPowerSaveMode == true

        val syncDue = prev.lastSyncEpoch <= 0 || now - prev.lastSyncEpoch >= NTP_STALE_MS
        val weatherDue = prev.lastWeatherEpoch <= 0 || now - prev.lastWeatherEpoch >= WEATHER_STALE_MS

        log("refresh reason=$reason syncDue=$syncDue weatherDue=$weatherDue powerSave=$powerSave")

        val sync = if (syncDue) {
            runCatching { TimeSyncRepository().sync(settings.server, samples = 2) }.getOrNull()
        } else {
            null
        }

        val weatherRepo = WeatherRepository(context)
        val location = LocationCache.load(context)
        val locationDue = location == null || now - location.savedAtEpoch >= LOCATION_STALE_MS
        val canRefreshLocation = backgroundLocationStatus(context).ready && !powerSave

        val weather = if (weatherDue) {
            // Refresh coordinates occasionally while travelling; don't wake the
            // location stack on every weather update.
            val fromFreshLocation = if (locationDue && canRefreshLocation) {
                runCatching { weatherRepo.fetch(forceFresh = true) }.getOrNull()
            } else {
                null
            }
            fromFreshLocation
                ?: runCatching { weatherRepo.fetchFromCachedLocation() }.getOrNull()
        } else {
            null
        }

        val snapshot = buildSnapshot(
            prev = prev,
            settings = settings,
            sync = sync,
            syncEpoch = now,
            weather = weather,
        )

        if (WidgetStore.saveIfChanged(context, snapshot)) {
            AtomicClockWidget.refresh(context)
            log("snapshot changed; widget redrawn")
        } else {
            log("snapshot unchanged; redraw skipped")
        }
    }

    fun shortSource(host: String): String = host
        .removePrefix("time.")
        .substringBefore('.')
        .replaceFirstChar { it.uppercase() }

    private fun log(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }
}
