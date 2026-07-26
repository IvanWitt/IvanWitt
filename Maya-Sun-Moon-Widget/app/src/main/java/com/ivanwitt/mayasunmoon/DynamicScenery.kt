package com.ivanwitt.mayasunmoon

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Builds the scenic layer behind the existing clock/calendar renderer.
 *
 * The scene is completely offline.  It uses the same rise/set orbit model already used by the
 * Sun/Moon artwork, so background brightness and the direction of the light follow the actual
 * celestial state without extra network requests.
 */
object DynamicScenery {
    fun compose(
        width: Int,
        height: Int,
        snapshot: AstroSnapshot,
        foreground: Bitmap
    ): Bitmap {
        val w = max(width, foreground.width)
        val h = max(height, foreground.height)
        val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        drawScene(canvas, w, h, snapshot)
        canvas.drawBitmap(foreground, 0f, 0f, null)
        return output
    }

    private fun drawScene(canvas: Canvas, width: Int, height: Int, snapshot: AstroSnapshot) {
        val master = SceneryAssets.masterBitmap()
        val destination = fitCenter(master, width, height)
        val scene = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val sceneCanvas = Canvas(scene)

        val sunStrength = upperArcStrength(snapshot.sun)
        val moonStrength = upperArcStrength(snapshot.moon)
        val twilightStrength = solarTwilightStrength(snapshot.sun)

        val colorMatrix = when {
            snapshot.sun.visible -> {
                // Sunrise/setting is warm and moderately dim; noon reaches the original artwork.
                val t = sqrt(sunStrength.coerceIn(0f, 1f))
                val brightness = lerp(0.58f, 1.00f, t)
                val warmth = 1f - sunStrength
                colorMatrix(
                    r = brightness * (1.06f + 0.08f * warmth),
                    g = brightness * (1.00f + 0.01f * warmth),
                    b = brightness * (0.96f - 0.16f * warmth)
                )
            }

            twilightStrength > 0f -> {
                // Smooth fade immediately after sunset and immediately before sunrise.
                val brightness = lerp(0.20f, 0.55f, twilightStrength)
                colorMatrix(
                    r = brightness * 1.12f,
                    g = brightness * 0.94f,
                    b = brightness * 0.78f
                )
            }

            snapshot.moon.visible -> {
                // Moon above the horizon: visibly lighter than moonless night and cooler in tone.
                val t = sqrt(moonStrength.coerceIn(0f, 1f))
                colorMatrix(
                    r = lerp(0.24f, 0.34f, t),
                    g = lerp(0.30f, 0.42f, t),
                    b = lerp(0.42f, 0.58f, t)
                )
            }

            else -> {
                // Neither Sun nor Moon is above the horizon: darkest state.
                colorMatrix(r = 0.105f, g = 0.13f, b = 0.19f)
            }
        }

        val basePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(colorMatrix)
        }
        sceneCanvas.drawBitmap(master, null, destination, basePaint)

        // Directional illumination follows the same actual orbital position as the moving body.
        when {
            snapshot.sun.visible -> drawDirectionalGlow(
                canvas = sceneCanvas,
                width = width,
                height = height,
                state = snapshot.sun,
                color = Color.rgb(255, 205, 112),
                strength = lerp(0.38f, 0.62f, sunStrength)
            )

            twilightStrength > 0f -> drawDirectionalGlow(
                canvas = sceneCanvas,
                width = width,
                height = height,
                state = snapshot.sun,
                color = Color.rgb(255, 151, 82),
                strength = 0.34f * twilightStrength
            )

            snapshot.moon.visible -> drawDirectionalGlow(
                canvas = sceneCanvas,
                width = width,
                height = height,
                state = snapshot.moon,
                color = Color.rgb(154, 194, 255),
                strength = lerp(0.18f, 0.34f, moonStrength)
            )
        }

        // Keep every tint/glow strictly inside the cloud-island alpha mask.  Wallpaper outside the
        // scenic shape therefore remains untouched and the background keeps its soft feathered edge.
        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        sceneCanvas.drawBitmap(master, null, destination, maskPaint)
        maskPaint.xfermode = null

        canvas.drawBitmap(scene, 0f, 0f, null)
    }

    private fun drawDirectionalGlow(
        canvas: Canvas,
        width: Int,
        height: Int,
        state: BodyState,
        color: Int,
        strength: Float
    ) {
        val degrees = state.orbitDegrees ?: return
        val (sourceX, sourceY) = bodyPosition(width, height, degrees)
        val alpha = (strength.coerceIn(0f, 1f) * 150f).toInt().coerceIn(0, 150)
        if (alpha <= 0) return

        val shader = RadialGradient(
            sourceX,
            sourceY,
            max(width, height) * 0.72f,
            intArrayOf(
                Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color)),
                Color.argb((alpha * 0.40f).toInt(), Color.red(color), Color.green(color), Color.blue(color)),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.34f, 1f),
            Shader.TileMode.CLAMP
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }

    private fun bodyPosition(width: Int, height: Int, degrees: Double): Pair<Float, Float> {
        // Match WidgetRenderer's v0.2.11 geometry exactly so the light appears to come from the
        // visible Sun/Moon image rather than from a second artificial point.
        val outerExtentFactor = 1.34f + 0.54f / 2f
        val safeRadiusByWidth = width * 0.485f / outerExtentFactor
        val safeRadiusByHeight = height * 0.485f / outerExtentFactor
        val radius = min(min(width * 0.39f, safeRadiusByWidth), safeRadiusByHeight)
        val cx = width / 2f
        val baselineY = height * 0.50f
        val orbitRadius = radius * 1.34f
        val theta = PI - degrees.coerceIn(0.0, 360.0) * PI / 180.0
        val x = cx + (orbitRadius * cos(theta)).toFloat()
        val y = baselineY - (orbitRadius * sin(theta)).toFloat()
        return x to y
    }

    private fun upperArcStrength(state: BodyState): Float {
        if (!state.visible) return 0f
        val degrees = (state.orbitDegrees ?: 90.0).coerceIn(0.0, 180.0)
        return sin(degrees * PI / 180.0).toFloat().coerceIn(0f, 1f)
    }

    private fun solarTwilightStrength(sun: BodyState): Float {
        if (sun.visible) return 0f
        val d = sun.orbitDegrees ?: return 0f
        return when {
            d in 180.0..212.0 -> (1.0 - (d - 180.0) / 32.0).toFloat()
            d in 328.0..360.0 -> ((d - 328.0) / 32.0).toFloat()
            else -> 0f
        }.coerceIn(0f, 1f)
    }

    private fun fitCenter(bitmap: Bitmap, width: Int, height: Int): RectF {
        val scale = min(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
        val drawW = bitmap.width * scale
        val drawH = bitmap.height * scale
        val left = (width - drawW) / 2f
        val top = (height - drawH) / 2f
        return RectF(left, top, left + drawW, top + drawH)
    }

    private fun colorMatrix(r: Float, g: Float, b: Float): ColorMatrix = ColorMatrix(
        floatArrayOf(
            r, 0f, 0f, 0f, 0f,
            0f, g, 0f, 0f, 0f,
            0f, 0f, b, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    )

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t.coerceIn(0f, 1f)
}
