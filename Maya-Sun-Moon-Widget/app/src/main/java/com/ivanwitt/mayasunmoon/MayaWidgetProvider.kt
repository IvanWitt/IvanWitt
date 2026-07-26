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

        // RemoteViews transports Bitmaps through the launcher process. Keep the published payload safely
        // below the sizes that caused the v0.3.1 placeholder to remain on some Android launchers.
        private const val PUBLISH_WIDTH = 420
        private const val PUBLISH_HEIGHT = 263
        private const val FALLBACK_PUBLISH_WIDTH = 320
        private const val FALLBACK_PUBLISH_HEIGHT = 200

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
                // initialLayout already provides a visible placeholder while this background render runs.
                // Do not overwrite an existing good frame with the placeholder on every refresh.
                val frameResult = runCatching {
                    val foreground = WidgetRenderer.render(
                        width = 480,
                        height = 300,
                        settings = settings,
                        snapshot = snapshot,
                        mayaDate = mayaDate,
                        nowMillis = now,
                        zone = zone
                    )

                    // If the new scenery layer fails for any device-specific reason, preserve the proven
                    // v0.2.11 foreground instead of leaving the widget blank/placeholder-only.
                    runCatching {
                        DynamicScenery.compose(
                            width = 480,
                            height = 300,
                            snapshot = snapshot,
                            foreground = foreground
                        )
                    }.onFailure {
                        Log.e(TAG, "Dynamic scenery failed; using classic foreground", it)
                    }.getOrElse { foreground }
                }

                frameResult.onSuccess { fullFrame ->
                    val publishResult = publishFrame(context, manager, id, fullFrame)
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

        private fun publishFrame(
            context: Context,
            manager: AppWidgetManager,
            id: Int,
            fullFrame: Bitmap
        ): Result<Unit> {
            fun publish(bitmap: Bitmap) {
                val views = RemoteViews(context.packageName, R.layout.maya_widget)
                views.setImageViewBitmap(R.id.widget_image, bitmap)
                views.setViewVisibility(R.id.widget_loading, View.GONE)
                attachSettingsClick(context, views, id)
                manager.updateAppWidget(id, views)
            }

            val normal = runCatching {
                val safe = Bitmap.createScaledBitmap(fullFrame, PUBLISH_WIDTH, PUBLISH_HEIGHT, true)
                publish(safe)
            }
            if (normal.isSuccess) return Result.success(Unit)

            Log.w(TAG, "Normal RemoteViews bitmap publish failed; retrying smaller payload", normal.exceptionOrNull())
            return runCatching {
                val tiny = Bitmap.createScaledBitmap(
                    fullFrame,
                    FALLBACK_PUBLISH_WIDTH,
                    FALLBACK_PUBLISH_HEIGHT,
                    true
                )
                publish(tiny)
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
