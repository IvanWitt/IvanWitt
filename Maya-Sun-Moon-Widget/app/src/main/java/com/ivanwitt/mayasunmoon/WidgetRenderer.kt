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
            alpha = 255
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        val state = if (snapshot.activeBody == SkyBody.SUN) snapshot.sun else snapshot.moon
        val isSun = snapshot.activeBody == SkyBody.SUN

        // Keep enough room above for the external indicator and below for all three text rows.
        // This avoids the clipped fragments that appeared at the bottom of short widget sizes.
        val radius = min(w * 0.39f, h * 0.40f)
        val cx = w / 2f
        val baselineY = h * 0.51f
        val arcRect = RectF(cx - radius, baselineY - radius, cx + radius, baselineY + radius)

        // Symmetric short horizon tails outside the upper semicircle.
        val horizonTail = radius * 0.14f
        val horizonStartX = (cx - radius - horizonTail).coerceAtLeast(w * 0.03f)
        val horizonEndX = (cx + radius + horizonTail).coerceAtMost(w * 0.97f)

        // Lower half: a 30%-opacity neutral gray background with no outline.
        // Draw it first so all text and the horizon stay crisp above it.
        paint.style = Paint.Style.FILL
        paint.color = Color.GRAY
        paint.alpha = (255 * 0.30f).roundToInt()
        canvas.drawArc(arcRect, 0f, 180f, true, paint)

        // Restore the selected widget color and full opacity before all foreground elements.
        val mainStroke = max(3f, w * 0.006f)
        paint.style = Paint.Style.STROKE
        paint.color = settings.color
        paint.alpha = 255
        paint.strokeWidth = mainStroke
        canvas.drawLine(horizonStartX, baselineY, horizonEndX, baselineY, paint)
        canvas.drawArc(arcRect, 180f, 180f, false, paint)

        drawTicks(canvas, paint, cx, baselineY, radius, isSun)

        // One position indicator only: a larger outlined white ring outside the upper arc.
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
        paint.color = paint.color
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

    private fun drawPositionRing(
        canvas: Canvas,
        paint: Paint,
        cx: Float,
        baselineY: Float,
        radius: Float,
        degrees: Double
    ) {
        val theta = PI - degrees.coerceIn(0.0, 180.0) * PI / 180.0
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
        paint.strokeWidth = max(3.5f, radius * 0.020f)
        val ringRadius = max(9f, radius * 0.050f)
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
        paint.color = paint.color
        paint.alpha = 255
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

        val longTargetWidth = radius * 1.94f
        val roundTargetWidth = radius * 1.62f
        val locationTargetWidth = radius * 1.70f
        val longTextSize = max(30f, width * 0.095f)
        val roundTextSize = max(27f, width * 0.067f)
        val locationTextSize = max(22f, width * 0.043f)

        // Position rows from their actual font metrics, not fixed baselines. This keeps every glyph
        // inside the bitmap and removes the small clipped white fragments at the bottom.
        val topGap = max(height * 0.016f, radius * 0.040f)
        val rowGap = max(2f, height * 0.006f)

        paint.textSize = longTextSize
        paint.textScaleX = 1f
        val longMetrics = paint.fontMetrics
        val longTop = baselineY + topGap
        val longCountY = longTop - longMetrics.top
        val longBottom = longCountY + longMetrics.bottom

        paint.textSize = roundTextSize
        val roundMetrics = paint.fontMetrics
        val roundTop = longBottom + rowGap
        val calendarRoundY = roundTop - roundMetrics.top
        val roundBottom = calendarRoundY + roundMetrics.bottom

        paint.textSize = locationTextSize
        val locationMetrics = paint.fontMetrics
        val desiredLocationTop = roundBottom + rowGap
        val bottomPadding = max(5f, height * 0.018f)
        val maxLocationBaseline = height - bottomPadding - locationMetrics.bottom
        val locationY = min(desiredLocationTop - locationMetrics.top, maxLocationBaseline)

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

        paint.color = settings.color
        paint.alpha = 255
        paint.style = Paint.Style.FILL
        paint.textScaleX = 1f
        paint.textAlign = Paint.Align.CENTER
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
