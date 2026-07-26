package com.ivanwitt.mayasunmoon

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import java.time.Instant
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

object WidgetRenderer {
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
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        val state = if (snapshot.activeBody == SkyBody.SUN) snapshot.sun else snapshot.moon
        val isSun = snapshot.activeBody == SkyBody.SUN

        val radius = min(w * 0.39f, h * 0.43f)
        val cx = w / 2f
        val baselineY = h * 0.57f
        val arcRect = RectF(cx - radius, baselineY - radius, cx + radius, baselineY + radius)

        // Slightly longer but still symmetric horizon tails outside the circle.
        val horizonTail = radius * 0.14f
        val horizonStartX = (cx - radius - horizonTail).coerceAtLeast(w * 0.03f)
        val horizonEndX = (cx + radius + horizonTail).coerceAtMost(w * 0.97f)

        // Main horizon and upper semicircle use the selected widget color.
        val mainStroke = max(3f, w * 0.006f)
        paint.style = Paint.Style.STROKE
        paint.color = settings.color
        paint.alpha = 255
        paint.strokeWidth = mainStroke
        canvas.drawLine(horizonStartX, baselineY, horizonEndX, baselineY, paint)
        canvas.drawArc(arcRect, 180f, 180f, false, paint)

        // The lower semicircle is now a neutral gray guide at 30% opacity.
        // It stays behind the text and keeps the lighter half-stroke from the previous design.
        paint.color = Color.GRAY
        paint.alpha = (255 * 0.30f).roundToInt()
        paint.strokeWidth = mainStroke / 2f
        canvas.drawArc(arcRect, 0f, 180f, false, paint)

        // Restore the normal widget color before drawing ticks and numerals.
        paint.color = settings.color
        paint.alpha = 255
        drawTicks(canvas, paint, cx, baselineY, radius, isSun)

        // One indicator only: a small outlined white ring outside the upper arc.
        // It follows the same rise-to-set progress previously represented by the filled dot.
        state.markerDegrees?.let {
            drawPositionRing(canvas, paint, cx, baselineY, radius, it)
        }

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

        // Keep the Mayan numeral anchored to a stable visual center inside the upper semicircle.
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
            settings = settings
        )
        return bitmap
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

    private fun drawPositionRing(
        canvas: Canvas,
        paint: Paint,
        cx: Float,
        baselineY: Float,
        radius: Float,
        degrees: Double
    ) {
        val theta = PI - degrees.coerceIn(0.0, 180.0) * PI / 180.0

        // Put the marker clearly outside the semicircle, close to the distance marked on the screenshot.
        val indicatorRadius = radius * 1.18f
        val x = cx + (indicatorRadius * cos(theta)).toFloat()
        val y = baselineY - (indicatorRadius * sin(theta)).toFloat()

        val oldColor = paint.color
        val oldAlpha = paint.alpha
        val oldStyle = paint.style
        val oldStrokeWidth = paint.strokeWidth

        paint.color = Color.WHITE
        paint.alpha = 255
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = max(3f, radius * 0.018f)
        val ringRadius = max(7f, radius * 0.035f)
        canvas.drawCircle(x, y, ringRadius, paint)

        paint.color = oldColor
        paint.alpha = oldAlpha
        paint.style = oldStyle
        paint.strokeWidth = oldStrokeWidth
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
        paint.style = Paint.Style.FILL

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
            for (i in 0 until dots) {
                canvas.drawCircle(startX + i * spacing, y + dotR, dotR, paint)
            }
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
        settings: WidgetSettings
    ) {
        paint.style = Paint.Style.FILL
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        paint.textAlign = Paint.Align.CENTER
        paint.color = settings.color
        paint.alpha = 255

        // Keep the established typography, but move the complete text block just a little closer
        // to the horizon while preserving a visible gap above the Long Count.
        val longTargetWidth = radius * 1.94f
        val roundTargetWidth = radius * 1.62f
        val locationTargetWidth = radius * 1.62f
        val longTextSize = max(30f, width * 0.095f)
        val roundTextSize = max(27f, width * 0.067f)
        val locationTextSize = max(22f, width * 0.043f)

        paint.textSize = longTextSize
        paint.textScaleX = 1f
        val longMetrics = paint.fontMetrics
        val topGap = max(height * 0.022f, radius * 0.055f)
        val longCountY = baselineY + topGap - longMetrics.top

        // Preserve the existing vertical rhythm between all three rows.
        val calendarRoundY = longCountY + height * 0.130f
        val locationY = calendarRoundY + height * 0.120f

        drawTextAtTargetWidth(
            canvas = canvas,
            paint = paint,
            text = mayaDate.longCount,
            centerX = width / 2f,
            baselineY = longCountY,
            targetWidth = longTargetWidth,
            preferredTextSize = longTextSize,
            minScaleX = 0.78f,
            maxScaleX = 1.22f
        )

        val roundText = "${mayaDate.tzolkin} / ${mayaDate.haab}"
        drawTextAtTargetWidth(
            canvas = canvas,
            paint = paint,
            text = roundText,
            centerX = width / 2f,
            baselineY = calendarRoundY,
            targetWidth = roundTargetWidth,
            preferredTextSize = roundTextSize,
            minScaleX = 0.64f,
            maxScaleX = 0.92f
        )

        if (settings.showLocationName) {
            val label = listOf(settings.cityName.trim(), settings.countryName.trim())
                .filter { it.isNotBlank() }
                .joinToString(", ")
            if (label.isNotBlank()) {
                drawTextAtTargetWidth(
                    canvas = canvas,
                    paint = paint,
                    text = label,
                    centerX = width / 2f,
                    baselineY = locationY,
                    targetWidth = locationTargetWidth,
                    preferredTextSize = locationTextSize,
                    minScaleX = 0.72f,
                    maxScaleX = 1.0f
                )
            }
        }

        paint.textScaleX = 1f
    }

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
        paint.textSize = preferredTextSize
        paint.textScaleX = 1f
        val measured = paint.measureText(text).coerceAtLeast(1f)
        paint.textScaleX = (targetWidth / measured).coerceIn(minScaleX, maxScaleX)
        canvas.drawText(text, centerX, baselineY, paint)
        paint.textScaleX = 1f
    }
}
