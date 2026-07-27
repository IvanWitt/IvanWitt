package com.ivanwitt.mayasunmoon

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min

object WidgetDecorationRenderer {
    private val cache = mutableMapOf<Int, Bitmap>()
    private val contentBoundsCache = mutableMapOf<Int, Rect>()

    fun apply(context: Context, frame: Bitmap, settings: WidgetSettings): Bitmap {
        val resId = resourceFor(settings.decorationStyle) ?: return frame
        val asset = synchronized(cache) {
            cache[resId] ?: BitmapFactory.decodeResource(context.resources, resId)?.also { cache[resId] = it }
        } ?: return frame
        val src = contentBounds(resId, asset)

        val w = max(frame.width, 480)
        val h = max(frame.height, 300)
        val outerExtentFactor = 1.34f + 0.54f / 2f
        val safeRadiusByWidth = w * 0.485f / outerExtentFactor
        val safeRadiusByHeight = h * 0.485f / outerExtentFactor
        val radius = min(min(w * 0.39f, safeRadiusByWidth), safeRadiusByHeight)
        val cx = w / 2f
        val baselineY = h * 0.50f
        val arcRect = RectF(cx - radius, baselineY - radius, cx + radius, baselineY + radius)

        val targetWidth = radius * when (settings.decorationStyle) {
            DecorationStyle.MAYA_NIGHT -> 1.84f
            DecorationStyle.MAYA_FLIGHT -> 1.82f
            DecorationStyle.PALMS -> 1.82f
            DecorationStyle.GOLDEN_TEMPLE -> 1.78f
            DecorationStyle.DEFAULT -> return frame
        }

        var targetHeight = targetWidth * src.height().toFloat() / src.width().toFloat()
        val maxHeight = radius * when (settings.decorationStyle) {
            DecorationStyle.MAYA_NIGHT -> 0.72f
            DecorationStyle.MAYA_FLIGHT -> 0.58f
            DecorationStyle.PALMS -> 0.38f
            DecorationStyle.GOLDEN_TEMPLE -> 0.66f
            DecorationStyle.DEFAULT -> 0f
        }
        var finalWidth = targetWidth
        if (targetHeight > maxHeight && targetHeight > 0f) {
            val scale = maxHeight / targetHeight
            targetHeight = maxHeight
            finalWidth *= scale
        }

        // The visible bottom row of every PNG is anchored exactly on the horizon.
        // A tiny overlap is intentional: the upper-half clip removes anything below
        // the horizon while preventing a sub-pixel transparent seam above the line.
        val dst = RectF(
            cx - finalWidth / 2f,
            baselineY - targetHeight,
            cx + finalWidth / 2f,
            baselineY + 2f
        )

        val canvas = Canvas(frame)
        val save = canvas.save()
        val clip = Path().apply {
            moveTo(cx - radius, baselineY)
            arcTo(arcRect, 180f, 180f, false)
            lineTo(cx - radius, baselineY)
            close()
        }
        canvas.clipPath(clip)

        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            isDither = true
            alpha = 255
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OVER)
        }
        canvas.drawBitmap(asset, src, dst, p)
        p.xfermode = null
        canvas.restoreToCount(save)
        return frame
    }

    private fun contentBounds(resId: Int, bitmap: Bitmap): Rect = synchronized(contentBoundsCache) {
        contentBoundsCache[resId] ?: findVisibleBounds(bitmap).also { contentBoundsCache[resId] = it }
    }

    private fun findVisibleBounds(bitmap: Bitmap): Rect {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        var minX = width
        var minY = height
        var maxX = -1
        var maxY = -1
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                // Ignore almost-transparent export noise. It previously made the full
                // 1536x1024 canvas look like content and shrank the real drawing until
                // some designs were effectively invisible on the widget.
                if ((pixels[row + x] ushr 24) >= 8) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        return if (maxX >= minX && maxY >= minY) {
            Rect(minX, minY, maxX + 1, maxY + 1)
        } else {
            Rect(0, 0, width, height)
        }
    }

    fun resourceFor(style: DecorationStyle): Int? = when (style) {
        DecorationStyle.DEFAULT -> null
        DecorationStyle.MAYA_NIGHT -> R.drawable.design_maya_night
        DecorationStyle.MAYA_FLIGHT -> R.drawable.design_maya_flight
        DecorationStyle.PALMS -> R.drawable.design_palms
        DecorationStyle.GOLDEN_TEMPLE -> R.drawable.design_golden_temple
    }
}