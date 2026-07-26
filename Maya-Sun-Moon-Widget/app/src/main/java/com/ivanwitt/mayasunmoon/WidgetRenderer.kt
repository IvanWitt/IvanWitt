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
            isFilterBitmap = true
        }

        val state = if (snapshot.activeBody == SkyBody.SUN) snapshot.sun else snapshot.moon
        val isSun = snapshot.activeBody == SkyBody.SUN

        val radius = min(w * 0.39f, h * 0.39f)
        val cx = w / 2f
        val baselineY = h * 0.50f
        val arcRect = RectF(cx - radius, baselineY - radius, cx + radius, baselineY + radius)

        val horizonTail = radius * 0.14f
        val horizonStartX = (cx - radius - horizonTail).coerceAtLeast(w * 0.03f)
        val horizonEndX = (cx + radius + horizonTail).coerceAtMost(w * 0.97f)

        // Invisible half of the cycle: darker gray at 50% opacity, with no outline.
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(45, 45, 45)
        paint.alpha = (255 * 0.50f).roundToInt()
        canvas.drawArc(arcRect, 0f, 180f, true, paint)

        paint.style = Paint.Style.STROKE
        paint.color = settings.color
        paint.alpha = 255
        paint.strokeWidth = max(3f, w * 0.006f)
        canvas.drawLine(horizonStartX, baselineY, horizonEndX, baselineY, paint)
        canvas.drawArc(arcRect, 180f, 180f, false, paint)
        drawTicks(canvas, paint, cx, baselineY, radius, isSun)

        // Sun and Moon share one larger, perfectly concentric orbit around the existing circle.
        // The orbit itself is intentionally not drawn: it is only their trajectory.
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
            settings = settings
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
        val topGap = max(height * 0.016f, radius * 0.040f)
        // Tighten only Long Count -> Tzolkin/Haab; keep the location row comfortably separate.
        val calendarGap = max(0.5f, height * 0.0015f)
        val locationGap = max(2f, height * 0.006f)

        paint.textSize = longTextSize
        paint.textScaleX = 1f
        val longMetrics = paint.fontMetrics
        val longTop = baselineY + topGap
        val longCountY = longTop - longMetrics.top
        val longBottom = longCountY + longMetrics.bottom

        paint.textSize = roundTextSize
        val roundMetrics = paint.fontMetrics
        val roundTop = longBottom + calendarGap
        val calendarRoundY = roundTop - roundMetrics.top
        val roundBottom = calendarRoundY + roundMetrics.bottom

        paint.textSize = locationTextSize
        val locationMetrics = paint.fontMetrics
        val desiredLocationTop = roundBottom + locationGap
        val bottomPadding = max(5f, height * 0.018f)
        val maxLocationBaseline = height - bottomPadding - locationMetrics.bottom
        val locationY = min(desiredLocationTop - locationMetrics.top, maxLocationBaseline)

        drawTextAtTargetWidth(
            canvas, paint, mayaDate.longCount, width / 2f, longCountY,
            longTargetWidth, longTextSize, 0.78f, 1.22f
        )

        val roundText = "${mayaDate.tzolkin} / ${mayaDate.haab}"
        drawTextAtTargetWidth(
            canvas, paint, roundText, width / 2f, calendarRoundY,
            roundTargetWidth, roundTextSize, 0.64f, 0.92f
        )

        if (settings.showLocationName) {
            val label = listOf(settings.cityName.trim(), settings.countryName.trim())
                .filter { it.isNotBlank() }
                .joinToString(", ")
            if (label.isNotBlank()) {
                drawTextAtTargetWidth(
                    canvas, paint, label, width / 2f, locationY,
                    locationTargetWidth, locationTextSize, 0.72f, 1.0f
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
