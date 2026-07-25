package com.ivanwitt.mayasunmoon

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.abs

/**
 * One local calendar day's rise/set information downloaded from the official
 * U.S. Naval Observatory Astronomical Applications API.
 */
data class DailySkySchedule(
    val date: LocalDate,
    val sunRiseMillis: Long?,
    val sunSetMillis: Long?,
    val moonRiseMillis: Long?,
    val moonSetMillis: Long?
)

data class SkyScheduleCache(
    val fetchedAtMillis: Long,
    val validUntilMillis: Long,
    val latitude: Double,
    val longitude: Double,
    val zoneId: String,
    val days: List<DailySkySchedule>
) {
    fun matches(settings: WidgetSettings, zone: ZoneId): Boolean =
        abs(latitude - settings.latitude) < 0.01 &&
            abs(longitude - settings.longitude) < 0.01 &&
            zoneId == zone.id

    fun isUsable(settings: WidgetSettings, zone: ZoneId, nowMillis: Long): Boolean {
        if (!matches(settings, zone)) return false
        if (nowMillis > validUntilMillis) return false
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        // A next-day row is required because the Moon can rise before midnight and set after it.
        return days.any { it.date == today } && days.any { it.date == today.plusDays(1) }
    }
}

object SkyScheduleStore {
    private const val PREFS = "maya_sun_moon_network_data"
    private const val KEY_CACHE = "usno_cache_json"
    const val CACHE_LIFETIME_MS = 72L * 60L * 60L * 1000L

    fun save(context: Context, cache: SkyScheduleCache) {
        val root = JSONObject()
            .put("fetchedAt", cache.fetchedAtMillis)
            .put("validUntil", cache.validUntilMillis)
            .put("lat", cache.latitude)
            .put("lon", cache.longitude)
            .put("zone", cache.zoneId)

        val days = JSONArray()
        cache.days.forEach { day ->
            days.put(
                JSONObject()
                    .put("date", day.date.toString())
                    .putNullableLong("sunRise", day.sunRiseMillis)
                    .putNullableLong("sunSet", day.sunSetMillis)
                    .putNullableLong("moonRise", day.moonRiseMillis)
                    .putNullableLong("moonSet", day.moonSetMillis)
            )
        }
        root.put("days", days)

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CACHE, root.toString())
            .apply()
    }

    fun load(context: Context): SkyScheduleCache? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_CACHE, null) ?: return null

        return runCatching {
            val root = JSONObject(raw)
            val array = root.getJSONArray("days")
            val days = ArrayList<DailySkySchedule>(array.length())
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                days += DailySkySchedule(
                    date = LocalDate.parse(obj.getString("date")),
                    sunRiseMillis = obj.optNullableLong("sunRise"),
                    sunSetMillis = obj.optNullableLong("sunSet"),
                    moonRiseMillis = obj.optNullableLong("moonRise"),
                    moonSetMillis = obj.optNullableLong("moonSet")
                )
            }
            SkyScheduleCache(
                fetchedAtMillis = root.getLong("fetchedAt"),
                validUntilMillis = root.getLong("validUntil"),
                latitude = root.getDouble("lat"),
                longitude = root.getDouble("lon"),
                zoneId = root.getString("zone"),
                days = days.sortedBy { it.date }
            )
        }.getOrNull()
    }

    fun loadUsable(
        context: Context,
        settings: WidgetSettings,
        zone: ZoneId,
        nowMillis: Long
    ): SkyScheduleCache? = load(context)?.takeIf { it.isUsable(settings, zone, nowMillis) }

    fun needsRefresh(
        context: Context,
        settings: WidgetSettings,
        zone: ZoneId,
        nowMillis: Long
    ): Boolean = load(context)?.isUsable(settings, zone, nowMillis) != true

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_CACHE)
            .apply()
    }

    private fun JSONObject.putNullableLong(key: String, value: Long?): JSONObject =
        put(key, value ?: JSONObject.NULL)

    private fun JSONObject.optNullableLong(key: String): Long? =
        if (!has(key) || isNull(key)) null else getLong(key)
}

/**
 * Very small HTTPS client for the official USNO "Complete Sun and Moon Data for One Day" API.
 * No API key is needed. We intentionally use the optional ID parameter so USNO can count this
 * client as a distinct API user.
 */
object UsnoDataClient {
    private const val ENDPOINT = "https://aa.usno.navy.mil/api/rstt/oneday"
    private const val CLIENT_ID = "IvanWitt"

    fun fetchCache(
        settings: WidgetSettings,
        zone: ZoneId,
        nowMillis: Long = System.currentTimeMillis()
    ): SkyScheduleCache {
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()

        // Yesterday reconstructs a Moon interval that began before midnight.
        // Through +4 days keeps a next-day row available for the full 72-hour cache lifetime,
        // including a lunar set that occurs after midnight near the end of that period.
        val schedules = (-1L..4L).map { delta ->
            fetchDay(settings, zone, today.plusDays(delta))
        }

        return SkyScheduleCache(
            fetchedAtMillis = nowMillis,
            validUntilMillis = nowMillis + SkyScheduleStore.CACHE_LIFETIME_MS,
            latitude = settings.latitude,
            longitude = settings.longitude,
            zoneId = zone.id,
            days = schedules
        )
    }

