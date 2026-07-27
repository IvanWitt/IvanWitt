package com.ivanwitt.mayasunmoon

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.min

/**
 * Composes the selected user-supplied decoration behind the classic widget frame.
 * The artwork is clipped to the visible upper semicircle and its bottom edge is
 * rigidly anchored to the horizon, so resizing the Android widget scales both
 * the dial and the artwork as one composition.
 */
object DecorationRenderer {
    private val cache = mutableMapOf<DecorationStyle, Bitmap>()

    fun compose(context: Context, frame: Bitmap, settings: WidgetSettings): Bitmap {
        if (settings.decorationStyle == DecorationStyle.DEFAULT) return frame

        val artwork = bitmap(context, settings.decorationStyle) ?: return frame
        val w = frame.width
        val h = frame.height
        val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        // Keep this geometry in sync with WidgetRenderer.
        val outerExtentFactor = 1.34f + 0.54f / 2f
        val safeRadiusByWidth = w * 0.485f / outerExtentFactor
        val safeRadiusByHeight = h * 0.485f / outerExtentFactor
        val radius = min(min(w * 0.39f, safeRadiusByWidth), safeRadiusByHeight)
        val cx = w / 2f
        val baselineY = h * 0.50f
        val arcRect = RectF(cx - radius, baselineY - radius, cx + radius, baselineY + radius)

        val widthFactor = when (settings.decorationStyle) {
            DecorationStyle.MAYA_NIGHT -> 1.78f
            DecorationStyle.MAYA_FLIGHT -> 1.82f
            DecorationStyle.PALMS -> 1.84f
            DecorationStyle.GOLDEN_TEMPLE -> 1.78f
            DecorationStyle.DEFAULT -> 1.0f
        }
        var targetWidth = radius * widthFactor
        var targetHeight = targetWidth * artwork.height.toFloat() / artwork.width.toFloat()
        val maxHeight = radius * 0.72f
        if (targetHeight > maxHeight) {
            val scale = maxHeight / targetHeight
            targetHeight *= scale
            targetWidth *= scale
        }

        val bottom = baselineY - radius * 0.012f
        val dst = RectF(
            cx - targetWidth / 2f,
            bottom - targetHeight,
            cx + targetWidth / 2f,
            bottom
        )

        val clip = Path().apply {
            moveTo(cx - radius, baselineY)
            arcTo(arcRect, 180f, 180f, false)
            lineTo(cx - radius, baselineY)
            close()
        }

        val save = canvas.save()
        canvas.clipPath(clip)
        canvas.drawBitmap(
            artwork,
            null,
            dst,
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                isDither = true
                alpha = 255
            }
        )
        canvas.restoreToCount(save)

        // The original widget frame is transparent above the horizon, therefore
        // drawing it second keeps all dial lines, numerals and celestial bodies
        // crisp and guarantees that the decoration stays behind them.
        canvas.drawBitmap(frame, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        return output
    }

    private fun bitmap(context: Context, style: DecorationStyle): Bitmap? = synchronized(cache) {
        cache[style] ?: run {
            val resId = when (style) {
                DecorationStyle.MAYA_NIGHT -> R.drawable.design_maya_night
                DecorationStyle.MAYA_FLIGHT -> R.drawable.design_maya_flight
                DecorationStyle.PALMS -> R.drawable.design_palms
                DecorationStyle.GOLDEN_TEMPLE -> R.drawable.design_golden_temple
                DecorationStyle.DEFAULT -> return@synchronized null
            }
            BitmapFactory.decodeResource(context.resources, resId)?.also { cache[style] = it }
        }
    }
}
