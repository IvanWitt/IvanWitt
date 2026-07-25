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
import java.time.ZoneId
import java.time.ZoneOffset

enum class SkyBody { SUN, MOON }

data class BodyState(
    val body: SkyBody,
    val visible: Boolean,
    val arcDegrees: Double?,
    val markerDegrees: Double?,
    val altitudeDegrees: Double,
    val azimuthDegrees: Double,
    val currentCycleHours: Double?
)

data class AstroSnapshot(
    val activeBody: SkyBody,
    val sun: BodyState,
    val moon: BodyState,
    val usingNetworkSchedule: Boolean
)

object AstroEngine {
    fun snapshot(
        settings: WidgetSettings,
        nowMillis: Long,
        zone: ZoneId,
        networkCache: SkyScheduleCache? = null
    ): AstroSnapshot {
        val observer = Observer(settings.latitude, settings.longitude, settings.elevationMeters)
        val now = timeFromMillis(nowMillis)

        val sunFromCache = networkCache?.let {
            bodyStateFromSchedule(Body.Sun, SkyBody.SUN, observer, now, nowMillis, it)
        }
        val moonFromCache = networkCache?.let {
            bodyStateFromSchedule(Body.Moon, SkyBody.MOON, observer, now, nowMillis, it)
        }

        val sun = sunFromCache ?: bodyStateLocal(Body.Sun, SkyBody.SUN, observer, now, nowMillis)
        val moon = moonFromCache ?: bodyStateLocal(Body.Moon, SkyBody.MOON, observer, now, nowMillis)

        // Sun always wins. The instant it is no longer above the horizon the widget becomes lunar,
        // even when the Moon itself has not risen yet or has already set.
        val active = if (sun.visible) SkyBody.SUN else SkyBody.MOON
        return AstroSnapshot(
            activeBody = active,
            sun = sun,
            moon = moon,
            usingNetworkSchedule = sunFromCache != null && moonFromCache != null
        )
    }

    private fun bodyStateFromSchedule(
        body: Body,
        skyBody: SkyBody,
        observer: Observer,
        now: Time,
        nowMillis: Long,
        cache: SkyScheduleCache
    ): BodyState? {
        val rises = cache.days.mapNotNull {
            if (skyBody == SkyBody.SUN) it.sunRiseMillis else it.moonRiseMillis
        }.sorted()
        val sets = cache.days.mapNotNull {
            if (skyBody == SkyBody.SUN) it.sunSetMillis else it.moonSetMillis
        }.sorted()

        if (rises.isEmpty() || sets.isEmpty()) return null

        val lastRise = rises.lastOrNull { it <= nowMillis }
        val lastSet = sets.lastOrNull { it <= nowMillis }
        val visible = lastRise != null && (lastSet == null || lastRise > lastSet)

        var arc: Double? = null
        var marker: Double? = null
        var cycleHours: Double? = null

        if (visible && lastRise != null) {
            val nextSet = sets.firstOrNull { it > lastRise }
            if (nextSet != null && nextSet > lastRise) {
                cycleHours = (nextSet - lastRise) / 3_600_000.0
                arc = ((nowMillis - lastRise).toDouble() / (nextSet - lastRise).toDouble() * 180.0)
                    .coerceIn(0.0, 180.0)
                marker = arc
            }
        } else {
            val nextRise = rises.firstOrNull { it > nowMillis }
            if (nextRise != null) {
                val nextSet = sets.firstOrNull { it > nextRise }
                if (nextSet != null && nextSet > nextRise) {
                    cycleHours = (nextSet - nextRise) / 3_600_000.0
                }
            }
            // Keep the marker on the horizon whenever rise/set data exist. After a set it remains
            // at the right-hand horizon until the next rise, then starts again from the left.
            marker = when {
                lastSet != null -> 180.0
                nextRise != null -> 0.0
                else -> null
            }
        }

        val position = horizontal(body, observer, now)
        return BodyState(
            body = skyBody,
            visible = visible,
            arcDegrees = arc,
            markerDegrees = marker,
            altitudeDegrees = position.first,
            azimuthDegrees = position.second,
            currentCycleHours = cycleHours
        )
    }

    private fun bodyStateLocal(
        body: Body,
        skyBody: SkyBody,
        observer: Observer,
        now: Time,
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

        var arc: Double? = null
        var marker: Double? = null
        var cycleHours: Double? = null

        if (visible && lastRise != null) {
            val nextSet = searchRiseSet(body, observer, Direction.Set, now, 3.0)
            if (nextSet != null) {
                val riseMs = lastRise.toMillisecondsSince1970()
                val setMs = nextSet.toMillisecondsSince1970()
                if (setMs > riseMs) {
                    cycleHours = (setMs - riseMs) / 3_600_000.0
                    arc = ((nowMillis - riseMs).toDouble() / (setMs - riseMs).toDouble() * 180.0)
                        .coerceIn(0.0, 180.0)
                    marker = arc
                }
            }
        } else {
            val nextRise = searchRiseSet(body, observer, Direction.Rise, now, 3.0)
            if (nextRise != null) {
                val nextSet = searchRiseSet(body, observer, Direction.Set, nextRise.addDays(0.001), 3.0)
                if (nextSet != null) {
                    val riseMs = nextRise.toMillisecondsSince1970()
                    val setMs = nextSet.toMillisecondsSince1970()
                    if (setMs > riseMs) cycleHours = (setMs - riseMs) / 3_600_000.0
                }
            }
            marker = when {
                lastSet != null -> 180.0
                nextRise != null -> 0.0
                else -> null
            }
        }

        return BodyState(
            body = skyBody,
            visible = visible,
            arcDegrees = arc,
            markerDegrees = marker,
            altitudeDegrees = position.first,
            azimuthDegrees = position.second,
            currentCycleHours = cycleHours
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
