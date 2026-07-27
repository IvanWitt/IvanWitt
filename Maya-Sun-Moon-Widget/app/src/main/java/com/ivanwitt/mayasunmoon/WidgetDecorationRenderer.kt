package com.ivanwitt.mayasunmoon

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min

object WidgetDecorationRenderer {
    private val cache = mutableMapOf<Int, Bitmap>()

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

        val targetWidth = radius * when (settings.decorationStyle) {
            DecorationStyle.MAYA_NIGHT -> 1.84f
            DecorationStyle.MAYA_FLIGHT -> 1.82f
            DecorationStyle.PALMS -> 1.82f
            DecorationStyle.GOLDEN_TEMPLE -> 1.78f
            DecorationStyle.DEFAULT -> return frame
        }

        var targetHeight = targetWidth * asset.height.toFloat() / asset.width.toFloat()
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

        val dst = RectF(
            cx - finalWidth / 2f,
            baselineY - targetHeight,
            cx + finalWidth / 2f,
            baselineY
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
        canvas.drawBitmap(asset, null, dst, p)
        p.xfermode = null
        canvas.restoreToCount(save)
        return frame
    }

    fun resourceFor(style: DecorationStyle): Int? = when (style) {
        DecorationStyle.DEFAULT -> null
        DecorationStyle.MAYA_NIGHT -> R.drawable.design_maya_night
        DecorationStyle.MAYA_FLIGHT -> R.drawable.design_maya_flight
        DecorationStyle.PALMS -> R.drawable.design_palms
        DecorationStyle.GOLDEN_TEMPLE -> R.drawable.design_golden_temple
    }
}
