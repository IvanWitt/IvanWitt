package com.ivanwitt.mayasunmoon

import android.content.Context
import android.graphics.Color

enum class CenterMode {
    ARC_DEGREES,
    VISIBLE_HOURS,
    CLOCK_12H
}

enum class PrimaryLineMode {
    LONG_COUNT,
    GREGORIAN_DATE
}

enum class SecondaryLineMode {
    TZOLKIN_HAAB,
    TIME
}

data class WidgetSettings(
    val centerMode: CenterMode,
    val correlation: Int,
    val color: Int,
    val titleText: String,
    val titleColor: Int,
    val titleSizeOffsetPercent: Int,
    val primaryLineMode: PrimaryLineMode,
    val primarySizeOffsetPercent: Int,
    val secondaryLineMode: SecondaryLineMode,
    val secondarySizeOffsetPercent: Int,
    val lowerPanelColor: Int,
    val lowerPanelOpacityPercent: Int,
    val latitude: Double,
    val longitude: Double,
    val elevationMeters: Double,
    val locationUpdatedAt: Long,
    val hasLocationFix: Boolean,
    val showLocationName: Boolean,
    val cityName: String,
    val countryName: String
)

object WidgetPrefs {
    private const val PREFS = "maya_sun_moon_widget"
    private const val KEY_MODE = "center_mode"
    private const val KEY_CORRELATION = "correlation"
    private const val KEY_COLOR = "color"

    private const val KEY_TITLE_TEXT = "design_title_text"
    private const val KEY_TITLE_COLOR = "design_title_color"
    private const val KEY_TITLE_SIZE = "design_title_size_offset"
    private const val KEY_PRIMARY_MODE = "design_primary_mode"
    private const val KEY_PRIMARY_SIZE = "design_primary_size_offset"
    private const val KEY_SECONDARY_MODE = "design_secondary_mode"
    private const val KEY_SECONDARY_SIZE = "design_secondary_size_offset"
    private const val KEY_LOWER_PANEL_COLOR = "design_lower_panel_color"
    private const val KEY_LOWER_PANEL_OPACITY = "design_lower_panel_opacity"

    private const val KEY_LAT = "latitude"
    private const val KEY_LON = "longitude"
    private const val KEY_ELEV = "elevation"
    private const val KEY_LOCATION_UPDATED = "location_updated"
    private const val KEY_HAS_FIX = "has_location_fix"
    private const val KEY_SHOW_LOCATION_NAME = "show_location_name"
    private const val KEY_CITY_NAME = "city_name_en"
    private const val KEY_COUNTRY_NAME = "country_name_en"

    // Moscow remains only as a visual fallback until an explicit location fix is saved.
    private const val DEFAULT_LAT = 55.7558
    private const val DEFAULT_LON = 37.6173

