package com.ivanwitt.mayasunmoon

import android.graphics.Bitmap
import android.graphics.Canvas
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

        drawLocationText(canvas, paint, w, h, settings)

        val sidePad = w * 0.10f
        val radius = min(w * 0.39f, h * 0.43f)
        val cx = w / 2f
        val baselineY = h * 0.57f

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = max(3f, w * 0.006f)
        canvas.drawLine(sidePad, baselineY, w - sidePad, baselineY, paint)
        val arcRect = RectF(cx - radius, baselineY - radius, cx + radius, baselineY + radius)
        canvas.drawArc(arcRect, 180f, 180f, false, paint)

        drawTicks(canvas, paint, cx, baselineY, radius, isSun)

        // Preserve the original requirement: while the body is actually above the horizon,
        // a thick ray marks the current position on the rise-to-set arc.
        state.arcDegrees?.let {
            drawActiveRay(canvas, paint, cx, baselineY, radius, it, isSun)
        }

        // New v0.2 marker: a clearly visible point sits on the arc continuously while rise/set
        // data exist. It starts at the left horizon at rise and reaches the right horizon at set.
        state.markerDegrees?.let {
            drawPositionDot(canvas, paint, cx, baselineY, radius, it)
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

        val numeralY = baselineY - radius * 0.52f
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

        // The old small open circle used as a "Moon below horizon" indicator was removed because
        // it visually merged with the Mayan numeral and could be mistaken for an extra digit.

        drawCalendarText(canvas, paint, w, h, mayaDate)
        return bitmap
    }

    private fun drawLocationText(
        canvas: Canvas,
        paint: Paint,
        width: Int,
        height: Int,
        settings: WidgetSettings
    ) {
        if (!settings.showLocationName) return
        val label = listOf(settings.cityName.trim(), settings.countryName.trim())
            .filter { it.isNotBlank() }
            .joinToString(", ")
        if (label.isBlank()) return

        paint.style = Paint.Style.FILL
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = max(14f, width * 0.027f)
        canvas.drawText(label, width / 2f, height * 0.075f, paint)
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

    private fun drawActiveRay(
        canvas: Canvas,
        paint: Paint,
        cx: Float,
        baselineY: Float,
        radius: Float,
        degrees: Double,
        isSun: Boolean
    ) {
        val theta = PI - degrees.coerceIn(0.0, 180.0) * PI / 180.0
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = max(7f, radius * 0.035f)

        val inner = if (isSun) radius * 0.86f else radius * 0.72f
        val outer = if (isSun) radius * 1.17f else radius * 0.99f

        val x1 = cx + (inner * cos(theta)).toFloat()
        val y1 = baselineY - (inner * sin(theta)).toFloat()
        val x2 = cx + (outer * cos(theta)).toFloat()
        val y2 = baselineY - (outer * sin(theta)).toFloat()
        canvas.drawLine(x1, y1, x2, y2, paint)
    }

    private fun drawPositionDot(
        canvas: Canvas,
        paint: Paint,
        cx: Float,
        baselineY: Float,
        radius: Float,
        degrees: Double
    ) {
        val theta = PI - degrees.coerceIn(0.0, 180.0) * PI / 180.0
        val x = cx + (radius * cos(theta)).toFloat()
        val y = baselineY - (radius * sin(theta)).toFloat()
        val dotRadius = max(9f, radius * 0.045f)
        paint.style = Paint.Style.FILL
        canvas.drawCircle(x, y, dotRadius, paint)
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
        mayaDate: MayaDate
    ) {
        paint.style = Paint.Style.FILL
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        paint.textAlign = Paint.Align.CENTER

        paint.textSize = max(20f, width * 0.050f)
        val y1 = height * 0.79f
        canvas.drawText(mayaDate.longCount, width / 2f, y1, paint)

        paint.textSize = max(17f, width * 0.039f)
        val y2 = height * 0.92f
        canvas.drawText("${mayaDate.tzolkin}  /  ${mayaDate.haab}", width / 2f, y2, paint)
    }
}
