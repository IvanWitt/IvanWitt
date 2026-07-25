package com.ivanwitt.mayasunmoon

import android.content.Context
import android.graphics.Color

enum class CenterMode {
    ARC_DEGREES,
    MONTH_VISIBLE_HOURS,
    CLOCK_12H
}

data class WidgetSettings(
    val centerMode: CenterMode,
    val correlation: Int,
    val color: Int,
    val latitude: Double,
    val longitude: Double,
    val elevationMeters: Double,
    val locationUpdatedAt: Long,
    val hasLocationFix: Boolean
)

object WidgetPrefs {
    private const val PREFS = "maya_sun_moon_widget"
    private const val KEY_MODE = "center_mode"
    private const val KEY_CORRELATION = "correlation"
    private const val KEY_COLOR = "color"
    private const val KEY_LAT = "latitude"
    private const val KEY_LON = "longitude"
    private const val KEY_ELEV = "elevation"
    private const val KEY_LOCATION_UPDATED = "location_updated"
    private const val KEY_HAS_FIX = "has_location_fix"

    // Moscow is a safe visual fallback until the user explicitly refreshes location.
    private const val DEFAULT_LAT = 55.7558
    private const val DEFAULT_LON = 37.6173

    fun load(context: Context): WidgetSettings {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val mode = runCatching {
            CenterMode.valueOf(p.getString(KEY_MODE, CenterMode.MONTH_VISIBLE_HOURS.name)!!)
        }.getOrDefault(CenterMode.MONTH_VISIBLE_HOURS)

        return WidgetSettings(
            centerMode = mode,
            correlation = p.getInt(KEY_CORRELATION, 584283),
            color = p.getInt(KEY_COLOR, Color.WHITE),
            latitude = Double.fromBits(p.getLong(KEY_LAT, DEFAULT_LAT.toBits())),
            longitude = Double.fromBits(p.getLong(KEY_LON, DEFAULT_LON.toBits())),
            elevationMeters = Double.fromBits(p.getLong(KEY_ELEV, 0.0.toBits())),
            locationUpdatedAt = p.getLong(KEY_LOCATION_UPDATED, 0L),
            hasLocationFix = p.getBoolean(KEY_HAS_FIX, false)
        )
    }

    fun saveDisplay(context: Context, mode: CenterMode, correlation: Int, color: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODE, mode.name)
            .putInt(KEY_CORRELATION, correlation)
            .putInt(KEY_COLOR, color)
            .apply()
    }

    fun saveLocation(context: Context, latitude: Double, longitude: Double, elevationMeters: Double) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAT, latitude.toBits())
            .putLong(KEY_LON, longitude.toBits())
            .putLong(KEY_ELEV, elevationMeters.toBits())
            .putLong(KEY_LOCATION_UPDATED, System.currentTimeMillis())
            .putBoolean(KEY_HAS_FIX, true)
            .apply()
    }
}
