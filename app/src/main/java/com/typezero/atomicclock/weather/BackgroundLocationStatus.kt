package com.typezero.atomicclock.weather

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** User-facing status for automatic widget weather updates while the app is closed. */
data class BackgroundLocationStatus(
    val foregroundGranted: Boolean,
    val backgroundGranted: Boolean,
) {
    val ready: Boolean get() = foregroundGranted && backgroundGranted

    val title: String
        get() = if (ready) "Live widget location is on" else "Enable live widget location"

    val message: String
        get() = when {
            ready -> "Weather can follow your location while Atomic Clock is closed."
            !foregroundGranted -> "Allow location so Atomic Clock can find local weather."
            else -> "Weather still refreshes for your last known location. Set Location to \"Allow all the time\" if you want the widget city and weather to follow you while travelling."
        }
}

fun backgroundLocationStatus(context: Context): BackgroundLocationStatus {
    val foreground = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED

    val background = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_BACKGROUND_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED

    return BackgroundLocationStatus(
        foregroundGranted = foreground,
        backgroundGranted = background,
    )
}
