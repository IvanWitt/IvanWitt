package com.ivanwitt.mayasunmoon

import io.github.cosinekitty.astronomy.Aberration
import io.github.cosinekitty.astronomy.Body
import io.github.cosinekitty.astronomy.Direction
import io.github.cosinekitty.astronomy.EquatorEpoch
import io.github.cosinekitty.astronomy.Observer
import io.github.cosinekitty.astronomy.Refraction
import io.github.cosinekitty.astronomy.Time
import io.github.cosinekitty.astronomy.equator
import io.github.cosinekitty.astronomy.horizon
import io.github.cosinekitty.astronomy.searchRiseSet
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.floor

enum class SkyBody { SUN, MOON }

data class BodyState(
    val body: SkyBody,
    val visible: Boolean,
    val arcDegrees: Double?,
    val altitudeDegrees: Double,
    val azimuthDegrees: Double,
    val currentCycleHours: Double?,
    val monthlyRepresentativeHours: Int?
)

data class AstroSnapshot(
    val activeBody: SkyBody,
    val sun: BodyState,
    val moon: BodyState
)

object AstroEngine {
    fun snapshot(settings: WidgetSettings, nowMillis: Long, zone: ZoneId): AstroSnapshot {
        val observer = Observer(settings.latitude, settings.longitude, settings.elevationMeters)
        val now = timeFromMillis(nowMillis)

        val sun = bodyState(Body.Sun, SkyBody.SUN, observer, now, zone, nowMillis)
        val moon = bodyState(Body.Moon, SkyBody.MOON, observer, now, zone, nowMillis)

        // Sun always wins. When it is below the horizon, the widget switches to Moon
        // even when the Moon itself is currently below the horizon.
        val active = if (sun.visible) SkyBody.SUN else SkyBody.MOON
        return AstroSnapshot(active, sun, moon)
    }

    private fun bodyState(
        body: Body,
        skyBody: SkyBody,
        observer: Observer,
        now: Time,
        zone: ZoneId,
        nowMillis: Long
    ): BodyState {
        val position = horizontal(body, observer, now)
        val altitudeThreshold = if (body == Body.Sun) -0.833 else 0.0

        val lastRise = previousEvent(body, observer, Direction.Rise, now)
        val lastSet = previousEvent(body, observer, Direction.Set, now)
        val eventBasedVisible = lastRise != null && (lastSet == null || lastRise > lastSet)
        val visible = if (lastRise == null && lastSet == null) {
            position.first > altitudeThreshold
        } else {
            eventBasedVisible
        }

        val nextSet = if (visible) searchRiseSet(body, observer, Direction.Set, now, 3.0) else null
        val currentHours =
            if (visible && lastRise != null && nextSet != null) {
                (nextSet.toMillisecondsSince1970() - lastRise.toMillisecondsSince1970()) / 3_600_000.0
            } else null

        val arc =
            if (visible && lastRise != null && nextSet != null) {
                val start = lastRise.toMillisecondsSince1970().toDouble()
                val end = nextSet.toMillisecondsSince1970().toDouble()
                if (end > start) ((nowMillis - start) / (end - start) * 180.0).coerceIn(0.0, 180.0)
                else null
            } else null

        return BodyState(
            body = skyBody,
            visible = visible,
            arcDegrees = arc,
            altitudeDegrees = position.first,
            azimuthDegrees = position.second,
            currentCycleHours = currentHours,
            monthlyRepresentativeHours = monthlyRepresentativeHours(body, observer, nowMillis, zone)
        )
    }

    private fun horizontal(body: Body, observer: Observer, time: Time): Pair<Double, Double> {
        val eq = equator(body, time, observer, EquatorEpoch.OfDate, Aberration.Corrected)
        val hor = horizon(time, observer, eq.ra, eq.dec, Refraction.Normal)
        return hor.altitude to hor.azimuth
    }

    private fun previousEvent(body: Body, observer: Observer, direction: Direction, now: Time): Time? {
        var cursor = now.addDays(-2.0)
        var latest: Time? = null

        repeat(6) {
            val event = searchRiseSet(body, observer, direction, cursor, 2.2) ?: return latest
            if (event > now) return latest
            latest = event
            cursor = event.addDays(0.002)
        }
        return latest
    }

    /**
     * Monthly mode follows the user's month-table concept: the 15th day of the current
     * month is used as the representative date. The returned value is whole visible hours.
     */
    private fun monthlyRepresentativeHours(
        body: Body,
        observer: Observer,
        nowMillis: Long,
        zone: ZoneId
    ): Int? {
        val localNow = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val date = LocalDate.of(localNow.year, localNow.monthValue, 15)
        val startLocal = LocalDateTime.of(date, LocalTime.MIDNIGHT).atZone(zone)
        val start = timeFromMillis(startLocal.toInstant().toEpochMilli())

        val rise = searchRiseSet(body, observer, Direction.Rise, start, 2.2) ?: return null
        val setSearch = rise.addDays(0.002)
        val set = searchRiseSet(body, observer, Direction.Set, setSearch, 2.2) ?: return null

        val hours = (set.toMillisecondsSince1970() - rise.toMillisecondsSince1970()) / 3_600_000.0
        if (hours < 0.0 || hours > 48.0) return null
        return floor(hours).toInt()
    }

    fun timeFromMillis(ms: Long): Time {
        val utc = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC)
        val second = utc.second.toDouble() + utc.nano / 1_000_000_000.0
        return Time(
            utc.year,
            utc.monthValue,
            utc.dayOfMonth,
            utc.hour,
            utc.minute,
            second
        )
    }
}
