package com.ivanwitt.mayasunmoon

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

object WidgetRenderer {
    private val GREGORIAN_FORMATTER = DateTimeFormatter.ofPattern("dd MMMM uuuu", Locale("ru", "RU"))
    private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")

    fun render(
        width: Int,
        height: Int,
        settings: WidgetSettings,
        snapshot: AstroSnapshot,
        mayaDate: MayaDate,
        nowMillis: Long,
        zone: ZoneId
    ): Bitmap {
        val w = max(width, 480)
        val h = max(height, 300)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = settings.color
            alpha = 255
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isFilterBitmap = true
            isDither = true
            isSubpixelText = true
        }

        val state = if (snapshot.activeBody == SkyBody.SUN) snapshot.sun else snapshot.moon
        val isSun = snapshot.activeBody == SkyBody.SUN

        // The outer orbit is 1.34R and the Sun is the larger artwork (0.54R wide).
        // Reserve enough room for orbit + half the body on every side so neither body is clipped.
        val outerExtentFactor = 1.34f + 0.54f / 2f
        val safeRadiusByWidth = w * 0.485f / outerExtentFactor
        val safeRadiusByHeight = h * 0.485f / outerExtentFactor
        val radius = min(min(w * 0.39f, safeRadiusByWidth), safeRadiusByHeight)
        val cx = w / 2f
        val baselineY = h * 0.50f
        val arcRect = RectF(cx - radius, baselineY - radius, cx + radius, baselineY + radius)

        val horizonTail = radius * 0.14f
        val horizonStartX = (cx - radius - horizonTail).coerceAtLeast(w * 0.03f)
        val horizonEndX = (cx + radius + horizonTail).coerceAtMost(w * 0.97f)

        // Lower half is a fill only. The design slider uses true transparency semantics:
        // 0% = opaque, 100% = fully transparent.
        paint.style = Paint.Style.FILL
        paint.color = settings.lowerPanelColor
        paint.alpha = (255f * (100 - settings.lowerPanelTransparencyPercent) / 100f)
            .roundToInt()
            .coerceIn(0, 255)
        canvas.drawArc(arcRect, 0f, 180f, true, paint)

        paint.style = Paint.Style.STROKE
        paint.color = settings.color
        paint.alpha = 255
        paint.strokeWidth = max(3f, w * 0.006f)
        canvas.drawLine(horizonStartX, baselineY, horizonEndX, baselineY, paint)
        canvas.drawArc(arcRect, 180f, 180f, false, paint)
        drawTicks(canvas, paint, cx, baselineY, radius, isSun)

        drawCelestialBody(
            canvas = canvas,
            bitmap = CelestialAssets.sunBitmap(),
            state = snapshot.sun,
            cx = cx,
            baselineY = baselineY,
            radius = radius,
            size = radius * 0.54f
        )
        drawCelestialBody(
            canvas = canvas,
            bitmap = CelestialAssets.moonBitmap(),
            state = snapshot.moon,
            cx = cx,
            baselineY = baselineY,
            radius = radius,
            size = radius * 0.46f
        )

        val centerValue = when (settings.centerMode) {
            CenterMode.ARC_DEGREES -> state.arcDegrees?.roundToInt()
                ?: state.markerDegrees?.roundToInt()
                ?: 0
            CenterMode.VISIBLE_HOURS -> state.currentCycleHours?.roundToInt() ?: 0
            CenterMode.CLOCK_12H -> {
                val hour = Instant.ofEpochMilli(nowMillis).atZone(zone).hour
                val h12 = hour % 12
                if (h12 == 0) 12 else h12
            }
        }.coerceAtLeast(0)

        val numeralY = baselineY - radius * 0.42f
        val numeralHeight = radius * 0.52f
        drawMayanNumber(
            canvas = canvas,
            paint = paint,
            value = centerValue,
            centerX = cx,
            centerY = numeralY,
            maxWidth = radius * 0.70f,
            maxHeight = numeralHeight
        )

