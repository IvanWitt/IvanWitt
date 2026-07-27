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

    /**
     * Draws the selected decoration as the background of the upper semicircle and then restores
     * the already rendered widget frame above it. The original frame is mutated intentionally:
     * MayaWidgetProvider invokes this method from also { ... } and keeps that exact Bitmap.
     */
    fun apply(context: Context, frame: Bitmap, settings: WidgetSettings): Bitmap {
        val resId = resourceFor(settings.decorationStyle) ?: return frame
        val asset = synchronized(cache) {
            cache[resId] ?: BitmapFactory.decodeResource(context.resources, resId)?.also { cache[resId] = it }
        } ?: return frame

        val w = max(frame.width, 480)
        val h = max(frame.height, 300)
        val outerExtentFactor = 1.34f + 0.54f / 2f
        val safeRadiusByWidth = w * 0.485f / outerExtentFactor
        val safeRadiusByHeight = h * 0.485f / outerExtentFactor
        val radius = min(min(w * 0.39f, safeRadiusByWidth), safeRadiusByHeight)
        val cx = w / 2f
        val baselineY = h * 0.50f
        val arcRect = RectF(cx - radius, baselineY - radius, cx + radius, baselineY + radius)
        val src = contentBounds(resId, asset)

        val dst = if (settings.decorationStyle == DecorationStyle.GOLDEN_TEMPLE) {
            // Keep the already approved golden-temple composition unchanged: it remains a
            // horizon decoration instead of being stretched into a photographic background.
            var targetWidth = radius * 1.78f
            var targetHeight = targetWidth * src.height().toFloat() / src.width().toFloat()
            val maxHeight = radius * 0.66f
            if (targetHeight > maxHeight && targetHeight > 0f) {
                val scale = maxHeight / targetHeight
                targetHeight = maxHeight
                targetWidth *= scale
            }
            RectF(
                cx - targetWidth / 2f,
                baselineY - targetHeight,
                cx + targetWidth / 2f,
                baselineY + 2f
            )
        } else {
            // The three supplied photographic PNGs are true backgrounds: use every pixel of the
            // upper semicircle from its apex to the horizon and from the left arc to the right.
            RectF(
                cx - radius,
                baselineY - radius,
                cx + radius,
                baselineY + 1f
            )
        }

        val composed = Bitmap.createBitmap(frame.width, frame.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(composed)
        val save = canvas.save()
        val clip = Path().apply {
            moveTo(cx - radius, baselineY)
            arcTo(arcRect, 180f, 180f, false)
            lineTo(cx - radius, baselineY)
            close()
        }
        canvas.clipPath(clip)

        val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            isDither = true
            alpha = 255
        }
        canvas.drawBitmap(asset, src, dst, imagePaint)
        canvas.restoreToCount(save)

        // Horizon, upper contour, Mayan digits, text and Sun/Moon remain in the foreground.
        canvas.drawBitmap(frame, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))

        val target = Canvas(frame)
        val replacePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC)
        }
        target.drawBitmap(composed, 0f, 0f, replacePaint)
        replacePaint.xfermode = null
        composed.recycle()
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
                // Ignore transparent export margins so the visible supplied artwork itself fills
                // the requested destination rather than being shrunk by invisible canvas space.
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