    private fun fetchDay(
        settings: WidgetSettings,
        zone: ZoneId,
        date: LocalDate
    ): DailySkySchedule {
        // USNO accepts a fixed east-positive offset. Use the actual offset at local noon for this date,
        // which handles ordinary daylight-saving dates without applying US-specific DST rules.
        val offset = date.atTime(12, 0).atZone(zone).offset
        val tzHours = offset.totalSeconds / 3600.0
        val tzText = if (tzHours % 1.0 == 0.0) {
            tzHours.toInt().toString()
        } else {
            String.format(Locale.US, "%.2f", tzHours).trimEnd('0').trimEnd('.')
        }

        val coords = String.format(Locale.US, "%.6f,%.6f", settings.latitude, settings.longitude)
        val url = URL(
            "$ENDPOINT?date=$date" +
                "&coords=${URLEncoder.encode(coords, "UTF-8")}" +
                "&tz=${URLEncoder.encode(tzText, "UTF-8")}" +
                "&ID=$CLIENT_ID"
        )

        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 12_000
            setRequestProperty("Accept", "application/json, application/geo+json")
            setRequestProperty("User-Agent", "MayaSunMoonWidget/0.2 IvanWitt")
        }

        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IllegalStateException("USNO HTTP $code")
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            return parseDay(JSONObject(body), date, offset)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseDay(root: JSONObject, date: LocalDate, offset: ZoneOffset): DailySkySchedule {
        if (root.has("error") && !root.isNull("error")) {
            val value = root.opt("error")
            if (value != false && value.toString() != "false") {
                throw IllegalStateException("USNO API error: $value")
            }
        }

        // v4 uses GeoJSON with properties/data; older response shapes exposed the arrays at root.
        val properties = root.optJSONObject("properties")
        val data = properties?.optJSONObject("data") ?: properties ?: root
        val sun = data.optJSONArray("sundata") ?: root.optJSONArray("sundata")
        val moon = data.optJSONArray("moondata") ?: root.optJSONArray("moondata")

        return DailySkySchedule(
            date = date,
            sunRiseMillis = eventMillis(sun, date, offset, rise = true),
            sunSetMillis = eventMillis(sun, date, offset, rise = false),
            moonRiseMillis = eventMillis(moon, date, offset, rise = true),
            moonSetMillis = eventMillis(moon, date, offset, rise = false)
        )
    }

    private fun eventMillis(
        events: JSONArray?,
        date: LocalDate,
        offset: ZoneOffset,
        rise: Boolean
    ): Long? {
        if (events == null) return null
        for (i in 0 until events.length()) {
            val item = events.optJSONObject(i) ?: continue
            val phen = item.optString("phen", "").trim().uppercase(Locale.US)
            val matches = if (rise) {
                phen == "R" || phen.contains("RISE")
            } else {
                phen == "S" || phen.contains("SET")
            }
            if (!matches) continue

            val timeText = item.optString("time", "").trim()
            if (timeText.isBlank() || timeText.equals("null", ignoreCase = true)) return null
            return localTimeToMillis(date, timeText, offset)
        }
        return null
    }

    private fun localTimeToMillis(date: LocalDate, raw: String, offset: ZoneOffset): Long {
        val clean = raw.substringBefore(' ').trim()
        val parts = clean.split(':')
        require(parts.size >= 2) { "Bad USNO time: $raw" }
        val hour = parts[0].toInt()
        val minute = parts[1].toInt()
        val second = parts.getOrNull(2)?.toDoubleOrNull() ?: 0.0
        val wholeSecond = second.toInt().coerceIn(0, 59)
        val nano = ((second - wholeSecond) * 1_000_000_000.0).toInt().coerceIn(0, 999_999_999)

        return if (hour == 24) {
            date.plusDays(1).atStartOfDay().toInstant(offset).toEpochMilli()
        } else {
            date.atTime(LocalTime.of(hour, minute, wholeSecond, nano)).toInstant(offset).toEpochMilli()
        }
    }
}

/**
 * System-managed network refresh. A stale cache schedules this job with a network constraint;
 * Android starts it when connectivity becomes available, so no always-on background process is used.
 */
class AstroSyncJobService : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        Thread {
            val success = runCatching {
                val settings = WidgetPrefs.load(this)
                if (!settings.hasLocationFix) return@runCatching false
                val zone = ZoneId.systemDefault()
                val cache = UsnoDataClient.fetchCache(settings, zone)
                SkyScheduleStore.save(this, cache)
                true
            }.onFailure {
                Log.w(TAG, "USNO synchronization failed", it)
            }.getOrDefault(false)

            MayaWidgetProvider.updateAll(this)
            jobFinished(params, !success)
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true

    companion object {
        private const val TAG = "MayaAstroSync"
        private const val JOB_ID = 0x4D4159 // "MAY"

        fun scheduleIfNeeded(context: Context) {
            val settings = WidgetPrefs.load(context)
            if (!settings.hasLocationFix) return
            val zone = ZoneId.systemDefault()
            if (SkyScheduleStore.needsRefresh(context, settings, zone, System.currentTimeMillis())) {
                schedule(context)
            }
        }

        fun schedule(context: Context) {
            val settings = WidgetPrefs.load(context)
            if (!settings.hasLocationFix) return

            val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            val component = ComponentName(context, AstroSyncJobService::class.java)
            val info = JobInfo.Builder(JOB_ID, component)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .setBackoffCriteria(30_000L, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                .build()
            scheduler.schedule(info)
        }
    }
}