        drawCalendarText(
            canvas = canvas,
            paint = paint,
            width = w,
            height = h,
            baselineY = baselineY,
            radius = radius,
            mayaDate = mayaDate,
            settings = settings,
            nowMillis = nowMillis,
            zone = zone
        )
        return bitmap
    }

    private fun drawCelestialBody(
        canvas: Canvas,
        bitmap: Bitmap,
        state: BodyState,
        cx: Float,
        baselineY: Float,
        radius: Float,
        size: Float
    ) {
        val degrees = state.orbitDegrees ?: return
        val theta = PI - degrees.coerceIn(0.0, 360.0) * PI / 180.0
        val orbitRadius = radius * 1.34f
        val x = cx + (orbitRadius * cos(theta)).toFloat()
        val y = baselineY - (orbitRadius * sin(theta)).toFloat()
        val half = size / 2f
        val dst = RectF(x - half, y - half, x + half, y + half)
        val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            isDither = true
            alpha = if (state.visible) 255 else (255 * 0.40f).roundToInt()
        }
        canvas.drawBitmap(bitmap, null, dst, imagePaint)
    }

    private fun drawTicks(
        canvas: Canvas,
        paint: Paint,
        cx: Float,
        baselineY: Float,
        radius: Float,
        isSun: Boolean
    ) {
        paint.style = Paint.Style.STROKE
        paint.alpha = 255
        paint.strokeWidth = max(2f, radius * 0.012f)

        for (deg in 0..180 step 15) {
            val theta = PI - deg * PI / 180.0
            val major = deg % 45 == 0
            val tick = radius * if (major) 0.14f else 0.085f
            val r1 = if (isSun) radius + radius * 0.015f else radius - tick
            val r2 = if (isSun) radius + tick else radius - radius * 0.015f
            val x1 = cx + (r1 * cos(theta)).toFloat()
            val y1 = baselineY - (r1 * sin(theta)).toFloat()
            val x2 = cx + (r2 * cos(theta)).toFloat()
            val y2 = baselineY - (r2 * sin(theta)).toFloat()
            canvas.drawLine(x1, y1, x2, y2, paint)
        }
    }

    private fun drawMayanNumber(
        canvas: Canvas,
        paint: Paint,
        value: Int,
        centerX: Float,
        centerY: Float,
        maxWidth: Float,
        maxHeight: Float
    ) {
        val digits = toBase20(value.coerceAtLeast(0))
        val digitGap = maxHeight * 0.08f
        val digitHeight = (maxHeight - digitGap * (digits.size - 1)) / digits.size
        val startY = centerY - maxHeight / 2f
        digits.forEachIndexed { index, digit ->
            val top = startY + index * (digitHeight + digitGap)
            drawMayanDigit(canvas, paint, digit, centerX, top, maxWidth, digitHeight)
        }
    }

    private fun drawMayanDigit(
        canvas: Canvas,
        paint: Paint,
        digit: Int,
        cx: Float,
        top: Float,
        width: Float,
        height: Float
    ) {
        paint.alpha = 255
        paint.style = Paint.Style.FILL
        paint.color = paint.color

        if (digit == 0) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = max(3f, height * 0.055f)
            val ovalW = width * 0.62f
            val ovalH = height * 0.42f
            val cy = top + height * 0.5f
            val rect = RectF(cx - ovalW / 2f, cy - ovalH / 2f, cx + ovalW / 2f, cy + ovalH / 2f)
            canvas.drawOval(rect, paint)
            canvas.drawLine(cx - ovalW * 0.28f, cy, cx + ovalW * 0.28f, cy, paint)
            return
        }

        val bars = digit / 5
        val dots = digit % 5
        val barW = width * 0.76f
        val barH = max(4f, height * 0.075f)
        val gap = height * 0.075f
        val dotR = max(4f, height * 0.07f)
        val barBlockH = if (bars > 0) bars * barH + (bars - 1) * gap else 0f
        val dotBlockH = if (dots > 0) dotR * 2f else 0f
        val between = if (dots > 0 && bars > 0) height * 0.11f else 0f
        val total = dotBlockH + between + barBlockH
        var y = top + (height - total) / 2f

        if (dots > 0) {
            val spacing = min(dotR * 2.7f, barW / max(1, dots).toFloat())
            val rowW = spacing * (dots - 1)
            val startX = cx - rowW / 2f
            for (i in 0 until dots) canvas.drawCircle(startX + i * spacing, y + dotR, dotR, paint)
            y += dotBlockH + between
        }
        repeat(bars) {
            val rect = RectF(cx - barW / 2f, y, cx + barW / 2f, y + barH)
            canvas.drawRoundRect(rect, barH / 2f, barH / 2f, paint)
            y += barH + gap
        }
    }

    private fun toBase20(value: Int): List<Int> {
        if (value == 0) return listOf(0)
        var n = value
        val rev = mutableListOf<Int>()
        while (n > 0) {
            rev += n % 20
            n /= 20
        }
        return rev.asReversed()
    }

    private fun drawCalendarText(
        canvas: Canvas,
        paint: Paint,
        width: Int,
        height: Int,
        baselineY: Float,
        radius: Float,
        mayaDate: MayaDate,
        settings: WidgetSettings,
        nowMillis: Long,
        zone: ZoneId
    ) {
        paint.style = Paint.Style.FILL
        paint.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        paint.textAlign = Paint.Align.CENTER
        paint.alpha = 255
        paint.isSubpixelText = true

        // Zero-position defaults intentionally preserve the exact typography/spacing approved in v0.3.4.
        val titleFactor = sizeFactor(settings.titleSizeOffsetPercent)
        val primaryFactor = sizeFactor(settings.primarySizeOffsetPercent)
        val secondaryFactor = sizeFactor(settings.secondarySizeOffsetPercent)

        val longTargetWidth = radius * 1.96f * primaryFactor
        val roundTargetWidth = radius * 1.62f * secondaryFactor
        val locationTargetWidth = radius * 1.70f

        val titleTextSize = max(22f, radius * 0.165f) * titleFactor
        val longTextSize = max(30f, radius * 0.285f) * primaryFactor
        val roundTextSize = max(24f, radius * 0.185f) * secondaryFactor
        val locationTextSize = max(20f, radius * 0.125f)

        // Custom title occupies the same place and uses the same typeface as the approved reference.
        paint.color = settings.titleColor
        paint.textSize = titleTextSize
        paint.textScaleX = 1f
        paint.setShadowLayer(
            max(1.5f, radius * 0.008f),
            0f,
            max(1f, radius * 0.004f),
            Color.argb(150, 0, 0, 0)
        )
        val titleY = baselineY + titleTextSize * 0.36f
        canvas.drawText(settings.titleText.ifBlank { "Ваш текст" }, width / 2f, titleY, paint)
        paint.clearShadowLayer()

        paint.color = settings.color
        paint.textSize = longTextSize
        paint.textScaleX = 1f
        val longMetrics = paint.fontMetrics
        val longTop = baselineY + radius * 0.105f
        val longCountY = longTop - longMetrics.top
        val longBottom = longCountY + longMetrics.bottom

        paint.textSize = roundTextSize
        val roundMetrics = paint.fontMetrics
        val roundTop = longBottom + radius * 0.035f
        val calendarRoundY = roundTop - roundMetrics.top
        val roundBottom = calendarRoundY + roundMetrics.bottom

        paint.textSize = locationTextSize
        val locationMetrics = paint.fontMetrics
        val desiredLocationTop = roundBottom + radius * 0.055f
        val bottomPadding = max(5f, height * 0.018f)
        val maxLocationBaseline = height - bottomPadding - locationMetrics.bottom
        val locationY = min(desiredLocationTop - locationMetrics.top, maxLocationBaseline)

        val instant = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val primaryText = when (settings.primaryLineMode) {
            PrimaryLineMode.LONG_COUNT -> mayaDate.longCount
            PrimaryLineMode.GREGORIAN_DATE -> instant.toLocalDate().format(GREGORIAN_FORMATTER)
        }
        drawTextAtTargetWidth(
            canvas, paint, primaryText, width / 2f, longCountY,
            longTargetWidth, longTextSize, 0.88f, 1.12f
        )

        val secondaryText = when (settings.secondaryLineMode) {
            SecondaryLineMode.TZOLKIN_HAAB -> "${mayaDate.tzolkin} / ${mayaDate.haab}"
            SecondaryLineMode.TIME -> instant.toLocalTime().format(TIME_FORMATTER)
        }
        drawTextAtTargetWidth(
            canvas, paint, secondaryText, width / 2f, calendarRoundY,
            roundTargetWidth, roundTextSize, 0.82f, 1.08f
        )

        if (settings.showLocationName) {
            val label = listOf(settings.cityName.trim(), settings.countryName.trim())
                .filter { it.isNotBlank() }
                .joinToString(", ")
            if (label.isNotBlank()) {
                drawTextAtTargetWidth(
                    canvas, paint, label, width / 2f, locationY,
                    locationTargetWidth, locationTextSize, 0.80f, 1.0f
                )
            }
        }

        paint.color = settings.color
        paint.alpha = 255
        paint.style = Paint.Style.FILL
        paint.textScaleX = 1f
        paint.textAlign = Paint.Align.CENTER
        paint.clearShadowLayer()
    }

    private fun sizeFactor(offsetPercent: Int): Float =
        (1f + offsetPercent.coerceIn(-50, 50) / 100f).coerceIn(0.5f, 1.5f)

    private fun drawTextAtTargetWidth(
        canvas: Canvas,
        paint: Paint,
        text: String,
        centerX: Float,
        baselineY: Float,
        targetWidth: Float,
        preferredTextSize: Float,
        minScaleX: Float,
        maxScaleX: Float
    ) {
        paint.alpha = 255
        paint.style = Paint.Style.FILL
        paint.textSize = preferredTextSize
        paint.textScaleX = 1f
        val measured = paint.measureText(text).coerceAtLeast(1f)
        paint.textScaleX = (targetWidth / measured).coerceIn(minScaleX, maxScaleX)
        canvas.drawText(text, centerX, baselineY, paint)
        paint.textScaleX = 1f
    }
}
