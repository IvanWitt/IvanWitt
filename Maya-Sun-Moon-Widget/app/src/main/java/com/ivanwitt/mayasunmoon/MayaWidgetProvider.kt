package com.ivanwitt.mayasunmoon

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt
import kotlin.math.sqrt

class MayaWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        scheduleNext(context)
        val pending = goAsync()
        val appContext = context.applicationContext
        Thread {
            try {
                renderWidgets(appContext, appWidgetManager, appWidgetIds)
            } finally {
                pending.finish()
            }
        }.start()
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        val pending = goAsync()
        val appContext = context.applicationContext
        Thread {
            try {
                renderWidgets(appContext, appWidgetManager, intArrayOf(appWidgetId))
            } finally {
                pending.finish()
            }
        }.start()
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        AstroSyncJobService.scheduleIfNeeded(context)
        scheduleNext(context)
    }

    override fun onDisabled(context: Context) {
        cancelScheduled(context)
        super.onDisabled(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_REFRESH,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                scheduleNext(context)
                val pending = goAsync()
                val appContext = context.applicationContext
                Thread {
                    try {
                        renderAllNow(appContext)
                    } finally {
                        pending.finish()
                    }
                }.start()
                return
            }
        }
        super.onReceive(context, intent)
    }

    companion object {
        const val ACTION_REFRESH = "com.ivanwitt.mayasunmoon.REFRESH_WIDGET"
        private const val REQUEST_REFRESH = 4107
        private const val REFRESH_INTERVAL_MS = 15 * 60 * 1000L
        private const val TAG = "MayaWidgetProvider"

        // Classic v0.2.11 artwork is now the only renderer.  The frame is drawn directly at the
        // resolution that is sent to RemoteViews, so there is no extra downscale pass that blurs
        // text, circle strokes, Sun or Moon.  The pixel budget stays below the Android Binder limit.
        private const val MAX_PUBLISH_PIXELS = 220_000.0
        private const val MAX_PUBLISH_WIDTH = 620
        private const val MAX_PUBLISH_HEIGHT = 460
        private const val FALLBACK_PIXELS = 125_000.0

        fun updateAll(context: Context) {
            val appContext = context.applicationContext
            Thread { renderAllNow(appContext) }.start()
        }

        private fun renderAllNow(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, MayaWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            renderWidgets(context, manager, ids)
        }

        private fun renderWidgets(
            context: Context,
            manager: AppWidgetManager,
            ids: IntArray
        ) {
            if (ids.isEmpty()) return

            val settings = WidgetPrefs.load(context)
            val now = System.currentTimeMillis()
            val zone = ZoneId.systemDefault()

            AstroSyncJobService.scheduleIfNeeded(context)
            val networkCache = SkyScheduleStore.loadUsable(context, settings, zone, now)
            val snapshot = AstroEngine.snapshot(settings, now, zone, networkCache)
            val localDate = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            val mayaDate = MayaCalendar.fromGregorian(localDate, settings.correlation)

            ids.forEach { id ->
                val options = manager.getAppWidgetOptions(id)
                val (renderWidth, renderHeight) = chooseRenderSize(options)

                val frameResult = runCatching {
                    // Render the proven classic widget directly at publication resolution.
                    // No DynamicScenery, no cloud artwork and no intermediate bitmap scaling.
                    WidgetRenderer.render(
                        width = renderWidth,
                        height = renderHeight,
                        settings = settings,
                        snapshot = snapshot,
                        mayaDate = mayaDate,
                        nowMillis = now,
                        zone = zone
                    )
                }

                frameResult.onSuccess { frame ->
                    val publishResult = publishFrame(context, manager, id, frame)
                    if (publishResult.isFailure) {
                        Log.e(TAG, "Unable to publish rendered widget $id", publishResult.exceptionOrNull())
                        showErrorPlaceholder(context, manager, id)
                    }
                }.onFailure { error ->
                    Log.e(TAG, "Unable to render widget $id", error)
                    showErrorPlaceholder(context, manager, id)
                }
            }
        }

        private fun chooseRenderSize(options: android.os.Bundle): Pair<Int, Int> {
            val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 300).coerceAtLeast(120)
            val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 190).coerceAtLeast(90)
            val aspect = (widthDp.toDouble() / heightDp.toDouble()).coerceIn(0.75, 2.60)

            var width = sqrt(MAX_PUBLISH_PIXELS * aspect).roundToInt()
            var height = sqrt(MAX_PUBLISH_PIXELS / aspect).roundToInt()

            if (width > MAX_PUBLISH_WIDTH) {
                width = MAX_PUBLISH_WIDTH
                height = (width / aspect).roundToInt()
            }
            if (height > MAX_PUBLISH_HEIGHT) {
                height = MAX_PUBLISH_HEIGHT
                width = (height * aspect).roundToInt()
            }

            return width.coerceAtLeast(320) to height.coerceAtLeast(220)
        }

        private fun publishFrame(
            context: Context,
            manager: AppWidgetManager,
            id: Int,
            frame: Bitmap
        ): Result<Unit> {
            fun publish(bitmap: Bitmap) {
                val views = RemoteViews(context.packageName, R.layout.maya_widget)
                views.setImageViewBitmap(R.id.widget_image, bitmap)
                views.setViewVisibility(R.id.widget_loading, View.GONE)
                attachSettingsClick(context, views, id)
                manager.updateAppWidget(id, views)
            }

            val normal = runCatching { publish(frame) }
            if (normal.isSuccess) return Result.success(Unit)

            Log.w(TAG, "HQ RemoteViews publish failed; retrying with smaller classic frame", normal.exceptionOrNull())
            return runCatching {
                val aspect = frame.width.toDouble() / frame.height.toDouble()
                val fallbackWidth = sqrt(FALLBACK_PIXELS * aspect).roundToInt().coerceAtLeast(320)
                val fallbackHeight = (fallbackWidth / aspect).roundToInt().coerceAtLeast(200)
                val smaller = Bitmap.createScaledBitmap(frame, fallbackWidth, fallbackHeight, true)
                publish(smaller)
            }
        }

        private fun showErrorPlaceholder(context: Context, manager: AppWidgetManager, id: Int) {
            runCatching {
                val views = RemoteViews(context.packageName, R.layout.maya_widget)
                views.setViewVisibility(R.id.widget_loading, View.VISIBLE)
                views.setTextViewText(R.id.widget_loading, "Maya Sun/Moon\nНажмите для настроек")
                attachSettingsClick(context, views, id)
                manager.updateAppWidget(id, views)
            }.onFailure {
                Log.e(TAG, "Unable to publish even the fallback placeholder", it)
            }
        }

        private fun attachSettingsClick(context: Context, views: RemoteViews, id: Int) {
            val openSettings = Intent(context, MainActivity::class.java)
            val openPending = PendingIntent.getActivity(
                context,
                id,
                openSettings,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, openPending)
        }

        private fun scheduleNext(context: Context) {
            val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarm.set(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + REFRESH_INTERVAL_MS,
                refreshPendingIntent(context)
            )
        }

        private fun cancelScheduled(context: Context) {
            val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarm.cancel(refreshPendingIntent(context))
        }

        private fun refreshPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, MayaWidgetProvider::class.java).setAction(ACTION_REFRESH)
            return PendingIntent.getBroadcast(
                context,
                REQUEST_REFRESH,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