    fun load(context: Context): WidgetSettings {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val rawMode = p.getString(KEY_MODE, CenterMode.VISIBLE_HOURS.name)
        val mode = when (rawMode) {
            // Migration from v0.1: the old month/15th-day mode is replaced by the
            // actual rise-to-set visibility interval requested for v0.2.
            "MONTH_VISIBLE_HOURS" -> CenterMode.VISIBLE_HOURS
            else -> runCatching { CenterMode.valueOf(rawMode ?: CenterMode.VISIBLE_HOURS.name) }
                .getOrDefault(CenterMode.VISIBLE_HOURS)
        }

        val primaryMode = runCatching {
            PrimaryLineMode.valueOf(
                p.getString(KEY_PRIMARY_MODE, PrimaryLineMode.LONG_COUNT.name)
                    ?: PrimaryLineMode.LONG_COUNT.name
            )
        }.getOrDefault(PrimaryLineMode.LONG_COUNT)

        val secondaryMode = runCatching {
            SecondaryLineMode.valueOf(
                p.getString(KEY_SECONDARY_MODE, SecondaryLineMode.TZOLKIN_HAAB.name)
                    ?: SecondaryLineMode.TZOLKIN_HAAB.name
            )
        }.getOrDefault(SecondaryLineMode.TZOLKIN_HAAB)

        return WidgetSettings(
            centerMode = mode,
            correlation = p.getInt(KEY_CORRELATION, 584283),
            color = p.getInt(KEY_COLOR, Color.WHITE),
            titleText = p.getString(KEY_TITLE_TEXT, "Ваш текст") ?: "Ваш текст",
            titleColor = p.getInt(KEY_TITLE_COLOR, Color.WHITE),
            titleSizeOffsetPercent = p.getInt(KEY_TITLE_SIZE, 0).coerceIn(-50, 50),
            primaryLineMode = primaryMode,
            primarySizeOffsetPercent = p.getInt(KEY_PRIMARY_SIZE, 0).coerceIn(-50, 50),
            secondaryLineMode = secondaryMode,
            secondarySizeOffsetPercent = p.getInt(KEY_SECONDARY_SIZE, 0).coerceIn(-50, 50),
            lowerPanelColor = p.getInt(KEY_LOWER_PANEL_COLOR, Color.rgb(45, 45, 45)),
            lowerPanelOpacityPercent = p.getInt(KEY_LOWER_PANEL_OPACITY, 50).coerceIn(0, 100),
            latitude = Double.fromBits(p.getLong(KEY_LAT, DEFAULT_LAT.toBits())),
            longitude = Double.fromBits(p.getLong(KEY_LON, DEFAULT_LON.toBits())),
            elevationMeters = Double.fromBits(p.getLong(KEY_ELEV, 0.0.toBits())),
            locationUpdatedAt = p.getLong(KEY_LOCATION_UPDATED, 0L),
            hasLocationFix = p.getBoolean(KEY_HAS_FIX, false),
            showLocationName = p.getBoolean(KEY_SHOW_LOCATION_NAME, false),
            cityName = p.getString(KEY_CITY_NAME, "") ?: "",
            countryName = p.getString(KEY_COUNTRY_NAME, "") ?: ""
        )
    }

    fun saveDisplay(
        context: Context,
        mode: CenterMode,
        correlation: Int,
        color: Int,
        showLocationName: Boolean
    ) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODE, mode.name)
            .putInt(KEY_CORRELATION, correlation)
            .putInt(KEY_COLOR, color)
            .putBoolean(KEY_SHOW_LOCATION_NAME, showLocationName)
            .apply()
    }

    fun saveDesign(
        context: Context,
        titleText: String,
        titleColor: Int,
        titleSizeOffsetPercent: Int,
        primaryLineMode: PrimaryLineMode,
        primarySizeOffsetPercent: Int,
        secondaryLineMode: SecondaryLineMode,
        secondarySizeOffsetPercent: Int,
        lowerPanelColor: Int,
        lowerPanelOpacityPercent: Int
    ) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TITLE_TEXT, titleText.ifBlank { "Ваш текст" })
            .putInt(KEY_TITLE_COLOR, titleColor)
            .putInt(KEY_TITLE_SIZE, titleSizeOffsetPercent.coerceIn(-50, 50))
            .putString(KEY_PRIMARY_MODE, primaryLineMode.name)
            .putInt(KEY_PRIMARY_SIZE, primarySizeOffsetPercent.coerceIn(-50, 50))
            .putString(KEY_SECONDARY_MODE, secondaryLineMode.name)
            .putInt(KEY_SECONDARY_SIZE, secondarySizeOffsetPercent.coerceIn(-50, 50))
            .putInt(KEY_LOWER_PANEL_COLOR, lowerPanelColor)
            .putInt(KEY_LOWER_PANEL_OPACITY, lowerPanelOpacityPercent.coerceIn(0, 100))
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
            // Do not display a place name belonging to the previous coordinates.
            .putString(KEY_CITY_NAME, "")
            .putString(KEY_COUNTRY_NAME, "")
            .apply()
    }

    fun saveLocationNames(context: Context, cityName: String, countryName: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CITY_NAME, cityName)
            .putString(KEY_COUNTRY_NAME, countryName)
            .apply()
    }
}
